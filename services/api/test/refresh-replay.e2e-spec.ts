import 'reflect-metadata';
import { randomUUID } from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { resolve } from 'node:path';
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { PreferredLanguage, UserRole } from '@prisma/client';
import request from 'supertest';
import { AppModule } from '../src/app.module.js';
import { AuthService, AuthResponse, SessionIdentity } from '../src/auth/auth.service.js';
import { PrismaService } from '../src/prisma/prisma.service.js';
import { AuthenticatedUser } from '../src/common/types/authenticated-user.js';

describe('Refresh-response loss and partner sync (isolated PostgreSQL)', () => {
  let app: INestApplication;
  let prisma: PrismaService;
  let auth: AuthService;
  const fixtureUsers: string[] = [];
  const fixtureHouseholds: string[] = [];

  beforeAll(async () => {
    const url = new URL(process.env.DATABASE_URL ?? 'http://invalid');
    if (!url.pathname.includes('lunara_sync115_test')) {
      throw new Error('This suite requires the dedicated disposable sync115 test database');
    }
    const module = await Test.createTestingModule({ imports: [AppModule] }).compile();
    app = module.createNestApplication();
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
    await app.init();
    prisma = app.get(PrismaService);
    auth = app.get(AuthService);
  });

  afterEach(async () => {
    if (!prisma) return;
    await prisma.user.deleteMany({ where: { id: { in: fixtureUsers.splice(0) } } });
    await prisma.household.deleteMany({ where: { id: { in: fixtureHouseholds.splice(0) } } });
  });

  afterAll(async () => { await app?.close(); });

  async function fixture(role: UserRole = UserRole.TRACKER, householdId?: string): Promise<AuthResponse> {
    return prisma.$transaction(async tx => {
      const household = householdId
        ? { id: householdId }
        : await tx.household.create({ data: { name: 'Synthetic refresh regression' } });
      if (!householdId) fixtureHouseholds.push(household.id);
      const user = await tx.user.create({ data: { username: `retry-${randomUUID()}`, displayName: 'Synthetic test', preferredLanguage: PreferredLanguage.EN } });
      fixtureUsers.push(user.id);
      await tx.membership.create({ data: { userId: user.id, householdId: household.id, role } });
      if (role === UserRole.TRACKER) {
        await tx.cycleProfile.create({ data: { userId: user.id, typicalCycleLengthOptional: 29 } });
        await tx.sharingSettings.create({ data: { ownerUserId: user.id, householdId: household.id, sharePeriodDates: true } });
      }
      const identity: SessionIdentity = { ...user, role, householdId: household.id };
      return auth.createAuthenticatedSession(tx, identity, 'Synthetic recovery test');
    });
  }

  async function authenticated(initial: AuthResponse): Promise<AuthenticatedUser> {
    const membership = await prisma.membership.findFirstOrThrow({ where: { userId: initial.user.id } });
    return {
      userId: initial.user.id, sessionId: initial.sessionId, householdId: membership.householdId,
      membershipId: membership.id, role: membership.role, username: initial.user.username,
      displayName: initial.user.displayName, preferredLanguage: PreferredLanguage.EN,
    };
  }

  it('retries a lost refresh response and uploads one period exactly once for the partner', async () => {
    const tracker = await fixture();
    const partner = await fixture(UserRole.PARTNER, tracker.user.householdId);
    const refreshRequestId = randomUUID();
    const payload = { refreshToken: tracker.refreshToken, refreshRequestId };
    const lost = await request(app.getHttpServer()).post('/api/v1/auth/refresh').send(payload).expect(201);
    const recovered = await request(app.getHttpServer()).post('/api/v1/auth/refresh').send(payload).expect(201);
    expect(recovered.body.refreshToken).toBe(lost.body.refreshToken);
    expect(recovered.body.sessionId).toBe(lost.body.sessionId);
    const id = randomUUID();
    const startDate = new Date().toISOString().slice(0, 10);
    const mutation = { cursor: '0', mutations: [{ clientMutationId: randomUUID(), entityType: 'PERIOD', operation: 'UPSERT', payload: { id, startDate } }] };
    for (let replay = 0; replay < 2; replay++) {
      await request(app.getHttpServer()).post('/api/v1/sync')
        .set('Authorization', `Bearer ${recovered.body.accessToken}`).send(mutation).expect(201);
    }
    expect(await prisma.periodEvent.count({ where: { userId: tracker.user.id, id } })).toBe(1);
    const snapshot = await request(app.getHttpServer()).get('/api/v1/sync')
      .set('Authorization', `Bearer ${partner.accessToken}`).expect(200);
    expect(snapshot.body.changes).toEqual([]);
    expect(snapshot.body.partnerDashboard.periodDates).toEqual([{ startDate, endDate: null }]);
    const caches = await prisma.idempotencyKey.findMany({ where: { userId: tracker.user.id, path: '/api/v1/auth/refresh' } });
    expect(caches).toHaveLength(1);
    expect(JSON.stringify(caches)).not.toContain(tracker.refreshToken);
    expect(JSON.stringify(caches)).not.toContain(recovered.body.refreshToken);
  });

  it('serializes simultaneous retries into one rotation', async () => {
    const initial = await fixture();
    const id = randomUUID();
    const results = await Promise.all(Array.from({ length: 4 }, () => auth.refresh(initial.refreshToken, undefined, id)));
    expect(new Set(results.map(result => result.sessionId)).size).toBe(1);
    expect(new Set(results.map(result => result.refreshToken)).size).toBe(1);
    expect(await prisma.deviceSession.count({ where: { userId: initial.user.id } })).toBe(2);
    expect(await prisma.deviceSession.count({ where: { userId: initial.user.id, revokedAt: null } })).toBe(1);
  });

  it('never restores an explicitly revoked successor', async () => {
    const initial = await fixture();
    const id = randomUUID();
    const next = await auth.refresh(initial.refreshToken, undefined, id);
    await prisma.deviceSession.update({ where: { id: next.sessionId }, data: { revokedAt: new Date() } });
    await expect(auth.refresh(initial.refreshToken, undefined, id)).rejects.toThrow();
    expect(await prisma.deviceSession.count({ where: { userId: initial.user.id, revokedAt: null } })).toBe(0);
  });

  it('does not revoke the current family for a delayed known retry after another rotation', async () => {
    const initial = await fixture();
    const id = randomUUID();
    const first = await auth.refresh(initial.refreshToken, undefined, id);
    const current = await auth.refresh(first.refreshToken, undefined, randomUUID());
    await expect(auth.refresh(initial.refreshToken, undefined, id)).rejects.toThrow();
    expect((await prisma.deviceSession.findUniqueOrThrow({ where: { id: current.sessionId } })).revokedAt).toBeNull();
  });

  it('still detects a reused old token presented as a different request', async () => {
    const initial = await fixture();
    await auth.refresh(initial.refreshToken, undefined, randomUUID());
    await expect(auth.refresh(initial.refreshToken, undefined, randomUUID())).rejects.toThrow();
    expect(await prisma.deviceSession.count({ where: { userId: initial.user.id, revokedAt: null } })).toBe(0);
  });

  it('revokes the rotated device using its original row without affecting another device', async () => {
    const initial = await fixture();
    const identity = await authenticated(initial);
    const other = await prisma.$transaction(tx => auth.createAuthenticatedSession(tx, {
      ...initial.user, preferredLanguage: PreferredLanguage.EN, role: UserRole.TRACKER,
    }, 'Synthetic other device'));
    const requestId = randomUUID();
    await auth.refresh(initial.refreshToken, undefined, requestId);
    await auth.revokeSession(identity, initial.sessionId);
    await expect(auth.refresh(initial.refreshToken, undefined, requestId)).rejects.toThrow();
    expect((await prisma.deviceSession.findUniqueOrThrow({ where: { id: other.sessionId } })).revokedAt).toBeNull();
    expect(await prisma.deviceSession.count({ where: { userId: initial.user.id, revokedAt: null } })).toBe(1);
  });

  it('serializes logout against refresh in either launch order, including response loss', async () => {
    for (const refreshFirst of [true, false]) {
      const initial = await fixture();
      const identity = await authenticated(initial);
      const refresh = () => auth.refresh(initial.refreshToken, undefined, randomUUID());
      const logout = () => auth.logout(identity, initial.refreshToken);
      const results = await Promise.allSettled(refreshFirst ? [refresh(), logout()] : [logout(), refresh()]);
      expect(results[refreshFirst ? 1 : 0].status).toBe('fulfilled');
      expect(await prisma.deviceSession.count({ where: { userId: initial.user.id, revokedAt: null } })).toBe(0);
    }
  });

  it('administratively revokes every device without leaving a concurrent replacement', async () => {
    const initial = await fixture();
    const results = await Promise.allSettled([
      auth.refresh(initial.refreshToken, undefined, randomUUID()),
      promisify(execFile)(process.execPath, [resolve('dist/admin/admin-cli.js'),
        'sessions', 'revoke-all', '--user-id', initial.user.id,
        '--confirm', `REVOKE-SESSIONS:${initial.user.id}`], { env: process.env }),
    ]);
    expect(results[1].status).toBe('fulfilled');
    expect(await prisma.deviceSession.count({ where: { userId: initial.user.id, revokedAt: null } })).toBe(0);
  });
});
