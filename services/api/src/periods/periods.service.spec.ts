import { ConflictException } from '@nestjs/common';
import { PeriodSource, PreferredLanguage, UserRole } from '@prisma/client';
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
});
