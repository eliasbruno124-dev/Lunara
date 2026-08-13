import { ConflictException } from '@nestjs/common';
import { PreferredLanguage, UserRole } from '@prisma/client';
import { PredictionService } from '../prediction/prediction.service.js';
import { SharingService } from './sharing.service.js';

const user = {
  userId: '11111111-1111-4111-8111-111111111111',
  householdId: '22222222-2222-4222-8222-222222222222',
  membershipId: '33333333-3333-4333-8333-333333333333',
  sessionId: '44444444-4444-4444-8444-444444444444',
  role: UserRole.TRACKER,
  username: 'tracker',
  displayName: 'Tracker',
  preferredLanguage: PreferredLanguage.DE,
};

describe('fertility sharing invariant', () => {
  it('rejects direct fertility sharing before the source estimate is enabled', async () => {
    const transaction = {
      $queryRaw: jest.fn().mockResolvedValue([{ pg_advisory_xact_lock: null }]),
      cycleProfile: {
        findUnique: jest.fn().mockResolvedValue({ fertilityPredictionEnabled: false }),
      },
      sharingSettings: { update: jest.fn() },
      auditEvent: { create: jest.fn() },
    };
    const prisma = {
      $transaction: jest.fn((work: (tx: typeof transaction) => unknown) => work(transaction)),
    };
    const service = new SharingService(prisma as never);

    await expect(service.update(user, { shareFertileWindow: true })).rejects.toBeInstanceOf(
      ConflictException,
    );
    expect(transaction.sharingSettings.update).not.toHaveBeenCalled();
    expect(transaction.$queryRaw).toHaveBeenCalledTimes(1);
  });

  it('revokes fertility sharing in the same transaction when the estimate is disabled', async () => {
    const updatedProfile = { fertilityPredictionEnabled: false };
    const transaction = {
      $queryRaw: jest.fn().mockResolvedValue([{ pg_advisory_xact_lock: null }]),
      cycleProfile: { update: jest.fn().mockResolvedValue(updatedProfile) },
      sharingSettings: { updateMany: jest.fn().mockResolvedValue({ count: 1 }) },
    };
    const prisma = {
      $transaction: jest.fn((work: (tx: typeof transaction) => unknown) => work(transaction)),
    };
    const service = new PredictionService(prisma as never);
    jest.spyOn(service, 'recalculateAndCache').mockResolvedValue({
      available: false,
      confidence: 'UNAVAILABLE',
      algorithmVersion: 'lunara-robust-v1',
      reason: 'INSUFFICIENT_DATA',
    });

    await expect(service.updateProfile(user, { fertilityPredictionEnabled: false }))
      .resolves.toEqual(updatedProfile);
    expect(transaction.sharingSettings.updateMany).toHaveBeenCalledWith({
      where: { ownerUserId: user.userId, householdId: user.householdId },
      data: { shareFertileWindow: false },
    });
    expect(transaction.$queryRaw).toHaveBeenCalledTimes(1);
  });
});
