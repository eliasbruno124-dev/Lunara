import 'reflect-metadata';
import { randomUUID } from 'node:crypto';
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { AuditEventType, TipCategory, UserRole } from '@prisma/client';
import { PrismaService } from '../src/prisma/prisma.service.js';
import request from 'supertest';
import { AppModule } from '../src/app.module.js';
import { AuthService } from '../src/auth/auth.service.js';

async function createTestApplication(): Promise<INestApplication> {
  const module = await Test.createTestingModule({
    imports: [AppModule],
  }).compile();
  const application = module.createNestApplication();
  application.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );
  await application.init();
  return application;
}

describe('Lunara core flow (real PostgreSQL)', () => {
  let app: INestApplication;
  let prisma: PrismaService;

  beforeAll(async () => {
    app = await createTestApplication();
    prisma = app.get(PrismaService);
  });

  afterAll(async () => {
    await app.close();
  });

  it('setup -> invite -> partner -> prediction -> sharing -> sync', async () => {
    const metaBefore = await request(app.getHttpServer())
      .get('/api/v1/meta')
      .expect(200);
    expect(metaBefore.body.setupRequired).toBe(true);
    expect(metaBefore.body.gatewayHeaderName).toBe('X-Lunara-Authorization');
    expect(metaBefore.headers['cache-control']).toContain('no-store');
    expect(metaBefore.headers.pragma).toBe('no-cache');

    await request(app.getHttpServer())
      .post('/api/v1/auth/register')
      .send({
        username: 'registration-must-be-disabled',
        password: 'A secure registration password!',
        displayName: 'Registration attempt',
        deviceName: 'Registration test device',
      })
      .expect(403)
      .expect(({ body }) =>
        expect(body.message).toBe('Registration is disabled'),
      );
    expect(await prisma.user.count()).toBe(0);
    expect(await prisma.household.count()).toBe(0);

    const trackerSetup = await request(app.getHttpServer())
      .post('/api/v1/setup')
      .send({
        bootstrapToken: process.env.BOOTSTRAP_TOKEN,
        username: 'tracker-e2e',
        password: 'A secure tracker password 2026!',
        displayName: 'Tracker',
        deviceName: 'E2E Tracker Device',
        preferredLanguage: 'DE',
      })
      .expect(201);
    expect(trackerSetup.body.user.role).toBe('TRACKER');
    expect(
      (
        await prisma.serverState.findUniqueOrThrow({ where: { id: 1 } })
      ).bootstrappedAt,
    ).toBeInstanceOf(Date);
    const trackerAccess = trackerSetup.body.accessToken as string;
    const originalRefresh = trackerSetup.body.refreshToken as string;
    const trackerUserId = trackerSetup.body.user.id as string;
    const trackerHouseholdId = trackerSetup.body.user.householdId as string;

    const unknownLogin = await request(app.getHttpServer())
      .post('/api/v1/auth/login')
      .send({
        username: 'does-not-exist',
        password: 'An intentionally wrong password!',
        deviceName: 'Unknown device',
      })
      .expect(401);
    const wrongPasswordLogin = await request(app.getHttpServer())
      .post('/api/v1/auth/login')
      .send({
        username: 'tracker-e2e',
        password: 'An intentionally wrong password!',
        deviceName: 'Wrong password device',
      })
      .expect(401);
    expect(wrongPasswordLogin.body.message).toBe(unknownLogin.body.message);

    await request(app.getHttpServer())
      .get('/api/v1/meta')
      .expect(200)
      .expect(({ body }) => expect(body.setupRequired).toBe(false));
    await request(app.getHttpServer())
      .post('/api/v1/setup')
      .send({
        bootstrapToken: process.env.BOOTSTRAP_TOKEN,
        householdName: 'Should fail',
        username: 'other-user',
        password: 'A secure other password!',
        displayName: 'Other',
        deviceName: 'Other',
      })
      .expect(409);

    const config = app.get(ConfigService);
    config.set('lunara.registrationEnabled', true);
    await request(app.getHttpServer())
      .get('/api/v1/meta')
      .expect(200)
      .expect(({ body }) => expect(body.registrationEnabled).toBe(true));

    // A separate application instance gives this concurrency check its own
    // in-memory rate-limit bucket while keeping the same real PostgreSQL DB.
    const registrationApp = await createTestApplication();
    registrationApp
      .get(ConfigService)
      .set('lunara.registrationEnabled', true);
    const concurrentRegistrations = await Promise.all([
      request(registrationApp.getHttpServer())
        .post('/api/v1/auth/register')
        .send({
          username: 'second-tracker-e2e',
          password: 'A secure second tracker password 2026!',
          displayName: 'Second tracker',
          deviceName: 'Second relationship device',
          preferredLanguage: 'DE',
        }),
      request(registrationApp.getHttpServer())
        .post('/api/v1/auth/register')
        .send({
          username: 'third-tracker-e2e',
          password: 'A secure third tracker password 2026!',
          displayName: 'Third tracker',
          deviceName: 'Third relationship device',
          preferredLanguage: 'EN',
        }),
    ]);
    await registrationApp.close();
    expect(concurrentRegistrations.map(({ status }) => status)).toEqual([
      201, 201,
    ]);
    expect(concurrentRegistrations.map(({ status }) => status)).not.toContain(
      500,
    );
    const [secondTracker, thirdTracker] = concurrentRegistrations;
    if (!secondTracker || !thirdTracker) {
      throw new Error('Expected both concurrent registrations to complete');
    }
    const secondTrackerAccess = secondTracker.body.accessToken as string;
    const secondTrackerUserId = secondTracker.body.user.id as string;
    const secondHouseholdId = secondTracker.body.user.householdId as string;
    const thirdTrackerUserId = thirdTracker.body.user.id as string;
    const thirdHouseholdId = thirdTracker.body.user.householdId as string;
    expect(secondTracker.body.user.role).toBe(UserRole.TRACKER);
    expect(thirdTracker.body.user.role).toBe(UserRole.TRACKER);
    expect(
      new Set([trackerHouseholdId, secondHouseholdId, thirdHouseholdId]).size,
    ).toBe(3);
    expect(
      await prisma.membership.count({
        where: {
          householdId: { in: [secondHouseholdId, thirdHouseholdId] },
          role: UserRole.TRACKER,
        },
      }),
    ).toBe(2);

    const secondRelationshipPeriodId = randomUUID();
    await request(app.getHttpServer())
      .post('/api/v1/periods')
      .set('Authorization', `Bearer ${secondTrackerAccess}`)
      .send({ id: secondRelationshipPeriodId, startDate: '2026-02-01' })
      .expect(201);
    await request(app.getHttpServer())
      .get('/api/v1/periods')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(200)
      .expect(({ body }) => {
        expect(JSON.stringify(body)).not.toContain(secondRelationshipPeriodId);
      });
    await request(app.getHttpServer())
      .patch(`/api/v1/periods/${secondRelationshipPeriodId}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ version: 1, endDate: '2026-02-05' })
      .expect(404);
    config.set('lunara.registrationEnabled', false);

    const invite = await request(app.getHttpServer())
      .post('/api/v1/household/invitations')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ neverExpires: true })
      .expect(201);
    expect(invite.body.code).toMatch(/^[A-HJ-NP-Z2-9]{12}$/);
    expect(invite.body.expiresAt).toBeNull();

    const isolatedInvite = await request(app.getHttpServer())
      .post('/api/v1/household/invitations')
      .set('Authorization', `Bearer ${secondTrackerAccess}`)
      .send({ neverExpires: true })
      .expect(201);
    const trackerInvitations = await request(app.getHttpServer())
      .get('/api/v1/household/invitations')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(200);
    expect(trackerInvitations.body).toEqual([
      expect.objectContaining({ id: invite.body.id }),
    ]);
    expect(JSON.stringify(trackerInvitations.body)).not.toContain(
      isolatedInvite.body.id,
    );
    const isolatedInvitations = await request(app.getHttpServer())
      .get('/api/v1/household/invitations')
      .set('Authorization', `Bearer ${secondTrackerAccess}`)
      .expect(200);
    expect(isolatedInvitations.body).toEqual([
      expect.objectContaining({ id: isolatedInvite.body.id }),
    ]);
    expect(JSON.stringify(isolatedInvitations.body)).not.toContain(
      invite.body.id,
    );

    // A tracker cannot revoke an invitation from another relationship. The
    // endpoint is deliberately idempotent, so verify the persisted state.
    await request(app.getHttpServer())
      .delete(`/api/v1/household/invitations/${isolatedInvite.body.id}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(204);
    expect(
      (
        await prisma.invitation.findUniqueOrThrow({
          where: { id: isolatedInvite.body.id },
        })
      ).revokedAt,
    ).toBeNull();
    await request(app.getHttpServer())
      .delete(`/api/v1/household/invitations/${isolatedInvite.body.id}`)
      .set('Authorization', `Bearer ${secondTrackerAccess}`)
      .expect(204);
    expect(
      (
        await prisma.invitation.findUniqueOrThrow({
          where: { id: isolatedInvite.body.id },
        })
      ).revokedAt,
    ).toBeInstanceOf(Date);
    expect(
      await prisma.auditEvent.count({
        where: {
          actorUserId: trackerUserId,
          type: AuditEventType.INVITATION_REVOKED,
        },
      }),
    ).toBe(0);
    expect(
      await prisma.auditEvent.count({
        where: {
          actorUserId: secondTrackerUserId,
          type: AuditEventType.INVITATION_REVOKED,
        },
      }),
    ).toBe(1);
    await request(app.getHttpServer())
      .get('/api/v1/household/invitations')
      .set('Authorization', `Bearer ${secondTrackerAccess}`)
      .expect(200)
      .expect([]);
    await request(app.getHttpServer())
      .post('/api/v1/household/join')
      .send({
        invitationCode: isolatedInvite.body.code,
        username: 'revoked-invite-user',
        password: 'A secure revoked invitation password!',
        displayName: 'Revoked invitation',
        deviceName: 'Revoked invitation device',
      })
      .expect(410);

    const concurrentPartnerJoins = await Promise.all([
      request(app.getHttpServer())
        .post('/api/v1/household/join')
        .send({
          invitationCode: invite.body.code,
          username: 'partner-e2e-a',
          password: 'A secure partner password 2026!',
          displayName: 'Partner A',
          deviceName: 'E2E Partner Device A',
          preferredLanguage: 'EN',
        }),
      request(app.getHttpServer())
        .post('/api/v1/household/join')
        .send({
          invitationCode: invite.body.code,
          username: 'partner-e2e-b',
          password: 'Another secure partner password 2026!',
          displayName: 'Partner B',
          deviceName: 'E2E Partner Device B',
          preferredLanguage: 'DE',
        }),
    ]);
    const concurrentJoinStatuses = concurrentPartnerJoins.map(
      ({ status }) => status,
    );
    expect(concurrentJoinStatuses).not.toContain(500);
    expect(
      concurrentJoinStatuses.filter((status) => status === 201),
    ).toHaveLength(1);
    expect(
      concurrentJoinStatuses.filter((status) => [409, 410].includes(status)),
    ).toHaveLength(1);
    const partnerJoin = concurrentPartnerJoins.find(
      ({ status }) => status === 201,
    );
    if (!partnerJoin) throw new Error('Expected one invitation claim winner');
    expect(partnerJoin.body.user.role).toBe('PARTNER');
    const partnerAccess = partnerJoin.body.accessToken as string;
    const partnerUserId = partnerJoin.body.user.id as string;
    expect(
      await prisma.membership.count({
        where: { householdId: trackerHouseholdId, role: UserRole.PARTNER },
      }),
    ).toBe(1);
    expect(
      await prisma.user.count({
        where: { username: { in: ['partner-e2e-a', 'partner-e2e-b'] } },
      }),
    ).toBe(1);
    await request(app.getHttpServer())
      .post('/api/v1/household/invitations')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ neverExpires: true })
      .expect(409);
    await request(app.getHttpServer())
      .post('/api/v1/household/join')
      .send({
        invitationCode: invite.body.code,
        username: 'replay-user',
        password: 'A secure replay password!',
        displayName: 'Replay',
        deviceName: 'Replay',
      })
      .expect(410);
    for (let attempt = 0; attempt < 1; attempt += 1) {
      await request(app.getHttpServer())
        .post('/api/v1/household/join')
        .send({
          invitationCode: 'AAAAAAAAAA',
          username: `invalid-invite-${attempt}`,
          password: 'A secure invalid invitation password!',
          displayName: 'Invalid invitation',
          deviceName: 'Invalid invitation device',
        })
        .expect(404);
    }
    await request(app.getHttpServer())
      .post('/api/v1/household/join')
      .send({
        invitationCode: 'BBBBBBBBBB',
        username: 'rate-limited-invite',
        password: 'A secure rate limited password!',
        displayName: 'Rate limited invitation',
        deviceName: 'Rate limited invitation device',
      })
      .expect(429);

    const symptomCatalog = await request(app.getHttpServer())
      .get('/api/v1/symptoms')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(200);
    expect(symptomCatalog.body.predefined).toHaveLength(12);
    expect(symptomCatalog.body.predefined).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          key: 'CRAMPS',
          labelDe: 'Krämpfe',
          labelEn: 'Cramps',
        }),
      ]),
    );
    await request(app.getHttpServer())
      .get('/api/v1/symptoms')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .expect(403);
    const customSymptomId = randomUUID();
    await request(app.getHttpServer())
      .post('/api/v1/symptoms')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ id: customSymptomId, label: '  Druckgefühl  ' })
      .expect(201)
      .expect(({ body }) => expect(body.label).toBe('Druckgefühl'));
    await request(app.getHttpServer())
      .post('/api/v1/symptoms')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ id: customSymptomId, label: 'Druckgefühl' })
      .expect(201);
    await request(app.getHttpServer())
      .patch(`/api/v1/symptoms/${customSymptomId}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ label: 'Druck im Bauch' })
      .expect(200);
    await request(app.getHttpServer())
      .delete(`/api/v1/symptoms/${customSymptomId}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(204);
    await request(app.getHttpServer())
      .get('/api/v1/symptoms')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(200)
      .expect(({ body }) => expect(body.custom).toHaveLength(0));
    await request(app.getHttpServer())
      .get('/api/v1/symptoms?includeArchived=true')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body.custom).toEqual(
          expect.arrayContaining([
            expect.objectContaining({ id: customSymptomId }),
          ]),
        );
      });

    const foreignPeriodId = randomUUID();
    const foreignUserId = randomUUID();
    const foreignHousehold = await prisma.household.create({
      data: {
        name: 'Isolierter fremder Haushalt',
        memberships: {
          create: {
            role: UserRole.TRACKER,
            user: {
              create: {
                id: foreignUserId,
                username: `foreign-${foreignUserId}`,
                displayName: 'Foreign tracker',
                passwordHash: 'not-used-by-this-isolation-test',
              },
            },
          },
        },
        syncChanges: {
          create: {
            ownerUserId: foreignUserId,
            entityType: 'PeriodEvent',
            entityId: foreignPeriodId,
            operation: 'UPSERT',
            entityVersion: 1,
          },
        },
      },
    });
    await prisma.periodEvent.create({
      data: {
        id: foreignPeriodId,
        userId: foreignUserId,
        startDate: new Date('2026-03-01T00:00:00.000Z'),
      },
    });
    const foreignSymptomId = randomUUID();
    await prisma.customSymptom.create({
      data: {
        id: foreignSymptomId,
        userId: foreignUserId,
        label: 'Fremdes Symptom',
      },
    });
    await request(app.getHttpServer())
      .patch(`/api/v1/symptoms/${foreignSymptomId}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ label: 'Unerlaubte Änderung' })
      .expect(404);
    await request(app.getHttpServer())
      .delete(`/api/v1/symptoms/${foreignSymptomId}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(404);

    await request(app.getHttpServer())
      .patch(`/api/v1/periods/${foreignPeriodId}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ version: 1, endDate: '2026-03-05' })
      .expect(404);
    await request(app.getHttpServer())
      .delete(`/api/v1/periods/${foreignPeriodId}?version=1`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(404);
    expect(
      await prisma.periodEvent.count({
        where: { id: foreignPeriodId, deletedAt: null },
      }),
    ).toBe(1);

    const isolatedSyncPull = await request(app.getHttpServer())
      .get('/api/v1/sync?cursor=0')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(200);
    expect(isolatedSyncPull.body.changes).not.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ entityId: foreignPeriodId }),
      ]),
    );
    expect(JSON.stringify(isolatedSyncPull.body)).not.toContain(
      foreignPeriodId,
    );

    const periodStarts = [
      '2026-04-01',
      '2026-05-01',
      '2026-05-31',
      '2026-06-30',
    ];
    const createdPeriods = [] as Array<{ id: string; version: number }>;
    for (const startDate of periodStarts) {
      const created = await request(app.getHttpServer())
        .post('/api/v1/periods')
        .set('X-Lunara-Authorization', `Bearer ${trackerAccess}`)
        .set('Authorization', 'Basic Z2F0ZXdheTpwYXNzd29yZA==')
        .send({ id: randomUUID(), startDate })
        .expect(201);
      createdPeriods.push({
        id: created.body.id as string,
        version: created.body.version as number,
      });
    }
    const firstCreatedPeriod = createdPeriods[0];
    if (!firstCreatedPeriod)
      throw new Error('Expected at least one E2E period');

    await request(app.getHttpServer())
      .patch(`/api/v1/periods/${firstCreatedPeriod.id}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ version: 99, endDate: '2026-04-05' })
      .expect(409);

    await request(app.getHttpServer())
      .patch(`/api/v1/periods/${firstCreatedPeriod.id}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ version: firstCreatedPeriod.version, startDate: periodStarts[1] })
      .expect(409)
      .expect(({ body }) =>
        expect(body.message).toBe(
          'An active period entry already exists for this date',
        ),
      );
    expect(
      (
        await prisma.periodEvent.findUniqueOrThrow({
          where: { id: firstCreatedPeriod.id },
        })
      ).startDate.toISOString().slice(0, 10),
    ).toBe(periodStarts[0]);

    const concurrentPeriodDate = '2026-07-15';
    const concurrentPeriodResponses = await Promise.all(
      [randomUUID(), randomUUID()].map((id) =>
        request(app.getHttpServer())
          .post('/api/v1/periods')
          .set('Authorization', `Bearer ${trackerAccess}`)
          .send({ id, startDate: concurrentPeriodDate }),
      ),
    );
    expect(
      concurrentPeriodResponses.map(({ status }) => status).sort(),
    ).toEqual([201, 409]);
    expect(
      concurrentPeriodResponses.find(({ status }) => status === 409)?.body
        .message,
    ).toBe('An active period entry already exists for this date');
    expect(
      await prisma.periodEvent.count({
        where: {
          userId: trackerUserId,
          startDate: new Date(`${concurrentPeriodDate}T00:00:00.000Z`),
          deletedAt: null,
        },
      }),
    ).toBe(1);

    const concurrentPeriodWinner = concurrentPeriodResponses.find(
      ({ status }) => status === 201,
    );
    if (!concurrentPeriodWinner) {
      throw new Error('Expected one concurrent period-create winner');
    }
    await request(app.getHttpServer())
      .delete(
        `/api/v1/periods/${concurrentPeriodWinner.body.id}?version=${concurrentPeriodWinner.body.version}`,
      )
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(204);

    const replacementPeriod = await request(app.getHttpServer())
      .post('/api/v1/periods')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ id: randomUUID(), startDate: concurrentPeriodDate })
      .expect(201);
    await request(app.getHttpServer())
      .delete(
        `/api/v1/periods/${replacementPeriod.body.id}?version=${replacementPeriod.body.version}`,
      )
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(204);
    expect(
      await prisma.periodEvent.count({
        where: {
          userId: trackerUserId,
          startDate: new Date(`${concurrentPeriodDate}T00:00:00.000Z`),
          deletedAt: null,
        },
      }),
    ).toBe(0);

    await request(app.getHttpServer())
      .post('/api/v1/periods')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .send({ id: randomUUID(), startDate: '2026-07-30' })
      .expect(403);

    const prediction = await request(app.getHttpServer())
      .get('/api/v1/predictions')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(200);
    expect(prediction.body.available).toBe(true);
    expect(prediction.body.typicalCycleLength).toBe(30);
    // Phase disclosure is evaluated against today, so anchor this assertion to
    // a recent completed period instead of a calendar date that will age out.
    const phaseAnchorStart = new Date();
    phaseAnchorStart.setUTCDate(phaseAnchorStart.getUTCDate() - 10);
    const phaseAnchorEnd = new Date(phaseAnchorStart);
    phaseAnchorEnd.setUTCDate(phaseAnchorEnd.getUTCDate() + 4);
    await request(app.getHttpServer())
      .post('/api/v1/periods')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({
        id: randomUUID(),
        startDate: phaseAnchorStart.toISOString().slice(0, 10),
        endDate: phaseAnchorEnd.toISOString().slice(0, 10),
      })
      .expect(201);
    await request(app.getHttpServer())
      .patch('/api/v1/sharing')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ sharePhase: true })
      .expect(200);
    const phaseDashboard = await request(app.getHttpServer())
      .get('/api/v1/partner/dashboard')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .expect(200);
    expect(phaseDashboard.body.phase).toMatch(/_ESTIMATE$|MENSTRUATION/);

    const logId = randomUUID();
    await request(app.getHttpServer())
      .post('/api/v1/daily-logs')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({
        id: logId,
        localDate: new Date().toISOString().slice(0, 10),
        painLevel: 9,
        customSymptoms: ['Krämpfe'],
        note: 'Diese Notiz bleibt privat',
      })
      .expect(201);
    const encryptedAtRest = await prisma.dailyLog.findUniqueOrThrow({
      where: { id: logId },
    });
    expect(encryptedAtRest.encryptedNote).not.toContain(
      'Diese Notiz bleibt privat',
    );

    const dashboard = await request(app.getHttpServer())
      .get('/api/v1/partner/dashboard')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .expect(200);
    expect(dashboard.body.prediction.predictedStartFrom).toBeDefined();
    expect(JSON.stringify(dashboard.body)).not.toContain('Krämpfe');
    expect(JSON.stringify(dashboard.body)).not.toContain(
      'Diese Notiz bleibt privat',
    );
    expect(JSON.stringify(dashboard.body)).not.toContain('painLevel');

    const dailyTip = await request(app.getHttpServer())
      .get('/api/v1/tips/today')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .expect(200);
    expect(dailyTip.body.tip.id).toMatch(/^[0-9a-f-]{36}$/);
    await request(app.getHttpServer())
      .post(`/api/v1/tips/${dailyTip.body.tip.id}/feedback`)
      .set('Authorization', `Bearer ${partnerAccess}`)
      .send({ helpful: true })
      .expect(201);

    const foreignTipId = randomUUID();
    await prisma.partnerTip.create({
      data: {
        id: foreignTipId,
        category: TipCategory.USER_DEFINED,
        createdByUserId: foreignUserId,
        householdId: (
          await prisma.membership.findFirstOrThrow({
            where: { userId: foreignUserId },
            select: { householdId: true },
          })
        ).householdId,
        titleDe: 'Fremder Hinweis',
        bodyDe: 'Dieser Text gehört in einen anderen Haushalt.',
        callToActionDe: 'Nicht anzeigen',
        titleEn: 'Foreign tip',
        bodyEn: 'This text belongs to another household.',
        callToActionEn: 'Do not show',
      },
    });
    await request(app.getHttpServer())
      .post(`/api/v1/tips/${foreignTipId}/feedback`)
      .set('Authorization', `Bearer ${partnerAccess}`)
      .send({ helpful: true })
      .expect(404);

    const builtInCategories = Object.values(TipCategory).filter(
      (category) => category !== TipCategory.USER_DEFINED,
    );
    await request(app.getHttpServer())
      .patch('/api/v1/sharing')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ disabledTipCategories: builtInCategories })
      .expect(200);
    await request(app.getHttpServer())
      .get('/api/v1/tips/today')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .expect(200)
      .expect(({ body }) => expect(body.tip).toBeNull());

    const ownTipId = randomUUID();
    const ownTipBody = {
      id: ownTipId,
      titleDe: 'Eigener Hinweis',
      bodyDe: 'Eine persönliche, respektvolle Unterstützungsidee.',
      callToActionDe: 'Frag nach einer kleinen Hilfe.',
      titleEn: 'Personal tip',
      bodyEn: 'A personal and respectful support idea.',
      callToActionEn: 'Ask about one small way to help.',
      priority: 100,
    };
    await request(app.getHttpServer())
      .post('/api/v1/tips/custom')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .send(ownTipBody)
      .expect(403);
    await request(app.getHttpServer())
      .post('/api/v1/tips/custom')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send(ownTipBody)
      .expect(201)
      .expect(({ body }) =>
        expect(body.category).toBe(TipCategory.USER_DEFINED),
      );
    await request(app.getHttpServer())
      .patch(`/api/v1/tips/custom/${foreignTipId}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ titleDe: 'Nicht erlaubt' })
      .expect(404);
    await request(app.getHttpServer())
      .delete(`/api/v1/tips/custom/${foreignTipId}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(404);
    await request(app.getHttpServer())
      .get('/api/v1/tips/custom')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body).toEqual([expect.objectContaining({ id: ownTipId })]);
        expect(JSON.stringify(body)).not.toContain(foreignTipId);
      });

    await request(app.getHttpServer())
      .get('/api/v1/tips/today')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .expect(200)
      .expect(({ body }) => expect(body.tip.id).toBe(ownTipId));
    await request(app.getHttpServer())
      .delete(`/api/v1/tips/custom/${ownTipId}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(204);

    const symptomTipId = randomUUID();
    await request(app.getHttpServer())
      .post('/api/v1/tips/custom')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({
        id: symptomTipId,
        titleDe: 'Freigegebener Symptomhinweis',
        bodyDe: 'Nur mit einer tatsächlich passenden Freigabe.',
        callToActionDe: 'Frag nach einer Wärmflasche.',
        titleEn: 'Shared symptom tip',
        bodyEn: 'Only with an actually matching permission.',
        callToActionEn: 'Ask about a hot-water bottle.',
        requiredSharedFields: ['shareSymptoms'],
        applicableSymptoms: ['Krämpfe'],
        priority: 100,
      })
      .expect(201);
    await request(app.getHttpServer())
      .get('/api/v1/tips/today')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .expect(200)
      .expect(({ body }) => expect(body.tip).toBeNull());
    await request(app.getHttpServer())
      .patch('/api/v1/sharing')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ shareSymptoms: true, allowSymptomBasedTips: false })
      .expect(200);
    await request(app.getHttpServer())
      .get('/api/v1/tips/today')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .expect(200)
      .expect(({ body }) => expect(body.tip).toBeNull());
    await request(app.getHttpServer())
      .patch('/api/v1/sharing')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ allowSymptomBasedTips: true })
      .expect(200);
    await request(app.getHttpServer())
      .get('/api/v1/tips/today')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .expect(200)
      .expect(({ body }) => expect(body.tip.id).toBe(symptomTipId));
    await request(app.getHttpServer())
      .patch('/api/v1/care-preferences')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ customUnhelpfulActions: ['Frag nach einer Wärmflasche.'] })
      .expect(200);
    await request(app.getHttpServer())
      .get('/api/v1/tips/today')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .expect(200)
      .expect(({ body }) => expect(body.tip).toBeNull());
    await request(app.getHttpServer())
      .delete(`/api/v1/tips/custom/${symptomTipId}`)
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(204);
    await request(app.getHttpServer())
      .get('/api/v1/tips/custom?includeDisabled=true')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({ id: ownTipId, enabled: false }),
            expect.objectContaining({ id: symptomTipId, enabled: false }),
          ]),
        );
        expect(JSON.stringify(body)).not.toContain(foreignTipId);
      });

    const exported = await request(app.getHttpServer())
      .post('/api/v1/export')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ format: 'JSON' })
      .expect(201);
    expect(exported.body.data.format).toBe('lunara-backup');
    expect(exported.body.data.dailyLogs[0].note).toBe(
      'Diese Notiz bleibt privat',
    );
    await request(app.getHttpServer())
      .post('/api/v1/import')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({ data: exported.body.data, preview: true })
      .expect(201)
      .expect(({ body }) => {
        expect(body.preview).toBe(true);
        expect(body.periods.duplicates).toBe(5);
        expect(body.dailyLogs.duplicates).toBe(1);
      });

    const mutationId = randomUUID();
    const offlineId = randomUUID();
    const syncBody = {
      cursor: '0',
      mutations: [
        {
          clientMutationId: mutationId,
          entityType: 'DAILY_LOG',
          operation: 'UPSERT',
          payload: {
            id: offlineId,
            localDate: '2026-08-11',
            energy: 6,
            customSymptoms: [],
          },
        },
      ],
    };
    await prisma.idempotencyKey.create({
      data: {
        userId: trackerUserId,
        householdId: trackerHouseholdId,
        key: mutationId,
        method: 'POST',
        path: '/api/v1/sync',
        requestHash: '0'.repeat(64),
        responseStatus: 102,
        responseBody: { pending: true },
        expiresAt: new Date(Date.now() - 60_000),
      },
    });
    const initialSyncPush = await request(app.getHttpServer())
      .post('/api/v1/sync')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send(syncBody)
      .expect(201);
    expect(initialSyncPush.body.changes).not.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ entityId: foreignPeriodId }),
      ]),
    );
    expect(JSON.stringify(initialSyncPush.body)).not.toContain(foreignPeriodId);
    await request(app.getHttpServer())
      .post('/api/v1/sync')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send({
        cursor: '0',
        mutations: [
          {
            clientMutationId: randomUUID(),
            entityType: 'DAILY_LOG',
            operation: 'UPSERT',
            payload: {
              id: randomUUID(),
              localDate: '2026-08-09',
              painLevel: 99,
              customSymptoms: [],
            },
          },
        ],
      })
      .expect(400);
    await request(app.getHttpServer())
      .post('/api/v1/sync')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .send(syncBody)
      .expect(201);
    expect(await prisma.dailyLog.count({ where: { id: offlineId } })).toBe(1);

    const concurrentId = randomUUID();
    const concurrentMutation = {
      cursor: '0',
      mutations: [
        {
          clientMutationId: randomUUID(),
          entityType: 'DAILY_LOG',
          operation: 'UPSERT',
          payload: {
            id: concurrentId,
            localDate: '2026-08-10',
            energy: 5,
            customSymptoms: [],
          },
        },
      ],
    };
    const concurrentResponses = await Promise.all([
      request(app.getHttpServer())
        .post('/api/v1/sync')
        .set('Authorization', `Bearer ${trackerAccess}`)
        .send(concurrentMutation),
      request(app.getHttpServer())
        .post('/api/v1/sync')
        .set('Authorization', `Bearer ${trackerAccess}`)
        .send(concurrentMutation),
    ]);
    expect(
      concurrentResponses.some((response) => response.status === 201),
    ).toBe(true);
    expect(
      concurrentResponses.every((response) =>
        [201, 409].includes(response.status),
      ),
    ).toBe(true);
    expect(await prisma.dailyLog.count({ where: { id: concurrentId } })).toBe(
      1,
    );

    await request(app.getHttpServer())
      .delete(
        `/api/v1/periods/${firstCreatedPeriod.id}?version=${firstCreatedPeriod.version}`,
      )
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(204);
    const softDeleted = await prisma.periodEvent.findUniqueOrThrow({
      where: { id: firstCreatedPeriod.id },
    });
    expect(softDeleted.deletedAt).toBeInstanceOf(Date);
    expect(softDeleted.version).toBe(firstCreatedPeriod.version + 1);
    const visiblePeriods = await request(app.getHttpServer())
      .get('/api/v1/periods')
      .set('Authorization', `Bearer ${trackerAccess}`)
      .expect(200);
    expect(visiblePeriods.body).not.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ id: firstCreatedPeriod.id }),
      ]),
    );

    const raceLogin = await request(app.getHttpServer())
      .post('/api/v1/auth/login')
      .send({
        username: 'tracker-e2e',
        password: 'A secure tracker password 2026!',
        deviceName: 'Concurrent refresh device',
      })
      .expect(201);
    const concurrentRefreshes = await Promise.all([
      request(app.getHttpServer())
        .post('/api/v1/auth/refresh')
        .send({ refreshToken: raceLogin.body.refreshToken }),
      request(app.getHttpServer())
        .post('/api/v1/auth/refresh')
        .send({ refreshToken: raceLogin.body.refreshToken }),
    ]);
    expect(
      concurrentRefreshes.map((response) => response.status).sort(),
    ).toEqual([201, 401]);
    const concurrentWinner = concurrentRefreshes.find(
      (response) => response.status === 201,
    );
    if (!concurrentWinner)
      throw new Error('Expected one concurrent refresh winner');
    await request(app.getHttpServer())
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: concurrentWinner.body.refreshToken })
      .expect(401);
    await request(app.getHttpServer())
      .get('/api/v1/me')
      .set('Authorization', `Bearer ${concurrentWinner.body.accessToken}`)
      .expect(401);

    const rotated = await request(app.getHttpServer())
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: originalRefresh })
      .expect(201);
    expect(rotated.body.refreshToken).not.toBe(originalRefresh);
    await request(app.getHttpServer())
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: originalRefresh })
      .expect(401);
    await request(app.getHttpServer())
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: rotated.body.refreshToken })
      .expect(401);

    await prisma.user.delete({ where: { id: foreignUserId } });
    await prisma.household.delete({ where: { id: foreignHousehold.id } });
    const authService = app.get(AuthService);
    const trackerIdentity = {
      id: trackerUserId,
      username: 'tracker-e2e',
      displayName: 'Tracker',
      preferredLanguage: 'DE' as const,
      role: UserRole.TRACKER,
      householdId: trackerHouseholdId,
    };
    const passwordChangeSession = await authService.createAuthenticatedSession(
      prisma,
      trackerIdentity,
      'Password change device',
    );
    const otherPasswordSession = await authService.createAuthenticatedSession(
      prisma,
      trackerIdentity,
      'Session revoked by password change',
    );
    const wrongPasswordChange = await request(app.getHttpServer())
      .post('/api/v1/auth/change-password')
      .set('Authorization', `Bearer ${passwordChangeSession.accessToken}`)
      .send({
        currentPassword: 'An incorrect current password!',
        newPassword: 'A new secure tracker password 2026!',
      })
      .expect(403);
    expect(wrongPasswordChange.body.message).toBe(
      'Password change could not be authorized',
    );
    await request(app.getHttpServer())
      .post('/api/v1/auth/change-password')
      .set('Authorization', `Bearer ${passwordChangeSession.accessToken}`)
      .send({
        currentPassword: 'A secure tracker password 2026!',
        newPassword: 'A secure tracker password 2026!',
      })
      .expect(400);
    expect(
      (
        await prisma.deviceSession.findUniqueOrThrow({
          where: { id: otherPasswordSession.sessionId },
        })
      ).revokedAt,
    ).toBeNull();
    await request(app.getHttpServer())
      .post('/api/v1/auth/change-password')
      .set('Authorization', `Bearer ${passwordChangeSession.accessToken}`)
      .send({
        currentPassword: 'A secure tracker password 2026!',
        newPassword: 'A new secure tracker password 2026!',
      })
      .expect(204);
    await request(app.getHttpServer())
      .get('/api/v1/me')
      .set('Authorization', `Bearer ${passwordChangeSession.accessToken}`)
      .expect(200);
    expect(
      await prisma.deviceSession.findUniqueOrThrow({
        where: { id: passwordChangeSession.sessionId },
      }),
    ).toEqual(expect.objectContaining({ revokedAt: null }));
    expect(
      (
        await prisma.deviceSession.findUniqueOrThrow({
          where: { id: otherPasswordSession.sessionId },
        })
      ).revokedAt,
    ).toBeInstanceOf(Date);
    await request(app.getHttpServer())
      .get('/api/v1/me')
      .set('Authorization', `Bearer ${otherPasswordSession.accessToken}`)
      .expect(401);
    await request(app.getHttpServer())
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: otherPasswordSession.refreshToken })
      .expect(401);
    await request(app.getHttpServer())
      .get('/api/v1/me')
      .set('Authorization', `Bearer ${partnerAccess}`)
      .expect(200);
    expect(
      await prisma.auditEvent.count({
        where: {
          actorUserId: trackerUserId,
          type: AuditEventType.PASSWORD_CHANGE_FAILED,
        },
      }),
    ).toBe(2);
    expect(
      await prisma.auditEvent.count({
        where: {
          actorUserId: trackerUserId,
          type: AuditEventType.PASSWORD_CHANGED,
        },
      }),
    ).toBe(1);
    await request(app.getHttpServer())
      .post('/api/v1/auth/login')
      .send({
        username: 'tracker-e2e',
        password: 'A secure tracker password 2026!',
        deviceName: 'Old password must fail',
      })
      .expect(401);
    const newPasswordLogin = await request(app.getHttpServer())
      .post('/api/v1/auth/login')
      .send({
        username: 'tracker-e2e',
        password: 'A new secure tracker password 2026!',
        deviceName: 'New password succeeds',
      })
      .expect(201);
    const currentRefreshAfterChange = await request(app.getHttpServer())
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: passwordChangeSession.refreshToken })
      .expect(201);
    // Reproduce the guard/refresh interleaving deterministically: the access
    // guard had already built a context for the now-rotated S0 session while
    // refresh created S1 in the same token family. A password change using that
    // stale context must preserve S1 while revoking every other family.
    await authService.changePassword(
      {
        userId: trackerUserId,
        sessionId: passwordChangeSession.sessionId,
        householdId: trackerHouseholdId,
        membershipId: randomUUID(),
        role: UserRole.TRACKER,
        username: 'tracker-e2e',
        displayName: 'Tracker',
        preferredLanguage: 'DE',
      },
      {
        currentPassword: 'A new secure tracker password 2026!',
        newPassword: 'A final secure tracker password 2026!',
      },
    );
    await request(app.getHttpServer())
      .get('/api/v1/me')
      .set(
        'Authorization',
        `Bearer ${currentRefreshAfterChange.body.accessToken}`,
      )
      .expect(200);
    await request(app.getHttpServer())
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: currentRefreshAfterChange.body.refreshToken })
      .expect(201);
    await request(app.getHttpServer())
      .get('/api/v1/me')
      .set('Authorization', `Bearer ${newPasswordLogin.body.accessToken}`)
      .expect(401);
    await expect(
      authService.login({
        username: 'tracker-e2e',
        password: 'A new secure tracker password 2026!',
        deviceName: 'Intermediate password must fail',
      }),
    ).rejects.toThrow('Invalid username or password');
    const finalPasswordLogin = await authService.login({
      username: 'tracker-e2e',
      password: 'A final secure tracker password 2026!',
      deviceName: 'Final password succeeds',
    });
    expect(
      await prisma.auditEvent.count({
        where: {
          actorUserId: trackerUserId,
          type: AuditEventType.PASSWORD_CHANGED,
        },
      }),
    ).toBe(2);
    await request(app.getHttpServer())
      .delete('/api/v1/account')
      .set('Authorization', `Bearer ${finalPasswordLogin.accessToken}`)
      .send({
        password: 'A final secure tracker password 2026!',
        deleteHousehold: true,
      })
      .expect(204);
    await prisma.$transaction(async (tx) => {
      await tx.user.delete({ where: { id: secondTrackerUserId } });
      await tx.household.delete({ where: { id: secondHouseholdId } });
      await tx.user.delete({ where: { id: thirdTrackerUserId } });
      await tx.household.delete({ where: { id: thirdHouseholdId } });
    });
    expect(
      await prisma.user.count({
        where: { id: { in: [trackerUserId, partnerUserId] } },
      }),
    ).toBe(0);
    expect(
      await prisma.deviceSession.count({
        where: { userId: { in: [trackerUserId, partnerUserId] } },
      }),
    ).toBe(0);
    expect(
      await prisma.periodEvent.count({ where: { userId: trackerUserId } }),
    ).toBe(0);
    expect(
      await prisma.dailyLog.count({ where: { userId: trackerUserId } }),
    ).toBe(0);
    expect(
      await prisma.auditEvent.count({
        where: {
          type: 'ACCOUNT_DELETED',
          householdId: null,
          actorUserId: null,
        },
      }),
    ).toBe(1);
    await request(app.getHttpServer())
      .get('/api/v1/meta')
      .expect(200)
      .expect('Cache-Control', 'no-store, max-age=0')
      .expect('Pragma', 'no-cache')
      .expect(({ body }) => expect(body.setupRequired).toBe(false));

    // Removing the last relationship must never reopen the privileged setup
    // endpoint. Ordinary registration can still create a new isolated
    // relationship when the administrator explicitly enables it.
    await request(app.getHttpServer())
      .post('/api/v1/setup')
      .send({
        bootstrapToken: process.env.BOOTSTRAP_TOKEN,
        username: 'bootstrap-must-stay-closed',
        password: 'A secure bootstrap replay password!',
        displayName: 'Bootstrap replay',
        deviceName: 'Bootstrap replay device',
      })
      .expect(409);
    config.set('lunara.registrationEnabled', true);
    await request(app.getHttpServer())
      .post('/api/v1/auth/register')
      .send({
        username: 'new-after-all-deleted',
        password: 'A secure post-deletion password!',
        displayName: 'New relationship',
        deviceName: 'Post-deletion registration device',
      })
      .expect(201)
      .expect(({ body }) => expect(body.user.role).toBe(UserRole.TRACKER));
  });
});
