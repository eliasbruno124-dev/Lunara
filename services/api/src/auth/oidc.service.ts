import {
  ConflictException,
  ForbiddenException,
  GoneException,
  Injectable,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  AuditEventType,
  PreferredLanguage,
  Prisma,
  SessionAuthMethod,
  UserRole,
} from '@prisma/client';
import { createHash } from 'node:crypto';
import {
  isServerBootstrapped,
  lockBootstrap,
  lockOidcIdentity,
  lockRelationship,
  lockUserAuthentication,
} from '../common/database/database-coordination.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { OidcLinkDto, OidcLoginDto } from './auth.dto.js';
import {
  AuthResponse,
  AuthService,
  SessionIdentity,
  verifyAccountPassword,
} from './auth.service.js';
import {
  OidcTokenVerifier,
  VerifiedOidcIdentity,
} from './oidc-token-verifier.js';

const OIDC_LOGIN_FAILURE = 'External sign-in could not be completed';
const OIDC_LINK_FAILURE = 'External identity could not be linked';

export interface OidcStatusResponse {
  enabled: boolean;
  providerName?: string;
  linked: boolean;
  linkedAt?: Date;
  lastUsedAt?: Date;
  passwordLoginEnabled: boolean;
}

@Injectable()
export class OidcService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly verifier: OidcTokenVerifier,
    private readonly auth: AuthService,
    private readonly config: ConfigService,
  ) {}

  async login(dto: OidcLoginDto): Promise<AuthResponse> {
    const verified = await this.verifier.verify(dto.accessToken);
    let response: AuthResponse | null;
    try {
      response = await this.prisma.$transaction(async (tx) => {
        await lockOidcIdentity(tx, verified.issuer, verified.subject);
        const existing = await tx.externalIdentity.findUnique({
          where: {
            issuer_subject: {
              issuer: verified.issuer,
              subject: verified.subject,
            },
          },
          select: { id: true, userId: true },
        });
        if (existing) {
          return this.loginExisting(tx, existing.id, existing.userId, verified, dto);
        }
        if (dto.invitationCode) {
          return this.provisionPartner(tx, verified, dto);
        }
        return dto.createIfMissing
          ? this.provisionTracker(tx, verified, dto)
          : null;
      });
    } catch (error) {
      if (
        error instanceof ForbiddenException ||
        error instanceof ConflictException ||
        error instanceof NotFoundException ||
        error instanceof GoneException
      ) {
        throw error;
      }
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2002'
      ) {
        throw new ConflictException(OIDC_LOGIN_FAILURE);
      }
      throw error;
    }

    if (!response) return this.rejectLogin();
    return response;
  }

  private async loginExisting(
    tx: Prisma.TransactionClient,
    identityId: string,
    userId: string,
    verified: VerifiedOidcIdentity,
    dto: OidcLoginDto,
  ): Promise<AuthResponse | null> {
    await lockUserAuthentication(tx, userId);
    const externalIdentity = await tx.externalIdentity.findUnique({
      where: { id: identityId },
      include: {
        user: {
          include: {
            memberships: { take: 1, orderBy: { createdAt: 'asc' } },
          },
        },
      },
    });
    const membership = externalIdentity?.user.memberships[0];
    if (
      !externalIdentity ||
      externalIdentity.userId !== userId ||
      externalIdentity.issuer !== verified.issuer ||
      externalIdentity.subject !== verified.subject ||
      externalIdentity.user.disabledAt ||
      !membership
    ) {
      return null;
    }

    const identity: SessionIdentity = {
      id: externalIdentity.user.id,
      username: externalIdentity.user.username,
      displayName: externalIdentity.user.displayName,
      preferredLanguage: externalIdentity.user.preferredLanguage,
      role: membership.role,
      householdId: membership.householdId,
    };
    const response = await this.auth.createAuthenticatedSession(
      tx,
      identity,
      dto.deviceName,
      {
        authMethod: SessionAuthMethod.OIDC,
        externalIdentityId: externalIdentity.id,
      },
    );
    await Promise.all([
      tx.externalIdentity.update({
        where: { id: externalIdentity.id },
        data: { lastUsedAt: new Date() },
      }),
      this.recordOidcLogin(tx, identity, response.sessionId),
    ]);
    return response;
  }

  private async provisionTracker(
    tx: Prisma.TransactionClient,
    verified: VerifiedOidcIdentity,
    dto: OidcLoginDto,
  ): Promise<AuthResponse> {
    if (!this.config.get<boolean>('lunara.registrationEnabled', false)) {
      throw new ForbiddenException('Registration is disabled');
    }
    await lockBootstrap(tx);
    if (!(await isServerBootstrapped(tx))) {
      throw new ConflictException(
        'Server bootstrap must be completed before registration',
      );
    }

    const preferredLanguage = dto.preferredLanguage ?? PreferredLanguage.EN;
    const identityAttributes = await this.provisionedIdentityAttributes(
      tx,
      verified,
    );
    const household = await tx.household.create({
      data: { name: this.relationshipName(preferredLanguage) },
    });
    const user = await tx.user.create({
      data: {
        ...identityAttributes,
        passwordHash: null,
        preferredLanguage,
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
    const externalIdentity = await tx.externalIdentity.create({
      data: {
        userId: user.id,
        issuer: verified.issuer,
        subject: verified.subject,
        lastUsedAt: new Date(),
      },
    });
    const identity: SessionIdentity = {
      id: user.id,
      username: user.username,
      displayName: user.displayName,
      preferredLanguage: user.preferredLanguage,
      role: membership.role,
      householdId: membership.householdId,
    };
    const response = await this.auth.createAuthenticatedSession(
      tx,
      identity,
      dto.deviceName,
      {
        authMethod: SessionAuthMethod.OIDC,
        externalIdentityId: externalIdentity.id,
      },
    );
    await Promise.all([
      this.recordOidcLink(tx, identity, externalIdentity.id, 'REGISTER'),
      this.recordOidcLogin(tx, identity, response.sessionId),
    ]);
    return response;
  }

  private async provisionPartner(
    tx: Prisma.TransactionClient,
    verified: VerifiedOidcIdentity,
    dto: OidcLoginDto,
  ): Promise<AuthResponse> {
    if (!dto.invitationCode) {
      throw new NotFoundException('Invitation is invalid');
    }
    const invitationCode = dto.invitationCode.trim().toUpperCase();
    const tokenHash = this.auth.hashToken(`invitation:${invitationCode}`);
    const candidate = await tx.invitation.findUnique({
      where: { tokenHash },
      select: {
        id: true,
        householdId: true,
        assignedRole: true,
        usedAt: true,
        revokedAt: true,
        expiresAt: true,
      },
    });
    this.assertInvitationAvailable(candidate);
    await lockRelationship(tx, candidate.householdId);

    const invitation = await tx.invitation.findUnique({
      where: { id: candidate.id },
      select: {
        id: true,
        householdId: true,
        assignedRole: true,
        usedAt: true,
        revokedAt: true,
        expiresAt: true,
      },
    });
    this.assertInvitationAvailable(invitation);
    if (invitation.assignedRole !== UserRole.PARTNER) {
      throw new ConflictException('Invitation role is not supported');
    }
    const occupied = await tx.membership.count({
      where: {
        householdId: invitation.householdId,
        role: UserRole.PARTNER,
      },
    });
    if (occupied > 0) {
      throw new ConflictException(
        'A partner is already connected to this relationship',
      );
    }
    const claimed = await tx.invitation.updateMany({
      where: {
        id: invitation.id,
        usedAt: null,
        revokedAt: null,
        OR: [{ expiresAt: null }, { expiresAt: { gt: new Date() } }],
      },
      data: { usedAt: new Date() },
    });
    if (claimed.count !== 1) {
      throw new GoneException('Invitation is no longer available');
    }
    await tx.invitation.updateMany({
      where: {
        householdId: invitation.householdId,
        assignedRole: invitation.assignedRole,
        id: { not: invitation.id },
        usedAt: null,
        revokedAt: null,
      },
      data: { revokedAt: new Date() },
    });

    const preferredLanguage = dto.preferredLanguage ?? PreferredLanguage.EN;
    const identityAttributes = await this.provisionedIdentityAttributes(
      tx,
      verified,
    );
    const user = await tx.user.create({
      data: {
        ...identityAttributes,
        passwordHash: null,
        preferredLanguage,
      },
    });
    const membership = await tx.membership.create({
      data: {
        householdId: invitation.householdId,
        userId: user.id,
        role: UserRole.PARTNER,
      },
    });
    const externalIdentity = await tx.externalIdentity.create({
      data: {
        userId: user.id,
        issuer: verified.issuer,
        subject: verified.subject,
        lastUsedAt: new Date(),
      },
    });
    const identity: SessionIdentity = {
      id: user.id,
      username: user.username,
      displayName: user.displayName,
      preferredLanguage: user.preferredLanguage,
      role: membership.role,
      householdId: membership.householdId,
    };
    const response = await this.auth.createAuthenticatedSession(
      tx,
      identity,
      dto.deviceName,
      {
        authMethod: SessionAuthMethod.OIDC,
        externalIdentityId: externalIdentity.id,
      },
    );
    await Promise.all([
      this.recordOidcLink(tx, identity, externalIdentity.id, 'INVITATION'),
      this.recordOidcLogin(tx, identity, response.sessionId),
    ]);
    return response;
  }

  private assertInvitationAvailable(
    invitation:
      | {
          householdId: string;
          usedAt: Date | null;
          revokedAt: Date | null;
          expiresAt: Date | null;
        }
      | null,
  ): asserts invitation is {
    id: string;
    householdId: string;
    assignedRole: UserRole;
    usedAt: null;
    revokedAt: null;
    expiresAt: Date | null;
  } {
    if (!invitation) throw new NotFoundException('Invitation is invalid');
    if (
      invitation.usedAt ||
      invitation.revokedAt ||
      (invitation.expiresAt !== null && invitation.expiresAt <= new Date())
    ) {
      throw new GoneException(
        'Invitation has expired or is no longer available',
      );
    }
  }

  private async provisionedIdentityAttributes(
    tx: Prisma.TransactionClient,
    verified: VerifiedOidcIdentity,
  ): Promise<{ username: string; displayName: string }> {
    const digest = createHash('sha256')
      .update(`${verified.issuer.length}:${verified.issuer}:${verified.subject}`)
      .digest('hex');
    const preferredUsername = verified.preferredUsername
      ? this.auth.normalizeUsername(verified.preferredUsername)
      : undefined;
    const preferredAvailable = preferredUsername
      ? !(await tx.user.findUnique({
          where: { username: preferredUsername },
          select: { id: true },
        }))
      : false;
    const fallbackCandidates = [
      `oidc-${digest.slice(0, 32)}`,
      `oidc-${digest.slice(0, 59)}`,
    ];
    let username = preferredAvailable ? preferredUsername : undefined;
    for (const fallback of fallbackCandidates) {
      if (username) break;
      const collision = await tx.user.findUnique({
        where: { username: fallback },
        select: { id: true },
      });
      if (!collision) username = fallback;
    }
    if (!username) {
      throw new ConflictException('External account identifier is unavailable');
    }
    const displayName =
      verified.displayName ??
      verified.preferredUsername ??
      `Lunara ${digest.slice(0, 8).toUpperCase()}`;
    return { username, displayName };
  }

  private relationshipName(preferredLanguage: PreferredLanguage): string {
    return preferredLanguage === PreferredLanguage.EN
      ? 'Private relationship'
      : 'Private Beziehung';
  }

  private async recordOidcLink(
    tx: Prisma.TransactionClient,
    identity: SessionIdentity,
    externalIdentityId: string,
    enrollment: 'REGISTER' | 'INVITATION',
  ): Promise<void> {
    await tx.auditEvent.create({
      data: {
        householdId: identity.householdId,
        actorUserId: identity.id,
        type: AuditEventType.OIDC_IDENTITY_LINKED,
        metadata: {
          identityId: externalIdentityId,
          administrative: false,
          autoProvisioned: true,
          enrollment,
        },
      },
    });
  }

  private async recordOidcLogin(
    tx: Prisma.TransactionClient,
    identity: SessionIdentity,
    sessionId: string,
  ): Promise<void> {
    await tx.auditEvent.createMany({
      data: [
        {
          householdId: identity.householdId,
          actorUserId: identity.id,
          type: AuditEventType.LOGIN_SUCCEEDED,
          metadata: { sessionId, method: 'OIDC' },
        },
        {
          householdId: identity.householdId,
          actorUserId: identity.id,
          type: AuditEventType.NEW_DEVICE,
          metadata: { sessionId, method: 'OIDC' },
        },
      ],
    });
  }

  async link(
    user: AuthenticatedUser,
    dto: OidcLinkDto,
  ): Promise<OidcStatusResponse> {
    const verified = await this.verifier.verify(dto.accessToken);
    const account = await this.prisma.user.findUnique({
      where: { id: user.userId },
      select: { passwordHash: true, disabledAt: true },
    });
    const passwordMatches = await verifyAccountPassword(
      account && !account.disabledAt ? account.passwordHash : null,
      dto.currentPassword,
    );
    if (!account || account.disabledAt || !passwordMatches) {
      await this.prisma.auditEvent.create({
        data: {
          householdId: user.householdId,
          actorUserId: user.userId,
          type: AuditEventType.LOGIN_FAILED,
          metadata: { flow: 'OIDC_LINK' },
        },
      });
      throw new ForbiddenException(OIDC_LINK_FAILURE);
    }

    try {
      await this.prisma.$transaction(async (tx) => {
        await lockOidcIdentity(tx, verified.issuer, verified.subject);
        await lockUserAuthentication(tx, user.userId);
        const current = await tx.user.findUnique({
          where: { id: user.userId },
          select: {
            id: true,
            passwordHash: true,
            disabledAt: true,
            memberships: {
              where: { householdId: user.householdId },
              take: 1,
              select: { id: true },
            },
          },
        });
        if (
          !current ||
          current.disabledAt ||
          current.passwordHash !== account.passwordHash ||
          current.memberships.length !== 1
        ) {
          throw new ForbiddenException(OIDC_LINK_FAILURE);
        }

        const [bySubject, forUser] = await Promise.all([
          tx.externalIdentity.findUnique({
            where: {
              issuer_subject: {
                issuer: verified.issuer,
                subject: verified.subject,
              },
            },
          }),
          tx.externalIdentity.findUnique({
            where: {
              userId_issuer: {
                userId: user.userId,
                issuer: verified.issuer,
              },
            },
          }),
        ]);
        if (
          (bySubject && bySubject.userId !== user.userId) ||
          (forUser && forUser.subject !== verified.subject)
        ) {
          throw new ConflictException(OIDC_LINK_FAILURE);
        }
        if (bySubject && forUser) return;

        const linked = await tx.externalIdentity.create({
          data: {
            userId: user.userId,
            issuer: verified.issuer,
            subject: verified.subject,
          },
        });
        await tx.auditEvent.create({
          data: {
            householdId: user.householdId,
            actorUserId: user.userId,
            type: AuditEventType.OIDC_IDENTITY_LINKED,
            metadata: { identityId: linked.id, administrative: false },
          },
        });
      });
    } catch (error) {
      if (
        error instanceof ForbiddenException ||
        error instanceof ConflictException
      ) {
        throw error;
      }
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2002'
      ) {
        throw new ConflictException(OIDC_LINK_FAILURE);
      }
      throw error;
    }
    return this.status(user.userId);
  }

  async status(userId: string): Promise<OidcStatusResponse> {
    const configuration = this.verifier.publicConfiguration();
    const account = await this.prisma.user.findUniqueOrThrow({
      where: { id: userId },
      select: {
        passwordHash: true,
        externalIdentities: {
          where: { issuer: configuration.issuer },
          take: 1,
          select: { createdAt: true, lastUsedAt: true },
        },
      },
    });
    const linked = configuration.enabled
      ? account.externalIdentities[0]
      : undefined;
    return {
      enabled: configuration.enabled,
      ...(configuration.enabled
        ? { providerName: configuration.providerName }
        : {}),
      linked: Boolean(linked),
      ...(linked
        ? {
            linkedAt: linked.createdAt,
            ...(linked.lastUsedAt ? { lastUsedAt: linked.lastUsedAt } : {}),
          }
        : {}),
      passwordLoginEnabled: account.passwordHash !== null,
    };
  }

  private async rejectLogin(): Promise<never> {
    await this.prisma.auditEvent.create({
      data: {
        type: AuditEventType.LOGIN_FAILED,
        metadata: { method: 'OIDC' },
      },
    });
    throw new UnauthorizedException(OIDC_LOGIN_FAILURE);
  }
}
