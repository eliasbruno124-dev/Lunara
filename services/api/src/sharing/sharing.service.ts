import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { AuditEventType } from '@prisma/client';
import {
  assertCurrentTrackerMembership,
  lockRelationship,
} from '../common/database/database-coordination.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { UpdateSharingDto } from './sharing.dto.js';

@Injectable()
export class SharingService {
  constructor(private readonly prisma: PrismaService) {}

  async get(user: AuthenticatedUser) {
    const settings = await this.prisma.sharingSettings.findUnique({
      where: {
        ownerUserId_householdId: {
          ownerUserId: user.userId,
          householdId: user.householdId,
        },
      },
    });
    if (!settings) throw new NotFoundException('Sharing settings not found');
    return this.serialize(settings);
  }

  async update(user: AuthenticatedUser, dto: UpdateSharingDto) {
    const settings = await this.prisma.$transaction(async (tx) => {
      // Revalidate the role before taking the relationship lock. This matches
      // invitation acceptance's membership -> relationship order.
      await assertCurrentTrackerMembership(tx, user);
      // Serialize this consent transition with cycle-profile changes. Without the shared lock,
      // a concurrent "enable sharing" and "disable estimate" request could commit in the wrong
      // order and silently restore a sensitive permission.
      await lockRelationship(tx, user.householdId);
      if (dto.shareFertileWindow === true) {
        const profile = await tx.cycleProfile.findUnique({
          where: { userId: user.userId },
          select: { fertilityPredictionEnabled: true },
        });
        if (!profile?.fertilityPredictionEnabled) {
          throw new ConflictException(
            'Fertility prediction must be explicitly enabled before it can be shared',
          );
        }
      }
      const updated = await tx.sharingSettings.update({
        where: {
          ownerUserId_householdId: {
            ownerUserId: user.userId,
            householdId: user.householdId,
          },
        },
        data: {
          ...dto,
          privateDate:
            dto.privateDate === undefined
              ? undefined
              : dto.privateDate
                ? new Date(`${dto.privateDate}T00:00:00.000Z`)
                : null,
        },
      });
      await tx.auditEvent.create({
        data: {
          householdId: user.householdId,
          actorUserId: user.userId,
          type: AuditEventType.SHARING_CHANGED,
          metadata: { changedFields: Object.keys(dto).sort() },
        },
      });
      return updated;
    });
    return this.serialize(settings);
  }

  private serialize<T extends { privateDate: Date | null }>(settings: T) {
    return {
      ...settings,
      privateDate: settings.privateDate?.toISOString().slice(0, 10) ?? null,
    };
  }
}
