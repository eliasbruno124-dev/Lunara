import { CyclePhase } from '@prisma/client';
import { PredictionResult } from '../prediction/prediction.algorithm.js';
import { estimatePartnerPhase, localDateInTimeZone } from './partner-phase.js';

const prediction: PredictionResult = {
  available: true,
  predictedStartFrom: '2026-09-18',
  predictedStartTo: '2026-09-21',
  predictedEndFrom: '2026-09-22',
  predictedEndTo: '2026-09-25',
  fertileWindowFrom: null,
  fertileWindowTo: null,
  confidence: 'MEDIUM',
  basedOnCycleCount: 6,
  calculatedAt: '2026-08-12T00:00:00.000Z',
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

describe('partner phase estimate', () => {
  it('returns an explicitly estimated premenstrual phase with sufficient data', () => {
    expect(estimatePartnerPhase('2026-09-14', null, prediction)).toBe(
      CyclePhase.PREMENSTRUAL_ESTIMATE,
    );
  });

  it('fails closed when fewer than three complete cycles support the prediction', () => {
    expect(estimatePartnerPhase('2026-09-14', null, { ...prediction, basedOnCycleCount: 2 })).toBeNull();
  });

  it('does not reveal an ovulation estimate without the separate fertility permission', () => {
    const withFertility = {
      ...prediction,
      predictedStartFrom: '2026-09-30',
      predictedStartTo: '2026-10-03',
      fertileWindowFrom: '2026-09-10',
      fertileWindowTo: '2026-09-16',
    };
    expect(estimatePartnerPhase('2026-09-14', null, withFertility)).not.toBe(
      CyclePhase.OVULATION_ESTIMATE,
    );
    expect(estimatePartnerPhase('2026-09-14', null, withFertility, true)).toBe(
      CyclePhase.OVULATION_ESTIMATE,
    );
  });

  it('uses the tracker IANA timezone for the local calendar day', () => {
    const instant = new Date('2026-08-12T22:30:00.000Z');
    expect(localDateInTimeZone(instant, 'Europe/Berlin')).toBe('2026-08-13');
    expect(localDateInTimeZone(instant, 'America/New_York')).toBe('2026-08-12');
  });
});
