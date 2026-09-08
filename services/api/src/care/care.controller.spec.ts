import { PreferredLanguage, UserRole } from '@prisma/client';
import { CareController } from './care.controller.js';

const tracker = {
  userId: 'tracker-id',
  sessionId: 'session-id',
  householdId: 'source-household-id',
  membershipId: 'membership-id',
  role: UserRole.TRACKER,
  username: 'tracker',
  displayName: 'Tracker',
  preferredLanguage: PreferredLanguage.DE,
};

describe('CareController membership reauthorization', () => {
  it('does not write preferences when invitation acceptance already moved the tracker', async () => {
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ id: tracker.membershipId }]),
      membership: {
        findUnique: jest.fn().mockResolvedValue({
          userId: tracker.userId,
          householdId: 'target-household-id',
          role: UserRole.PARTNER,
        }),
      },
      carePreferences: { update: jest.fn() },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const controller = new CareController(prisma as never);

    await expect(controller.update(tracker, { likesWarmth: true })).rejects.toMatchObject({
      status: 403,
    });
    expect(tx.carePreferences.update).not.toHaveBeenCalled();
  });
});
