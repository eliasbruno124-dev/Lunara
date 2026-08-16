import { BadRequestException, ConflictException, Injectable } from '@nestjs/common';
import { Prisma, UserRole } from '@prisma/client';
import { createHash } from 'node:crypto';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { lockUserPeriods } from '../common/database/database-coordination.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { DailyLogsService } from '../daily-logs/daily-logs.service.js';
import { CreateDailyLogDto, UpdateDailyLogDto } from '../daily-logs/daily-logs.dto.js';
import { PartnerService } from '../partner/partner.service.js';
import { CreatePeriodDto, UpdatePeriodDto } from '../periods/periods.dto.js';
import { PeriodsService } from '../periods/periods.service.js';
import { PredictionService } from '../prediction/prediction.service.js';
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
    private readonly predictions: PredictionService,
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
    return this.prisma.$transaction(async (tx) => {
      const now = new Date();
      const reserved = await tx.idempotencyKey.createMany({
        data: {
          userId: user.userId,
          householdId: user.householdId,
          key: mutation.clientMutationId,
          method: 'POST',
          path: '/api/v1/sync',
          requestHash,
          responseStatus: 102,
          responseBody: { pending: true },
          expiresAt: new Date(now.getTime() + 5 * 60_000),
        },
        skipDuplicates: true,
      });

      if (reserved.count === 0) {
        const stored = await tx.idempotencyKey.findUniqueOrThrow({
          where: {
            userId_key: {
              userId: user.userId,
              key: mutation.clientMutationId,
            },
          },
        });
        const matchesCurrentRequest =
          stored.requestHash === requestHash &&
          stored.householdId === user.householdId;
        const isExpired = stored.expiresAt <= now;

        if (!isExpired) {
          if (!matchesCurrentRequest) {
            throw new ConflictException(
              'clientMutationId was already used for another mutation',
            );
          }
          if (stored.responseStatus !== 102) {
            return stored.responseBody as Prisma.JsonObject;
          }

          // Versions before 1.0.7 could commit an entity and leave a pending
          // key if prediction refresh or the following response write failed.
          // Recover only an owner-scoped exact postcondition with a matching
          // SyncChange.
          const recovered = await this.recoverCommittedMutation(
            tx,
            user,
            mutation,
          );
          if (recovered) {
            await this.completeIdempotencyKey(
              tx,
              user.userId,
              user.householdId,
              mutation.clientMutationId,
              requestHash,
              recovered,
            );
            return recovered;
          }

          throw new ConflictException(
            'This mutation is already being processed; retry shortly',
          );
        }

        // Before reusing an expired matching pending key, recover a legacy
        // mutation that may already have committed. Expired completed keys and
        // expired keys for another request are deliberately reusable.
        if (matchesCurrentRequest && stored.responseStatus === 102) {
          const recovered = await this.recoverCommittedMutation(
            tx,
            user,
            mutation,
          );
          if (recovered) {
            await this.completeIdempotencyKey(
              tx,
              user.userId,
              user.householdId,
              mutation.clientMutationId,
              requestHash,
              recovered,
            );
            return recovered;
          }
        }

        const reclaimed = await tx.idempotencyKey.updateMany({
          where: {
            id: stored.id,
            userId: user.userId,
            householdId: stored.householdId,
            requestHash: stored.requestHash,
            responseStatus: stored.responseStatus,
            expiresAt: { lte: now },
          },
          data: {
            householdId: user.householdId,
            method: 'POST',
            path: '/api/v1/sync',
            requestHash,
            responseStatus: 102,
            createdAt: now,
            expiresAt: new Date(now.getTime() + 5 * 60_000),
            responseBody: { pending: true },
          },
        });
        if (reclaimed.count !== 1) {
          throw new ConflictException(
            'This mutation is already being processed; retry shortly',
          );
        }
      }

      const response = await this.applyMutation(tx, user, mutation);
      await this.completeIdempotencyKey(
        tx,
        user.userId,
        user.householdId,
        mutation.clientMutationId,
        requestHash,
        response,
      );
      return response as unknown as Prisma.JsonObject;
    });
  }

  private async applyMutation(
    tx: Prisma.TransactionClient,
    user: AuthenticatedUser,
    mutation: SyncMutationDto,
  ) {
    const payload = mutation.payload;
    const id = this.requiredUuid(payload, 'id');
    if (mutation.entityType === SyncEntityTypeDto.PERIOD) {
      if (mutation.operation === SyncMutationOperationDto.DELETE) {
        await this.periods.remove(
          user,
          id,
          this.requiredNumber(payload, 'version'),
          tx,
        );
        return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, id, deleted: true };
      }
      if ('version' in payload) {
        const updatePayload = Object.fromEntries(
          Object.entries(payload).filter(([key]) => key !== 'id'),
        );
        const dto = await this.validatedPayload(UpdatePeriodDto, updatePayload);
        const result = await this.periods.update(user, id, dto, tx);
        return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, entity: result };
      }
      const dto = await this.validatedPayload(CreatePeriodDto, payload);
      const result = await this.periods.create(user, dto, tx);
      return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, entity: result };
    }
    if (mutation.entityType === SyncEntityTypeDto.DAILY_LOG) {
      if (mutation.operation === SyncMutationOperationDto.DELETE) {
        await this.logs.remove(
          user,
          id,
          this.requiredNumber(payload, 'version'),
          tx,
        );
        return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, id, deleted: true };
      }
      if ('version' in payload) {
        const dto = await this.validatedPayload(UpdateDailyLogDto, payload);
        const result = await this.logs.update(user, id, dto, tx);
        return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, entity: result };
      }
      const dto = await this.validatedPayload(CreateDailyLogDto, payload);
      const result = await this.logs.create(user, dto, tx);
      return { clientMutationId: mutation.clientMutationId, entityType: mutation.entityType, entity: result };
    }
    throw new BadRequestException('Unsupported sync entity type');
  }

  private async recoverCommittedMutation(
    tx: Prisma.TransactionClient,
    user: AuthenticatedUser,
    mutation: SyncMutationDto,
  ): Promise<Prisma.JsonObject | null> {
    const payload = mutation.payload;
    const id = this.requiredUuid(payload, 'id');
    if (mutation.entityType === SyncEntityTypeDto.PERIOD) {
      await lockUserPeriods(tx, user.userId);
      const period = await tx.periodEvent.findFirst({
        where: { id, userId: user.userId },
      });
      if (!period) return null;

      if (mutation.operation === SyncMutationOperationDto.DELETE) {
        const baseVersion = this.requiredNumber(payload, 'version');
        if (
          period.deletedAt === null ||
          period.version !== baseVersion + 1 ||
          !(await this.hasMatchingSyncChange(
            tx,
            user,
            'PeriodEvent',
            id,
            'DELETE',
            period.version,
          ))
        ) {
          return null;
        }
        await this.predictions.recalculateAndCache(user.userId, tx);
        return {
          clientMutationId: mutation.clientMutationId,
          entityType: mutation.entityType,
          id,
          deleted: true,
        };
      }

      if ('version' in payload) {
        const updatePayload = Object.fromEntries(
          Object.entries(payload).filter(([key]) => key !== 'id'),
        );
        const dto = await this.validatedPayload(UpdatePeriodDto, updatePayload);
        const matchesDesiredState =
          period.deletedAt === null &&
          period.version === dto.version + 1 &&
          (dto.startDate === undefined ||
            this.localDate(period.startDate) === dto.startDate) &&
          (dto.endDate === undefined ||
            (period.endDate ? this.localDate(period.endDate) : null) ===
              dto.endDate);
        if (
          !matchesDesiredState ||
          !(await this.hasMatchingSyncChange(
            tx,
            user,
            'PeriodEvent',
            id,
            'UPSERT',
            period.version,
          ))
        ) {
          return null;
        }
        await this.predictions.recalculateAndCache(user.userId, tx);
        return {
          clientMutationId: mutation.clientMutationId,
          entityType: mutation.entityType,
          entity: this.periods.serialize(period),
        } as unknown as Prisma.JsonObject;
      }

      const dto = await this.validatedPayload(CreatePeriodDto, payload);
      if (
        period.deletedAt !== null ||
        period.version !== 1 ||
        !this.periods.matchesCreateRequest(period, user.userId, dto) ||
        !(await this.hasMatchingSyncChange(
          tx,
          user,
          'PeriodEvent',
          id,
          'UPSERT',
          period.version,
        ))
      ) {
        return null;
      }
      await this.predictions.recalculateAndCache(user.userId, tx);
      return {
        clientMutationId: mutation.clientMutationId,
        entityType: mutation.entityType,
        entity: this.periods.serialize(period),
      } as unknown as Prisma.JsonObject;
    }

    if (mutation.entityType === SyncEntityTypeDto.DAILY_LOG) {
      const log = await tx.dailyLog.findFirst({
        where: { id, userId: user.userId },
      });
      if (!log) return null;

      if (mutation.operation === SyncMutationOperationDto.DELETE) {
        const baseVersion = this.requiredNumber(payload, 'version');
        if (
          log.deletedAt === null ||
          log.version !== baseVersion + 1 ||
          !(await this.hasMatchingSyncChange(
            tx,
            user,
            'DailyLog',
            id,
            'DELETE',
            log.version,
          ))
        ) {
          return null;
        }
        return {
          clientMutationId: mutation.clientMutationId,
          entityType: mutation.entityType,
          id,
          deleted: true,
        };
      }

      if ('version' in payload) {
        const dto = await this.validatedPayload(UpdateDailyLogDto, payload);
        if (
          log.deletedAt !== null ||
          log.version !== dto.version + 1 ||
          !this.logs.matchesUpdateRequest(log, dto) ||
          !(await this.hasMatchingSyncChange(
            tx,
            user,
            'DailyLog',
            id,
            'UPSERT',
            log.version,
          ))
        ) {
          return null;
        }
        return {
          clientMutationId: mutation.clientMutationId,
          entityType: mutation.entityType,
          entity: this.logs.serialize(log),
        } as unknown as Prisma.JsonObject;
      }

      const dto = await this.validatedPayload(CreateDailyLogDto, payload);
      if (
        log.deletedAt !== null ||
        log.version !== 1 ||
        !this.logs.matchesCreateRequest(log, dto) ||
        !(await this.hasMatchingSyncChange(
          tx,
          user,
          'DailyLog',
          id,
          'UPSERT',
          log.version,
        ))
      ) {
        return null;
      }
      return {
        clientMutationId: mutation.clientMutationId,
        entityType: mutation.entityType,
        entity: this.logs.serialize(log),
      } as unknown as Prisma.JsonObject;
    }
    return null;
  }

  private async hasMatchingSyncChange(
    tx: Prisma.TransactionClient,
    user: AuthenticatedUser,
    entityType: 'PeriodEvent' | 'DailyLog',
    entityId: string,
    operation: 'UPSERT' | 'DELETE',
    entityVersion: number,
  ): Promise<boolean> {
    const change = await tx.syncChange.findFirst({
      where: {
        householdId: user.householdId,
        ownerUserId: user.userId,
        entityType,
        entityId,
        operation,
        entityVersion,
      },
      select: { cursor: true },
    });
    return change !== null;
  }

  private async completeIdempotencyKey(
    tx: Prisma.TransactionClient,
    userId: string,
    householdId: string,
    key: string,
    requestHash: string,
    response: object,
  ): Promise<void> {
    const completed = await tx.idempotencyKey.updateMany({
      where: {
        userId,
        householdId,
        key,
        requestHash,
        responseStatus: 102,
      },
      data: {
        responseStatus: 200,
        responseBody: response as Prisma.InputJsonValue,
        expiresAt: new Date(Date.now() + 30 * 86_400_000),
      },
    });
    if (completed.count !== 1) {
      throw new ConflictException(
        'This mutation could not be finalized; retry shortly',
      );
    }
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

  private requiredUuid(object: Record<string, unknown>, key: string): string {
    const value = this.requiredString(object, key);
    if (
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
        value,
      )
    ) {
      throw new BadRequestException(`${key} must be a UUID`);
    }
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

  private localDate(value: Date): string {
    return value.toISOString().slice(0, 10);
  }
}
