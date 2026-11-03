import { calculatePrediction } from './prediction.algorithm.js';
import { isSupportedFertileWindow } from './fertility-policy.js';

describe('supported fertility envelopes', () => {
  it.each([
    ['2026-01-01', '2026-01-19', true],
    ['2026-01-01', '2026-01-20', false],
    ['2026-01-01', '2026-02-02', false],
    ['2024-02-20', '2024-03-09', true],
    ['2026-03-20', '2026-04-07', true],
    ['2026-10-20', '2026-11-07', true],
    ['2026-01-02', '2026-01-01', false],
    ['2026-02-30', '2026-03-02', false],
    ['invalid', '2026-01-19', false],
    [null, '2026-01-19', false],
    ['2026-01-01', null, false],
  ])('validates inclusive dates %s through %s', (from, to, expected) => {
    expect(isSupportedFertileWindow(from, to)).toBe(expected);
  });

  it('retains the current v2 maximum and suppresses the historical problematic cycle vector', () => {
    const predict = (lengths: number[]) => {
      const periods = [{ startDate: '2026-01-01' }];
      for (const length of lengths) {
        const previous = periods[periods.length - 1]!.startDate;
        periods.push({ startDate: new Date(Date.parse(`${previous}T00:00:00Z`) + length * 86_400_000).toISOString().slice(0, 10) });
      }
      return calculatePrediction({ periods, referenceDate: periods[periods.length - 1]!.startDate, fertilityPredictionEnabled: true });
    };
    const maximum = predict([24, 27, 30]);
    expect(maximum.available).toBe(true);
    if (!maximum.available) throw new Error('Expected available synthetic prediction');
    expect(maximum.fertilityEstimate.available).toBe(true);
    expect((Date.parse(maximum.fertileWindowTo!) - Date.parse(maximum.fertileWindowFrom!)) / 86_400_000 + 1).toBe(19);
    expect(isSupportedFertileWindow(maximum.fertileWindowFrom, maximum.fertileWindowTo)).toBe(true);
    const legacyVector = predict([20, 28, 36]);
    expect(legacyVector.available).toBe(true);
    if (!legacyVector.available) throw new Error('Expected available synthetic cycle estimate');
    expect(legacyVector.fertilityEstimate).toMatchObject({ available: false, reason: 'UNRELIABLE_DATA' });
    expect(legacyVector.fertileWindowFrom).toBeNull();
    expect(legacyVector.fertileWindowTo).toBeNull();
  });
});
