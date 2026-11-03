import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createCalendarLegend, SwipeCalendar } from '../calendar';
import { addMonths, monthKey } from '../date';
import { translate, type MessageKey } from '../i18n';
import { MinimalElement as CalendarElement } from './minimal-dom';

function fixture(options: Partial<ConstructorParameters<typeof SwipeCalendar>[0]> = {}) {
  const calendar = new SwipeCalendar({
    language: 'de', role: 'TRACKER', periods: [], prediction: null,
    initialMonth: '2026-09', ...options,
  });
  return { calendar, root: calendar.element as unknown as CalendarElement };
}

describe('accessible calendar presentation', () => {
  it('refreshes five-day paint and today at midnight while retaining selection and raw unknown end', () => {
    const period = Object.freeze({ id: 'raw-open', startDate: '2026-09-01', endDate: null });
    const { calendar, root } = fixture({ periods: [period], referenceDate: '2026-09-04', selectedDate: '2026-09-03' });
    expect(root.querySelectorAll('.actual')).toHaveLength(4);
    calendar.update({ referenceDate: '2026-09-05' });
    expect(root.querySelectorAll('.actual')).toHaveLength(5);
    calendar.update({ referenceDate: '2026-09-06' });
    expect(root.querySelectorAll('.actual')).toHaveLength(5);
    expect(root.querySelectorAll('.is-today')[0]?.dataset.date).toBe('2026-09-06');
    expect(calendar.selectedDate()).toBe('2026-09-03');
    expect(period.endDate).toBeNull();
  });
  beforeEach(() => {
    vi.stubGlobal('document', { createElement: (tag: string) => new CalendarElement(tag) });
  });
  afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

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
      prediction: { available: true, confidence: 'MEDIUM', predictedStartFrom: '2026-09-18', predictedStartTo: '2026-09-21', predictedEndFrom: '2026-09-22', predictedEndTo: '2026-09-25' },
    });
    const days = root.querySelectorAll('.calendar-day');
    expect(days.find((day) => day.dataset.date === '2026-09-11')?.attributes.get('aria-label')).toContain(translate('de', 'actualPeriod'));
    expect(days.find((day) => day.dataset.date === '2026-09-21')?.attributes.get('aria-label')).toContain(translate('de', 'predictedPeriod'));
    expect(days.find((day) => day.dataset.date === '2026-09-22')?.attributes.get('aria-label')).toContain(translate('de', 'predictedPeriod'));
    expect(days.find((day) => day.dataset.date === '2026-09-26')?.attributes.get('aria-label')).not.toContain(translate('de', 'predictedPeriod'));
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

  it.each(['de', 'en'] as const)('renders one estimated category across start, duration and the year boundary in %s', (language) => {
    const prediction = Object.freeze({
      available: true, predictedStartFrom: '2026-12-29', predictedStartTo: '2027-01-02',
      predictedEndFrom: '2027-01-01', predictedEndTo: '2027-01-06',
    });
    for (const [month, dates] of [
      ['2026-12', ['2026-12-29', '2026-12-30', '2026-12-31']],
      ['2027-01', ['2027-01-01', '2027-01-02', '2027-01-03', '2027-01-04', '2027-01-05', '2027-01-06']],
    ] as const) {
      const { root } = fixture({ language, prediction, initialMonth: month });
      const days = root.querySelectorAll('.predicted');
      expect(days.map((day) => day.dataset.date)).toEqual(dates);
      for (const day of days) {
        expect(day.querySelectorAll('.day-marker')[0]?.textContent).toBe('≈');
        expect(day.attributes.get('aria-label')).toContain(translate(language, 'predictedPeriod'));
      }
    }
    expect(prediction.predictedStartTo).toBe('2027-01-02');
    expect(prediction.predictedEndTo).toBe('2027-01-06');
  });

  it.each(['de', 'en'] as const)('uses one dominant visual state and preserves accessible descriptions on overlapping days in %s', (language) => {
    const { root } = fixture({
      language,
      periods: [{ id: 'synthetic-overlap', startDate: '2026-09-10', endDate: '2026-09-12' }],
      prediction: {
        available: true, predictedStartFrom: '2026-09-11', predictedStartTo: '2026-09-14',
        predictedEndFrom: '2026-09-14', predictedEndTo: '2026-09-15',
        fertileWindowFrom: '2026-09-12', fertileWindowTo: '2026-09-16', fertilityEstimate: true,
      },
    });
    const days = root.querySelectorAll('.calendar-day');
    for (const [date, state, marker, label] of [
      ['2026-09-12', 'actual', 'P', 'actualPeriod'],
      ['2026-09-14', 'predicted', '≈', 'predictedPeriod'],
      ['2026-09-15', 'predicted', '≈', 'predictedPeriod'],
      ['2026-09-16', 'fertile', 'F?', 'fertileEstimate'],
    ] as const) {
      const day = days.find((candidate) => candidate.dataset.date === date)!;
      expect(day.className.split(' ').filter((name) => ['actual', 'predicted', 'fertile'].includes(name))).toEqual([state]);
      expect(day.querySelectorAll('.day-marker')[0]?.textContent).toBe(marker);
      expect(day.attributes.get('aria-label')).toContain(translate(language, label));
    }
    const overlap = days.find((day) => day.dataset.date === '2026-09-12')!;
    for (const label of ['actualPeriod', 'predictedPeriod', 'fertileEstimate'] as const) {
      expect(overlap.attributes.get('aria-label')).toContain(translate(language, label));
    }
  });

  it('preserves recorded state, marker and accessibility when today is selected', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-12T12:00:00Z'));
    const { root } = fixture({
      selectedDate: '2026-09-12',
      periods: [{ id: 'synthetic-today', startDate: '2026-09-10', endDate: '2026-09-13' }],
    });
    const today = root.querySelectorAll('.calendar-day').find((day) => day.dataset.date === '2026-09-12')!;
    expect(today.className.split(' ')).toEqual(expect.arrayContaining(['actual', 'is-today', 'selected']));
    expect(today.attributes.get('aria-current')).toBe('date');
    expect(today.attributes.get('aria-pressed')).toBe('true');
    expect(today.querySelectorAll('.day-marker')[0]?.textContent).toBe('P');
  });

  it('renders independent fertility only when its existing safety checks allow it', () => {
    const prediction = {
      available: false, fertileWindowFrom: '2026-09-12', fertileWindowTo: '2026-09-16',
      fertilityEstimate: { enabled: true, available: true, estimated: true, notContraception: true, doesNotConfirmOvulation: true, reason: 'AVAILABLE' },
    };
    const allowed = fixture({ role: 'PARTNER', prediction }).root;
    expect(allowed.querySelectorAll('.fertile')).toHaveLength(5);
    expect(allowed.querySelectorAll('.day-marker').every((marker) => marker.textContent === 'F?')).toBe(true);
    const denied = fixture({ role: 'PARTNER', prediction: { ...prediction, fertilityEstimate: { ...prediction.fertilityEstimate, enabled: false } } }).root;
    expect(denied.querySelectorAll('.fertile')).toHaveLength(0);
    expect(denied.querySelectorAll('.day-marker')).toHaveLength(0);
  });

  it.each(['de', 'en'] as const)('matches every visible semantic symbol to a localized colored legend in %s', (language) => {
    const legend = createCalendarLegend(language, true) as unknown as CalendarElement;
    const keys = legend.querySelectorAll('.legend-key');
    expect(keys.map((key) => key.textContent.split(' ')[0])).toEqual(['P', '≈', 'F?']);
    expect(keys.map((key) => key.className)).toEqual(['legend-key actual-key', 'legend-key predicted-key', 'legend-key fertile-key']);
    expect(keys.map((key) => key.textContent)).toEqual([
      translate(language, 'calendarActualLegend'), translate(language, 'calendarPredictionLegend'),
      translate(language, 'calendarFertileLegend'),
    ]);
    const hiddenFertility = createCalendarLegend(language, false) as unknown as CalendarElement;
    expect(hiddenFertility.querySelectorAll('.fertile-key')).toHaveLength(0);
    expect(hiddenFertility.querySelectorAll('.legend-key')).toHaveLength(2);
  });
});

