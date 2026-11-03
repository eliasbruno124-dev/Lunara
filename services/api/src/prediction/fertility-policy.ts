/** v2 eligible range maximum: 7 + 2 * variationDays, with variationDays <= 6. */
export const MAX_FERTILE_WINDOW_DAYS = 19;

/** Reject unsupported legacy/cached envelopes; never clip uncertainty. */
export function isSupportedFertileWindow(from: unknown, to: unknown): boolean {
  const first = epochDay(from);
  const last = epochDay(to);
  return first !== null && last !== null && last >= first &&
    last - first + 1 <= MAX_FERTILE_WINDOW_DAYS;
}

function epochDay(value: unknown): number | null {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const timestamp = Date.parse(`${value}T00:00:00.000Z`);
  if (!Number.isFinite(timestamp) || new Date(timestamp).toISOString().slice(0, 10) !== value) return null;
  return Math.floor(timestamp / 86_400_000);
}
