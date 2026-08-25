import {
  validatePortablePlaintext,
  type PortableBackupExtras,
  type PortablePlaintext,
} from './backup';
import type { PeriodEvent } from './types';

const FORMAT = 'lunara-local-vault';
const VERSION = 1;
const ITERATIONS = 600_000;
const MAX_VAULT_BYTES = 16 * 1024 * 1024;
const AAD = new TextEncoder().encode(`${FORMAT}:v${VERSION}`);

export interface LocalVaultEnvelope {
  format: typeof FORMAT;
  version: typeof VERSION;
  kdf: {
    name: 'PBKDF2-HMAC-SHA256';
    iterations: typeof ITERATIONS;
    salt: string;
  };
  cipher: {
    name: 'AES-256-GCM';
    iv: string;
  };
  ciphertext: string;
}

export interface LocalVaultState {
  periods: PeriodEvent[];
  extras: PortableBackupExtras;
}

function ownedBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
  }
  return btoa(binary);
}

function base64ToBytes(value: string): Uint8Array {
  if (!/^[A-Za-z0-9+/]*={0,2}$/u.test(value) || value.length % 4 !== 0) {
    throw new Error('Invalid local vault base64');
  }
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

export function isValidVaultPassphrase(passphrase: string): boolean {
  const length = [...passphrase].length;
  return length >= 12 && length <= 128;
}

function assertPassphrase(passphrase: string): void {
  if (!isValidVaultPassphrase(passphrase)) {
    throw new Error('Vault passphrase must contain 12–128 Unicode code points');
  }
}

async function deriveKey(passphrase: string, salt: Uint8Array): Promise<CryptoKey> {
  const passphraseBytes = new TextEncoder().encode(passphrase);
  try {
    const material = await crypto.subtle.importKey(
      'raw',
      ownedBuffer(passphraseBytes),
      'PBKDF2',
      false,
      ['deriveKey'],
    );
    return await crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        hash: 'SHA-256',
        salt: ownedBuffer(salt),
        iterations: ITERATIONS,
      },
      material,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt'],
    );
  } finally {
    passphraseBytes.fill(0);
  }
}

function normalizedState(value: LocalVaultState): LocalVaultState {
  const normalized = validatePortablePlaintext({
    schemaVersion: 1,
    product: 'Lunara',
    exportedAt: new Date().toISOString(),
    profile: value.extras.profile,
    settings: value.extras.settings,
    periods: value.periods,
    dailyLogs: value.extras.dailyLogs,
  } satisfies PortablePlaintext);
  return {
    periods: normalized.periods,
    extras: {
      profile: normalized.profile,
      settings: normalized.settings,
      dailyLogs: normalized.dailyLogs,
    },
  };
}

function validatedEnvelope(value: unknown): {
  envelope: LocalVaultEnvelope;
  salt: Uint8Array;
  iv: Uint8Array;
  ciphertext: Uint8Array;
} {
  if (!value || typeof value !== 'object') throw new Error('Invalid local vault');
  const input = value as Partial<LocalVaultEnvelope>;
  if (
    input.format !== FORMAT ||
    input.version !== VERSION ||
    input.kdf?.name !== 'PBKDF2-HMAC-SHA256' ||
    input.kdf.iterations !== ITERATIONS ||
    input.cipher?.name !== 'AES-256-GCM' ||
    typeof input.kdf.salt !== 'string' ||
    typeof input.cipher.iv !== 'string' ||
    typeof input.ciphertext !== 'string'
  ) {
    throw new Error('Unsupported local vault');
  }
  if (new TextEncoder().encode(JSON.stringify(input)).byteLength > MAX_VAULT_BYTES) {
    throw new Error('Local vault is too large');
  }
  const salt = base64ToBytes(input.kdf.salt);
  const iv = base64ToBytes(input.cipher.iv);
  const ciphertext = base64ToBytes(input.ciphertext);
  if (salt.length !== 16 || iv.length !== 12 || ciphertext.length < 16) {
    throw new Error('Invalid local vault parameters');
  }
  return { envelope: input as LocalVaultEnvelope, salt, iv, ciphertext };
}

