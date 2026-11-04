import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createDonationCard, DONATION_URL } from '../donation';
import { translate } from '../i18n';
import { MinimalElement } from './minimal-dom';

describe('optional PayPal support link', () => {
  beforeEach(() => {
    vi.stubGlobal('document', { createElement: (tag: string) => new MinimalElement(tag) });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('uses the same address as the Android app', () => {
    const brand = readFileSync(new URL('../../../android/app/src/main/res/values/brand.xml', import.meta.url), 'utf8');
    expect(brand).toContain(`<string name="donation_url" translatable="false">${DONATION_URL}</string>`);
  });

  it.each(['de', 'en'] as const)('creates a localized external link with an optional hint in %s', (language) => {
    const card = createDonationCard(language, () => true) as unknown as MinimalElement;
    expect(card.querySelectorAll('h2')[0]?.textContent).toBe(translate(language, 'donationTitle'));
    expect(card.querySelectorAll('p').map((item) => item.textContent)).toEqual([translate(language, 'donationOptional')]);
    const anchors = card.querySelectorAll('a');
    expect(anchors).toHaveLength(1);
    const link = anchors[0]!;
    expect(link.href).toBe(DONATION_URL);
    expect(link.target).toBe('_blank');
    expect(link.rel).toBe('noopener noreferrer');
    expect(link.textContent).toBe(translate(language, 'donationButton'));
  });

  it('asks before leaving and stays on the page when cancelled', () => {
    const asked: string[] = [];
    const cancel = createDonationCard('de', (message) => { asked.push(message); return false; }) as unknown as MinimalElement;
    const preventDefault = vi.fn();
    cancel.querySelectorAll('a')[0]!.emit('click', { preventDefault });
    expect(preventDefault).toHaveBeenCalledOnce();
    expect(asked[0]).toContain(DONATION_URL);

    const middle = vi.fn();
    cancel.querySelectorAll('a')[0]!.emit('auxclick', { preventDefault: middle });
    expect(middle).toHaveBeenCalledOnce();

    const accept = createDonationCard('de', () => true) as unknown as MinimalElement;
    const allowDefault = vi.fn();
    accept.querySelectorAll('a')[0]!.emit('click', { preventDefault: allowDefault });
    expect(allowDefault).not.toHaveBeenCalled();
  });

  it('is part of the app settings group', () => {
    const main = readFileSync(new URL('../main.ts', import.meta.url), 'utf8');
    expect(main).toContain('appItems.push(createDonationCard(this.language, (message) => window.confirm(message)));');
  });
});
