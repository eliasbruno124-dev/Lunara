import { parseIsoDate, todayIso } from './date';
import type { PeriodEvent } from './types';
import { periodRangesOverlap } from './period-end';

const FORMAT = 'lunara-portable-backup';
const VERSION = 1;
const ITERATIONS = 310_000;
const AAD = new TextEncoder().encode('lunara-portable-backup:v1');
const MAX_IMPORT_BYTES = 16 * 1024 * 1024;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;

function ownedBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

export interface PortableBackupEnvelope {
  format: 'lunara-portable-backup';
  version: 1;
  kdf: {
    name: 'PBKDF2-HMAC-SHA256';
    iterations: 310000;
    salt: string;
  };
  cipher: {
    name: 'AES-256-GCM';
    iv: string;
  };
  ciphertext: string;
}

export interface PortableProfile {
  fertilityPredictionEnabled: boolean;
  typicalCycleLength: number | null;
  typicalPeriodLength: number | null;
  lutealLength: number | null;
  predictionPaused: boolean;
  specialMode: string;
}

export interface PortableSettings {
  displayName: string;
  privacyMode: boolean;
  useSystemTheme: boolean;
  darkMode: boolean;
  notifications: PortableNotificationPreferences;
}

export interface PortableNotificationPreferences {
  master: boolean;
  periodTomorrow: boolean;
  fertileTomorrow: boolean;
  dailyLogReminder: boolean;
  partnerTip: boolean;
  reminderHour: number;
  reminderMinute: number;
  quietStartHour: number;
  quietStartMinute: number;
  quietEndHour: number;
  quietEndMinute: number;
  hideSensitiveContent: boolean;
  maxPerDay: number;
}

export interface PortableDailyLog {
  id: string;
  localDate: string;
  flowLevel: string | null;
  painLevel: number | null;
  mood: string | null;
  energy: number | null;
  sleepQuality: number | null;
  bodyTemperature: number | null;
  symptomsJson: string;
  encryptedNote: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface PortableBackupExtras {
  profile: PortableProfile;
  settings: PortableSettings;
  dailyLogs: PortableDailyLog[];
}

export interface PortablePlaintext extends PortableBackupExtras {
  schemaVersion: 1;
  product: 'Lunara';
  exportedAt: string;
  periods: PeriodEvent[];
}

export function defaultPortableExtras(): PortableBackupExtras {
  return {
    profile: {
      fertilityPredictionEnabled: false,
      typicalCycleLength: null,
      typicalPeriodLength: null,
      lutealLength: null,
      predictionPaused: false,
      specialMode: 'NONE',
    },
    settings: {
      displayName: 'Lunara',
      privacyMode: true,
      useSystemTheme: true,
      darkMode: false,
      notifications: {
        master: false,
        periodTomorrow: true,
        fertileTomorrow: false,
        dailyLogReminder: false,
        partnerTip: false,
        reminderHour: 9,
        reminderMinute: 0,
        quietStartHour: 22,
        quietStartMinute: 0,
        quietEndHour: 8,
        quietEndMinute: 0,
        hideSensitiveContent: true,
        maxPerDay: 2,
      },
    },
    dailyLogs: [],
  };
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
    throw new Error('Invalid base64');
  }
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function validatePassphrase(passphrase: string): void {
  const length = [...passphrase].length;
  if (length < 12 || length > 128) throw new Error('Passphrase must contain 12–128 code points');
}

async function deriveKey(passphrase: string, salt: Uint8Array): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(passphrase),
    'PBKDF2',
    false,
    ['deriveKey'],
  );
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', hash: 'SHA-256', salt: ownedBuffer(salt), iterations: ITERATIONS },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
}

function sanitizedPeriod(value: unknown): PeriodEvent {
  if (!value || typeof value !== 'object') throw new Error('Invalid period');
  const input = value as Record<string, unknown>;
  if (typeof input.id !== 'string' || !UUID.test(input.id)) throw new Error('Invalid period id');
  if (typeof input.startDate !== 'string') throw new Error('Invalid period start');
  parseIsoDate(input.startDate);
  const endDate = input.endDate === undefined || input.endDate === null ? null : input.endDate;
  if (endDate !== null && typeof endDate !== 'string') throw new Error('Invalid period end');
  if (typeof endDate === 'string') {
    parseIsoDate(endDate);
    if (endDate < input.startDate) throw new Error('Period ends before it starts');
  }
  const result: PeriodEvent = {
    id: input.id.toLowerCase(),
    startDate: input.startDate,
    endDate,
    source: typeof input.source === 'string' ? input.source.slice(0, 32) : 'MANUAL',
    createdAt: portableInstantOrFallback(input.createdAt, `${input.startDate}T00:00:00.000Z`),
    updatedAt: portableInstantOrFallback(input.updatedAt, `${input.startDate}T00:00:00.000Z`),
    version:
      typeof input.version === 'number' && Number.isSafeInteger(input.version) && input.version >= 0
        ? input.version
        : 1,
  };
  return result;
}

