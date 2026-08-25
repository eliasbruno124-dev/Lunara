import { appConfig, validateEnvironment } from './app.config.js';

const valid = {
  NODE_ENV: 'production',
  DATABASE_URL: 'postgresql://lunara:password@postgres:5432/lunara',
  PUBLIC_BASE_URL: 'https://lunara.example.test',
  AUTH_SIGNING_KEY: 'a'.repeat(32),
  REFRESH_TOKEN_PEPPER: 'b'.repeat(32),
  BOOTSTRAP_TOKEN: 'c'.repeat(24),
  APP_MASTER_KEY: Buffer.alloc(32, 7).toString('base64'),
};

describe('validateEnvironment', () => {
  it('accepts secure production defaults', () => {
    expect(validateEnvironment({ ...valid })).toMatchObject(valid);
  });

  it('allows the bootstrap token to be removed after first setup', () => {
    const withoutBootstrap = { ...valid };
    delete (withoutBootstrap as Partial<typeof valid>).BOOTSTRAP_TOKEN;

    expect(validateEnvironment(withoutBootstrap)).toEqual(withoutBootstrap);
    expect(
      validateEnvironment({ ...withoutBootstrap, BOOTSTRAP_TOKEN: '' }),
    ).toMatchObject({ BOOTSTRAP_TOKEN: '' });
  });

  it('rejects a configured but weak bootstrap token', () => {
    expect(() =>
      validateEnvironment({ ...valid, BOOTSTRAP_TOKEN: 'too-short' }),
    ).toThrow('BOOTSTRAP_TOKEN');
  });

  it('rejects production HTTP and malformed boolean values', () => {
    expect(() =>
      validateEnvironment({ ...valid, PUBLIC_BASE_URL: 'http://lunara.example.test' }),
    ).toThrow('HTTPS');
    expect(() => validateEnvironment({ ...valid, NTPY_ENABLED: 'yes' })).toThrow(
      'NTPY_ENABLED must be true or false',
    );
  });

  it('rejects unsafe token lifetimes', () => {
    expect(() =>
      validateEnvironment({ ...valid, ACCESS_TOKEN_TTL_SECONDS: '86400' }),
    ).toThrow('ACCESS_TOKEN_TTL_SECONDS');
  });

  it('keeps OIDC disabled unless explicitly enabled', () => {
    const previous = process.env.OIDC_ENABLED;
    delete process.env.OIDC_ENABLED;
    try {
      expect(appConfig().lunara.oidc.enabled).toBe(false);
    } finally {
      if (previous === undefined) delete process.env.OIDC_ENABLED;
      else process.env.OIDC_ENABLED = previous;
    }
  });

  it('accepts a fixed HTTPS OIDC provider with a public PKCE client', () => {
    expect(
      validateEnvironment({
        ...valid,
        OIDC_ENABLED: 'true',
        OIDC_ISSUER: 'https://auth.example/application/o/lunara/',
        OIDC_JWKS_URL: 'https://auth.example/application/o/lunara/jwks/',
        OIDC_DISCOVERY_URL:
          'https://auth.example/application/o/lunara/.well-known/openid-configuration',
        OIDC_AUDIENCE: 'lunara-api',
        OIDC_CLIENT_ID: 'lunara-android',
        OIDC_SCOPES: 'openid profile',
        OIDC_ALLOWED_ALGORITHMS: 'RS256',
      }),
    ).toMatchObject({ OIDC_ENABLED: 'true' });
  });

  it.each([
    ['an HTTP issuer', { OIDC_ISSUER: 'http://auth.example/' }],
    ['a symmetric algorithm', { OIDC_ALLOWED_ALGORITHMS: 'HS256' }],
    ['an HTTP discovery endpoint', { OIDC_DISCOVERY_URL: 'http://auth.example/.well-known/openid-configuration' }],
    ['scopes without openid', { OIDC_SCOPES: 'profile email' }],
    ['an invalid enabled flag', { OIDC_ENABLED: 'yes' }],
  ])('rejects OIDC configuration with %s', (_caseName, override) => {
    expect(() =>
      validateEnvironment({
        ...valid,
        OIDC_ENABLED: 'true',
        OIDC_ISSUER: 'https://auth.example/application/o/lunara/',
        OIDC_JWKS_URL: 'https://auth.example/application/o/lunara/jwks/',
        OIDC_AUDIENCE: 'lunara-api',
        OIDC_ALLOWED_ALGORITHMS: 'RS256',
        ...override,
      }),
    ).toThrow();
  });
});
