import {
  ConflictException,
  ForbiddenException,
  UnauthorizedException,
} from '@nestjs/common';
import { PreferredLanguage, SessionAuthMethod, UserRole } from '@prisma/client';
import * as argon2 from 'argon2';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { AuthResponse } from './auth.service.js';
import { OidcService } from './oidc.service.js';

const userId = '22222222-2222-4222-8222-222222222222';
const householdId = '11111111-1111-4111-8111-111111111111';
const identityId = '33333333-3333-4333-8333-333333333333';
const issuer = 'https://auth.example/application/o/lunara/';
const subject = 'authentik-user-id-123';
const currentPassword = 'existing password';

const user: AuthenticatedUser = {
  userId,
  householdId,
  membershipId: '44444444-4444-4444-8444-444444444444',
  sessionId: '55555555-5555-4555-8555-555555555555',
  username: 'tracker-a',
  displayName: 'Tracker A',
  preferredLanguage: PreferredLanguage.DE,
  role: UserRole.TRACKER,
};

const authResponse: AuthResponse = {
  accessToken: 'lunara-access-token',
  refreshToken: 'lunara-refresh-token',
  tokenType: 'Bearer',
  expiresInSeconds: 900,
  sessionId: '66666666-6666-4666-8666-666666666666',
  user: {
    id: userId,
    username: 'tracker-a',
    displayName: 'Tracker A',
    preferredLanguage: PreferredLanguage.DE,
    role: UserRole.TRACKER,
    householdId,
  },
};

const verifiedIdentity = {
  issuer,
  subject,
  issuedAt: 1_777_000_000,
  expiresAt: 1_777_000_300,
};

function verifier() {
  return {
    verify: jest.fn().mockResolvedValue(verifiedIdentity),
    publicConfiguration: jest.fn().mockReturnValue({
      enabled: true,
      providerName: 'Authentik',
      issuer,
      audience: 'lunara-mobile',
    }),
  };
}

