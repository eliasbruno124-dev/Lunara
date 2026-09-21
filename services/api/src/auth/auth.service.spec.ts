import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  UnauthorizedException,
} from '@nestjs/common';
import {
  AuditEventType,
  PreferredLanguage,
  SessionAuthMethod,
  UserRole,
} from '@prisma/client';
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
  const oidcVerifier = { verify: jest.fn() };
  return new AuthService(
    prisma as never,
    config as never,
    jwt as never,
    oidcVerifier as never,
    {} as never,
  );
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

  it('fails generically when local password login is disabled', async () => {
    const prisma = {
      user: {
        findUnique: jest
          .fn()
          .mockResolvedValue({ passwordHash: null, disabledAt: null }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
      $transaction: jest.fn(),
    };
    const service = createService(prisma);

    await expect(
      service.changePassword(authenticatedUser, {
        currentPassword,
        newPassword,
      }),
    ).rejects.toBeInstanceOf(ForbiddenException);
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

describe('AuthService.login with an OIDC-only account', () => {
  it('does dummy password work and returns the normal generic login failure', async () => {
    const prisma = {
      user: {
        findUnique: jest.fn().mockResolvedValue({
          id: authenticatedUser.userId,
          username: authenticatedUser.username,
          passwordHash: null,
          disabledAt: null,
          memberships: [{ householdId: authenticatedUser.householdId }],
        }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
      $transaction: jest.fn(),
    };
    const service = createService(prisma);

    const error: unknown = await service
      .login({
        username: authenticatedUser.username,
        password: currentPassword,
        deviceName: 'Pixel',
      })
      .catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(UnauthorizedException);
    expect((error as Error).message).toBe('Invalid username or password');
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });
});

describe('AuthService.refresh OIDC revalidation', () => {
  const refreshUserId = '77777777-7777-4777-8777-777777777777';
  const sessionId = '88888888-8888-4888-8888-888888888888';
  const replacementSessionId = '99999999-9999-4999-8999-999999999999';
  const externalIdentityId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  const tokenFamilyId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  const issuer = 'https://auth.example/application/o/lunara/';
  const subject = 'authentik-subject-123';
  const refreshToken = 'r'.repeat(64);
  const oidcAccessToken = 'o'.repeat(128);

  function createRefreshHarness(
    authMethod: SessionAuthMethod,
    revokedAt: Date | null = null,
  ) {
    const relation =
      authMethod === SessionAuthMethod.OIDC
        ? { issuer, subject }
        : null;
    const linkedIdentityId =
      authMethod === SessionAuthMethod.OIDC ? externalIdentityId : null;
    const initial = {
      id: sessionId,
      userId: refreshUserId,
      authMethod,
      externalIdentityId: linkedIdentityId,
      externalIdentity: relation,
    };
    const tokenRecord = {
      id: sessionId,
      tokenFamilyId,
      revokedAt,
      authMethod,
      externalIdentityId: linkedIdentityId,
      externalIdentity: relation,
    };
    const old = {
      id: sessionId,
      userId: refreshUserId,
      tokenFamilyId,
      deviceName: 'Pixel',
      expiresAt: new Date(Date.now() + 60_000),
      authMethod,
      externalIdentityId: linkedIdentityId,
      user: {
        id: refreshUserId,
        username: 'tracker-a',
        displayName: 'Tracker A',
        preferredLanguage: PreferredLanguage.DE,
        disabledAt: null,
        memberships: [
          { householdId: authenticatedUser.householdId, role: UserRole.TRACKER },
        ],
      },
    };
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      deviceSession: {
        findUnique: jest
          .fn()
          .mockResolvedValueOnce(tokenRecord)
          .mockResolvedValueOnce(old),
        updateMany: jest.fn().mockResolvedValue({ count: 1 }),
        create: jest.fn().mockResolvedValue({ id: replacementSessionId }),
      },
      externalIdentity: { update: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      deviceSession: { findUnique: jest.fn().mockResolvedValue(initial) },
      $transaction: jest.fn(
        (operation: (database: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const config = {
      get: jest.fn((_key: string, defaultValue: unknown) => defaultValue),
      getOrThrow: jest.fn().mockReturnValue('refresh-test-pepper'),
    };
    const jwt = { signAsync: jest.fn().mockResolvedValue('access-token') };
    const oidcVerifier = {
      verify: jest.fn().mockResolvedValue({
        issuer,
        subject,
        issuedAt: 1_777_000_000,
        expiresAt: 1_777_000_300,
      }),
    };
    const service = new AuthService(
      prisma as never,
      config as never,
      jwt as never,
      oidcVerifier as never,
      {} as never,
    );
    return { service, prisma, tx, oidcVerifier };
  }

  it('rejects an OIDC refresh without a fresh provider token before rotation', async () => {
    const harness = createRefreshHarness(SessionAuthMethod.OIDC);

    await expect(harness.service.refresh(refreshToken)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
    expect(harness.oidcVerifier.verify).not.toHaveBeenCalled();
    expect(harness.prisma.$transaction).not.toHaveBeenCalled();
    expect(harness.tx.deviceSession.create).not.toHaveBeenCalled();
  });

  it('rejects an invalid OIDC token before rotation', async () => {
    const harness = createRefreshHarness(SessionAuthMethod.OIDC);
    harness.oidcVerifier.verify.mockRejectedValueOnce(
      new UnauthorizedException('invalid provider token'),
    );

    await expect(
      harness.service.refresh(refreshToken, oidcAccessToken),
    ).rejects.toMatchObject({
      message: 'Invalid or expired refresh token',
    });
    expect(harness.prisma.$transaction).not.toHaveBeenCalled();
    expect(harness.tx.deviceSession.create).not.toHaveBeenCalled();
  });

  it('rejects a valid token belonging to a different exact subject', async () => {
    const harness = createRefreshHarness(SessionAuthMethod.OIDC);
    harness.oidcVerifier.verify.mockResolvedValueOnce({
      issuer,
      subject: 'another-subject',
      issuedAt: 1_777_000_000,
      expiresAt: 1_777_000_300,
    });

    await expect(
      harness.service.refresh(refreshToken, oidcAccessToken),
    ).rejects.toBeInstanceOf(UnauthorizedException);
    expect(harness.prisma.$transaction).not.toHaveBeenCalled();
    expect(harness.tx.deviceSession.create).not.toHaveBeenCalled();
  });

  it('rotates an OIDC session only after an exact proof and preserves its authentication binding', async () => {
    const harness = createRefreshHarness(SessionAuthMethod.OIDC);

    await expect(
      harness.service.refresh(refreshToken, oidcAccessToken),
    ).resolves.toMatchObject({
      accessToken: 'access-token',
      sessionId: replacementSessionId,
    });
    expect(harness.oidcVerifier.verify).toHaveBeenCalledWith(oidcAccessToken);
    expect(harness.tx.deviceSession.updateMany).toHaveBeenCalledWith({
      where: { id: sessionId, revokedAt: null },
      data: {
        revokedAt: expect.any(Date) as Date,
        lastUsedAt: expect.any(Date) as Date,
      },
    });
    expect(harness.tx.deviceSession.create).toHaveBeenCalledWith({
      data: expect.objectContaining({
        authMethod: SessionAuthMethod.OIDC,
        externalIdentityId,
        rotatedFromId: sessionId,
        tokenFamilyId,
      }) as unknown,
    });
    expect(harness.tx.externalIdentity.update).toHaveBeenCalledWith({
      where: { id: externalIdentityId },
      data: { lastUsedAt: expect.any(Date) as Date },
    });
  });

  it('keeps refresh-token reuse detection for OIDC token families', async () => {
    const harness = createRefreshHarness(
      SessionAuthMethod.OIDC,
      new Date('2026-08-25T12:00:00.000Z'),
    );

    await expect(
      harness.service.refresh(refreshToken, oidcAccessToken),
    ).rejects.toMatchObject({
      message: 'Refresh token reuse detected; token family revoked',
    });
    expect(harness.tx.deviceSession.updateMany).toHaveBeenCalledWith({
      where: { tokenFamilyId, revokedAt: null },
      data: { revokedAt: expect.any(Date) as Date },
    });
    expect(harness.tx.deviceSession.create).not.toHaveBeenCalled();
  });

  it('leaves password-session rotation unchanged and ignores OIDC proof', async () => {
    const harness = createRefreshHarness(SessionAuthMethod.PASSWORD);

    await expect(harness.service.refresh(refreshToken)).resolves.toMatchObject({
      accessToken: 'access-token',
      sessionId: replacementSessionId,
    });
    expect(harness.oidcVerifier.verify).not.toHaveBeenCalled();
    expect(harness.tx.deviceSession.create).toHaveBeenCalledWith({
      data: expect.objectContaining({
        authMethod: SessionAuthMethod.PASSWORD,
        externalIdentityId: undefined,
      }) as unknown,
    });
    expect(harness.tx.externalIdentity.update).not.toHaveBeenCalled();
  });
});

describe('AuthService.setup', () => {
  it('fails generically without a configured bootstrap token', async () => {
    const prisma = { $transaction: jest.fn() };
    const service = createService(prisma);

    const error: unknown = await service
      .setup({
        bootstrapToken: 'request-token-with-enough-length',
        username: 'first-tracker',
        password: 'A secure setup password!',
        displayName: 'First tracker',
        deviceName: 'Setup device',
      })
      .catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(UnauthorizedException);
    expect((error as Error).message).toBe(
      'Setup token is invalid or setup is unavailable',
    );
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });

  it('also fails generically when the configured bootstrap token is too short', async () => {
    const prisma = { $transaction: jest.fn() };
    const config = {
      get: jest.fn((key: string, defaultValue: unknown) =>
        key === 'BOOTSTRAP_TOKEN' ? 'short' : defaultValue,
      ),
    };
    const service = new AuthService(
      prisma as never,
      config as never,
      {} as never,
      {} as never,
      {} as never,
    );

    const error: unknown = await service
      .setup({
        bootstrapToken: 'request-token-with-enough-length',
        username: 'first-tracker',
        password: 'A secure setup password!',
        displayName: 'First tracker',
        deviceName: 'Setup device',
      })
      .catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(UnauthorizedException);
    expect((error as Error).message).toBe(
      'Setup token is invalid or setup is unavailable',
    );
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });

  it('creates the first account and marks bootstrap complete in one transaction', async () => {
    const bootstrapToken = 'bootstrap-token-that-is-long-enough';
    const householdId = '66666666-6666-4666-8666-666666666666';
    const userId = '77777777-7777-4777-8777-777777777777';
    const sessionId = '88888888-8888-4888-8888-888888888888';
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([
        { pg_advisory_xact_lock: null },
      ]),
      serverState: {
        findUniqueOrThrow: jest
          .fn()
          .mockResolvedValue({ bootstrappedAt: null }),
        updateMany: jest.fn().mockResolvedValue({ count: 1 }),
      },
      household: { create: jest.fn().mockResolvedValue({ id: householdId }) },
      user: {
        create: jest.fn().mockResolvedValue({
          id: userId,
          username: 'first-tracker',
          displayName: 'First tracker',
          preferredLanguage: PreferredLanguage.DE,
        }),
      },
      membership: {
        create: jest.fn().mockResolvedValue({
          role: UserRole.TRACKER,
          householdId,
        }),
      },
      cycleProfile: { create: jest.fn().mockResolvedValue({}) },
      carePreferences: { create: jest.fn().mockResolvedValue({}) },
      sharingSettings: { create: jest.fn().mockResolvedValue({}) },
      deviceSession: {
        create: jest.fn().mockResolvedValue({ id: sessionId }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const config = {
      get: jest.fn((key: string, defaultValue: unknown) =>
        key === 'BOOTSTRAP_TOKEN' ? bootstrapToken : defaultValue,
      ),
      getOrThrow: jest.fn().mockReturnValue('setup-test-refresh-pepper'),
    };
    const jwt = { signAsync: jest.fn().mockResolvedValue('access-token') };
    const service = new AuthService(
      prisma as never,
      config as never,
      jwt as never,
      {} as never,
      {} as never,
    );

    await service.setup({
      bootstrapToken,
      username: 'first-tracker',
      password: 'A secure setup password!',
      displayName: 'First tracker',
      deviceName: 'Setup device',
    });

    expect(tx.$queryRaw).toHaveBeenCalledTimes(1);
    expect(tx.serverState.findUniqueOrThrow).toHaveBeenCalledWith({
      where: { id: 1 },
      select: { bootstrappedAt: true },
    });
    expect(tx.serverState.updateMany).toHaveBeenCalledWith({
      where: { id: 1, bootstrappedAt: null },
      data: { bootstrappedAt: expect.any(Date) as Date },
    });
    expect(tx.auditEvent.create.mock.invocationCallOrder[0]).toBeLessThan(
      tx.serverState.updateMany.mock.invocationCallOrder[0] ?? 0,
    );
    expect(prisma.$transaction).toHaveBeenCalledTimes(1);
  });
});

describe('AuthService.register', () => {
  it('rejects registration before password hashing or database access when disabled', async () => {
    const prisma = { $transaction: jest.fn() };
    const service = createService(prisma);

    await expect(
      service.register({
        username: 'new-tracker',
        password: 'A secure registration password!',
        displayName: 'New tracker',
        deviceName: 'Registration device',
      }),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });

  it('requires the one-time bootstrap before open registration on an empty server', async () => {
    const prisma = {
      serverState: {
        findUniqueOrThrow: jest
          .fn()
          .mockResolvedValue({ bootstrappedAt: null }),
      },
      $transaction: jest.fn(),
    };
    const config = {
      get: jest.fn((key: string, defaultValue: unknown) =>
        key === 'lunara.registrationEnabled' ? true : defaultValue,
      ),
    };
    const service = new AuthService(
      prisma as never,
      config as never,
      {} as never,
      {} as never,
      {} as never,
    );

    await expect(
      service.register({
        username: 'new-tracker',
        password: 'A secure registration password!',
        displayName: 'New tracker',
        deviceName: 'Registration device',
      }),
    ).rejects.toBeInstanceOf(ConflictException);
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });

  it('creates an isolated TRACKER relationship even when all earlier users were deleted after bootstrap', async () => {
    const householdId = '66666666-6666-4666-8666-666666666666';
    const userId = '77777777-7777-4777-8777-777777777777';
    const sessionId = '88888888-8888-4888-8888-888888888888';
    let storedPasswordHash = '';
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([
        { pg_advisory_xact_lock: null },
      ]),
      serverState: {
        findUniqueOrThrow: jest.fn().mockResolvedValue({
          bootstrappedAt: new Date('2026-08-13T12:00:00.000Z'),
        }),
      },
      user: {
        create: jest.fn((input: { data: { passwordHash: string } }) => {
          storedPasswordHash = input.data.passwordHash;
          return Promise.resolve({
            id: userId,
            username: 'new-tracker',
            displayName: 'New tracker',
            preferredLanguage: PreferredLanguage.DE,
          });
        }),
      },
      household: {
        create: jest.fn().mockResolvedValue({ id: householdId }),
      },
      membership: {
        create: jest.fn().mockResolvedValue({
          role: UserRole.TRACKER,
          householdId,
        }),
      },
      cycleProfile: { create: jest.fn().mockResolvedValue({}) },
      carePreferences: { create: jest.fn().mockResolvedValue({}) },
      sharingSettings: { create: jest.fn().mockResolvedValue({}) },
      deviceSession: {
        create: jest.fn().mockResolvedValue({ id: sessionId }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      // There are intentionally no User.count mocks: registration is governed
      // by the persistent lifecycle marker, not current account cardinality.
      serverState: {
        findUniqueOrThrow: jest.fn().mockResolvedValue({
          bootstrappedAt: new Date('2026-08-13T12:00:00.000Z'),
        }),
      },
      $transaction: jest.fn(
        (operation: (client: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const config = {
      get: jest.fn((key: string, defaultValue: unknown) =>
        key === 'lunara.registrationEnabled' ? true : defaultValue,
      ),
      getOrThrow: jest.fn().mockReturnValue('registration-test-pepper'),
    };
    const jwt = { signAsync: jest.fn().mockResolvedValue('access-token') };
    const service = new AuthService(
      prisma as never,
      config as never,
      jwt as never,
      {} as never,
      {} as never,
    );

    const result = await service.register({
      username: ' New-Tracker ',
      password: 'A secure registration password!',
      displayName: ' New tracker ',
      deviceName: 'Registration device',
    });

    expect(prisma.$transaction).toHaveBeenCalledWith(expect.any(Function));
    expect(tx.$queryRaw).toHaveBeenCalledTimes(1);
    expect(tx.serverState.findUniqueOrThrow).toHaveBeenCalledWith({
      where: { id: 1 },
      select: { bootstrappedAt: true },
    });
    expect(tx.household.create).toHaveBeenCalledWith({
      data: { name: 'Private Beziehung' },
    });
    expect(tx.membership.create).toHaveBeenCalledWith({
      data: { householdId, userId, role: UserRole.TRACKER },
    });
    expect(tx.cycleProfile.create).toHaveBeenCalledWith({ data: { userId } });
    expect(tx.carePreferences.create).toHaveBeenCalledWith({
      data: { userId },
    });
    expect(tx.sharingSettings.create).toHaveBeenCalledWith({
      data: { ownerUserId: userId, householdId },
    });
    expect(storedPasswordHash).not.toContain('A secure registration password!');
    await expect(
      argon2.verify(storedPasswordHash, 'A secure registration password!'),
    ).resolves.toBe(true);
    expect(result.user).toEqual(
      expect.objectContaining({
        id: userId,
        username: 'new-tracker',
        role: UserRole.TRACKER,
        householdId,
      }),
    );
    expect(tx.auditEvent.create).toHaveBeenCalledWith({
      data: {
        householdId,
        actorUserId: userId,
        type: AuditEventType.NEW_DEVICE,
        metadata: { sessionId },
      },
    });
  });
});
