import { UnauthorizedException } from '@nestjs/common';
import {
  DeviceSession,
  IdempotencyKey,
  PreferredLanguage,
  SessionAuthMethod,
  UserRole,
} from '@prisma/client';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { randomUUID } from 'node:crypto';
import { EncryptionService } from '../common/security/encryption.service.js';
import { AuthController } from './auth.controller.js';
import { RefreshDto } from './auth.dto.js';
import { AuthService } from './auth.service.js';

const requestId = 'abbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const oldRefreshToken = 'r'.repeat(64);
const issuer = 'https://auth.example/application/o/lunara/';
const subject = 'test-subject';

/** Transactional unit model; real advisory-lock behavior needs PostgreSQL tests. */
function createHarness(
  authMethod: SessionAuthMethod = SessionAuthMethod.PASSWORD,
) {
  const user = {
    id: randomUUID(),
    username: 'test-tracker',
    displayName: 'Test tracker',
    preferredLanguage: PreferredLanguage.DE,
    disabledAt: null as Date | null,
    memberships: [{ householdId: randomUUID(), role: UserRole.TRACKER }],
  };
  const config = {
    get: jest.fn((_key: string, fallback: unknown) => fallback),
    getOrThrow: jest.fn((key: string) =>
      key === 'APP_MASTER_KEY'
        ? Buffer.alloc(32, 7).toString('base64')
        : 'test-refresh-pepper',
    ),
  };
  const encryption = new EncryptionService(config as never);
  const oidcVerifier = {
    verify: jest.fn().mockImplementation(() =>
      Promise.resolve({
        issuer,
        subject,
        issuedAt: Math.floor(Date.now() / 1_000),
        expiresAt: Math.floor(Date.now() / 1_000) + 300,
      }),
    ),
  };
  let accessTokens = 0;
  const jwt = {
    signAsync: jest
      .fn()
      .mockImplementation(() =>
        Promise.resolve(`fresh-access-${++accessTokens}`),
      ),
  };
  const sessions = new Map<string, DeviceSession>();
  const records = new Map<string, IdempotencyKey>();
  const deviceSession = {
    findUnique: jest.fn(
      ({ where }: { where: { id?: string; refreshTokenHash?: string } }) => {
        const session = where.id
          ? sessions.get(where.id)
          : [...sessions.values()].find(
              (entry) => entry.refreshTokenHash === where.refreshTokenHash,
            );
        return Promise.resolve(
          session
            ? {
                ...session,
                user,
                externalIdentity: session.externalIdentityId
                  ? { issuer, subject }
                  : null,
              }
            : null,
        );
      },
    ),
    create: jest.fn(({ data }: { data: Partial<DeviceSession> }) => {
      const session = {
        id: randomUUID(),
        createdAt: new Date(),
        lastUsedAt: new Date(),
        revokedAt: null,
        ...data,
        externalIdentityId: data.externalIdentityId ?? null,
        rotatedFromId: data.rotatedFromId ?? null,
      } as DeviceSession;
      sessions.set(session.id, session);
      return Promise.resolve(session);
    }),
    updateMany: jest.fn(
      ({
        where,
        data,
      }: {
        where: Partial<DeviceSession>;
        data: Partial<DeviceSession>;
      }) => {
        let count = 0;
        for (const session of sessions.values()) {
          if (
            !Object.entries(where).every(
              ([key, value]) => session[key as keyof DeviceSession] === value,
            )
          )
            continue;
          Object.assign(session, data);
          count++;
        }
        return Promise.resolve({ count });
      },
    ),
  };
  const idempotencyKey = {
    findUnique: jest.fn(
      ({ where }: { where: { userId_key: { userId: string; key: string } } }) =>
        Promise.resolve(
          records.get(`${where.userId_key.userId}:${where.userId_key.key}`) ??
            null,
        ),
    ),
    create: jest.fn(({ data }: { data: Omit<IdempotencyKey, 'createdAt'> }) => {
      const record = { createdAt: new Date(), ...data };
      const key = `${record.userId}:${record.key}`;
      if (records.has(key)) throw new Error('Duplicate replay record');
      records.set(key, record);
      return Promise.resolve(record);
    }),
  };
  const tx = {
    $queryRaw: jest.fn().mockResolvedValue([{ locked: true }]),
    deviceSession,
    idempotencyKey,
    externalIdentity: { update: jest.fn().mockResolvedValue({}) },
    auditEvent: { create: jest.fn().mockResolvedValue({}) },
  };
  let tail: Promise<unknown> = Promise.resolve();
  const prisma = {
    ...tx,
    $transaction: jest.fn(
      (operation: (client: typeof tx) => Promise<unknown>) => {
        const result = tail.then(async () => {
          const beforeSessions = structuredClone(sessions);
          const beforeRecords = structuredClone(records);
          try {
            return await operation(tx);
          } catch (error) {
            sessions.clear();
            records.clear();
            for (const [key, value] of beforeSessions) sessions.set(key, value);
            for (const [key, value] of beforeRecords) records.set(key, value);
            throw error;
          }
        });
        tail = result.catch(() => undefined);
        return result;
      },
    ),
  };
  const service = new AuthService(
    prisma as never,
    config as never,
    jwt as never,
    oidcVerifier as never,
    encryption,
  );
  const original: DeviceSession = {
    id: randomUUID(),
    userId: user.id,
    tokenFamilyId: randomUUID(),
    deviceName: 'Test phone',
    refreshTokenHash: service.hashToken(oldRefreshToken),
    createdAt: new Date(),
    lastUsedAt: new Date(),
    expiresAt: new Date(Date.now() + 30 * 86_400_000),
    revokedAt: null,
    rotatedFromId: null,
    authMethod,
    externalIdentityId:
      authMethod === SessionAuthMethod.OIDC ? randomUUID() : null,
  };
  sessions.set(original.id, original);
  const replay = () =>
    service.refresh(oldRefreshToken, 'fresh-oidc-proof', requestId);
  const cached = () => {
    const record = [...records.values()][0];
    if (!record) throw new Error('Expected cached refresh');
    return record;
  };
  const session = (id: string) => {
    const result = sessions.get(id);
    if (!result) throw new Error('Expected device session');
    return result;
  };
  return {
    service,
    replay,
    cached,
    session,
    user,
    original,
    sessions,
    records,
    tx,
    prisma,
    jwt,
    config,
    encryption,
    oidcVerifier,
  };
}

