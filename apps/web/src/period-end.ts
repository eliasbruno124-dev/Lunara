import { addDays, parseIsoDate } from './date';

export interface PeriodDates {
  startDate: string;
  endDate: string | null;
  deletedAt?: string | null;
}

/** A derived bound, never an observed end or a value to persist/export. */
export function effectivePeriodEnd(period: PeriodDates): string | null {
  if (period.deletedAt) return null;
  try {
    parseIsoDate(period.startDate);
    const end = period.endDate === null ? addDays(period.startDate, 4) : period.endDate;
    parseIsoDate(end);
    return end >= period.startDate ? end : null;
  } catch { return null; }
}

export function periodEndState(period: PeriodDates, referenceDate: string) {
  const endDate = effectivePeriodEnd(period);
  try { parseIsoDate(referenceDate); } catch { return null; }
  if (!endDate) return null;
  if (period.startDate > referenceDate) return null;
  const unconfirmed = period.endDate === null;
  return {
    endDate,
    // A missing end is not evidence of future bleeding.
    displayEndDate: referenceDate < endDate ? referenceDate : endDate,
    ongoing: unconfirmed && period.startDate <= referenceDate && referenceDate <= endDate,
    assumedComplete: unconfirmed && referenceDate > endDate,
  };
}

export function periodRangesOverlap(left: PeriodDates, right: PeriodDates): boolean {
  const leftEnd = effectivePeriodEnd(left), rightEnd = effectivePeriodEnd(right);
  return Boolean(leftEnd && rightEnd && left.startDate <= rightEnd && right.startDate <= leftEnd);
}

export function periodForDate<T extends PeriodDates>(periods: readonly T[], date: string, referenceDate: string): T | null {
  try { parseIsoDate(date); } catch { return null; }
  let selected: T | null = null;
  for (const period of periods) {
    const state = periodEndState(period, referenceDate);
    if (state && period.startDate <= date && date <= state.displayEndDate && (!selected || selected.startDate < period.startDate)) selected = period;
  }
  return selected;
}
