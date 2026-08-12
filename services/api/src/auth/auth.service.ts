import {
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import {
  AuditEventType,
  PreferredLanguage,
  Prisma,
  UserRole,
} from '@prisma/client';
import * as argon2 from 'argon2';
import { createHmac, randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { LoginDto, SetupDto } from './auth.dto.js';

const PASSWORD_OPTIONS = {
  type: argon2.argon2id,
  memoryCost: 65_536,
  timeCost: 3,
  parallelism: 1,
  hashLength: 32,
} satisfies argon2.HashOptions;

// Valid Argon2id hash used only to equalise work for an unknown username. It
// never authenticates a real account and contains no secret.
const DUMMY_PASSWORD_HASH =
  '$argon2id$v=19$m=65536,p=1,t=3$THVuYXJhRHVtbXlTYWx0IQ$eE4mWDzU9Ua7tfNx1fLLJESOaLNI34PxYLIuNYOIFxQ';

type DatabaseClient = PrismaService | Prisma.TransactionClient;

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresInSeconds: number;
  sessionId: string;
  user: {
    id: string;
    username: string;
    displayName: string;
    preferredLanguage: PreferredLanguage;
    role: UserRole;
    householdId: string;
  };
}

export interface SessionIdentity {
  id: string;
  username: string;
  displayName: string;
  preferredLanguage: PreferredLanguage;
  role: UserRole;
  householdId: string;
}

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly config: ConfigService,
    private readonly jwt: JwtService,
  ) {}

  async setup(dto: SetupDto): Promise<AuthResponse> {
    if (!this.constantTimeEqual(dto.bootstrapToken, this.config.getOrThrow('BOOTSTRAP_TOKEN'))) {
      throw new UnauthorizedException('Setup token is invalid or setup is unavailable');
    }
    const username = this.normalizeUsername(dto.username);
    const passwordHash = await argon2.hash(dto.password, PASSWORD_OPTIONS);
    const refreshToken = this.createOpaqueToken();

    let result: { identity: SessionIdentity; sessionId: string };
    try {
      result = await this.prisma.$transaction(
        async (tx) => {
          if ((await tx.user.count()) !== 0) {
            throw new ConflictException('Server setup has already been completed');
          }
          const household = await tx.household.create({
            data: { name: dto.householdName.trim() },
          });
          const user = await tx.user.create({
            data: {
              username,
              displayName: dto.displayName.trim(),
              passwordHash,
              preferredLanguage: dto.preferredLanguage ?? PreferredLanguage.DE,
            },
          });
          const membership = await tx.membership.create({
            data: {
              householdId: household.id,
              userId: user.id,
              role: UserRole.TRACKER,
            },
          });
          await Promise.all([
            tx.cycleProfile.create({ data: { userId: user.id } }),
            tx.carePreferences.create({ data: { userId: user.id } }),
            tx.sharingSettings.create({
              data: { ownerUserId: user.id, householdId: household.id },
            }),
          ]);
          const session = await this.persistSession(tx, user.id, dto.deviceName, refreshToken);
          await tx.auditEvent.create({
            data: {
              householdId: household.id,
              actorUserId: user.id,
              type: AuditEventType.NEW_DEVICE,
              metadata: { sessionId: session.id },
            },
          });
          return {
            identity: {
              id: user.id,
              username: user.username,
              displayName: user.displayName,
              preferredLanguage: user.preferredLanguage,
              role: membership.role,
              householdId: membership.householdId,
            },
            sessionId: session.id,
          };
        },
        { isolationLevel: Prisma.TransactionIsolationLevel.Serializable },
      );
    } catch (error) {
      if (error instanceof ConflictException) throw error;
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
        throw new ConflictException('Server setup has already been completed');
      }
      throw error;
    }
    return this.buildAuthResponse(result.identity, result.sessionId, refreshToken);
  }

  async login(dto: LoginDto): Promise<AuthResponse> {
    const username = this.normalizeUsername(dto.username);
    const user = await this.prisma.user.findUnique({
      where: { username },
      include: { memberships: { take: 1, orderBy: { createdAt: 'asc' } } },
    });
    const candidateHash = user && !user.disabledAt ? user.passwordHash : DUMMY_PASSWORD_HASH;
    const passwordMatches = await argon2.verify(candidateHash, dto.password).catch(() => false);
    const valid = Boolean(user && !user.disabledAt && passwordMatches);
    if (!valid || !user || !user.memberships[0]) {
      await this.prisma.auditEvent.create({
        data: { type: AuditEventType.LOGIN_FAILED, metadata: {} },
      });
      throw new UnauthorizedException('Invalid username or password');
    }

    const membership = user.memberships[0];
    const refreshToken = this.createOpaqueToken();
    const session = await this.persistSession(this.prisma, user.id, dto.deviceName, refreshToken);
    await this.prisma.auditEvent.createMany({
      data: [
        {
          householdId: membership.householdId,
          actorUserId: user.id,
          type: AuditEventType.LOGIN_SUCCEEDED,
          metadata: { sessionId: session.id },
        },
        {
          householdId: membership.householdId,
          actorUserId: user.id,
          type: AuditEventType.NEW_DEVICE,
          metadata: { sessionId: session.id },
        },
      ],
    });
    return this.buildAuthResponse(
      {
        id: user.id,
        username: user.username,
        displayName: user.displayName,
        preferredLanguage: user.preferredLanguage,
        role: membership.role,
        householdId: membership.householdId,
      },
      session.id,
      refreshToken,
    );
  }

  async refresh(rawToken: string): Promise<AuthResponse> {
    const tokenHash = this.hashToken(rawToken);
    const replacementToken = this.createOpaqueToken();
    const result = await this.prisma.$transaction(async (tx) => {
      const tokenRecord = await tx.deviceSession.findUnique({
        where: { refreshTokenHash: tokenHash },
        select: { id: true, tokenFamilyId: true, revokedAt: true },
      });
      if (!tokenRecord) {
        throw new UnauthorizedException('Invalid or expired refresh token');
      }
      if (tokenRecord.revokedAt) {
        await tx.deviceSession.updateMany({
          where: { tokenFamilyId: tokenRecord.tokenFamilyId, revokedAt: null },
          data: { revokedAt: new Date() },
        });
        return { reuseDetected: true as const };
      }

      const old = await tx.deviceSession.findUnique({
        where: { id: tokenRecord.id },
        include: {
          user: { include: { memberships: { take: 1, orderBy: { createdAt: 'asc' } } } },
        },
      });
      const membership = old?.user.memberships[0];
      if (!old || !membership || old.expiresAt <= new Date() || old.user.disabledAt) {
        throw new UnauthorizedException('Invalid or expired refresh token');
      }
      const revoked = await tx.deviceSession.updateMany({
        where: { id: old.id, revokedAt: null },
        data: { revokedAt: new Date(), lastUsedAt: new Date() },
      });
      if (revoked.count !== 1) {
        // A concurrent refresh won the compare-and-set after both requests had
        // observed the old token as active. Fail closed and commit revocation of
        // the whole family, including the winner's freshly created replacement.
        await tx.deviceSession.updateMany({
          where: { tokenFamilyId: old.tokenFamilyId, revokedAt: null },
          data: { revokedAt: new Date() },
        });
        return { reuseDetected: true as const };
      }
      const replacement = await this.persistSession(
        tx,
        old.userId,
        old.deviceName,
        replacementToken,
        old.id,
        old.tokenFamilyId,
      );
      return {
        reuseDetected: false as const,
        identity: {
          id: old.user.id,
          username: old.user.username,
          displayName: old.user.displayName,
          preferredLanguage: old.user.preferredLanguage,
          role: membership.role,
          householdId: membership.householdId,
        },
        sessionId: replacement.id,
      };
    });
    if (result.reuseDetected) {
      throw new UnauthorizedException('Refresh token reuse detected; token family revoked');
    }
    return this.buildAuthResponse(result.identity, result.sessionId, replacementToken);
  }

  async logout(user: AuthenticatedUser, rawToken?: string): Promise<void> {
    const filter = rawToken
      ? { userId: user.userId, refreshTokenHash: this.hashToken(rawToken) }
      : { id: user.sessionId, userId: user.userId };
    const result = await this.prisma.deviceSession.updateMany({
      where: { ...filter, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    if (result.count > 0) {
      await this.prisma.auditEvent.create({
        data: {
          householdId: user.householdId,
          actorUserId: user.userId,
          type: AuditEventType.SESSION_REVOKED,
          metadata: {},
        },
      });
    }
  }

  async listSessions(userId: string, currentSessionId: string) {
    const sessions = await this.prisma.deviceSession.findMany({
      where: { userId },
      orderBy: { lastUsedAt: 'desc' },
      select: {
        id: true,
        deviceName: true,
        lastUsedAt: true,
        createdAt: true,
        expiresAt: true,
        revokedAt: true,
      },
    });
    return sessions.map((session) => ({ ...session, current: session.id === currentSessionId }));
  }

  async revokeSession(user: AuthenticatedUser, sessionId: string): Promise<void> {
    const result = await this.prisma.deviceSession.updateMany({
      where: { id: sessionId, userId: user.userId, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    if (result.count === 0) return;
    await this.prisma.auditEvent.create({
      data: {
        householdId: user.householdId,
        actorUserId: user.userId,
        type: AuditEventType.SESSION_REVOKED,
        metadata: { sessionId },
      },
    });
  }

  async hashPassword(password: string): Promise<string> {
    return argon2.hash(password, PASSWORD_OPTIONS);
  }

  async createAuthenticatedSession(
    database: DatabaseClient,
    identity: SessionIdentity,
    deviceName: string,
  ): Promise<AuthResponse> {
    const refreshToken = this.createOpaqueToken();
    const session = await this.persistSession(
      database,
      identity.id,
      deviceName,
      refreshToken,
    );
    return this.buildAuthResponse(identity, session.id, refreshToken);
  }

  createOpaqueToken(): string {
    return randomBytes(48).toString('base64url');
  }

  hashToken(rawToken: string): string {
    return createHmac('sha256', this.config.getOrThrow<string>('REFRESH_TOKEN_PEPPER'))
      .update(rawToken, 'utf8')
      .digest('hex');
  }

  normalizeUsername(username: string): string {
    return username.trim().normalize('NFKC').toLocaleLowerCase('en-US');
  }

  private async persistSession(
    database: DatabaseClient,
    userId: string,
    deviceName: string,
    refreshToken: string,
    rotatedFromId?: string,
    tokenFamilyId: string = randomUUID(),
  ) {
    const ttlDays = this.config.get<number>('lunara.refreshTokenTtlDays', 30);
    return database.deviceSession.create({
      data: {
        userId,
        tokenFamilyId,
        deviceName: deviceName.trim(),
        refreshTokenHash: this.hashToken(refreshToken),
        expiresAt: new Date(Date.now() + ttlDays * 86_400_000),
        rotatedFromId,
      },
    });
  }

  private async buildAuthResponse(
    identity: SessionIdentity,
    sessionId: string,
    refreshToken: string,
  ): Promise<AuthResponse> {
    const expiresInSeconds = this.config.get<number>('lunara.accessTokenTtlSeconds', 900);
    const accessToken = await this.jwt.signAsync(
      { sub: identity.id, sid: sessionId, typ: 'access' },
      { expiresIn: expiresInSeconds },
    );
    return {
      accessToken,
      refreshToken,
      tokenType: 'Bearer',
      expiresInSeconds,
      sessionId,
      user: {
        id: identity.id,
        username: identity.username,
        displayName: identity.displayName,
        preferredLanguage: identity.preferredLanguage,
        role: identity.role,
        householdId: identity.householdId,
      },
    };
  }

  private constantTimeEqual(left: string, right: string): boolean {
    const leftBuffer = Buffer.from(left);
    const rightBuffer = Buffer.from(right);
    return leftBuffer.length === rightBuffer.length && timingSafeEqual(leftBuffer, rightBuffer);
  }
}
