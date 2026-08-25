import {
  oidcLinkConfirmation,
  oidcLinkFingerprint,
  optionalBoolean,
  validateOidcIssuer,
  validateOidcSubject,
} from './oidc-link.js';

describe('OIDC administrative linking safeguards', () => {
  const issuer = 'https://auth.example/application/o/lunara/';
  const subject = '4b59a9f5d2f84e46a0e7';
  const userId = '11111111-1111-4111-8111-111111111111';

  it('requires the exact configured HTTPS issuer', () => {
    expect(validateOidcIssuer(issuer, issuer)).toBe(issuer);
    expect(() =>
      validateOidcIssuer(
        'https://auth.example/application/o/other/',
        issuer,
      ),
    ).toThrow('exactly match');
    expect(() => validateOidcIssuer('http://auth.example/', 'http://auth.example/')).toThrow(
      'HTTPS',
    );
    const credentialedIssuer = ['https://user', 'pass@auth.example/'].join(':');
    expect(() => validateOidcIssuer(credentialedIssuer, credentialedIssuer)).toThrow(
      'credential-free',
    );
  });

  it('preserves an exact subject and rejects invisible or normalized variants', () => {
    expect(validateOidcSubject(subject)).toBe(subject);
    expect(() => validateOidcSubject(` ${subject}`)).toThrow('exact');
    expect(() => validateOidcSubject(`bad${String.fromCharCode(0)}subject`)).toThrow(
      'exact',
    );
    expect(() => validateOidcSubject(undefined)).toThrow('exact');
  });

  it('uses a deterministic non-reversible subject fingerprint in confirmation', () => {
    const fingerprint = oidcLinkFingerprint(issuer, subject);
    expect(fingerprint).toMatch(/^[a-f0-9]{16}$/u);
    expect(fingerprint).not.toContain(subject);
    expect(oidcLinkConfirmation(userId, issuer, subject)).toBe(
      `LINK-OIDC:${userId}:${fingerprint}`,
    );
  });

  it('accepts only explicit boolean values for password disabling', () => {
    expect(optionalBoolean(new Map(), 'disable-password', false)).toBe(false);
    expect(
      optionalBoolean(
        new Map([['disable-password', 'true']]),
        'disable-password',
        false,
      ),
    ).toBe(true);
    expect(() =>
      optionalBoolean(
        new Map([['disable-password', 'yes']]),
        'disable-password',
        false,
      ),
    ).toThrow('true or false');
  });
});
