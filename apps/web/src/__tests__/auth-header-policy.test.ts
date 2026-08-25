import { describe, expect, it } from 'vitest';
import { lunaraAuthHeaders, oidcGatewayHeaders } from '../auth';

describe('Lunara browser auth header policy', () => {
  it('attaches the app token only to the exact application origin', () => {
    expect(
      lunaraAuthHeaders({
        token: 'secret-token',
        requestUrl: '/api/v1/me',
        applicationOrigin: 'https://lunara.example.test',
      }),
    ).toEqual({ 'X-Lunara-Authorization': 'Bearer secret-token' });
  });

  it('never forwards a token to an Authentik or attacker origin', () => {
    expect(
      lunaraAuthHeaders({
        token: 'secret-token',
        requestUrl: 'https://auth.example.test/application/o/token/',
        applicationOrigin: 'https://lunara.example.test',
      }),
    ).toEqual({});
    expect(
      lunaraAuthHeaders({
        token: 'secret-token',
        requestUrl: 'https://lunara.example.test.evil.invalid/api/v1/me',
        applicationOrigin: 'https://lunara.example.test',
      }),
    ).toEqual({});
  });

  it('does not create an authorization header without a session', () => {
    expect(
      lunaraAuthHeaders({
        token: null,
        requestUrl: '/api/v1/meta',
        applicationOrigin: 'https://lunara.example.test',
      }),
    ).toEqual({});
  });

  it('uses an OIDC bearer only as same-origin outer gateway authorization', () => {
    expect(oidcGatewayHeaders({
      token: 'authentik-access',
      requestUrl: '/api/v1/auth/oidc/login',
      applicationOrigin: 'https://lunara.example.test',
    })).toEqual({ Authorization: 'Bearer authentik-access' });
    expect(oidcGatewayHeaders({
      token: 'authentik-access',
      requestUrl: 'https://attacker.invalid/collect',
      applicationOrigin: 'https://lunara.example.test',
    })).toEqual({});
  });
});
