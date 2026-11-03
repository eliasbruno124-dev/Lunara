import { describe, expect, it } from 'vitest';
import { hasCurrentFertileEstimate, hasFertileEstimate } from '../fertility-presentation';
import { addDays } from '../date';
import { visiblePartnerCalendar, partnerCalendarNotices } from '../partner-calendar';
import type { Prediction } from '../types';

const fertility: Prediction = {
  fertileWindowFrom: '2026-10-15', fertileWindowTo: '2026-10-20',
  fertilityEstimate: { enabled: true, available: true, estimated: true, notContraception: true, doesNotConfirmOvulation: true, reason: 'AVAILABLE' },
};
describe('independent fertile-window disclosure', () => {
  it.each([true, false])('guards both complete metadata and legacy boolean %s against wide cached ranges', (legacy) => {
    for (const days of [19, 20, 33]) {
      const prediction = { ...fertility, available: true, fertileWindowTo: addDays(fertility.fertileWindowFrom!, days - 1), fertilityEstimate: legacy ? true : fertility.fertilityEstimate! };
      expect(hasFertileEstimate(prediction)).toBe(days === 19);
    }
  });
  it('expires the current card on its own end, not the period interval, without deleting historical data', () => {
    const input = Object.freeze({ ...fertility, available: true, predictedStartTo: '2026-11-30' });
    expect(hasCurrentFertileEstimate(input, '2026-10-20')).toBe(true);
    expect(hasCurrentFertileEstimate(input, '2026-10-21')).toBe(false);
    expect(hasCurrentFertileEstimate(input, 'bad')).toBe(false);
    expect(hasCurrentFertileEstimate(input, '2026-10-20', true)).toBe(false);
    expect(hasFertileEstimate(input)).toBe(true);
    expect(hasCurrentFertileEstimate({ ...fertility, available: false }, '2026-10-20')).toBe(true);
  });
  it('shows an explicitly eligible fertile window without disclosing the period forecast', () => {
    expect(hasFertileEstimate(fertility)).toBe(true);
    expect(partnerCalendarNotices({ calendarAccess: { periodDates: false, fertileWindow: true }, prediction: fertility })).toEqual(['calendarPeriodsNotShared']);
  });
  it.each(['enabled', 'available', 'estimated', 'notContraception', 'doesNotConfirmOvulation'])('fails closed when %s is not true', (key) => {
    expect(hasFertileEstimate({ ...fertility, fertilityEstimate: { ...(fertility.fertilityEstimate as object), [key]: false } as never })).toBe(false);
  });
  it('rejects unavailable reason and invalid/reversed boundaries', () => {
    expect(hasFertileEstimate({ ...fertility, fertilityEstimate: { ...(fertility.fertilityEstimate as object), reason: 'INSUFFICIENT_DATA' } as never })).toBe(false);
    expect(hasFertileEstimate({ ...fertility, fertileWindowFrom: '2026-02-30' })).toBe(false);
    expect(hasFertileEstimate({ ...fertility, fertileWindowTo: '2026-10-01' })).toBe(false);
  });
  it('supports the explicit local-only boolean eligibility result', () => {
    expect(hasFertileEstimate({ ...fertility, available: true, fertilityEstimate: true })).toBe(true);
    expect(hasFertileEstimate({ ...fertility, available: false, fertilityEstimate: true })).toBe(false);
  });
  it('suppresses cached data when permission is explicitly denied or paused', () => {
    const dashboard = { prediction: fertility, periodDates: [{ startDate: '2026-10-01', endDate: null }] };
    const denied = visiblePartnerCalendar({ ...dashboard, calendarAccess: { periodDates: false, fertileWindow: false } });
    expect(denied.periodDates).toEqual([]); expect(hasFertileEstimate(denied.prediction)).toBe(false);
    expect(visiblePartnerCalendar({ ...dashboard, sharingPaused: true })).toEqual({ prediction: null, periodDates: [] });
    expect(visiblePartnerCalendar(dashboard).periodDates).toEqual(dashboard.periodDates);
  });
});
