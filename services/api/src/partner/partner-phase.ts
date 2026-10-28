import { CyclePhase } from '@prisma/client';
import { estimatedOvulationDay, PredictionResult } from '../prediction/prediction.algorithm.js';

const DAY_MS = 86_400_000;

export interface PartnerPhasePeriod {
  startDate: Date;
  endDate: Date | null;
}

export function localDateInTimeZone(now: Date, timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

export function estimatePartnerPhase(
  today: string,
  latestPeriod: PartnerPhasePeriod | null,
  prediction: PredictionResult | null,
  shareFertileWindow = false,
): CyclePhase | null {
  const todayDay = epochDay(today);
  if (latestPeriod) {
    const start = epochDay(latestPeriod.startDate.toISOString().slice(0, 10));
    const end = latestPeriod.endDate
      ? epochDay(latestPeriod.endDate.toISOString().slice(0, 10))
      : start + 13;
    if (todayDay >= start && todayDay <= end) return CyclePhase.MENSTRUATION;
  }
  if (!prediction || prediction.basedOnCycleCount < 3) return null;

  const predictedFrom = epochDay(prediction.predictedStartFrom);
  const predictedTo = epochDay(prediction.predictedStartTo);
  if (todayDay >= predictedFrom - 7 && todayDay <= predictedTo) {
    return CyclePhase.PREMENSTRUAL_ESTIMATE;
  }
  if (
    shareFertileWindow &&
    prediction.fertilityEstimate.enabled &&
    prediction.fertilityEstimate.available &&
    prediction.fertileWindowFrom &&
    prediction.fertileWindowTo &&
    todayDay >= epochDay(prediction.fertileWindowFrom) &&
    todayDay <= epochDay(prediction.fertileWindowTo)
  ) {
    return CyclePhase.OVULATION_ESTIMATE;
  }
  if (!latestPeriod) return null;
  const latestStart = epochDay(latestPeriod.startDate.toISOString().slice(0, 10));
  if (todayDay < latestStart || todayDay > predictedTo) return null;
  const estimatedOvulation = estimatedOvulationDay(prediction);
  return todayDay < estimatedOvulation
    ? CyclePhase.FOLLICULAR_ESTIMATE
    : CyclePhase.LUTEAL_ESTIMATE;
}

function epochDay(value: string): number {
  return Math.floor(Date.parse(`${value}T00:00:00.000Z`) / DAY_MS);
}
