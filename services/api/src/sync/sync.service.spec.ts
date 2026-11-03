import { ConflictException } from '@nestjs/common';
import { PeriodSource, PreferredLanguage, UserRole } from '@prisma/client';
import { createHash } from 'node:crypto';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import {
  SyncEntityTypeDto,
  SyncMutationDto,
  SyncMutationOperationDto,
} from './sync.dto.js';
import { SyncService } from './sync.service.js';

const tracker: AuthenticatedUser = {
  userId: 'tracker-id',
  sessionId: 'session-id',
  householdId: 'household-id',
  membershipId: 'membership-id',
  role: UserRole.TRACKER,
  username: 'tracker',
  displayName: 'Tracker',
  preferredLanguage: PreferredLanguage.DE,
};

const periodId = '64237264-8026-4f1e-8b0e-f426974037e0';
const mutationId = '4ddc0c67-eb3c-4496-8710-df0270e11443';

describe('SyncService atomic mutation receipts', () => {
  it('pulls an old unconfirmed period as raw null without changing version, timestamp or owner scope', async () => {
    const period = { ...storedPeriod(1, null), endDate: null };
    const prisma = {
      syncChange: { findMany: jest.fn().mockResolvedValue([{
        cursor: 1n, entityType: 'PeriodEvent', entityId: period.id,
        operation: 'UPSERT', entityVersion: 1, occurredAt: period.updatedAt,
      }]) },
      periodEvent: { findFirst: jest.fn().mockResolvedValue(period), updateMany: jest.fn() },
    };
    const result = await syncService(prisma).pull(tracker, '0');
    expect(result.changes).toMatchObject([{ entity: { startDate: '2026-07-01', endDate: null, version: 1, updatedAt: period.updatedAt } }]);
    expect(prisma.periodEvent.findFirst).toHaveBeenCalledWith({ where: { id: period.id, userId: tracker.userId } });
    expect(prisma.syncChange.findMany).toHaveBeenCalledWith(expect.objectContaining({
      where: { householdId: tracker.householdId, ownerUserId: tracker.userId, cursor: { gt: 0n } },
    }));
    expect(prisma.periodEvent.updateMany).not.toHaveBeenCalled();
    expect(period.endDate).toBeNull();
  });

  it('writes a new period and its idempotency result through the same transaction client', async () => {
    const tx = {
      idempotencyKey: {
        createMany: jest.fn().mockResolvedValue({ count: 1 }),
        updateMany: jest.fn().mockResolvedValue({ count: 1 }),
      },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = {
      create: jest.fn().mockResolvedValue({
        id: periodId,
        startDate: '2026-07-01',
        endDate: '2026-07-05',
        version: 1,
      }),
    };
    const service = syncService(prisma, periods);
    const mutation = periodCreateMutation();

    await expect(invokeApply(service, mutation)).resolves.toMatchObject({
      clientMutationId: mutationId,
      entityType: SyncEntityTypeDto.PERIOD,
      entity: { id: periodId, version: 1 },
    });
    expect(periods.create).toHaveBeenCalledWith(
      tracker,
      expect.objectContaining({ id: periodId }),
      tx,
    );
    expect(tx.idempotencyKey.updateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          userId: tracker.userId,
          householdId: tracker.householdId,
          key: mutationId,
          responseStatus: 102,
        }) as unknown,
        data: expect.objectContaining({ responseStatus: 200 }) as unknown,
      }),
    );
  });

  it('returns the stored success after the HTTP response was lost without applying twice', async () => {
    const mutation = periodCreateMutation();
    const response = {
      clientMutationId: mutationId,
      entityType: SyncEntityTypeDto.PERIOD,
      entity: { id: periodId, version: 1 },
    };
    const tx = {
      idempotencyKey: {
        createMany: jest.fn().mockResolvedValue({ count: 0 }),
        findUniqueOrThrow: jest.fn().mockResolvedValue({
          id: 'receipt-id',
          userId: tracker.userId,
          householdId: tracker.householdId,
          requestHash: requestHash(mutation),
          responseStatus: 200,
          responseBody: response,
          expiresAt: new Date(Date.now() + 60_000),
        }),
        updateMany: jest.fn(),
      },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = { create: jest.fn() };
    const service = syncService(prisma, periods);

    await expect(invokeApply(service, mutation)).resolves.toEqual(response);
    expect(periods.create).not.toHaveBeenCalled();
    expect(tx.idempotencyKey.updateMany).not.toHaveBeenCalled();
  });

  it('reclaims an expired pending key that belonged to another request', async () => {
    const mutation = periodCreateMutation();
    const tx = {
      idempotencyKey: {
        createMany: jest.fn().mockResolvedValue({ count: 0 }),
        findUniqueOrThrow: jest.fn().mockResolvedValue({
          id: 'expired-receipt-id',
          userId: tracker.userId,
          householdId: tracker.householdId,
          requestHash: '0'.repeat(64),
          responseStatus: 102,
          responseBody: { pending: true },
          expiresAt: new Date(Date.now() - 60_000),
        }),
        updateMany: jest.fn().mockResolvedValue({ count: 1 }),
      },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = {
      create: jest.fn().mockResolvedValue({ id: periodId, version: 1 }),
    };
    const service = syncService(prisma, periods);

    await expect(invokeApply(service, mutation)).resolves.toMatchObject({
      entity: { id: periodId, version: 1 },
    });
    expect(periods.create).toHaveBeenCalledTimes(1);
    expect(tx.idempotencyKey.updateMany).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        where: expect.objectContaining({
          id: 'expired-receipt-id',
          requestHash: '0'.repeat(64),
          responseStatus: 102,
          expiresAt: { lte: expect.any(Date) as unknown },
        }) as unknown,
        data: expect.objectContaining({
          householdId: tracker.householdId,
          requestHash: requestHash(mutation),
          responseStatus: 102,
          responseBody: { pending: true },
        }) as unknown,
      }),
    );
    expect(tx.idempotencyKey.updateMany).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        data: expect.objectContaining({ responseStatus: 200 }) as unknown,
      }),
    );
  });

  it('reuses an expired completed key instead of returning its stale response', async () => {
    const mutation = periodCreateMutation();
    const tx = {
      idempotencyKey: {
        createMany: jest.fn().mockResolvedValue({ count: 0 }),
        findUniqueOrThrow: jest.fn().mockResolvedValue({
          id: 'expired-completed-id',
          userId: tracker.userId,
          householdId: tracker.householdId,
          requestHash: requestHash(mutation),
          responseStatus: 200,
          responseBody: { stale: true },
          expiresAt: new Date(Date.now() - 60_000),
        }),
        updateMany: jest.fn().mockResolvedValue({ count: 1 }),
      },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = {
      create: jest.fn().mockResolvedValue({ id: periodId, version: 1 }),
    };
    const service = syncService(prisma, periods);

    await expect(invokeApply(service, mutation)).resolves.toMatchObject({
      entity: { id: periodId, version: 1 },
    });
    expect(periods.create).toHaveBeenCalledTimes(1);
    expect(tx.idempotencyKey.updateMany).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        where: expect.objectContaining({ responseStatus: 200 }) as unknown,
        data: expect.objectContaining({ responseStatus: 102 }) as unknown,
      }),
    );
  });

  it('rejects another request while its idempotency key is still active', async () => {
    const mutation = periodCreateMutation();
    const tx = {
      idempotencyKey: {
        createMany: jest.fn().mockResolvedValue({ count: 0 }),
        findUniqueOrThrow: jest.fn().mockResolvedValue({
          id: 'active-receipt-id',
          userId: tracker.userId,
          householdId: tracker.householdId,
          requestHash: '0'.repeat(64),
          responseStatus: 102,
          responseBody: { pending: true },
          expiresAt: new Date(Date.now() + 60_000),
        }),
        updateMany: jest.fn(),
      },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = { create: jest.fn() };
    const service = syncService(prisma, periods);

    await expect(invokeApply(service, mutation)).rejects.toThrow(
      'clientMutationId was already used for another mutation',
    );
    expect(periods.create).not.toHaveBeenCalled();
    expect(tx.idempotencyKey.updateMany).not.toHaveBeenCalled();
  });

  it('recovers an expired matching legacy mutation before reclaiming its key', async () => {
    const mutation = periodCreateMutation();
    const period = storedPeriod(1, null);
    const tx = legacyPendingTransaction(mutation, {
      periodEvent: { findFirst: jest.fn().mockResolvedValue(period) },
    });
    tx.idempotencyKey.findUniqueOrThrow.mockResolvedValue({
      id: 'expired-legacy-id',
      userId: tracker.userId,
      householdId: tracker.householdId,
      requestHash: requestHash(mutation),
      responseStatus: 102,
      responseBody: { pending: true },
      expiresAt: new Date(Date.now() - 60_000),
    });
    tx.syncChange = { findFirst: jest.fn().mockResolvedValue({ cursor: 14n }) };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = {
      matchesCreateRequest: jest.fn().mockReturnValue(true),
      serialize: jest.fn().mockReturnValue({ id: periodId, version: 1 }),
      create: jest.fn(),
    };
    const predictions = { recalculateAndCache: jest.fn().mockResolvedValue({}) };
    const service = syncService(prisma, periods, {}, predictions);

    await expect(invokeApply(service, mutation)).resolves.toMatchObject({
      entity: { id: periodId, version: 1 },
    });
    expect(periods.create).not.toHaveBeenCalled();
    expect(tx.idempotencyKey.updateMany).toHaveBeenCalledTimes(1);
    expect(tx.idempotencyKey.updateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ responseStatus: 200 }) as unknown,
      }),
    );
  });

  it('replaces an expired receipt from an old household without attempting cross-household recovery', async () => {
    const mutation = periodCreateMutation();
    const tx = {
      idempotencyKey: {
        createMany: jest.fn().mockResolvedValue({ count: 0 }),
        findUniqueOrThrow: jest.fn().mockResolvedValue({
          id: 'old-household-receipt-id',
          userId: tracker.userId,
          householdId: 'old-household-id',
          requestHash: requestHash(mutation),
          responseStatus: 102,
          responseBody: { pending: true },
          expiresAt: new Date(Date.now() - 60_000),
        }),
        updateMany: jest.fn().mockResolvedValue({ count: 1 }),
      },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = {
      create: jest.fn().mockResolvedValue({ id: periodId, version: 1 }),
    };
    const service = syncService(prisma, periods);

    await expect(invokeApply(service, mutation)).resolves.toMatchObject({
      entity: { id: periodId, version: 1 },
    });
    expect(periods.create).toHaveBeenCalledTimes(1);
    expect(tx.idempotencyKey.updateMany).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        where: expect.objectContaining({
          householdId: 'old-household-id',
        }) as unknown,
        data: expect.objectContaining({
          householdId: tracker.householdId,
          createdAt: expect.any(Date) as unknown,
        }) as unknown,
      }),
    );
  });

  it('does not apply a mutation when another request wins the expired-key reclaim race', async () => {
    const mutation = periodCreateMutation();
    const tx = {
      idempotencyKey: {
        createMany: jest.fn().mockResolvedValue({ count: 0 }),
        findUniqueOrThrow: jest.fn().mockResolvedValue({
          id: 'raced-receipt-id',
          userId: tracker.userId,
          householdId: tracker.householdId,
          requestHash: '0'.repeat(64),
          responseStatus: 102,
          responseBody: { pending: true },
          expiresAt: new Date(Date.now() - 60_000),
        }),
        updateMany: jest.fn().mockResolvedValue({ count: 0 }),
      },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = { create: jest.fn() };
    const service = syncService(prisma, periods);

    await expect(invokeApply(service, mutation)).rejects.toThrow(
      'This mutation is already being processed; retry shortly',
    );
    expect(periods.create).not.toHaveBeenCalled();
    expect(tx.idempotencyKey.updateMany).toHaveBeenCalledTimes(1);
  });

  it('reclaims an expired matching pending key when no committed entity exists', async () => {
    const mutation = periodCreateMutation();
    const tx = legacyPendingTransaction(mutation, {
      periodEvent: { findFirst: jest.fn().mockResolvedValue(null) },
    });
    tx.idempotencyKey.findUniqueOrThrow.mockResolvedValue({
      id: 'expired-uncommitted-id',
      userId: tracker.userId,
      householdId: tracker.householdId,
      requestHash: requestHash(mutation),
      responseStatus: 102,
      responseBody: { pending: true },
      expiresAt: new Date(Date.now() - 60_000),
    });
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = {
      create: jest.fn().mockResolvedValue({ id: periodId, version: 1 }),
    };
    const service = syncService(prisma, periods);

    await expect(invokeApply(service, mutation)).resolves.toMatchObject({
      entity: { id: periodId, version: 1 },
    });
    expect(tx.periodEvent.findFirst).toHaveBeenCalledWith({
      where: { id: periodId, userId: tracker.userId },
    });
    expect(periods.create).toHaveBeenCalledTimes(1);
    expect(tx.idempotencyKey.updateMany).toHaveBeenCalledTimes(2);
  });

  it('retries a failed prediction refresh before completing a legacy pending period create', async () => {
    const mutation = periodCreateMutation();
    const period = storedPeriod(1, null);
    const tx = legacyPendingTransaction(mutation, {
      periodEvent: { findFirst: jest.fn().mockResolvedValue(period) },
    });
    tx.syncChange = { findFirst: jest.fn().mockResolvedValue({ cursor: 9n }) };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = {
      matchesCreateRequest: jest.fn().mockReturnValue(true),
      serialize: jest.fn().mockReturnValue({
        id: periodId,
        startDate: '2026-07-01',
        endDate: '2026-07-05',
        version: 1,
      }),
      create: jest.fn(),
    };
    const predictions = {
      recalculateAndCache: jest
        .fn()
        .mockRejectedValueOnce(new Error('cache temporarily unavailable'))
        .mockResolvedValueOnce({}),
    };
    const service = syncService(prisma, periods, {}, predictions);

    await expect(invokeApply(service, mutation)).rejects.toThrow(
      'cache temporarily unavailable',
    );
    expect(tx.idempotencyKey.updateMany).not.toHaveBeenCalled();

    await expect(invokeApply(service, mutation)).resolves.toMatchObject({
      entity: { id: periodId, version: 1 },
    });
    expect(predictions.recalculateAndCache).toHaveBeenLastCalledWith(
      tracker.userId,
      tx,
    );
    expect(tx.idempotencyKey.updateMany).toHaveBeenCalledTimes(1);
    expect(periods.create).not.toHaveBeenCalled();
  });

  it('recovers an already committed period update only for its exact owner-scoped postcondition', async () => {
    const mutation: SyncMutationDto = {
      clientMutationId: mutationId,
      entityType: SyncEntityTypeDto.PERIOD,
      operation: SyncMutationOperationDto.UPSERT,
      payload: {
        id: periodId,
        version: 1,
        startDate: '2026-07-01',
        endDate: '2026-07-06',
      },
    };
    const period = {
      ...storedPeriod(2, null),
      endDate: new Date('2026-07-06T00:00:00.000Z'),
    };
    const tx = legacyPendingTransaction(mutation, {
      periodEvent: { findFirst: jest.fn().mockResolvedValue(period) },
    });
    tx.syncChange = { findFirst: jest.fn().mockResolvedValue({ cursor: 10n }) };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = {
      serialize: jest.fn().mockReturnValue({ id: periodId, version: 2 }),
      update: jest.fn(),
    };
    const predictions = { recalculateAndCache: jest.fn().mockResolvedValue({}) };
    const service = syncService(prisma, periods, {}, predictions);

    await expect(invokeApply(service, mutation)).resolves.toMatchObject({
      entity: { id: periodId, version: 2 },
    });
    expect(tx.periodEvent.findFirst).toHaveBeenCalledWith({
      where: { id: periodId, userId: tracker.userId },
    });
    expect(tx.syncChange.findFirst).toHaveBeenCalledWith({
      where: expect.objectContaining({
        householdId: tracker.householdId,
        ownerUserId: tracker.userId,
        entityId: periodId,
        entityVersion: 2,
      }) as unknown,
      select: { cursor: true },
    });
    expect(periods.update).not.toHaveBeenCalled();
  });

  it('recovers a committed period delete and refreshes its prediction before acknowledgment', async () => {
    const mutation: SyncMutationDto = {
      clientMutationId: mutationId,
      entityType: SyncEntityTypeDto.PERIOD,
      operation: SyncMutationOperationDto.DELETE,
      payload: { id: periodId, version: 2 },
    };
    const period = storedPeriod(3, new Date('2026-08-16T12:00:00.000Z'));
    const tx = legacyPendingTransaction(mutation, {
      periodEvent: { findFirst: jest.fn().mockResolvedValue(period) },
    });
    tx.syncChange = { findFirst: jest.fn().mockResolvedValue({ cursor: 12n }) };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const periods = { remove: jest.fn() };
    const predictions = { recalculateAndCache: jest.fn().mockResolvedValue({}) };
    const service = syncService(prisma, periods, {}, predictions);

    await expect(invokeApply(service, mutation)).resolves.toMatchObject({
      id: periodId,
      deleted: true,
    });
    expect(predictions.recalculateAndCache).toHaveBeenCalledWith(
      tracker.userId,
      tx,
    );
    expect(periods.remove).not.toHaveBeenCalled();
  });

  it('recovers an already committed daily-log update from its desired end state', async () => {
    const mutation: SyncMutationDto = {
      clientMutationId: mutationId,
      entityType: SyncEntityTypeDto.DAILY_LOG,
      operation: SyncMutationOperationDto.UPSERT,
      payload: {
        id: periodId,
        version: 4,
        localDate: '2026-08-15',
        painLevel: 3,
        customSymptoms: ['cramps'],
      },
    };
    const log = {
      id: periodId,
      userId: tracker.userId,
      localDate: new Date('2026-08-15T00:00:00.000Z'),
      version: 5,
      deletedAt: null,
    };
    const tx = legacyPendingTransaction(mutation, {
      dailyLog: { findFirst: jest.fn().mockResolvedValue(log) },
    });
    tx.syncChange = { findFirst: jest.fn().mockResolvedValue({ cursor: 13n }) };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const logs = {
      matchesUpdateRequest: jest.fn().mockReturnValue(true),
      serialize: jest.fn().mockReturnValue({ id: periodId, version: 5 }),
      update: jest.fn(),
    };
    const service = syncService(prisma, {}, logs);

    await expect(invokeApply(service, mutation)).resolves.toMatchObject({
      entity: { id: periodId, version: 5 },
    });
    expect(logs.matchesUpdateRequest).toHaveBeenCalledWith(
      log,
      expect.objectContaining({ version: 4, painLevel: 3 }),
    );
    expect(logs.update).not.toHaveBeenCalled();
  });

  it('recovers committed daily-log delete but never acknowledges a foreign entity', async () => {
    const mutation: SyncMutationDto = {
      clientMutationId: mutationId,
      entityType: SyncEntityTypeDto.DAILY_LOG,
      operation: SyncMutationOperationDto.DELETE,
      payload: { id: periodId, version: 4 },
    };
    const deletedLog = {
      id: periodId,
      userId: tracker.userId,
      version: 5,
      deletedAt: new Date(),
    };
    const tx = legacyPendingTransaction(mutation, {
      dailyLog: {
        findFirst: jest
          .fn()
          .mockResolvedValueOnce(deletedLog)
          .mockResolvedValueOnce(null),
      },
    });
    tx.syncChange = { findFirst: jest.fn().mockResolvedValue({ cursor: 11n }) };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const logs = { remove: jest.fn() };
    const service = syncService(prisma, {}, logs);

    await expect(invokeApply(service, mutation)).resolves.toMatchObject({
      id: periodId,
      deleted: true,
    });
    expect(logs.remove).not.toHaveBeenCalled();

    tx.idempotencyKey.updateMany.mockClear();
    await expect(invokeApply(service, mutation)).rejects.toBeInstanceOf(
      ConflictException,
    );
    expect(tx.idempotencyKey.updateMany).not.toHaveBeenCalled();
    expect(tx.dailyLog.findFirst).toHaveBeenLastCalledWith({
      where: { id: periodId, userId: tracker.userId },
    });
  });
});