describe('Android-aligned journal design contracts', () => {
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
      for (const [foreground, background] of [['ink', 'surface'], ['muted', 'surface'], ['ink', 'surface-raised'], ['muted', 'surface-muted'], ['sage-ink', 'sage'], ['on-petrol', 'petrol'], ['coral-ink', 'coral-soft'], ['nav-ink', 'coral-soft'], ['period-ink', 'period-bg'], ['prediction-ink', 'prediction-bg'], ['fertile-ink', 'fertile-bg']]) {
        const first = luminance(tokens.get(foreground!)!);
        const second = luminance(tokens.get(background!)!);
        const contrast = (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
        expect(contrast, `${selector}: ${foreground} on ${background}`).toBeGreaterThanOrEqual(4.5);
      }
    }
  });

  it('mirrors native light/dark tokens and keeps system-dark identical to explicit dark', () => {
    const tokens = (selector: string) => Object.fromEntries([...css.slice(css.indexOf(`${selector} {`)).split('}')[0]!.matchAll(/--([a-z-]+): (#[a-f\d]{6});/g)].map((match) => [match[1]!, match[2]!]));
    expect(tokens(':root')).toMatchObject({ surface: '#f7f4ed', ink: '#173e43', 'surface-raised': '#fcfaf5', sage: '#dde6db', hero: '#173e43', 'on-hero': '#fff8ec', 'period-bg': '#ffe4ec', 'period-ink': '#881337', 'prediction-bg': '#fff1c2', 'prediction-ink': '#713f12', 'fertile-bg': '#ccfbf1', 'fertile-ink': '#115e59' });
    expect(tokens(':root[data-theme="dark"]')).toMatchObject({ surface: '#111d1e', 'surface-raised': '#182729', petrol: '#b5d4c8', 'period-bg': '#5b1635', 'period-ink': '#ffd8e5', 'prediction-bg': '#4c3415', 'prediction-ink': '#ffe7a0', 'fertile-bg': '#123f42', 'fertile-ink': '#99f6e4' });
    expect(tokens(':root[data-theme="system"]')).toEqual(tokens(':root[data-theme="dark"]'));
  });

  it('uses the exact same semantic fill/ink for days and keys; state overlays cannot replace them', () => {
    for (const [state, token] of [['actual', 'period'], ['predicted', 'prediction'], ['fertile', 'fertile']] as const) {
      const declaration = css.slice(css.indexOf(`.calendar-day.${state}, .${state}-key {`)).split('}')[0]!;
      expect(declaration).toContain(`background: var(--${token}-bg)`);
      expect(declaration).toContain(`color: var(--${token}-ink)`);
    }
    const hasDestructiveOverlay = (source: string) => [...source.matchAll(/([^{}]+)\{([^{}]*)\}/g)].some(([, selector, body]) =>
      /\.calendar-day\.(?:selected|is-today)/.test(selector!) && /(?:background(?:-color)?|(?<!-)color)\s*:/.test(body!));
    expect(hasDestructiveOverlay(css)).toBe(false);
    // Positive control: the absence oracle must reject a color-destroying rule.
    expect(hasDestructiveOverlay(`${css}\n.calendar-day.selected { background: white; }`)).toBe(true);
    expect(css).toContain('.calendar-day.is-today .day-number { text-decoration: underline;');
  });

  it('keeps the entire predicted category dashed and distinct from recorded periods', () => {
    const declaration = css.slice(css.indexOf('.calendar-day.predicted, .predicted-key {')).split('}')[0]!;
    expect(declaration).toContain('border: 1px dashed var(--prediction-ink)');
    const containsSplitStyle = (source: string) => /\.predicted-duration(?:\b|-key)/.test(source);
    expect(containsSplitStyle(css)).toBe(false);
    expect(containsSplitStyle(`${css}\n.calendar-day.predicted-duration { border-style: solid; }`)).toBe(true);
  });

  it.each(['de', 'en'] as const)('matches the single Android estimate label and legend in %s', (language) => {
    const resources = readFileSync(new URL(`../../../android/app/src/main/res/${language === 'de' ? 'values' : 'values-en'}/strings.xml`, import.meta.url), 'utf8');
    const nativeString = (name: string) => resources.match(new RegExp(`<string name="${name}">([^<]+)</string>`))?.[1];
    expect(translate(language, 'predictedPeriod')).toBe(language === 'de' ? 'Geschätzte Periode' : 'Estimated period');
    expect(nativeString('calendar_day_prediction_status')).toBe(translate(language, 'predictedPeriod'));
    expect(nativeString('calendar_prediction_marker')).toBe(translate(language, 'calendarPredictionLegend'));
    expect(nativeString('calendar_actual_marker')).toBe(translate(language, 'calendarActualLegend'));
    expect(nativeString('calendar_fertility_marker')).toBe(translate(language, 'calendarFertileLegend'));
  });

  it('retains the native heading → forecast → action hierarchy and responsive navigation', () => {
    const home = main.slice(main.indexOf('private todayView()'), main.indexOf('private predictionCard()'));
    expect(home.indexOf('lead.append(greeting, this.predictionCard())')).toBeLessThan(home.indexOf("const controls = element('section', 'period-controls')"));
    expect(home).toContain('rail.append(this.recentPeriodsCard())');
    expect(css).toMatch(/\.prediction-summary \{[^}]*border-radius: 28px;/);
    expect(css).toMatch(/\.period-action \{[^}]*min-height: 56px;/);
    expect(css).toContain('.bottom-nav button.active .nav-icon { background: var(--coral-soft);');
    expect(css).toContain('@media (min-width: 1050px)');
    expect(main).toContain("createIcon(screen, 'nav-icon')");
    expect(main).toContain("createIcon(icon, 'settings-icon')");
    expect(main).toContain("this.user?.role === 'PARTNER' ? 'predictionUnavailable' : 'predictionEmpty'");
  });

  it('removes the 336px calendar minimum at narrow widths without shrinking labels or hiding Sunday', () => {
    const narrow = css.slice(css.indexOf('@media (max-width: 359px)')).split('@media')[1]!;
    expect(narrow).toContain('grid-template-columns: repeat(7, minmax(0, 1fr))');
    expect(narrow).toContain('.calendar-day { min-width: 0; aspect-ratio: auto; }');
    expect(css).toMatch(/\.calendar-day \{[^}]*width: 100%;/);
    expect(narrow).not.toMatch(/font-size|display:\s*none|visibility:\s*hidden/);
    const navigation = css.slice(css.indexOf('@media (max-width: 383px)'), css.indexOf('@media (max-width: 359px)'));
    expect(navigation).toContain('.bottom-nav { gap: 0; padding-inline: 4px; }');
    expect(navigation).toContain('.bottom-nav button { white-space: nowrap; overflow-wrap: normal; }');
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
    const keys: MessageKey[] = ['mainNavigation', 'previousMonth', 'nextMonth', 'preferencesGroup', 'sharingGroup', 'dataGroup', 'appGroup', 'tryToday', 'noPartnerPrediction', 'predictionEmpty', 'predictionUnavailable', 'welcomeEyebrow', 'estimatedStart'];
    for (const key of keys) expect(translate(language, key).length).toBeGreaterThan(3);
    expect(translate(language, 'periodActiveSince', { date: '17.09.' })).toContain('17.09.');
    expect(translate(language, 'startPeriod')).not.toBe(translate(language, 'periodStarted'));
  });
});
