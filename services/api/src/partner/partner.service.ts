import { Injectable, NotFoundException } from '@nestjs/common';
import { UserRole } from '@prisma/client';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { EncryptionService } from '../common/security/encryption.service.js';
import { PredictionService } from '../prediction/prediction.service.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { projectPartnerDashboard } from './partner-projection.js';
import { estimatePartnerPhase, localDateInTimeZone } from './partner-phase.js';

@Injectable()
export class PartnerService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly predictions: PredictionService,
    private readonly encryption: EncryptionService,
  ) {}

  async dashboard(user: AuthenticatedUser) {
    // The tracker id is derived exclusively from the authenticated household;
    // no owner id supplied by a client is ever accepted.
    const tracker = await this.prisma.membership.findFirst({
      where: { householdId: user.householdId, role: UserRole.TRACKER },
      select: { userId: true },
    });
    if (!tracker) throw new NotFoundException('Tracker account not found in this household');

    const profile = await this.prisma.cycleProfile.findUnique({
      where: { userId: tracker.userId },
      select: { timezone: true },
    });
    if (!profile) throw new NotFoundException('Tracker cycle profile not found');
    const today = localDateInTimeZone(new Date(), profile.timezone);
    const todayDate = new Date(`${today}T00:00:00.000Z`);
    const [settings, predictionResult, recentPeriods, todayLog] = await Promise.all([
      this.prisma.sharingSettings.findUnique({
        where: {
          ownerUserId_householdId: {
            ownerUserId: tracker.userId,
            householdId: user.householdId,
          },
        },
      }),
      this.predictions.calculateForUser(tracker.userId),
      this.prisma.periodEvent.findMany({
        where: { userId: tracker.userId, deletedAt: null },
        orderBy: { startDate: 'desc' },
        take: 6,
        select: { startDate: true, endDate: true },
      }),
      this.prisma.dailyLog.findUnique({
        where: { userId_localDate: { userId: tracker.userId, localDate: todayDate } },
      }),
    ]);
    if (!settings) throw new NotFoundException('Sharing settings not found');
    const privateToday = settings.privateDate?.toISOString().slice(0, 10) === today;
    const latestStartedPeriod = recentPeriods.find(
      (period) => period.startDate <= todayDate,
    ) ?? null;

    const prediction = predictionResult.available
      ? {
          available: true,
          predictedStartFrom: predictionResult.predictedStartFrom,
          predictedStartTo: predictionResult.predictedStartTo,
          predictedEndFrom: predictionResult.predictedEndFrom,
          predictedEndTo: predictionResult.predictedEndTo,
          fertileWindowFrom: predictionResult.fertileWindowFrom,
          fertileWindowTo: predictionResult.fertileWindowTo,
          fertilityEstimate: predictionResult.fertilityEstimate,
          confidence: predictionResult.confidence,
          basedOnCycleCount: predictionResult.basedOnCycleCount,
          variationDays: predictionResult.variationDays,
          explanation: predictionResult.explanation,
        }
      : { available: false, confidence: predictionResult.confidence, reason: predictionResult.reason };

    return projectPartnerDashboard({
      settings,
      prediction,
      phase:
        settings.sharePhase
          ? estimatePartnerPhase(
              today,
              latestStartedPeriod,
              predictionResult.available ? predictionResult : null,
              settings.shareFertileWindow,
            )
          : null,
      periodDates: recentPeriods.map((period) => ({
        startDate: period.startDate.toISOString().slice(0, 10),
        endDate: period.endDate?.toISOString().slice(0, 10) ?? null,
      })),
      todayLog: todayLog && !todayLog.deletedAt
        ? {
            localDate: today,
            flowLevel: todayLog.flowLevel,
            painLevel: todayLog.painLevel,
            mood: todayLog.mood,
            energy: todayLog.energy,
            sleepQuality: todayLog.sleepQuality,
            customSymptoms: todayLog.customSymptoms,
            note:
              settings.shareNotes && !settings.sharingPaused && !privateToday && todayLog.encryptedNote
                ? this.encryption.decrypt(todayLog.encryptedNote)
                : null,
          }
        : null,
      today,
    });
  }
}
