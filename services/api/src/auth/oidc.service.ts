import {
  ConflictException,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import {
  AuditEventType,
  Prisma,
  SessionAuthMethod,
} from '@prisma/client';
import { lockUserAuthentication } from '../common/database/database-coordination.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { OidcLinkDto, OidcLoginDto } from './auth.dto.js';
import {
  AuthResponse,
  AuthService,
  SessionIdentity,
  verifyAccountPassword,
} from './auth.service.js';
import { OidcTokenVerifier } from './oidc-token-verifier.js';

const OIDC_LOGIN_FAILURE = 'External sign-in is not linked to an active account';
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
  ) {}

  async login(dto: OidcLoginDto): Promise<AuthResponse> {
    const verified = await this.verifier.verify(dto.accessToken);
    const candidate = await this.prisma.externalIdentity.findUnique({
      where: {
        issuer_subject: {
          issuer: verified.issuer,
          subject: verified.subject,
        },
      },
      select: { id: true, userId: true },
    });
    if (!candidate) return this.rejectLogin();

    const response = await this.prisma.$transaction(async (tx) => {
      await lockUserAuthentication(tx, candidate.userId);
      const externalIdentity = await tx.externalIdentity.findUnique({
        where: { id: candidate.id },
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
      const authResponse = await this.auth.createAuthenticatedSession(
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
        tx.auditEvent.createMany({
          data: [
            {
              householdId: membership.householdId,
              actorUserId: externalIdentity.user.id,
              type: AuditEventType.LOGIN_SUCCEEDED,
              metadata: {
                sessionId: authResponse.sessionId,
                method: 'OIDC',
              },
            },
            {
              householdId: membership.householdId,
              actorUserId: externalIdentity.user.id,
              type: AuditEventType.NEW_DEVICE,
              metadata: {
                sessionId: authResponse.sessionId,
                method: 'OIDC',
              },
            },
          ],
        }),
      ]);
      return authResponse;
    });

    if (!response) return this.rejectLogin();
    return response;
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
