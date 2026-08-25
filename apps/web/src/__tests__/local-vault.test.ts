import { describe, expect, it } from 'vitest';
import { defaultPortableExtras } from '../backup';
import { LocalVaultSession } from '../local-vault';

const state = {
  periods: [{
    id: '123e4567-e89b-42d3-a456-426614174000',
    startDate: '2026-07-18',
    endDate: '2026-07-22',
    source: 'MANUAL',
    createdAt: '2026-07-18T10:00:00.000Z',
    updatedAt: '2026-07-22T10:00:00.000Z',
    version: 1,
  }],
  extras: {
    ...defaultPortableExtras(),
    settings: {
      ...defaultPortableExtras().settings,
      displayName: 'Private Person',
      darkMode: true,
      useSystemTheme: false,
    },
    dailyLogs: [{
      id: '223e4567-e89b-42d3-a456-426614174000',
      localDate: '2026-07-19',
      flowLevel: 'MEDIUM',
      painLevel: 4,
      mood: null,
      energy: 6,
      sleepQuality: 7,
      bodyTemperature: null,
      symptomsJson: '["cramps"]',
      encryptedNote: 'private note',
      createdAt: '2026-07-19T08:00:00.000Z',
      updatedAt: '2026-07-19T08:00:00.000Z',
      version: 1,
    }],
  },
};

describe('local Solo vault', () => {
  it('stores only authenticated ciphertext and requires the passphrase after locking', async () => {
    const creator = new LocalVaultSession();
    const envelope = await creator.create('correct horse battery', state);
    const serialized = JSON.stringify(envelope);

    expect(serialized).not.toContain('2026-07-18');
    expect(serialized).not.toContain('cramps');
    expect(serialized).not.toContain('Private Person');
    expect(serialized).not.toContain('private note');
    expect(envelope).toMatchObject({
      format: 'lunara-local-vault',
      version: 1,
      kdf: { name: 'PBKDF2-HMAC-SHA256', iterations: 600_000 },
      cipher: { name: 'AES-256-GCM' },
    });

    creator.lock();
    await expect(creator.seal(state)).rejects.toThrow(/locked/u);

    const reopened = new LocalVaultSession();
    await expect(reopened.unlock('definitely wrong phrase', envelope)).rejects.toThrow();
    await expect(reopened.unlock('correct horse battery', envelope)).resolves.toEqual(state);
  });

  it('rejects modified ciphertext and weak passphrases', async () => {
    const session = new LocalVaultSession();
    await expect(session.create('too short', state)).rejects.toThrow(/12/u);
    const envelope = await session.create('correct horse battery', state);
    const index = Math.max(0, envelope.ciphertext.length - 4);
    envelope.ciphertext = `${envelope.ciphertext.slice(0, index)}${
      envelope.ciphertext[index] === 'A' ? 'B' : 'A'
    }${envelope.ciphertext.slice(index + 1)}`;
    await expect(new LocalVaultSession().unlock('correct horse battery', envelope)).rejects.toThrow();
  });

  it('keeps an in-flight seal decryptable when the page session locks', async () => {
    const session = new LocalVaultSession();
    await session.create('correct horse battery', state);
    const sealing = session.seal({
      ...state,
      periods: [{ ...state.periods[0]!, endDate: '2026-07-23', version: 2 }],
    });
    session.lock();

    const envelope = await sealing;
    await expect(
      new LocalVaultSession().unlock('correct horse battery', envelope),
    ).resolves.toMatchObject({
      periods: [{ endDate: '2026-07-23', version: 2 }],
    });
  });
});