describe('OidcService.login', () => {
  it('never auto-provisions an unknown subject', async () => {
    const prisma = {
      externalIdentity: { findUnique: jest.fn().mockResolvedValue(null) },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
      $transaction: jest.fn(),
    };
    const auth = { createAuthenticatedSession: jest.fn() };
    const service = new OidcService(
      prisma as never,
      verifier() as never,
      auth as never,
    );

    await expect(
      service.login({ accessToken: 'token', deviceName: 'Pixel' }),
    ).rejects.toBeInstanceOf(UnauthorizedException);
    expect(prisma.$transaction).not.toHaveBeenCalled();
    expect(auth.createAuthenticatedSession).not.toHaveBeenCalled();
    expect(prisma.auditEvent.create).toHaveBeenCalledWith({
      data: { type: 'LOGIN_FAILED', metadata: { method: 'OIDC' } },
    });
  });

  it('creates an OIDC session only for the exact linked, active identity', async () => {
    const externalIdentity = {
      id: identityId,
      userId,
      issuer,
      subject,
      user: {
        id: userId,
        username: 'tracker-a',
        displayName: 'Tracker A',
        preferredLanguage: PreferredLanguage.DE,
        disabledAt: null,
        memberships: [{ householdId, role: UserRole.TRACKER }],
      },
    };
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      externalIdentity: {
        findUnique: jest.fn().mockResolvedValue(externalIdentity),
        update: jest.fn().mockResolvedValue({}),
      },
      auditEvent: { createMany: jest.fn().mockResolvedValue({ count: 2 }) },
    };
    const prisma = {
      externalIdentity: {
        findUnique: jest.fn().mockResolvedValue({ id: identityId, userId }),
      },
      auditEvent: { create: jest.fn() },
      $transaction: jest.fn(
        (operation: (database: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const auth = {
      createAuthenticatedSession: jest.fn().mockResolvedValue(authResponse),
    };
    const service = new OidcService(
      prisma as never,
      verifier() as never,
      auth as never,
    );

    await expect(
      service.login({ accessToken: 'token', deviceName: 'Pixel' }),
    ).resolves.toEqual(authResponse);
    expect(auth.createAuthenticatedSession).toHaveBeenCalledWith(
      tx,
      expect.objectContaining({ id: userId, householdId }),
      'Pixel',
      { authMethod: SessionAuthMethod.OIDC, externalIdentityId: identityId },
    );
    expect(tx.externalIdentity.update).toHaveBeenCalledWith({
      where: { id: identityId },
      data: { lastUsedAt: expect.any(Date) as Date },
    });
  });

  it('rejects a linked identity when its Lunara account is disabled', async () => {
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      externalIdentity: {
        findUnique: jest.fn().mockResolvedValue({
          id: identityId,
          issuer,
          subject,
          user: {
            disabledAt: new Date(),
            memberships: [{ householdId, role: UserRole.TRACKER }],
          },
        }),
      },
    };
    const prisma = {
      externalIdentity: {
        findUnique: jest.fn().mockResolvedValue({ id: identityId, userId }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
      $transaction: jest.fn(
        (operation: (database: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const auth = { createAuthenticatedSession: jest.fn() };
    const service = new OidcService(
      prisma as never,
      verifier() as never,
      auth as never,
    );

    await expect(
      service.login({ accessToken: 'token', deviceName: 'Pixel' }),
    ).rejects.toBeInstanceOf(UnauthorizedException);
    expect(auth.createAuthenticatedSession).not.toHaveBeenCalled();
  });
});

describe('OidcService.link and status', () => {
  let passwordHash: string;

  beforeAll(async () => {
    passwordHash = await argon2.hash(currentPassword);
  });

  it('requires the current Lunara password before linking', async () => {
    const prisma = {
      user: {
        findUnique: jest
          .fn()
          .mockResolvedValue({ passwordHash, disabledAt: null }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
      $transaction: jest.fn(),
    };
    const service = new OidcService(
      prisma as never,
      verifier() as never,
      {} as never,
    );

    await expect(
      service.link(user, {
        accessToken: 'token',
        currentPassword: 'wrong password',
      }),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });

  it('links only the exact issuer and subject and reports password status', async () => {
    const linkedAt = new Date('2026-08-25T12:00:00.000Z');
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      user: {
        findUnique: jest.fn().mockResolvedValue({
          id: userId,
          passwordHash,
          disabledAt: null,
          memberships: [{ id: user.membershipId }],
        }),
      },
      externalIdentity: {
        findUnique: jest.fn().mockResolvedValue(null),
        create: jest.fn().mockResolvedValue({ id: identityId }),
      },
      auditEvent: { create: jest.fn().mockResolvedValue({}) },
    };
    const prisma = {
      user: {
        findUnique: jest
          .fn()
          .mockResolvedValue({ passwordHash, disabledAt: null }),
        findUniqueOrThrow: jest.fn().mockResolvedValue({
          passwordHash,
          externalIdentities: [{ createdAt: linkedAt, lastUsedAt: null }],
        }),
      },
      auditEvent: { create: jest.fn() },
      $transaction: jest.fn(
        (operation: (database: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const service = new OidcService(
      prisma as never,
      verifier() as never,
      {} as never,
    );

    await expect(
      service.link(user, { accessToken: 'token', currentPassword }),
    ).resolves.toEqual({
      enabled: true,
      providerName: 'Authentik',
      linked: true,
      linkedAt,
      passwordLoginEnabled: true,
    });
    expect(tx.externalIdentity.create).toHaveBeenCalledWith({
      data: { userId, issuer, subject },
    });
    expect(tx.auditEvent.create).toHaveBeenCalledWith({
      data: {
        householdId,
        actorUserId: userId,
        type: 'OIDC_IDENTITY_LINKED',
        metadata: { identityId, administrative: false },
      },
    });
  });

  it('rejects a subject already linked to another Lunara account', async () => {
    const tx = {
      $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
      user: {
        findUnique: jest.fn().mockResolvedValue({
          id: userId,
          passwordHash,
          disabledAt: null,
          memberships: [{ id: user.membershipId }],
        }),
      },
      externalIdentity: {
        findUnique: jest
          .fn()
          .mockResolvedValueOnce({ userId: '77777777-7777-4777-8777-777777777777' })
          .mockResolvedValueOnce(null),
        create: jest.fn(),
      },
    };
    const prisma = {
      user: {
        findUnique: jest
          .fn()
          .mockResolvedValue({ passwordHash, disabledAt: null }),
      },
      auditEvent: { create: jest.fn() },
      $transaction: jest.fn(
        (operation: (database: typeof tx) => Promise<unknown>) => operation(tx),
      ),
    };
    const service = new OidcService(
      prisma as never,
      verifier() as never,
      {} as never,
    );

    await expect(
      service.link(user, { accessToken: 'token', currentPassword }),
    ).rejects.toBeInstanceOf(ConflictException);
    expect(tx.externalIdentity.create).not.toHaveBeenCalled();
  });

  it('returns disabled without exposing provider configuration', async () => {
    const disabledVerifier = {
      publicConfiguration: jest.fn().mockReturnValue({
        enabled: false,
        providerName: 'Authentik',
        issuer: '',
        audience: '',
      }),
    };
    const prisma = {
      user: {
        findUniqueOrThrow: jest.fn().mockResolvedValue({
          passwordHash: null,
          externalIdentities: [],
        }),
      },
    };
    const service = new OidcService(
      prisma as never,
      disabledVerifier as never,
      {} as never,
    );

    await expect(service.status(userId)).resolves.toEqual({
      enabled: false,
      linked: false,
      passwordLoginEnabled: false,
    });
  });
});
