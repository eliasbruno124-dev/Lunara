import { afterEach, describe, expect, it, vi } from 'vitest';
import { LunaraApi } from '../api';
import type { OidcRefreshSession, RefreshTokenStore, SessionTokens } from '../auth';
import type { AuthResponse } from '../types';

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function authResponse(name: string): AuthResponse {
  return {
    accessToken: `${name}-access`,
    refreshToken: `${name}-refresh`,
    tokenType: 'Bearer',
    expiresInSeconds: 900,
    sessionId: `${name}-session`,
    user: {
      id: `${name}-user`,
      username: name,
      displayName: name,
      preferredLanguage: 'DE',
      role: 'TRACKER',
      householdId: `${name}-relationship`,
    },
  };
}

function jsonResponse(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

class RaceTokenStore implements RefreshTokenStore {
  tokens: SessionTokens | null = {
    accessToken: 'old-access',
    refreshToken: 'old-refresh',
  };
  readonly firstSaveStarted = deferred<void>();
  readonly releaseFirstSave = deferred<void>();
  blockFirstSave = false;
  saveCount = 0;

  async save(
    refreshToken: string,
    accessToken: string,
    oidc?: OidcRefreshSession,
  ): Promise<void> {
    this.saveCount += 1;
    if (this.blockFirstSave && this.saveCount === 1) {
      this.firstSaveStarted.resolve();
      await this.releaseFirstSave.promise;
    }
    this.tokens = { refreshToken, accessToken, ...(oidc ? { oidc } : {}) };
  }

  async load(): Promise<SessionTokens | null> {
    return this.tokens ? structuredClone(this.tokens) : null;
  }

  async clear(): Promise<void> {
    this.tokens = null;
  }
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('browser session generation boundary', () => {
  it('does not resurrect a refresh commit that was saving when forgetSession ran', async () => {
    vi.stubGlobal('navigator', { onLine: true });
    vi.stubGlobal('fetch', vi.fn(async (input: URL | RequestInfo) => {
      const url = new URL(String(input));
      if (url.pathname === '/api/v1/auth/refresh') return jsonResponse(authResponse('old-rotated'));
      throw new Error(`Unexpected request ${url.pathname}`);
    }));
    const store = new RaceTokenStore();
    store.blockFirstSave = true;
    const api = new LunaraApi('https://lunara.example.test', store);

    const restoring = api.restoreSession();
    await store.firstSaveStarted.promise;
    const forgetting = api.forgetSession();
    store.releaseFirstSave.resolve();

    await expect(restoring).resolves.toBe(false);
    await forgetting;
    expect(store.tokens).toBeNull();
  });

  it('invalidates immediately and remains empty when logout races a refresh save', async () => {
    vi.stubGlobal('navigator', { onLine: true });
    vi.stubGlobal('fetch', vi.fn(async (input: URL | RequestInfo) => {
      const url = new URL(String(input));
      if (url.pathname === '/api/v1/auth/refresh') return jsonResponse(authResponse('old-rotated'));
      if (url.pathname === '/api/v1/auth/logout') return new Response(null, { status: 204 });
      throw new Error(`Unexpected request ${url.pathname}`);
    }));
    const store = new RaceTokenStore();
    store.blockFirstSave = true;
    const api = new LunaraApi('https://lunara.example.test', store);

    const restoring = api.restoreSession();
    await store.firstSaveStarted.promise;
    const loggingOut = api.logout();
    store.releaseFirstSave.resolve();

    await expect(restoring).resolves.toBe(false);
    await loggingOut;
    expect(store.tokens).toBeNull();
  });

  it('never lets a late old refresh overwrite or clear a newer login', async () => {
    vi.stubGlobal('navigator', { onLine: true });
    const oldRefresh = deferred<Response>();
    vi.stubGlobal('fetch', vi.fn(async (input: URL | RequestInfo) => {
      const url = new URL(String(input));
      if (url.pathname === '/api/v1/auth/refresh') return oldRefresh.promise;
      if (url.pathname === '/api/v1/auth/login') return jsonResponse(authResponse('new'));
      throw new Error(`Unexpected request ${url.pathname}`);
    }));
    const store = new RaceTokenStore();
    const api = new LunaraApi('https://lunara.example.test', store);

    const restoring = api.restoreSession();
    await vi.waitFor(() => {
      expect(fetch).toHaveBeenCalled();
    });
    await expect(api.login('new', 'new-password')).resolves.toMatchObject({
      accessToken: 'new-access',
    });
    oldRefresh.resolve(jsonResponse(authResponse('old-rotated')));

    await expect(restoring).resolves.toBe(false);
    expect(store.tokens).toMatchObject({
      accessToken: 'new-access',
      refreshToken: 'new-refresh',
    });
    expect(store.saveCount).toBe(1);
  });
});