/**
 * Holds the non-extractable vault key only while the page is unlocked. Neither
 * this key nor the passphrase is ever written to IndexedDB or localStorage.
 */
export class LocalVaultSession {
  private key: CryptoKey | null = null;
  private salt: Uint8Array | null = null;

  get unlocked(): boolean {
    return this.key !== null && this.salt !== null;
  }

  async create(passphrase: string, state: LocalVaultState): Promise<LocalVaultEnvelope> {
    assertPassphrase(passphrase);
    const normalized = normalizedState(state);
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const key = await deriveKey(passphrase, salt);
    const envelope = await this.encryptWith(key, salt, normalized);
    this.key = key;
    this.salt = new Uint8Array(salt);
    return envelope;
  }

  async unlock(passphrase: string, value: unknown): Promise<LocalVaultState> {
    assertPassphrase(passphrase);
    const { salt, iv, ciphertext } = validatedEnvelope(value);
    const key = await deriveKey(passphrase, salt);
    const plaintextBuffer = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: ownedBuffer(iv), additionalData: ownedBuffer(AAD) },
      key,
      ownedBuffer(ciphertext),
    );
    const plaintextBytes = new Uint8Array(plaintextBuffer);
    try {
      const decoded = new TextDecoder('utf-8', { fatal: true }).decode(plaintextBytes);
      const parsed = JSON.parse(decoded) as PortablePlaintext;
      const normalized = validatePortablePlaintext(parsed);
      const state: LocalVaultState = {
        periods: normalized.periods,
        extras: {
          profile: normalized.profile,
          settings: normalized.settings,
          dailyLogs: normalized.dailyLogs,
        },
      };
      this.key = key;
      this.salt = new Uint8Array(salt);
      return state;
    } finally {
      plaintextBytes.fill(0);
    }
  }

  async seal(state: LocalVaultState): Promise<LocalVaultEnvelope> {
    if (!this.key || !this.salt) throw new Error('Local vault is locked');
    // Copy the salt before the first await. A pagehide/visibility lock clears
    // the session salt immediately; an in-flight authenticated write must
    // either remain decryptable or be discarded by the caller's vault epoch.
    const key = this.key;
    const salt = new Uint8Array(this.salt);
    try {
      return await this.encryptWith(key, salt, normalizedState(state));
    } finally {
      salt.fill(0);
    }
  }

  lock(): void {
    this.key = null;
    this.salt?.fill(0);
    this.salt = null;
  }

  private async encryptWith(
    key: CryptoKey,
    salt: Uint8Array,
    state: LocalVaultState,
  ): Promise<LocalVaultEnvelope> {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const plaintext: PortablePlaintext = {
      schemaVersion: 1,
      product: 'Lunara',
      exportedAt: new Date().toISOString(),
      profile: state.extras.profile,
      settings: state.extras.settings,
      periods: state.periods,
      dailyLogs: state.extras.dailyLogs,
    };
    const plaintextBytes = new TextEncoder().encode(JSON.stringify(plaintext));
    try {
      if (plaintextBytes.byteLength > MAX_VAULT_BYTES - 1024) {
        throw new Error('Local vault plaintext is too large');
      }
      const ciphertext = await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv: ownedBuffer(iv), additionalData: ownedBuffer(AAD) },
        key,
        ownedBuffer(plaintextBytes),
      );
      return {
        format: FORMAT,
        version: VERSION,
        kdf: {
          name: 'PBKDF2-HMAC-SHA256',
          iterations: ITERATIONS,
          salt: bytesToBase64(salt),
        },
        cipher: { name: 'AES-256-GCM', iv: bytesToBase64(iv) },
        ciphertext: bytesToBase64(new Uint8Array(ciphertext)),
      };
    } finally {
      plaintextBytes.fill(0);
      iv.fill(0);
    }
  }
}
