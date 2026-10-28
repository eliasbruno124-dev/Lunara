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

/** One estimated category covers the complete possible band, without changing its boundaries. */
export function calendarPredictionContains(date: string, band: CalendarPredictionBand | null): boolean {
  return band !== null && date >= band.startFrom && date <= band.endTo;
}
