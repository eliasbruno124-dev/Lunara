import { ConflictException, ForbiddenException } from '@nestjs/common';
import { PreferredLanguage, UserRole } from '@prisma/client';
import * as argon2 from 'argon2';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { DataManagementService } from './data-management.service.js';
import { ExportFormatDto } from './data-management.dto.js';

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

describe('DataManagementService CSV export', () => {
  it('neutralizes every spreadsheet formula sigil in user-controlled cells', async () => {
    const formulaValues = ['=1+1', '+1+1', '-1+1', '@SUM(A1)', '\t=1+1', '\r=1+1'] as const;
    const prisma = {
      user: { findUniqueOrThrow: jest.fn().mockResolvedValue({ id: tracker.userId }) },
      cycleProfile: { findUnique: jest.fn().mockResolvedValue(null) },
      periodEvent: { findMany: jest.fn().mockResolvedValue([]) },
      dailyLog: { findMany: jest.fn().mockResolvedValue(formulaValues.map(() => ({}))) },
      sharingSettings: { findUnique: jest.fn().mockResolvedValue(null) },
      carePreferences: { findUnique: jest.fn().mockResolvedValue(null) },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
    };
    const logs = {
      serialize: jest
        .fn()
        .mockImplementationOnce(() => dailyLog('log-1', formulaValues[0]))
        .mockImplementationOnce(() => dailyLog('log-2', formulaValues[1]))
        .mockImplementationOnce(() => dailyLog('log-3', formulaValues[2]))
        .mockImplementationOnce(() => dailyLog('log-4', formulaValues[3]))
        .mockImplementationOnce(() => dailyLog('log-5', formulaValues[4]))
        .mockImplementationOnce(() => dailyLog('log-6', formulaValues[5])),
    };
    const service = new DataManagementService(
      prisma as never,
      logs as never,
      {} as never,
      {} as never,
    );

    const result = await service.export(tracker, { format: ExportFormatDto.DAILY_LOGS_CSV });

    expect(result.encoding).toBe('utf8');
    expect(typeof result.data).toBe('string');
    const csv = result.data as string;
    for (const formula of formulaValues) {
      expect(csv).toContain(`'${formula}`);
    }
    expect(csv).not.toContain(',=1+1');
    expect(csv).not.toContain(',+1+1');
    expect(csv).not.toContain(',-1+1');
    expect(csv).not.toContain(',@SUM(A1)');
  });
});

