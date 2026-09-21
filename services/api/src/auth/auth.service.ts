import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import {
  AuditEventType,
  IdempotencyKey,
  PreferredLanguage,
  Prisma,
  SessionAuthMethod,
  UserRole,
} from '@prisma/client';
import * as argon2 from 'argon2';
import {
  createHmac,
  randomBytes,
  randomUUID,
  timingSafeEqual,
} from 'node:crypto';
import {
  isServerBootstrapped,
  lockBootstrap,
  lockUserAuthentication,
  markServerBootstrapped,
} from '../common/database/database-coordination.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { EncryptionService } from '../common/security/encryption.service.js';
import { PrismaService } from '../prisma/prisma.service.js';
import {
  ChangePasswordDto,
  LoginDto,
  RegisterDto,
  SetupDto,
} from './auth.dto.js';
import {
  OidcTokenVerifier,
  VerifiedOidcIdentity,
} from './oidc-token-verifier.js';

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

const PASSWORD_CHANGE_FORBIDDEN_MESSAGE =
  'Password change could not be authorized';

const REFRESH_REPLAY_PATH = '/api/v1/auth/refresh';
const REFRESH_REPLAY_TTL_MS = 30 * 86_400_000;
const REFRESH_REPLAY_PURPOSE = 'auth-refresh-replay-v1';

interface RefreshReplayEnvelope {
  version: 1;
  recordId: string;
  userId: string;
  householdId: string;
  key: string;
  method: 'POST';
  path: typeof REFRESH_REPLAY_PATH;
  requestHash: string;
  expiresAt: string;
  originalSessionId: string;
  sessionId: string;
  refreshToken: string;
}

class PasswordChangeRaceError extends Error {}

