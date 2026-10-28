import { parseIsoDate } from './date';
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
  return prediction.fertileWindowFrom <= prediction.fertileWindowTo;
}
