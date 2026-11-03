import { ConflictException } from '@nestjs/common';
import { PeriodEvent, PeriodSource, PreferredLanguage, UserRole } from '@prisma/client';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PeriodsService } from './periods.service.js';

const tracker: AuthenticatedUser = {
  userId: 'tracker-id', sessionId: 'session-id', householdId: 'household-id', membershipId: 'membership-id',
  role: UserRole.TRACKER, username: 'tracker', displayName: 'Tracker', preferredLanguage: PreferredLanguage.DE,
};
const date = (value: string) => new Date(`${value}T00:00:00Z`);
const row = (id: string, start: string, end: string | null = null): PeriodEvent => ({
  id, userId: tracker.userId, startDate: date(start), endDate: end ? date(end) : null,
  source: PeriodSource.MANUAL, createdAt: date('2026-01-01'), updatedAt: date('2026-01-01'), deletedAt: null, version: 1,
});

// Evaluate the actual Prisma where tree emitted by the service, rather than
// mocking an overlap answer. PostgreSQL exclusion execution is verified separately.
function matches(record: PeriodEvent, where: Record<string, unknown>): boolean {
  return Object.entries(where).every(([key, expected]) => {
    if (key === 'OR') return (expected as Array<Record<string, unknown>>).some(part => matches(record, part));
    const actual = record[key as keyof PeriodEvent];
    if (expected !== null && typeof expected === 'object' && !(expected instanceof Date)) {
      return Object.entries(expected).every(([operator, bound]) => {
        if (operator === 'not') return actual !== bound;
        if (actual === null) return false;
        if (!(actual instanceof Date) || !(bound instanceof Date)) throw new Error('Unexpected date comparison');
        if (operator === 'lte') return actual <= bound;
        if (operator === 'gte') return actual >= bound;
        throw new Error(`Unexpected operator ${operator}`);
      });
    }
    return actual === expected;
  });
}

function harness(initial: PeriodEvent[]) {
  const rows = initial.map(item => ({ ...item }));
  const tx = {
    $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
    membership: { findUnique: jest.fn().mockResolvedValue({ userId: tracker.userId, householdId: tracker.householdId, role: tracker.role }) },
    cycleProfile: { findUnique: jest.fn().mockResolvedValue({ timezone: 'Europe/Berlin' }) },
    periodEvent: {
      findUnique: jest.fn(({ where }: { where: { id: string } }) => rows.find(item => item.id === where.id) ?? null),
      findUniqueOrThrow: jest.fn(({ where }: { where: { id: string } }) => rows.find(item => item.id === where.id)!),
      findFirst: jest.fn(({ where }: { where: Record<string, unknown> }) => rows.find(item => matches(item, where)) ?? null),
      findMany: jest.fn(({ where }: { where: Record<string, unknown> }) => rows.filter(item => matches(item, where))),
      create: jest.fn(({ data }: { data: Pick<PeriodEvent, 'id' | 'userId' | 'startDate' | 'endDate' | 'source'> }) => {
        const created = { ...row(data.id, data.startDate.toISOString().slice(0, 10)), ...data };
        rows.push(created);
        return created;
      }),
      updateMany: jest.fn(({ where, data }: { where: Record<string, unknown>; data: { startDate?: Date; endDate?: Date | null; version: { increment: number } } }) => {
        const current = rows.find(item => matches(item, where));
        if (!current) return { count: 0 };
        if (data.startDate !== undefined) current.startDate = data.startDate;
        if (data.endDate !== undefined) current.endDate = data.endDate;
        current.version += data.version.increment;
        return { count: 1 };
      }),
    },
    syncChange: { create: jest.fn().mockResolvedValue({}) },
  };
  const prisma = { ...tx, $transaction: jest.fn(async (callback: (value: typeof tx) => Promise<unknown>) => callback(tx)) };
  const predictions = { recalculateAndCache: jest.fn().mockResolvedValue({}) };
  return { rows, tx, predictions, service: new PeriodsService(prisma as never, predictions as never) };
}

