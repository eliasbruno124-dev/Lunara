import { ConflictException } from '@nestjs/common';
import { PreferredLanguage, UserRole } from '@prisma/client';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { DailyLogsService } from './daily-logs.service.js';

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

describe('DailyLogsService client identifier idempotency', () => {
  it('returns 409 when an existing client UUID is reused with a different payload', async () => {
    const existing = {
      id: 'bf3bc5a9-8850-4aec-89e6-8c9644b15732',
      userId: tracker.userId,
      localDate: new Date('2026-08-10T00:00:00.000Z'),
      flowLevel: null,
      painLevel: 2,
      mood: null,
      energy: null,
      sleepQuality: null,
      bodyTemperature: null,
      ovulationTestResult: null,
      customSymptoms: [],
      encryptedNote: null,
      createdAt: new Date('2026-08-10T12:00:00.000Z'),
      updatedAt: new Date('2026-08-10T12:00:00.000Z'),
      deletedAt: null,
      version: 1,
    };
    const prisma = {
      dailyLog: { findUnique: jest.fn().mockResolvedValue(existing) },
      $transaction: jest.fn(),
    };
    const encryption = { encrypt: jest.fn(), decrypt: jest.fn() };
    const service = new DailyLogsService(prisma as never, encryption as never);

    const error: unknown = await service
      .create(tracker, {
        id: existing.id,
        localDate: '2026-08-10',
        painLevel: 8,
      })
      .catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ConflictException);
    expect((error as ConflictException).getStatus()).toBe(409);
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });
});
