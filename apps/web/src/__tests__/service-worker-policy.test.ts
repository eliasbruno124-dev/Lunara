import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

describe('PWA service-worker reserved paths', () => {
  it('keeps Authentik, public invitation, and well-known paths out of fallback and runtime cache', () => {
    const config = readFileSync(new URL('../../vite.config.ts', import.meta.url), 'utf8');
    expect(config).toContain('^\\/outpost\\.goauthentik\\.io');
    expect(config).toContain('^\\/\\.well-known');
    expect(config).toContain('^\\/invite(?:\\/|$)');
    expect(config.match(/outpost\\\.goauthentik\\\.io/gu)).toHaveLength(2);
    expect(config.match(/\\\.well-known/gu)).toHaveLength(2);
    expect(config.match(/invite\(\?:\\\/\|\$/gu)).toHaveLength(2);
    expect(config.match(/downloads\(\?:\\\/\|\$/gu)).toHaveLength(2);
  });
});
