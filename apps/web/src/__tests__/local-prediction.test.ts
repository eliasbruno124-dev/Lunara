import { describe, expect, it } from 'vitest';
import { calculateLocalPrediction, LOCAL_PREDICTION_ALGORITHM_VERSION } from '../local-prediction';
import { defaultPortableExtras } from '../backup';
import { addDays, daysBetween } from '../date';
import type { PeriodEvent } from '../types';

function period(id: number, startDate: string, endDate: string): PeriodEvent {
  return {
    id: `123e4567-e89b-42d3-a456-4266141740${String(id).padStart(2, '0')}`,
    startDate,
    endDate,
  };
}

describe('local solo prediction', () => {
  const profile = { ...defaultPortableExtras().profile, fertilityPredictionEnabled: true };
  const history = (intervals: number[], durations: number[] = []) => {
    let start = '2026-01-01';
    return [0, ...intervals].map((interval, index) => {
      start = addDays(start, interval);
      return { id: `synthetic-${index}`, startDate: start, endDate: durations[index] ? addDays(start, durations[index]! - 1) : null };
    });
  };
  const duration = (prediction: ReturnType<typeof calculateLocalPrediction>) => daysBetween(prediction.predictedStartFrom!, prediction.predictedEndFrom!) + 1;

  it('matches the canonical medium-confidence vector and half-width metadata', () => {
    const prediction = calculateLocalPrediction(history([28, 28, 28]), '2026-03-26', profile);
    expect(prediction).toMatchObject({
      available: true, predictedStartFrom: '2026-04-20', predictedStartTo: '2026-04-26',
      predictedEndFrom: '2026-04-24', predictedEndTo: '2026-04-30',
      fertileWindowFrom: '2026-04-01', fertileWindowTo: '2026-04-13',
      confidence: 'MEDIUM', basedOnCycleCount: 3, variationDays: 3, fertilityEstimate: true,
      algorithmVersion: 'lunara-robust-v3',
    });
    expect(prediction.variationDays).toBe(daysBetween(prediction.predictedStartFrom!, prediction.predictedStartTo!) / 2);
  });

  it('keeps a valid observed 16-day period rather than applying the profile-only limit', () => {
    const prediction = calculateLocalPrediction(history([28, 28, 28], [16, 16, 16, 16]), '2026-04-10', { ...profile, typicalPeriodLength: 7 });
    expect(duration(prediction)).toBe(16);
    expect(prediction.predictedEndTo).toBe('2026-05-11');
  });
  it('never treats an assumed five-day end as an observed duration sample', () => {
    const records = history([28, 28, 28], [8]);
    const result = calculateLocalPrediction(records, '2026-04-10', { ...profile, typicalPeriodLength: 3 });
    expect(duration(result)).toBe(8);
    expect(records.slice(1).map((item) => item.endDate)).toEqual([null, null, null]);
    expect(result.basedOnCycleCount).toBe(3);
  });

  it('uses the most recent 12 completed observations regardless of input order', () => {
    const periods = history(Array(24).fill(28), [...Array(13).fill(2), ...Array(12).fill(8)]);
    const today = periods.at(-1)!.startDate;
    const expected = calculateLocalPrediction(periods, today, profile);
    expect(duration(expected)).toBe(8);
    expect(calculateLocalPrediction([...periods].reverse(), today, profile)).toEqual(expected);
    const shuffled = [...periods.filter((_, index) => index % 2 === 0), ...periods.filter((_, index) => index % 2 === 1).reverse()];
    expect(calculateLocalPrediction(shuffled, today, profile)).toEqual(expected);
    expect(periods[0]!.startDate).toBe('2026-01-01');
  });

  it('excludes oversized durations after selecting the most recent 12 completed observations', () => {
    const periods = history(Array(13).fill(60), [2, 2, ...Array(12).fill(40)]);
    expect(duration(calculateLocalPrediction(periods, periods.at(-1)!.startDate, profile))).toBe(5);
    expect(duration(calculateLocalPrediction(periods, periods.at(-1)!.startDate, { ...profile, typicalPeriodLength: 9 }))).toBe(9);
  });

  it('ignores deleted periods for both starts and durations without mutating the source', () => {
    const active = history([28, 28, 28], [2, 2, 2, 2]);
    const deleted = history([28, 28, 28], [14, 14, 14, 14]).map(item => ({ ...item, id: `deleted-${item.id}`, deletedAt: '2026-04-01T00:00:00Z' }));
    const frozen = Object.freeze([...active, ...deleted, { id: 'deleted-future', startDate: '2030-01-01', endDate: null, deletedAt: '2026-04-01T00:00:00Z' }]);
    expect(calculateLocalPrediction([...frozen], '2026-03-26', profile)).toEqual(calculateLocalPrediction(active, '2026-03-26', profile));
    expect(duration(calculateLocalPrediction([...frozen], '2026-03-26', profile))).toBe(2);
  });

  it.each([0, 14, 91, 28.5, NaN, Infinity])('rejects unsupported cycle fallback %s', typicalCycleLength => {
    expect(calculateLocalPrediction(history([]), '2026-01-02', { ...profile, typicalCycleLength })).toMatchObject({ available: false, reason: 'INSUFFICIENT_DATA' });
  });
  it.each([15, 90])('accepts supported cycle fallback boundary %s', typicalCycleLength => {
    const prediction = calculateLocalPrediction(history([]), '2026-01-02', { ...profile, typicalCycleLength });
    expect(prediction).toMatchObject({ available: true, confidence: 'LOW', variationDays: 4, fertilityEstimate: false });
    expect(prediction.predictedStartFrom).toBe(addDays('2026-01-01', typicalCycleLength - 4));
  });
  it.each([0, 15, 5.5, NaN, Infinity])('ignores unsupported period fallback %s', typicalPeriodLength => {
    expect(duration(calculateLocalPrediction(history([28]), '2026-01-29', { ...profile, typicalPeriodLength }))).toBe(5);
  });
  it.each([1, 14])('accepts supported period fallback boundary %s', typicalPeriodLength => {
    expect(duration(calculateLocalPrediction(history([28]), '2026-01-29', { ...profile, typicalPeriodLength }))).toBe(typicalPeriodLength);
  });
  it.each([7, 21, 14.5, NaN, Infinity])('ignores unsupported luteal fallback %s', lutealLength => {
    const records = history([28, 28, 28]);
    expect(calculateLocalPrediction(records, '2026-03-26', { ...profile, lutealLength })).toEqual(calculateLocalPrediction(records, '2026-03-26', { ...profile, lutealLength: 14 }));
  });
  it.each([8, 20])('accepts supported luteal boundary %s', lutealLength => {
    const prediction = calculateLocalPrediction(history([28, 28, 28]), '2026-03-26', { ...profile, lutealLength });
    expect(prediction.fertileWindowFrom).toBe(addDays('2026-04-23', -lutealLength - 8));
    expect(prediction.fertileWindowTo).toBe(addDays('2026-04-23', -lutealLength + 4));
  });

  it.each([
    { intervals: [28], confidence: 'LOW', halfWidth: 4, fertile: false },
    { intervals: [28, 28, 28], confidence: 'MEDIUM', halfWidth: 3, fertile: true },
    { intervals: [28, 28, 28, 28, 28, 28], confidence: 'HIGH', halfWidth: 2, fertile: true },
    { intervals: [28, 28, 28, 28, 28, 28, 365], confidence: 'MEDIUM', halfWidth: 2, fertile: false },
  ])('preserves confidence and fertility safety for $intervals', ({ intervals, confidence, halfWidth, fertile }) => {
    const periods = history(intervals);
    const result = calculateLocalPrediction(periods, periods.at(-1)!.startDate, profile);
    expect(result).toMatchObject({ available: true, confidence, variationDays: halfWidth, fertilityEstimate: fertile });
    const disabled = calculateLocalPrediction(periods, periods.at(-1)!.startDate, { ...profile, fertilityPredictionEnabled: false });
    expect(disabled.fertilityEstimate).toBe(false);
    expect(disabled.fertileWindowFrom).toBeNull();
    expect(disabled.predictedStartFrom).toBe(result.predictedStartFrom);
  });

  it('widens a variable history with the v3 scaled MAD while preserving its center', () => {
    const records = history([26, 28, 30]);
    const lastStart = records.at(-1)!.startDate;
    const result = calculateLocalPrediction(records, lastStart, profile);
    expect(result).toMatchObject({
      available: true,
      algorithmVersion: 'lunara-robust-v3',
      confidence: 'MEDIUM',
      variationDays: 5,
      predictedStartFrom: addDays(lastStart, 28 - 5),
      predictedStartTo: addDays(lastStart, 28 + 5),
    });
  });

  it('keeps a fourteen-day half-width available but rejects fifteen days', () => {
    const accepted = history([22, 22, 22, 35, 35, 35]);
    const rejected = history([21, 21, 21, 35, 35, 35]);
    expect(calculateLocalPrediction(accepted, accepted.at(-1)!.startDate, profile)).toMatchObject({
      available: true,
      confidence: 'LOW',
      variationDays: 14,
      fertilityEstimate: false,
    });
    const result = calculateLocalPrediction(rejected, rejected.at(-1)!.startDate, profile);
    expect(result).toMatchObject({
      available: false,
      confidence: 'NONE',
      reason: 'UNRELIABLE_RANGE',
      basedOnCycleCount: 6,
      algorithmVersion: 'lunara-robust-v3',
    });
    expect(result.predictedStartFrom).toBeUndefined();
    expect(result.predictedStartTo).toBeUndefined();
    expect(result.fertileWindowFrom).toBeUndefined();
    expect(result.fertileWindowTo).toBeUndefined();
  });

  it('does not display a next-start window reaching before the latest recorded start, even for stale inputs', () => {
    const records = history([90, 15]);
    for (const referenceDate of [records.at(-1)!.startDate, '2030-01-01']) {
      expect(calculateLocalPrediction(records, referenceDate, profile)).toMatchObject({
        available: false,
        confidence: 'NONE',
        reason: 'UNRELIABLE_RANGE',
        basedOnCycleCount: 2,
      });
    }
  });

  it.each([
    { intervals: [28, 28, 28, 28, 28, 28, 365], confidence: 'MEDIUM' },
    { intervals: [28, 28, 28, 28, 28, 28, 365, 28], confidence: 'MEDIUM' },
    { intervals: [28, 28, 28, 28, 28, 28, 365, 28, 28], confidence: 'HIGH' },
    { intervals: [28, 28, 28, 28, 28, 28, 1], confidence: 'HIGH' },
    { intervals: [15, 15, 15, 15, 50, 15, 365], confidence: 'LOW' },
    { intervals: [365, ...Array<number>(12).fill(28)], confidence: 'HIGH' },
  ])('caps only high confidence after one of the two most recent raw intervals was a long gap: $intervals', ({ intervals, confidence }) => {
    const records = history(intervals);
    expect(calculateLocalPrediction(records, records.at(-1)!.startDate, profile)).toMatchObject({ available: true, confidence });
  });

  it('expires strictly after the latest predicted start, including across DST dates', () => {
    const periods = history([28, 28, 28]);
    expect(calculateLocalPrediction(periods, '2026-04-26', profile).available).toBe(true);
    expect(calculateLocalPrediction(periods, '2026-04-27', profile)).toMatchObject({ available: false, reason: 'STALE_HISTORY' });
    expect(calculateLocalPrediction(periods, '2026-03-29', { ...profile, predictionPaused: true })).toMatchObject({ available: false, reason: 'PREDICTION_PAUSED' });
  });
  it('uses recorded cycle intervals instead of assuming 28 days', () => {
    const prediction = calculateLocalPrediction([
      period(1, '2026-04-01', '2026-04-05'),
      period(2, '2026-05-01', '2026-05-05'),
      period(3, '2026-05-31', '2026-06-04'),
      period(4, '2026-06-30', '2026-07-04'),
    ], '2026-07-04');
    expect(prediction.available).toBe(true);
    expect(prediction.predictedStartFrom).toBe('2026-07-27');
    expect(prediction.predictedStartTo).toBe('2026-08-02');
    expect(prediction.basedOnCycleCount).toBe(3);
  });

  it('is honest when there is no complete cycle interval', () => {
    expect(calculateLocalPrediction([period(1, '2026-07-01', '2026-07-05')], '2026-07-05')).toEqual({
      available: false,
      confidence: 'NONE',
      reason: 'INSUFFICIENT_DATA',
      algorithmVersion: LOCAL_PREDICTION_ALGORITHM_VERSION,
    });
  });

  it('suppresses ordinary estimates in a special mode', () => {
    expect(calculateLocalPrediction([
      period(1, '2026-07-01', '2026-07-05'),
      period(2, '2026-07-30', '2026-08-03'),
    ], '2026-08-03', {
      fertilityPredictionEnabled: false,
      typicalCycleLength: null,
      typicalPeriodLength: null,
      lutealLength: null,
      predictionPaused: false,
      specialMode: 'PREGNANCY',
    })).toMatchObject({ available: false, reason: 'SPECIAL_MODE' });
  });
});
