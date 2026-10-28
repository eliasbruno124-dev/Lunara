import { CyclePhase, UserRole } from '@prisma/client';
import { PartnerService } from './partner.service.js';

describe('PartnerService household isolation', () => {
  it.each([
    ['INSUFFICIENT_DATA', true, false, null, CyclePhase.MENSTRUATION],
    ['PREDICTION_PAUSED', true, false, null, CyclePhase.MENSTRUATION],
    ['SPECIAL_MODE', true, false, null, CyclePhase.MENSTRUATION],
    ['INSUFFICIENT_DATA', false, false, null, null],
    ['INSUFFICIENT_DATA', true, true, null, null],
    ['INSUFFICIENT_DATA', true, false, '2026-03-26', null],
  ])('keeps observed phase independent from %s without bypassing consent (%s/%s/%s)', async (
    reason, sharePhase, sharingPaused, privateDate, expected,
  ) => {
    jest.useFakeTimers().setSystemTime(new Date('2026-03-26T12:00:00Z'));
    try {
      const settings = {
        sharePhase, sharingPaused,
        privateDate: privateDate ? new Date(`${privateDate}T00:00:00Z`) : null,
        sharePrediction: false, sharePeriodDates: false, shareFertileWindow: false,
        shareFlow: false, sharePain: false, shareMood: false, shareEnergy: false,
        shareSleep: false, shareSymptoms: false, shareNotes: false,
      };
      const prisma = {
        membership: { findFirst: jest.fn().mockResolvedValue({ userId: 'synthetic-tracker' }) },
        cycleProfile: { findUnique: jest.fn().mockResolvedValue({ timezone: 'Europe/Berlin' }) },
        sharingSettings: { findUnique: jest.fn().mockResolvedValue(settings) },
        periodEvent: { findMany: jest.fn().mockResolvedValue([
          { startDate: new Date('2026-03-26T00:00:00Z'), endDate: null },
        ]) },
        dailyLog: { findUnique: jest.fn().mockResolvedValue(null) },
      };
      const predictions = { calculateForUser: jest.fn().mockResolvedValue({
        available: false, confidence: 'UNAVAILABLE', reason,
      }) };
      const service = new PartnerService(prisma as never, predictions as never, {} as never);
      const result = await service.dashboard({
        userId: 'synthetic-partner', sessionId: 'synthetic-session',
        householdId: 'synthetic-household', membershipId: 'synthetic-membership',
        role: UserRole.PARTNER, username: 'synthetic', displayName: 'Synthetic', preferredLanguage: 'DE',
      });
      expect(result.phase).toBe(expected);
      expect(result.periodDates).toEqual([]);
      expect(result.prediction).toBeNull();
    } finally { jest.useRealTimers(); }
  });

  it('derives tracker and sharing scope only from the authenticated household', async () => {
    const membershipFindFirst = jest.fn().mockResolvedValue({ userId: 'tracker-in-household' });
    const sharingFindUnique = jest.fn().mockResolvedValue({
      sharePrediction: true, sharePeriodDates: false, sharePhase: false, shareFlow: false,
      sharePain: false, shareMood: false, shareEnergy: false, shareSleep: false,
      shareSymptoms: false, shareNotes: false, shareFertileWindow: false,
      sharingPaused: false, privateDate: null,
    });
    const prisma = {
      membership: { findFirst: membershipFindFirst },
      cycleProfile: { findUnique: jest.fn().mockResolvedValue({ timezone: 'Europe/Berlin' }) },
      sharingSettings: { findUnique: sharingFindUnique },
      periodEvent: { findMany: jest.fn().mockResolvedValue([]) },
      dailyLog: {
        findUnique: jest.fn().mockResolvedValue({
          deletedAt: null,
          flowLevel: null,
          painLevel: null,
          mood: null,
          energy: null,
          sleepQuality: null,
          customSymptoms: [],
          encryptedNote: 'intentionally-invalid-ciphertext',
        }),
      },
    };
    const predictions = {
      calculateForUser: jest.fn().mockResolvedValue({
        available: false, confidence: 'UNAVAILABLE', reason: 'INSUFFICIENT_DATA',
      }),
    };
    const encryption = { decrypt: jest.fn(() => { throw new Error('must not decrypt'); }) };
    const service = new PartnerService(prisma as never, predictions as never, encryption as never);
    await service.dashboard({
      userId: 'partner', sessionId: 'session', householdId: 'household-a',
      membershipId: 'membership', role: UserRole.PARTNER, username: 'p',
      displayName: 'P', preferredLanguage: 'DE',
    });
    expect(membershipFindFirst).toHaveBeenCalledWith({
      where: { householdId: 'household-a', role: UserRole.TRACKER },
      select: { userId: true },
    });
    expect(sharingFindUnique).toHaveBeenCalledWith({
      where: {
        ownerUserId_householdId: {
          ownerUserId: 'tracker-in-household', householdId: 'household-a',
        },
      },
    });
    expect(predictions.calculateForUser).toHaveBeenCalledWith('tracker-in-household');
    expect(encryption.decrypt).not.toHaveBeenCalled();
  });
});
