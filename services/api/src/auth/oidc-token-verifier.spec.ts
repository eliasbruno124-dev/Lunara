import { ServiceUnavailableException } from '@nestjs/common';
import { generateKeyPair, SignJWT, type KeyLike } from 'jose';
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
