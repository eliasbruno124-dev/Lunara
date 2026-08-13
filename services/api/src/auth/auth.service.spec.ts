import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { AuditEventType, PreferredLanguage, UserRole } from '@prisma/client';
import * as argon2 from 'argon2';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { AuthService } from './auth.service.js';

const authenticatedUser: AuthenticatedUser = {
  userId: '22222222-2222-4222-8222-222222222222',
  sessionId: '33333333-3333-4333-8333-333333333333',
  householdId: '11111111-1111-4111-8111-111111111111',
  membershipId: '44444444-4444-4444-8444-444444444444',
  role: UserRole.TRACKER,
  username: 'tracker',
  displayName: 'Tracker',
  preferredLanguage: PreferredLanguage.DE,
};

const currentPassword = 'Current password 2026!';
const newPassword = 'Replacement password 2026!';
const currentTokenFamilyId = '55555555-5555-4555-8555-555555555555';

interface PasswordUpdateInput {
  data: { passwordHash: string };
}

function createService(prisma: Record<string, unknown>): AuthService {
  const config = {
    get: jest.fn((_key: string, defaultValue: unknown) => defaultValue),
    getOrThrow: jest.fn().mockReturnValue('test-secret'),
  };
  const jwt = { signAsync: jest.fn() };
  return new AuthService(prisma as never, config as never, jwt as never);
}

describe('AuthService.changePassword', () => {
  let currentHash: string;

  beforeAll(async () => {
    currentHash = await argon2.hash(currentPassword, {
      type: argon2.argon2id,
      memoryCost: 65_536,
      timeCost: 3,
      parallelism: 1,
      hashLength: 32,
    });
  });

  it('atomically changes the hash, preserves the current session, revokes other sessions, and audits success', async () => {
    const updatePassword = jest.fn(
      (input: PasswordUpdateInput): Promise<{ count: number }> => {
        void input;
        return Promise.resolve({ count: 1 });
      },
    );
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ pg_advisory_xact_lock: null }]),
      user: { updateMany: updatePassword },
      deviceSession: {
        findUnique: jest.fn().mockResolvedValue({
          userId: authenticatedUser.userId,
          tokenFamilyId: currentTokenFamilyId,
        }),
        updateMany: jest.fn().mockResolvedValue({ count: 2 }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      user: {
        findUnique: jest
          .fn()
          .mockResolvedValue({ passwordHash: currentHash, disabledAt: null }),
      },
      auditEvent: { create: jest.fn() },
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const service = createService(prisma);

    await service.changePassword(authenticatedUser, {
      currentPassword,
      newPassword,
    });

    expect(tx.deviceSession.findUnique).toHaveBeenCalledWith({
      where: { id: authenticatedUser.sessionId },
      select: { userId: true, tokenFamilyId: true },
    });
    expect(tx.user.updateMany).toHaveBeenCalledWith({
      where: {
        id: authenticatedUser.userId,
        passwordHash: currentHash,
        disabledAt: null,
      },
      data: { passwordHash: expect.any(String) as string },
    });
    const replacementHash = updatePassword.mock.calls[0]?.[0].data.passwordHash;
    if (!replacementHash)
      throw new Error('Expected the replacement hash to be persisted');
    await expect(argon2.verify(replacementHash, newPassword)).resolves.toBe(
      true,
    );
    expect(tx.deviceSession.updateMany).toHaveBeenCalledWith({
      where: {
        userId: authenticatedUser.userId,
        tokenFamilyId: { not: currentTokenFamilyId },
        revokedAt: null,
        expiresAt: { gt: expect.any(Date) as Date },
      },
      data: { revokedAt: expect.any(Date) as Date },
    });
    expect(tx.auditEvent.create).toHaveBeenCalledWith({
      data: {
        householdId: authenticatedUser.householdId,
        actorUserId: authenticatedUser.userId,
        type: AuditEventType.PASSWORD_CHANGED,
        metadata: { revokedSessionCount: 2 },
      },
    });
    expect(prisma.auditEvent.create).not.toHaveBeenCalled();
  });

  it('uses the same generic forbidden response for a bad current password and a compare-and-set race', async () => {
    const badCurrentPrisma = {
      user: {
        findUnique: jest
          .fn()
          .mockResolvedValue({ passwordHash: currentHash, disabledAt: null }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
      $transaction: jest.fn(),
    };
    const badCurrentService = createService(badCurrentPrisma);
    const badCurrentError: unknown = await badCurrentService
      .changePassword(authenticatedUser, {
        currentPassword: 'Incorrect current password!',
        newPassword: 'Incorrect current password!',
      })
      .catch((error: unknown) => error);

    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ pg_advisory_xact_lock: null }]),
      user: { updateMany: jest.fn().mockResolvedValue({ count: 0 }) },
      deviceSession: {
        findUnique: jest.fn().mockResolvedValue({
          userId: authenticatedUser.userId,
          tokenFamilyId: currentTokenFamilyId,
        }),
        updateMany: jest.fn(),
      },
      auditEvent: { create: jest.fn() },
    };
    const racePrisma = {
      user: {
        findUnique: jest
          .fn()
          .mockResolvedValue({ passwordHash: currentHash, disabledAt: null }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const raceService = createService(racePrisma);
    const raceError: unknown = await raceService
      .changePassword(authenticatedUser, { currentPassword, newPassword })
      .catch((error: unknown) => error);

    expect(badCurrentError).toBeInstanceOf(ForbiddenException);
    expect(raceError).toBeInstanceOf(ForbiddenException);
    if (
      !(badCurrentError instanceof ForbiddenException) ||
      !(raceError instanceof ForbiddenException)
    ) {
      throw new Error('Expected both password-change failures to be forbidden');
    }
    expect(raceError.getResponse()).toEqual(badCurrentError.getResponse());
    expect(badCurrentPrisma.$transaction).not.toHaveBeenCalled();
    expect(tx.deviceSession.updateMany).not.toHaveBeenCalled();
    expect(tx.auditEvent.create).not.toHaveBeenCalled();
    expect(badCurrentPrisma.auditEvent.create).toHaveBeenCalledWith({
      data: {
        householdId: authenticatedUser.householdId,
        actorUserId: authenticatedUser.userId,
        type: AuditEventType.PASSWORD_CHANGE_FAILED,
        metadata: {},
      },
    });
    expect(racePrisma.auditEvent.create).toHaveBeenCalledWith({
      data: {
        householdId: authenticatedUser.householdId,
        actorUserId: authenticatedUser.userId,
        type: AuditEventType.PASSWORD_CHANGE_FAILED,
        metadata: {},
      },
    });
  });

  it('rejects an unchanged verified password before the transaction and records a failed attempt', async () => {
    const prisma = {
      user: {
        findUnique: jest
          .fn()
          .mockResolvedValue({ passwordHash: currentHash, disabledAt: null }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
      $transaction: jest.fn(),
    };
    const service = createService(prisma);

    await expect(
      service.changePassword(authenticatedUser, {
        currentPassword,
        newPassword: currentPassword,
      }),
    ).rejects.toBeInstanceOf(BadRequestException);
    expect(prisma.user.findUnique).toHaveBeenCalledWith({
      where: { id: authenticatedUser.userId },
      select: { passwordHash: true, disabledAt: true },
    });
    expect(prisma.$transaction).not.toHaveBeenCalled();
    expect(prisma.auditEvent.create).toHaveBeenCalledWith({
      data: {
        householdId: authenticatedUser.householdId,
        actorUserId: authenticatedUser.userId,
        type: AuditEventType.PASSWORD_CHANGE_FAILED,
        metadata: {},
      },
    });
  });
});
