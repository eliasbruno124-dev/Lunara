import { describe, expect, it } from 'vitest';
import {
  decryptPortableBackup,
  decryptPortablePayload,
  encryptPortableBackup,
  mergeDailyLogs,
  mergePeriods,
  validatePortablePlaintext,
} from '../backup';
import type { PeriodEvent } from '../types';

const periods: PeriodEvent[] = [
  {
    id: '123e4567-e89b-42d3-a456-426614174000',
    startDate: '2026-07-18',
    endDate: '2026-07-22',
    source: 'MANUAL',
    version: 1,
    updatedAt: '2026-07-22T10:00:00.000Z',
  },
];

describe('portable encrypted backup', () => {
  it('round trips and merges adjacent unknown ends while retaining null and rejecting day5 overlaps', async () => {
    const first = { ...periods[0]!, startDate: '2026-07-01', endDate: null };
    const next = { ...first, id: '223e4567-e89b-42d3-a456-426614174000', startDate: '2026-07-06' };
    const envelope = await encryptPortableBackup([first, next], 'synthetic test backup');
    const restored = await decryptPortableBackup(JSON.stringify(envelope), 'synthetic test backup');
    expect(restored.periods).toMatchObject([first, next]);
    expect(restored.periods.map((item) => item.endDate)).toEqual([null, null]);
    expect(mergePeriods([first], [next])).toEqual([next, first]);
    expect(mergePeriods([next], [first])).toEqual([next, first]);
    const conflict = { ...next, startDate: '2026-07-05' };
    expect(() => validatePortablePlaintext({ ...restored, periods: [first, conflict] })).toThrow(/overlap/i);
    expect(() => mergePeriods([first], [conflict])).toThrow(/overlap/i);
    expect(() => mergePeriods([first], [{ ...next, startDate: '2026-06-27' }])).toThrow(/overlap/i);
  });
  it('round-trips the Android-compatible envelope parameters', async () => {
    const envelope = await encryptPortableBackup(periods, 'correct horse battery');
    expect(envelope).toMatchObject({
      format: 'lunara-portable-backup',
      version: 1,
      kdf: { name: 'PBKDF2-HMAC-SHA256', iterations: 310_000 },
      cipher: { name: 'AES-256-GCM' },
    });
    const plaintext = await decryptPortableBackup(JSON.stringify(envelope), 'correct horse battery');
    expect(plaintext).toMatchObject({
      schemaVersion: 1,
      product: 'Lunara',
      periods,
      dailyLogs: [],
    });
  });

  it('authenticates ciphertext and passphrase', async () => {
    const envelope = await encryptPortableBackup(periods, 'correct horse battery');
    const last = envelope.ciphertext.at(-3);
    envelope.ciphertext = `${envelope.ciphertext.slice(0, -3)}${last === 'A' ? 'B' : 'A'}${envelope.ciphertext.slice(-2)}`;
    await expect(decryptPortableBackup(JSON.stringify(envelope), 'correct horse battery')).rejects.toThrow();
    const intact = await encryptPortableBackup(periods, 'correct horse battery');
    await expect(decryptPortableBackup(JSON.stringify(intact), 'totally wrong phrase')).rejects.toThrow();
  });

  it('deduplicates by UUID and preserves the newest version', () => {
    const newer = { ...periods[0]!, endDate: '2026-07-23', version: 2 };
    expect(mergePeriods(periods, [newer])).toEqual([newer]);
  });

  it('accepts nullable fields omitted by Android Gson and normalizes them to null', () => {
    const normalized = validatePortablePlaintext({
      schemaVersion: 1,
      product: 'Lunara',
      exportedAt: '2026-08-25T10:00:00.000Z',
      profile: {
        fertilityPredictionEnabled: false,
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
      periods: [{
        id: '223e4567-e89b-42d3-a456-426614174000',
        startDate: '2026-08-18',
        source: 'MANUAL',
        createdAt: '2026-08-18T10:00:00.000Z',
        updatedAt: '2026-08-18T10:00:00.000Z',
        version: 1,
      }],
      dailyLogs: [{
        id: '323e4567-e89b-42d3-a456-426614174000',
        localDate: '2026-08-18',
        symptomsJson: '[]',
        createdAt: '2026-08-18T10:00:00.000Z',
        updatedAt: '2026-08-18T10:00:00.000Z',
        version: 1,
      }],
    });

    expect(normalized.profile).toMatchObject({
      typicalCycleLength: null,
      typicalPeriodLength: null,
      lutealLength: null,
    });
    expect(normalized.periods[0]?.endDate).toBeNull();
    expect(normalized.dailyLogs[0]).toMatchObject({
      flowLevel: null,
      painLevel: null,
      mood: null,
      energy: null,
      sleepQuality: null,
      bodyTemperature: null,
      encryptedNote: null,
    });
  });

  it('rejects overlap introduced only by merging local and imported histories', () => {
    const imported: PeriodEvent = {
      id: '423e4567-e89b-42d3-a456-426614174000',
      startDate: '2026-07-20',
      endDate: '2026-07-24',
      version: 1,
    };
    expect(() => mergePeriods(periods, [imported])).toThrow(/overlap/u);
  });

  it('applies daily logs without silently resolving two records for one day', () => {
    const original = {
      id: '323e4567-e89b-42d3-a456-426614174000',
      localDate: '2026-07-18',
      flowLevel: null,
      painLevel: 3,
      mood: null,
      energy: null,
      sleepQuality: null,
      bodyTemperature: null,
      symptomsJson: '["cramps"]',
      encryptedNote: null,
      createdAt: '2026-07-18T10:00:00.000Z',
      updatedAt: '2026-07-18T10:00:00.000Z',
      version: 1,
    };
    const updated = {
      ...original,
      painLevel: 5,
      updatedAt: '2026-07-18T11:00:00.000Z',
      version: 2,
    };
    expect(mergeDailyLogs([original], [updated])).toEqual([updated]);

    expect(() => mergeDailyLogs([original], [{
      ...original,
      id: '523e4567-e89b-42d3-a456-426614174000',
    }])).toThrow(/same local date/u);
  });

  it('rejects short passphrases', async () => {
    await expect(encryptPortableBackup(periods, 'too short')).rejects.toThrow(/12/u);
  });

  it('decrypts the shared deterministic Android/WebCrypto Unicode vector', async () => {
    const vector = '{"format":"lunara-portable-backup","version":1,"kdf":{"name":"PBKDF2-HMAC-SHA256","iterations":310000,"salt":"AAECAwQFBgcICQoLDA0ODw=="},"cipher":{"name":"AES-256-GCM","iv":"EBESExQVFhcYGRob"},"ciphertext":"xPAznaEyiz2TBpZXbvL9pQKKfE2guAEPYukqQgzWbxfm9H3C7lMC4+L7LO/MYK91GQZ6DPBj"}';
    await expect(decryptPortablePayload(vector, 'Lunara 🔒 Backup sicher')).resolves.toBe(
      '{"schemaVersion":1,"product":"Lunara"}',
    );
  });
});
