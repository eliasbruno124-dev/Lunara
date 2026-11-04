import type { LunaraApi } from './api';
import type { OidcRefreshSession } from './auth';
import type {
  AuthResponse,
  MetaResponse,
  OidcEnrollmentIntent,
  PreferredLanguage,
} from './types';

const STORAGE_KEY = 'lunara-oidc-pkce';
const CALLBACK_PATH = '/auth/callback';

interface DiscoveryDocument {
  issuer: string;
  authorization_endpoint: string;
  token_endpoint: string;
  code_challenge_methods_supported?: string[];
}

interface PendingAuthorization {
  state: string;
  verifier: string;
  issuer: string;
  tokenEndpoint: string;
  clientId: string;
  redirectUri: string;
  createdAt: number;
  intent: OidcEnrollmentIntent['kind'];
  preferredLanguage: PreferredLanguage;
  invitationCode?: string;
}

const INVITATION_PATTERN = /^[A-HJ-NP-Z2-9]{8,12}$/u;

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/u, '');
}

function randomUrlSafe(byteCount: number): string {
  return base64Url(crypto.getRandomValues(new Uint8Array(byteCount)));
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return base64Url(new Uint8Array(digest));
}

function secureEndpoint(value: string): URL {
  const url = new URL(value);
  if (url.protocol !== 'https:' || url.username || url.password) {
    throw new Error('OIDC endpoint must use credential-free HTTPS');
  }
  return url;
}

export function supportsOidcEnrollment(meta: MetaResponse): boolean {
  const enrollment = meta.oidc?.enrollment;
  return Boolean(
    meta.gatewayMode === 'authentik' &&
      meta.primaryAuthenticationMethod === 'OIDC' &&
      enrollment?.createIfMissingRequired === true &&
      enrollment.localPasswordRequired === false,
  );
}

export function canStartOidc(
  meta: MetaResponse,
  intent: OidcEnrollmentIntent['kind'] = 'login',
): boolean {
  const scopes = meta.oidc?.scopes ?? [];
  const configured = Boolean(
    meta.authenticationMethods?.includes('OIDC') &&
      meta.oidc?.clientId &&
      meta.oidc.discoveryUrl &&
      scopes.includes('openid') &&
      scopes.includes('offline_access'),
  );
  if (!configured || intent === 'login' || intent === 'link') return configured;
  if (!supportsOidcEnrollment(meta)) return false;
  if (intent === 'new-tracker') {
    return meta.registrationEnabled && meta.oidc?.enrollment?.registration === true;
  }
  return meta.oidc?.enrollment?.invitation === true;
}

export function normalizeInvitationCode(value: string): string | null {
  const normalized = value.trim().toUpperCase();
  return INVITATION_PATTERN.test(normalized) ? normalized : null;
}

function preferredLanguage(intent: OidcEnrollmentIntent): PreferredLanguage {
  return intent.preferredLanguage ?? 'DE';
}

function normalizedIntent(intent: OidcEnrollmentIntent): OidcEnrollmentIntent {
  if (intent.kind !== 'partner') return intent;
  const invitationCode = normalizeInvitationCode(intent.invitationCode);
  if (!invitationCode) throw new Error('Invitation code is invalid');
  return { ...intent, invitationCode };
}

