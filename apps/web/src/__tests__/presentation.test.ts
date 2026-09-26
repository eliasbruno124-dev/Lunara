import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SwipeCalendar } from '../calendar';
import { addMonths, monthKey } from '../date';
import { translate, type MessageKey } from '../i18n';

// A deliberately small DOM adapter for calendar behavior, not a layout/browser
// simulation. Real responsive sizing and screenshots are checked in the browser.
class CalendarElement {
  className = '';
  textContent = '';
  disabled = false;
  offsetWidth = 500;
  dataset: Record<string, string> = {};
  attributes = new Map<string, string>();
  children: CalendarElement[] = [];
  events = new Map<string, (event: Record<string, unknown>) => void>();
  focused = false;

  constructor(readonly tagName: string) {}

  get classList() {
    return {
      add: (...classes: string[]) => { this.className = [...new Set([...this.className.split(' '), ...classes])].join(' '); },
      remove: (...classes: string[]) => { this.className = this.className.split(' ').filter((value) => !classes.includes(value)).join(' '); },
    };
  }

  append(...children: CalendarElement[]) { this.children.push(...children); }
  replaceChildren() { this.children = []; }
  setAttribute(name: string, value: string) { this.attributes.set(name, value); }
  addEventListener(name: string, listener: (event: Record<string, unknown>) => void) { this.events.set(name, listener); }
  focus() { this.focused = true; }
  emit(name: string, event: Record<string, unknown> = {}) { this.events.get(name)?.(event); }
  querySelectorAll(selector: string): CalendarElement[] {
    return this.children.flatMap((child) => [
      ...(child.className.split(' ').includes(selector.slice(1)) ? [child] : []),
      ...child.querySelectorAll(selector),
    ]);
  }
}

function fixture(options: Partial<ConstructorParameters<typeof SwipeCalendar>[0]> = {}) {
  const calendar = new SwipeCalendar({
    language: 'de', role: 'TRACKER', periods: [], prediction: null,
    initialMonth: '2026-09', ...options,
  });
  return { calendar, root: calendar.element as unknown as CalendarElement };
}

describe('accessible calendar presentation', () => {
  beforeEach(() => {
    vi.stubGlobal('document', { createElement: (tag: string) => new CalendarElement(tag) });
  });
  afterEach(() => vi.unstubAllGlobals());

  it.each(['de', 'en'] as const)('provides named month buttons in %s without removing swipes', (language) => {
    const onMonthChange = vi.fn();
    const { root } = fixture({ language, onMonthChange });
    const buttons = root.querySelectorAll('.month-button');
    expect(buttons.map((button) => button.attributes.get('aria-label'))).toEqual([
      translate(language, 'previousMonth'), translate(language, 'nextMonth'),
    ]);
    buttons[1]!.emit('click');
    expect(onMonthChange).toHaveBeenLastCalledWith('2026-10');
    expect(root.querySelectorAll('.month-button')[1]!.focused).toBe(true);
    root.emit('pointerdown', { clientX: 80, clientY: 100 });
    root.emit('pointerup', { clientX: 220, clientY: 106 });
    expect(onMonthChange).toHaveBeenLastCalledWith('2026-09');
  });

  it('retains a chosen date when the parent recreates or updates the calendar', () => {
    const { calendar, root } = fixture({ selectedDate: '2026-09-17' });
    const selected = root.querySelectorAll('.calendar-day').find((day) => day.dataset.date === '2026-09-17');
    expect(selected?.attributes.get('aria-pressed')).toBe('true');
    expect(selected?.className).toContain('selected');
    expect(calendar.selectedDate()).toBe('2026-09-17');
    calendar.update({ selectedDate: null });
    expect(root.querySelectorAll('.calendar-day').some((day) => day.attributes.get('aria-pressed') === 'true')).toBe(false);
  });

  it('still exposes actual and predicted ranges as words and symbols, not color alone', () => {
    const { root } = fixture({
      periods: [{ id: 'presentation-period', startDate: '2026-09-10', endDate: '2026-09-12' }],
      prediction: { available: true, confidence: 'MEDIUM', predictedStartFrom: '2026-09-18', predictedStartTo: '2026-09-21' },
    });
    const days = root.querySelectorAll('.calendar-day');
    expect(days.find((day) => day.dataset.date === '2026-09-11')?.attributes.get('aria-label')).toContain(translate('de', 'actualPeriod'));
    expect(days.find((day) => day.dataset.date === '2026-09-21')?.attributes.get('aria-label')).toContain(translate('de', 'predictedPeriod'));
    expect(days.find((day) => day.dataset.date === '2026-09-22')?.attributes.get('aria-label')).not.toContain(translate('de', 'predictedPeriod'));
    expect(root.querySelectorAll('.day-marker').some((marker) => marker.textContent === '≈')).toBe(true);
  });

  it('keeps keyboard month navigation and the original historical/future bounds', () => {
    const onMonthChange = vi.fn();
    const { root } = fixture({ initialMonth: addMonths(monthKey(), -120), onMonthChange });
    expect(root.querySelectorAll('.month-button')[0]!.disabled).toBe(true);
    const preventDefault = vi.fn();
    root.emit('keydown', { key: 'ArrowLeft', preventDefault });
    expect(onMonthChange).not.toHaveBeenCalled();
    root.emit('keydown', { key: 'ArrowRight', preventDefault });
    expect(onMonthChange).toHaveBeenCalledWith(addMonths(monthKey(), -119));
    expect(preventDefault).toHaveBeenCalledTimes(2);
    expect(root.focused).toBe(true);
    const future = fixture({ initialMonth: addMonths(monthKey(), 60) });
    expect(future.root.querySelectorAll('.month-button')[1]!.disabled).toBe(true);
  });
});

