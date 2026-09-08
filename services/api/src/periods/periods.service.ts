import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PeriodEvent, Prisma, SyncOperation } from '@prisma/client';
import {
  assertCurrentTrackerMembership,
  lockUserPeriods,
} from '../common/database/database-coordination.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PredictionService } from '../prediction/prediction.service.js';
import { PrismaService } from '../prisma/prisma.service.js';
import {
  isPeriodOverlapConstraintError,
  PERIOD_OVERLAP_MESSAGE,
} from './period-constraints.js';
import { CreatePeriodDto, UpdatePeriodDto } from './periods.dto.js';

@Injectable()
export class PeriodsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly predictions: PredictionService,
  ) {}

  async list(userId: string) {
    const periods = await this.prisma.periodEvent.findMany({
      where: { userId, deletedAt: null },
      orderBy: { startDate: 'desc' },
      take: 100,
    });
    return periods.map((period) => this.serialize(period));
  }

  async create(
    user: AuthenticatedUser,
    dto: CreatePeriodDto,
    transaction?: Prisma.TransactionClient,
  ) {
    const operation = async (tx: Prisma.TransactionClient): Promise<PeriodEvent> => {
      await assertCurrentTrackerMembership(tx, user);
      await lockUserPeriods(tx, user.userId);
      await this.validateRangeForUser(
        tx,
        user.userId,
        dto.startDate,
        dto.endDate,
      );
      const existing = await tx.periodEvent.findUnique({ where: { id: dto.id } });
      if (existing) {
        this.assertMatchingCreate(existing, user.userId, dto);
        // A matching row can be the committed half of an older sync request
        // whose prediction refresh failed. Refresh before acknowledging it.
        await this.predictions.recalculateAndCache(user.userId, tx);
        return existing;
      }

      await this.assertNoOverlap(
        tx,
        user.userId,
        dto.startDate,
        dto.endDate ?? null,
      );
      const created = await tx.periodEvent.create({
        data: {
          id: dto.id,
          userId: user.userId,
          startDate: this.parseDate(dto.startDate),
          endDate: dto.endDate ? this.parseDate(dto.endDate) : null,
          source: dto.source,
        },
      });
      await tx.syncChange.create({
        data: {
          householdId: user.householdId,
          ownerUserId: user.userId,
          entityType: 'PeriodEvent',
          entityId: created.id,
          operation: SyncOperation.UPSERT,
          entityVersion: created.version,
        },
      });
      await this.predictions.recalculateAndCache(user.userId, tx);
      return created;
    };

    try {
      const period = transaction
        ? await operation(transaction)
        : await this.prisma.$transaction(operation);
      return this.serialize(period);
    } catch (error) {
      if (this.isUniqueConstraintError(error)) {
        throw new ConflictException(
          'An active period entry already exists for this date',
        );
      }
      if (isPeriodOverlapConstraintError(error)) {
        throw new ConflictException(PERIOD_OVERLAP_MESSAGE);
      }
      throw error;
    }
  }

  async update(
    user: AuthenticatedUser,
    id: string,
    dto: UpdatePeriodDto,
    transaction?: Prisma.TransactionClient,
  ) {
    const operation = async (tx: Prisma.TransactionClient): Promise<PeriodEvent> => {
      await assertCurrentTrackerMembership(tx, user);
      await lockUserPeriods(tx, user.userId);
      const current = await tx.periodEvent.findFirst({
        where: { id, userId: user.userId, deletedAt: null },
      });
      if (!current) throw new NotFoundException('Period entry not found');
      if (current.version !== dto.version) {
        throw new ConflictException({
          message: 'Period entry was changed on another device',
          expectedVersion: dto.version,
          currentVersion: current.version,
        });
      }

      const startDate = dto.startDate ?? this.localDate(current.startDate);
      const endDate =
        dto.endDate === undefined
          ? current.endDate
            ? this.localDate(current.endDate)
            : null
          : dto.endDate;
      await this.validateRangeForUser(tx, user.userId, startDate, endDate);
      await this.assertNoOverlap(tx, user.userId, startDate, endDate, id);
      const result = await tx.periodEvent.updateMany({
        where: {
          id,
          userId: user.userId,
          version: dto.version,
          deletedAt: null,
        },
        data: {
          ...(dto.startDate !== undefined
            ? { startDate: this.parseDate(dto.startDate) }
            : {}),
          ...(dto.endDate !== undefined
            ? { endDate: dto.endDate ? this.parseDate(dto.endDate) : null }
            : {}),
          version: { increment: 1 },
        },
      });
      if (result.count !== 1) {
        throw new ConflictException({
          message: 'Period entry was changed on another device',
          expectedVersion: dto.version,
          currentVersion: current.version,
        });
      }
      const entity = await tx.periodEvent.findUniqueOrThrow({ where: { id } });
      await tx.syncChange.create({
        data: {
          householdId: user.householdId,
          ownerUserId: user.userId,
          entityType: 'PeriodEvent',
          entityId: entity.id,
          operation: SyncOperation.UPSERT,
          entityVersion: entity.version,
        },
      });
      await this.predictions.recalculateAndCache(user.userId, tx);
      return entity;
    };

    try {
      const updated = transaction
        ? await operation(transaction)
        : await this.prisma.$transaction(operation);
      return this.serialize(updated);
    } catch (error) {
      if (this.isUniqueConstraintError(error)) {
        throw new ConflictException(
          'An active period entry already exists for this date',
        );
      }
      if (isPeriodOverlapConstraintError(error)) {
        throw new ConflictException(PERIOD_OVERLAP_MESSAGE);
      }
      throw error;
    }
  }

  async remove(
    user: AuthenticatedUser,
    id: string,
    version: number,
    transaction?: Prisma.TransactionClient,
  ): Promise<void> {
    const operation = async (tx: Prisma.TransactionClient): Promise<void> => {
      await assertCurrentTrackerMembership(tx, user);
      await lockUserPeriods(tx, user.userId);
      const result = await tx.periodEvent.updateMany({
        where: { id, userId: user.userId, version, deletedAt: null },
        data: { deletedAt: new Date(), version: { increment: 1 } },
      });
      if (result.count !== 1) {
        const existing = await tx.periodEvent.findFirst({
          where: { id, userId: user.userId },
          select: { id: true },
        });
        if (!existing) throw new NotFoundException('Period entry not found');
        throw new ConflictException('Period entry was changed on another device');
      }
      await tx.syncChange.create({
        data: {
          householdId: user.householdId,
          ownerUserId: user.userId,
          entityType: 'PeriodEvent',
          entityId: id,
          operation: SyncOperation.DELETE,
          entityVersion: version + 1,
        },
      });
      await this.predictions.recalculateAndCache(user.userId, tx);
    };

    if (transaction) {
      await operation(transaction);
    } else {
      await this.prisma.$transaction(operation);
    }
  }

  serialize(period: PeriodEvent) {
    return {
      id: period.id,
      startDate: this.localDate(period.startDate),
      endDate: period.endDate ? this.localDate(period.endDate) : null,
      source: period.source,
      createdAt: period.createdAt,
      updatedAt: period.updatedAt,
      deletedAt: period.deletedAt,
      version: period.version,
    };
  }

  private validateRange(startDate: string, endDate?: string | null): void {
    const start = this.parseDate(startDate);
    if (!endDate) return;
    const end = this.parseDate(endDate);
    if (end < start) throw new BadRequestException('endDate must not be before startDate');
  }

  private async validateRangeForUser(
    database: Prisma.TransactionClient,
    userId: string,
    startDate: string,
    endDate?: string | null,
  ): Promise<void> {
    this.validateRange(startDate, endDate);
    const profile = await database.cycleProfile.findUnique({
      where: { userId },
      select: { timezone: true },
    });
    if (!profile) throw new NotFoundException('Cycle profile not found');
    const today = this.todayInTimezone(profile.timezone);
    if (startDate > today || (endDate !== null && endDate !== undefined && endDate > today)) {
      throw new BadRequestException('Period dates must not be in the future');
    }
  }

  private todayInTimezone(timezone: string): string {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: timezone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(new Date());
    const value = (type: 'year' | 'month' | 'day'): string =>
      parts.find((part) => part.type === type)?.value ?? '';
    return `${value('year')}-${value('month')}-${value('day')}`;
  }

  private parseDate(value: string): Date {
    const date = new Date(`${value}T00:00:00.000Z`);
    if (Number.isNaN(date.valueOf()) || date.toISOString().slice(0, 10) !== value) {
      throw new BadRequestException(`Invalid local date: ${value}`);
    }
    return date;
  }

  private localDate(value: Date): string {
    return value.toISOString().slice(0, 10);
  }

  matchesCreateRequest(
    period: PeriodEvent,
    userId: string,
    dto: CreatePeriodDto,
  ): boolean {
    return (
      period.userId === userId &&
      this.localDate(period.startDate) === dto.startDate &&
      (period.endDate ? this.localDate(period.endDate) : null) ===
        (dto.endDate ?? null) &&
      period.source === (dto.source ?? 'MANUAL')
    );
  }

  private assertMatchingCreate(
    period: PeriodEvent,
    userId: string,
    dto: CreatePeriodDto,
  ): void {
    if (!this.matchesCreateRequest(period, userId, dto)) {
      throw new ConflictException('Client identifier is already in use');
    }
  }

  private async assertNoOverlap(
    database: Prisma.TransactionClient,
    userId: string,
    startDate: string,
    endDate: string | null,
    excludingId?: string,
  ): Promise<void> {
    const overlap = await database.periodEvent.findFirst({
      where: {
        userId,
        deletedAt: null,
        ...(excludingId ? { id: { not: excludingId } } : {}),
        ...(endDate ? { startDate: { lte: this.parseDate(endDate) } } : {}),
        OR: [
          { endDate: null },
          { endDate: { gte: this.parseDate(startDate) } },
        ],
      },
      select: { startDate: true },
    });
    if (!overlap) return;
    if (this.localDate(overlap.startDate) === startDate) {
      throw new ConflictException(
        'An active period entry already exists for this date',
      );
    }
    throw new ConflictException(PERIOD_OVERLAP_MESSAGE);
  }

  private isUniqueConstraintError(
    error: unknown,
  ): error is Prisma.PrismaClientKnownRequestError {
    return (
      error instanceof Prisma.PrismaClientKnownRequestError &&
      error.code === 'P2002'
    );
  }

}
