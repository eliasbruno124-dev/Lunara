import { isSupportedFertileWindow } from '../prediction/fertility-policy.js';

export interface PartnerSharingSettings {
  sharePrediction: boolean;
  sharePeriodDates: boolean;
  sharePhase: boolean;
  shareFlow: boolean;
  sharePain: boolean;
  shareMood: boolean;
  shareEnergy: boolean;
  shareSleep: boolean;
  shareSymptoms: boolean;
  shareNotes: boolean;
  shareFertileWindow: boolean;
  sharingPaused: boolean;
  privateDate: Date | null;
}

export interface PartnerProjectionInput {
  settings: PartnerSharingSettings;
  prediction: Record<string, unknown> | null;
  phase: string | null;
  periodDates: Array<Record<string, unknown>>;
  todayLog: {
    localDate: string;
    flowLevel: unknown;
    painLevel: unknown;
    mood: unknown;
    energy: unknown;
    sleepQuality: unknown;
    customSymptoms: unknown;
    note: unknown;
  } | null;
  today: string;
}

export function projectPartnerDashboard(input: PartnerProjectionInput) {
  const { settings } = input;
  const privateToday = settings.privateDate?.toISOString().slice(0, 10) === input.today;
  if (settings.sharingPaused) {
    return {
      sharingPaused: true,
      calendarAccess: { periodDates: false, fertileWindow: false },
      prediction: null,
      phase: null,
      periodDates: [],
      todayLog: null,
    };
  }

  // Consent remains independent of period-forecast sharing. Invalid cached
  // ranges lose only their fertility envelope, not unrelated shared fields.
  const supportedFertility = input.prediction && isSupportedFertileWindow(
    input.prediction.fertileWindowFrom, input.prediction.fertileWindowTo,
  );
  const projectedPrediction = input.prediction
    ? compact({
        available: settings.sharePrediction ? input.prediction.available : undefined,
        predictedStartFrom: settings.sharePrediction
          ? input.prediction.predictedStartFrom
          : undefined,
        predictedStartTo: settings.sharePrediction
          ? input.prediction.predictedStartTo
          : undefined,
        predictedEndFrom: settings.sharePrediction
          ? input.prediction.predictedEndFrom
          : undefined,
        predictedEndTo: settings.sharePrediction
          ? input.prediction.predictedEndTo
          : undefined,
        confidence: settings.sharePrediction ? input.prediction.confidence : undefined,
        basedOnCycleCount: settings.sharePrediction
          ? input.prediction.basedOnCycleCount
          : undefined,
        variationDays: settings.sharePrediction ? input.prediction.variationDays : undefined,
        reason: settings.sharePrediction ? input.prediction.reason : undefined,
        fertileWindowFrom: settings.shareFertileWindow && !privateToday
          ? supportedFertility ? input.prediction.fertileWindowFrom : null
          : undefined,
        fertileWindowTo: settings.shareFertileWindow && !privateToday
          ? supportedFertility ? input.prediction.fertileWindowTo : null
          : undefined,
        fertilityEstimate: settings.shareFertileWindow && !privateToday
          ? supportedFertility ? input.prediction.fertilityEstimate
            : unavailableFertilityEstimate(input.prediction.fertilityEstimate)
          : undefined,
      })
    : {};
  const prediction = Object.keys(projectedPrediction).length > 0
    ? projectedPrediction
    : null;

  const todayLog = privateToday || !input.todayLog
    ? null
    : compact({
        localDate: input.todayLog.localDate,
        flowLevel: settings.shareFlow ? input.todayLog.flowLevel : undefined,
        painLevel: settings.sharePain ? input.todayLog.painLevel : undefined,
        mood: settings.shareMood ? input.todayLog.mood : undefined,
        energy: settings.shareEnergy ? input.todayLog.energy : undefined,
        sleepQuality: settings.shareSleep ? input.todayLog.sleepQuality : undefined,
        customSymptoms: settings.shareSymptoms ? input.todayLog.customSymptoms : undefined,
        note: settings.shareNotes ? input.todayLog.note : undefined,
      });

  return {
    sharingPaused: false,
    // Disclose only effective access, not the underlying private-day choice or
    // whether any health record exists. Older clients can ignore this metadata.
    calendarAccess: {
      periodDates: settings.sharePeriodDates && !privateToday,
      fertileWindow: settings.shareFertileWindow && !privateToday,
    },
    prediction,
    // A private day is an explicit same-day revocation. Remove every field that could reveal
    // whether a period or estimated fertile/phase state applies today. Historical knowledge on
    // the partner's device cannot be undone, but the API must stop confirming it immediately.
    phase: settings.sharePhase && !privateToday ? input.phase : null,
    periodDates: settings.sharePeriodDates && !privateToday ? input.periodDates : [],
    todayLog: todayLog && Object.keys(todayLog).length > 1 ? todayLog : null,
  };
}

function compact<T extends Record<string, unknown>>(value: T): Partial<T> {
  return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== undefined)) as Partial<T>;
}

function unavailableFertilityEstimate(value: unknown): unknown {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return value === true ? false : value;
  const estimate = value as Record<string, unknown>;
  return estimate.available === true
    ? { ...estimate, available: false, reason: 'UNRELIABLE_DATA' }
    : value;
}
