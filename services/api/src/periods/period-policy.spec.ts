import { CyclePhase } from '@prisma/client';
import { estimatePartnerPhase, localDateInTimeZone } from '../partner/partner-phase.js';
import { calculatePrediction } from '../prediction/prediction.algorithm.js';
import { applicableTipPhases } from '../tips/tip-phase.js';
import { effectivePeriodEndDate, effectivePeriodEndLocalDate } from './period-policy.js';

const shared = { sharePrediction: true, sharePeriodDates: true, sharePhase: true, shareFertileWindow: false };

describe('five inclusive calendar day period policy', () => {
  it.each([
    ['2026-01-29', '2026-02-02'],
    ['2024-02-27', '2024-03-02'],
    ['2025-02-27', '2025-03-03'],
    ['2025-12-29', '2026-01-02'],
    ['2026-03-27', '2026-03-31'],
    ['2026-10-23', '2026-10-27'],
  ])('derives the bound across month/leap/year/DST boundaries: %s', (start, end) => {
    const raw = { startDate: new Date(`${start}T00:00:00Z`), endDate: null };
    expect(effectivePeriodEndDate(raw.startDate, raw.endDate).toISOString().slice(0, 10)).toBe(end);
    expect(effectivePeriodEndLocalDate(start, null)).toBe(end);
    expect(raw.endDate).toBeNull();
    expect(raw.startDate.toISOString().slice(0, 10)).toBe(start);
  });

  it.each([
    ['2026-10-22', false], // Future start is not current bleeding.
    ['2026-10-23', true],  // Day 1.
    ['2026-10-27', true],  // Day 5, across Berlin DST end.
    ['2026-10-28', false], // Day 6.
  ])('agrees in partner phase and tips on %s', (today, current) => {
    const period = { startDate: '2026-10-23', endDate: null };
    expect(estimatePartnerPhase(today, { startDate: new Date(`${period.startDate}T00:00:00Z`), endDate: null }, null))
      .toBe(current ? CyclePhase.MENSTRUATION : null);
    expect(applicableTipPhases(today, period, null, shared))
      .toEqual(current ? [CyclePhase.MENSTRUATION] : []);
    expect(applicableTipPhases(today, period, null, { ...shared, sharePeriodDates: false, sharePhase: false })).toEqual([]);
  });

  it('uses the profile local date on either side of midnight after a DST transition', () => {
    const startDate = new Date('2026-03-27T00:00:00Z');
    const day5 = localDateInTimeZone(new Date('2026-03-31T21:59:59Z'), 'Europe/Berlin');
    const day6 = localDateInTimeZone(new Date('2026-03-31T22:00:00Z'), 'Europe/Berlin');
    expect(day5).toBe('2026-03-31');
    expect(day6).toBe('2026-04-01');
    expect(estimatePartnerPhase(day5, { startDate, endDate: null }, null)).toBe(CyclePhase.MENSTRUATION);
    expect(estimatePartnerPhase(day6, { startDate, endDate: null }, null)).toBeNull();
  });

  it('retains shorter and eight-day manual ends, and clearing restores only the assumption', () => {
    const start = '2026-10-23';
    expect(effectivePeriodEndLocalDate(start, '2026-10-24')).toBe('2026-10-24');
    expect(effectivePeriodEndLocalDate(start, '2026-10-30')).toBe('2026-10-30');
    const period = { startDate: new Date(`${start}T00:00:00Z`), endDate: new Date('2026-10-30T00:00:00Z') };
    expect(estimatePartnerPhase('2026-10-29', period, null)).toBe(CyclePhase.MENSTRUATION);
    expect(applicableTipPhases('2026-10-29', { startDate: start, endDate: '2026-10-30' }, null, shared)).toEqual([CyclePhase.MENSTRUATION]);
    expect(effectivePeriodEndLocalDate(start, null)).toBe('2026-10-27');
  });

  it('never turns an assumed end into an observed duration or changes the raw input', () => {
    const periods = ['2026-01-01', '2026-01-29', '2026-02-26', '2026-03-26']
      .map(startDate => ({ startDate, endDate: null }));
    const before = JSON.stringify(periods);
    periods.forEach(period => effectivePeriodEndLocalDate(period.startDate, period.endDate));
    const result = calculatePrediction({ periods, referenceDate: '2026-03-26' });
    expect(result.statistics?.completedPeriodDurationCount).toBe(0);
    expect(result.statistics?.periodDurationDataBasis).toBe('default-prior');
    expect(JSON.stringify(periods)).toBe(before);
    const confirmed = calculatePrediction({
      periods: periods.map(period => ({ ...period, endDate: effectivePeriodEndLocalDate(period.startDate, null) })),
      referenceDate: '2026-03-26',
    });
    expect(confirmed.statistics?.completedPeriodDurationCount).toBe(4);
    expect(confirmed.statistics?.periodDurationDataBasis).toBe('observed-periods');
  });
});
