import { ConflictException } from '@nestjs/common';
import {
  PeriodSource,
  PreferredLanguage,
  Prisma,
  UserRole,
} from '@prisma/client';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PeriodsService } from './periods.service.js';

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

describe('PeriodsService period mutation safety', () => {
  it('returns 409 when an existing client UUID is reused with a different payload', async () => {
    const existing = {
      id: '4a2da013-22b7-46ba-a9b3-5545b7df67ce',
      userId: tracker.userId,
      startDate: new Date('2026-08-10T00:00:00.000Z'),
      endDate: null,
      source: PeriodSource.MANUAL,
      createdAt: new Date('2026-08-10T12:00:00.000Z'),
      updatedAt: new Date('2026-08-10T12:00:00.000Z'),
      deletedAt: null,
      version: 1,
    };
    const transaction = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      cycleProfile: {
        findUnique: jest.fn().mockResolvedValue({ timezone: 'Europe/Berlin' }),
      },
      periodEvent: { findUnique: jest.fn().mockResolvedValue(existing) },
    };
    const prisma = {
      $transaction: jest.fn(
        async (callback: (tx: typeof transaction) => Promise<unknown>) =>
          callback(transaction),
      ),
    };
    const predictions = { recalculateAndCache: jest.fn() };
    const service = new PeriodsService(prisma as never, predictions as never);

    const error: unknown = await service
      .create(tracker, {
        id: existing.id,
        startDate: '2026-08-11',
        source: PeriodSource.MANUAL,
      })
      .catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ConflictException);
    expect((error as ConflictException).getStatus()).toBe(409);
    expect(prisma.$transaction).toHaveBeenCalledTimes(1);
    expect(predictions.recalculateAndCache).not.toHaveBeenCalled();
  });

  it('creates a complete historical range and its sync change in one transaction', async () => {
    const historical = {
      id: '59f57ca1-c980-47ea-8e84-e1b17eab0779',
      userId: tracker.userId,
      startDate: new Date('2026-07-01T00:00:00.000Z'),
      endDate: new Date('2026-07-05T00:00:00.000Z'),
      source: PeriodSource.MANUAL,
      createdAt: new Date('2026-08-10T12:00:00.000Z'),
      updatedAt: new Date('2026-08-10T12:00:00.000Z'),
      deletedAt: null,
      version: 1,
    };
    const transaction = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      cycleProfile: {
        findUnique: jest.fn().mockResolvedValue({ timezone: 'Europe/Berlin' }),
      },
      periodEvent: {
        findUnique: jest.fn().mockResolvedValue(null),
        findFirst: jest.fn().mockResolvedValue(null),
        create: jest.fn().mockResolvedValue(historical),
      },
      syncChange: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      $transaction: jest.fn(
        async (callback: (tx: typeof transaction) => Promise<unknown>) =>
          callback(transaction),
      ),
    };
    const predictions = { recalculateAndCache: jest.fn().mockResolvedValue({}) };
    const service = new PeriodsService(prisma as never, predictions as never);

    await expect(
      service.create(tracker, {
        id: historical.id,
        startDate: '2026-07-01',
        endDate: '2026-07-05',
        source: PeriodSource.MANUAL,
      }),
    ).resolves.toMatchObject({
      id: historical.id,
      startDate: '2026-07-01',
      endDate: '2026-07-05',
      version: 1,
    });
    expect(transaction.$queryRaw).toHaveBeenCalledTimes(1);
    expect(transaction.periodEvent.create).toHaveBeenCalledWith({
      data: {
        id: historical.id,
        userId: tracker.userId,
        startDate: historical.startDate,
        endDate: historical.endDate,
        source: PeriodSource.MANUAL,
      },
    });
    expect(transaction.syncChange.create).toHaveBeenCalledTimes(1);
    expect(predictions.recalculateAndCache).toHaveBeenCalledWith(
      tracker.userId,
      transaction,
    );
  });

  it('rejects an overlapping historical range before writing', async () => {
    const transaction = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      cycleProfile: {
        findUnique: jest.fn().mockResolvedValue({ timezone: 'Europe/Berlin' }),
      },
      periodEvent: {
        findUnique: jest.fn().mockResolvedValue(null),
        findFirst: jest.fn().mockResolvedValue({
          startDate: new Date('2026-07-01T00:00:00.000Z'),
        }),
        create: jest.fn(),
      },
      syncChange: { create: jest.fn() },
    };
    const prisma = {
      $transaction: jest.fn(
        async (callback: (tx: typeof transaction) => Promise<unknown>) =>
          callback(transaction),
      ),
    };
    const predictions = { recalculateAndCache: jest.fn() };
    const service = new PeriodsService(prisma as never, predictions as never);

    const error: unknown = await service
      .create(tracker, {
        id: '94cfc8a7-e10e-4fca-988b-8b7484156e93',
        startDate: '2026-07-03',
        endDate: '2026-07-07',
      })
      .catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ConflictException);
    expect((error as ConflictException).getStatus()).toBe(409);
    expect((error as Error).message).toBe(
      'Period range overlaps an existing period entry',
    );
    expect(transaction.periodEvent.create).not.toHaveBeenCalled();
    expect(transaction.syncChange.create).not.toHaveBeenCalled();
    expect(predictions.recalculateAndCache).not.toHaveBeenCalled();
  });

  it('maps the PostgreSQL exclusion constraint to a clean 409 response', async () => {
    const overlapError = new Prisma.PrismaClientKnownRequestError(
      'Exclusion constraint failed',
      {
        code: 'P2004',
        clientVersion: '7.9.1',
        meta: {
          database_error: {
            code: '23P01',
            constraint: 'PeriodEvent_userId_active_range_excl',
          },
        },
      },
    );
    const prisma = {
      $transaction: jest.fn().mockRejectedValue(overlapError),
    };
    const predictions = { recalculateAndCache: jest.fn() };
    const service = new PeriodsService(prisma as never, predictions as never);

    await expect(
      service.create(tracker, {
        id: 'f817feac-c0bd-4d6a-84f8-5de2eb47e535',
        startDate: '2026-07-03',
        endDate: '2026-07-07',
      }),
    ).rejects.toMatchObject({
      status: 409,
      message: 'Period range overlaps an existing period entry',
    });
    expect(predictions.recalculateAndCache).not.toHaveBeenCalled();
  });

  it('rejects an update that would extend into another active period', async () => {
    const current = {
      id: '6ddd82c5-9419-464d-bb0c-f8ce24f0ead7',
      userId: tracker.userId,
      startDate: new Date('2026-07-01T00:00:00.000Z'),
      endDate: new Date('2026-07-05T00:00:00.000Z'),
      source: PeriodSource.MANUAL,
      createdAt: new Date('2026-08-10T12:00:00.000Z'),
      updatedAt: new Date('2026-08-10T12:00:00.000Z'),
      deletedAt: null,
      version: 2,
    };
    const transaction = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      cycleProfile: {
        findUnique: jest.fn().mockResolvedValue({ timezone: 'Europe/Berlin' }),
      },
      periodEvent: {
        findFirst: jest
          .fn()
          .mockResolvedValueOnce(current)
          .mockResolvedValueOnce({
            startDate: new Date('2026-07-08T00:00:00.000Z'),
          }),
        updateMany: jest.fn(),
      },
      syncChange: { create: jest.fn() },
    };
    const prisma = {
      $transaction: jest.fn(
        async (callback: (tx: typeof transaction) => Promise<unknown>) =>
          callback(transaction),
      ),
    };
    const predictions = { recalculateAndCache: jest.fn() };
    const service = new PeriodsService(prisma as never, predictions as never);

    await expect(
      service.update(tracker, current.id, {
        version: current.version,
        endDate: '2026-07-10',
      }),
    ).rejects.toMatchObject({
      status: 409,
      message: 'Period range overlaps an existing period entry',
    });
    expect(transaction.periodEvent.updateMany).not.toHaveBeenCalled();
    expect(transaction.syncChange.create).not.toHaveBeenCalled();
    expect(predictions.recalculateAndCache).not.toHaveBeenCalled();
  });

  it('maps an atomic active-date create collision to a safe 409 response', async () => {
    const uniqueDateError = new Prisma.PrismaClientKnownRequestError(
      'Unique constraint failed',
      {
        code: 'P2002',
        clientVersion: '7.9.1',
        meta: { target: 'PeriodEvent_userId_startDate_active_key' },
      },
    );
    const prisma = {
      $transaction: jest.fn().mockRejectedValue(uniqueDateError),
    };
    const predictions = { recalculateAndCache: jest.fn() };
    const service = new PeriodsService(prisma as never, predictions as never);

    const error: unknown = await service
      .create(tracker, {
        id: '71160745-d546-47ea-b7c3-573fdc42de65',
        startDate: '2026-08-10',
        source: PeriodSource.SYNC,
      })
      .catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ConflictException);
    expect((error as ConflictException).getStatus()).toBe(409);
    expect((error as Error).message).toBe(
      'An active period entry already exists for this date',
    );
    expect((error as Error).message).not.toContain(tracker.userId);
    expect(predictions.recalculateAndCache).not.toHaveBeenCalled();
  });

  it('refreshes the prediction before acknowledging a committed matching create', async () => {
    const concurrent = {
      id: '71160745-d546-47ea-b7c3-573fdc42de65',
      userId: tracker.userId,
      startDate: new Date('2026-08-10T00:00:00.000Z'),
      endDate: null,
      source: PeriodSource.SYNC,
      createdAt: new Date('2026-08-10T12:00:00.000Z'),
      updatedAt: new Date('2026-08-10T12:00:00.000Z'),
      deletedAt: null,
      version: 1,
    };
    const transaction = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      cycleProfile: {
        findUnique: jest.fn().mockResolvedValue({ timezone: 'Europe/Berlin' }),
      },
      periodEvent: { findUnique: jest.fn().mockResolvedValue(concurrent) },
    };
    const prisma = {
      $transaction: jest.fn(
        async (callback: (tx: typeof transaction) => Promise<unknown>) =>
          callback(transaction),
      ),
    };
    const predictions = { recalculateAndCache: jest.fn().mockResolvedValue({}) };
    const service = new PeriodsService(prisma as never, predictions as never);

    await expect(
      service.create(tracker, {
        id: concurrent.id,
        startDate: '2026-08-10',
        source: PeriodSource.SYNC,
      }),
    ).resolves.toMatchObject({ id: concurrent.id, version: 1 });
    expect(predictions.recalculateAndCache).toHaveBeenCalledWith(
      tracker.userId,
      transaction,
    );
  });

  it('rejects future period dates at the API boundary in the profile timezone', async () => {
    const transaction = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      cycleProfile: {
        findUnique: jest.fn().mockResolvedValue({ timezone: 'Europe/Berlin' }),
      },
      periodEvent: { findUnique: jest.fn() },
    };
    const prisma = {
      $transaction: jest.fn(
        async (callback: (tx: typeof transaction) => Promise<unknown>) =>
          callback(transaction),
      ),
    };
    const predictions = { recalculateAndCache: jest.fn() };
    const service = new PeriodsService(prisma as never, predictions as never);

    await expect(
      service.create(tracker, {
        id: 'ed7cb8f4-540d-48bc-ab34-1a0ef5dab67a',
        startDate: '9999-12-31',
        source: PeriodSource.MANUAL,
      }),
    ).rejects.toThrow('Period dates must not be in the future');
    expect(transaction.periodEvent.findUnique).not.toHaveBeenCalled();
    expect(prisma.$transaction).toHaveBeenCalledTimes(1);
    expect(predictions.recalculateAndCache).not.toHaveBeenCalled();
  });

  it('maps an atomic active-date update collision to 409 without recalculating', async () => {
    const current = {
      id: 'bb533a3c-8bf2-48bb-9674-cd944a8038eb',
      userId: tracker.userId,
      startDate: new Date('2026-08-10T00:00:00.000Z'),
      endDate: null,
      source: PeriodSource.MANUAL,
      createdAt: new Date('2026-08-10T12:00:00.000Z'),
      updatedAt: new Date('2026-08-10T12:00:00.000Z'),
      deletedAt: null,
      version: 2,
    };
    const uniqueDateError = new Prisma.PrismaClientKnownRequestError(
      'Unique constraint failed',
      {
        code: 'P2002',
        clientVersion: '7.9.1',
        meta: { target: ['userId', 'startDate'] },
      },
    );
    const prisma = {
      $transaction: jest.fn().mockRejectedValue(uniqueDateError),
    };
    const predictions = { recalculateAndCache: jest.fn() };
    const service = new PeriodsService(prisma as never, predictions as never);

    const error: unknown = await service
      .update(tracker, current.id, {
        version: current.version,
        startDate: '2026-08-11',
      })
      .catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ConflictException);
    expect((error as ConflictException).getStatus()).toBe(409);
    expect((error as Error).message).toBe(
      'An active period entry already exists for this date',
    );
    expect((error as Error).message).not.toContain(current.id);
    expect(predictions.recalculateAndCache).not.toHaveBeenCalled();
  });
});
