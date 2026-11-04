export const PREDICTION_ALGORITHM_VERSION = 'lunara-robust-v3';

export type PredictionConfidenceValue = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNAVAILABLE';

export interface HistoricalPeriod {
  startDate: string;
  endDate?: string | null;
}

export interface PredictionInput {
  periods: HistoricalPeriod[];
  /** Local calendar date in the cycle profile's time zone. */
  referenceDate?: string;
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
  /** Number of raw intervals retained in the statistics window. */
  observedCycleCount?: number;
  /** Chronological intervals that are suitable as model inputs. */
  modelCycleLengths?: number[];
  /** Raw intervals retained for transparency but excluded from the model. */
  excludedCycleLengths?: number[];
  excludedCycleCount?: number;
  modelMedianCycleLength?: number | null;
  completedPeriodDurationCount?: number;
  allObservedPeriodDurations?: number[];
  excludedPeriodDurations?: number[];
  periodDurationDataBasis?: 'observed-periods' | 'profile-fallback' | 'default-prior';
}

export interface PredictionResult {
  available: true;
  predictedStartFrom: string;
  predictedStartTo: string;
  predictedEndFrom: string;
  predictedEndTo: string;
  fertileWindowFrom: string | null;
  fertileWindowTo: string | null;
  fertilityEstimate: FertilityEstimate;
  confidence: Exclude<PredictionConfidenceValue, 'UNAVAILABLE'>;
  basedOnCycleCount: number;
  calculatedAt: string;
  algorithmVersion: string;
  typicalCycleLength: number;
  typicalPeriodLength: number;
  /** Existing profile assumption, shared with phase consumers; not an observation. */
  lutealLength?: number;
  variationDays: number;
  statistics: CycleStatistics;
  explanation: {
    method: 'recency-weighted-median-with-mad';
    dataBasis: 'observed-cycles' | 'profile-fallback';
    cyclesUsed: number;
    outliersRetained: true;
    excludedImplausibleIntervalsFromModel?: number;
    uncertainty: 'low' | 'medium' | 'high';
    note: string;
  };
}

export type FertilityEstimateReason =
  | 'AVAILABLE'
  | 'DISABLED'
  | 'INSUFFICIENT_DATA'
  | 'UNRELIABLE_DATA';

/**
 * Explicit safety metadata for the optional calendar-based fertility estimate.
 * Clients must keep the warning visible anywhere the estimate is presented.
 */
export interface FertilityEstimate {
  enabled: boolean;
  available: boolean;
  estimated: true;
  reason: FertilityEstimateReason;
  basedOnCycleCount: number;
  notContraception: true;
  doesNotConfirmOvulation: true;
}

export interface UnavailablePrediction {
  available: false;
  confidence: 'UNAVAILABLE';
  algorithmVersion: string;
  reason:
    | 'NO_PERIOD_START'
    | 'PREDICTION_PAUSED'
    | 'SPECIAL_MODE'
    | 'INSUFFICIENT_DATA'
    | 'STALE_HISTORY'
    | 'UNRELIABLE_RANGE';
  basedOnCycleCount?: number;
  statistics?: CycleStatistics;
}