function portableInstantOrFallback(value: unknown, fallback: string): string {
  try {
    return validateInstant(value);
  } catch {
    return fallback;
  }
}

function nullableInteger(value: unknown, minimum: number, maximum: number): number | null {
  // Gson omits null-valued properties by default. Missing and explicit null
  // therefore have identical meaning in the Android portable format.
  if (value === null || value === undefined) return null;
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum || value > maximum) {
    throw new Error('Invalid profile number');
  }
  return value;
}

function validateProfile(value: unknown): PortableProfile {
  if (!value || typeof value !== 'object') throw new Error('Invalid profile');
  const input = value as Record<string, unknown>;
  if (
    typeof input.fertilityPredictionEnabled !== 'boolean' ||
    typeof input.predictionPaused !== 'boolean' ||
    typeof input.specialMode !== 'string' ||
    !new Set([
      'NONE',
      'HORMONAL_CONTRACEPTION',
      'PREGNANCY',
      'POSTPARTUM',
      'MENOPAUSE_TRANSITION',
      'PAUSED',
    ]).has(input.specialMode)
  ) throw new Error('Invalid profile');
  return {
    fertilityPredictionEnabled: input.fertilityPredictionEnabled,
    typicalCycleLength: nullableInteger(input.typicalCycleLength, 15, 90),
    typicalPeriodLength: nullableInteger(input.typicalPeriodLength, 1, 14),
    lutealLength: nullableInteger(input.lutealLength, 8, 20),
    predictionPaused: input.predictionPaused,
    specialMode: input.specialMode,
  };
}

function validateSettings(value: unknown): PortableSettings {
  if (!value || typeof value !== 'object') throw new Error('Invalid settings');
  const input = value as Record<string, unknown>;
  if (
    typeof input.displayName !== 'string' ||
    [...input.displayName.trim()].length < 1 ||
    [...input.displayName.trim()].length > 120 ||
    typeof input.privacyMode !== 'boolean' ||
    typeof input.useSystemTheme !== 'boolean' ||
    typeof input.darkMode !== 'boolean'
  ) throw new Error('Invalid settings');
  return {
    displayName: input.displayName,
    privacyMode: input.privacyMode,
    useSystemTheme: input.useSystemTheme,
    darkMode: input.darkMode,
    notifications: validateNotifications(input.notifications),
  };
}

function validateNotifications(value: unknown): PortableNotificationPreferences {
  if (!value || typeof value !== 'object') throw new Error('Invalid notifications');
  const input = value as Record<string, unknown>;
  for (const key of [
    'master',
    'periodTomorrow',
    'fertileTomorrow',
    'dailyLogReminder',
    'partnerTip',
    'hideSensitiveContent',
  ]) {
    if (typeof input[key] !== 'boolean') throw new Error('Invalid notification flag');
  }
  const integer = (key: string, minimum: number, maximum: number): number => {
    const number = input[key];
    if (typeof number !== 'number' || !Number.isSafeInteger(number) || number < minimum || number > maximum) {
      throw new Error('Invalid notification time');
    }
    return number;
  };
  return {
    master: input.master as boolean,
    periodTomorrow: input.periodTomorrow as boolean,
    fertileTomorrow: input.fertileTomorrow as boolean,
    dailyLogReminder: input.dailyLogReminder as boolean,
    partnerTip: input.partnerTip as boolean,
    reminderHour: integer('reminderHour', 0, 23),
    reminderMinute: integer('reminderMinute', 0, 59),
    quietStartHour: integer('quietStartHour', 0, 23),
    quietStartMinute: integer('quietStartMinute', 0, 59),
    quietEndHour: integer('quietEndHour', 0, 23),
    quietEndMinute: integer('quietEndMinute', 0, 59),
    hideSensitiveContent: input.hideSensitiveContent as boolean,
    maxPerDay: integer('maxPerDay', 1, 3),
  };
}

function nullableString(value: unknown, maximum: number): string | null {
  if (value === null || value === undefined) return null;
  if (typeof value !== 'string' || value.length > maximum) throw new Error('Invalid daily log string');
  return value;
}

