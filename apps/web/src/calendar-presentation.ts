import { parseIsoDate } from './date';
import type { Prediction } from './types';

export interface CalendarPredictionBand {
  startFrom: string;
  startTo: string;
  endTo: string;
}

/** Mirrors Android CalendarPredictionBand: display existing complete estimates,
 * never infer a duration or recover a withheld/invalid prediction locally. */
export function calendarPredictionBand(prediction: Prediction | null): CalendarPredictionBand | null {
  if (prediction?.available !== true) return null;
  const { predictedStartFrom: startFrom, predictedStartTo: startTo, predictedEndFrom: endFrom, predictedEndTo: endTo } = prediction;
  if (!startFrom || !startTo || !endFrom || !endTo) return null;
  try {
    for (const date of [startFrom, startTo, endFrom, endTo]) parseIsoDate(date);
  } catch { return null; }
  if (startTo < startFrom || endFrom < startFrom || endTo < endFrom || endTo < startTo) return null;
  return { startFrom, startTo, endTo };
}

export function calendarPredictionRole(date: string, band: CalendarPredictionBand | null): 'predicted' | 'predicted-duration' | null {
  if (!band || date < band.startFrom || date > band.endTo) return null;
  return date <= band.startTo ? 'predicted' : 'predicted-duration';
}