describe('PeriodsService five-day overlap semantics', () => {
  it.each([
    ['2026-07-01', null, '2026-07-05', null, true],
    ['2026-07-01', null, '2026-07-06', null, false],
    ['2026-07-06', null, '2026-07-01', null, false],
    ['2026-07-05', null, '2026-07-01', null, true],
    ['2026-07-01', '2026-07-08', '2026-07-06', null, true],
    ['2026-07-06', null, '2026-07-01', '2026-07-08', true],
    ['2026-07-01', '2026-07-03', '2026-07-04', null, false],
    ['2026-01-29', null, '2026-02-03', null, false],
    ['2024-02-27', null, '2024-03-02', null, true],
    ['2024-02-27', null, '2024-03-03', null, false],
    ['2026-03-27', null, '2026-04-01', null, false],
    ['2026-10-23', null, '2026-10-28', null, false],
  ] as const)('checks both operands: %s/%s and %s/%s (conflict=%s)', async (priorStart, priorEnd, startDate, endDate, conflict) => {
    const original = row('existing', priorStart, priorEnd);
    const { service, tx, rows } = harness([original]);
    const operation = service.create(tracker, { id: 'new', startDate, endDate, source: PeriodSource.MANUAL });
    if (conflict) {
      await expect(operation).rejects.toBeInstanceOf(ConflictException);
      expect(tx.periodEvent.create).not.toHaveBeenCalled();
    } else {
      await expect(operation).resolves.toMatchObject({ startDate, endDate, version: 1 });
      expect(tx.periodEvent.create).toHaveBeenCalledTimes(1);
    }
    expect(rows[0]).toEqual(original);
  });

  it('preserves raw null on read and idempotent replay without extra sync mutations', async () => {
    const original = row('existing', '2026-07-01');
    const { service, tx, rows } = harness([original]);
    await expect(service.list(tracker.userId)).resolves.toMatchObject([{ endDate: null, version: 1, updatedAt: original.updatedAt }]);
    await expect(service.create(tracker, { id: original.id, startDate: '2026-07-01', endDate: null, source: PeriodSource.MANUAL }))
      .resolves.toMatchObject({ endDate: null, version: 1 });
    expect(tx.periodEvent.create).not.toHaveBeenCalled();
    expect(tx.periodEvent.updateMany).not.toHaveBeenCalled();
    expect(tx.syncChange.create).not.toHaveBeenCalled();
    expect(rows).toEqual([original]);
  });

  it('honours manual extension, rejects overlap, and clearing keeps null instead of a synthetic end', async () => {
    const { service, tx } = harness([row('existing', '2026-07-01')]);
    await expect(service.update(tracker, 'existing', { version: 1, endDate: '2026-07-08' })).resolves.toMatchObject({ endDate: '2026-07-08', version: 2 });
    await expect(service.create(tracker, { id: 'next', startDate: '2026-07-06' })).rejects.toBeInstanceOf(ConflictException);
    await expect(service.update(tracker, 'existing', { version: 2, endDate: null })).resolves.toMatchObject({ endDate: null, version: 3 });
    await expect(service.create(tracker, { id: 'next', startDate: '2026-07-06', source: PeriodSource.MANUAL })).resolves.toMatchObject({ endDate: null });
    await expect(service.update(tracker, 'existing', { version: 3, endDate: '2026-07-08' })).rejects.toBeInstanceOf(ConflictException);
    await expect(service.update(tracker, 'existing', { version: 2, endDate: '2026-07-03' })).rejects.toBeInstanceOf(ConflictException);
    expect(tx.periodEvent.updateMany).toHaveBeenCalledTimes(2);
    expect(tx.syncChange.create).toHaveBeenCalledTimes(3);
  });

  it('ignores deleted and other-owner ranges without leaking them into owner results', async () => {
    const { service } = harness([
      { ...row('foreign', '2026-07-01'), userId: 'other-owner' },
      { ...row('deleted', '2026-07-01'), deletedAt: date('2026-07-02') },
    ]);
    await expect(service.create(tracker, { id: 'new', startDate: '2026-07-01', source: PeriodSource.MANUAL })).resolves.toMatchObject({ endDate: null });
    const visible = await service.list(tracker.userId);
    expect(visible.map(item => item.id)).toEqual(['new']);
  });
});
