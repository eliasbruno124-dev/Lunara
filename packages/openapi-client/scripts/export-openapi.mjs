import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const packageDir = resolve(scriptDir, '..');
const rootDir = resolve(packageDir, '../..');
const outputPath = resolve(packageDir, 'openapi/lunara-v1.openapi.json');

// Exporting metadata must never require live infrastructure or real secrets.
// Nest constructs the Prisma adapter but does not connect before app.init().
process.env.DATABASE_URL ??= 'postgresql://openapi:openapi@127.0.0.1:1/openapi';
process.env.AUTH_SIGNING_KEY ??= 'openapi-only-signing-key-not-used-0123456789-abcdefghijklmnopqrstuvwxyz';
process.env.REFRESH_TOKEN_PEPPER ??= 'openapi-only-refresh-pepper-not-used-0123456789-abcdefghijklmnopqrstuvwxyz';
process.env.BOOTSTRAP_TOKEN ??= 'openapi-only-bootstrap-token-not-used';
process.env.APP_MASTER_KEY ??= 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=';
process.env.PUBLIC_BASE_URL ??= 'https://lunara.example.com';

const { NestFactory } = await import('@nestjs/core');
const { DocumentBuilder, SwaggerModule } = await import('@nestjs/swagger');
const { AppModule } = await import(
  `${resolve(rootDir, 'services/api/dist/app.module.js')}?openapi=${Date.now()}`
);

const app = await NestFactory.create(AppModule, { logger: false, abortOnError: true });
const configuration = new DocumentBuilder()
  .setTitle('Lunara API')
  .setDescription(
    'Versioned API for the self-hosted Lunara Android application. ' +
      'Health information is returned according to server-side role and sharing checks.',
  )
  .setVersion('1.0.0')
  .addServer('https://lunara.example.com', 'Configured self-hosted server')
  .addBearerAuth(
    {
      type: 'http',
      scheme: 'bearer',
      bearerFormat: 'JWT',
      description: 'Normal Lunara access token transport.',
    },
    'bearer',
  )
  .addApiKey(
    {
      type: 'apiKey',
      in: 'header',
      name: 'X-Lunara-Authorization',
      description:
        'Use the value `Bearer <access-token>` when Authorization is occupied by an outer Basic-auth gateway.',
    },
    'gatewayBearer',
  )
  .build();

const document = SwaggerModule.createDocument(app, configuration, {
  operationIdFactory: (controller, method) =>
    `${controller.replace(/Controller$/, '')}_${method}`,
});
document.openapi = '3.0.3';
document.info['x-api-version'] = 'v1';
document.info['x-generated-from'] = 'services/api Nest metadata';
addResponseContracts(document);

const normalized = sortDeep(document);
await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, `${JSON.stringify(normalized, null, 2)}\n`, 'utf8');
await app.close();
process.stdout.write(`OpenAPI exported to ${outputPath}\n`);

function sortDeep(value) {
  if (Array.isArray(value)) return value.map(sortDeep);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, nested]) => [key, sortDeep(nested)]),
    );
  }
  return value;
}

