import {
  BadRequestException,
  ConflictException,
  GoneException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import {
  AuditEventType,
  PreferredLanguage,
  Prisma,
  SpecialMode,
  UserRole,
} from '@prisma/client';
import { ConfigService } from '@nestjs/config';
import { randomInt } from 'node:crypto';
import { AuthResponse, AuthService } from '../auth/auth.service.js';
import {
  assertCurrentTrackerMembership,
  lockRelationship,
  lockMembership,
  lockUserAuthentication,
} from '../common/database/database-coordination.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PrismaService } from '../prisma/prisma.service.js';
import {
  AcceptInvitationDto,
  CreateInvitationDto,
  JoinHouseholdDto,
} from './household.dto.js';

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
      where: {
        id: user.householdId,
        memberships: { some: { userId: user.userId } },
      },
      include: {
        memberships: {
          orderBy: { createdAt: 'asc' },
          include: {
            user: {
              select: {
                id: true,
                username: true,
                displayName: true,
                disabledAt: true,
              },
            },
          },
        },
      },
    });
    if (!household) throw new NotFoundException('Relationship not found');
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
      throw new ConflictException(
        'A partner is already connected to this relationship',
      );
    }
    if (dto.neverExpires && dto.expiresInMinutes !== undefined) {
      throw new BadRequestException(
        'expiresInMinutes cannot be combined with neverExpires',
      );
    }
    const codeLength = dto.neverExpires ? 12 : 10;
    const code = Array.from(
      { length: codeLength },
      () => INVITATION_ALPHABET[randomInt(INVITATION_ALPHABET.length)],
    ).join('');
    const policyMinutes = Number(
      this.config.get<string>('INVITATION_TTL_MINUTES', '30'),
    );
    const requestedMinutes = dto.expiresInMinutes ?? policyMinutes;
    if (!dto.neverExpires && requestedMinutes > policyMinutes) {
      throw new ConflictException(
        `Invitation lifetime cannot exceed ${policyMinutes} minutes`,
      );
    }
    const expiresAt = dto.neverExpires
      ? null
      : new Date(Date.now() + requestedMinutes * 60_000);
    const invitation = await this.prisma.$transaction(async (tx) => {
      await assertCurrentTrackerMembership(tx, user);
      await lockRelationship(tx, user.householdId);
      const roleOccupied = await tx.membership.count({
        where: { householdId: user.householdId, role: UserRole.PARTNER },
      });
      if (roleOccupied > 0) {
        throw new ConflictException(
          'A partner is already connected to this relationship',
        );
      }
      await tx.invitation.updateMany({
        where: {
          householdId: user.householdId,
          assignedRole: UserRole.PARTNER,
          usedAt: null,
          revokedAt: null,
        },
        data: { revokedAt: new Date() },
      });
      const created = await tx.invitation.create({
        data: {
          householdId: user.householdId,
          tokenHash: this.hashInvitation(code),
          assignedRole: UserRole.PARTNER,
          expiresAt,
          createdByUserId: user.userId,
        },
      });
      await tx.auditEvent.create({
        data: {
          householdId: user.householdId,
          actorUserId: user.userId,
          type: AuditEventType.INVITATION_CREATED,
          metadata: {
            invitationId: created.id,
            assignedRole: UserRole.PARTNER,
          },
        },
      });
      return created;
    });
    return {
      id: invitation.id,
      code,
      acceptanceUrl: this.invitationAcceptanceUrl(code),
      assignedRole: invitation.assignedRole,
      expiresAt: invitation.expiresAt,
      // The raw code is deliberately returned exactly once and is never stored.
    };
  }

  async listOpenInvitations(user: AuthenticatedUser) {
    const invitations = await this.prisma.invitation.findMany({
      where: {
        householdId: user.householdId,
        assignedRole: UserRole.PARTNER,
        usedAt: null,
        revokedAt: null,
        OR: [{ expiresAt: null }, { expiresAt: { gt: new Date() } }],
      },
      orderBy: { createdAt: 'desc' },
      select: {
        id: true,
        assignedRole: true,
        expiresAt: true,
        createdAt: true,
      },
    });
    // Invitation codes are bearer credentials. Only their hashes are stored,
    // so a code can be displayed once after creation but never recovered here.
    return invitations;
  }

  async revokeInvitation(
    user: AuthenticatedUser,
    invitationId: string,
  ): Promise<void> {
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

  /**
   * Moves an explicitly confirmed, otherwise empty standalone TRACKER account
   * into the relationship represented by an invitation. This is intentionally
   * separate from OIDC login: an identity can never change relationship merely
   * because a browser happens to hold both an Authentik session and a code.
   */
  async acceptInvitation(
    user: AuthenticatedUser,
    dto: AcceptInvitationDto,
  ): Promise<{ accepted: true }> {
    const tokenHash = this.hashInvitation(dto.invitationCode);
    const candidate = await this.prisma.invitation.findUnique({
      where: { tokenHash },
      select: { id: true, householdId: true },
    });
    if (!candidate) throw new NotFoundException('Invitation is invalid');

    await this.prisma.$transaction(async (tx) => {
      // Serialize a user-initiated relationship move with login/session
      // creation and other authentication-sensitive changes first.
      await lockUserAuthentication(tx, user.userId);
      await lockMembership(tx, user.membershipId, user.userId);

      const initialMembership = await tx.membership.findUnique({
        where: { id: user.membershipId },
        select: { id: true, userId: true, householdId: true },
      });
      if (!initialMembership || initialMembership.userId !== user.userId) {
        throw new ConflictException('Account relationship changed; sign in again');
      }

      // Always take relationship locks in a stable order to avoid reciprocal
      // acceptance deadlocks between two standalone accounts.
      for (const householdId of [
        ...new Set([initialMembership.householdId, candidate.householdId]),
      ].sort()) {
        await lockRelationship(tx, householdId);
      }

      const [session, membership, invitation] = await Promise.all([
        tx.deviceSession.findFirst({
          where: {
            id: user.sessionId,
            userId: user.userId,
            revokedAt: null,
            expiresAt: { gt: new Date() },
            user: { disabledAt: null },
          },
          select: { id: true },
        }),
        tx.membership.findUnique({
          where: { id: user.membershipId },
          select: { id: true, userId: true, householdId: true, role: true },
        }),
        tx.invitation.findUnique({
          where: { id: candidate.id },
          select: {
            id: true,
            householdId: true,
            assignedRole: true,
            usedAt: true,
            revokedAt: true,
            expiresAt: true,
          },
        }),
      ]);
      if (!session) throw new ConflictException('Session is no longer active');
      if (
        !membership ||
        membership.userId !== user.userId ||
        membership.householdId !== initialMembership.householdId
      ) {
        throw new ConflictException('Account relationship changed; sign in again');
      }
      if (!invitation) throw new NotFoundException('Invitation is invalid');

      // The successful claimant is now the sole target PARTNER. Returning the
      // same result for that account makes an interrupted client retry safe,
      // while every other account still receives the normal one-use failure.
      if (invitation.usedAt) {
        if (
          membership.householdId === invitation.householdId &&
          membership.role === UserRole.PARTNER
        ) {
          return;
        }
        throw new GoneException('Invitation has expired or is no longer available');
      }
      this.assertInvitationAvailable(invitation);
      if (invitation.assignedRole !== UserRole.PARTNER) {
        throw new ConflictException('Invitation role is not supported');
      }
      if (membership.householdId === invitation.householdId) {
        throw new ConflictException('An account cannot accept its own relationship invitation');
      }
      if (membership.role !== UserRole.TRACKER) {
        throw new ConflictException('Only an empty standalone tracker account can accept an invitation');
      }

      const [sourceMembers, partnerExists, sourceHasData] = await Promise.all([
        tx.membership.count({ where: { householdId: membership.householdId } }),
        tx.membership.count({
          where: { householdId: invitation.householdId, role: UserRole.PARTNER },
        }),
        this.sourceAccountHasData(tx, user.userId, membership.householdId),
      ]);
      if (sourceMembers !== 1) {
        throw new ConflictException('Only a standalone tracker account can accept an invitation');
      }
      if (partnerExists > 0) {
        throw new ConflictException('A partner is already connected to this relationship');
      }
      if (sourceHasData) {
        throw new ConflictException(
          'This account contains health or relationship data and cannot be moved by an invitation',
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

      // The confirmation expressly covers invalidating invitations issued by
      // the old standalone relationship. They must not remain usable after
      // its sole member has moved away.
      await tx.invitation.updateMany({
        where: {
          householdId: membership.householdId,
          usedAt: null,
          revokedAt: null,
        },
        data: { revokedAt: new Date() },
      });
      const moved = await tx.membership.updateMany({
        where: {
          id: membership.id,
          userId: user.userId,
          householdId: membership.householdId,
          role: UserRole.TRACKER,
        },
        data: {
          householdId: invitation.householdId,
          role: UserRole.PARTNER,
        },
      });
      if (moved.count !== 1) {
        throw new ConflictException('Account relationship changed; sign in again');
      }
    });
    return { accepted: true };
  }

  async join(dto: JoinHouseholdDto): Promise<AuthResponse> {
    const tokenHash = this.hashInvitation(dto.invitationCode);
    const username = this.auth.normalizeUsername(dto.username);

    // Reject invalid, expired or already-used codes before deliberately costly
    // Argon2 password hashing. The relationship lock below repeats this check
    // and performs the conditional one-time claim atomically.
    const candidate = await this.prisma.invitation.findUnique({
      where: { tokenHash },
    });
    if (!candidate) throw new NotFoundException('Invitation is invalid');
    if (
      candidate.usedAt ||
      candidate.revokedAt ||
      (candidate.expiresAt !== null && candidate.expiresAt <= new Date())
    ) {
      throw new GoneException(
        'Invitation has expired or is no longer available',
      );
    }
    const passwordHash = await this.auth.hashPassword(dto.password);

    try {
      return await this.prisma.$transaction(
        async (tx) => {
          await lockRelationship(tx, candidate.householdId);
          const invitation = await tx.invitation.findUnique({
            where: { tokenHash },
          });
          if (!invitation) throw new NotFoundException('Invitation is invalid');
          if (
            invitation.usedAt ||
            invitation.revokedAt ||
            (invitation.expiresAt !== null &&
              invitation.expiresAt <= new Date())
          ) {
            throw new GoneException(
              'Invitation has expired or is no longer available',
            );
          }
          const occupied = await tx.membership.count({
            where: {
              householdId: invitation.householdId,
              role: invitation.assignedRole,
            },
          });
          if (occupied > 0) {
            throw new ConflictException(
              `The ${invitation.assignedRole} role is already assigned`,
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
          if (claimed.count !== 1)
            throw new GoneException('Invitation is no longer available');

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

          const joinedUser = await tx.user.create({
            data: {
              username,
              displayName: dto.displayName.trim(),
              passwordHash,
              preferredLanguage: dto.preferredLanguage ?? PreferredLanguage.EN,
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
      );
    } catch (error) {
      if (
        error instanceof NotFoundException ||
        error instanceof GoneException ||
        error instanceof ConflictException
      ) {
        throw error;
      }
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2002'
      ) {
        throw new ConflictException('Username is already in use');
      }
      throw error;
    }
  }

  private hashInvitation(rawCode: string): string {
    return this.auth.hashToken(`invitation:${rawCode.trim().toUpperCase()}`);
  }

  private invitationAcceptanceUrl(code: string): string {
    const baseUrl = this.config.getOrThrow<string>('lunara.publicBaseUrl');
    const invitationUrl = new URL('/invite', baseUrl);
    // The code is a bearer credential. Keeping it in the fragment prevents it
    // from reaching reverse-proxy and application request logs.
    invitationUrl.hash = `invite=${encodeURIComponent(code)}`;
    return invitationUrl.toString();
  }

  private assertInvitationAvailable(
    invitation:
      | {
          usedAt: Date | null;
          revokedAt: Date | null;
          expiresAt: Date | null;
        }
      | null,
  ): asserts invitation is {
    usedAt: null;
    revokedAt: null;
    expiresAt: Date | null;
  } {
    if (!invitation) throw new NotFoundException('Invitation is invalid');
    if (
      invitation.revokedAt ||
      (invitation.expiresAt !== null && invitation.expiresAt <= new Date())
    ) {
      throw new GoneException('Invitation has expired or is no longer available');
    }
  }

  private async sourceAccountHasData(
    tx: Prisma.TransactionClient,
    userId: string,
    householdId: string,
  ): Promise<boolean> {
    const [
      periods,
      dailyLogs,
      predictions,
      customSymptoms,
      imports,
      createdTips,
      tipFeedback,
      sentCheckIns,
      receivedCheckIns,
      syncChanges,
      profile,
      preferences,
      sharing,
    ] = await Promise.all([
      // Deliberately include soft-deleted health records: accepting an invite
      // must never turn a relationship move into a hidden data-discard path.
      tx.periodEvent.count({ where: { userId } }),
      tx.dailyLog.count({ where: { userId } }),
      tx.prediction.count({ where: { userId } }),
      tx.customSymptom.count({ where: { userId } }),
      tx.importBatch.count({ where: { userId } }),
      tx.partnerTip.count({ where: { createdByUserId: userId } }),
      tx.tipFeedback.count({ where: { userId } }),
      tx.checkIn.count({ where: { senderUserId: userId } }),
      tx.checkIn.count({ where: { recipientUserId: userId } }),
      tx.syncChange.count({ where: { ownerUserId: userId } }),
      tx.cycleProfile.findUnique({ where: { userId } }),
      tx.carePreferences.findUnique({ where: { userId } }),
      tx.sharingSettings.findUnique({
        where: { ownerUserId_householdId: { ownerUserId: userId, householdId } },
      }),
    ]);
    if (
      periods > 0 ||
      dailyLogs > 0 ||
      predictions > 0 ||
      customSymptoms > 0 ||
      imports > 0 ||
      createdTips > 0 ||
      tipFeedback > 0 ||
      sentCheckIns > 0 ||
      receivedCheckIns > 0 ||
      syncChanges > 0
    ) {
      return true;
    }
    if (
      profile &&
      (profile.typicalCycleLengthOptional !== null ||
        profile.typicalPeriodLengthOptional !== null ||
        profile.lutealLengthOptional !== null ||
        profile.fertilityPredictionEnabled ||
        profile.predictionPaused ||
        profile.specialMode !== SpecialMode.NONE ||
        profile.timezone !== 'Europe/Berlin')
    ) {
      return true;
    }
    if (
      preferences &&
      (preferences.likesWarmth ||
        preferences.likesCuddling ||
        preferences.likesSpace ||
        preferences.likesSnacks ||
        preferences.likesConversation ||
        preferences.likesHouseholdHelp ||
        preferences.likesReminders ||
        !preferences.dislikesUnsolicitedAdvice ||
        this.nonEmptyJsonArray(preferences.customHelpfulActions) ||
        this.nonEmptyJsonArray(preferences.customUnhelpfulActions))
    ) {
      return true;
    }
    return Boolean(
      sharing &&
        (!sharing.sharePrediction ||
          sharing.sharePeriodDates ||
          sharing.sharePhase ||
          sharing.shareFlow ||
          sharing.sharePain ||
          sharing.shareMood ||
          sharing.shareEnergy ||
          sharing.shareSleep ||
          sharing.shareSymptoms ||
          sharing.shareNotes ||
          sharing.shareFertileWindow ||
          sharing.allowSymptomBasedTips ||
          !sharing.tipsEnabled ||
          !sharing.checkInsEnabled ||
          sharing.sharingPaused ||
          sharing.privateDate !== null ||
          sharing.disabledTipCategories.length > 0),
    );
  }

  private nonEmptyJsonArray(value: Prisma.JsonValue): boolean {
    return Array.isArray(value) && value.length > 0;
  }

}
