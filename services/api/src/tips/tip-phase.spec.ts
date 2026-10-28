import { CyclePhase } from '@prisma/client';
import { calculatePrediction, PredictionResult } from '../prediction/prediction.algorithm.js';
import { applicableTipPhases, TipPhaseSharing } from './tip-phase.js';

const prediction: PredictionResult = {
  available: true,
  predictedStartFrom: '2026-09-28',
  predictedStartTo: '2026-10-02',
  predictedEndFrom: '2026-10-02',
  predictedEndTo: '2026-10-06',
  fertileWindowFrom: '2026-09-10',
  fertileWindowTo: '2026-09-17',
  fertilityEstimate: {
    enabled: true,
    available: true,
    estimated: true,
    reason: 'AVAILABLE',
    basedOnCycleCount: 6,
    notContraception: true,
    doesNotConfirmOvulation: true,
  },
  confidence: 'MEDIUM',
  basedOnCycleCount: 6,
  calculatedAt: '2026-09-01T00:00:00.000Z',
  algorithmVersion: 'test',
  typicalCycleLength: 29,
  typicalPeriodLength: 5,
  variationDays: 2,
  statistics: {
    averageCycleLength: 29,
    medianCycleLength: 29,
    shortestCycle: 28,
    longestCycle: 30,
    typicalPeriodDuration: 5,
    medianAbsoluteDeviation: 1,
    variationDays: 2,
    consideredCycleCount: 6,
    allObservedCycleLengths: [29, 29, 28, 30, 29, 29],
  },
  explanation: {
    method: 'recency-weighted-median-with-mad',
    dataBasis: 'observed-cycles',
    cyclesUsed: 6,
    outliersRetained: true,
    uncertainty: 'medium',
    note: 'test',
  },
};

const sharing: TipPhaseSharing = {
  sharePrediction: true,
  sharePeriodDates: false,
  sharePhase: true,
  shareFertileWindow: false,
};

describe('applicableTipPhases', () => {
  it.each([
    [undefined, '2026-04-07', CyclePhase.FOLLICULAR_ESTIMATE],
    [undefined, '2026-04-09', CyclePhase.LUTEAL_ESTIMATE],
    [8, '2026-04-10', CyclePhase.FOLLICULAR_ESTIMATE],
    [20, '2026-04-04', CyclePhase.LUTEAL_ESTIMATE],
  ])('uses the calendar model center and configured luteal assumption (%s)', (lutealLength, today, expected) => {
    const result = calculatePrediction({
      periods: ['2026-01-01', '2026-01-29', '2026-02-26', '2026-03-26'].map(startDate => ({ startDate })),
      referenceDate: '2026-03-26', lutealLength, fertilityPredictionEnabled: false,
    });
    expect(result.available).toBe(true);
    if (!result.available) throw new Error('Expected synthetic prediction');
    const period = { startDate: '2026-03-26', endDate: '2026-03-30' };
    expect(applicableTipPhases(today, period, result, sharing)).toEqual([expected]);
    expect(applicableTipPhases(today, period, result, { ...sharing, sharePhase: false })).toEqual([]);
  });

  it('supports actual menstruation and the non-fertility estimated phases', () => {
    const period = { startDate: '2026-09-01', endDate: '2026-09-05' };
    expect(applicableTipPhases('2026-09-03', period, prediction, sharing)).toEqual([
      CyclePhase.MENSTRUATION,
    ]);
    expect(applicableTipPhases('2026-09-08', period, prediction, sharing)).toContain(
      CyclePhase.FOLLICULAR_ESTIMATE,
    );
    expect(applicableTipPhases('2026-09-20', period, prediction, sharing)).toContain(
      CyclePhase.LUTEAL_ESTIMATE,
    );
    expect(applicableTipPhases('2026-09-24', period, prediction, sharing)).toContain(
      CyclePhase.PREMENSTRUAL_ESTIMATE,
    );
  });

  it('allows the premenstrual phase through either prediction or phase sharing', () => {
    const period = { startDate: '2026-09-01', endDate: '2026-09-05' };
    expect(
      applicableTipPhases('2026-09-24', period, prediction, {
        ...sharing,
        sharePrediction: false,
      }),
    ).toContain(CyclePhase.PREMENSTRUAL_ESTIMATE);
    expect(
      applicableTipPhases('2026-09-24', period, prediction, {
        ...sharing,
        sharePrediction: false,
        sharePhase: false,
      }),
    ).not.toContain(CyclePhase.PREMENSTRUAL_ESTIMATE);
  });

  it('never selects an ovulation estimate without the separate fertility permission', () => {
    const period = { startDate: '2026-09-01', endDate: '2026-09-05' };
    expect(applicableTipPhases('2026-09-14', period, prediction, sharing)).not.toContain(
      CyclePhase.OVULATION_ESTIMATE,
    );
    expect(
      applicableTipPhases('2026-09-14', period, prediction, {
        ...sharing,
        shareFertileWindow: true,
      }),
    ).toContain(CyclePhase.OVULATION_ESTIMATE);
  });

  it('fails closed for every estimated phase with fewer than three complete cycles', () => {
    const period = { startDate: '2026-09-01', endDate: '2026-09-05' };
    expect(
      applicableTipPhases('2026-09-14', period, {
        ...prediction,
        basedOnCycleCount: 2,
        fertilityEstimate: {
          ...prediction.fertilityEstimate,
          available: false,
          reason: 'INSUFFICIENT_DATA',
          basedOnCycleCount: 2,
        },
      }, { ...sharing, shareFertileWindow: true }),
    ).toEqual([]);
  });

  it('does not continue a luteal estimate after the predicted range passed', () => {
    expect(
      applicableTipPhases(
        '2026-10-10',
        { startDate: '2026-09-01', endDate: '2026-09-05' },
        prediction,
        sharing,
      ),
    ).toEqual([]);
  });
});
