import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApkDownloadCard } from '../apk-download';
import { translate } from '../i18n';
import { MinimalElement } from './minimal-dom';

describe('native Android download', () => {
  beforeEach(() => {
    vi.stubGlobal('document', { createElement: (tag: string) => new MinimalElement(tag) });
  });
  afterEach(() => vi.unstubAllGlobals());

  it.each(['de', 'en'] as const)('creates a localized ordinary same-origin download anchor in %s', (language) => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    const card = createApkDownloadCard(language) as unknown as MinimalElement;
    expect(card.querySelectorAll('h2')[0]?.textContent).toBe(translate(language, 'androidApp'));
    expect(card.querySelectorAll('p').map((item) => item.textContent)).toEqual([
      translate(language, 'androidAppDescription'), translate(language, 'downloadApkHint'),
    ]);
    const anchors = card.querySelectorAll('a');
    expect(anchors).toHaveLength(1);
    const anchor = anchors[0]!;
    expect(anchor.href).toBe('/api/v1/downloads/lunara.apk');
    expect(anchor.download).toBe('lunara.apk');
    expect(anchor.textContent).toBe(translate(language, 'downloadApk'));
    expect(anchor.className).toContain('download-link');
    // No JavaScript download interception, token-bearing URL, or large Blob allocation.
    expect(anchor.events.size).toBe(0);
    const preventDefault = vi.fn();
    anchor.emit('click', { preventDefault });
    expect(preventDefault).not.toHaveBeenCalled();
    expect(fetch).not.toHaveBeenCalled();
    const url = new URL(anchor.href, 'https://lunara.example/settings');
    expect(url.origin).toBe('https://lunara.example');
    expect([url.search, url.hash, url.username, url.password]).toEqual(['', '', '', '']);
  });

  it('keeps APK settings independent of PWA installation availability', () => {
    const main = readFileSync(new URL('../main.ts', import.meta.url), 'utf8');
    expect(main).toContain('const appItems = [server, createApkDownloadCard(this.language), ...(install ? [install] : [])]');
    expect(main).toContain("this.settingsGroup('app', 'appGroup', 'appHint', ...appItems)");
  });
});
