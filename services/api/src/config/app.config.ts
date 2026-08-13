export interface LunaraConfig {
  appName: string;
  appVersion: string;
  publicBaseUrl: string;
  registrationEnabled: boolean;
  accessTokenTtlSeconds: number;
  refreshTokenTtlDays: number;
  corsOrigins: string[];
}

const booleanValue = (value: string | undefined, fallback = false): boolean =>
  value === undefined ? fallback : value.toLowerCase() === 'true';

export const appConfig = (): { lunara: LunaraConfig } => ({
  lunara: {
    appName: process.env.APP_NAME?.trim() || 'Lunara',
    appVersion: process.env.APP_VERSION?.trim() || '1.0.3',
    publicBaseUrl: process.env.PUBLIC_BASE_URL?.trim() || 'http://localhost:3000',
    registrationEnabled: booleanValue(process.env.REGISTRATION_ENABLED),
    accessTokenTtlSeconds: Number(process.env.ACCESS_TOKEN_TTL_SECONDS || 900),
    refreshTokenTtlDays: Number(process.env.REFRESH_TOKEN_TTL_DAYS || 30),
    corsOrigins: (process.env.CORS_ORIGINS || '')
      .split(',')
      .map((origin) => origin.trim())
      .filter(Boolean),
  },
});

export function validateEnvironment(
  environment: Record<string, unknown>,
): Record<string, unknown> {
  const stringValue = (key: string, fallback?: string): string => {
    const value = environment[key];
    if (value === undefined && fallback !== undefined) return fallback;
    if (typeof value !== 'string') {
      throw new Error(`${key} must be a string`);
    }
    return value;
  };
  const required = [
    'DATABASE_URL',
    'AUTH_SIGNING_KEY',
    'REFRESH_TOKEN_PEPPER',
    'APP_MASTER_KEY',
  ] as const;

  const missing = required.filter(
    (key) => typeof environment[key] !== 'string' || environment[key].length === 0,
  );
  if (missing.length > 0) {
    throw new Error(`Missing required environment variables: ${missing.join(', ')}`);
  }

  for (const key of ['AUTH_SIGNING_KEY', 'REFRESH_TOKEN_PEPPER', 'APP_MASTER_KEY'] as const) {
    if (stringValue(key).length < 32) {
      throw new Error(`${key} must contain at least 32 characters`);
    }
  }
  const masterKeyValue = stringValue('APP_MASTER_KEY');
  const canonicalBase64 = /^[A-Za-z0-9+/]{43}=$/.test(masterKeyValue);
  const decodedMasterKey = canonicalBase64
    ? Buffer.from(masterKeyValue, 'base64')
    : Buffer.alloc(0);
  if (
    decodedMasterKey.length !== 32 ||
    decodedMasterKey.toString('base64') !== masterKeyValue
  ) {
    throw new Error('APP_MASTER_KEY must be exactly 32 random bytes encoded as canonical base64');
  }
  const bootstrapToken = environment.BOOTSTRAP_TOKEN;
  if (bootstrapToken !== undefined && typeof bootstrapToken !== 'string') {
    throw new Error('BOOTSTRAP_TOKEN must be a string');
  }
  if (
    typeof bootstrapToken === 'string' &&
    bootstrapToken.length > 0 &&
    (bootstrapToken.length < 20 || bootstrapToken.length > 256)
  ) {
    throw new Error('BOOTSTRAP_TOKEN must contain between 20 and 256 characters');
  }

  const nodeEnv = stringValue('NODE_ENV', 'development');
  const publicBaseUrl = stringValue('PUBLIC_BASE_URL', '');
  if (nodeEnv === 'production' && !publicBaseUrl.startsWith('https://')) {
    throw new Error('PUBLIC_BASE_URL must use HTTPS in production');
  }

  const positiveInteger = (
    key: string,
    fallback: number,
    minimum: number,
    maximum: number,
  ): void => {
    const raw = stringValue(key, String(fallback));
    const value = Number(raw);
    if (!/^\d+$/.test(raw) || !Number.isSafeInteger(value) || value < minimum || value > maximum) {
      throw new Error(`${key} must be an integer between ${minimum} and ${maximum}`);
    }
  };
  positiveInteger('ACCESS_TOKEN_TTL_SECONDS', 900, 60, 3_600);
  positiveInteger('REFRESH_TOKEN_TTL_DAYS', 30, 1, 365);
  positiveInteger('INVITATION_TTL_MINUTES', 30, 5, 1_440);

  for (const key of ['REGISTRATION_ENABLED', 'NTPY_ENABLED', 'OPENAPI_ENABLED', 'OPENAPI_TRUST_AUTHENTIK']) {
    const raw = environment[key];
    if (raw !== undefined && (typeof raw !== 'string' || !['true', 'false'].includes(raw.toLowerCase()))) {
      throw new Error(`${key} must be true or false`);
    }
  }

  return environment;
}
