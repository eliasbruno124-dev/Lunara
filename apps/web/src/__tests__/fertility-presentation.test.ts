import { describe, expect, it } from 'vitest';
import { hasFertileEstimate } from '../fertility-presentation';
import { visiblePartnerCalendar, partnerCalendarNotices } from '../partner-calendar';
import type { Prediction } from '../types';

const fertility: Prediction = {
  fertileWindowFrom: '2026-10-15', fertileWindowTo: '2026-10-20',
  fertilityEstimate: { enabled: true, available: true, estimated: true, notContraception: true, doesNotConfirmOvulation: true, reason: 'AVAILABLE' },
};
describe('independent fertile-window disclosure', () => {
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
