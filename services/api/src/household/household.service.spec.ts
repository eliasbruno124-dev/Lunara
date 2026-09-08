import {
  BadRequestException,
  ConflictException,
  NotFoundException,
} from '@nestjs/common';
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
    const config = { get: jest.fn(), getOrThrow: jest.fn() };
    const service = new HouseholdService(
      prisma as never,
      auth as never,
      config as never,
    );

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
      membership: {
        count: jest.fn().mockResolvedValue(0),
        findUnique: jest.fn().mockResolvedValue({
          userId: tracker.userId,
          householdId: tracker.householdId,
          role: UserRole.TRACKER,
        }),
      },
      invitation: { create: jest.fn() },
      auditEvent: { create: jest.fn() },
    };
    const auth = { hashToken: jest.fn().mockReturnValue('invitation-hash') };
    const config = {
      get: jest.fn().mockReturnValue('30'),
      getOrThrow: jest.fn().mockReturnValue('https://lunara.example'),
    };
    const service = new HouseholdService(
      prisma as never,
      auth as never,
      config as never,
    );

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
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ pg_advisory_xact_lock: null }]),
      membership: {
        count: jest.fn().mockResolvedValue(0),
        findUnique: jest.fn().mockResolvedValue({
          userId: tracker.userId,
          householdId: tracker.householdId,
          role: UserRole.TRACKER,
        }),
      },
      invitation: {
        create: jest.fn().mockResolvedValue({
          id: 'invitation-id',
          assignedRole: UserRole.PARTNER,
          expiresAt: expectedExpiry,
        }),
        updateMany: jest.fn(),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      membership: { count: jest.fn().mockResolvedValue(0) },
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const auth = { hashToken: jest.fn().mockReturnValue('invitation-hash') };
    const config = {
      get: jest.fn().mockReturnValue('30'),
      getOrThrow: jest.fn().mockReturnValue('https://lunara.example'),
    };
    const service = new HouseholdService(
      prisma as never,
      auth as never,
      config as never,
    );

    const invitation = await service.createInvitation(tracker, {});

    expect(invitation.expiresAt).toEqual(expectedExpiry);
    expect(invitation.acceptanceUrl).toMatch(
      /^https:\/\/lunara\.example\/invite#invite=/u,
    );
    expect(tx.invitation.create).toHaveBeenCalledTimes(1);
    expect(tx.invitation.updateMany).toHaveBeenCalledWith({
      where: {
        householdId: tracker.householdId,
        assignedRole: UserRole.PARTNER,
        usedAt: null,
        revokedAt: null,
      },
      data: { revokedAt: expect.any(Date) as Date },
    });
    expect(tx.invitation.create).toHaveBeenCalledWith(
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

  it('creates a longer, single-use invitation without an expiry when requested', async () => {
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ pg_advisory_xact_lock: null }]),
      membership: {
        count: jest.fn().mockResolvedValue(0),
        findUnique: jest.fn().mockResolvedValue({
          userId: tracker.userId,
          householdId: tracker.householdId,
          role: UserRole.TRACKER,
        }),
      },
      invitation: {
        updateMany: jest.fn().mockResolvedValue({ count: 0 }),
        create: jest
          .fn()
          .mockImplementation((input: { data: { expiresAt: Date | null } }) =>
            Promise.resolve({
              id: 'invitation-id',
              assignedRole: UserRole.PARTNER,
              expiresAt: input.data.expiresAt,
            }),
          ),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      membership: { count: jest.fn().mockResolvedValue(0) },
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const auth = { hashToken: jest.fn().mockReturnValue('invitation-hash') };
    const config = {
      get: jest.fn().mockReturnValue('30'),
      getOrThrow: jest.fn().mockReturnValue('https://lunara.example'),
    };
    const service = new HouseholdService(
      prisma as never,
      auth as never,
      config as never,
    );

    const invitation = await service.createInvitation(tracker, {
      neverExpires: true,
    });

    expect(invitation.code).toMatch(/^[A-HJ-NP-Z2-9]{12}$/);
    expect(invitation.expiresAt).toBeNull();
    expect(tx.invitation.updateMany).toHaveBeenCalledWith({
      where: {
        householdId: tracker.householdId,
        assignedRole: UserRole.PARTNER,
        usedAt: null,
        revokedAt: null,
      },
      data: { revokedAt: expect.any(Date) as Date },
    });
    expect(tx.invitation.create).toHaveBeenCalledWith(
      expect.objectContaining<Record<string, unknown>>({
        data: expect.objectContaining<Record<string, unknown>>({
          expiresAt: null,
          assignedRole: UserRole.PARTNER,
        }),
      }),
    );
  });

  it('rejects contradictory finite and permanent lifetime options', async () => {
    const prisma = {
      membership: { count: jest.fn().mockResolvedValue(0) },
      invitation: { create: jest.fn() },
      auditEvent: { create: jest.fn() },
    };
    const auth = { hashToken: jest.fn() };
    const config = { get: jest.fn(), getOrThrow: jest.fn() };
    const service = new HouseholdService(
      prisma as never,
      auth as never,
      config as never,
    );

    await expect(
      service.createInvitation(tracker, {
        expiresInMinutes: 30,
        neverExpires: true,
      }),
    ).rejects.toBeInstanceOf(BadRequestException);
    expect(prisma.invitation.create).not.toHaveBeenCalled();
    expect(auth.hashToken).not.toHaveBeenCalled();
  });
});