describe('editorial design contracts', () => {
  const css = readFileSync(new URL('../styles.css', import.meta.url), 'utf8');
  const main = readFileSync(new URL('../main.ts', import.meta.url), 'utf8');

  it('uses offline fonts, two themes, reduced motion and 48px calendar hit areas', () => {
    expect(css).toContain('Georgia, "Times New Roman", serif');
    expect(css).not.toMatch(/@import|@font-face|https?:\/\//);
    expect(css).toContain(':root[data-theme="dark"]');
    expect(css).toContain(':root[data-theme="system"]');
    expect(css).toContain('(prefers-reduced-motion: reduce)');
    expect(css).toContain('grid-template-columns: repeat(7, minmax(48px, 1fr))');
    expect(css).toMatch(/button, input, summary \{ min-height: 48px;/);
  });

  it('keeps normal text above 4.5:1 contrast in both palettes', () => {
    const luminance = (hex: string) => {
      const components = [0, 2, 4].map((offset) => {
        const channel = parseInt(hex.slice(offset, offset + 2), 16) / 255;
        return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
      });
      return components[0]! * 0.2126 + components[1]! * 0.7152 + components[2]! * 0.0722;
    };
    for (const selector of [':root', ':root[data-theme="dark"]']) {
      const block = css.slice(css.indexOf(`${selector} {`)).split('}')[0]!;
      const tokens = new Map([...block.matchAll(/--([a-z-]+): #([a-f\d]{6});/g)].map((match) => [match[1]!, match[2]!]));
      for (const [foreground, background] of [['ink', 'surface'], ['muted', 'surface'], ['sage-ink', 'sage'], ['on-petrol', 'petrol'], ['coral-ink', 'coral-soft']]) {
        const first = luminance(tokens.get(foreground!)!);
        const second = luminance(tokens.get(background!)!);
        const contrast = (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
        expect(contrast, `${selector}: ${foreground} on ${background}`).toBeGreaterThanOrEqual(4.5);
      }
    }
  });

  it('retains all grouped settings operations and separates the tip action from its body', () => {
    for (const control of ['language, appearance', 'this.partnerInvitationCard()', 'this.manualInvitationAcceptanceCard()', 'this.backupCard()', 'this.installCard()', 'this.lockLocalVault()', 'this.logout()']) {
      expect(main).toContain(control);
    }
    expect(main).toContain("element('details', 'settings-group')");
    expect(main).toContain("element('div', 'tip-action')");
    expect(main).toContain('tip.callToAction');
    expect(main).toContain("navigation.setAttribute('aria-label', this.t('mainNavigation'))");
  });

  it.each(['de', 'en'] as const)('has usable localized labels for new navigation and settings in %s', (language) => {
    const keys: MessageKey[] = ['mainNavigation', 'previousMonth', 'nextMonth', 'preferencesGroup', 'sharingGroup', 'dataGroup', 'appGroup', 'tryToday', 'noPartnerPrediction'];
    for (const key of keys) expect(translate(language, key).length).toBeGreaterThan(3);
    expect(translate(language, 'periodActiveSince', { date: '17.09.' })).toContain('17.09.');
    expect(translate(language, 'startPeriod')).not.toBe(translate(language, 'periodStarted'));
  });
});