export async function startOidc(
  meta: MetaResponse,
  requestedIntent: OidcEnrollmentIntent = { kind: 'login' },
): Promise<void> {
  const intent = normalizedIntent(requestedIntent);
  if (!canStartOidc(meta, intent.kind) || !meta.oidc?.clientId) {
    throw new Error('OIDC browser login is not configured');
  }
  const discoveryUrl = secureEndpoint(meta.oidc.discoveryUrl);
  const discoveryResponse = await fetch(discoveryUrl, {
    headers: { Accept: 'application/json' },
    cache: 'no-store',
    credentials: 'omit',
    redirect: 'error',
  });
  if (!discoveryResponse.ok) throw new Error('OIDC discovery failed');
  const discovery = (await discoveryResponse.json()) as DiscoveryDocument;
  const configuredIssuer = secureEndpoint(meta.oidc.issuer).href.replace(/\/$/u, '');
  const discoveredIssuer = secureEndpoint(discovery.issuer).href.replace(/\/$/u, '');
  if (configuredIssuer !== discoveredIssuer) throw new Error('OIDC issuer mismatch');
  if (
    discovery.code_challenge_methods_supported &&
    !discovery.code_challenge_methods_supported.includes('S256')
  ) {
    throw new Error('OIDC provider does not support PKCE S256');
  }
  const authorizationEndpoint = secureEndpoint(discovery.authorization_endpoint);
  const tokenEndpoint = secureEndpoint(discovery.token_endpoint);
  const verifier = randomUrlSafe(48);
  const state = randomUrlSafe(24);
  const redirectUri = `${window.location.origin}${CALLBACK_PATH}`;
  const pending: PendingAuthorization = {
    state,
    verifier,
    issuer: configuredIssuer,
    tokenEndpoint: tokenEndpoint.href,
    clientId: meta.oidc.clientId,
    redirectUri,
    createdAt: Date.now(),
    intent: intent.kind,
    preferredLanguage: preferredLanguage(intent),
    ...(intent.kind === 'partner' ? { invitationCode: intent.invitationCode } : {}),
  };
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(pending));
  authorizationEndpoint.searchParams.set('client_id', meta.oidc.clientId);
  authorizationEndpoint.searchParams.set('redirect_uri', redirectUri);
  authorizationEndpoint.searchParams.set('response_type', 'code');
  authorizationEndpoint.searchParams.set('scope', (meta.oidc.scopes ?? ['openid']).join(' '));
  authorizationEndpoint.searchParams.set('state', state);
  authorizationEndpoint.searchParams.set('code_challenge', await sha256(verifier));
  authorizationEndpoint.searchParams.set('code_challenge_method', 'S256');
  window.location.assign(authorizationEndpoint.href);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function pendingAuthorization(raw: string): PendingAuthorization {
  const value: unknown = JSON.parse(raw);
  if (!isRecord(value)) throw new Error('OIDC callback state is invalid');
  const intent = value.intent ?? 'login';
  const language = value.preferredLanguage ?? 'DE';
  if (
    typeof value.state !== 'string' ||
    typeof value.verifier !== 'string' ||
    typeof value.issuer !== 'string' ||
    typeof value.tokenEndpoint !== 'string' ||
    typeof value.clientId !== 'string' ||
    typeof value.redirectUri !== 'string' ||
    typeof value.createdAt !== 'number' ||
    !['login', 'new-tracker', 'partner', 'link'].includes(String(intent)) ||
    !['DE', 'EN'].includes(String(language))
  ) {
    throw new Error('OIDC callback state is invalid');
  }
  if (intent === 'partner') {
    if (typeof value.invitationCode !== 'string') {
      throw new Error('OIDC callback state is invalid');
    }
    const invitationCode = normalizeInvitationCode(value.invitationCode);
    if (!invitationCode) throw new Error('OIDC callback state is invalid');
    return {
      state: value.state,
      verifier: value.verifier,
      issuer: value.issuer,
      tokenEndpoint: value.tokenEndpoint,
      clientId: value.clientId,
      redirectUri: value.redirectUri,
      createdAt: value.createdAt,
      intent,
      preferredLanguage: language as PreferredLanguage,
      invitationCode,
    };
  }
  return {
    state: value.state,
    verifier: value.verifier,
    issuer: value.issuer,
    tokenEndpoint: value.tokenEndpoint,
    clientId: value.clientId,
    redirectUri: value.redirectUri,
    createdAt: value.createdAt,
    intent: intent as 'login' | 'new-tracker' | 'link',
    preferredLanguage: language as PreferredLanguage,
  };
}

function enrollmentIntent(pending: PendingAuthorization): OidcEnrollmentIntent {
  switch (pending.intent) {
    case 'login':
      return { kind: 'login' };
    case 'link':
      return { kind: 'link' };
    case 'new-tracker':
      return {
        kind: 'new-tracker',
        preferredLanguage: pending.preferredLanguage,
      };
    case 'partner':
      if (!pending.invitationCode) throw new Error('OIDC callback state is invalid');
      return {
        kind: 'partner',
        invitationCode: pending.invitationCode,
        preferredLanguage: pending.preferredLanguage,
      };
  }
}

/** Result of returning from the provider to connect it to the signed-in account. */
export interface OidcLinkCallback {
  linkAccessToken: string;
}

export async function finishOidcCallback(api: LunaraApi): Promise<AuthResponse | OidcLinkCallback | null> {
  const current = new URL(window.location.href);
  if (current.pathname !== CALLBACK_PATH) return null;
  const code = current.searchParams.get('code');
  const returnedState = current.searchParams.get('state');
  const error = current.searchParams.get('error');
  const pendingRaw = sessionStorage.getItem(STORAGE_KEY);
  sessionStorage.removeItem(STORAGE_KEY);
  history.replaceState({}, '', '/');
  if (error) throw new Error('OIDC authorization was rejected');
  if (!code || !returnedState || !pendingRaw) throw new Error('Incomplete OIDC callback');
  const pending = pendingAuthorization(pendingRaw);
  if (
    pending.state !== returnedState ||
    Date.now() - pending.createdAt > 10 * 60_000 ||
    pending.redirectUri !== `${window.location.origin}${CALLBACK_PATH}`
  ) {
    throw new Error('OIDC callback could not be verified');
  }
  const tokenResponse = await fetch(secureEndpoint(pending.tokenEndpoint), {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: pending.redirectUri,
      client_id: pending.clientId,
      code_verifier: pending.verifier,
    }),
    credentials: 'omit',
    cache: 'no-store',
    redirect: 'error',
  });
  if (!tokenResponse.ok) throw new Error('OIDC code exchange failed');
  const tokenBody = (await tokenResponse.json()) as {
    access_token?: string;
    refresh_token?: string;
  };
  if (!tokenBody.access_token || !tokenBody.refresh_token) {
    throw new Error('OIDC provider returned no renewable session');
  }
  // Connecting needs the signed-in Lunara session plus the password, not a new login.
  if (pending.intent === 'link') return { linkAccessToken: tokenBody.access_token };
  const refreshSession: OidcRefreshSession = {
    accessToken: tokenBody.access_token,
    refreshToken: tokenBody.refresh_token,
    tokenEndpoint: pending.tokenEndpoint,
    clientId: pending.clientId,
  };
  return api.oidcLogin(
    tokenBody.access_token,
    refreshSession,
    enrollmentIntent(pending),
  );
}
