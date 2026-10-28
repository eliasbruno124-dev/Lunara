import { parseIsoDate, todayIso } from './date';
import type { Prediction } from './types';

export type PredictionSummary =
  | { kind: 'current'; from: string; to: string }
  | { kind: 'stale' | 'pending' | 'unavailable' };

/** A summary projection only: never change cached calendar/fertility data or infer withheld dates. */
export function predictionSummary(prediction: Prediction | null, referenceDate: string, pendingChanges = false): PredictionSummary {
  if (!prediction?.available) return { kind: prediction?.reason === 'STALE_HISTORY' ? 'stale' : 'unavailable' };
  const from = prediction.predictedStartFrom;
  const to = prediction.predictedStartTo;
  if (!from || !to) return { kind: 'unavailable' };
  try {
    for (const date of [from, to, referenceDate]) parseIsoDate(date);
  } catch { return { kind: 'unavailable' }; }
  if (to < from) return { kind: 'unavailable' };
  if (to < referenceDate) return { kind: 'stale' };
  if (pendingChanges) return { kind: 'pending' };
  return { kind: 'current', from, to };
}

/** Current clients have no server profile timezone; never guess a partner's timezone. */
export function predictionReferenceDate(now = new Date(), knownTimeZone?: string | null): string {
  if (knownTimeZone) {
    try {
      const parts = new Intl.DateTimeFormat('en', { timeZone: knownTimeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
      const value = (type: string) => parts.find(part => part.type === type)?.value;
      return `${value('year')}-${value('month')}-${value('day')}`;
    } catch { /* Unavailable/invalid zone: use the same device day as local mode. */ }
  }
  return todayIso(now);
}

/** One foreground timer per local midnight, including DST's 23/25-hour days. */
export function millisecondsUntilNextPredictionDay(now = new Date()): number {
  const next = new Date(now);
  next.setHours(24, 0, 0, 25);
  return Math.max(25, next.getTime() - now.getTime());
}
