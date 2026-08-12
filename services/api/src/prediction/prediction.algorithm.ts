export const PREDICTION_ALGORITHM_VERSION = 'lunara-robust-v1';

export type PredictionConfidenceValue = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNAVAILABLE';

export interface HistoricalPeriod {
  startDate: string;
  endDate?: string | null;
}

export interface PredictionInput {
  periods: HistoricalPeriod[];
  typicalCycleLength?: number | null;
  typicalPeriodLength?: number | null;
  lutealLength?: number | null;
  fertilityPredictionEnabled?: boolean;
  predictionPaused?: boolean;
  specialMode?: string;
}

export interface CycleStatistics {
  averageCycleLength: number | null;
  medianCycleLength: number | null;
  shortestCycle: number | null;
  longestCycle: number | null;
  typicalPeriodDuration: number;
  medianAbsoluteDeviation: number | null;
  variationDays: number;
  consideredCycleCount: number;
  allObservedCycleLengths: number[];
}

export interface PredictionResult {
  available: true;
  predictedStartFrom: string;
  predictedStartTo: string;
  predictedEndFrom: string;
  predictedEndTo: string;
  fertileWindowFrom: string | null;
  fertileWindowTo: string | null;
  confidence: Exclude<PredictionConfidenceValue, 'UNAVAILABLE'>;
  basedOnCycleCount: number;
  calculatedAt: string;
  algorithmVersion: string;
  typicalCycleLength: number;
  typicalPeriodLength: number;
  variationDays: number;
  statistics: CycleStatistics;
  explanation: {
    method: 'recency-weighted-median-with-mad';
    dataBasis: 'observed-cycles' | 'profile-fallback';
    cyclesUsed: number;
    outliersRetained: true;
    uncertainty: 'low' | 'medium' | 'high';
    note: string;
  };
}

export interface UnavailablePrediction {
  available: false;
  confidence: 'UNAVAILABLE';
  algorithmVersion: string;
  reason: 'NO_PERIOD_START' | 'PREDICTION_PAUSED' | 'SPECIAL_MODE' | 'INSUFFICIENT_DATA';
}

const DAY_MS = 86_400_000;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function epochDay(value: string): number {
  if (!ISO_DATE.test(value)) throw new Error(`Invalid local date: ${value}`);
  const [year, month, day] = value.split('-').map(Number) as [number, number, number];
  const timestamp = Date.UTC(year, month - 1, day);
  const date = new Date(timestamp);
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    throw new Error(`Invalid local date: ${value}`);
  }
  return Math.floor(timestamp / DAY_MS);
}

function fromEpochDay(value: number): string {
  return new Date(value * DAY_MS).toISOString().slice(0, 10);
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  if (sorted.length % 2 === 1) return sorted[middle] as number;
  return ((sorted[middle - 1] as number) + (sorted[middle] as number)) / 2;
}

function weightedMedian(values: number[]): number {
  // Chronological input: a linear 1.0 -> 2.0 weight makes recent cycles matter
  // more without permitting one newest cycle to dominate the history.
  const weighted = values.map((value, index) => ({
    value,
    weight: values.length === 1 ? 1 : 1 + index / (values.length - 1),
  }));
  weighted.sort((a, b) => a.value - b.value);
  const half = weighted.reduce((sum, item) => sum + item.weight, 0) / 2;
  let accumulated = 0;
  for (const item of weighted) {
    accumulated += item.weight;
    if (accumulated >= half) return item.value;
  }
  return weighted[weighted.length - 1]?.value ?? 28;
}

function finiteInteger(value: number | null | undefined, min: number, max: number): number | null {
  return Number.isInteger(value) && (value as number) >= min && (value as number) <= max
    ? (value as number)
    : null;
}

