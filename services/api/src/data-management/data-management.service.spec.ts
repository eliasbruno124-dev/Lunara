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
    const service = new DataManagementService(prisma as never, logs as never, {} as never);

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

describe('DataManagementService relationship deletion', () => {
  it('takes the relationship advisory lock before re-reading every member id', async () => {
    const password = 'A secure deletion password 2026!';
    const passwordHash = await argon2.hash(password);
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([
        { pg_advisory_xact_lock: null },
      ]),
      membership: {
        findMany: jest.fn().mockResolvedValue([
          { userId: tracker.userId },
          { userId: 'partner-id' },
        ]),
      },
      user: { deleteMany: jest.fn().mockResolvedValue({ count: 2 }) },
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
    );

    await service.deleteAccount(tracker, {
      password,
      deleteHousehold: true,
    });

    expect(tx.$queryRaw).toHaveBeenCalledTimes(1);
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
      where: { id: { in: [tracker.userId, 'partner-id'] } },
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
