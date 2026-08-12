import { BadRequestException, ConflictException, Injectable } from '@nestjs/common';
import { Prisma, UserRole } from '@prisma/client';
import { createHash } from 'node:crypto';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { DailyLogsService } from '../daily-logs/daily-logs.service.js';
import { CreateDailyLogDto, UpdateDailyLogDto } from '../daily-logs/daily-logs.dto.js';
import { PartnerService } from '../partner/partner.service.js';
import { CreatePeriodDto, UpdatePeriodDto } from '../periods/periods.dto.js';
import { PeriodsService } from '../periods/periods.service.js';
import { PrismaService } from '../prisma/prisma.service.js';
import {
  SyncEntityTypeDto,
  SyncMutationDto,
  SyncMutationOperationDto,
  SyncRequestDto,
} from './sync.dto.js';

@Injectable()
export class SyncService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly periods: PeriodsService,
    private readonly logs: DailyLogsService,
    private readonly partner: PartnerService,
  ) {}

  async pull(user: AuthenticatedUser, cursorValue = '0') {
    const cursor = this.parseCursor(cursorValue);
    if (user.role === UserRole.PARTNER) {
      const latest = await this.prisma.syncChange.findFirst({
        where: { householdId: user.householdId, cursor: { gt: cursor } },
        orderBy: { cursor: 'desc' },
        select: { cursor: true },
      });
      return {
        cursor: (latest?.cursor ?? cursor).toString(),
        hasMore: false,
        changes: [],
        partnerDashboard: await this.partner.dashboard(user),
      };
    }

    const changes = await this.prisma.syncChange.findMany({
      where: { householdId: user.householdId, ownerUserId: user.userId, cursor: { gt: cursor } },
      orderBy: { cursor: 'asc' },
      take: 501,
    });
    const page = changes.slice(0, 500);
    const serialized = await Promise.all(
      page.map(async (change) => ({
        cursor: change.cursor.toString(),
        entityType: change.entityType,
        entityId: change.entityId,
        operation: change.operation,
        entityVersion: change.entityVersion,
        occurredAt: change.occurredAt,
        entity: await this.loadEntity(user.userId, change.entityType, change.entityId),
      })),
    );
    return {
      cursor: (page.at(-1)?.cursor ?? cursor).toString(),
      hasMore: changes.length > 500,
      changes: serialized,
    };
  }

  async push(user: AuthenticatedUser, request: SyncRequestDto) {
    const results = [];
    for (const mutation of request.mutations) {
      results.push(await this.applyIdempotently(user, mutation));
    }
    return {
      results,
      ...(await this.pull(user, request.cursor ?? '0')),
    };
  }

  private async applyIdempotently(
    user: AuthenticatedUser,
    mutation: SyncMutationDto,
  ): Promise<Prisma.JsonObject> {
    const requestHash = createHash('sha256')
      .update(this.stableJson(mutation))
      .digest('hex');
    let reservationCreated = false;
    try {
      await this.prisma.idempotencyKey.create({
        data: {
          userId: user.userId,
          householdId: user.householdId,
          key: mutation.clientMutationId,
          method: 'POST',
          path: '/api/v1/sync',
          requestHash,
          responseStatus: 102,
          responseBody: { pending: true },
          expiresAt: new Date(Date.now() + 5 * 60_000),
        },
      });
      reservationCreated = true;
    } catch (error) {
      if (!(error instanceof Prisma.PrismaClientKnownRequestError) || error.code !== 'P2002') {
        throw error;
      }
      const stored = await this.prisma.idempotencyKey.findUniqueOrThrow({
        where: { userId_key: { userId: user.userId, key: mutation.clientMutationId } },
      });
      if (stored.expiresAt <= new Date()) {
        const removed = await this.prisma.idempotencyKey.deleteMany({
          where: {
            id: stored.id,
            expiresAt: { lte: new Date() },
          },
        });
        if (removed.count === 1) return await this.applyIdempotently(user, mutation);
        throw new ConflictException('This mutation is already being processed; retry shortly');
      }
      if (stored.requestHash !== requestHash) {
        throw new ConflictException('clientMutationId was already used for another mutation');
      }
      if (stored.responseStatus === 102) {
        throw new ConflictException('This mutation is already being processed; retry shortly');
      }
      return stored.responseBody as Prisma.JsonObject;
    }

    try {
      const response = await this.applyMutation(user, mutation);
      await this.prisma.idempotencyKey.update({
        where: { userId_key: { userId: user.userId, key: mutation.clientMutationId } },
        data: {
          responseStatus: 200,
          responseBody: response as Prisma.InputJsonValue,
          expiresAt: new Date(Date.now() + 30 * 86_400_000),
        },
      });
      return response as unknown as Prisma.JsonObject;
    } catch (error) {
      // A failed mutation must not poison its key. The same payload can safely
      // be retried; a concurrent request could never pass the unique reserve.
      if (reservationCreated) {
        await this.prisma.idempotencyKey.deleteMany({
          where: {
            userId: user.userId,
            key: mutation.clientMutationId,
            requestHash,
            responseStatus: 102,
          },
        });
      }
      throw error;
    }
  }

  private async applyMutation(user: AuthenticatedUser, mutation: SyncMutationDto) {
    const payload = mutation.payload;
    const id = this.requiredString(payload, 'id');
    if (mutation.entityType === SyncEntityTypeDto.PERIOD) {
      if (mutation.operation === SyncMutationOperationDto.DELETE) {
        await this.periods.remove(user, id, this.requiredNumber(payload, 'version'));
        return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, id, deleted: true };
      }
      if ('version' in payload) {
        const updatePayload = Object.fromEntries(
          Object.entries(payload).filter(([key]) => key !== 'id'),
        );
        const dto = await this.validatedPayload(UpdatePeriodDto, updatePayload);
        const result = await this.periods.update(user, id, dto);
        return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, entity: result };
      }
      const dto = await this.validatedPayload(CreatePeriodDto, payload);
      const result = await this.periods.create(user, dto);
      return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, entity: result };
    }
    if (mutation.entityType === SyncEntityTypeDto.DAILY_LOG) {
      if (mutation.operation === SyncMutationOperationDto.DELETE) {
        await this.logs.remove(user, id, this.requiredNumber(payload, 'version'));
        return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, id, deleted: true };
      }
      if ('version' in payload) {
        const dto = await this.validatedPayload(UpdateDailyLogDto, payload);
        const result = await this.logs.update(user, id, dto);
        return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, entity: result };
      }
      const dto = await this.validatedPayload(CreateDailyLogDto, payload);
      const result = await this.logs.create(user, dto);
      return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, entity: result };
    }
    throw new BadRequestException('Unsupported sync entity type');
  }

  private async loadEntity(userId: string, type: string, id: string) {
    if (type === 'PeriodEvent') {
      const period = await this.prisma.periodEvent.findFirst({ where: { id, userId } });
      return period
        ? {
            ...period,
            startDate: period.startDate.toISOString().slice(0, 10),
            endDate: period.endDate?.toISOString().slice(0, 10) ?? null,
          }
        : null;
    }
    if (type === 'DailyLog') {
      const log = await this.prisma.dailyLog.findFirst({ where: { id, userId } });
      return log ? this.logs.serialize(log) : null;
    }
    return null;
  }

  private parseCursor(value: string): bigint {
    if (!/^\d+$/.test(value)) throw new BadRequestException('cursor must be a non-negative integer');
    return BigInt(value);
  }

  private requiredString(object: Record<string, unknown>, key: string): string {
    const value = object[key];
    if (typeof value !== 'string') throw new BadRequestException(`${key} must be a string`);
    return value;
  }

  private requiredNumber(object: Record<string, unknown>, key: string): number {
    const value = object[key];
    if (!Number.isInteger(value) || (value as number) < 1) {
      throw new BadRequestException(`${key} must be a positive integer`);
    }
    return value as number;
  }

  private async validatedPayload<T extends object>(
    type: new () => T,
    payload: Record<string, unknown>,
  ): Promise<T> {
    const dto = plainToInstance(type, payload);
    const errors = await validate(dto, {
      whitelist: true,
      forbidNonWhitelisted: true,
      forbidUnknownValues: true,
    });
    if (errors.length > 0) {
      throw new BadRequestException('Sync mutation payload is invalid');
    }
    return dto;
  }

  private stableJson(value: unknown): string {
    if (Array.isArray(value)) return `[${value.map((item) => this.stableJson(item)).join(',')}]`;
    if (value && typeof value === 'object') {
      return `{${Object.entries(value as Record<string, unknown>)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([key, item]) => `${JSON.stringify(key)}:${this.stableJson(item)}`)
        .join(',')}}`;
    }
    return JSON.stringify(value);
  }
}