describe('DataManagementService period import safety', () => {
  const existingPeriod = {
    id: '1ded543f-acf1-4451-b0fc-cb92b5c06483',
    startDate: new Date('2026-07-01T00:00:00.000Z'),
    endDate: new Date('2026-07-05T00:00:00.000Z'),
    deletedAt: null,
  };

  it('reports overlaps with existing rows and within the supplied batch in preview', async () => {
    const prisma = {
      periodEvent: { findMany: jest.fn().mockResolvedValue([existingPeriod]) },
      dailyLog: { findMany: jest.fn().mockResolvedValue([]) },
    };
    const service = new DataManagementService(
      prisma as never,
      {} as never,
      {} as never,
      {} as never,
    );

    await expect(
      service.import(tracker, {
        data: {
          periods: [
            { startDate: '2026-07-04', endDate: '2026-07-08' },
            { startDate: '2026-08-01', endDate: '2026-08-05' },
            { startDate: '2026-08-04', endDate: '2026-08-07' },
          ],
        },
      }),
    ).resolves.toEqual({
      preview: true,
      periods: { supplied: 3, create: 1, duplicates: 0, conflicts: 2 },
      dailyLogs: { supplied: 0, create: 0, duplicates: 0 },
    });
  });

  it('rebuilds the plan under the period lock and refreshes prediction atomically', async () => {
    const periodId = '86178311-8f06-409e-b2cf-99e83c362327';
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      membership: {
        findUnique: jest.fn().mockResolvedValue({
          userId: tracker.userId,
          householdId: tracker.householdId,
          role: UserRole.TRACKER,
        }),
      },
      periodEvent: {
        findMany: jest.fn().mockResolvedValue([existingPeriod]),
        create: jest.fn().mockResolvedValue({ id: periodId }),
      },
      dailyLog: {
        findMany: jest.fn().mockResolvedValue([]),
        create: jest.fn(),
      },
      importBatch: {
        create: jest.fn().mockResolvedValue({ id: 'import-batch-id' }),
      },
      syncChange: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const predictions = { recalculateAndCache: jest.fn().mockResolvedValue({}) };
    const service = new DataManagementService(
      prisma as never,
      {} as never,
      { encrypt: jest.fn() } as never,
      predictions as never,
    );

    await expect(
      service.import(tracker, {
        preview: false,
        data: {
          periods: [
            {
              id: periodId,
              startDate: '2026-06-01',
              endDate: '2026-06-05',
            },
          ],
        },
      }),
    ).resolves.toEqual({
      preview: false,
      batchId: 'import-batch-id',
      periods: { supplied: 1, create: 1, duplicates: 0, conflicts: 0 },
      dailyLogs: { supplied: 0, create: 0, duplicates: 0 },
    });
    expect(tx.$queryRaw.mock.invocationCallOrder[0]).toBeLessThan(
      tx.membership.findUnique.mock.invocationCallOrder[0] ?? 0,
    );
    expect(tx.membership.findUnique.mock.invocationCallOrder[0]).toBeLessThan(
      tx.periodEvent.findMany.mock.invocationCallOrder[0] ?? 0,
    );
    expect(predictions.recalculateAndCache).toHaveBeenCalledWith(
      tracker.userId,
      tx,
    );
    expect(tx.periodEvent.create).toHaveBeenCalledTimes(1);
    expect(tx.syncChange.create).toHaveBeenCalledTimes(1);
  });

  it('rejects a plan that became overlapping before commit without writing', async () => {
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      membership: {
        findUnique: jest.fn().mockResolvedValue({
          userId: tracker.userId,
          householdId: tracker.householdId,
          role: UserRole.TRACKER,
        }),
      },
      periodEvent: { findMany: jest.fn().mockResolvedValue([existingPeriod]), create: jest.fn() },
      dailyLog: { findMany: jest.fn().mockResolvedValue([]), create: jest.fn() },
      importBatch: { create: jest.fn() },
      syncChange: { create: jest.fn() },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const predictions = { recalculateAndCache: jest.fn() };
    const service = new DataManagementService(
      prisma as never,
      {} as never,
      {} as never,
      predictions as never,
    );

    await expect(
      service.import(tracker, {
        preview: false,
        data: {
          periods: [{ startDate: '2026-07-03', endDate: '2026-07-06' }],
        },
      }),
    ).rejects.toMatchObject({ status: 409 });
    expect(tx.importBatch.create).not.toHaveBeenCalled();
    expect(tx.periodEvent.create).not.toHaveBeenCalled();
    expect(predictions.recalculateAndCache).not.toHaveBeenCalled();
  });

  it('rejects a stale tracker import before it creates any counted health data', async () => {
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      membership: {
        findUnique: jest.fn().mockResolvedValue({
          userId: tracker.userId,
          householdId: 'new-relationship-id',
          role: UserRole.PARTNER,
        }),
      },
      periodEvent: { findMany: jest.fn(), create: jest.fn() },
      dailyLog: { findMany: jest.fn(), create: jest.fn() },
      importBatch: { create: jest.fn() },
      syncChange: { create: jest.fn() },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const service = new DataManagementService(
      prisma as never,
      {} as never,
      {} as never,
      {} as never,
    );

    await expect(
      service.import(tracker, {
        preview: false,
        data: { periods: [{ startDate: '2026-06-01' }] },
      }),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(tx.periodEvent.findMany).not.toHaveBeenCalled();
    expect(tx.importBatch.create).not.toHaveBeenCalled();
    expect(tx.periodEvent.create).not.toHaveBeenCalled();
    expect(tx.dailyLog.create).not.toHaveBeenCalled();
  });

  it('maps an adapter-level exclusion violation without exposing range details', async () => {
    const databaseError = Object.assign(
      new Error(
        'conflicting key value violates exclusion constraint "PeriodEvent_userId_active_range_excl"',
      ),
      {
        cause: {
          kind: 'postgres',
          code: '23P01',
          detail: 'sensitive range detail',
        },
      },
    );
    const prisma = { $transaction: jest.fn().mockRejectedValue(databaseError) };
    const service = new DataManagementService(
      prisma as never,
      {} as never,
      {} as never,
      {} as never,
    );

    const error = await service
      .import(tracker, { preview: false, data: { periods: [] } })
      .catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ConflictException);
    expect((error as ConflictException).getStatus()).toBe(409);
    expect((error as Error).message).not.toContain('sensitive range detail');
  });
});

describe('DataManagementService relationship deletion', () => {
  it('locks membership and relationship before deleting an exclusive household', async () => {
    const password = 'A secure deletion password 2026!';
    const passwordHash = await argon2.hash(password);
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([
        { pg_advisory_xact_lock: null },
      ]),
      membership: {
        findUnique: jest.fn().mockResolvedValue({ userId: tracker.userId, householdId: tracker.householdId, role: tracker.role }),
        findMany: jest.fn().mockResolvedValue([
          { userId: tracker.userId },
        ]),
      },
      user: {
        findUniqueOrThrow: jest.fn().mockResolvedValue({ passwordHash, disabledAt: null }),
        deleteMany: jest.fn().mockResolvedValue({ count: 1 }),
      },
      deviceSession: { findUnique: jest.fn().mockResolvedValue({ userId: tracker.userId, revokedAt: null, expiresAt: new Date(Date.now() + 60_000) }) },
      household: { delete: jest.fn().mockResolvedValue({}) },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      user: {
        findUniqueOrThrow: jest.fn().mockResolvedValue({ passwordHash }),
      },
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const service = new DataManagementService(
      prisma as never,
      {} as never,
      {} as never,
      {} as never,
    );

    await service.deleteAccount(tracker, {
      password,
      deleteHousehold: true,
    });

    expect(tx.$queryRaw).toHaveBeenCalledTimes(3);
    expect(tx.$queryRaw).toHaveBeenCalledWith(
      expect.anything(),
      `lunara:relationship:${tracker.householdId}`,
    );
    expect(tx.$queryRaw.mock.invocationCallOrder[0]).toBeLessThan(
      tx.membership.findMany.mock.invocationCallOrder[0] ?? 0,
    );
    expect(tx.membership.findMany).toHaveBeenCalledWith({
      where: { householdId: tracker.householdId },
      select: { userId: true },
    });
    expect(tx.user.deleteMany).toHaveBeenCalledWith({
      where: { id: { in: [tracker.userId] } },
    });
  });
});

function dailyLog(id: string, note: string): Record<string, unknown> {
  return {
    id,
    localDate: '2026-08-12',
    flowLevel: null,
    painLevel: null,
    mood: null,
    energy: null,
    sleepQuality: null,
    bodyTemperature: null,
    customSymptoms: [],
    note,
  };
}
