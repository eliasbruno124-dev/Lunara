import { Injectable, NotFoundException } from '@nestjs/common';
import { PredictionConfidence, Prisma } from '@prisma/client';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { lockRelationship } from '../common/database/database-coordination.js';
import { PrismaService } from '../prisma/prisma.service.js';
import {
  calculatePrediction,
  PredictionResult,
  UnavailablePrediction,
} from './prediction.algorithm.js';
import { UpdateCycleProfileDto } from './prediction.dto.js';

@Injectable()
export class PredictionService {
  constructor(private readonly prisma: PrismaService) {}

  async getProfile(userId: string) {
    const profile = await this.prisma.cycleProfile.findUnique({ where: { userId } });
    if (!profile) throw new NotFoundException('Cycle profile not found');
    return profile;
  }

  async updateProfile(user: AuthenticatedUser, dto: UpdateCycleProfileDto) {
    const profile = await this.prisma.$transaction(async (tx) => {
      await lockRelationship(tx, user.householdId);
      const updated = await tx.cycleProfile.update({
        where: { userId: user.userId },
        data: dto,
      });
      if (dto.fertilityPredictionEnabled === false) {
        // Disabling the source estimate also revokes its sharing permission. Re-enabling the
        // estimate later must never silently restore a previously granted sensitive share.
        await tx.sharingSettings.updateMany({
          where: { ownerUserId: user.userId, householdId: user.householdId },
          data: { shareFertileWindow: false },
        });
      }
      return updated;
    });
    await this.recalculateAndCache(user.userId);
    return profile;
  }

  async calculateForUser(
    userId: string,
    transaction?: Prisma.TransactionClient,
  ): Promise<PredictionResult | UnavailablePrediction> {
    const database = transaction ?? this.prisma;
    const [profile, periods] = await Promise.all([
      database.cycleProfile.findUnique({ where: { userId } }),
      database.periodEvent.findMany({
        where: { userId, deletedAt: null },
        orderBy: { startDate: 'asc' },
        select: { startDate: true, endDate: true },
      }),
    ]);
    if (!profile) throw new NotFoundException('Cycle profile not found');
    return calculatePrediction({
      periods: periods.map((period) => ({
        startDate: this.localDate(period.startDate),
        endDate: period.endDate ? this.localDate(period.endDate) : null,
      })),
      referenceDate: this.localDateInTimeZone(new Date(), profile.timezone),
      typicalCycleLength: profile.typicalCycleLengthOptional,
      typicalPeriodLength: profile.typicalPeriodLengthOptional,
      lutealLength: profile.lutealLengthOptional,
      fertilityPredictionEnabled: profile.fertilityPredictionEnabled,
      predictionPaused: profile.predictionPaused,
      specialMode: profile.specialMode,
    });
  }

  async recalculateAndCache(
    userId: string,
    transaction?: Prisma.TransactionClient,
  ): Promise<PredictionResult | UnavailablePrediction> {
    const prediction = await this.calculateForUser(userId, transaction);
    const updateCache = async (tx: Prisma.TransactionClient): Promise<void> => {
      await tx.prediction.deleteMany({ where: { userId } });
      if (!prediction.available) return;
      await tx.prediction.create({
        data: {
          userId,
          predictedStartFrom: this.parseLocalDate(prediction.predictedStartFrom),
          predictedStartTo: this.parseLocalDate(prediction.predictedStartTo),
          predictedEndFrom: this.parseLocalDate(prediction.predictedEndFrom),
          predictedEndTo: this.parseLocalDate(prediction.predictedEndTo),
          fertileWindowFrom: prediction.fertileWindowFrom
            ? this.parseLocalDate(prediction.fertileWindowFrom)
            : null,
          fertileWindowTo: prediction.fertileWindowTo
            ? this.parseLocalDate(prediction.fertileWindowTo)
            : null,
          confidence: prediction.confidence as PredictionConfidence,
          basedOnCycleCount: prediction.basedOnCycleCount,
          calculatedAt: new Date(prediction.calculatedAt),
          algorithmVersion: prediction.algorithmVersion,
          explanation: {
            explanation: prediction.explanation,
            statistics: prediction.statistics,
          } as unknown as Prisma.InputJsonValue,
          typicalCycleLength: prediction.typicalCycleLength,
          typicalPeriodLength: prediction.typicalPeriodLength,
          variationDays: prediction.variationDays,
        },
      });
    };
    if (transaction) {
      await updateCache(transaction);
    } else {
      await this.prisma.$transaction(updateCache);
    }
    return prediction;
  }

  localDate(value: Date): string {
    return value.toISOString().slice(0, 10);
  }

  parseLocalDate(value: string): Date {
    return new Date(`${value}T00:00:00.000Z`);
  }

  private localDateInTimeZone(value: Date, timeZone: string): string {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(value);
    const part = (type: 'year' | 'month' | 'day'): string =>
      parts.find((item) => item.type === type)?.value ?? '';
    return `${part('year')}-${part('month')}-${part('day')}`;
  }
}
