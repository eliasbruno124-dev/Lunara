import {
  ConflictException,
  GoneException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import {
  AuditEventType,
  PreferredLanguage,
  Prisma,
  UserRole,
} from '@prisma/client';
import { ConfigService } from '@nestjs/config';
import { randomInt } from 'node:crypto';
import { AuthResponse, AuthService } from '../auth/auth.service.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { CreateInvitationDto, JoinHouseholdDto } from './household.dto.js';

const INVITATION_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

@Injectable()
export class HouseholdService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auth: AuthService,
    private readonly config: ConfigService,
  ) {}

  async getHousehold(user: AuthenticatedUser) {
    const household = await this.prisma.household.findFirst({
      where: { id: user.householdId, memberships: { some: { userId: user.userId } } },
      include: {
        memberships: {
          orderBy: { createdAt: 'asc' },
          include: { user: { select: { id: true, username: true, displayName: true, disabledAt: true } } },
        },
      },
    });
    if (!household) throw new NotFoundException('Household not found');
    return {
      id: household.id,
      name: household.name,
      createdAt: household.createdAt,
      updatedAt: household.updatedAt,
      members: household.memberships.map((membership) => ({
        membershipId: membership.id,
        userId: membership.user.id,
        username: membership.user.username,
        displayName: membership.user.displayName,
        role: membership.role,
        customRoleLabel: membership.customRoleLabel,
        disabled: membership.user.disabledAt !== null,
      })),
    };
  }

  async createInvitation(user: AuthenticatedUser, dto: CreateInvitationDto) {
    const partnerExists = await this.prisma.membership.count({
      where: { householdId: user.householdId, role: UserRole.PARTNER },
    });
    if (partnerExists > 0) {
      throw new ConflictException('The PARTNER role is already assigned in this household');
    }
    const code = Array.from({ length: 10 }, () =>
      INVITATION_ALPHABET[randomInt(INVITATION_ALPHABET.length)],
    ).join('');
    const policyMinutes = Number(this.config.get<string>('INVITATION_TTL_MINUTES', '30'));
    const requestedMinutes = dto.expiresInMinutes ?? policyMinutes;
    if (requestedMinutes > policyMinutes) {
      throw new ConflictException(`Invitation lifetime cannot exceed ${policyMinutes} minutes`);
    }
    const expiresAt = new Date(Date.now() + requestedMinutes * 60_000);
    const invitation = await this.prisma.invitation.create({
      data: {
        householdId: user.householdId,
        tokenHash: this.hashInvitation(code),
        assignedRole: UserRole.PARTNER,
        expiresAt,
        createdByUserId: user.userId,
      },
    });
    await this.prisma.auditEvent.create({
      data: {
        householdId: user.householdId,
        actorUserId: user.userId,
        type: AuditEventType.INVITATION_CREATED,
        metadata: { invitationId: invitation.id, assignedRole: UserRole.PARTNER },
      },
    });
    return {
      id: invitation.id,
      code,
      assignedRole: invitation.assignedRole,
      expiresAt: invitation.expiresAt,
      // The raw code is deliberately returned exactly once and is never stored.
    };
  }

  async revokeInvitation(user: AuthenticatedUser, invitationId: string): Promise<void> {
    const result = await this.prisma.invitation.updateMany({
      where: {
        id: invitationId,
        householdId: user.householdId,
        usedAt: null,
        revokedAt: null,
      },
      data: { revokedAt: new Date() },
    });
    if (result.count > 0) {
      await this.prisma.auditEvent.create({
        data: {
          householdId: user.householdId,
          actorUserId: user.userId,
          type: AuditEventType.INVITATION_REVOKED,
          metadata: { invitationId },
        },
      });
    }
  }

  async join(dto: JoinHouseholdDto): Promise<AuthResponse> {
    const tokenHash = this.hashInvitation(dto.invitationCode);
    const username = this.auth.normalizeUsername(dto.username);

    // Reject invalid, expired or already-used codes before deliberately costly
    // Argon2 password hashing. The serializable transaction below repeats this
    // check and performs the conditional one-time claim atomically.
    const candidate = await this.prisma.invitation.findUnique({ where: { tokenHash } });
    if (!candidate) throw new NotFoundException('Invitation is invalid');
    if (candidate.usedAt || candidate.revokedAt || candidate.expiresAt <= new Date()) {
      throw new GoneException('Invitation has expired or is no longer available');
    }
    const passwordHash = await this.auth.hashPassword(dto.password);

    try {
      return await this.prisma.$transaction(
        async (tx) => {
          const invitation = await tx.invitation.findUnique({ where: { tokenHash } });
          if (!invitation) throw new NotFoundException('Invitation is invalid');
          if (invitation.usedAt || invitation.revokedAt || invitation.expiresAt <= new Date()) {
            throw new GoneException('Invitation has expired or is no longer available');
          }
          const occupied = await tx.membership.count({
            where: { householdId: invitation.householdId, role: invitation.assignedRole },
          });
          if (occupied > 0) {
            throw new ConflictException(`The ${invitation.assignedRole} role is already assigned`);
          }
          const claimed = await tx.invitation.updateMany({
            where: { id: invitation.id, usedAt: null, revokedAt: null, expiresAt: { gt: new Date() } },
            data: { usedAt: new Date() },
          });
          if (claimed.count !== 1) throw new GoneException('Invitation is no longer available');

          const joinedUser = await tx.user.create({
            data: {
              username,
              displayName: dto.displayName.trim(),
              passwordHash,
              preferredLanguage: dto.preferredLanguage ?? PreferredLanguage.DE,
            },
          });
          const membership = await tx.membership.create({
            data: {
              householdId: invitation.householdId,
              userId: joinedUser.id,
              role: invitation.assignedRole,
            },
          });
          const response = await this.auth.createAuthenticatedSession(
            tx,
            {
              id: joinedUser.id,
              username: joinedUser.username,
              displayName: joinedUser.displayName,
              preferredLanguage: joinedUser.preferredLanguage,
              role: membership.role,
              householdId: membership.householdId,
            },
            dto.deviceName,
          );
          await tx.auditEvent.create({
            data: {
              householdId: membership.householdId,
              actorUserId: joinedUser.id,
              type: AuditEventType.NEW_DEVICE,
              metadata: { sessionId: response.sessionId },
            },
          });
          return response;
        },
        { isolationLevel: Prisma.TransactionIsolationLevel.Serializable },
      );
    } catch (error) {
      if (
        error instanceof NotFoundException ||
        error instanceof GoneException ||
        error instanceof ConflictException
      ) {
        throw error;
      }
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
        throw new ConflictException('Username is already in use');
      }
      throw error;
    }
  }

  private hashInvitation(rawCode: string): string {
    return this.auth.hashToken(`invitation:${rawCode.trim().toUpperCase()}`);
  }
}