type Harness = ReturnType<typeof createHarness>;
type RevocationAction = 'logout-token' | 'logout-session' | 'revoke-session';

function authenticatedOriginal(h: Harness) {
  return {
    userId: h.user.id,
    sessionId: h.original.id,
    householdId: h.user.memberships[0]!.householdId,
    membershipId: randomUUID(),
    username: h.user.username,
    displayName: h.user.displayName,
    preferredLanguage: PreferredLanguage.DE,
    role: UserRole.TRACKER,
  };
}

function revokeOriginal(h: Harness, action: RevocationAction) {
  const authenticated = authenticatedOriginal(h);
  if (action === 'logout-token')
    return h.service.logout(authenticated, oldRefreshToken);
  if (action === 'logout-session') return h.service.logout(authenticated);
  return h.service.revokeSession(authenticated, h.original.id);
}

function addOtherDevice(h: Harness) {
  const device = {
    ...h.original,
    id: randomUUID(),
    tokenFamilyId: randomUUID(),
    refreshTokenHash: h.service.hashToken('other-device-refresh-token'),
    revokedAt: null,
  };
  h.sessions.set(device.id, device);
  return device;
}

function barrier() {
  let release = () => {};
  const promise = new Promise<void>((resolve) => {
    release = resolve;
  });
  return { promise, release: () => release() };
}

