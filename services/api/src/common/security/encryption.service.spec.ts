import { ConfigService } from '@nestjs/config';
import { EncryptionService } from './encryption.service.js';

describe('purpose-separated encryption', () => {
  const config = { getOrThrow: () => Buffer.alloc(32, 7).toString('base64') };
  const encryption = new EncryptionService(config as unknown as ConfigService);
  const purpose = 'auth-refresh-replay-v1';

  it('round trips with the same purpose after process restart and randomizes ciphertext', () => {
    const value = encryption.encryptForPurpose('synthetic credential envelope', purpose);
    const restarted = new EncryptionService(config as unknown as ConfigService);
    expect(restarted.decryptForPurpose(value, purpose)).toBe('synthetic credential envelope');
    expect(encryption.encryptForPurpose('synthetic credential envelope', purpose)).not.toBe(value);
  });

  it('cannot expose a replay credential through the legacy note decryptor, even after relabeling', () => {
    const value = encryption.encryptForPurpose('synthetic credential envelope', purpose);
    expect(() => encryption.decrypt(value)).toThrow();
    expect(() => encryption.decrypt(value.replace(/^v2p\./u, 'v1.'))).toThrow();
  });

  it('rejects another purpose and legacy ciphertext relabeled as a replay', () => {
    const value = encryption.encryptForPurpose('test', purpose);
    expect(() => encryption.decryptForPurpose(value, 'other-purpose')).toThrow();
    const note = encryption.encrypt('existing note');
    expect(encryption.decrypt(note)).toBe('existing note');
    expect(() => encryption.decryptForPurpose(note.replace(/^v1\./u, 'v2p.'), purpose)).toThrow();
  });

  it('rejects tampered tags, unsupported framing and malformed purpose', () => {
    const value = encryption.encryptForPurpose('test', purpose);
    const parts = value.split('.');
    parts[3] = Buffer.alloc(16).toString('base64url');
    expect(() => encryption.decryptForPurpose(parts.join('.'), purpose)).toThrow();
    expect(() => encryption.decryptForPurpose(`${value}.extra`, purpose)).toThrow();
    expect(() => encryption.encryptForPurpose('test', '')).toThrow();
  });
});
