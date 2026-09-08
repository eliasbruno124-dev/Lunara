import {
  lunaraAuthHeaders,
  oidcGatewayHeaders,
  SecureRefreshTokenStore,
  type OidcRefreshSession,
  type RefreshTokenStore,
  type SessionTokens,
} from './auth';
import type {
  AuthResponse,
  HouseholdResponse,
  InvitationResponse,
  MetaResponse,
  OidcEnrollmentIntent,
  PartnerDashboard,
  PeriodEvent,
  PartnerTip,
  Prediction,
  SyncMutation,
  SyncResponse,
} from './types';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export class LunaraApi {
  private accessToken: string | null = null;
  private oidcAccessToken: string | null = null;
  private refreshInFlight: { generation: number; promise: Promise<boolean> } | null = null;
  private sessionGeneration = 0;
  private tokenCommitTail: Promise<void> = Promise.resolve();

  constructor(
    private readonly origin: string,
    private readonly refreshTokens: RefreshTokenStore = new SecureRefreshTokenStore(),
  ) {}

  async meta(): Promise<MetaResponse> {
    return this.request<MetaResponse>('/api/v1/meta', { auth: false });
  }

  async login(username: string, password: string): Promise<AuthResponse> {
    const boundary = this.beginSessionBoundary();
    await boundary.cleared;
    const response = await this.request<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      auth: false,
      body: { username, password, deviceName: 'Lunara Web App' },
    });
    if (!(await this.acceptSession(response, undefined, boundary.generation))) {
      throw new ApiError(409, 'Sign-in was superseded by another session change');
    }
    return response;
  }

  async oidcLogin(
    accessToken: string,
    oidcRefresh: OidcRefreshSession,
    intent: OidcEnrollmentIntent = { kind: 'login' },
  ): Promise<AuthResponse> {
    const boundary = this.beginSessionBoundary();
    await boundary.cleared;
    const response = await this.request<AuthResponse>('/api/v1/auth/oidc/login', {
      method: 'POST',
      auth: false,
      oidcAccessTokenOverride: accessToken,
      body: {
        accessToken,
        deviceName: 'Lunara Web App',
        ...this.oidcEnrollmentFields(intent),
      },
    });
    if (!(await this.acceptSession(
      response,
      { ...oidcRefresh, accessToken },
      boundary.generation,
    ))) {
      throw new ApiError(409, 'Sign-in was superseded by another session change');
    }
    return response;
  }

  private oidcEnrollmentFields(intent: OidcEnrollmentIntent): {
    createIfMissing?: true;
    invitationCode?: string;
    preferredLanguage?: 'DE' | 'EN';
  } {
    switch (intent.kind) {
      case 'login':
        return {};
      case 'new-tracker':
        return {
          createIfMissing: true,
          preferredLanguage: intent.preferredLanguage,
        };
      case 'partner':
        return {
          invitationCode: intent.invitationCode,
          preferredLanguage: intent.preferredLanguage,
        };
    }
  }

  async restoreSession(): Promise<boolean> {
    const generation = this.sessionGeneration;
    const persisted = await this.loadTokensSerialized();
    if (generation !== this.sessionGeneration) return false;
    this.accessToken = persisted?.accessToken ?? null;
    this.oidcAccessToken = persisted?.oidc?.accessToken ?? null;
    if (!navigator.onLine) return this.accessToken !== null;
    return this.refresh();
  }

  async me(): Promise<AuthResponse['user']> {
    return this.request<AuthResponse['user']>('/api/v1/me');
  }

  async acceptInvitation(invitationCode: string): Promise<{ accepted: true }> {
    return this.request<{ accepted: true }>('/api/v1/household/invitations/accept', {
      method: 'POST',
      body: { invitationCode, confirm: true },
    });
  }

  async household(): Promise<HouseholdResponse> {
    return this.request<HouseholdResponse>('/api/v1/household');
  }

  async createInvitation(): Promise<InvitationResponse> {
    return this.request<InvitationResponse>('/api/v1/household/invitations', {
      method: 'POST',
      body: { neverExpires: true },
    });
  }

  async periods(): Promise<PeriodEvent[]> {
    return this.request<PeriodEvent[]>('/api/v1/periods');
  }

  async prediction(): Promise<Prediction> {
    return this.request<Prediction>('/api/v1/predictions');
  }

  async partnerDashboard(): Promise<PartnerDashboard> {
    return this.request<PartnerDashboard>('/api/v1/partner/dashboard');
  }

  async todayTip(): Promise<PartnerTip | null> {
    const response = await this.request<{ tip: PartnerTip | null; date: string }>('/api/v1/tips/today');
    return response.tip;
  }

  async sync(cursor: string, mutation: SyncMutation): Promise<SyncResponse> {
    return this.request<SyncResponse>('/api/v1/sync', {
      method: 'POST',
      body: {
        cursor,
        mutations: [
          {
            clientMutationId: mutation.clientMutationId,
            entityType: mutation.entityType,
            operation: mutation.operation,
            payload: mutation.payload,
          },
        ],
      },
    });
  }

  async logout(): Promise<void> {
    const accessToken = this.accessToken;
    const oidcAccessToken = this.oidcAccessToken;
    const boundary = this.beginLogoutBoundary();
    const tokens = await boundary.tokens;
    try {
      await this.request<void>('/api/v1/auth/logout', {
        method: 'POST',
        body: tokens ? { refreshToken: tokens.refreshToken } : {},
        accessTokenOverride: accessToken,
        oidcAccessTokenOverride: oidcAccessToken,
      });
    } finally {
      await boundary.cleared;
    }
  }

  async forgetSession(): Promise<void> {
    await this.beginSessionBoundary().cleared;
  }

  private async acceptSession(
    response: AuthResponse,
    oidc?: OidcRefreshSession,
    generation = this.sessionGeneration,
  ): Promise<boolean> {
    return this.enqueueTokenOperation(async () => {
      if (generation !== this.sessionGeneration) return false;
      await this.refreshTokens.save(response.refreshToken, response.accessToken, oidc);
      if (generation !== this.sessionGeneration) return false;
      this.accessToken = response.accessToken;
      this.oidcAccessToken = oidc?.accessToken ?? null;
      return true;
    });
  }

  private async refresh(): Promise<boolean> {
    const generation = this.sessionGeneration;
    if (this.refreshInFlight?.generation === generation) return this.refreshInFlight.promise;
    const promise = (async () => {
      try {
        const tokens = await this.loadTokensSerialized();
        if (!tokens || generation !== this.sessionGeneration) return false;
        const refreshedOidc = tokens.oidc
          ? await this.refreshExternalOidc(tokens.oidc)
          : null;
        if (generation !== this.sessionGeneration) return false;
        const response = await this.request<AuthResponse>('/api/v1/auth/refresh', {
          method: 'POST',
          auth: false,
          retry: false,
          oidcAccessTokenOverride: refreshedOidc?.accessToken ?? this.oidcAccessToken,
          body: {
            refreshToken: tokens.refreshToken,
            ...(refreshedOidc ? { oidcAccessToken: refreshedOidc.accessToken } : {}),
          },
        });
        if (generation !== this.sessionGeneration) return false;
        return this.acceptSession(
          response,
          refreshedOidc?.session ?? tokens.oidc,
          generation,
        );
      } catch (error) {
        if (generation !== this.sessionGeneration) return false;
        if (error instanceof ApiError && error.status === 0 && this.accessToken) return true;
        await this.beginSessionBoundary().cleared;
        return false;
      }
    })();
    const entry = { generation, promise };
    this.refreshInFlight = entry;
    void promise.then(
      () => {
        if (this.refreshInFlight === entry) this.refreshInFlight = null;
      },
      () => {
        if (this.refreshInFlight === entry) this.refreshInFlight = null;
      },
    );
    return promise;
  }

  private beginSessionBoundary(): { generation: number; cleared: Promise<void> } {
    const generation = ++this.sessionGeneration;
    this.accessToken = null;
    this.oidcAccessToken = null;
    const cleared = this.enqueueTokenOperation(() => this.refreshTokens.clear());
    return { generation, cleared };
  }

  private beginLogoutBoundary(): {
    generation: number;
    tokens: Promise<SessionTokens | null>;
    cleared: Promise<void>;
  } {
    const generation = ++this.sessionGeneration;
    this.accessToken = null;
    this.oidcAccessToken = null;
    const tokens = this.enqueueTokenOperation(async () => {
      const current = await this.refreshTokens.load();
      await this.refreshTokens.clear();
      return current;
    });
    return { generation, tokens, cleared: tokens.then(() => undefined) };
  }

  private loadTokensSerialized() {
    return this.enqueueTokenOperation(() => this.refreshTokens.load());
  }

  private enqueueTokenOperation<T>(operation: () => Promise<T>): Promise<T> {
    const result = this.tokenCommitTail.then(operation, operation);
    this.tokenCommitTail = result.then(() => undefined, () => undefined);
    return result;
  }

  private async refreshExternalOidc(
    session: OidcRefreshSession,
  ): Promise<{ accessToken: string; session: OidcRefreshSession }> {
    const endpoint = new URL(session.tokenEndpoint);
    if (endpoint.protocol !== 'https:' || endpoint.username || endpoint.password) {
      throw new Error('Invalid stored OIDC token endpoint');
    }
    let response: Response;
    try {
      response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
          grant_type: 'refresh_token',
          refresh_token: session.refreshToken,
          client_id: session.clientId,
        }),
        credentials: 'omit',
        cache: 'no-store',
        redirect: 'error',
      });
    } catch (error) {
      throw new ApiError(0, error instanceof Error ? error.message : 'OIDC refresh failed');
    }
    if (!response.ok) throw new ApiError(response.status, 'OIDC refresh was rejected');
    const body = (await response.json()) as {
      access_token?: string;
      refresh_token?: string;
    };
    if (!body.access_token) throw new ApiError(502, 'OIDC refresh returned no access token');
    return {
      accessToken: body.access_token,
      session: {
        ...session,
        accessToken: body.access_token,
        refreshToken: body.refresh_token ?? session.refreshToken,
      },
    };
  }

  private async request<T>(
    path: string,
    options: {
      method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
      body?: unknown;
      auth?: boolean;
      retry?: boolean;
      accessTokenOverride?: string | null;
      oidcAccessTokenOverride?: string | null;
    } = {},
  ): Promise<T> {
    const url = new URL(path, this.origin);
    if (url.origin !== this.origin) throw new Error('Cross-origin API request refused');
    const headers = new Headers({ Accept: 'application/json' });
    if (options.body !== undefined) headers.set('Content-Type', 'application/json');
    const authHeaders = lunaraAuthHeaders({
      token: options.auth === false
        ? null
        : options.accessTokenOverride === undefined
          ? this.accessToken
          : options.accessTokenOverride,
      requestUrl: url.href,
      applicationOrigin: this.origin,
    });
    new Headers(authHeaders).forEach((value, key) => headers.set(key, value));
    const gatewayHeaders = oidcGatewayHeaders({
      token: options.oidcAccessTokenOverride === undefined
        ? this.oidcAccessToken
        : options.oidcAccessTokenOverride,
      requestUrl: url.href,
      applicationOrigin: this.origin,
    });
    new Headers(gatewayHeaders).forEach((value, key) => headers.set(key, value));

    let response: Response;
    try {
      response = await fetch(url, {
        method: options.method ?? 'GET',
        headers,
        credentials: 'same-origin',
        cache: 'no-store',
        redirect: 'error',
        ...(options.body === undefined ? {} : { body: JSON.stringify(options.body) }),
      });
    } catch (error) {
      throw new ApiError(0, error instanceof Error ? error.message : 'Network request failed');
    }
    if (response.status === 401 && options.auth !== false && options.retry !== false) {
      if (await this.refresh()) return this.request<T>(path, { ...options, retry: false });
    }
    if (!response.ok) {
      const body = (await response.json().catch(() => null)) as { message?: string } | null;
      throw new ApiError(response.status, body?.message ?? `HTTP ${response.status}`);
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }
}