function addResponseContracts(document) {
  const schemas = document.components.schemas;
  Object.assign(schemas, {
    MetaResponse: objectSchema({
      name: stringSchema(),
      version: stringSchema(),
      apiVersion: { type: 'string', enum: ['v1'] },
      setupRequired: booleanSchema(),
      registrationEnabled: booleanSchema(),
      serverTime: { type: 'string', format: 'date-time' },
      gatewayHeaderName: { type: 'string', enum: ['X-Lunara-Authorization'] },
    }),
    AuthUserResponse: objectSchema({
      id: uuidSchema(),
      username: stringSchema(),
      displayName: stringSchema(),
      preferredLanguage: { type: 'string', enum: ['DE', 'EN'] },
      role: { type: 'string', enum: ['TRACKER', 'PARTNER'] },
      householdId: uuidSchema(),
    }),
    AuthResponse: objectSchema({
      accessToken: stringSchema(),
      refreshToken: stringSchema(),
      tokenType: { type: 'string', enum: ['Bearer'] },
      expiresInSeconds: { type: 'integer', format: 'int32' },
      sessionId: uuidSchema(),
      user: ref('AuthUserResponse'),
    }),
    SessionResponse: objectSchema({
      id: uuidSchema(),
      deviceName: stringSchema(),
      lastUsedAt: dateTimeSchema(),
      createdAt: dateTimeSchema(),
      expiresAt: dateTimeSchema(),
      revokedAt: nullable(dateTimeSchema()),
      current: booleanSchema(),
    }),
    InvitationResponse: objectSchema({
      id: uuidSchema(),
      code: stringSchema(),
      assignedRole: { type: 'string', enum: ['PARTNER'] },
      expiresAt: dateTimeSchema(),
    }),
    PeriodResponse: objectSchema({
      id: uuidSchema(),
      startDate: dateSchema(),
      endDate: nullable(dateSchema()),
      source: { type: 'string', enum: ['MANUAL', 'IMPORT', 'SYNC'] },
      createdAt: dateTimeSchema(),
      updatedAt: dateTimeSchema(),
      deletedAt: nullable(dateTimeSchema()),
      version: { type: 'integer', format: 'int32' },
    }),
    DailyLogResponse: objectSchema({
      id: uuidSchema(),
      localDate: dateSchema(),
      flowLevel: nullable({ type: 'string', enum: ['SPOTTING', 'LIGHT', 'MEDIUM', 'HEAVY'] }),
      painLevel: nullable({ type: 'integer', format: 'int32' }),
      mood: nullable(stringSchema()),
      energy: nullable({ type: 'integer', format: 'int32' }),
      sleepQuality: nullable({ type: 'integer', format: 'int32' }),
      bodyTemperature: nullable({ type: 'number', format: 'double' }),
      ovulationTestResult: nullable({ type: 'string', enum: ['NEGATIVE', 'POSITIVE', 'UNCLEAR'] }),
      customSymptoms: { type: 'array', items: stringSchema() },
      note: nullable(stringSchema()),
      createdAt: dateTimeSchema(),
      updatedAt: dateTimeSchema(),
      deletedAt: nullable(dateTimeSchema()),
      version: { type: 'integer', format: 'int32' },
    }),
    PredictionResponse: {
      type: 'object',
      additionalProperties: true,
      required: ['available', 'confidence', 'algorithmVersion'],
      properties: {
        available: booleanSchema(),
        confidence: { type: 'string', enum: ['LOW', 'MEDIUM', 'HIGH', 'UNAVAILABLE'] },
        algorithmVersion: stringSchema(),
        predictedStartFrom: dateSchema(),
        predictedStartTo: dateSchema(),
        predictedEndFrom: dateSchema(),
        predictedEndTo: dateSchema(),
        fertileWindowFrom: nullable(dateSchema()),
        fertileWindowTo: nullable(dateSchema()),
        basedOnCycleCount: { type: 'integer', format: 'int32' },
        typicalCycleLength: { type: 'integer', format: 'int32' },
        typicalPeriodLength: { type: 'integer', format: 'int32' },
        variationDays: { type: 'integer', format: 'int32' },
        reason: stringSchema(),
      },
    },
    TipResponse: objectSchema({
      id: uuidSchema(),
      category: stringSchema(),
      title: stringSchema(),
      body: stringSchema(),
      callToAction: stringSchema(),
      applicablePhase: nullable(stringSchema()),
    }),
    TodayTipResponse: {
      type: 'object',
      required: ['tip'],
      properties: {
        date: dateSchema(),
        tip: nullable(ref('TipResponse')),
      },
    },
    CheckInResponse: objectSchema({
      id: uuidSchema(),
      householdId: uuidSchema(),
      senderUserId: uuidSchema(),
      recipientUserId: uuidSchema(),
      prompt: stringSchema(),
      response: nullable(stringSchema()),
      status: { type: 'string', enum: ['PENDING', 'ANSWERED', 'EXPIRED', 'DISMISSED'] },
      createdAt: dateTimeSchema(),
      expiresAt: dateTimeSchema(),
      answeredAt: nullable(dateTimeSchema()),
    }),
    PartnerDashboardResponse: {
      type: 'object',
      additionalProperties: true,
      required: ['sharingPaused'],
      properties: {
        sharingPaused: booleanSchema(),
        prediction: nullable(ref('PredictionResponse')),
        phase: nullable(stringSchema()),
        periodDates: { type: 'array', items: ref('PeriodResponse') },
        todayLog: nullable(ref('DailyLogResponse')),
      },
    },
    SyncResponse: {
      type: 'object',
      additionalProperties: true,
      required: ['cursor', 'hasMore', 'changes'],
      properties: {
        cursor: stringSchema(),
        hasMore: booleanSchema(),
        changes: { type: 'array', items: { type: 'object', additionalProperties: true } },
      },
    },
    GenericObjectResponse: { type: 'object', additionalProperties: true },
  });

  response(document, 'get', '/api/v1/meta', 200, ref('MetaResponse'));
  for (const path of ['/api/v1/setup', '/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/household/join']) {
    response(document, 'post', path, 201, ref('AuthResponse'));
  }
  response(document, 'get', '/api/v1/auth/sessions', 200, arrayOf('SessionResponse'));
  response(document, 'post', '/api/v1/household/invitations', 201, ref('InvitationResponse'));
  response(document, 'get', '/api/v1/periods', 200, arrayOf('PeriodResponse'));
  response(document, 'post', '/api/v1/periods', 201, ref('PeriodResponse'));
  response(document, 'patch', '/api/v1/periods/{id}', 200, ref('PeriodResponse'));
  response(document, 'get', '/api/v1/daily-logs', 200, arrayOf('DailyLogResponse'));
  response(document, 'post', '/api/v1/daily-logs', 201, ref('DailyLogResponse'));
  response(document, 'patch', '/api/v1/daily-logs/{id}', 200, ref('DailyLogResponse'));
  response(document, 'get', '/api/v1/predictions', 200, ref('PredictionResponse'));
  response(document, 'get', '/api/v1/partner/dashboard', 200, ref('PartnerDashboardResponse'));
  response(document, 'get', '/api/v1/sync', 200, ref('SyncResponse'));
  response(document, 'post', '/api/v1/sync', 201, ref('SyncResponse'));
  response(document, 'get', '/api/v1/tips/today', 200, ref('TodayTipResponse'));
  response(document, 'get', '/api/v1/check-ins', 200, arrayOf('CheckInResponse'));
  response(document, 'post', '/api/v1/check-ins', 201, ref('CheckInResponse'));
  response(document, 'post', '/api/v1/check-ins/{id}/respond', 201, ref('CheckInResponse'));

  for (const item of Object.values(document.paths)) {
    for (const operation of Object.values(item)) {
      if (!operation?.responses) continue;
      for (const [status, successfulResponse] of Object.entries(operation.responses)) {
        if (!status.startsWith('2') || status === '204' || successfulResponse.content) continue;
        successfulResponse.description ||= 'Successful response';
        successfulResponse.content = {
          'application/json': { schema: ref('GenericObjectResponse') },
        };
      }
    }
  }
}

function response(document, method, path, status, schema) {
  const operation = document.paths[path]?.[method];
  if (!operation) throw new Error(`Cannot attach response contract: ${method.toUpperCase()} ${path}`);
  operation.responses[String(status)] = {
    description: 'Successful response',
    content: { 'application/json': { schema } },
  };
}

function objectSchema(properties) {
  return {
    type: 'object',
    required: Object.keys(properties),
    properties,
  };
}

function ref(name) {
  return { $ref: `#/components/schemas/${name}` };
}

function arrayOf(name) {
  return { type: 'array', items: ref(name) };
}

function nullable(schema) {
  return { ...schema, nullable: true };
}

function stringSchema() {
  return { type: 'string' };
}

function booleanSchema() {
  return { type: 'boolean' };
}

function uuidSchema() {
  return { type: 'string', format: 'uuid' };
}

function dateSchema() {
  return { type: 'string', format: 'date' };
}

function dateTimeSchema() {
  return { type: 'string', format: 'date-time' };
}
