import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import {
  PartnerTip,
  PreferredLanguage,
  Prisma,
  SharingSettings,
  TipCategory,
  UserRole,
} from '@prisma/client';
import { createHash } from 'node:crypto';
import { assertCurrentTrackerMembership } from '../common/database/database-coordination.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PredictionService } from '../prediction/prediction.service.js';
import { PrismaService } from '../prisma/prisma.service.js';
import {
  carePreferenceScore,
  eligibleTips,
  SelectableTip,
  TipSelectionContext,
} from './tip-filter.js';
import { TIP_CATALOG } from './tip-catalog.js';
import { applicableTipPhases } from './tip-phase.js';
import {
  CreateUserDefinedTipDto,
  TipFeedbackDto,
  UpdateUserDefinedTipDto,
} from './tips.dto.js';

@Injectable()
export class TipsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly predictions: PredictionService,
  ) {}

  async today(user: AuthenticatedUser) {
    const partnerContext = await this.partnerContext(user);
    const { trackerId, sharing, selection, date } = partnerContext;
    if (!sharing.tipsEnabled || sharing.sharingPaused) return { tip: null, date };
    await this.seedBuiltIns();
    const tips = await this.prisma.partnerTip.findMany({
      where: {
        enabled: true,
        OR: this.accessibleTipOwnership(trackerId, user.householdId),
      },
      include: { feedback: { where: { userId: user.userId } } },
      orderBy: [{ priority: 'desc' }, { id: 'asc' }],
    });
    const candidates = eligibleTips(tips, sharing, selection);
    const chosen = this.chooseWeighted(candidates, user.userId, date, selection);
    if (!chosen) return { tip: null, date };
    const german = user.preferredLanguage === PreferredLanguage.DE;
    return {
      tip: {
        id: chosen.id,
        category: chosen.category,
        title: german ? chosen.titleDe : chosen.titleEn,
        body: german ? chosen.bodyDe : chosen.bodyEn,
        callToAction: german ? chosen.callToActionDe : chosen.callToActionEn,
        applicablePhase: chosen.applicablePhase,
      },
      date,
    };
  }

  async feedback(user: AuthenticatedUser, tipId: string, dto: TipFeedbackDto) {
    const { trackerId, sharing, selection } = await this.partnerContext(user);
    const tip = await this.prisma.partnerTip.findFirst({
      where: {
        id: tipId,
        enabled: true,
        OR: this.accessibleTipOwnership(trackerId, user.householdId),
      },
      include: { feedback: { where: { userId: user.userId } } },
    });
    if (!tip || eligibleTips([tip], sharing, selection).length !== 1) {
      throw new NotFoundException('Tip not found');
    }
    return this.prisma.tipFeedback.upsert({
      where: { tipId_userId: { tipId, userId: user.userId } },
      create: {
        tipId,
        userId: user.userId,
        helpful: dto.helpful,
        dismissed: dto.dismissed ?? false,
      },
      update: {
        helpful: dto.helpful,
        dismissed: dto.dismissed,
      },
    });
  }

  async listUserDefined(user: AuthenticatedUser, includeDisabled: boolean) {
    const tips = await this.prisma.partnerTip.findMany({
      where: {
        category: TipCategory.USER_DEFINED,
        createdByUserId: user.userId,
        householdId: user.householdId,
        ...(includeDisabled ? {} : { enabled: true }),
      },
      orderBy: [{ enabled: 'desc' }, { updatedAt: 'desc' }, { id: 'asc' }],
    });
    return tips.map((tip) => this.serializeUserDefined(tip));
  }

  async createUserDefined(user: AuthenticatedUser, dto: CreateUserDefinedTipDto) {
    const tip = await this.prisma.$transaction(async (tx) => {
      await assertCurrentTrackerMembership(tx, user);
      const existing = await tx.partnerTip.findUnique({ where: { id: dto.id } });
      if (existing) {
        if (
          existing.category !== TipCategory.USER_DEFINED ||
          existing.createdByUserId !== user.userId ||
          existing.householdId !== user.householdId ||
          !this.matchesCreateRequest(existing, dto)
        ) {
          throw new ConflictException('Client identifier is already in use');
        }
        return existing;
      }
      return tx.partnerTip.create({
        data: {
          id: dto.id,
          category: TipCategory.USER_DEFINED,
          createdByUserId: user.userId,
          householdId: user.householdId,
          titleDe: dto.titleDe,
          bodyDe: dto.bodyDe,
          callToActionDe: dto.callToActionDe,
          titleEn: dto.titleEn,
          bodyEn: dto.bodyEn,
          callToActionEn: dto.callToActionEn,
          requiredSharedFields: dto.requiredSharedFields ?? [],
          applicablePhase: dto.applicablePhase,
          applicableSymptoms: dto.applicableSymptoms ?? [],
          priority: dto.priority ?? 50,
        },
      });
    });
    return this.serializeUserDefined(tip);
  }

  async updateUserDefined(
    user: AuthenticatedUser,
    id: string,
    dto: UpdateUserDefinedTipDto,
  ) {
    if (Object.keys(dto).length === 0) {
      throw new BadRequestException('At least one user-defined tip change is required');
    }
    const updated = await this.prisma.$transaction(async (tx) => {
      await assertCurrentTrackerMembership(tx, user);
      return tx.partnerTip.updateMany({
        where: {
          id,
          category: TipCategory.USER_DEFINED,
          createdByUserId: user.userId,
          householdId: user.householdId,
        },
        data: dto,
      });
    });
    if (updated.count !== 1) throw new NotFoundException('User-defined tip not found');
    const tip = await this.prisma.partnerTip.findUniqueOrThrow({ where: { id } });
    return this.serializeUserDefined(tip);
  }

  async disableUserDefined(user: AuthenticatedUser, id: string): Promise<void> {
    await this.prisma.$transaction(async (tx) => {
      await assertCurrentTrackerMembership(tx, user);
      const disabled = await tx.partnerTip.updateMany({
        where: {
          id,
          category: TipCategory.USER_DEFINED,
          createdByUserId: user.userId,
          householdId: user.householdId,
          enabled: true,
        },
        data: { enabled: false },
      });
      if (disabled.count === 1) return;
      const owned = await tx.partnerTip.count({
        where: {
          id,
          category: TipCategory.USER_DEFINED,
          createdByUserId: user.userId,
          householdId: user.householdId,
        },
      });
      if (owned === 0) throw new NotFoundException('User-defined tip not found');
      // Deactivation is intentionally idempotent.
    });
  }

  private async partnerContext(user: AuthenticatedUser): Promise<{
    trackerId: string;
    sharing: SharingSettings;
    selection: TipSelectionContext;
    date: string;
  }> {
    const tracker = await this.prisma.membership.findFirst({
      where: { householdId: user.householdId, role: UserRole.TRACKER },
      select: { userId: true },
    });
    if (!tracker) throw new NotFoundException('Tracker account not found');

    const profile = await this.prisma.cycleProfile.findUnique({
      where: { userId: tracker.userId },
      select: { timezone: true },
    });
    if (!profile) throw new NotFoundException('Tracker cycle profile not found');
    const date = new Intl.DateTimeFormat('en-CA', {
      timeZone: profile.timezone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(new Date());
    const today = new Date(`${date}T00:00:00.000Z`);
    const [sharing, carePreferences, todayLog, latestPeriod, predictionResult] = await Promise.all([
      this.prisma.sharingSettings.findUnique({
        where: {
          ownerUserId_householdId: {
            ownerUserId: tracker.userId,
            householdId: user.householdId,
          },
        },
      }),
      this.prisma.carePreferences.findUnique({ where: { userId: tracker.userId } }),
      this.prisma.dailyLog.findUnique({
        where: { userId_localDate: { userId: tracker.userId, localDate: today } },
      }),
      this.prisma.periodEvent.findFirst({
        where: { userId: tracker.userId, deletedAt: null, startDate: { lte: today } },
        orderBy: { startDate: 'desc' },
      }),
      this.predictions.calculateForUser(tracker.userId),
    ]);
    if (!sharing) throw new NotFoundException('Sharing settings not found');

    const privateToday = sharing.privateDate?.toISOString().slice(0, 10) === date;
    const sharedSymptoms =
      !privateToday &&
      sharing.shareSymptoms &&
      sharing.allowSymptomBasedTips &&
      todayLog &&
      !todayLog.deletedAt &&
      Array.isArray(todayLog.customSymptoms)
        ? todayLog.customSymptoms.filter(
            (symptom): symptom is string => typeof symptom === 'string',
          )
        : [];
    const applicablePhases = privateToday
      ? []
      : applicableTipPhases(
          date,
          latestPeriod
            ? {
                startDate: latestPeriod.startDate.toISOString().slice(0, 10),
                endDate: latestPeriod.endDate?.toISOString().slice(0, 10) ?? null,
              }
            : null,
          predictionResult.available ? predictionResult : null,
          sharing,
        );
    return {
      trackerId: tracker.userId,
      sharing,
      selection: { applicablePhases, sharedSymptoms, carePreferences },
      date,
    };
  }

  private chooseWeighted(
    candidates: SelectableTip[],
    partnerUserId: string,
    date: string,
    selection: TipSelectionContext,
  ): SelectableTip | undefined {
    if (candidates.length === 0) return undefined;
    const weighted = candidates.map((tip) => ({
      tip,
      weight:
        1 +
        Math.max(0, carePreferenceScore(tip, selection.carePreferences)) * 4 +
        Math.max(0, Math.min(4, Math.floor(tip.priority / 25))) +
        (tip.feedback.some((feedback) => feedback.helpful === true) ? 3 : 0),
    }));
    const total = weighted.reduce((sum, item) => sum + item.weight, 0);
    let slot =
      createHash('sha256')
        .update(`${partnerUserId}:${date}:lunara-tip-v2`)
        .digest()
        .readUInt32BE(0) % total;
    for (const item of weighted) {
      if (slot < item.weight) return item.tip;
      slot -= item.weight;
    }
    return weighted[weighted.length - 1]?.tip;
  }

  private accessibleTipOwnership(
    trackerId: string,
    householdId: string,
  ): Prisma.PartnerTipWhereInput[] {
    return [
      {
        category: { not: TipCategory.USER_DEFINED },
        createdByUserId: null,
        householdId: null,
      },
      {
        category: TipCategory.USER_DEFINED,
        createdByUserId: trackerId,
        householdId,
      },
    ];
  }

  private serializeUserDefined(tip: PartnerTip) {
    return {
      id: tip.id,
      category: tip.category,
      titleDe: tip.titleDe,
      bodyDe: tip.bodyDe,
      callToActionDe: tip.callToActionDe,
      titleEn: tip.titleEn,
      bodyEn: tip.bodyEn,
      callToActionEn: tip.callToActionEn,
      requiredSharedFields: tip.requiredSharedFields,
      applicablePhase: tip.applicablePhase,
      applicableSymptoms: tip.applicableSymptoms,
      priority: tip.priority,
      enabled: tip.enabled,
      createdAt: tip.createdAt,
      updatedAt: tip.updatedAt,
    };
  }

  private matchesCreateRequest(
    tip: PartnerTip,
    dto: CreateUserDefinedTipDto,
  ): boolean {
    const sameArray = (left: string[], right: string[]) =>
      left.length === right.length && left.every((item, index) => item === right[index]);
    return (
      tip.titleDe === dto.titleDe &&
      tip.bodyDe === dto.bodyDe &&
      tip.callToActionDe === dto.callToActionDe &&
      tip.titleEn === dto.titleEn &&
      tip.bodyEn === dto.bodyEn &&
      tip.callToActionEn === dto.callToActionEn &&
      sameArray(tip.requiredSharedFields, dto.requiredSharedFields ?? []) &&
      tip.applicablePhase === (dto.applicablePhase ?? null) &&
      sameArray(tip.applicableSymptoms, dto.applicableSymptoms ?? []) &&
      tip.priority === (dto.priority ?? 50)
    );
  }

  private async seedBuiltIns(): Promise<void> {
    await this.prisma.partnerTip.createMany({
      data: TIP_CATALOG.map((tip) => ({
        ...tip,
        applicableSymptoms: tip.applicableSymptoms ?? [],
      })),
      skipDuplicates: true,
    });
  }
}
