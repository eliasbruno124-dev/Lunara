import {
  calculatePrediction,
  HistoricalPeriod,
  PREDICTION_ALGORITHM_VERSION,
} from './prediction.algorithm.js';

const DAY_MS = 86_400_000;

function periodsForCycleLengths(
  lengths: number[],
  firstStart = '2024-01-01',
): HistoricalPeriod[] {
  let day = Date.parse(`${firstStart}T00:00:00.000Z`) / DAY_MS;
  const starts = [day];
  for (const length of lengths) {
    day += length;
    starts.push(day);
  }
  return starts.map((value) => ({
    startDate: new Date(value * DAY_MS).toISOString().slice(0, 10),
  }));
}

function lastStart(periods: HistoricalPeriod[]): string {
  return [...periods].map((period) => period.startDate).sort().at(-1) as string;
}

describe('calculatePrediction v3', () => {
  it('uses observed cycles rather than a hard-coded 28-day cycle', () => {
    const result = calculatePrediction(
      {
        periods: [
          { startDate: '2026-01-01', endDate: '2026-01-05' },
          { startDate: '2026-01-31', endDate: '2026-02-04' },
          { startDate: '2026-03-02', endDate: '2026-03-06' },
          { startDate: '2026-04-01', endDate: '2026-04-05' },
          { startDate: '2026-05-01', endDate: '2026-05-05' },
        ],
        referenceDate: '2026-05-02',
      },
      new Date('2026-05-02T00:00:00.000Z'),
    );
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.algorithmVersion).toBe(PREDICTION_ALGORITHM_VERSION);
    expect(result.algorithmVersion).toBe('lunara-robust-v3');
    expect(result.typicalCycleLength).toBe(30);
    expect(result.predictedStartFrom).toBe('2026-05-28');
    expect(result.predictedStartTo).toBe('2026-06-03');
    expect(result.statistics.consideredCycleCount).toBe(4);
    expect(result.confidence).toBe('MEDIUM');
  });

  it('gives recent cycles more influence for the same observed multiset', () => {
    const recentLong = periodsForCycleLengths([26, 26, 26, 34, 34, 34]);
    const recentShort = periodsForCycleLengths([34, 34, 34, 26, 26, 26]);
    const longResult = calculatePrediction({
      periods: recentLong,
      referenceDate: lastStart(recentLong),
    });
    const shortResult = calculatePrediction({
      periods: recentShort,
      referenceDate: lastStart(recentShort),
    });
    expect(longResult.available && longResult.typicalCycleLength).toBe(34);
    expect(shortResult.available && shortResult.typicalCycleLength).toBe(26);
  });

  it('uses only the twelve most recent observed intervals', () => {
    const periods = periodsForCycleLengths([40, ...Array<number>(12).fill(28)]);
    const result = calculatePrediction({ periods, referenceDate: lastStart(periods) });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.basedOnCycleCount).toBe(12);
    expect(result.statistics.allObservedCycleLengths).toEqual(Array<number>(12).fill(28));
    expect(result.typicalCycleLength).toBe(28);
    expect(result.confidence).toBe('HIGH');
  });

  it('retains a plausible outlier but does not let it dominate the robust center', () => {
    const periods = periodsForCycleLengths([29, 29, 49, 29, 29, 29]);
    const result = calculatePrediction({ periods, referenceDate: lastStart(periods) });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.typicalCycleLength).toBe(29);
    expect(result.statistics.allObservedCycleLengths).toContain(49);
    expect(result.statistics.modelCycleLengths).toContain(49);
    expect(result.variationDays).toBe(2);
    expect(result.confidence).toBe('MEDIUM');
  });

  it('retains implausible intervals in statistics but excludes them from model and fertility', () => {
    const periods = periodsForCycleLengths([28, 28, 365, 28]);
    const result = calculatePrediction({
      periods,
      referenceDate: lastStart(periods),
      fertilityPredictionEnabled: true,
    });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.typicalCycleLength).toBe(28);
    expect(result.statistics.allObservedCycleLengths).toEqual([28, 28, 365, 28]);
    expect(result.statistics.modelCycleLengths).toEqual([28, 28, 28]);
    expect(result.statistics.excludedCycleLengths).toEqual([365]);
    expect(result.statistics.shortestCycle).toBe(28);
    expect(result.statistics.longestCycle).toBe(365);
    expect(result.variationDays).toBe(3);
    expect(result.predictedStartFrom).toBe('2025-04-19');
    expect(result.predictedStartTo).toBe('2025-04-25');
    expect(result.fertileWindowFrom).toBeNull();
    expect(result.fertilityEstimate).toMatchObject({
      enabled: true,
      available: false,
      reason: 'UNRELIABLE_DATA',
      basedOnCycleCount: 3,
    });
  });

  it('does not create a model or fertility estimate from one-day intervals', () => {
    const periods = periodsForCycleLengths([1, 1, 1]);
    const result = calculatePrediction({
      periods,
      referenceDate: lastStart(periods),
      fertilityPredictionEnabled: true,
    });
    expect(result).toMatchObject({
      available: false,
      reason: 'INSUFFICIENT_DATA',
      basedOnCycleCount: 0,
      statistics: {
        allObservedCycleLengths: [1, 1, 1],
        modelCycleLengths: [],
        excludedCycleLengths: [1, 1, 1],
      },
    });
  });

  it('marks fewer than three usable complete cycles as low confidence and broad', () => {
    const periods = periodsForCycleLengths([31]);
    const result = calculatePrediction({ periods, referenceDate: lastStart(periods) });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.confidence).toBe('LOW');
    expect(result.variationDays).toBeGreaterThanOrEqual(4);
  });

  it('uses a minimum three-day half-width for three to five cycles', () => {
    const periods = periodsForCycleLengths([28, 28, 28]);
    const result = calculatePrediction({ periods, referenceDate: lastStart(periods) });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.variationDays).toBe(3);
    expect(result.confidence).toBe('MEDIUM');
  });

  it('widens the robust spread without changing the model center', () => {
    const periods = periodsForCycleLengths([24, 26, 28, 28, 30, 32]);
    const result = calculatePrediction({ periods, referenceDate: lastStart(periods) });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.typicalCycleLength).toBe(28);
    expect(result.statistics.medianAbsoluteDeviation).toBe(2);
    expect(result.variationDays).toBe(5);
    expect(result.confidence).toBe('MEDIUM');
  });

  it('keeps a fourteen-day half-width available with two observed intervals', () => {
    const periods = periodsForCycleLengths([28, 40]);
    const result = calculatePrediction({ periods, referenceDate: lastStart(periods) });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.typicalCycleLength).toBe(40);
    expect(result.variationDays).toBe(14);
    expect(result.confidence).toBe('LOW');
    expect(result.fertilityEstimate.available).toBe(false);
  });

  it('withholds a fifteen-day half-width rather than presenting it as the next period', () => {
    const periods = periodsForCycleLengths([28, 41]);
    const result = calculatePrediction({
      periods,
      referenceDate: lastStart(periods),
      fertilityPredictionEnabled: true,
    });
    expect(result).toMatchObject({
      available: false,
      confidence: 'UNAVAILABLE',
      algorithmVersion: 'lunara-robust-v3',
      reason: 'UNRELIABLE_RANGE',
      basedOnCycleCount: 2,
      statistics: {
        modelCycleLengths: [28, 41],
        variationDays: 15,
      },
    });
    expect(result).not.toHaveProperty('predictedStartFrom');
    expect(result).not.toHaveProperty('fertileWindowFrom');
    expect(result).not.toHaveProperty('fertileWindowTo');
  });

  it.each(['2026-04-16', '2027-04-16'])(
    'withholds an unusable range before the last start, even at reference date %s',
    (referenceDate) => {
      const result = calculatePrediction({
        periods: [
          { startDate: '2026-01-01' },
          { startDate: '2026-04-01' },
          { startDate: '2026-04-16' },
        ],
        referenceDate,
        fertilityPredictionEnabled: true,
      });
      expect(result).toMatchObject({
        available: false,
        reason: 'UNRELIABLE_RANGE',
        statistics: { modelCycleLengths: [90, 15], variationDays: 74 },
      });
      expect(result).not.toHaveProperty('predictedStartFrom');
      expect(result).not.toHaveProperty('fertileWindowFrom');
    },
  );

  it.each([
    { name: 'newest', lengths: [...Array<number>(6).fill(28), 365], expected: 'MEDIUM' },
    { name: 'second newest', lengths: [...Array<number>(6).fill(28), 365, 28], expected: 'MEDIUM' },
    { name: 'third newest', lengths: [...Array<number>(6).fill(28), 365, 28, 28], expected: 'HIGH' },
    { name: 'outside the statistics window', lengths: [365, ...Array<number>(12).fill(28)], expected: 'HIGH' },
    { name: 'recent short excluded interval', lengths: [...Array<number>(6).fill(28), 1], expected: 'HIGH' },
    { name: 'ninety-day usable intervals', lengths: Array<number>(6).fill(90), expected: 'HIGH' },
  ])('caps high confidence only for a recent excluded long gap: $name', ({ lengths, expected }) => {
    const periods = periodsForCycleLengths(lengths);
    const result = calculatePrediction({ periods, referenceDate: lastStart(periods) });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.confidence).toBe(expected);
    expect(result.variationDays).toBe(2);
  });

  it('does not upgrade low confidence after a recent long gap', () => {
    const periods = periodsForCycleLengths([24, 28, 32, 365]);
    const result = calculatePrediction({ periods, referenceDate: lastStart(periods) });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.confidence).toBe('LOW');
  });

  it('uses an explicit profile fallback only when no usable complete cycle exists', () => {
    const result = calculatePrediction({
      periods: [{ startDate: '2026-08-01' }],
      referenceDate: '2026-08-01',
      typicalCycleLength: 32,
      typicalPeriodLength: 6,
    });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.typicalCycleLength).toBe(32);
    expect(result.basedOnCycleCount).toBe(0);
    expect(result.explanation.dataBasis).toBe('profile-fallback');
    expect(result.statistics.periodDurationDataBasis).toBe('profile-fallback');
  });

  it('labels the conservative five-day duration prior instead of presenting it as observed', () => {
    const periods = periodsForCycleLengths([28, 28]);
    const result = calculatePrediction({ periods, referenceDate: lastStart(periods) });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.typicalPeriodLength).toBe(5);
    expect(result.statistics.completedPeriodDurationCount).toBe(0);
    expect(result.statistics.periodDurationDataBasis).toBe('default-prior');
  });

  it('retains an implausibly long period duration while excluding it from the duration model', () => {
    const result = calculatePrediction({
      periods: [{ startDate: '2026-01-01', endDate: '2026-02-04' }],
      referenceDate: '2026-01-01',
      typicalCycleLength: 40,
    });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.typicalPeriodLength).toBe(5);
    expect(result.statistics.allObservedPeriodDurations).toEqual([35]);
    expect(result.statistics.excludedPeriodDurations).toEqual([35]);
    expect(result.statistics.completedPeriodDurationCount).toBe(0);
    expect(result.statistics.periodDurationDataBasis).toBe('default-prior');
  });

  it('calculates period duration from the latest records independent of input order', () => {
    const periods = periodsForCycleLengths(Array<number>(12).fill(28)).map((period, index) => ({
      ...period,
      endDate: new Date(
        Date.parse(`${period.startDate}T00:00:00.000Z`) +
          ((index < 6 ? 1 : 14) - 1) * DAY_MS,
      ).toISOString().slice(0, 10),
    }));
    const chronological = calculatePrediction({
      periods,
      referenceDate: lastStart(periods),
    });
    const reversed = calculatePrediction({
      periods: [...periods].reverse(),
      referenceDate: lastStart(periods),
    });
    expect(chronological.available && chronological.typicalPeriodLength).toBe(14);
    expect(reversed.available && reversed.typicalPeriodLength).toBe(14);
    if (chronological.available) {
      expect(chronological.statistics.completedPeriodDurationCount).toBe(12);
      expect(chronological.statistics.periodDurationDataBasis).toBe('observed-periods');
    }
  });

  it('fails closed when the entire predicted start interval is in the past', () => {
    const periods = periodsForCycleLengths([28, 28, 28], '2025-01-01');
    const result = calculatePrediction({ periods, referenceDate: '2026-08-25' });
    expect(result).toMatchObject({
      available: false,
      reason: 'STALE_HISTORY',
      basedOnCycleCount: 3,
      statistics: { modelCycleLengths: [28, 28, 28] },
    });
  });

  it('keeps a prediction available while today remains inside its possible start interval', () => {
    const periods = periodsForCycleLengths([28, 28, 28]);
    const provisional = calculatePrediction({ periods, referenceDate: lastStart(periods) });
    expect(provisional.available).toBe(true);
    if (!provisional.available) return;
    const result = calculatePrediction({
      periods,
      referenceDate: provisional.predictedStartTo,
    });
    expect(result.available).toBe(true);
  });

  it.each([
    'PREGNANCY',
    'POSTPARTUM',
    'HORMONAL_CONTRACEPTION',
    'MENOPAUSE_TRANSITION',
    'PAUSED',
  ])('suppresses ordinary predictions in %s mode', (specialMode) => {
    expect(
      calculatePrediction({
        periods: periodsForCycleLengths([29]),
        referenceDate: '2024-01-30',
        specialMode,
      }),
    ).toMatchObject({ available: false, reason: 'SPECIAL_MODE' });
  });

  it('suppresses prediction when the independent pause flag is enabled', () => {
    expect(
      calculatePrediction({
        periods: periodsForCycleLengths([29]),
        predictionPaused: true,
      }),
    ).toMatchObject({ available: false, reason: 'PREDICTION_PAUSED' });
  });

  it('only estimates a fertile window after explicit enablement and reliable data', () => {
    const periods = periodsForCycleLengths([29, 29, 29]);
    const result = calculatePrediction({
      periods,
      referenceDate: lastStart(periods),
      fertilityPredictionEnabled: true,
    });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.fertileWindowFrom).not.toBeNull();
    expect(result.fertileWindowTo).not.toBeNull();
    expect(result.fertilityEstimate).toEqual({
      enabled: true,
      available: true,
      estimated: true,
      reason: 'AVAILABLE',
      basedOnCycleCount: 3,
      notContraception: true,
      doesNotConfirmOvulation: true,
    });
  });

  it('matches the canonical cross-client prediction vector', () => {
    const result = calculatePrediction(
      {
        periods: [
          { startDate: '2026-01-01' },
          { startDate: '2026-01-29' },
          { startDate: '2026-02-26' },
          { startDate: '2026-03-26' },
        ],
        referenceDate: '2026-03-26',
        fertilityPredictionEnabled: true,
      },
      new Date('2026-03-26T12:00:00.000Z'),
    );
    expect(result).toMatchObject({
      available: true,
      predictedStartFrom: '2026-04-20',
      predictedStartTo: '2026-04-26',
      predictedEndFrom: '2026-04-24',
      predictedEndTo: '2026-04-30',
      fertileWindowFrom: '2026-04-01',
      fertileWindowTo: '2026-04-13',
      confidence: 'MEDIUM',
      basedOnCycleCount: 3,
      algorithmVersion: 'lunara-robust-v3',
      typicalCycleLength: 28,
      typicalPeriodLength: 5,
      variationDays: 3,
      statistics: {
        allObservedCycleLengths: [28, 28, 28],
        modelCycleLengths: [28, 28, 28],
        excludedCycleLengths: [],
        periodDurationDataBasis: 'default-prior',
      },
      fertilityEstimate: {
        enabled: true,
        available: true,
        reason: 'AVAILABLE',
      },
    });
  });

  it('keeps fertility unavailable when disabled even with sufficient data', () => {
    const periods = periodsForCycleLengths([29, 29, 29]);
    const result = calculatePrediction({ periods, referenceDate: lastStart(periods) });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.fertilityEstimate).toMatchObject({
      enabled: false,
      available: false,
      reason: 'DISABLED',
    });
  });

  it('keeps fertility unavailable with fewer than three usable cycles', () => {
    const periods = periodsForCycleLengths([29, 29]);
    const result = calculatePrediction({
      periods,
      referenceDate: lastStart(periods),
      fertilityPredictionEnabled: true,
    });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.fertilityEstimate).toMatchObject({
      enabled: true,
      available: false,
      reason: 'INSUFFICIENT_DATA',
      basedOnCycleCount: 2,
    });
  });

  it('fails fertility closed for highly irregular but technically usable history', () => {
    const periods = periodsForCycleLengths([15, 90, 15]);
    const result = calculatePrediction({
      periods,
      referenceDate: lastStart(periods),
      fertilityPredictionEnabled: true,
    });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.confidence).toBe('LOW');
    expect(result.fertilityEstimate).toMatchObject({
      available: false,
      reason: 'UNRELIABLE_DATA',
    });
  });

  it('accepts a real leap day and rejects an invalid local calendar date', () => {
    const leap = calculatePrediction({
      periods: [{ startDate: '2024-02-01' }, { startDate: '2024-02-29' }],
      referenceDate: '2024-02-29',
    });
    expect(leap.available).toBe(true);
    expect(() =>
      calculatePrediction({
        periods: [{ startDate: '2025-02-29' }],
        referenceDate: '2025-02-28',
      }),
    ).toThrow('Invalid local date');
  });

  it('returns explicit unavailable reasons for an empty history and missing fallback', () => {
    expect(calculatePrediction({ periods: [] })).toMatchObject({
      available: false,
      reason: 'NO_PERIOD_START',
    });
    expect(
      calculatePrediction({ periods: [{ startDate: '2026-08-01' }], referenceDate: '2026-08-01' }),
    ).toMatchObject({ available: false, reason: 'INSUFFICIENT_DATA' });
  });
});