export async function verifyAccountPassword(
  passwordHash: string | null | undefined,
  password: string,
): Promise<boolean> {
  const passwordMatches = await argon2
    .verify(passwordHash ?? DUMMY_PASSWORD_HASH, password)
    .catch(() => false);
  return Boolean(passwordHash && passwordMatches);
}

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
    private readonly oidcVerifier: OidcTokenVerifier,
    private readonly encryption: EncryptionService,
  ) {}

  async setup(dto: SetupDto): Promise<AuthResponse> {
    const configuredBootstrapToken =
      this.config.get<string>('BOOTSTRAP_TOKEN') ?? '';
    if (
      configuredBootstrapToken.length < 20 ||
      configuredBootstrapToken.length > 256 ||
      !this.constantTimeEqual(dto.bootstrapToken, configuredBootstrapToken)
    ) {
      throw new UnauthorizedException(
        'Setup token is invalid or setup is unavailable',
      );
    }
    const username = this.normalizeUsername(dto.username);
    const passwordHash = await argon2.hash(dto.password, PASSWORD_OPTIONS);
    const refreshToken = this.createOpaqueToken();

    let result: { identity: SessionIdentity; sessionId: string };
    try {
      result = await this.prisma.$transaction(
        async (tx) => {
          await lockBootstrap(tx);
          if (await isServerBootstrapped(tx)) {
            throw new ConflictException(
              'Server setup has already been completed',
            );
          }
          const household = await tx.household.create({
            data: { name: this.householdName(dto.preferredLanguage) },
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
          const session = await this.persistSession(
            tx,
            user.id,
            dto.deviceName,
            refreshToken,
          );
          await tx.auditEvent.create({
            data: {
              householdId: household.id,
              actorUserId: user.id,
              type: AuditEventType.NEW_DEVICE,
              metadata: { sessionId: session.id },
            },
          });
          if (!(await markServerBootstrapped(tx))) {
            throw new ConflictException(
              'Server setup has already been completed',
            );
          }
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
      );
    } catch (error) {
      if (error instanceof ConflictException) throw error;
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2002'
      ) {
        throw new ConflictException('Server setup has already been completed');
      }
      throw error;
    }
    return this.buildAuthResponse(
      result.identity,
      result.sessionId,
      refreshToken,
    );
  }

  async register(dto: RegisterDto): Promise<AuthResponse> {
    if (!this.config.get<boolean>('lunara.registrationEnabled', false)) {
      throw new ForbiddenException('Registration is disabled');
    }
    if (!(await isServerBootstrapped(this.prisma))) {
      throw new ConflictException(
        'Server bootstrap must be completed before registration',
      );
    }
    const username = this.normalizeUsername(dto.username);
    const passwordHash = await argon2.hash(dto.password, PASSWORD_OPTIONS);
    const refreshToken = this.createOpaqueToken();

    let result: { identity: SessionIdentity; sessionId: string };
    try {
      result = await this.prisma.$transaction(
        async (tx) => {
          await lockBootstrap(tx);
          if (!(await isServerBootstrapped(tx))) {
            throw new ConflictException(
              'Server bootstrap must be completed before registration',
            );
          }
          const household = await tx.household.create({
            data: { name: this.householdName(dto.preferredLanguage) },
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
          const session = await this.persistSession(
            tx,
            user.id,
            dto.deviceName,
            refreshToken,
          );
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
      );
    } catch (error) {
      if (error instanceof ConflictException) throw error;
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2002'
      ) {
        throw new ConflictException('Username is already in use');
      }
      throw error;
    }
    return this.buildAuthResponse(
      result.identity,
      result.sessionId,
      refreshToken,
    );
  }

  async login(dto: LoginDto): Promise<AuthResponse> {
    const username = this.normalizeUsername(dto.username);
    const user = await this.prisma.user.findUnique({
      where: { username },
      include: { memberships: { take: 1, orderBy: { createdAt: 'asc' } } },
    });
    const passwordMatches = await verifyAccountPassword(
      user && !user.disabledAt ? user.passwordHash : null,
      dto.password,
    );
    const valid = Boolean(user && !user.disabledAt && passwordMatches);
    if (!valid || !user || !user.memberships[0]) {
      await this.prisma.auditEvent.create({
        data: { type: AuditEventType.LOGIN_FAILED, metadata: {} },
      });
      throw new UnauthorizedException('Invalid username or password');
    }

    const refreshToken = this.createOpaqueToken();
    const result = await this.prisma.$transaction(async (tx) => {
      await lockUserAuthentication(tx, user.id);
      // Password verification is intentionally done before taking the database
      // lock. Rechecking the exact Argon2 hash under the lock prevents a login
      // that verified the old password from creating a session after a password
      // change committed.
      const current = await tx.user.findUnique({
        where: { id: user.id },
        include: { memberships: { take: 1, orderBy: { createdAt: 'asc' } } },
      });
      const membership = current?.memberships[0];
      if (
        !current ||
        current.disabledAt ||
        !membership ||
        current.passwordHash !== user.passwordHash
      ) {
        await tx.auditEvent.create({
          data: { type: AuditEventType.LOGIN_FAILED, metadata: {} },
        });
        return null;
      }
      const session = await this.persistSession(
        tx,
        current.id,
        dto.deviceName,
        refreshToken,
      );
      await tx.auditEvent.createMany({
        data: [
          {
            householdId: membership.householdId,
            actorUserId: current.id,
            type: AuditEventType.LOGIN_SUCCEEDED,
            metadata: { sessionId: session.id },
          },
          {
            householdId: membership.householdId,
            actorUserId: current.id,
            type: AuditEventType.NEW_DEVICE,
            metadata: { sessionId: session.id },
          },
        ],
      });
      return { current, membership, session };
    });
    if (!result)
      throw new UnauthorizedException('Invalid username or password');
    const { current, membership, session } = result;
    return this.buildAuthResponse(
      {
        id: current.id,
        username: current.username,
        displayName: current.displayName,
        preferredLanguage: current.preferredLanguage,
        role: membership.role,
        householdId: membership.householdId,
      },
      session.id,
      refreshToken,
    );
  }

  async refresh(
    rawToken: string,
    oidcAccessToken?: string,
    refreshRequestId?: string,
  ): Promise<AuthResponse> {
    const tokenHash = this.hashToken(rawToken);
    const replacementToken = this.createOpaqueToken();
    const replayKey = refreshRequestId
      ? `auth-refresh:${refreshRequestId.toLowerCase()}`
      : undefined;
    const initialTokenRecord = await this.prisma.deviceSession.findUnique({
      where: { refreshTokenHash: tokenHash },
      select: {
        id: true,
        userId: true,
        authMethod: true,
        externalIdentityId: true,
        externalIdentity: { select: { issuer: true, subject: true } },
      },
    });
    if (!initialTokenRecord) {
      throw new UnauthorizedException('Invalid or expired refresh token');
    }

    let oidcProof: VerifiedOidcIdentity | undefined;
    if (initialTokenRecord.authMethod === SessionAuthMethod.OIDC) {
      if (
        !oidcAccessToken ||
        !initialTokenRecord.externalIdentityId ||
        !initialTokenRecord.externalIdentity
      ) {
        throw new UnauthorizedException('Invalid or expired refresh token');
      }
      try {
        oidcProof = await this.oidcVerifier.verify(oidcAccessToken);
      } catch {
        throw new UnauthorizedException('Invalid or expired refresh token');
      }
      if (
        oidcProof.issuer !== initialTokenRecord.externalIdentity.issuer ||
        oidcProof.subject !== initialTokenRecord.externalIdentity.subject
      ) {
        throw new UnauthorizedException('Invalid or expired refresh token');
      }
    }

    const result = await this.prisma.$transaction(async (tx) => {
      await lockUserAuthentication(tx, initialTokenRecord.userId);
      // Re-read after acquiring the per-user lock. A password change or another
      // refresh may have revoked this record while this transaction waited.
      const tokenRecord = await tx.deviceSession.findUnique({
        where: { id: initialTokenRecord.id },
        select: {
          id: true,
          tokenFamilyId: true,
          revokedAt: true,
          authMethod: true,
          externalIdentityId: true,
          externalIdentity: { select: { issuer: true, subject: true } },
        },
      });
      if (!tokenRecord)
        throw new UnauthorizedException('Invalid or expired refresh token');
      if (
        tokenRecord.authMethod !== initialTokenRecord.authMethod ||
        tokenRecord.externalIdentityId !==
          initialTokenRecord.externalIdentityId ||
        (tokenRecord.authMethod === SessionAuthMethod.OIDC &&
          (!oidcProof ||
            !tokenRecord.externalIdentity ||
            oidcProof.issuer !== tokenRecord.externalIdentity.issuer ||
            oidcProof.subject !== tokenRecord.externalIdentity.subject))
      ) {
        throw new UnauthorizedException('Invalid or expired refresh token');
      }

      if (replayKey) {
        const cached = await tx.idempotencyKey.findUnique({
          where: {
            userId_key: { userId: initialTokenRecord.userId, key: replayKey },
          },
        });
        // A known retry must never fall through to family-reuse detection,
        // even when its successor has since rotated, expired or been revoked.
        if (cached) {
          const replay = this.decryptRefreshReplay(
            cached,
            initialTokenRecord.userId,
            replayKey,
            tokenHash,
            tokenRecord.id,
          );
          const replacement = await tx.deviceSession.findUnique({
            where: { id: replay.sessionId },
            include: {
              user: {
                include: {
                  memberships: { take: 1, orderBy: { createdAt: 'asc' } },
                },
              },
              externalIdentity: { select: { issuer: true, subject: true } },
            },
          });
          const membership = replacement?.user.memberships[0];
          if (
            !replacement ||
            !membership ||
            !tokenRecord.revokedAt ||
            replacement.userId !== initialTokenRecord.userId ||
            replacement.user.disabledAt ||
            replacement.revokedAt ||
            replacement.expiresAt <= new Date() ||
            replacement.rotatedFromId !== tokenRecord.id ||
            replacement.tokenFamilyId !== tokenRecord.tokenFamilyId ||
            replacement.refreshTokenHash !==
              this.hashToken(replay.refreshToken) ||
            membership.householdId !== cached.householdId ||
            replacement.authMethod !== tokenRecord.authMethod ||
            replacement.externalIdentityId !== tokenRecord.externalIdentityId ||
            (replacement.authMethod === SessionAuthMethod.OIDC &&
              (!oidcProof ||
                !replacement.externalIdentity ||
                oidcProof.issuer !== replacement.externalIdentity.issuer ||
                oidcProof.subject !== replacement.externalIdentity.subject))
          ) {
            throw new UnauthorizedException('Invalid or expired refresh token');
          }
          return {
            reuseDetected: false as const,
            identity: {
              id: replacement.user.id,
              username: replacement.user.username,
              displayName: replacement.user.displayName,
              preferredLanguage: replacement.user.preferredLanguage,
              role: membership.role,
              householdId: membership.householdId,
            },
            sessionId: replacement.id,
            refreshToken: replay.refreshToken,
          };
        }
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
          user: {
            include: {
              memberships: { take: 1, orderBy: { createdAt: 'asc' } },
            },
          },
        },
      });
      const membership = old?.user.memberships[0];
      if (
        !old ||
        !membership ||
        old.expiresAt <= new Date() ||
        old.user.disabledAt
      ) {
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
        {
          rotatedFromId: old.id,
          tokenFamilyId: old.tokenFamilyId,
          authMethod: old.authMethod,
          externalIdentityId: old.externalIdentityId ?? undefined,
        },
      );
      if (replayKey) {
        const recordId = randomUUID();
        const expiresAt = new Date(
          Math.min(
            Date.now() + REFRESH_REPLAY_TTL_MS,
            replacement.expiresAt.getTime(),
          ),
        );
        const envelope: RefreshReplayEnvelope = {
          version: 1,
          recordId,
          userId: old.userId,
          householdId: membership.householdId,
          key: replayKey,
          method: 'POST',
          path: REFRESH_REPLAY_PATH,
          requestHash: tokenHash,
          expiresAt: expiresAt.toISOString(),
          originalSessionId: old.id,
          sessionId: replacement.id,
          refreshToken: replacementToken,
        };
        // Commit the rotation and authenticated ciphertext together. Neither a
        // refresh credential nor an access JWT is stored as plaintext JSON.
        await tx.idempotencyKey.create({
          data: {
            id: recordId,
            userId: old.userId,
            householdId: membership.householdId,
            key: replayKey,
            method: 'POST',
            path: REFRESH_REPLAY_PATH,
            requestHash: tokenHash,
            responseStatus: 201,
            responseBody: {
              encryptedRefreshReplay: this.encryption.encryptForPurpose(
                JSON.stringify(envelope),
                REFRESH_REPLAY_PURPOSE,
              ),
            },
            expiresAt,
          },
        });
      }
      if (
        old.authMethod === SessionAuthMethod.OIDC &&
        old.externalIdentityId
      ) {
        await tx.externalIdentity.update({
          where: { id: old.externalIdentityId },
          data: { lastUsedAt: new Date() },
        });
      }
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
        refreshToken: replacementToken,
      };
    });
    if (result.reuseDetected) {
      throw new UnauthorizedException(
        'Refresh token reuse detected; token family revoked',
      );
    }
    return this.buildAuthResponse(
      result.identity,
      result.sessionId,
      result.refreshToken,
    );
  }

  private decryptRefreshReplay(
    cached: IdempotencyKey,
    userId: string,
    key: string,
    requestHash: string,
    originalSessionId: string,
  ): RefreshReplayEnvelope {
    try {
      const body = cached.responseBody;
      if (
        cached.userId !== userId ||
        cached.key !== key ||
        cached.requestHash !== requestHash ||
        cached.method !== 'POST' ||
        cached.path !== REFRESH_REPLAY_PATH ||
        cached.responseStatus !== 201 ||
        cached.expiresAt <= new Date() ||
        !body ||
        typeof body !== 'object' ||
        Array.isArray(body) ||
        typeof body.encryptedRefreshReplay !== 'string'
      ) {
        throw new Error('Invalid replay binding');
      }
      const envelope: unknown = JSON.parse(
        this.encryption.decryptForPurpose(
          body.encryptedRefreshReplay,
          REFRESH_REPLAY_PURPOSE,
        ),
      );
      if (!envelope || typeof envelope !== 'object') {
        throw new Error('Invalid replay envelope');
      }
      const value = envelope as Partial<RefreshReplayEnvelope>;
      if (
        value.version !== 1 ||
        value.recordId !== cached.id ||
        value.userId !== userId ||
        value.householdId !== cached.householdId ||
        value.key !== key ||
        value.method !== cached.method ||
        value.path !== cached.path ||
        value.requestHash !== requestHash ||
        value.expiresAt !== cached.expiresAt.toISOString() ||
        value.originalSessionId !== originalSessionId ||
        typeof value.sessionId !== 'string' ||
        typeof value.refreshToken !== 'string' ||
        !/^[A-Za-z0-9_-]{64}$/u.test(value.refreshToken)
      ) {
        throw new Error('Invalid replay envelope');
      }
      return value as RefreshReplayEnvelope;
    } catch {
      throw new UnauthorizedException('Invalid or expired refresh token');
    }
  }

  async logout(user: AuthenticatedUser, rawToken?: string): Promise<void> {
    await this.revokeSessionFamily(
      user,
      rawToken
        ? { refreshTokenHash: this.hashToken(rawToken) }
        : { id: user.sessionId },
      {},
    );
  }

  async changePassword(
    user: AuthenticatedUser,
    dto: ChangePasswordDto,
  ): Promise<void> {
    const account = await this.prisma.user.findUnique({
      where: { id: user.userId },
      select: { passwordHash: true, disabledAt: true },
    });
    const currentPasswordMatches = await verifyAccountPassword(
      account && !account.disabledAt ? account.passwordHash : null,
      dto.currentPassword,
    );
    if (!account || account.disabledAt || !currentPasswordMatches) {
      await this.recordPasswordChangeFailure(user);
      throw new ForbiddenException(PASSWORD_CHANGE_FORBIDDEN_MESSAGE);
    }
    if (dto.currentPassword === dto.newPassword) {
      await this.recordPasswordChangeFailure(user);
      throw new BadRequestException(
        'New password must differ from current password',
      );
    }

    const replacementHash = await argon2.hash(
      dto.newPassword,
      PASSWORD_OPTIONS,
    );
    try {
      await this.prisma.$transaction(async (tx) => {
        await lockUserAuthentication(tx, user.userId);
        // The access-token guard may have authenticated the request immediately
        // before this device rotated its refresh token. Preserve the complete
        // token family, not only the session id embedded in that access token.
        const currentSession = await tx.deviceSession.findUnique({
          where: { id: user.sessionId },
          select: { userId: true, tokenFamilyId: true },
        });
        if (!currentSession || currentSession.userId !== user.userId) {
          throw new PasswordChangeRaceError();
        }

        const changed = await tx.user.updateMany({
          where: {
            id: user.userId,
            passwordHash: account.passwordHash,
            disabledAt: null,
          },
          data: { passwordHash: replacementHash },
        });
        if (changed.count !== 1) throw new PasswordChangeRaceError();

        const revoked = await tx.deviceSession.updateMany({
          where: {
            userId: user.userId,
            tokenFamilyId: { not: currentSession.tokenFamilyId },
            revokedAt: null,
            expiresAt: { gt: new Date() },
          },
          data: { revokedAt: new Date() },
        });
        await tx.auditEvent.create({
          data: {
            householdId: user.householdId,
            actorUserId: user.userId,
            type: AuditEventType.PASSWORD_CHANGED,
            metadata: { revokedSessionCount: revoked.count },
          },
        });
      });
    } catch (error) {
      if (!(error instanceof PasswordChangeRaceError)) throw error;
      // This audit deliberately runs after the failed transaction so its
      // rollback cannot erase the security-relevant failure event.
      await this.recordPasswordChangeFailure(user);
      throw new ForbiddenException(PASSWORD_CHANGE_FORBIDDEN_MESSAGE);
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
        authMethod: true,
      },
    });
    return sessions.map((session) => ({
      ...session,
      current: session.id === currentSessionId,
    }));
  }

  async revokeSession(
    user: AuthenticatedUser,
    sessionId: string,
  ): Promise<void> {
    await this.revokeSessionFamily(user, { id: sessionId }, { sessionId });
  }

  private async revokeSessionFamily(
    user: AuthenticatedUser,
    selector: { id: string } | { refreshTokenHash: string },
    metadata: Prisma.InputJsonObject,
  ): Promise<void> {
    await this.prisma.$transaction(async (tx) => {
      await lockUserAuthentication(tx, user.userId);
      // The client may still know only the consumed parent after a lost
      // rotation response, or its access guard may have run before rotation.
      // Resolve that exact device family under the refresh lock; never revoke
      // unrelated devices, and never let an in-flight refresh outlive logout.
      const session = await tx.deviceSession.findUnique({
        where: selector,
        select: { userId: true, tokenFamilyId: true },
      });
      if (!session || session.userId !== user.userId) return;
      const revoked = await tx.deviceSession.updateMany({
        where: {
          userId: user.userId,
          tokenFamilyId: session.tokenFamilyId,
          revokedAt: null,
        },
        data: { revokedAt: new Date() },
      });
      if (revoked.count === 0) return;
      await tx.auditEvent.create({
        data: {
          householdId: user.householdId,
          actorUserId: user.userId,
          type: AuditEventType.SESSION_REVOKED,
          metadata,
        },
      });
    });
  }

  async hashPassword(password: string): Promise<string> {
    return argon2.hash(password, PASSWORD_OPTIONS);
  }

  async createAuthenticatedSession(
    database: DatabaseClient,
    identity: SessionIdentity,
    deviceName: string,
    authentication: {
      authMethod: SessionAuthMethod;
      externalIdentityId?: string;
    } = { authMethod: SessionAuthMethod.PASSWORD },
  ): Promise<AuthResponse> {
    const refreshToken = this.createOpaqueToken();
    const session = await this.persistSession(
      database,
      identity.id,
      deviceName,
      refreshToken,
      authentication,
    );
    return this.buildAuthResponse(identity, session.id, refreshToken);
  }

  createOpaqueToken(): string {
    return randomBytes(48).toString('base64url');
  }

  hashToken(rawToken: string): string {
    return createHmac(
      'sha256',
      this.config.getOrThrow<string>('REFRESH_TOKEN_PEPPER'),
    )
      .update(rawToken, 'utf8')
      .digest('hex');
  }

  normalizeUsername(username: string): string {
    return username.trim().normalize('NFKC').toLocaleLowerCase('en-US');
  }

  private householdName(preferredLanguage?: PreferredLanguage): string {
    return preferredLanguage === PreferredLanguage.EN
      ? 'Private relationship'
      : 'Private Beziehung';
  }

  private async recordPasswordChangeFailure(
    user: AuthenticatedUser,
  ): Promise<void> {
    await this.prisma.auditEvent.create({
      data: {
        householdId: user.householdId,
        actorUserId: user.userId,
        type: AuditEventType.PASSWORD_CHANGE_FAILED,
        metadata: {},
      },
    });
  }

  private async persistSession(
    database: DatabaseClient,
    userId: string,
    deviceName: string,
    refreshToken: string,
    authentication: {
      rotatedFromId?: string;
      tokenFamilyId?: string;
      authMethod?: SessionAuthMethod;
      externalIdentityId?: string;
    } = {},
  ) {
    const ttlDays = this.config.get<number>('lunara.refreshTokenTtlDays', 30);
    return database.deviceSession.create({
      data: {
        userId,
        tokenFamilyId: authentication.tokenFamilyId ?? randomUUID(),
        deviceName: deviceName.trim(),
        refreshTokenHash: this.hashToken(refreshToken),
        expiresAt: new Date(Date.now() + ttlDays * 86_400_000),
        rotatedFromId: authentication.rotatedFromId,
        authMethod:
          authentication.authMethod ?? SessionAuthMethod.PASSWORD,
        externalIdentityId: authentication.externalIdentityId,
      },
    });
  }

  private async buildAuthResponse(
    identity: SessionIdentity,
    sessionId: string,
    refreshToken: string,
  ): Promise<AuthResponse> {
    const expiresInSeconds = this.config.get<number>(
      'lunara.accessTokenTtlSeconds',
      900,
    );
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
    return (
      leftBuffer.length === rightBuffer.length &&
      timingSafeEqual(leftBuffer, rightBuffer)
    );
  }
}
