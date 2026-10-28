import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createBrandMark, createIcon, type IconName } from '../ui-icons';
import { MinimalElement } from './minimal-dom';

describe('local Android-aligned navigation and branding', () => {
  beforeEach(() => vi.stubGlobal('document', {
    createElement: (tag: string) => new MinimalElement(tag),
    createElementNS: (_namespace: string, tag: string) => new MinimalElement(tag),
  }));
  afterEach(() => vi.unstubAllGlobals());

  it.each<IconName>(['today', 'calendar', 'settings', 'preferences', 'sharing', 'data', 'app'])('renders %s without remote images or duplicate accessible text', (name) => {
    const icon = createIcon(name, 'test-icon') as unknown as MinimalElement;
    expect(icon.attributes.get('aria-hidden')).toBe('true');
    const svg = icon.querySelectorAll('svg')[0]!;
    expect(svg.attributes.get('viewBox')).toBe('0 0 24 24');
    expect(svg.attributes.get('focusable')).toBe('false');
    expect(svg.attributes.get('stroke')).toBe('currentColor');
    expect(svg.querySelectorAll('path')[0]?.attributes.get('d')).toMatch(/^M|^m/);
    expect(svg.querySelectorAll('image')).toHaveLength(0);
  });

  it('reuses the genuine Lunara logo geometry with theme-aware ink instead of a new mark', () => {
    const source = readFileSync(new URL('../../public/icons/lunara.svg', import.meta.url), 'utf8');
    const mark = createBrandMark() as unknown as MinimalElement;
    expect(mark.attributes.get('aria-hidden')).toBe('true');
    const arc = mark.querySelectorAll('path')[0]!;
    expect(source).toContain(`d="${arc.attributes.get('d')}"`);
    expect(arc.attributes.get('stroke')).toBe('currentColor');
    const dot = mark.querySelectorAll('circle')[0]!;
    for (const name of ['cx', 'cy', 'r', 'fill']) expect(source).toContain(`${name}="${dot.attributes.get(name)}"`);
  });
});
