import { describe, expect, it } from 'vitest';
import { calculateLocalPrediction } from '../local-prediction';
import type { PeriodEvent } from '../types';

function period(id: number, startDate: string, endDate: string): PeriodEvent {
  return {
    id: `123e4567-e89b-42d3-a456-4266141740${String(id).padStart(2, '0')}`,
    startDate,
    endDate,
  };
}

describe('local solo prediction', () => {
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
