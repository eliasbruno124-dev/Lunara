import { CyclePhase } from '@prisma/client';
import { estimatedOvulationDay, PredictionResult } from '../prediction/prediction.algorithm.js';
import { effectivePeriodEndLocalDate } from '../periods/period-policy.js';
import { isSupportedFertileWindow } from '../prediction/fertility-policy.js';

const DAY_MS = 86_400_000;

export interface TipPhasePeriod {
  startDate: string;
  endDate: string | null;
}

export interface TipPhaseSharing {
  sharePrediction: boolean;
  sharePeriodDates: boolean;
  sharePhase: boolean;
  shareFertileWindow: boolean;
}

/**
 * Derives only phases the partner is allowed to use for tip selection. Actual
 * menstruation can be derived from shared period dates or the phase sharing
 * switch. Every estimated phase needs at least three complete cycles. The
 * fertility estimate additionally needs its dedicated sharing switch and the
 * algorithm's explicit availability signal.
 */
export function applicableTipPhases(
  today: string,
  latestPeriod: TipPhasePeriod | null,
  prediction: PredictionResult | null,
  sharing: TipPhaseSharing,
): CyclePhase[] {
  const phases = new Set<CyclePhase>();
  const todayDay = epochDay(today);
  const periodStart = latestPeriod ? epochDay(latestPeriod.startDate) : null;
  const periodEnd = latestPeriod
    ? epochDay(effectivePeriodEndLocalDate(latestPeriod.startDate, latestPeriod.endDate))
    : null;
  const periodIsCurrent =
    periodStart !== null &&
    periodStart <= todayDay &&
    periodEnd !== null && todayDay <= periodEnd;

  if (periodIsCurrent && (sharing.sharePeriodDates || sharing.sharePhase)) {
    phases.add(CyclePhase.MENSTRUATION);
    // An actual shared period overrides potentially stale estimated phases.
    return [...phases];
  }

  if (!prediction || prediction.basedOnCycleCount < 3) return [...phases];
  const predictedFrom = epochDay(prediction.predictedStartFrom);
  const predictedTo = epochDay(prediction.predictedStartTo);

  if (
    (sharing.sharePrediction || sharing.sharePhase) &&
    todayDay >= predictedFrom - 7 &&
    todayDay <= predictedTo
  ) {
    phases.add(CyclePhase.PREMENSTRUAL_ESTIMATE);
  }

  const inSharedFertileWindow =
    sharing.shareFertileWindow &&
    prediction.fertilityEstimate.enabled &&
    prediction.fertilityEstimate.available &&
    prediction.fertileWindowFrom !== null &&
    prediction.fertileWindowTo !== null &&
    isSupportedFertileWindow(prediction.fertileWindowFrom, prediction.fertileWindowTo) &&
    todayDay >= epochDay(prediction.fertileWindowFrom) &&
    todayDay <= epochDay(prediction.fertileWindowTo);
  if (inSharedFertileWindow) phases.add(CyclePhase.OVULATION_ESTIMATE);

  if (
    sharing.sharePhase &&
    periodStart !== null &&
    todayDay >= periodStart &&
    todayDay <= predictedTo
  ) {
    if (!inSharedFertileWindow) {
      const estimatedOvulation = estimatedOvulationDay(prediction);
      phases.add(
        todayDay < estimatedOvulation
          ? CyclePhase.FOLLICULAR_ESTIMATE
          : CyclePhase.LUTEAL_ESTIMATE,
      );
    }
  }
  return [...phases];
}

function epochDay(value: string): number {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new Error(`Invalid local date: ${value}`);
  const timestamp = Date.parse(`${value}T00:00:00.000Z`);
  if (!Number.isFinite(timestamp) || new Date(timestamp).toISOString().slice(0, 10) !== value) {
    throw new Error(`Invalid local date: ${value}`);
  }
  return Math.floor(timestamp / DAY_MS);
}
