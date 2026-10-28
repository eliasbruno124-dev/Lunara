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
  it('never refreshes or retries an old import 401 with a newer account', async () => {
    vi.stubGlobal('navigator', { onLine: false });
    const oldImport = deferred<Response>();
    const request = vi.fn(async (input: URL | RequestInfo, _init?: RequestInit) => {
      const path = new URL(String(input)).pathname;
      if (path === '/api/v1/auth/login') return jsonResponse(authResponse('new'));
      if (path === '/api/v1/import') return oldImport.promise;
      if (path === '/api/v1/auth/refresh') return jsonResponse(authResponse('new-rotated'));
      throw new Error(`Unexpected request ${path}`);
    });
    vi.stubGlobal('fetch', request);
    const store = new RaceTokenStore();
    const api = new LunaraApi('https://lunara.example.test', store);
    await api.restoreSession();
    const importing = api.importPeriods([{ startDate: '2026-09-01', endDate: null }], false);
    const rejected = expect(importing).rejects.toMatchObject({ status: 409 });
    await api.login('new', 'synthetic-password');
    oldImport.resolve(jsonResponse({ message: 'Expired' }, 401));
    await rejected;
    expect(new Headers(request.mock.calls[0]![1]?.headers).get('X-Lunara-Authorization')).toBe('Bearer old-access');
    expect(request.mock.calls.map(([input]) => new URL(String(input)).pathname)).toEqual(['/api/v1/import', '/api/v1/auth/login']);
    expect(store.tokens?.accessToken).toBe('new-access');
  });

  it.each([200, 204])('rejects a late authenticated HTTP %i response after the account changes', async (status) => {
    const response = deferred<Response>();
    vi.stubGlobal('fetch', vi.fn(() => response.promise));
    const api = new LunaraApi('https://lunara.example.test', new RaceTokenStore());
    const loading = api.periods();
    const rejected = expect(loading).rejects.toMatchObject({ status: 409 });
    await api.forgetSession();
    response.resolve(status === 204 ? new Response(null, { status }) : jsonResponse([{ id: 'old-private-row' }]));
    await rejected;
  });

  it.each([200, 403])('rechecks identity after an HTTP %i response body finishes reading', async (status) => {
    const body = deferred<unknown>();
    const reading = deferred<void>();
    const response = jsonResponse({}, status);
    vi.spyOn(response, 'json').mockImplementation(() => { reading.resolve(); return body.promise; });
    vi.stubGlobal('fetch', vi.fn(async () => response));
    const api = new LunaraApi('https://lunara.example.test', new RaceTokenStore());
    const loading = api.periods();
    const rejected = expect(loading).rejects.toMatchObject({ status: 409 });
    await reading.promise;
    await api.forgetSession();
    body.resolve(status === 200 ? [{ id: 'old-private-row' }] : { message: 'old private error' });
    await rejected;
  });

  it('does not retry an import when a new login supersedes its pending refresh body', async () => {
    const refreshBody = deferred<unknown>();
    const reading = deferred<void>();
    const refreshResponse = jsonResponse({});
    vi.spyOn(refreshResponse, 'json').mockImplementation(() => { reading.resolve(); return refreshBody.promise; });
    const request = vi.fn(async (input: URL | RequestInfo) => {
      const path = new URL(String(input)).pathname;
      if (path === '/api/v1/import') return jsonResponse({}, 401);
      if (path === '/api/v1/auth/refresh') return refreshResponse;
      if (path === '/api/v1/auth/login') return jsonResponse(authResponse('new'));
      throw new Error(`Unexpected request ${path}`);
    });
    vi.stubGlobal('fetch', request);
    const store = new RaceTokenStore();
    const api = new LunaraApi('https://lunara.example.test', store);
    const importing = api.importPeriods([{ startDate: '2026-09-01', endDate: null }], false);
    const rejected = expect(importing).rejects.toMatchObject({ status: 409 });
    await reading.promise;
    await api.login('new', 'synthetic-password');
    refreshBody.resolve(authResponse('old-rotated'));
    await rejected;
    expect(request.mock.calls.filter(([input]) => new URL(String(input)).pathname === '/api/v1/import')).toHaveLength(1);
    expect(store.tokens?.accessToken).toBe('new-access');
  });

  it('still refreshes and retries once within the same account generation', async () => {
    let imports = 0;
    const request = vi.fn(async (input: URL | RequestInfo, init?: RequestInit) => {
      const path = new URL(String(input)).pathname;
      if (path === '/api/v1/auth/refresh') return jsonResponse(authResponse('rotated'));
      if (path === '/api/v1/import') {
        if (++imports === 1) return jsonResponse({}, 401);
        expect(new Headers(init?.headers).get('X-Lunara-Authorization')).toBe('Bearer rotated-access');
        return jsonResponse({ periods: { create: 1, duplicates: 0, conflicts: 0 } });
      }
      throw new Error(`Unexpected request ${path}`);
    });
    vi.stubGlobal('fetch', request);
    const api = new LunaraApi('https://lunara.example.test', new RaceTokenStore());
    await expect(api.importPeriods([{ startDate: '2026-09-01', endDate: null }], false)).resolves.toMatchObject({ periods: { create: 1 } });
    expect(imports).toBe(2);
  });

  it('keeps unauthenticated metadata usable across a login boundary', async () => {
    const meta = deferred<Response>();
    vi.stubGlobal('fetch', vi.fn(async (input: URL | RequestInfo) => new URL(String(input)).pathname === '/api/v1/meta'
      ? meta.promise : jsonResponse(authResponse('new'))));
    const api = new LunaraApi('https://lunara.example.test', new RaceTokenStore());
    const loading = api.meta();
    await api.login('new', 'synthetic-password');
    meta.resolve(jsonResponse({ name: 'Lunara', version: 'synthetic' }));
    await expect(loading).resolves.toMatchObject({ name: 'Lunara' });
  });

  it('does not classify a stale network failure as an offline session', async () => {
    const response = deferred<Response>();
    vi.stubGlobal('fetch', vi.fn(() => response.promise));
    const api = new LunaraApi('https://lunara.example.test', new RaceTokenStore());
    const loading = api.me();
    const rejected = expect(loading).rejects.toMatchObject({ status: 409 });
    await api.forgetSession();
    response.reject(new TypeError('Synthetic connection failure'));
    await rejected;
  });

  it.each([200, 403])('rejects a stale HTTP %i body decoding failure at the identity boundary', async (status) => {
    const body = deferred<unknown>();
    const reading = deferred<void>();
    const response = jsonResponse({}, status);
    vi.spyOn(response, 'json').mockImplementation(() => { reading.resolve(); return body.promise; });
    vi.stubGlobal('fetch', vi.fn(async () => response));
    const api = new LunaraApi('https://lunara.example.test', new RaceTokenStore());
    const loading = api.me();
    const rejected = expect(loading).rejects.toMatchObject({ status: 409 });
    await reading.promise;
    await api.forgetSession();
    body.reject(new SyntaxError('Synthetic invalid body'));
    await rejected;
  });

  it('retains a current-session 401 when refresh itself signs out invalid credentials', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({}, 401)));
    const store = new RaceTokenStore();
    const api = new LunaraApi('https://lunara.example.test', store);
    await expect(api.me()).rejects.toMatchObject({ status: 401 });
    expect(store.tokens).toBeNull();
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('does not expose an old refresh sign-out as the newer account\'s 401', async () => {
    const clearing = deferred<void>();
    const releaseClear = deferred<void>();
    class BlockingClearStore extends RaceTokenStore {
      override async clear() {
        clearing.resolve();
        await releaseClear.promise;
        await super.clear();
      }
    }
    vi.stubGlobal('fetch', vi.fn(async (input: URL | RequestInfo) => new URL(String(input)).pathname === '/api/v1/auth/login'
      ? jsonResponse(authResponse('new')) : jsonResponse({}, 401)));
    const store = new BlockingClearStore();
    const api = new LunaraApi('https://lunara.example.test', store);
    const loading = api.me();
    const rejected = expect(loading).rejects.toMatchObject({ status: 409 });
    await clearing.promise;
    const login = api.login('new', 'synthetic-password');
    releaseClear.resolve();
    await login;
    await rejected;
    expect(store.tokens?.accessToken).toBe('new-access');
  });

  it('logout uses captured credentials and never refreshes a newer account', async () => {
    vi.stubGlobal('navigator', { onLine: false });
    const oldLogout = deferred<Response>();
    const request = vi.fn(async (input: URL | RequestInfo, init?: RequestInit) => {
      const path = new URL(String(input)).pathname;
      if (path === '/api/v1/auth/login') return jsonResponse(authResponse('new'));
      if (path === '/api/v1/auth/logout') {
        expect(new Headers(init?.headers).get('X-Lunara-Authorization')).toBe('Bearer old-access');
        expect(JSON.parse(String(init?.body))).toEqual({ refreshToken: 'old-refresh' });
        return oldLogout.promise;
      }
      throw new Error(`Unexpected request ${path}`);
    });
    vi.stubGlobal('fetch', request);
    const store = new RaceTokenStore();
    const api = new LunaraApi('https://lunara.example.test', store);
    await api.restoreSession();
    const loggingOut = api.logout();
    const rejected = expect(loggingOut).rejects.toMatchObject({ status: 401 });
    await api.login('new', 'synthetic-password');
    oldLogout.resolve(jsonResponse({}, 401));
    await rejected;
    expect(store.tokens?.accessToken).toBe('new-access');
    expect(request.mock.calls.map(([input]) => new URL(String(input)).pathname).sort()).toEqual(['/api/v1/auth/login', '/api/v1/auth/logout']);
  });

  it('rejects a superseded login after its unauthenticated response body arrives', async () => {
    const oldBody = deferred<unknown>();
    const reading = deferred<void>();
    const response = jsonResponse({});
    vi.spyOn(response, 'json').mockImplementation(() => { reading.resolve(); return oldBody.promise; });
    let calls = 0;
    vi.stubGlobal('fetch', vi.fn(async () => ++calls === 1 ? response : jsonResponse(authResponse('new'))));
    const store = new RaceTokenStore();
    const api = new LunaraApi('https://lunara.example.test', store);
    const firstLogin = api.login('old', 'synthetic-password');
    const rejected = expect(firstLogin).rejects.toMatchObject({ status: 409 });
    await reading.promise;
    await api.login('new', 'synthetic-password');
    oldBody.resolve(authResponse('old'));
    await rejected;
    expect(store.tokens?.accessToken).toBe('new-access');
  });

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
