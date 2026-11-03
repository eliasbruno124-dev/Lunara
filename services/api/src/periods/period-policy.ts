/** An unconfirmed period is assumed to last five inclusive calendar days. */
export const ASSUMED_PERIOD_DURATION_DAYS = 5;

/** Prisma @db.Date values represent local dates at UTC midnight, not instants. */
export function addCalendarDays(date: Date, days: number): Date {
  const result = new Date(date.getTime());
  result.setUTCDate(result.getUTCDate() + days);
  return result;
}

/** Derived bound only: never persist it as an observed/manual end date. */
export function effectivePeriodEndDate(startDate: Date, endDate: Date | null): Date {
  return endDate ?? addCalendarDays(startDate, ASSUMED_PERIOD_DURATION_DAYS - 1);
}

export function effectivePeriodEndLocalDate(startDate: string, endDate?: string | null): string {
  return endDate ?? effectivePeriodEndDate(new Date(`${startDate}T00:00:00.000Z`), null)
    .toISOString().slice(0, 10);
}
