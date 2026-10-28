import 'reflect-metadata';
import { randomUUID } from 'node:crypto';
import type { Server } from 'node:http';
import {
  INestApplication,
  ServiceUnavailableException,
  UnauthorizedException,
  ValidationPipe,
} from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { PreferredLanguage, SessionAuthMethod, UserRole } from '@prisma/client';
import request from 'supertest';
import { AppModule } from '../src/app.module.js';
import { AuthResponse, AuthService } from '../src/auth/auth.service.js';
import {
  OidcTokenVerifier,
  VerifiedOidcIdentity,
} from '../src/auth/oidc-token-verifier.js';
import { lockUserAuthentication } from '../src/common/database/database-coordination.js';
import { PrismaService } from '../src/prisma/prisma.service.js';

const issuer = 'https://synthetic-auth.example/application/o/session-test/';

/** Never permit fixtures or their cleanup against any other database. */
function requireDisposableDatabase(): void {
  const url = new URL(process.env.DATABASE_URL ?? 'http://invalid');
  if (
    process.env.NODE_ENV !== 'test' ||
    !['postgres:', 'postgresql:'].includes(url.protocol) ||
    url.hostname !== '127.0.0.1' ||
    url.port !== '55438' ||
    url.pathname !== '/lunara_test' ||
    (url.search !== '' && url.search !== '?schema=public') ||
    url.hash !== ''
  ) {
    throw new Error(
      'Session persistence tests require NODE_ENV=test and the dedicated loopback database on port 55438 named lunara_test',
    );
  }
}

function barrier() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, release: () => resolve() };
}