export function calculatePrediction(
  input: PredictionInput,
  now: Date = new Date(),
): PredictionResult | UnavailablePrediction {
  if (input.predictionPaused) {
    return unavailable('PREDICTION_PAUSED');
  }
  if (input.specialMode && input.specialMode !== 'NONE') {
    return unavailable('SPECIAL_MODE');
  }

  const starts = [...new Set(input.periods.map((period) => period.startDate))]
    .map((date) => ({ date, day: epochDay(date) }))
    .sort((a, b) => a.day - b.day);
  if (starts.length === 0) return unavailable('NO_PERIOD_START');

  const allCycleLengths = starts.slice(1).map((start, index) => start.day - (starts[index]?.day ?? 0));
  // At most twelve recent complete cycles. No value is silently dropped as an
  // outlier; median and MAD limit its influence. Non-positive intervals can
  // only result from malformed duplicate data and are rejected explicitly.
  if (allCycleLengths.some((length) => length <= 0)) {
    throw new Error('Period starts must form positive cycle intervals');
  }
  const cycleLengths = allCycleLengths.slice(-12);
  const fallbackCycleLength = finiteInteger(input.typicalCycleLength, 15, 90);
  if (cycleLengths.length === 0 && fallbackCycleLength === null) {
    return unavailable('INSUFFICIENT_DATA');
  }

  const robustCycleLength = Math.round(
    cycleLengths.length > 0 ? weightedMedian(cycleLengths) : (fallbackCycleLength as number),
  );
  const cycleMedian = cycleLengths.length > 0 ? median(cycleLengths) : null;
  const mad = cycleMedian === null
    ? null
    : median(cycleLengths.map((length) => Math.abs(length - cycleMedian)));
  const robustSpread = mad === null ? 0 : Math.ceil(mad * 1.4826);
  const rangeSpread = cycleLengths.length > 0
    ? Math.ceil(((Math.max(...cycleLengths) - Math.min(...cycleLengths)) / 2) * 0.5)
    : 0;
  const variationDays = cycleLengths.length < 3
    ? Math.max(4, robustSpread + 2, rangeSpread)
    : Math.max(2, robustSpread + 1, rangeSpread);

  const completedDurations = input.periods
    .filter((period): period is HistoricalPeriod & { endDate: string } => Boolean(period.endDate))
    .map((period) => epochDay(period.endDate) - epochDay(period.startDate) + 1)
    .filter((duration) => duration > 0 && duration <= 30)
    .slice(-12);
  const fallbackPeriodLength = finiteInteger(input.typicalPeriodLength, 1, 14);
  const typicalPeriodLength = Math.round(
    completedDurations.length > 0
      ? median(completedDurations)
      : (fallbackPeriodLength ?? 5),
  );

  const lastStart = starts[starts.length - 1] as { date: string; day: number };
  const center = lastStart.day + robustCycleLength;
  const startFrom = center - variationDays;
  const startTo = center + variationDays;
  const confidence = confidenceFor(cycleLengths.length, robustSpread, rangeSpread);

  let fertileWindowFrom: string | null = null;
  let fertileWindowTo: string | null = null;
  if (input.fertilityPredictionEnabled && cycleLengths.length >= 3) {
    const lutealLength = finiteInteger(input.lutealLength, 8, 20) ?? 14;
    const ovulationCenter = center - lutealLength;
    fertileWindowFrom = fromEpochDay(ovulationCenter - 5 - variationDays);
    fertileWindowTo = fromEpochDay(ovulationCenter + 1 + variationDays);
  }

  return {
    available: true,
    predictedStartFrom: fromEpochDay(startFrom),
    predictedStartTo: fromEpochDay(startTo),
    predictedEndFrom: fromEpochDay(startFrom + typicalPeriodLength - 1),
    predictedEndTo: fromEpochDay(startTo + typicalPeriodLength - 1),
    fertileWindowFrom,
    fertileWindowTo,
    confidence,
    basedOnCycleCount: cycleLengths.length,
    calculatedAt: now.toISOString(),
    algorithmVersion: PREDICTION_ALGORITHM_VERSION,
    typicalCycleLength: robustCycleLength,
    typicalPeriodLength,
    variationDays,
    statistics: {
      averageCycleLength: cycleLengths.length > 0
        ? Number((cycleLengths.reduce((sum, length) => sum + length, 0) / cycleLengths.length).toFixed(1))
        : null,
      medianCycleLength: cycleMedian,
      shortestCycle: cycleLengths.length > 0 ? Math.min(...cycleLengths) : null,
      longestCycle: cycleLengths.length > 0 ? Math.max(...cycleLengths) : null,
      typicalPeriodDuration: typicalPeriodLength,
      medianAbsoluteDeviation: mad,
      variationDays,
      consideredCycleCount: cycleLengths.length,
      allObservedCycleLengths: cycleLengths,
    },
    explanation: {
      method: 'recency-weighted-median-with-mad',
      dataBasis: cycleLengths.length > 0 ? 'observed-cycles' : 'profile-fallback',
      cyclesUsed: cycleLengths.length,
      outliersRetained: true,
      uncertainty: confidence === 'HIGH' ? 'low' : confidence === 'MEDIUM' ? 'medium' : 'high',
      note:
        cycleLengths.length < 3
          ? 'Fewer than three complete cycles are available; the estimate is deliberately broad.'
          : 'The interval uses recent observed cycles, a robust median and median absolute deviation.',
    },
  };
}

function confidenceFor(
  cycleCount: number,
  robustSpread: number,
  rangeSpread: number,
): 'LOW' | 'MEDIUM' | 'HIGH' {
  if (cycleCount < 3) return 'LOW';
  if (cycleCount >= 6 && robustSpread <= 2 && rangeSpread <= 3) return 'HIGH';
  if (cycleCount >= 3 && robustSpread <= 5 && rangeSpread <= 6) return 'MEDIUM';
  return 'LOW';
}

function unavailable(reason: UnavailablePrediction['reason']): UnavailablePrediction {
  return {
    available: false,
    confidence: 'UNAVAILABLE',
    algorithmVersion: PREDICTION_ALGORITHM_VERSION,
    reason,
  };
}