describe('HouseholdService permanent invitation claim', () => {
  it('accepts a non-expiring code and still claims it exactly once', async () => {
    const invitation = {
      id: 'invitation-id',
      householdId: 'household-id',
      assignedRole: UserRole.PARTNER,
      expiresAt: null,
      usedAt: null,
      revokedAt: null,
    };
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ pg_advisory_xact_lock: null }]),
      invitation: {
        findUnique: jest.fn().mockResolvedValue(invitation),
        updateMany: jest.fn().mockResolvedValue({ count: 1 }),
      },
      membership: {
        count: jest.fn().mockResolvedValue(0),
        create: jest.fn().mockResolvedValue({
          role: UserRole.PARTNER,
          householdId: invitation.householdId,
        }),
      },
      user: {
        create: jest.fn().mockResolvedValue({
          id: 'partner-id',
          username: 'partner',
          displayName: 'Partner',
          preferredLanguage: PreferredLanguage.DE,
        }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      invitation: { findUnique: jest.fn().mockResolvedValue(invitation) },
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const authResponse = {
      accessToken: 'access',
      refreshToken: 'refresh',
      tokenType: 'Bearer' as const,
      expiresInSeconds: 900,
      sessionId: 'session-id',
      user: {
        id: 'partner-id',
        username: 'partner',
        displayName: 'Partner',
        preferredLanguage: PreferredLanguage.DE,
        role: UserRole.PARTNER,
        householdId: invitation.householdId,
      },
    };
    const auth = {
      hashToken: jest.fn().mockReturnValue('invitation-hash'),
      normalizeUsername: jest.fn().mockReturnValue('partner'),
      hashPassword: jest.fn().mockResolvedValue('password-hash'),
      createAuthenticatedSession: jest.fn().mockResolvedValue(authResponse),
    };
    const service = new HouseholdService(
      prisma as never,
      auth as never,
      { get: jest.fn() } as never,
    );

    await expect(
      service.join({
        invitationCode: 'AAAAAAAAAAAA',
        username: 'partner',
        password: 'A secure partner password!',
        displayName: 'Partner',
        deviceName: 'Partner phone',
      }),
    ).resolves.toEqual(authResponse);
    expect(tx.invitation.updateMany).toHaveBeenCalledWith({
      where: {
        id: invitation.id,
        usedAt: null,
        revokedAt: null,
        OR: [
          { expiresAt: null },
          { expiresAt: { gt: expect.any(Date) as Date } },
        ],
      },
      data: { usedAt: expect.any(Date) as Date },
    });
    expect(tx.invitation.updateMany).toHaveBeenCalledWith({
      where: {
        householdId: invitation.householdId,
        assignedRole: invitation.assignedRole,
        id: { not: invitation.id },
        usedAt: null,
        revokedAt: null,
      },
      data: { revokedAt: expect.any(Date) as Date },
    });
    expect(auth.createAuthenticatedSession).toHaveBeenCalledTimes(1);
  });
});
