import { createHash } from 'node:crypto';

export function validateOidcIssuer(
  requestedIssuer: string,
  configuredIssuer: string,
): string {
  if (
    requestedIssuer.length > 512 ||
    requestedIssuer !== configuredIssuer ||
    requestedIssuer.trim() !== requestedIssuer
  ) {
    throw new Error('--issuer must exactly match the configured OIDC_ISSUER');
  }
  let parsed: URL;
  try {
    parsed = new URL(requestedIssuer);
  } catch {
    throw new Error('--issuer must be a valid HTTPS URL');
  }
  if (
    parsed.protocol !== 'https:' ||
    parsed.username ||
    parsed.password ||
    parsed.search ||
    parsed.hash
  ) {
    throw new Error('--issuer must be a credential-free HTTPS URL without query or fragment');
  }
  return requestedIssuer;
}

export function validateOidcSubject(subject: string | undefined): string {
  if (
    !subject ||
    subject.length > 255 ||
    subject !== subject.trim() ||
    [...subject].some((character) => {
      const codePoint = character.codePointAt(0) ?? 0;
      return codePoint <= 31 || codePoint === 127;
    })
  ) {
    throw new Error('--subject must be an exact, non-empty OIDC subject of at most 255 characters');
  }
  return subject;
}

export function optionalBoolean(
  options: Map<string, string>,
  name: string,
  fallback: boolean,
): boolean {
  const raw = options.get(name);
  if (raw === undefined) return fallback;
  if (raw !== 'true' && raw !== 'false') {
    throw new Error(`--${name} must be true or false`);
  }
  return raw === 'true';
}

export function oidcLinkFingerprint(issuer: string, subject: string): string {
  return createHash('sha256')
    .update(`${issuer}\n${subject}`, 'utf8')
    .digest('hex')
    .slice(0, 16);
}

export function oidcLinkConfirmation(
  userId: string,
  issuer: string,
  subject: string,
): string {
  return `LINK-OIDC:${userId}:${oidcLinkFingerprint(issuer, subject)}`;
}