const DAY_MS = 86_400_000;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const MIN_MODEL_CYCLE_LENGTH = 15;
const MAX_MODEL_CYCLE_LENGTH = 90;
const MAX_FERTILITY_VARIATION_DAYS = 14;

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
  // more. With at least three cycles, one newest cycle cannot dominate alone.
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
  // At most twelve recent complete intervals. Every positive raw value remains
  // visible in statistics; values outside 15..90 are explicitly reported but
  // excluded from the model because they cannot reliably represent one cycle.
  if (allCycleLengths.some((length) => length <= 0)) {
    throw new Error('Period starts must form positive cycle intervals');
  }
  const observedCycleLengths = allCycleLengths.slice(-12);
  const cycleLengths = observedCycleLengths.filter(
    (length) => length >= MIN_MODEL_CYCLE_LENGTH && length <= MAX_MODEL_CYCLE_LENGTH,
  );
  const excludedCycleLengths = observedCycleLengths.filter(
    (length) => length < MIN_MODEL_CYCLE_LENGTH || length > MAX_MODEL_CYCLE_LENGTH,
  );
  const fallbackCycleLength = finiteInteger(input.typicalCycleLength, 15, 90);

  const modelCycleMedian = cycleLengths.length > 0 ? median(cycleLengths) : null;
  const observedCycleMedian = observedCycleLengths.length > 0
    ? median(observedCycleLengths)
    : null;
  const mad = modelCycleMedian === null
    ? null
    : median(cycleLengths.map((length) => Math.abs(length - modelCycleMedian)));
  const robustSpread = mad === null ? 0 : Math.ceil(1.28 * 1.4826 * mad);
  // The usable range affects confidence only. The visible band itself remains
  // based on MAD, so one retained in-range outlier cannot inflate it by months.
  const rangeSignal = cycleLengths.length > 0
    ? Math.ceil(((Math.max(...cycleLengths) - Math.min(...cycleLengths)) / 2) * 0.5)
    : 0;
  const variationDays = cycleLengths.length < 3
    ? Math.max(4, robustSpread + 2)
    : cycleLengths.length < 6
      ? Math.max(3, robustSpread + 1)
      : Math.max(2, robustSpread + 1);

  const allObservedPeriodDurations = [...input.periods]
    .sort((left, right) => epochDay(left.startDate) - epochDay(right.startDate))
    .filter((period): period is HistoricalPeriod & { endDate: string } => Boolean(period.endDate))
    .map((period) => epochDay(period.endDate) - epochDay(period.startDate) + 1)
    .filter((duration) => duration > 0)
    .slice(-12);
  const completedDurations = allObservedPeriodDurations.filter(
    (duration) => duration <= 30,
  );
  const excludedPeriodDurations = allObservedPeriodDurations.filter(
    (duration) => duration > 30,
  );
  const fallbackPeriodLength = finiteInteger(input.typicalPeriodLength, 1, 14);
  const periodDurationDataBasis: CycleStatistics['periodDurationDataBasis'] =
    completedDurations.length > 0
      ? 'observed-periods'
      : fallbackPeriodLength !== null
        ? 'profile-fallback'
        : 'default-prior';
  const typicalPeriodLength = Math.round(
    completedDurations.length > 0
      ? median(completedDurations)
      : (fallbackPeriodLength ?? 5),
  );

  const statistics: CycleStatistics = {
    averageCycleLength: observedCycleLengths.length > 0
      ? Number(
          (
            observedCycleLengths.reduce((sum, length) => sum + length, 0) /
            observedCycleLengths.length
          ).toFixed(1),
        )
      : null,
    medianCycleLength: observedCycleMedian,
    shortestCycle: observedCycleLengths.length > 0
      ? Math.min(...observedCycleLengths)
      : null,
    longestCycle: observedCycleLengths.length > 0
      ? Math.max(...observedCycleLengths)
      : null,
    typicalPeriodDuration: typicalPeriodLength,
    medianAbsoluteDeviation: mad,
    variationDays,
    consideredCycleCount: cycleLengths.length,
    allObservedCycleLengths: observedCycleLengths,
    observedCycleCount: observedCycleLengths.length,
    modelCycleLengths: cycleLengths,
    excludedCycleLengths,
    excludedCycleCount: excludedCycleLengths.length,
    modelMedianCycleLength: modelCycleMedian,
    completedPeriodDurationCount: completedDurations.length,
    allObservedPeriodDurations,
    excludedPeriodDurations,
    periodDurationDataBasis,
  };

  if (cycleLengths.length === 0 && fallbackCycleLength === null) {
    return unavailable('INSUFFICIENT_DATA', {
      basedOnCycleCount: 0,
      statistics,
    });
  }

  const robustCycleLength = Math.round(
    cycleLengths.length > 0 ? weightedMedian(cycleLengths) : (fallbackCycleLength as number),
  );

  const lastStart = starts[starts.length - 1] as { date: string; day: number };
  const center = lastStart.day + robustCycleLength;
  const startFrom = center - variationDays;
  const startTo = center + variationDays;
  if (variationDays > 14 || startFrom < lastStart.day) {
    return unavailable('UNRELIABLE_RANGE', {
      basedOnCycleCount: cycleLengths.length,
      statistics,
    });
  }
  const modelConfidence = confidenceFor(cycleLengths.length, robustSpread, rangeSignal);
  const recentLongGap = observedCycleLengths.slice(-2).some(
    (length) => length > MAX_MODEL_CYCLE_LENGTH,
  );
  const confidence = recentLongGap && modelConfidence === 'HIGH' ? 'MEDIUM' : modelConfidence;
  const referenceDay = epochDay(input.referenceDate ?? now.toISOString().slice(0, 10));
  if (startTo < referenceDay) {
    return unavailable('STALE_HISTORY', {
      basedOnCycleCount: cycleLengths.length,
      statistics,
    });
  }

  let fertileWindowFrom: string | null = null;
  let fertileWindowTo: string | null = null;
  const fertilityEnabled = input.fertilityPredictionEnabled === true;
  const fertilityHasEnoughData = cycleLengths.length >= 3;
  const fertilityDataReliable =
    excludedCycleLengths.length === 0 &&
    confidence !== 'LOW' &&
    variationDays <= MAX_FERTILITY_VARIATION_DAYS;
  const fertilityAvailable =
    fertilityEnabled && fertilityHasEnoughData && fertilityDataReliable;
  const lutealLength = finiteInteger(input.lutealLength, 8, 20) ?? 14;
  if (fertilityAvailable) {
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
    fertilityEstimate: {
      enabled: fertilityEnabled,
      available: fertilityAvailable,
      estimated: true,
      reason: fertilityAvailable
        ? 'AVAILABLE'
        : !fertilityEnabled
          ? 'DISABLED'
          : !fertilityHasEnoughData
            ? 'INSUFFICIENT_DATA'
            : 'UNRELIABLE_DATA',
      basedOnCycleCount: cycleLengths.length,
      notContraception: true,
      doesNotConfirmOvulation: true,
    },
    confidence,
    basedOnCycleCount: cycleLengths.length,
    calculatedAt: now.toISOString(),
    algorithmVersion: PREDICTION_ALGORITHM_VERSION,
    typicalCycleLength: robustCycleLength,
    typicalPeriodLength,
    lutealLength,
    variationDays,
    statistics,
    explanation: {
      method: 'recency-weighted-median-with-mad',
      dataBasis: cycleLengths.length > 0 ? 'observed-cycles' : 'profile-fallback',
      cyclesUsed: cycleLengths.length,
      outliersRetained: true,
      excludedImplausibleIntervalsFromModel: excludedCycleLengths.length,
      uncertainty: confidence === 'HIGH' ? 'low' : confidence === 'MEDIUM' ? 'medium' : 'high',
      note:
        cycleLengths.length < 3
          ? 'Fewer than three usable complete cycles are available; the estimate is deliberately broad.'
          : excludedCycleLengths.length > 0
            ? 'Implausible intervals remain visible in statistics but are excluded from the model and fertility estimate.'
            : 'The interval uses recent observed cycles, a robust median and median absolute deviation.',
    },
  };
}

/** Same model center used by the calendar estimate, never a confirmed ovulation. */
export function estimatedOvulationDay(prediction: PredictionResult): number {
  const center = Math.round(
    (epochDay(prediction.predictedStartFrom) + epochDay(prediction.predictedStartTo)) / 2,
  );
  return center - (finiteInteger(prediction.lutealLength, 8, 20) ?? 14);
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

function unavailable(
  reason: UnavailablePrediction['reason'],
  details: Pick<UnavailablePrediction, 'basedOnCycleCount' | 'statistics'> = {},
): UnavailablePrediction {
  return {
    available: false,
    confidence: 'UNAVAILABLE',
    algorithmVersion: PREDICTION_ALGORITHM_VERSION,
    reason,
    ...details,
  };
}