describe('replay-safe refresh', () => {
  it('recovers a lost response with the same replacement credential and a fresh access token', async () => {
    const h = createHarness();
    const first = await h.replay();
    const retry = await h.replay();
    expect(retry.refreshToken).toBe(first.refreshToken);
    expect(retry.sessionId).toBe(first.sessionId);
    expect(retry.accessToken).not.toBe(first.accessToken);
    expect(h.tx.deviceSession.create).toHaveBeenCalledTimes(1);
    expect(h.sessions.size).toBe(2);
    expect(h.session(first.sessionId).revokedAt).toBeNull();
    expect(h.tx.$queryRaw).toHaveBeenCalledTimes(2);
    expect(h.tx.idempotencyKey.create).toHaveBeenCalledTimes(1);
    expect(h.cached().requestHash).toBe(h.service.hashToken(oldRefreshToken));
    expect(h.cached().expiresAt.getTime()).toBeLessThanOrEqual(
      h.session(first.sessionId).expiresAt.getTime(),
    );
    const stored = JSON.stringify(h.cached());
    for (const credential of [
      oldRefreshToken,
      first.refreshToken,
      first.accessToken,
    ]) {
      expect(stored).not.toContain(credential);
    }
    expect(h.cached().responseBody).toEqual({
      encryptedRefreshReplay: expect.stringMatching(/^v2p\./u) as string,
    });
    const ciphertext = (
      h.cached().responseBody as { encryptedRefreshReplay: string }
    ).encryptedRefreshReplay;
    expect(() => h.encryption.decrypt(ciphertext)).toThrow();
    expect(() =>
      h.encryption.decrypt(ciphertext.replace(/^v2p\./u, 'v1.')),
    ).toThrow();
  });

  it('serializes concurrent identical requests into exactly one rotation', async () => {
    const h = createHarness();
    const responses = await Promise.all(
      Array.from({ length: 8 }, () => h.replay()),
    );
    expect(new Set(responses.map((value) => value.refreshToken)).size).toBe(1);
    expect(new Set(responses.map((value) => value.sessionId)).size).toBe(1);
    expect(h.tx.deviceSession.create).toHaveBeenCalledTimes(1);
    expect(h.tx.idempotencyKey.create).toHaveBeenCalledTimes(1);
    expect(
      [...h.sessions.values()].filter((value) => !value.revokedAt),
    ).toHaveLength(1);
  });

  it('recovers after access-token signing fails after the rotation has committed', async () => {
    const h = createHarness();
    h.jwt.signAsync.mockRejectedValueOnce(new Error('signer unavailable'));
    await expect(h.replay()).rejects.toThrow('signer unavailable');
    expect(h.records.size).toBe(1);
    const replacement = [...h.sessions.values()].find(
      (entry) => !entry.revokedAt,
    );
    const restarted = new AuthService(
      h.prisma as never,
      h.config as never,
      h.jwt as never,
      h.oidcVerifier as never,
      new EncryptionService(h.config as never),
    );
    const retry = await restarted.refresh(
      oldRefreshToken,
      undefined,
      requestId,
    );
    expect(retry.sessionId).toBe(replacement?.id);
    expect(h.service.hashToken(retry.refreshToken)).toBe(
      replacement?.refreshTokenHash,
    );
    expect(h.tx.deviceSession.create).toHaveBeenCalledTimes(1);
  });

  it('rolls back rotation when encryption fails before replay persistence', async () => {
    const h = createHarness();
    jest.spyOn(h.encryption, 'encryptForPurpose').mockImplementationOnce(() => {
      throw new Error('encryption unavailable');
    });
    await expect(h.replay()).rejects.toThrow('encryption unavailable');
    expect(h.session(h.original.id).revokedAt).toBeNull();
    expect(h.sessions.size).toBe(1);
    expect(h.records.size).toBe(0);
    expect(h.tx.idempotencyKey.create).not.toHaveBeenCalled();
  });

  it('rolls back rotation when encrypted replay persistence fails', async () => {
    const h = createHarness();
    h.tx.idempotencyKey.create.mockRejectedValueOnce(
      new Error('database unavailable'),
    );
    await expect(h.replay()).rejects.toThrow('database unavailable');
    expect(h.session(h.original.id).revokedAt).toBeNull();
    expect(h.sessions.size).toBe(1);
    expect(h.records.size).toBe(0);
    await expect(h.replay()).resolves.toHaveProperty('refreshToken');
  });

  it('uses the original refresh hash, not the changing OIDC access proof, as its binding', async () => {
    const h = createHarness(SessionAuthMethod.OIDC);
    const first = await h.replay();
    const retry = await h.service.refresh(
      oldRefreshToken,
      'renewed-provider-token',
      requestId,
    );
    expect(retry.refreshToken).toBe(first.refreshToken);
    expect(h.oidcVerifier.verify).toHaveBeenLastCalledWith(
      'renewed-provider-token',
    );
    expect(h.tx.deviceSession.create).toHaveBeenCalledTimes(1);
  });

  it.each(['issuer', 'subject'])(
    'requires the exact OIDC %s again on a cached retry',
    async (claim) => {
      const h = createHarness(SessionAuthMethod.OIDC);
      const first = await h.replay();
      h.oidcVerifier.verify.mockResolvedValueOnce({
        issuer,
        subject,
        [claim]: 'another-identity',
      });
      await expect(h.replay()).rejects.toBeInstanceOf(UnauthorizedException);
      expect(h.session(first.sessionId).revokedAt).toBeNull();
      expect(h.jwt.signAsync).toHaveBeenCalledTimes(1);
    },
  );

  it('rejects missing or invalid OIDC proof even when the replay is cached', async () => {
    const h = createHarness(SessionAuthMethod.OIDC);
    const first = await h.replay();
    await expect(
      h.service.refresh(oldRefreshToken, undefined, requestId),
    ).rejects.toBeInstanceOf(UnauthorizedException);
    h.oidcVerifier.verify.mockRejectedValueOnce(new UnauthorizedException());
    await expect(h.replay()).rejects.toBeInstanceOf(UnauthorizedException);
    expect(h.session(first.sessionId).revokedAt).toBeNull();
  });

  it('rejects reusing a request ID with its replacement token without revoking it', async () => {
    const h = createHarness();
    const first = await h.replay();
    await expect(
      h.service.refresh(first.refreshToken, undefined, requestId),
    ).rejects.toBeInstanceOf(UnauthorizedException);
    expect(h.session(first.sessionId).revokedAt).toBeNull();
    expect(h.tx.deviceSession.create).toHaveBeenCalledTimes(1);
  });

  it.each([undefined, 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'])(
    'retains reuse protection for a consumed token with a missing or different request ID (%s)',
    async (differentId) => {
      const h = createHarness();
      const first = await h.replay();
      await expect(
        h.service.refresh(oldRefreshToken, undefined, differentId),
      ).rejects.toThrow('Refresh token reuse detected; token family revoked');
      expect(h.session(first.sessionId).revokedAt).not.toBeNull();
      await expect(h.replay()).rejects.toBeInstanceOf(UnauthorizedException);
    },
  );

  it('does not revoke a newer descendant when an older known retry arrives', async () => {
    const h = createHarness();
    const first = await h.replay();
    const second = await h.service.refresh(
      first.refreshToken,
      undefined,
      randomUUID(),
    );
    await expect(h.replay()).rejects.toBeInstanceOf(UnauthorizedException);
    expect(h.session(second.sessionId).revokedAt).toBeNull();
    expect(h.tx.deviceSession.create).toHaveBeenCalledTimes(2);
  });

  it.each([
    'revoked',
    'expired',
    'disabled-user',
    'missing-membership',
    'changed-household',
    'missing-session',
  ])('does not revive an invalid successor: %s', async (state) => {
    const h = createHarness();
    const first = await h.replay();
    const successor = h.session(first.sessionId);
    if (state === 'revoked') successor.revokedAt = new Date();
    if (state === 'expired') successor.expiresAt = new Date(0);
    if (state === 'disabled-user') h.user.disabledAt = new Date();
    if (state === 'missing-membership') h.user.memberships = [];
    if (state === 'changed-household')
      h.user.memberships[0]!.householdId = randomUUID();
    if (state === 'missing-session') h.sessions.delete(first.sessionId);
    await expect(h.replay()).rejects.toBeInstanceOf(UnauthorizedException);
    expect(h.tx.deviceSession.create).toHaveBeenCalledTimes(1);
    expect(h.jwt.signAsync).toHaveBeenCalledTimes(1);
  });

  it.each(['logout', 'revokeSession'] as const)(
    'honors explicit %s after a lost response',
    async (action) => {
      const h = createHarness();
      const first = await h.replay();
      const authenticated = {
        userId: h.user.id,
        sessionId: first.sessionId,
        householdId: first.user.householdId,
        membershipId: randomUUID(),
        username: h.user.username,
        displayName: h.user.displayName,
        preferredLanguage: PreferredLanguage.DE,
        role: UserRole.TRACKER,
      };
      if (action === 'logout') await h.service.logout(authenticated);
      else await h.service.revokeSession(authenticated, first.sessionId);
      await expect(h.replay()).rejects.toBeInstanceOf(UnauthorizedException);
      expect(h.session(first.sessionId).revokedAt).not.toBeNull();
    },
  );

  it.each([
    'expired-cache',
    'wrong-owner',
    'wrong-request',
    'wrong-path',
    'wrong-method',
    'wrong-record-id',
    'wrong-household',
    'extended-expiry',
    'corrupted-ciphertext',
  ])(
    'rejects an invalid replay record without revocation: %s',
    async (state) => {
      const h = createHarness();
      const first = await h.replay();
      const cache = h.cached();
      if (state === 'expired-cache') cache.expiresAt = new Date(0);
      if (state === 'wrong-owner') cache.userId = randomUUID();
      if (state === 'wrong-request') cache.requestHash = '0'.repeat(64);
      if (state === 'wrong-path') cache.path = '/api/v1/sync';
      if (state === 'wrong-method') cache.method = 'DELETE';
      if (state === 'wrong-record-id') cache.id = randomUUID();
      if (state === 'wrong-household') cache.householdId = randomUUID();
      if (state === 'extended-expiry')
        cache.expiresAt = new Date(Date.now() + 31 * 86_400_000);
      if (state === 'corrupted-ciphertext')
        cache.responseBody = {
          encryptedRefreshReplay: 'v1.invalid.invalid.invalid',
        };
      await expect(h.replay()).rejects.toBeInstanceOf(UnauthorizedException);
      expect(h.session(first.sessionId).revokedAt).toBeNull();
      expect(h.tx.deviceSession.create).toHaveBeenCalledTimes(1);
    },
  );

  it('normalizes UUID case for retry identity', async () => {
    const h = createHarness();
    const first = await h.replay();
    const retry = await h.service.refresh(
      oldRefreshToken,
      undefined,
      requestId.toUpperCase(),
    );
    expect(retry.refreshToken).toBe(first.refreshToken);
  });

  it('keeps no-ID clients working without creating replay records', async () => {
    const h = createHarness();
    const first = await h.service.refresh(oldRefreshToken);
    expect(h.records.size).toBe(0);
    expect(h.tx.idempotencyKey.findUnique).not.toHaveBeenCalled();
    await expect(h.service.refresh(oldRefreshToken)).rejects.toThrow(
      'token family revoked',
    );
    expect(h.session(first.sessionId).revokedAt).not.toBeNull();
  });
});

describe.each<RevocationAction>([
  'logout-token',
  'logout-session',
  'revoke-session',
])('device-family revocation using the original credential: %s', (action) => {
  it('revokes a lost-response successor but preserves other device families', async () => {
    const h = createHarness();
    const other = addOtherDevice(h);
    const lost = await h.replay();
    await revokeOriginal(h, action);
    expect(h.session(lost.sessionId).revokedAt).not.toBeNull();
    expect(h.session(other.id).revokedAt).toBeNull();
    await expect(h.replay()).rejects.toBeInstanceOf(UnauthorizedException);
    await expect(
      h.service.refresh(lost.refreshToken, undefined, randomUUID()),
    ).rejects.toBeInstanceOf(UnauthorizedException);
    expect(h.session(other.id).revokedAt).toBeNull();
    expect(h.tx.auditEvent.create).toHaveBeenCalledTimes(1);
    expect(h.tx.$queryRaw).toHaveBeenCalledTimes(4);
    await revokeOriginal(h, action);
    expect(h.tx.auditEvent.create).toHaveBeenCalledTimes(1);
  });

  it('revokes the descendant when refresh holds the transaction lock first', async () => {
    const h = createHarness();
    const other = addOtherDevice(h);
    const entered = barrier();
    const proceed = barrier();
    const create = h.tx.deviceSession.create.getMockImplementation();
    if (!create) throw new Error('Expected session persistence model');
    h.tx.deviceSession.create.mockImplementationOnce(async (input) => {
      entered.release();
      await proceed.promise;
      return create(input);
    });
    const rotation = h.replay();
    await entered.promise;
    const revocation = revokeOriginal(h, action);
    proceed.release();
    const [next] = await Promise.all([rotation, revocation]);
    expect(h.session(next.sessionId).revokedAt).not.toBeNull();
    expect(h.session(other.id).revokedAt).toBeNull();
    await expect(h.replay()).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('prevents rotation when revocation holds the transaction lock first', async () => {
    const h = createHarness();
    const other = addOtherDevice(h);
    const entered = barrier();
    const proceed = barrier();
    const update = h.tx.deviceSession.updateMany.getMockImplementation();
    if (!update) throw new Error('Expected revocation model');
    h.tx.deviceSession.updateMany.mockImplementationOnce(async (input) => {
      entered.release();
      await proceed.promise;
      return update(input);
    });
    const revocation = revokeOriginal(h, action);
    await entered.promise;
    const rotation = h.replay().catch((failure: unknown) => failure);
    proceed.release();
    await revocation;
    expect(await rotation).toBeInstanceOf(UnauthorizedException);
    expect(h.tx.deviceSession.create).not.toHaveBeenCalled();
    expect(h.session(h.original.id).revokedAt).not.toBeNull();
    expect(h.session(other.id).revokedAt).toBeNull();
  });
});

describe('device revocation ownership', () => {
  it('does not allow an account to revoke another account by session ID or refresh token', async () => {
    const h = createHarness();
    const foreign = {
      ...h.original,
      id: randomUUID(),
      userId: randomUUID(),
      tokenFamilyId: randomUUID(),
      refreshTokenHash: h.service.hashToken('foreign-token'),
    };
    h.sessions.set(foreign.id, foreign);
    await h.service.logout(authenticatedOriginal(h), 'foreign-token');
    await h.service.revokeSession(authenticatedOriginal(h), foreign.id);
    expect(h.session(foreign.id).revokedAt).toBeNull();
    expect(h.session(h.original.id).revokedAt).toBeNull();
    expect(h.tx.deviceSession.updateMany).not.toHaveBeenCalled();
    expect(h.tx.auditEvent.create).not.toHaveBeenCalled();
  });

  it('treats absent session IDs and refresh tokens as no-ops', async () => {
    const h = createHarness();
    await h.service.logout(authenticatedOriginal(h), 'unknown-token');
    await h.service.revokeSession(authenticatedOriginal(h), randomUUID());
    expect(h.tx.deviceSession.updateMany).not.toHaveBeenCalled();
    expect(h.tx.auditEvent.create).not.toHaveBeenCalled();
  });
});

describe('refresh replay wire contract', () => {
  it.each([undefined, requestId])(
    'accepts optional UUID request IDs (%s)',
    async (refreshRequestId) => {
      const dto = plainToInstance(RefreshDto, {
        refreshToken: oldRefreshToken,
        refreshRequestId,
      });
      expect(await validate(dto)).toHaveLength(0);
    },
  );

  it('rejects a malformed request ID', async () => {
    const dto = plainToInstance(RefreshDto, {
      refreshToken: oldRefreshToken,
      refreshRequestId: 'not-a-uuid',
    });
    expect((await validate(dto)).map((error) => error.property)).toContain(
      'refreshRequestId',
    );
  });

  it('passes the UUID as the third refresh argument', async () => {
    const service = { refresh: jest.fn().mockResolvedValue({}) };
    await new AuthController(service as never).refresh({
      refreshToken: oldRefreshToken,
      oidcAccessToken: 'provider-proof',
      refreshRequestId: requestId,
    });
    expect(service.refresh).toHaveBeenCalledWith(
      oldRefreshToken,
      'provider-proof',
      requestId,
    );
  });
});