function syncService(
  prisma: object,
  periods: object = {},
  logs: object = {},
  predictions: object = {},
): SyncService {
  return new SyncService(
    prisma as never,
    periods as never,
    logs as never,
    {} as never,
    predictions as never,
  );
}

function invokeApply(
  service: SyncService,
  mutation: SyncMutationDto,
): Promise<Record<string, unknown>> {
  return (
    service as unknown as {
      applyIdempotently(
        user: AuthenticatedUser,
        value: SyncMutationDto,
      ): Promise<Record<string, unknown>>;
    }
  ).applyIdempotently(tracker, mutation);
}

function periodCreateMutation(): SyncMutationDto {
  return {
    clientMutationId: mutationId,
    entityType: SyncEntityTypeDto.PERIOD,
    operation: SyncMutationOperationDto.UPSERT,
    payload: {
      id: periodId,
      startDate: '2026-07-01',
      endDate: '2026-07-05',
      source: PeriodSource.MANUAL,
    },
  };
}

function storedPeriod(version: number, deletedAt: Date | null) {
  return {
    id: periodId,
    userId: tracker.userId,
    startDate: new Date('2026-07-01T00:00:00.000Z'),
    endDate: new Date('2026-07-05T00:00:00.000Z'),
    source: PeriodSource.MANUAL,
    createdAt: new Date('2026-07-01T12:00:00.000Z'),
    updatedAt: new Date('2026-07-05T12:00:00.000Z'),
    deletedAt,
    version,
  };
}

function legacyPendingTransaction<T extends Record<string, unknown>>(
  mutation: SyncMutationDto,
  entities: T,
) {
  return {
    ...entities,
    $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
    syncChange: { findFirst: jest.fn() },
    idempotencyKey: {
      createMany: jest.fn().mockResolvedValue({ count: 0 }),
      findUniqueOrThrow: jest.fn().mockResolvedValue({
        id: 'legacy-pending-id',
        userId: tracker.userId,
        householdId: tracker.householdId,
        requestHash: requestHash(mutation),
        responseStatus: 102,
        responseBody: { pending: true },
        expiresAt: new Date(Date.now() + 60_000),
      }),
      updateMany: jest.fn().mockResolvedValue({ count: 1 }),
    },
  };
}

function requestHash(mutation: SyncMutationDto): string {
  return createHash('sha256').update(stableJson(mutation)).digest('hex');
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map((item) => stableJson(item)).join(',')}]`;
  }
  if (value && typeof value === 'object') {
    return `{${Object.entries(value as Record<string, unknown>)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, item]) => `${JSON.stringify(key)}:${stableJson(item)}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}