describe('Provider-bound session persistence (isolated PostgreSQL)', () => {
  let app: INestApplication<Server>;
  let prisma: PrismaService;
  let auth: AuthService;
  let providerAvailable = true;
  const fixtureUsers: string[] = [];
  const fixtureHouseholds: string[] = [];
  const proofs = new Map<string, VerifiedOidcIdentity>();
  // Only the external provider boundary is substituted. Signature/JWKS behavior
  // has separate real-crypto/HTTP unit tests; all transactions and locks here
  // use the actual PostgreSQL database and the production AuthService.
  const verifier = {
    verify: jest.fn((proof: string): Promise<VerifiedOidcIdentity> => {
      if (!providerAvailable) {
        return Promise.reject(
          new ServiceUnavailableException(
            'External sign-in is temporarily unavailable',
          ),
        );
      }
      const identity = proofs.get(proof);
      return identity
        ? Promise.resolve(identity)
        : Promise.reject(new UnauthorizedException('Synthetic invalid proof'));
    }),
  };

  beforeAll(async () => {
    requireDisposableDatabase();
    const module = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(OidcTokenVerifier)
      .useValue(verifier)
      .compile();
    app = module.createNestApplication<INestApplication<Server>>();
    app.useGlobalPipes(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }),
    );
    await app.init();
    prisma = app.get(PrismaService);
    auth = app.get(AuthService);
  });

  beforeEach(() => {
    providerAvailable = true;
    verifier.verify.mockClear();
  });

  afterEach(async () => {
    if (!prisma) return;
    requireDisposableDatabase();
    // Delete only IDs created by this suite. Do not clear global tables or
    // ServerState: the core-flow suite still needs its pristine bootstrap flag.
    await prisma.user.deleteMany({ where: { id: { in: fixtureUsers } } });
    await prisma.household.deleteMany({
      where: { id: { in: fixtureHouseholds } },
    });
    expect(
      await prisma.user.count({ where: { id: { in: fixtureUsers } } }),
    ).toBe(0);
    expect(
      await prisma.household.count({
        where: { id: { in: fixtureHouseholds } },
      }),
    ).toBe(0);
    fixtureUsers.length = 0;
    fixtureHouseholds.length = 0;
    proofs.clear();
  });

  afterAll(async () => {
    await app?.close();
  });

  async function fixture(method: SessionAuthMethod = SessionAuthMethod.OIDC) {
    const subject = `synthetic-subject-${randomUUID()}`;
    const proof = `synthetic-provider-proof-${randomUUID()}-${randomUUID()}`;
    const seconds = Math.floor(Date.now() / 1_000);
    proofs.set(proof, {
      issuer,
      subject,
      issuedAt: seconds,
      expiresAt: seconds + 300,
    });
    return prisma.$transaction(async (tx) => {
      const household = await tx.household.create({
        data: { name: 'Synthetic persistence test' },
      });
      fixtureHouseholds.push(household.id);
      const user = await tx.user.create({
        data: {
          username: `persist-${randomUUID()}`,
          displayName: 'Synthetic persistence account',
          preferredLanguage: PreferredLanguage.EN,
        },
      });
      fixtureUsers.push(user.id);
      const membership = await tx.membership.create({
        data: {
          userId: user.id,
          householdId: household.id,
          role: UserRole.TRACKER,
        },
      });
      const identity =
        method === SessionAuthMethod.OIDC
          ? await tx.externalIdentity.create({
              data: { userId: user.id, issuer, subject },
            })
          : undefined;
      const initial = await auth.createAuthenticatedSession(
        tx,
        {
          ...user,
          role: UserRole.TRACKER,
          householdId: household.id,
        },
        'Synthetic persistence device',
        {
          authMethod: method,
          externalIdentityId: identity?.id,
        },
      );
      await tx.deviceSession.update({
        where: { id: initial.sessionId },
        data: { expiresAt: new Date(Date.now() - 86_400_000) },
      });
      return { initial, membership, identity, proof, subject };
    });
  }

  function refreshPayload(initial: AuthResponse, proof?: string) {
    return {
      refreshToken: initial.refreshToken,
      oidcAccessToken: proof,
      refreshRequestId: randomUUID(),
    };
  }

  async function hasBlockedAuthenticationLock(userId: string): Promise<boolean> {
    const deadline = Date.now() + 2_000;
    do {
      const rows = await prisma.$queryRaw<Array<{ waiting: boolean }>>`
        SELECT EXISTS (
          SELECT 1 FROM pg_locks
          WHERE locktype = 'advisory' AND NOT granted AND objsubid = 1
            AND classid = ((hashtextextended(${userId}, 0) >> 32) & 4294967295)::oid
            AND objid = (hashtextextended(${userId}, 0) & 4294967295)::oid
        ) AS waiting
      `;
      if (rows[0]?.waiting) return true;
      await new Promise<void>((resolve) => setTimeout(resolve, 10));
    } while (Date.now() < deadline);
    return false;
  }

  it('renews an idle OIDC session in its retained family and safely replays a lost response', async () => {
    const f = await fixture();
    const before = await prisma.deviceSession.findUniqueOrThrow({
      where: { id: f.initial.sessionId },
    });
    await request(app.getHttpServer())
      .get('/api/v1/me')
      .set('Authorization', `Bearer ${f.initial.accessToken}`)
      .expect(401);
    const payload = refreshPayload(f.initial, f.proof);
    const renewed = await request(app.getHttpServer())
      .post('/api/v1/auth/refresh')
      .send(payload)
      .expect(201);
    const replay = await request(app.getHttpServer())
      .post('/api/v1/auth/refresh')
      .send(payload)
      .expect(201);
    const next = renewed.body as AuthResponse;
    expect(next.user.id).toBe(f.initial.user.id);
    expect((replay.body as AuthResponse).refreshToken).toBe(next.refreshToken);
    expect((replay.body as AuthResponse).sessionId).toBe(next.sessionId);
    const successor = await prisma.deviceSession.findUniqueOrThrow({
      where: { id: next.sessionId },
    });
    expect(successor).toMatchObject({
      userId: f.initial.user.id,
      tokenFamilyId: before.tokenFamilyId,
      externalIdentityId: f.identity!.id,
      authMethod: SessionAuthMethod.OIDC,
      rotatedFromId: before.id,
      revokedAt: null,
    });
    expect(successor.expiresAt.getTime()).toBeGreaterThan(Date.now());
    expect(
      (
        await prisma.deviceSession.findUniqueOrThrow({
          where: { id: before.id },
        })
      ).revokedAt,
    ).not.toBeNull();
    expect(
      await prisma.deviceSession.count({
        where: { userId: f.initial.user.id },
      }),
    ).toBe(2);
    expect(
      await prisma.idempotencyKey.count({
        where: { userId: f.initial.user.id },
      }),
    ).toBe(1);
    const me = await request(app.getHttpServer())
      .get('/api/v1/me')
      .set('Authorization', `Bearer ${next.accessToken}`)
      .expect(200);
    expect((me.body as { id: string }).id).toBe(f.initial.user.id);
  });

  it('keeps the original credential untouched on provider 503 and accepts its retry after recovery', async () => {
    const f = await fixture();
    const before = await prisma.deviceSession.findUniqueOrThrow({
      where: { id: f.initial.sessionId },
    });
    const payload = refreshPayload(f.initial, f.proof);
    providerAvailable = false;
    await request(app.getHttpServer())
      .post('/api/v1/auth/refresh')
      .send(payload)
      .expect(503);
    expect(
      await prisma.deviceSession.findUniqueOrThrow({
        where: { id: before.id },
      }),
    ).toEqual(before);
    expect(
      await prisma.deviceSession.count({
        where: { userId: f.initial.user.id },
      }),
    ).toBe(1);
    expect(
      await prisma.idempotencyKey.count({
        where: { userId: f.initial.user.id },
      }),
    ).toBe(0);
    providerAvailable = true;
    await request(app.getHttpServer())
      .post('/api/v1/auth/refresh')
      .send(payload)
      .expect(201);
    expect(
      await prisma.deviceSession.count({
        where: { userId: f.initial.user.id, revokedAt: null },
      }),
    ).toBe(1);
  });

  it('serializes simultaneous idle-session retries with the real PostgreSQL advisory lock', async () => {
    const f = await fixture();
    const requestId = randomUUID();
    const results = await Promise.all(
      Array.from({ length: 4 }, () =>
        auth.refresh(f.initial.refreshToken, f.proof, requestId),
      ),
    );
    expect(new Set(results.map((value) => value.sessionId)).size).toBe(1);
    expect(new Set(results.map((value) => value.refreshToken)).size).toBe(1);
    expect(
      await prisma.deviceSession.count({
        where: { userId: f.initial.user.id },
      }),
    ).toBe(2);
    expect(
      await prisma.idempotencyKey.count({
        where: { userId: f.initial.user.id },
      }),
    ).toBe(1);
  });

  it.each([
    'missing-proof',
    'wrong-subject',
    'wrong-issuer',
    'unlinked',
    'revoked',
    'deleted',
    'disabled',
    'wrong-owner',
  ])(
    'does not create a successor for an idle session when %s',
    async (state) => {
      const f = await fixture();
      let proof: string | undefined = f.proof;
      if (state === 'missing-proof') proof = undefined;
      if (state === 'wrong-subject')
        proofs.set(f.proof, {
          ...proofs.get(f.proof)!,
          subject: 'another-subject',
        });
      if (state === 'wrong-issuer')
        proofs.set(f.proof, {
          ...proofs.get(f.proof)!,
          issuer: 'https://another.example/',
        });
      if (state === 'unlinked')
        await prisma.externalIdentity.delete({ where: { id: f.identity!.id } });
      if (state === 'revoked')
        await prisma.deviceSession.update({
          where: { id: f.initial.sessionId },
          data: { revokedAt: new Date() },
        });
      if (state === 'deleted')
        await prisma.deviceSession.delete({
          where: { id: f.initial.sessionId },
        });
      if (state === 'disabled')
        await prisma.user.update({
          where: { id: f.initial.user.id },
          data: { disabledAt: new Date() },
        });
      if (state === 'wrong-owner') {
        const other = await fixture(SessionAuthMethod.PASSWORD);
        await prisma.externalIdentity.update({
          where: { id: f.identity!.id },
          data: { userId: other.initial.user.id },
        });
      }
      await expect(
        auth.refresh(f.initial.refreshToken, proof, randomUUID()),
      ).rejects.toBeInstanceOf(UnauthorizedException);
      expect(
        await prisma.deviceSession.count({
          where: { userId: f.initial.user.id },
        }),
      ).toBe(state === 'deleted' ? 0 : 1);
      expect(
        await prisma.idempotencyKey.count({
          where: { userId: f.initial.user.id },
        }),
      ).toBe(0);
    },
  );

  it('does not use an OIDC proof to extend an expired password session', async () => {
    const f = await fixture(SessionAuthMethod.PASSWORD);
    await expect(
      auth.refresh(f.initial.refreshToken, f.proof, randomUUID()),
    ).rejects.toBeInstanceOf(UnauthorizedException);
    expect(verifier.verify).not.toHaveBeenCalled();
    expect(
      await prisma.deviceSession.count({
        where: { userId: f.initial.user.id },
      }),
    ).toBe(1);
  });

  it('rechecks revocation committed by the transaction holding the user lock', async () => {
    const f = await fixture();
    const locked = barrier();
    const release = barrier();
    const revocation = prisma.$transaction(async (tx) => {
      await lockUserAuthentication(tx, f.initial.user.id);
      locked.release();
      await release.promise;
      await tx.deviceSession.update({
        where: { id: f.initial.sessionId },
        data: { revokedAt: new Date() },
      });
    });
    await locked.promise;
    const pending = auth
      .refresh(f.initial.refreshToken, f.proof, randomUUID())
      .then(
        () => undefined,
        (error: unknown) => error,
      );
    let outcome: unknown;
    try {
      // Observe the actual blocked advisory lock, not merely a favorable
      // promise scheduling order. Without the production lock this must fail.
      expect(await hasBlockedAuthenticationLock(f.initial.user.id)).toBe(true);
    } finally {
      release.release();
      await revocation;
      outcome = await pending;
    }
    expect(outcome).toBeInstanceOf(UnauthorizedException);
    expect(
      await prisma.deviceSession.count({
        where: { userId: f.initial.user.id },
      }),
    ).toBe(1);
    expect(
      await prisma.deviceSession.count({
        where: { userId: f.initial.user.id, revokedAt: null },
      }),
    ).toBe(0);
  });
});