function nullableFiniteNumber(value: unknown, minimum: number, maximum: number): number | null {
  if (value === null || value === undefined) return null;
  if (typeof value !== 'number' || !Number.isFinite(value) || value < minimum || value > maximum) {
    throw new Error('Invalid daily log number');
  }
  return value;
}

function validateInstant(value: unknown): string {
  if (
    typeof value !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/u.test(value) ||
    Number.isNaN(Date.parse(value))
  ) throw new Error('Invalid instant');
  return value;
}

function validateDailyLog(value: unknown): PortableDailyLog {
  if (!value || typeof value !== 'object') throw new Error('Invalid daily log');
  const input = value as Record<string, unknown>;
  if (typeof input.id !== 'string' || !UUID.test(input.id)) throw new Error('Invalid daily log id');
  if (typeof input.localDate !== 'string') throw new Error('Invalid daily log date');
  parseIsoDate(input.localDate);
  if (input.localDate > todayIso()) throw new Error('Future daily logs are not portable');
  const painLevel = nullableInteger(input.painLevel, 0, 10);
  const bodyTemperature = nullableFiniteNumber(input.bodyTemperature, 25, 45);
  if (typeof input.symptomsJson !== 'string' || input.symptomsJson.length > 20_000) {
    throw new Error('Invalid symptoms');
  }
  let symptoms: unknown;
  try {
    symptoms = JSON.parse(input.symptomsJson);
  } catch {
    throw new Error('Invalid symptoms');
  }
  if (
    !Array.isArray(symptoms) ||
    symptoms.length > 30 ||
    symptoms.some((symptom) => typeof symptom !== 'string' || symptom.length > 80)
  ) throw new Error('Invalid symptoms');
  if (typeof input.version !== 'number' || !Number.isSafeInteger(input.version) || input.version < 0) {
    throw new Error('Invalid daily log version');
  }
  return {
    id: input.id.toLowerCase(),
    localDate: input.localDate,
    flowLevel: nullableString(input.flowLevel, 64),
    painLevel,
    mood: nullableString(input.mood, 64),
    energy: nullableInteger(input.energy, 0, 10),
    sleepQuality: nullableInteger(input.sleepQuality, 0, 10),
    bodyTemperature,
    symptomsJson: input.symptomsJson,
    encryptedNote: nullableString(input.encryptedNote, 20_000),
    createdAt: validateInstant(input.createdAt),
    updatedAt: validateInstant(input.updatedAt),
    version: input.version,
  };
}

export function validatePortablePlaintext(value: unknown): PortablePlaintext {
  if (!value || typeof value !== 'object') throw new Error('Invalid backup data');
  const input = value as Partial<PortablePlaintext>;
  if (
    input.schemaVersion !== 1 ||
    input.product !== 'Lunara' ||
    !Array.isArray(input.periods) ||
    !Array.isArray(input.dailyLogs)
  ) {
    throw new Error('Unsupported backup data');
  }
  if (input.periods.length > 5_000 || input.dailyLogs.length > 50_000) {
    throw new Error('Too many records');
  }
  const deduplicated = new Map<string, PeriodEvent>();
  for (const value of input.periods) {
    const period = sanitizedPeriod(value);
    const existing = deduplicated.get(period.id);
    if (!existing || compareFreshness(existing, period) < 0) deduplicated.set(period.id, period);
  }
  const periods = [...deduplicated.values()].sort((left, right) =>
    left.startDate.localeCompare(right.startDate),
  );
  for (const [index, period] of periods.entries()) {
    if (
      period.startDate > todayIso() ||
      (period.endDate !== null && period.endDate > todayIso())
    ) throw new Error('Future periods are not portable');
    const previous = periods[index - 1];
    if (previous && periodRangesOverlap(previous, period)) {
      throw new Error('Overlapping periods are not portable');
    }
  }
  const dailyLogs = input.dailyLogs.map(validateDailyLog);
  if (
    new Set(dailyLogs.map((log) => log.id)).size !== dailyLogs.length ||
    new Set(dailyLogs.map((log) => log.localDate)).size !== dailyLogs.length
  ) throw new Error('Duplicate daily logs');
  return {
    schemaVersion: 1,
    product: 'Lunara',
    exportedAt: validateInstant(input.exportedAt),
    profile: validateProfile(input.profile),
    settings: validateSettings(input.settings),
    periods,
    dailyLogs,
  };
}

function compareFreshness(left: PeriodEvent, right: PeriodEvent): number {
  const versionDifference = (left.version ?? 0) - (right.version ?? 0);
  if (versionDifference !== 0) return versionDifference;
  return (left.updatedAt ?? '').localeCompare(right.updatedAt ?? '');
}

