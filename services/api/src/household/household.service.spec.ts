import { ConflictException, NotFoundException } from '@nestjs/common';
import { PreferredLanguage, UserRole } from '@prisma/client';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { HouseholdService } from './household.service.js';

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

describe('HouseholdService invitation preflight', () => {
  it('rejects an unknown invitation before hashing the supplied password', async () => {
    const prisma = {
      invitation: { findUnique: jest.fn().mockResolvedValue(null) },
    };
    const auth = {
      normalizeUsername: jest.fn().mockReturnValue('partner'),
      hashToken: jest.fn().mockReturnValue('invitation-hash'),
      hashPassword: jest.fn(),
    };
    const config = { get: jest.fn() };
    const service = new HouseholdService(prisma as never, auth as never, config as never);

    await expect(
      service.join({
        invitationCode: 'AAAAAAAAAA',
        username: 'partner',
        password: 'A secure partner password!',
        displayName: 'Partner',
        deviceName: 'Phone',
      }),
    ).rejects.toBeInstanceOf(NotFoundException);
    expect(auth.hashPassword).not.toHaveBeenCalled();
  });
});

describe('HouseholdService invitation lifetime policy', () => {
  afterEach(() => {
    jest.useRealTimers();
  });

  it('returns 409 before persistence when the requested lifetime exceeds server policy', async () => {
    const prisma = {
      membership: { count: jest.fn().mockResolvedValue(0) },
      invitation: { create: jest.fn() },
      auditEvent: { create: jest.fn() },
    };
    const auth = { hashToken: jest.fn().mockReturnValue('invitation-hash') };
    const config = { get: jest.fn().mockReturnValue('30') };
    const service = new HouseholdService(prisma as never, auth as never, config as never);

    const error: unknown = await service
      .createInvitation(tracker, { expiresInMinutes: 31 })
      .catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ConflictException);
    expect((error as ConflictException).getStatus()).toBe(409);
    expect(prisma.invitation.create).not.toHaveBeenCalled();
    expect(prisma.auditEvent.create).not.toHaveBeenCalled();
  });

  it('uses the server policy as the default lifetime', async () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-08-12T10:00:00.000Z'));
    const expectedExpiry = new Date('2026-08-12T10:30:00.000Z');
    const prisma = {
      membership: { count: jest.fn().mockResolvedValue(0) },
      invitation: {
        create: jest.fn().mockResolvedValue({
          id: 'invitation-id',
          assignedRole: UserRole.PARTNER,
          expiresAt: expectedExpiry,
        }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
    };
    const auth = { hashToken: jest.fn().mockReturnValue('invitation-hash') };
    const config = { get: jest.fn().mockReturnValue('30') };
    const service = new HouseholdService(prisma as never, auth as never, config as never);

    const invitation = await service.createInvitation(tracker, {});

    expect(invitation.expiresAt).toEqual(expectedExpiry);
    expect(prisma.invitation.create).toHaveBeenCalledTimes(1);
    expect(prisma.invitation.create).toHaveBeenCalledWith(
      expect.objectContaining<Record<string, unknown>>({
        data: expect.objectContaining<Record<string, unknown>>({
          householdId: tracker.householdId,
          assignedRole: UserRole.PARTNER,
          expiresAt: expectedExpiry,
          createdByUserId: tracker.userId,
        }),
      }),
    );
  });
});
