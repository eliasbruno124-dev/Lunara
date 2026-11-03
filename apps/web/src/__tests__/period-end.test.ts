import { describe, expect, it } from 'vitest';
import { effectivePeriodEnd, periodEndState, periodForDate, periodRangesOverlap } from '../period-end';
import { addDays } from '../date';

const open = Object.freeze({ id: 'synthetic', startDate: '2026-11-01', endDate: null });
describe('derived five-inclusive-day policy', () => {
  it.each([1, 4, 5])('is ongoing on day %i without painting unobserved future days', (day) => {
    const reference = addDays(open.startDate, day - 1);
    expect(periodEndState(open, reference)).toEqual({ endDate: '2026-11-05', displayEndDate: reference, ongoing: true, assumedComplete: false });
    expect(periodForDate([open], addDays(reference, 1), reference)).toBeNull();
  });
  it('is assumed complete on day6, retaining unknown raw end and unchanged metadata', () => {
    expect(periodEndState(open, '2026-11-06')).toEqual({ endDate: '2026-11-05', displayEndDate: '2026-11-05', ongoing: false, assumedComplete: true });
    expect(open).toEqual({ id: 'synthetic', startDate: '2026-11-01', endDate: null });
  });
  it.each([
    ['2026-12-29', '2027-01-02'], ['2028-02-27', '2028-03-02'], ['2026-02-27', '2026-03-03'],
    ['2026-03-27', '2026-03-31'], ['2026-10-23', '2026-10-27'],
  ])('counts calendar days across month/year/leap/DST: %s', (startDate, endDate) => {
    expect(effectivePeriodEnd({ startDate, endDate: null })).toBe(endDate);
    expect(periodEndState({ startDate, endDate: null }, endDate)?.ongoing).toBe(true);
    expect(periodEndState({ startDate, endDate: null }, addDays(endDate, 1))?.ongoing).toBe(false);
  });
  it.each([1, 5, 8, 16])('keeps a manual %i-day end authoritative', (length) => {
    const manual = { ...open, endDate: addDays(open.startDate, length - 1) };
    expect(effectivePeriodEnd(manual)).toBe(manual.endDate);
    expect(periodEndState(manual, '2026-12-01')).toMatchObject({ ongoing: false, assumedComplete: false, displayEndDate: manual.endDate });
    expect(periodEndState({ ...manual, endDate: null }, '2026-12-01')?.displayEndDate).toBe('2026-11-05');
  });
  it('uses both effective ends, accepts day6 and rejects day5, including older candidates', () => {
    for (const startDate of ['2026-10-28', '2026-11-05']) {
      const other = { startDate, endDate: null };
      expect(periodRangesOverlap(open, other)).toBe(true);
      expect(periodRangesOverlap(other, open)).toBe(true);
    }
    for (const startDate of ['2026-10-27', '2026-11-06']) {
      const other = { startDate, endDate: null };
      expect(periodRangesOverlap(open, other)).toBe(false);
      expect(periodRangesOverlap(other, open)).toBe(false);
    }
    expect(periodRangesOverlap({ ...open, endDate: '2026-11-08' }, { startDate: '2026-11-06', endDate: null })).toBe(true);
  });
  it('ignores malformed/deleted/future starts and never paints future bleeding', () => {
    for (const value of [{ ...open, startDate: '2026-02-30' }, { ...open, endDate: '2026-10-31' }, { ...open, endDate: '' }, { ...open, deletedAt: '2026-11-02' }]) {
      expect(effectivePeriodEnd(value)).toBeNull();
      expect(periodForDate([value], '2026-11-01', '2026-11-09')).toBeNull();
    }
    expect(periodEndState(open, 'bad-date')).toBeNull();
    expect(periodEndState(open, '2026-10-31')).toBeNull();
    expect(periodForDate([{ ...open, endDate: '2026-11-08' }], '2026-11-08', '2026-11-02')).toBeNull();
    expect(periodForDate([open], '2026-11-06', '2026-11-09')).toBeNull();
    expect(periodForDate([open], '2026-11-05', '2026-11-09')).toBe(open);
  });
});