export function mergePeriods(existing: PeriodEvent[], imported: PeriodEvent[]): PeriodEvent[] {
  const merged = new Map(existing.map((period) => [period.id, period]));
  for (const period of imported) {
    const current = merged.get(period.id);
    if (!current || compareFreshness(current, period) < 0) merged.set(period.id, period);
  }
  const result = [...merged.values()].sort((left, right) => right.startDate.localeCompare(left.startDate));
  const chronological = result.filter((period) => !period.deletedAt).sort((left, right) => left.startDate.localeCompare(right.startDate));
  for (const [index, period] of chronological.entries()) {
    const previous = chronological[index - 1];
    if (previous && periodRangesOverlap(previous, period)) {
      throw new Error('Merged period histories overlap');
    }
  }
  return result;
}

/**
 * Daily logs are single records per local calendar day. Updates with the same
 * UUID are resolved by version/timestamp; two different UUIDs for one day are
 * rejected so an import can never silently discard a note or symptom log.
 */
export function mergeDailyLogs(
  existing: PortableDailyLog[],
  imported: PortableDailyLog[],
): PortableDailyLog[] {
  const merged = new Map<string, PortableDailyLog>();
  for (const value of [...existing, ...imported]) {
    const log = validateDailyLog(value);
    const current = merged.get(log.id);
    if (
      !current ||
      log.version > current.version ||
      (log.version === current.version && log.updatedAt > current.updatedAt)
    ) {
      merged.set(log.id, log);
    }
  }

  const dates = new Map<string, string>();
  for (const log of merged.values()) {
    const existingId = dates.get(log.localDate);
    if (existingId && existingId !== log.id) {
      throw new Error('Daily log histories conflict on the same local date');
    }
    dates.set(log.localDate, log.id);
  }
  return [...merged.values()].sort((left, right) => right.localDate.localeCompare(left.localDate));
}

export async function encryptPortableBackup(
  periods: PeriodEvent[],
  passphrase: string,
  extras: PortableBackupExtras = defaultPortableExtras(),
): Promise<PortableBackupEnvelope> {
  validatePassphrase(passphrase);
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await deriveKey(passphrase, salt);
  const plaintext: PortablePlaintext = {
    schemaVersion: 1,
    product: 'Lunara',
    exportedAt: new Date().toISOString(),
    profile: validateProfile(extras.profile),
    settings: validateSettings(extras.settings),
    periods: periods.map(sanitizedPeriod),
    dailyLogs: extras.dailyLogs.map(validateDailyLog),
  };
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, additionalData: AAD },
    key,
    new TextEncoder().encode(JSON.stringify(plaintext)),
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
}

export async function decryptPortableBackup(
  serialized: string,
  passphrase: string,
): Promise<PortablePlaintext> {
  const plaintext = JSON.parse(await decryptPortablePayload(serialized, passphrase));
  return validatePortablePlaintext(plaintext);
}

export async function decryptPortablePayload(
  serialized: string,
  passphrase: string,
): Promise<string> {
  validatePassphrase(passphrase);
  if (new TextEncoder().encode(serialized).byteLength > MAX_IMPORT_BYTES) {
    throw new Error('Backup exceeds 16 MiB');
  }
  const envelope = JSON.parse(serialized) as Partial<PortableBackupEnvelope>;
  if (
    envelope.format !== FORMAT ||
    envelope.version !== VERSION ||
    envelope.kdf?.name !== 'PBKDF2-HMAC-SHA256' ||
    envelope.kdf.iterations !== ITERATIONS ||
    envelope.cipher?.name !== 'AES-256-GCM' ||
    typeof envelope.kdf.salt !== 'string' ||
    typeof envelope.cipher.iv !== 'string' ||
    typeof envelope.ciphertext !== 'string'
  ) {
    throw new Error('Unsupported backup envelope');
  }
  const salt = base64ToBytes(envelope.kdf.salt);
  const iv = base64ToBytes(envelope.cipher.iv);
  if (salt.length !== 16 || iv.length !== 12) throw new Error('Invalid backup parameters');
  const key = await deriveKey(passphrase, salt);
  const decrypted = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: ownedBuffer(iv), additionalData: ownedBuffer(AAD) },
    key,
    ownedBuffer(base64ToBytes(envelope.ciphertext)),
  );
  return new TextDecoder('utf-8', { fatal: true }).decode(decrypted);
}

export function assertImportSize(file: File): void {
  if (file.size <= 0 || file.size > MAX_IMPORT_BYTES) throw new Error('Backup size is invalid');
}
