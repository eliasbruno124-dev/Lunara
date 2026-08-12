import { validateEnvironment } from './app.config.js';

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
});
