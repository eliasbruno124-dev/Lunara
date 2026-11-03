import { ConflictException } from '@nestjs/common';
import { PeriodSource, PreferredLanguage, UserRole } from '@prisma/client';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { DataManagementService } from './data-management.service.js';
import { ExportFormatDto } from './data-management.dto.js';

const tracker: AuthenticatedUser = {
  userId: 'tracker-id', sessionId: 'session-id', householdId: 'household-id', membershipId: 'membership-id',
  role: UserRole.TRACKER, username: 'tracker', displayName: 'Tracker', preferredLanguage: PreferredLanguage.DE,
};
const date = (value: string) => new Date(`${value}T00:00:00Z`);
const row = (start: string, end: string | null = null) => ({
  id: '1ded543f-acf1-4451-b0fc-cb92b5c06483', userId: tracker.userId, startDate: date(start), endDate: end ? date(end) : null,
  source: PeriodSource.MANUAL, createdAt: date('2026-01-01'), updatedAt: date('2026-01-01'), deletedAt: null, version: 1,
});

function harness(initial: ReturnType<typeof row>[] = []) {
  const rows = initial.map(item => ({ ...item }));
  const tx = {
    $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
    membership: { findUnique: jest.fn().mockResolvedValue({ userId: tracker.userId, householdId: tracker.householdId, role: tracker.role }) },
    periodEvent: {
      findMany: jest.fn().mockImplementation(() => rows),
      create: jest.fn().mockImplementation(({ data }: { data: Partial<ReturnType<typeof row>> }) => {
        const item = { ...row('2026-01-01'), ...data };
        rows.push(item);
        return item;
      }),
    },
    dailyLog: { findMany: jest.fn().mockResolvedValue([]) },
    importBatch: { create: jest.fn().mockResolvedValue({ id: 'batch-id' }) },
    syncChange: { create: jest.fn().mockResolvedValue({}) },
    user: { findUniqueOrThrow: jest.fn().mockResolvedValue({ id: tracker.userId }) },
    cycleProfile: { findUnique: jest.fn().mockResolvedValue(null) },
    sharingSettings: { findUnique: jest.fn().mockResolvedValue(null) },
    carePreferences: { findUnique: jest.fn().mockResolvedValue(null) },
    auditEvent: { create: jest.fn().mockResolvedValue({}) },
  };
  const prisma = { ...tx, $transaction: jest.fn(async (callback: (value: typeof tx) => Promise<unknown>) => callback(tx)) };
  const predictions = { recalculateAndCache: jest.fn().mockResolvedValue({}) };
  return { rows, tx, predictions, service: new DataManagementService(prisma as never, {} as never, {} as never, predictions as never) };
}

describe('five-day import/export fidelity', () => {
  it.each([
    ['2026-07-01', null, '2026-07-05', null, 1],
    ['2026-07-01', null, '2026-07-06', null, 0],
    ['2026-07-06', null, '2026-07-01', null, 0],
    ['2026-07-05', null, '2026-07-01', null, 1],
    ['2026-07-01', '2026-07-08', '2026-07-06', null, 1],
    ['2026-07-06', null, '2026-07-01', '2026-07-08', 1],
  ] as const)('preview and commit agree for both operands: %s/%s + %s/%s', async (storedStart, storedEnd, startDate, endDate, conflicts) => {
    const original = row(storedStart, storedEnd);
    const { service, tx, rows } = harness([original]);
    const data = { periods: [{ startDate, endDate }] };
    await expect(service.import(tracker, { data })).resolves.toMatchObject({ periods: { create: conflicts ? 0 : 1, conflicts } });
    if (conflicts) {
      await expect(service.import(tracker, { data, preview: false })).rejects.toBeInstanceOf(ConflictException);
      expect(tx.periodEvent.create).not.toHaveBeenCalled();
      expect(tx.importBatch.create).not.toHaveBeenCalled();
    } else {
      await expect(service.import(tracker, { data, preview: false })).resolves.toMatchObject({ periods: { create: 1, conflicts: 0 } });
      expect(tx.periodEvent.create).toHaveBeenCalledWith({ data: expect.objectContaining({ endDate: endDate ? date(endDate) : null }) as unknown });
      expect(tx.syncChange.create).toHaveBeenCalledTimes(1);
    }
    expect(rows[0]).toEqual(original);
    expect(tx.periodEvent.findMany).toHaveBeenCalledWith(expect.objectContaining({ where: { userId: tracker.userId } }));
  });

  it('handles unsorted null-ended batches, including duplicate retries and day-five conflicts', async () => {
    const { service, tx } = harness();
    const periods = [{ startDate: '2026-07-11', endDate: null }, { startDate: '2026-07-01', endDate: null }, { startDate: '2026-07-06' }];
    await expect(service.import(tracker, { data: { periods } })).resolves.toMatchObject({ periods: { create: 3, conflicts: 0 } });
    await expect(service.import(tracker, { data: { periods }, preview: false })).resolves.toMatchObject({ periods: { create: 3, conflicts: 0 } });
    await expect(service.import(tracker, { data: { periods }, preview: false })).resolves.toMatchObject({ periods: { create: 0, duplicates: 3, conflicts: 0 } });
    expect(tx.periodEvent.create).toHaveBeenCalledTimes(3);
    expect(tx.syncChange.create).toHaveBeenCalledTimes(3);
    await expect(service.import(tracker, { data: { periods: [{ startDate: '2026-07-05' }] } })).resolves.toMatchObject({ periods: { create: 0, conflicts: 1 } });
  });

  it('retains raw-null JSON/CSV/backup fields and JSON reimport never materializes assumed ends', async () => {
    const { service, rows } = harness([row('2026-07-01')]);
    const json = await service.export(tracker, { format: ExportFormatDto.JSON });
    const bundle = json.data as { periods: Array<{ startDate: string; endDate: string | null }> };
    expect(bundle.periods).toMatchObject([{ startDate: '2026-07-01', endDate: null }]);
    const csv = await service.export(tracker, { format: ExportFormatDto.PERIODS_CSV });
    expect(csv.data).toContain(',2026-07-01,,MANUAL');
    expect(csv.data).not.toContain('2026-07-05');
    const restored = harness();
    await restored.service.import(tracker, { data: bundle, preview: false });
    expect(restored.rows[0]?.endDate).toBeNull();
    expect(rows[0]?.endDate).toBeNull();
    expect(rows[0]?.version).toBe(1);
    expect(rows[0]?.updatedAt).toEqual(date('2026-01-01'));
  });
});
