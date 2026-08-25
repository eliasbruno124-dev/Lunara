export interface LunaraConfig {
  appName: string;
  appVersion: string;
  publicBaseUrl: string;
  registrationEnabled: boolean;
  accessTokenTtlSeconds: number;
  refreshTokenTtlDays: number;
  corsOrigins: string[];
  androidAppLink: {
    enabled: boolean;
    packageName: string;
    certificateFingerprints: string[];
  };
  oidc: {
    enabled: boolean;
    providerName: string;
    issuer: string;
    audience: string;
    clientId: string;
    scopes: string[];
    discoveryUrl: string;
    jwksUrl: string;
    allowedAlgorithms: string[];
    maxTokenAgeSeconds: number;
    clockToleranceSeconds: number;
  };
}

const booleanValue = (value: string | undefined, fallback = false): boolean =>
  value === undefined ? fallback : value.toLowerCase() === 'true';

const defaultOidcDiscoveryUrl = (issuer: string): string =>
  issuer
    ? `${issuer.endsWith('/') ? issuer : `${issuer}/`}.well-known/openid-configuration`
    : '';

export const appConfig = (): { lunara: LunaraConfig } => ({
  lunara: {
    appName: process.env.APP_NAME?.trim() || 'Lunara',
    appVersion: process.env.APP_VERSION?.trim() || '1.1.0',
    publicBaseUrl: process.env.PUBLIC_BASE_URL?.trim() || 'http://localhost:3000',
    registrationEnabled: booleanValue(process.env.REGISTRATION_ENABLED),
    accessTokenTtlSeconds: Number(process.env.ACCESS_TOKEN_TTL_SECONDS || 900),
    refreshTokenTtlDays: Number(process.env.REFRESH_TOKEN_TTL_DAYS || 30),
    corsOrigins: (process.env.CORS_ORIGINS || '')
      .split(',')
      .map((origin) => origin.trim())
      .filter(Boolean),
    androidAppLink: {
      enabled: booleanValue(process.env.ANDROID_APP_LINK_ENABLED),
      packageName: process.env.ANDROID_APP_LINK_PACKAGE?.trim() || 'de.lunara.app',
      certificateFingerprints: (process.env.ANDROID_APP_LINK_CERT_SHA256 || '')
        .split(',')
        .map((fingerprint) => fingerprint.trim().toUpperCase())
        .filter(Boolean),
    },
    oidc: {
      enabled: booleanValue(process.env.OIDC_ENABLED),
      providerName: process.env.OIDC_PROVIDER_NAME?.trim() || 'Authentik',
      issuer: process.env.OIDC_ISSUER?.trim() || '',
      audience: process.env.OIDC_AUDIENCE?.trim() || '',
      clientId:
        process.env.OIDC_CLIENT_ID?.trim() ||
        process.env.OIDC_AUDIENCE?.trim() ||
        '',
      scopes: (process.env.OIDC_SCOPES || 'openid offline_access')
        .trim()
        .split(/\s+/u)
        .filter(Boolean),
      discoveryUrl:
        process.env.OIDC_DISCOVERY_URL?.trim() ||
        defaultOidcDiscoveryUrl(process.env.OIDC_ISSUER?.trim() || ''),
      jwksUrl: process.env.OIDC_JWKS_URL?.trim() || '',
      allowedAlgorithms: (process.env.OIDC_ALLOWED_ALGORITHMS || 'RS256')
        .split(',')
        .map((algorithm) => algorithm.trim())
        .filter(Boolean),
      maxTokenAgeSeconds: Number(
        process.env.OIDC_MAX_TOKEN_AGE_SECONDS || 3_600,
      ),
      clockToleranceSeconds: Number(
        process.env.OIDC_CLOCK_TOLERANCE_SECONDS || 30,
      ),
    },
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
  positiveInteger('OIDC_MAX_TOKEN_AGE_SECONDS', 3_600, 60, 86_400);
  positiveInteger('OIDC_CLOCK_TOLERANCE_SECONDS', 30, 0, 120);

  for (const key of ['REGISTRATION_ENABLED', 'NTPY_ENABLED', 'OPENAPI_ENABLED', 'OPENAPI_TRUST_AUTHENTIK', 'OIDC_ENABLED', 'ANDROID_APP_LINK_ENABLED']) {
    const raw = environment[key];
    if (raw !== undefined && (typeof raw !== 'string' || !['true', 'false'].includes(raw.toLowerCase()))) {
      throw new Error(`${key} must be true or false`);
    }
  }

  if (stringValue('ANDROID_APP_LINK_ENABLED', 'false').toLowerCase() === 'true') {
    const packageName = stringValue('ANDROID_APP_LINK_PACKAGE', 'de.lunara.app').trim();
    if (!/^[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+$/u.test(packageName)) {
      throw new Error('ANDROID_APP_LINK_PACKAGE must be a valid Android application ID');
    }
    const fingerprints = stringValue('ANDROID_APP_LINK_CERT_SHA256', '')
      .split(',')
      .map((fingerprint) => fingerprint.trim().toUpperCase())
      .filter(Boolean);
    const fingerprintPattern = /^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$/u;
    if (
      fingerprints.length < 1 ||
      fingerprints.length > 8 ||
      new Set(fingerprints).size !== fingerprints.length ||
      fingerprints.some((fingerprint) => !fingerprintPattern.test(fingerprint))
    ) {
      throw new Error(
        'ANDROID_APP_LINK_CERT_SHA256 must contain 1-8 unique colon-separated SHA-256 certificate fingerprints',
      );
    }
  }

  if (stringValue('OIDC_ENABLED', 'false').toLowerCase() === 'true') {
    const providerName = stringValue('OIDC_PROVIDER_NAME', 'Authentik').trim();
    if (providerName.length < 1 || providerName.length > 80) {
      throw new Error('OIDC_PROVIDER_NAME must contain between 1 and 80 characters');
    }
    const issuer = stringValue('OIDC_ISSUER').trim();
    const jwksUrl = stringValue('OIDC_JWKS_URL').trim();
    const discoveryUrl = stringValue(
      'OIDC_DISCOVERY_URL',
      defaultOidcDiscoveryUrl(issuer),
    ).trim();
    const audience = stringValue('OIDC_AUDIENCE').trim();
    if (audience.length < 1 || audience.length > 256 || /\s/u.test(audience)) {
      throw new Error('OIDC_AUDIENCE must contain between 1 and 256 non-whitespace characters');
    }
    if (issuer.length < 1 || issuer.length > 512) {
      throw new Error('OIDC_ISSUER must contain between 1 and 512 characters');
    }
    if (jwksUrl.length < 1 || jwksUrl.length > 2_048) {
      throw new Error('OIDC_JWKS_URL must contain between 1 and 2048 characters');
    }
    if (discoveryUrl.length < 1 || discoveryUrl.length > 2_048) {
      throw new Error('OIDC_DISCOVERY_URL must contain between 1 and 2048 characters');
    }
    const clientId = stringValue('OIDC_CLIENT_ID', audience).trim();
    if (clientId.length < 1 || clientId.length > 256 || /\s/u.test(clientId)) {
      throw new Error('OIDC_CLIENT_ID must contain between 1 and 256 non-whitespace characters');
    }
    const scopes = stringValue('OIDC_SCOPES', 'openid offline_access')
      .trim()
      .split(/\s+/u)
      .filter(Boolean);
    if (
      !scopes.includes('openid') ||
      scopes.length > 20 ||
      new Set(scopes).size !== scopes.length ||
      scopes.some((scope) => !/^[A-Za-z0-9._:-]{1,80}$/u.test(scope))
    ) {
      throw new Error('OIDC_SCOPES must be unique valid scopes and include openid');
    }
    for (const [key, rawUrl] of [
      ['OIDC_ISSUER', issuer],
      ['OIDC_JWKS_URL', jwksUrl],
      ['OIDC_DISCOVERY_URL', discoveryUrl],
    ] as const) {
      let parsed: URL;
      try {
        parsed = new URL(rawUrl);
      } catch {
        throw new Error(`${key} must be a valid HTTPS URL`);
      }
      if (
        parsed.protocol !== 'https:' ||
        parsed.username ||
        parsed.password ||
        parsed.search ||
        parsed.hash
      ) {
        throw new Error(`${key} must be a credential-free HTTPS URL without query or fragment`);
      }
    }
    const supportedAlgorithms = new Set([
      'RS256',
      'RS384',
      'RS512',
      'PS256',
      'PS384',
      'PS512',
      'ES256',
      'ES384',
      'ES512',
      'EdDSA',
    ]);
    const algorithms = stringValue('OIDC_ALLOWED_ALGORITHMS', 'RS256')
      .split(',')
      .map((algorithm) => algorithm.trim())
      .filter(Boolean);
    if (
      algorithms.length < 1 ||
      new Set(algorithms).size !== algorithms.length ||
      algorithms.some((algorithm) => !supportedAlgorithms.has(algorithm))
    ) {
      throw new Error(
        'OIDC_ALLOWED_ALGORITHMS must be a unique comma-separated asymmetric algorithm allowlist',
      );
    }
  }

  return environment;
}
