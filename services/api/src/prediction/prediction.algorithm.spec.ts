import { calculatePrediction } from './prediction.algorithm.js';

describe('calculatePrediction', () => {
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
      },
      new Date('2026-05-02T00:00:00.000Z'),
    );
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.typicalCycleLength).toBe(30);
    expect(result.predictedStartFrom).toBe('2026-05-29');
    expect(result.predictedStartTo).toBe('2026-06-02');
    expect(result.statistics.consideredCycleCount).toBe(4);
    expect(result.confidence).toBe('MEDIUM');
  });

  it('retains an outlier but does not let it dominate the robust center', () => {
    const result = calculatePrediction({
      periods: [
        { startDate: '2025-12-01' },
        { startDate: '2025-12-30' },
        { startDate: '2026-01-28' },
        { startDate: '2026-03-18' }, // observed 49-day interval stays in statistics
        { startDate: '2026-04-16' },
        { startDate: '2026-05-15' },
        { startDate: '2026-06-13' },
      ],
    });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.typicalCycleLength).toBe(29);
    expect(result.statistics.allObservedCycleLengths).toContain(49);
    expect(result.variationDays).toBeGreaterThanOrEqual(2);
  });

  it('marks fewer than three complete cycles as low confidence and broad', () => {
    const result = calculatePrediction({
      periods: [{ startDate: '2026-06-01' }, { startDate: '2026-07-02' }],
    });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.confidence).toBe('LOW');
    expect(result.variationDays).toBeGreaterThanOrEqual(4);
  });

  it('uses an explicit profile fallback only when no complete cycle exists', () => {
    const result = calculatePrediction({
      periods: [{ startDate: '2026-08-01' }],
      typicalCycleLength: 32,
      typicalPeriodLength: 6,
    });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.typicalCycleLength).toBe(32);
    expect(result.basedOnCycleCount).toBe(0);
    expect(result.explanation.dataBasis).toBe('profile-fallback');
  });

  it.each(['PREGNANCY', 'POSTPARTUM', 'HORMONAL_CONTRACEPTION', 'PAUSED'])(
    'suppresses ordinary predictions in %s mode',
    (specialMode) => {
      expect(
        calculatePrediction({
          periods: [{ startDate: '2026-01-01' }, { startDate: '2026-01-30' }],
          specialMode,
        }),
      ).toMatchObject({ available: false, reason: 'SPECIAL_MODE' });
    },
  );

  it('only estimates a fertile window after explicit enablement and enough data', () => {
    const result = calculatePrediction({
      periods: [
        { startDate: '2026-01-01' },
        { startDate: '2026-01-30' },
        { startDate: '2026-02-28' },
        { startDate: '2026-03-29' },
      ],
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

  it('keeps fertility unavailable when disabled even with sufficient cycle data', () => {
    const result = calculatePrediction({
      periods: [
        { startDate: '2026-01-01' },
        { startDate: '2026-01-30' },
        { startDate: '2026-02-28' },
        { startDate: '2026-03-29' },
      ],
    });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.fertileWindowFrom).toBeNull();
    expect(result.fertileWindowTo).toBeNull();
    expect(result.fertilityEstimate).toMatchObject({
      enabled: false,
      available: false,
      reason: 'DISABLED',
      notContraception: true,
      doesNotConfirmOvulation: true,
    });
  });

  it('keeps fertility unavailable with fewer than three complete cycles', () => {
    const result = calculatePrediction({
      periods: [
        { startDate: '2026-01-01' },
        { startDate: '2026-01-30' },
        { startDate: '2026-02-28' },
      ],
      fertilityPredictionEnabled: true,
    });
    expect(result.available).toBe(true);
    if (!result.available) return;
    expect(result.fertileWindowFrom).toBeNull();
    expect(result.fertileWindowTo).toBeNull();
    expect(result.fertilityEstimate).toMatchObject({
      enabled: true,
      available: false,
      reason: 'INSUFFICIENT_DATA',
      basedOnCycleCount: 2,
    });
  });
});
