import { ConflictException } from '@nestjs/common';
import { PreferredLanguage, UserRole } from '@prisma/client';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { UpdateDailyLogDto } from './daily-logs.dto.js';
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
    const tx = {
      dailyLog: { findUnique: jest.fn().mockResolvedValue(existing) },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
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
    expect(prisma.$transaction).toHaveBeenCalledTimes(1);
  });

  it('persists explicit nulls and an explicit empty symptom list', async () => {
    const updated = {
      id: 'bf3bc5a9-8850-4aec-89e6-8c9644b15732',
      userId: tracker.userId,
      localDate: new Date('2026-08-10T00:00:00.000Z'),
      flowLevel: null,
      painLevel: null,
      mood: null,
      energy: null,
      sleepQuality: null,
      bodyTemperature: null,
      ovulationTestResult: null,
      customSymptoms: [],
      encryptedNote: null,
      createdAt: new Date('2026-08-10T12:00:00.000Z'),
      updatedAt: new Date('2026-08-10T13:00:00.000Z'),
      deletedAt: null,
      version: 3,
    };
    const updateMany = jest.fn().mockResolvedValue({ count: 1 });
    const tx = {
      dailyLog: {
        updateMany,
        findUniqueOrThrow: jest.fn().mockResolvedValue(updated),
      },
      syncChange: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const encryption = { encrypt: jest.fn(), decrypt: jest.fn() };
    const service = new DailyLogsService(prisma as never, encryption as never);

    await service.update(tracker, updated.id, {
      id: updated.id,
      localDate: '2026-08-10',
      version: 2,
      flowLevel: null,
      painLevel: null,
      mood: null,
      energy: null,
      sleepQuality: null,
      bodyTemperature: null,
      ovulationTestResult: null,
      customSymptoms: [],
      note: null,
    });

    expect(updateMany).toHaveBeenCalledWith({
      where: {
        id: updated.id,
        userId: tracker.userId,
        version: 2,
        deletedAt: null,
      },
      data: expect.objectContaining({
        flowLevel: null,
        painLevel: null,
        mood: null,
        energy: null,
        sleepQuality: null,
        bodyTemperature: null,
        ovulationTestResult: null,
        customSymptoms: [],
        encryptedNote: null,
      }) as unknown,
    });
  });

  it('leaves symptoms unchanged when customSymptoms is omitted from an update', async () => {
    const updated = {
      id: 'bf3bc5a9-8850-4aec-89e6-8c9644b15732',
      userId: tracker.userId,
      localDate: new Date('2026-08-10T00:00:00.000Z'),
      flowLevel: null,
      painLevel: 3,
      mood: null,
      energy: null,
      sleepQuality: null,
      bodyTemperature: null,
      ovulationTestResult: null,
      customSymptoms: ['cramps'],
      encryptedNote: null,
      createdAt: new Date('2026-08-10T12:00:00.000Z'),
      updatedAt: new Date('2026-08-10T13:00:00.000Z'),
      deletedAt: null,
      version: 3,
    };
    const updateMany = jest.fn().mockResolvedValue({ count: 1 });
    const tx = {
      dailyLog: {
        updateMany,
        findUniqueOrThrow: jest.fn().mockResolvedValue(updated),
      },
      syncChange: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const service = new DailyLogsService(
      prisma as never,
      { encrypt: jest.fn(), decrypt: jest.fn() } as never,
    );

    await service.update(tracker, updated.id, {
      id: updated.id,
      localDate: '2026-08-10',
      version: 2,
      painLevel: 3,
    });

    expect(updateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ customSymptoms: undefined }) as unknown,
      }),
    );
  });

  it('treats whitespace-only mood like the normalized empty value on create retry', async () => {
    const id = 'bf3bc5a9-8850-4aec-89e6-8c9644b15732';
    const existing = {
      id,
      userId: tracker.userId,
      localDate: new Date('2026-08-10T00:00:00.000Z'),
      flowLevel: null,
      painLevel: null,
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
    const tx = {
      dailyLog: { findUnique: jest.fn().mockResolvedValue(existing) },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const service = new DailyLogsService(
      prisma as never,
      { encrypt: jest.fn(), decrypt: jest.fn() } as never,
    );

    await expect(
      service.create(tracker, {
        id,
        localDate: '2026-08-10',
        mood: '   ',
      }),
    ).resolves.toEqual(expect.objectContaining({ id, mood: null }));
    expect(prisma.$transaction).toHaveBeenCalledTimes(1);
  });

  it('accepts and preserves explicit nulls for nullable scalar DTO fields', async () => {
    const dto = plainToInstance(UpdateDailyLogDto, {
      id: 'bf3bc5a9-8850-4aec-89e6-8c9644b15732',
      localDate: '2026-08-10',
      version: 2,
      flowLevel: null,
      painLevel: null,
      mood: null,
      energy: null,
      sleepQuality: null,
      bodyTemperature: null,
      ovulationTestResult: null,
      note: null,
    });

    const errors = await validate(dto, {
      whitelist: true,
      forbidNonWhitelisted: true,
      forbidUnknownValues: true,
    });

    expect(errors).toEqual([]);
    expect(dto).toEqual(
      expect.objectContaining({
        flowLevel: null,
        painLevel: null,
        mood: null,
        energy: null,
        sleepQuality: null,
        bodyTemperature: null,
        ovulationTestResult: null,
        note: null,
      }),
    );
  });

  it('rejects null for the non-nullable custom symptom list', async () => {
    const dto = plainToInstance(UpdateDailyLogDto, {
      id: 'bf3bc5a9-8850-4aec-89e6-8c9644b15732',
      localDate: '2026-08-10',
      version: 2,
      customSymptoms: null,
    });

    const errors = await validate(dto, {
      whitelist: true,
      forbidNonWhitelisted: true,
      forbidUnknownValues: true,
    });

    expect(errors.some((error) => error.property === 'customSymptoms')).toBe(
      true,
    );
  });
});
