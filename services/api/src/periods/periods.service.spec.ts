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

describe('PeriodsService client identifier idempotency', () => {
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
    const prisma = {
      periodEvent: { findUnique: jest.fn().mockResolvedValue(existing) },
      $transaction: jest.fn(),
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
    expect(prisma.$transaction).not.toHaveBeenCalled();
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
      periodEvent: { findUnique: jest.fn().mockResolvedValue(null) },
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
      periodEvent: { findFirst: jest.fn().mockResolvedValue(current) },
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
