import {
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { exportJWK, generateKeyPair, SignJWT, type KeyLike } from 'jose';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import {
  OidcTokenVerifier,
  OidcVerifierConfiguration,
  verifyOidcAccessToken,
} from './oidc-token-verifier.js';

const now = new Date('2026-08-25T12:00:00.000Z');
const nowSeconds = Math.floor(now.getTime() / 1_000);
const configuration: OidcVerifierConfiguration = {
  enabled: true,
  providerName: 'Authentik',
  issuer: 'https://auth.example/application/o/lunara/',
  audience: 'lunara-mobile',
  jwksUrl: 'https://auth.example/application/o/lunara/jwks/',
  allowedAlgorithms: ['RS256'],
  maxTokenAgeSeconds: 3_600,
  clockToleranceSeconds: 30,
};

describe('verifyOidcAccessToken', () => {
  let privateKey: KeyLike;
  let publicKey: KeyLike;

  beforeAll(async () => {
    ({ privateKey, publicKey } = await generateKeyPair('RS256'));
  });

  async function token(
    overrides: Record<string, unknown> = {},
    signingKey: KeyLike = privateKey,
    algorithm = 'RS256',
  ): Promise<string> {
    return new SignJWT({
      iss: configuration.issuer,
      aud: configuration.audience,
      sub: 'authentik-user-id-123',
      iat: nowSeconds - 10,
      exp: nowSeconds + 300,
      ...overrides,
    })
      .setProtectedHeader({ alg: algorithm, kid: 'test-key' })
      .sign(signingKey);
  }

  it('accepts an exact issuer, audience and subject without using profile claims', async () => {
    const rawToken = await token({
      email: 'must-not-be-used@example.test',
      preferred_username: 'must-not-be-used',
    });

    await expect(
      verifyOidcAccessToken(rawToken, configuration, publicKey, now),
    ).resolves.toEqual({
      issuer: configuration.issuer,
      subject: 'authentik-user-id-123',
      issuedAt: nowSeconds - 10,
      expiresAt: nowSeconds + 300,
    });
  });

  it('accepts strict display hints only with the signed profile scope', async () => {
    await expect(
      verifyOidcAccessToken(
        await token({
          scope: 'openid profile offline_access',
          name: 'Elias Example',
          preferred_username: 'Elias',
          email: 'not-used@example.test',
        }),
        configuration,
        publicKey,
        now,
      ),
    ).resolves.toMatchObject({
      issuer: configuration.issuer,
      subject: 'authentik-user-id-123',
      displayName: 'Elias Example',
      preferredUsername: 'Elias',
    });
  });

  it.each([
    ['scope is absent', { name: 'Elias', preferred_username: 'elias' }],
    ['name has controls', { scope: 'profile', name: 'Elias\u0000', preferred_username: 'elias' }],
    ['username has an unsafe shape', { scope: 'profile', name: 'Elias', preferred_username: '../elias' }],
    ['claims have wrong types', { scope: 'profile', name: ['Elias'], preferred_username: { value: 'elias' } }],
  ])('does not trust unusable profile hints when %s', async (_name, overrides) => {
    const result = await verifyOidcAccessToken(
      await token(overrides),
      configuration,
      publicKey,
      now,
    );
    if (_name === 'scope is absent') {
      expect(result).not.toHaveProperty('displayName');
      expect(result).not.toHaveProperty('preferredUsername');
    } else if (_name === 'name has controls') {
      expect(result).not.toHaveProperty('displayName');
      expect(result.preferredUsername).toBe('elias');
    } else if (_name === 'username has an unsafe shape') {
      expect(result.displayName).toBe('Elias');
      expect(result).not.toHaveProperty('preferredUsername');
    } else {
      expect(result).not.toHaveProperty('displayName');
      expect(result).not.toHaveProperty('preferredUsername');
    }
  });

  it.each([
    ['wrong issuer', { iss: 'https://attacker.example/' }],
    ['wrong audience', { aud: 'another-client' }],
    ['wrong authorized party', { azp: 'another-client' }],
    ['multiple audiences without azp', { aud: [configuration.audience, 'other'] }],
    ['missing subject', { sub: undefined }],
    ['blank subject', { sub: '   ' }],
    ['future issued-at', { iat: nowSeconds + 31 }],
    ['stale issued-at', { iat: nowSeconds - 3_631 }],
    ['expiration before issued-at', { exp: nowSeconds - 20 }],
    ['an excessive token lifetime', { exp: nowSeconds + 3_631 }],
  ])('rejects %s', async (_caseName, overrides) => {
    await expect(
      verifyOidcAccessToken(
        await token(overrides),
        configuration,
        publicKey,
        now,
      ),
    ).rejects.toBeDefined();
  });

  it('accepts multiple audiences only with the configured azp', async () => {
    await expect(
      verifyOidcAccessToken(
        await token({
          aud: [configuration.audience, 'resource-api'],
          azp: configuration.audience,
        }),
        configuration,
        publicKey,
        now,
      ),
    ).resolves.toMatchObject({ subject: 'authentik-user-id-123' });
  });

  it('rejects a validly formed token signed by an untrusted key', async () => {
    const attacker = await generateKeyPair('RS256');
    await expect(
      verifyOidcAccessToken(
        await token({}, attacker.privateKey),
        configuration,
        publicKey,
        now,
      ),
    ).rejects.toBeDefined();
  });

  it('rejects a signature algorithm outside the explicit allowlist', async () => {
    await expect(
      verifyOidcAccessToken(
        await token({}, privateKey, 'PS256'),
        configuration,
        publicKey,
        now,
      ),
    ).rejects.toBeDefined();
  });
});

describe('OidcTokenVerifier disabled mode', () => {
  it('fails closed without constructing or contacting a remote JWKS', async () => {
    const config = {
      get: jest.fn((_key: string, fallback: unknown) => fallback),
    };
    const verifier = new OidcTokenVerifier(config as never);

    expect(verifier.publicConfiguration()).toEqual({
      enabled: false,
      providerName: 'Authentik',
      issuer: '',
      audience: '',
    });
    await expect(verifier.verify('not-a-token')).rejects.toBeInstanceOf(
      ServiceUnavailableException,
    );
  });
});

describe('OidcTokenVerifier remote key availability', () => {
  let server: Server;
  let jwksUrl: string;
  let privateKey: KeyLike;
  let jwks: string;
  let mode: string;

  beforeAll(async () => {
    const keyPair = await generateKeyPair('RS256');
    privateKey = keyPair.privateKey;
    jwks = JSON.stringify({
      keys: [{ ...(await exportJWK(keyPair.publicKey)), kid: 'trusted-key' }],
    });
    server = createServer((request, response) => {
      if (mode === 'connection-reset') {
        request.socket.destroy();
        return;
      }
      if (mode === 'timeout') return;
      response.setHeader('Content-Type', 'application/json');
      if (mode === 'http-error') {
        response.writeHead(503).end('{}');
      } else if (mode === 'invalid-json') {
        response.end('not json');
      } else if (mode === 'invalid-jwks') {
        response.end('{"keys":null}');
      } else {
        response.end(jwks);
      }
    });
    await new Promise<void>((resolve) =>
      server.listen(0, '127.0.0.1', resolve),
    );
    jwksUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}/jwks`;
  });

  beforeEach(() => {
    mode = 'healthy';
  });

  afterAll(async () => {
    await new Promise<void>((resolve, reject) => {
      server.close((error) => (error ? reject(error) : resolve()));
      server.closeAllConnections();
    });
  });

  function verifier() {
    const values: Record<string, unknown> = {
      'lunara.oidc.enabled': true,
      'lunara.oidc.issuer': configuration.issuer,
      'lunara.oidc.audience': configuration.audience,
      'lunara.oidc.jwksUrl': jwksUrl,
    };
    return new OidcTokenVerifier({
      get: (key: string, fallback: unknown) => values[key] ?? fallback,
    } as never);
  }

  function signedToken(kid = 'trusted-key', key = privateKey) {
    return new SignJWT({ sub: 'test-subject' })
      .setProtectedHeader({ alg: 'RS256', kid })
      .setIssuer(configuration.issuer)
      .setAudience(configuration.audience)
      .setIssuedAt()
      .setExpirationTime('5m')
      .sign(key);
  }

  it.each([
    'http-error',
    'invalid-json',
    'invalid-jwks',
    'connection-reset',
    'timeout',
  ])(
    'reports %s as unavailable and verifies the same credential after recovery',
    async (failure) => {
      const instance = verifier();
      const rawToken = await signedToken();
      mode = failure;
      await expect(instance.verify(rawToken)).rejects.toBeInstanceOf(
        ServiceUnavailableException,
      );
      mode = 'healthy';
      await expect(instance.verify(rawToken)).resolves.toMatchObject({
        issuer: configuration.issuer,
        subject: 'test-subject',
      });
    },
    10_000,
  );

  it('still rejects an unknown signing key when the provider key set is available', async () => {
    await expect(
      verifier().verify(await signedToken('unknown-key')),
    ).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('still rejects a bad signature made with an untrusted key', async () => {
    const attacker = await generateKeyPair('RS256');
    await expect(
      verifier().verify(await signedToken('trusted-key', attacker.privateKey)),
    ).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('still rejects an expired token after resolving a healthy signing key', async () => {
    const rawToken = await new SignJWT({ sub: 'test-subject' })
      .setProtectedHeader({ alg: 'RS256', kid: 'trusted-key' })
      .setIssuer(configuration.issuer)
      .setAudience(configuration.audience)
      .setIssuedAt(Math.floor(Date.now() / 1_000) - 300)
      .setExpirationTime(Math.floor(Date.now() / 1_000) - 60)
      .sign(privateKey);
    await expect(verifier().verify(rawToken)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });
});

describe('OidcTokenVerifier fresh authentication', () => {
  it.each([
    'missing-auth-time',
    'old-auth-time',
    'future-auth-time',
    'before-auth-time',
  ])(
    'does not mistake a refreshed access token for a fresh login: %s',
    async (state) => {
      const instance = new OidcTokenVerifier({
        get: (_key: string, fallback: unknown) => fallback,
      } as never);
      const seconds = Math.floor(Date.now() / 1_000);
      jest.spyOn(instance, 'verify').mockResolvedValue({
        issuer: configuration.issuer,
        subject: 'test-subject',
        issuedAt: state === 'before-auth-time' ? seconds - 60 : seconds,
        expiresAt: seconds + 300,
        ...(state === 'missing-auth-time'
          ? {}
          : {
              authenticatedAt:
                state === 'old-auth-time'
                  ? seconds - 301
                  : state === 'future-auth-time'
                    ? seconds + 31
                    : seconds,
            }),
      });
      await expect(
        instance.verifyFreshAuthentication('test-token'),
      ).rejects.toBeInstanceOf(UnauthorizedException);
    },
  );

  it('preserves the production fresh-authentication check for a recent signed auth_time', async () => {
    const instance = new OidcTokenVerifier({
      get: (_key: string, fallback: unknown) => fallback,
    } as never);
    const seconds = Math.floor(Date.now() / 1_000);
    const identity = {
      issuer: configuration.issuer,
      subject: 'test-subject',
      issuedAt: seconds,
      expiresAt: seconds + 300,
      authenticatedAt: seconds - 60,
    };
    jest.spyOn(instance, 'verify').mockResolvedValue(identity);
    await expect(
      instance.verifyFreshAuthentication('test-token'),
    ).resolves.toEqual(identity);
  });
});
