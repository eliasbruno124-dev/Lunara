import { CyclePhase } from '@prisma/client';
import { calculatePrediction, PredictionResult } from '../prediction/prediction.algorithm.js';
import { estimatePartnerPhase, localDateInTimeZone } from './partner-phase.js';

const prediction: PredictionResult = {
  available: true,
  predictedStartFrom: '2026-09-18',
  predictedStartTo: '2026-09-21',
  predictedEndFrom: '2026-09-22',
  predictedEndTo: '2026-09-25',
  fertileWindowFrom: null,
  fertileWindowTo: null,
  fertilityEstimate: {
    enabled: false,
    available: false,
    estimated: true,
    reason: 'DISABLED',
    basedOnCycleCount: 6,
    notContraception: true,
    doesNotConfirmOvulation: true,
  },
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
  it.each([['2026-09-19', true], ['2026-09-20', false], ['2026-10-03', false]])(
    'does not infer current ovulation from an unsupported cached range ending %s', (end, accepted) => {
      const cached = {
        ...prediction, predictedStartFrom: '2026-10-15', predictedStartTo: '2026-10-18',
        fertileWindowFrom: '2026-09-01', fertileWindowTo: end,
        fertilityEstimate: { ...prediction.fertilityEstimate, enabled: true, available: true },
      };
      expect(estimatePartnerPhase('2026-09-05', null, cached, true)).toBe(accepted ? CyclePhase.OVULATION_ESTIMATE : null);
      expect(estimatePartnerPhase('2026-10-04', null, cached, true)).toBeNull();
    },
  );

  it.each([
    [undefined, '2026-04-07', CyclePhase.FOLLICULAR_ESTIMATE],
    [undefined, '2026-04-09', CyclePhase.LUTEAL_ESTIMATE],
    [8, '2026-04-10', CyclePhase.FOLLICULAR_ESTIMATE],
    [20, '2026-04-04', CyclePhase.LUTEAL_ESTIMATE],
  ])('uses the same cycle center and luteal assumption (%s) as the prediction', (lutealLength, today, expected) => {
    const result = calculatePrediction({
      periods: ['2026-01-01', '2026-01-29', '2026-02-26', '2026-03-26'].map(startDate => ({ startDate })),
      referenceDate: '2026-03-26', lutealLength,
      fertilityPredictionEnabled: false,
    });
    expect(result.available).toBe(true);
    if (!result.available) throw new Error('Expected synthetic prediction');
    expect(estimatePartnerPhase(today, {
      startDate: new Date('2026-03-26T00:00:00Z'), endDate: new Date('2026-03-30T00:00:00Z'),
    }, result, false)).toBe(expected);
    expect(result.fertileWindowFrom).toBeNull();
    expect(result.fertileWindowTo).toBeNull();
  });

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
      fertilityEstimate: {
        ...prediction.fertilityEstimate,
        enabled: true,
        available: true,
        reason: 'AVAILABLE' as const,
      },
    };
    expect(estimatePartnerPhase('2026-09-14', null, withFertility)).not.toBe(
      CyclePhase.OVULATION_ESTIMATE,
    );
    expect(estimatePartnerPhase('2026-09-14', null, withFertility, true)).toBe(
      CyclePhase.OVULATION_ESTIMATE,
    );
  });

  it('can report an actual period without enough history for an estimate', () => {
    expect(
      estimatePartnerPhase(
        '2026-09-14',
        {
          startDate: new Date('2026-09-12T00:00:00.000Z'),
          endDate: null,
        },
        null,
      ),
    ).toBe(CyclePhase.MENSTRUATION);
  });

  it('does not keep returning a luteal estimate after the forecast passed', () => {
    expect(
      estimatePartnerPhase(
        '2026-10-10',
        {
          startDate: new Date('2026-08-22T00:00:00.000Z'),
          endDate: new Date('2026-08-26T00:00:00.000Z'),
        },
        prediction,
      ),
    ).toBeNull();
  });

  it('uses the tracker IANA timezone for the local calendar day', () => {
    const instant = new Date('2026-08-12T22:30:00.000Z');
    expect(localDateInTimeZone(instant, 'Europe/Berlin')).toBe('2026-08-13');
    expect(localDateInTimeZone(instant, 'America/New_York')).toBe('2026-08-12');
  });
});
