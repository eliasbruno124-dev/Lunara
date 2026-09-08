import { afterEach, describe, expect, it, vi } from 'vitest';
import { LunaraApi } from '../api';
import type { OidcRefreshSession, RefreshTokenStore, SessionTokens } from '../auth';
import {
  canStartOidc,
  finishOidcCallback,
  normalizeInvitationCode,
  startOidc,
  supportsOidcEnrollment,
} from '../oidc';
import type { AuthResponse, MetaResponse } from '../types';

const APP_ORIGIN = 'https://lunara.example.test';
const ISSUER = 'https://auth.example.test/application/o/lunara/';

function meta(enrollment = false): MetaResponse {
  return {
    name: 'Lunara',
    version: '1.1.2',
    apiVersion: 'v1',
    setupRequired: false,
    registrationEnabled: true,
    serverTime: '2026-08-29T08:00:00.000Z',
    gatewayMode: 'authentik',
    authenticationMethods: ['OIDC', 'PASSWORD'],
    ...(enrollment ? { primaryAuthenticationMethod: 'OIDC' as const } : {}),
    oidc: {
      providerName: 'Authentik',
      issuer: ISSUER,
      audience: 'lunara-api',
      discoveryUrl: `${ISSUER}.well-known/openid-configuration`,
      clientId: 'lunara-web',
      scopes: ['openid', 'profile', 'offline_access'],
      ...(enrollment
        ? {
            enrollment: {
              registration: true,
              invitation: true,
              localPasswordRequired: false,
              createIfMissingRequired: true,
            },
          }
        : {}),
    },
  };
}

function authResponse(): AuthResponse {
  return {
    accessToken: 'lunara-access',
    refreshToken: 'lunara-refresh',
    tokenType: 'Bearer',
    expiresInSeconds: 900,
    sessionId: 'session-id',
    user: {
      id: 'user-id',
      username: 'user',
      displayName: 'User',
      preferredLanguage: 'DE',
      role: 'TRACKER',
      householdId: 'household-id',
    },
  };
}

