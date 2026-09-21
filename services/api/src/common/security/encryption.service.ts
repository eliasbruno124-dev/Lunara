import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createCipheriv, createDecipheriv, createHmac, randomBytes } from 'node:crypto';

@Injectable()
export class EncryptionService {
  private readonly key: Buffer;

  constructor(config: ConfigService) {
    this.key = Buffer.from(config.getOrThrow<string>('APP_MASTER_KEY'), 'base64');
    if (this.key.length !== 32) {
      throw new Error('APP_MASTER_KEY must decode to exactly 32 bytes');
    }
  }

  encrypt(value: string): string {
    const iv = randomBytes(12);
    const cipher = createCipheriv('aes-256-gcm', this.key, iv);
    const ciphertext = Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]);
    const tag = cipher.getAuthTag();
    return `v1.${iv.toString('base64url')}.${ciphertext.toString('base64url')}.${tag.toString('base64url')}`;
  }

  decrypt(value: string): string {
    const [version, ivValue, ciphertextValue, tagValue] = value.split('.');
    if (version !== 'v1' || !ivValue || !ciphertextValue || !tagValue) {
      throw new Error('Unsupported encrypted value');
    }
    const decipher = createDecipheriv('aes-256-gcm', this.key, Buffer.from(ivValue, 'base64url'));
    decipher.setAuthTag(Buffer.from(tagValue, 'base64url'));
    return Buffer.concat([
      decipher.update(Buffer.from(ciphertextValue, 'base64url')),
      decipher.final(),
    ]).toString('utf8');
  }

  /** Credentials must not be decryptable through the legacy health-note path. */
  encryptForPurpose(value: string, purpose: string): string {
    const context = this.purposeContext(purpose);
    const key = createHmac('sha256', this.key).update(context).digest();
    const iv = randomBytes(12);
    const cipher = createCipheriv('aes-256-gcm', key, iv);
    cipher.setAAD(context);
    const ciphertext = Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]);
    return `v2p.${iv.toString('base64url')}.${ciphertext.toString('base64url')}.${cipher.getAuthTag().toString('base64url')}`;
  }

  decryptForPurpose(value: string, purpose: string): string {
    const parts = value.split('.');
    const [version, ivValue, ciphertextValue, tagValue] = parts;
    if (parts.length !== 4 || version !== 'v2p' || !ivValue || !tagValue || ciphertextValue === undefined) {
      throw new Error('Unsupported purpose-bound encrypted value');
    }
    const context = this.purposeContext(purpose);
    const key = createHmac('sha256', this.key).update(context).digest();
    const iv = Buffer.from(ivValue, 'base64url');
    const tag = Buffer.from(tagValue, 'base64url');
    if (iv.length !== 12 || tag.length !== 16) throw new Error('Invalid encrypted value');
    const decipher = createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAAD(context);
    decipher.setAuthTag(tag);
    return Buffer.concat([
      decipher.update(Buffer.from(ciphertextValue, 'base64url')),
      decipher.final(),
    ]).toString('utf8');
  }

  private purposeContext(purpose: string): Buffer {
    if (!/^[a-z0-9-]{1,96}$/u.test(purpose)) throw new Error('Invalid encryption purpose');
    return Buffer.from(`lunara:purpose-encryption:v2:${purpose}`, 'utf8');
  }
}
