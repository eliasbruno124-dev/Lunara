import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PeriodEvent, SyncOperation } from '@prisma/client';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PredictionService } from '../prediction/prediction.service.js';
import { PrismaService } from '../prisma/prisma.service.js';
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

  async create(user: AuthenticatedUser, dto: CreatePeriodDto) {
    this.validateRange(dto.startDate, dto.endDate);
    const existing = await this.prisma.periodEvent.findUnique({ where: { id: dto.id } });
    if (existing) {
      if (
        existing.userId !== user.userId ||
        this.localDate(existing.startDate) !== dto.startDate ||
        (existing.endDate ? this.localDate(existing.endDate) : null) !== (dto.endDate ?? null) ||
        existing.source !== (dto.source ?? 'MANUAL')
      ) {
        throw new ConflictException('Client identifier is already in use');
      }
      return this.serialize(existing);
    }

    const period = await this.prisma.$transaction(async (tx) => {
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
      return created;
    });
    await this.predictions.recalculateAndCache(user.userId);
    return this.serialize(period);
  }

  async update(user: AuthenticatedUser, id: string, dto: UpdatePeriodDto) {
    const current = await this.prisma.periodEvent.findFirst({
      where: { id, userId: user.userId, deletedAt: null },
    });
    if (!current) throw new NotFoundException('Period entry not found');
    const startDate = dto.startDate ?? this.localDate(current.startDate);
    const endDate = dto.endDate === undefined
      ? current.endDate
        ? this.localDate(current.endDate)
        : null
      : dto.endDate;
    this.validateRange(startDate, endDate);

    const updated = await this.prisma.$transaction(async (tx) => {
      const result = await tx.periodEvent.updateMany({
        where: { id, userId: user.userId, version: dto.version, deletedAt: null },
        data: {
          ...(dto.startDate !== undefined ? { startDate: this.parseDate(dto.startDate) } : {}),
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
      return entity;
    });
    await this.predictions.recalculateAndCache(user.userId);
    return this.serialize(updated);
  }

  async remove(user: AuthenticatedUser, id: string, version: number): Promise<void> {
    const changed = await this.prisma.$transaction(async (tx) => {
      const result = await tx.periodEvent.updateMany({
        where: { id, userId: user.userId, version, deletedAt: null },
        data: { deletedAt: new Date(), version: { increment: 1 } },
      });
      if (result.count !== 1) return false;
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
      return true;
    });
    if (!changed) {
      const exists = await this.prisma.periodEvent.count({ where: { id, userId: user.userId } });
      if (exists === 0) throw new NotFoundException('Period entry not found');
      throw new ConflictException('Period entry was changed on another device');
    }
    await this.predictions.recalculateAndCache(user.userId);
  }

  private serialize(period: PeriodEvent) {
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
}
