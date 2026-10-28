import { addDays, daysBetween, todayIso } from './date';
import type { PortableProfile } from './backup';
import type { PeriodEvent, Prediction } from './types';

function median(values: number[]): number {
  if (!values.length) return 0;
  const sorted = [...values].sort((left, right) => left - right);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0
    ? (sorted[middle - 1]! + sorted[middle]!) / 2
    : sorted[middle]!;
}

function weightedMedian(values: number[]): number {
  const weighted = values.map((value, index) => ({
    value,
    weight: values.length === 1 ? 1 : 1 + index / (values.length - 1),
  })).sort((left, right) => left.value - right.value);
  const half = weighted.reduce((sum, item) => sum + item.weight, 0) / 2;
  let accumulated = 0;
  for (const item of weighted) {
    accumulated += item.weight;
    if (accumulated >= half) return item.value;
  }
  return weighted.at(-1)?.value ?? 28;
}

function supportedInteger(value: number | null | undefined, minimum: number, maximum: number): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= minimum && value <= maximum ? value : null;
}

/** Lightweight offline estimate. The server remains the canonical, richer algorithm. */
export function calculateLocalPrediction(
  periods: PeriodEvent[],
  referenceDate = todayIso(),
  profile?: PortableProfile,
): Prediction {
  if (profile?.predictionPaused) {
    return { available: false, confidence: 'NONE', reason: 'PREDICTION_PAUSED' };
  }
  if (profile?.specialMode && profile.specialMode !== 'NONE') {
    return { available: false, confidence: 'NONE', reason: 'SPECIAL_MODE' };
  }
  const activePeriods = periods.filter((period) => !period.deletedAt).sort((left, right) => left.startDate.localeCompare(right.startDate));
  const starts = [...new Set(activePeriods.map((period) => period.startDate))]
    .sort()
    .slice(-13);
  if (!starts.length) {
    return { available: false, confidence: 'NONE', reason: 'NO_PERIOD_START' };
  }
  const observedCycleLengths = starts
    .slice(1)
    .map((start, index) => daysBetween(starts[index]!, start))
    .slice(-12);
  const cycleLengths = observedCycleLengths
    .filter((length) => length >= 15 && length <= 90);
  const excludedCycleCount = observedCycleLengths.length - cycleLengths.length;
  const fallbackCycleLength = supportedInteger(profile?.typicalCycleLength, 15, 90);
  if (!cycleLengths.length && fallbackCycleLength === null) {
    return { available: false, confidence: 'NONE', reason: 'INSUFFICIENT_DATA' };
  }
  const modelMedian = cycleLengths.length ? median(cycleLengths) : fallbackCycleLength!;
  const deviations = cycleLengths.map((length) => Math.abs(length - modelMedian));
  const robustVariation = Math.ceil(median(deviations) * 1.4826);
  const rangeSignal = cycleLengths.length
    ? Math.ceil(((Math.max(...cycleLengths) - Math.min(...cycleLengths)) / 2) * 0.5)
    : 0;
  const uncertainty = cycleLengths.length < 3
    ? Math.max(4, robustVariation + 2)
    : cycleLengths.length < 6
      ? Math.max(3, robustVariation + 1)
      : Math.max(2, robustVariation + 1);
  const typicalCycle = Math.round(
    cycleLengths.length ? weightedMedian(cycleLengths) : fallbackCycleLength!,
  );
  const predicted = addDays(starts.at(-1)!, typicalCycle);
  // Match the canonical model: select the latest 12 positive completed observations
  // before excluding >30-day durations. Profile fallback limits are not observation limits.
  const durations = activePeriods
    .filter((period) => period.endDate && period.endDate >= period.startDate)
    .map((period) => daysBetween(period.startDate, period.endDate!) + 1)
    .filter((duration) => duration >= 1)
    .slice(-12)
    .filter((duration) => duration <= 30);
  const typicalDuration = durations.length
    ? Math.round(median(durations))
    : (supportedInteger(profile?.typicalPeriodLength, 1, 14) ?? 5);
  if (addDays(predicted, uncertainty) < referenceDate) {
    return {
      available: false,
      confidence: 'NONE',
      reason: 'STALE_HISTORY',
      basedOnCycleCount: cycleLengths.length,
    };
  }
  const confidence = cycleLengths.length >= 6 && robustVariation <= 2 && rangeSignal <= 3
    ? 'HIGH'
    : cycleLengths.length >= 3 && robustVariation <= 5 && rangeSignal <= 6
      ? 'MEDIUM'
      : 'LOW';
  const fertilityAvailable =
    profile?.fertilityPredictionEnabled === true &&
    cycleLengths.length >= 3 &&
    excludedCycleCount === 0 &&
    confidence !== 'LOW' &&
    uncertainty <= 14;
  const lutealLength = supportedInteger(profile?.lutealLength, 8, 20) ?? 14;
  return {
    available: true,
    predictedStartFrom: addDays(predicted, -uncertainty),
    predictedStartTo: addDays(predicted, uncertainty),
    predictedEndFrom: addDays(predicted, typicalDuration - 1 - uncertainty),
    predictedEndTo: addDays(predicted, typicalDuration - 1 + uncertainty),
    fertileWindowFrom: fertilityAvailable
      ? addDays(predicted, -lutealLength - 5 - uncertainty)
      : null,
    fertileWindowTo: fertilityAvailable
      ? addDays(predicted, -lutealLength + 1 + uncertainty)
      : null,
    fertilityEstimate: fertilityAvailable,
    confidence,
    basedOnCycleCount: cycleLengths.length,
    variationDays: uncertainty,
    explanation: `Recency-weighted median with MAD from ${cycleLengths.length} recorded cycles`,
  };
}
