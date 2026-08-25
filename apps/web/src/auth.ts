import { secretStore } from './db';

const KEY_NAME = 'refresh-key-v1';
const TOKEN_NAME = 'refresh-token-v1';

interface EncryptedTokens {
  version: 1;
  iv: number[];
  ciphertext: number[];
}

export interface OidcRefreshSession {
  accessToken: string;
  refreshToken: string;
  tokenEndpoint: string;
  clientId: string;
}

export interface SessionTokens {
  accessToken: string;
  refreshToken: string;
  oidc?: OidcRefreshSession;
}

export interface RefreshTokenStore {
  save(refreshToken: string, accessToken: string, oidc?: OidcRefreshSession): Promise<void>;
  load(): Promise<SessionTokens | null>;
  clear(): Promise<void>;
}

export interface HeaderPolicyInput {
  token: string | null;
  requestUrl: string;
  applicationOrigin: string;
}

export function lunaraAuthHeaders({
  token,
  requestUrl,
  applicationOrigin,
}: HeaderPolicyInput): HeadersInit {
  if (!token) return {};
  const target = new URL(requestUrl, applicationOrigin);
  if (target.origin !== applicationOrigin) return {};
  return { 'X-Lunara-Authorization': `Bearer ${token}` };
}

export function oidcGatewayHeaders({
  token,
  requestUrl,
  applicationOrigin,
}: HeaderPolicyInput): HeadersInit {
  if (!token) return {};
  const target = new URL(requestUrl, applicationOrigin);
  if (target.origin !== applicationOrigin) return {};
  return { Authorization: `Bearer ${token}` };
}

async function getOrCreateKey(): Promise<CryptoKey> {
  const existing = await secretStore.get<CryptoKey>(KEY_NAME);
  if (existing) return existing;
  const key = await crypto.subtle.generateKey(
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
  await secretStore.put(KEY_NAME, key);
  return key;
}

export class SecureRefreshTokenStore implements RefreshTokenStore {
  async save(
    refreshToken: string,
    accessToken: string,
    oidc?: OidcRefreshSession,
  ): Promise<void> {
    const key = await getOrCreateKey();
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const plaintext = new TextEncoder().encode(
      JSON.stringify({ refreshToken, accessToken, ...(oidc ? { oidc } : {}) } satisfies SessionTokens),
    );
    const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, plaintext);
    await secretStore.put(TOKEN_NAME, {
      version: 1,
      iv: Array.from(iv),
      ciphertext: Array.from(new Uint8Array(ciphertext)),
    } satisfies EncryptedTokens);
  }

  async load(): Promise<SessionTokens | null> {
    const [key, value] = await Promise.all([
      secretStore.get<CryptoKey>(KEY_NAME),
      secretStore.get<EncryptedTokens>(TOKEN_NAME),
    ]);
    if (!key || !value || value.version !== 1) return null;
    try {
      const plaintext = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: new Uint8Array(value.iv) },
        key,
        new Uint8Array(value.ciphertext),
      );
      const parsed = JSON.parse(new TextDecoder().decode(plaintext)) as Partial<SessionTokens>;
      if (!parsed.accessToken || !parsed.refreshToken) throw new Error('Invalid token envelope');
      return {
        accessToken: parsed.accessToken,
        refreshToken: parsed.refreshToken,
        ...(parsed.oidc ? { oidc: parsed.oidc } : {}),
      };
    } catch {
      await this.clear();
      return null;
    }
  }

  async clear(): Promise<void> {
    await Promise.all([secretStore.delete(KEY_NAME), secretStore.delete(TOKEN_NAME)]);
  }
}
