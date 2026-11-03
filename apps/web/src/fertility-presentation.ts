import { daysBetween, parseIsoDate } from './date';
import type { Prediction } from './types';

/** Fertility has its own permission/availability; the period forecast may be withheld. */
export function hasFertileEstimate(prediction: Prediction | null | undefined): boolean {
  if (!prediction?.fertileWindowFrom || !prediction.fertileWindowTo) return false;
  const safety = prediction.fertilityEstimate;
  const allowed = typeof safety === 'boolean' ? safety && prediction.available === true
    : safety?.enabled === true && safety.available === true && safety.estimated === true &&
      safety.notContraception === true && safety.doesNotConfirmOvulation === true && safety.reason === 'AVAILABLE';
  if (!allowed) return false;
  try { parseIsoDate(prediction.fertileWindowFrom); parseIsoDate(prediction.fertileWindowTo); } catch { return false; }
  // v2's widest eligible interval is 7 + 2*6 = 19 inclusive days. Reject legacy
  // broad intervals rather than clipping them into apparently precise estimates.
  const width = daysBetween(prediction.fertileWindowFrom, prediction.fertileWindowTo) + 1;
  return width >= 1 && width <= 19;
}

/** Today summaries expire independently; valid historical calendar bands remain. */
export function hasCurrentFertileEstimate(prediction: Prediction | null | undefined, referenceDate: string, pendingChanges = false): boolean {
  if (pendingChanges || !hasFertileEstimate(prediction)) return false;
  try { parseIsoDate(referenceDate); } catch { return false; }
  return prediction!.fertileWindowTo! >= referenceDate;
}