function jsonResponse(value: unknown): Response {
  return new Response(JSON.stringify(value), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

class MemoryTokenStore implements RefreshTokenStore {
  tokens: SessionTokens | null = null;

  async save(
    refreshToken: string,
    accessToken: string,
    oidc?: OidcRefreshSession,
  ): Promise<void> {
    this.tokens = { refreshToken, accessToken, ...(oidc ? { oidc } : {}) };
  }

  async load(): Promise<SessionTokens | null> {
    return this.tokens;
  }

  async clear(): Promise<void> {
    this.tokens = null;
  }
}

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();

  get length(): number {
    return this.values.size;
  }

  clear(): void {
    this.values.clear();
  }

  getItem(key: string): string | null {
    return this.values.get(key) ?? null;
  }

  key(index: number): string | null {
    return [...this.values.keys()][index] ?? null;
  }

  removeItem(key: string): void {
    this.values.delete(key);
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value);
  }
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('browser OIDC enrollment intents', () => {
  it('keeps legacy servers login-only and gates both new intents on explicit capability metadata', () => {
    const legacy = meta(false);
    expect(canStartOidc(legacy, 'login')).toBe(true);
    expect(canStartOidc(legacy, 'new-tracker')).toBe(false);
    expect(canStartOidc(legacy, 'partner')).toBe(false);
    expect(supportsOidcEnrollment(legacy)).toBe(false);

    const current = meta(true);
    expect(supportsOidcEnrollment(current)).toBe(true);
    expect(canStartOidc(current, 'new-tracker')).toBe(true);
    expect(canStartOidc(current, 'partner')).toBe(true);
  });

  it('accepts only normalized invitation alphabet and length', () => {
    expect(normalizeInvitationCode(' abcd2345 ')).toBe('ABCD2345');
    expect(normalizeInvitationCode('ABCO2345')).toBeNull();
    expect(normalizeInvitationCode('ABC123')).toBeNull();
  });

  it('sends mutually exclusive API fields for login, tracker creation, and partner invitation', async () => {
    const bodies: Array<Record<string, unknown>> = [];
    vi.stubGlobal('fetch', vi.fn(async (_input: URL | RequestInfo, init?: RequestInit) => {
      bodies.push(JSON.parse(String(init?.body)) as Record<string, unknown>);
      return jsonResponse(authResponse());
    }));
    const refresh: OidcRefreshSession = {
      accessToken: 'oidc-access',
      refreshToken: 'oidc-refresh',
      tokenEndpoint: 'https://auth.example.test/token',
      clientId: 'lunara-web',
    };

    await new LunaraApi(APP_ORIGIN, new MemoryTokenStore()).oidcLogin(
      'oidc-access',
      refresh,
      { kind: 'login', preferredLanguage: 'EN' },
    );
    await new LunaraApi(APP_ORIGIN, new MemoryTokenStore()).oidcLogin(
      'oidc-access',
      refresh,
      { kind: 'new-tracker', preferredLanguage: 'EN' },
    );
    await new LunaraApi(APP_ORIGIN, new MemoryTokenStore()).oidcLogin(
      'oidc-access',
      refresh,
      {
        kind: 'partner',
        invitationCode: 'ABCD2345',
        preferredLanguage: 'DE',
      },
    );

    expect(bodies).toEqual([
      {
        accessToken: 'oidc-access',
        deviceName: 'Lunara Web App',
      },
      {
        accessToken: 'oidc-access',
        deviceName: 'Lunara Web App',
        createIfMissing: true,
        preferredLanguage: 'EN',
      },
      {
        accessToken: 'oidc-access',
        deviceName: 'Lunara Web App',
        invitationCode: 'ABCD2345',
        preferredLanguage: 'DE',
      },
    ]);
  });

  it('requires an explicit confirmed request to accept an invitation for a signed-in account', async () => {
    const bodies: Array<Record<string, unknown>> = [];
    vi.stubGlobal('fetch', vi.fn(async (_input: URL | RequestInfo, init?: RequestInit) => {
      bodies.push(JSON.parse(String(init?.body)) as Record<string, unknown>);
      return jsonResponse({ accepted: true });
    }));
    const api = new LunaraApi(APP_ORIGIN, new MemoryTokenStore());
    await api.acceptInvitation('ABCD2345');
    expect(bodies).toEqual([{ invitationCode: 'ABCD2345', confirm: true }]);
  });

  it('checks the household before creating a non-expiring share link', async () => {
    const requests: Array<{ url: string; body: Record<string, unknown> | null }> = [];
    vi.stubGlobal('fetch', vi.fn(async (input: URL | RequestInfo, init?: RequestInit) => {
      requests.push({
        url: String(input),
        body: init?.body ? JSON.parse(String(init.body)) as Record<string, unknown> : null,
      });
      if (requests.length === 1) {
        return jsonResponse({ id: 'household', members: [{ role: 'TRACKER', disabled: false }] });
      }
      return jsonResponse({
        id: 'invite',
        code: 'ABCD2345',
        acceptanceUrl: `${APP_ORIGIN}/invite#invite=ABCD2345`,
        assignedRole: 'PARTNER',
        expiresAt: null,
      });
    }));
    const api = new LunaraApi(APP_ORIGIN, new MemoryTokenStore());
    const household = await api.household();
    expect(household.members.some((member) => member.role === 'PARTNER' && !member.disabled)).toBe(false);
    const invitation = await api.createInvitation();
    expect(invitation.acceptanceUrl).toBe(`${APP_ORIGIN}/invite#invite=ABCD2345`);
    expect(requests).toEqual([
      { url: `${APP_ORIGIN}/api/v1/household`, body: null },
      { url: `${APP_ORIGIN}/api/v1/household/invitations`, body: { neverExpires: true } },
    ]);
  });

  it('binds partner intent to PKCE state and consumes it exactly once on callback', async () => {
    const storage = new MemoryStorage();
    let assignedUrl = '';
    const location = {
      origin: APP_ORIGIN,
      href: `${APP_ORIGIN}/`,
      pathname: '/',
      assign: vi.fn((value: string) => {
        assignedUrl = value;
      }),
    };
    const replaceState = vi.fn();
    vi.stubGlobal('window', { location });
    vi.stubGlobal('sessionStorage', storage);
    vi.stubGlobal('history', { replaceState });
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        issuer: ISSUER,
        authorization_endpoint: `${ISSUER}authorize/`,
        token_endpoint: `${ISSUER}token/`,
        code_challenge_methods_supported: ['S256'],
      }))
      .mockResolvedValueOnce(jsonResponse({
        access_token: 'oidc-access',
        refresh_token: 'oidc-refresh',
      })));

    await startOidc(meta(true), {
      kind: 'partner',
      invitationCode: ' abcd2345 ',
      preferredLanguage: 'EN',
    });

    const pendingRaw = storage.getItem('lunara-oidc-pkce');
    expect(pendingRaw).not.toBeNull();
    expect(JSON.parse(pendingRaw ?? '{}')).toMatchObject({
      intent: 'partner',
      invitationCode: 'ABCD2345',
      preferredLanguage: 'EN',
    });
    const authorization = new URL(assignedUrl);
    location.href = `${APP_ORIGIN}/auth/callback?code=authorization-code&state=${authorization.searchParams.get('state') ?? ''}`;
    location.pathname = '/auth/callback';
    const oidcLogin = vi.fn().mockResolvedValue(authResponse());

    await expect(
      finishOidcCallback({ oidcLogin } as unknown as LunaraApi),
    ).resolves.toMatchObject({ sessionId: 'session-id' });
    expect(oidcLogin).toHaveBeenCalledWith(
      'oidc-access',
      {
        accessToken: 'oidc-access',
        refreshToken: 'oidc-refresh',
        tokenEndpoint: `${ISSUER}token/`,
        clientId: 'lunara-web',
      },
      {
        kind: 'partner',
        invitationCode: 'ABCD2345',
        preferredLanguage: 'EN',
      },
    );
    expect(storage.getItem('lunara-oidc-pkce')).toBeNull();
    expect(replaceState).toHaveBeenCalledWith({}, '', '/');

    await expect(
      finishOidcCallback({ oidcLogin } as unknown as LunaraApi),
    ).rejects.toThrow('Incomplete OIDC callback');
    expect(oidcLogin).toHaveBeenCalledTimes(1);
  });
});
