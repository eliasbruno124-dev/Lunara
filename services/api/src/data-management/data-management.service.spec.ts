import { PreferredLanguage, UserRole } from '@prisma/client';
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
