import { UserRole } from '@prisma/client';
import { PartnerService } from './partner.service.js';

describe('PartnerService household isolation', () => {
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
      dailyLog: { findUnique: jest.fn().mockResolvedValue(null) },
    };
    const predictions = {
      calculateForUser: jest.fn().mockResolvedValue({
        available: false, confidence: 'UNAVAILABLE', reason: 'INSUFFICIENT_DATA',
      }),
    };
    const service = new PartnerService(prisma as never, predictions as never, {} as never);
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
  });
});
