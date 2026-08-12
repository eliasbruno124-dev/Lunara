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
      prediction: null,
      phase: null,
      periodDates: [],
      todayLog: null,
    };
  }

  const prediction = settings.sharePrediction && input.prediction
    ? {
        ...input.prediction,
        fertileWindowFrom: settings.shareFertileWindow
          ? input.prediction.fertileWindowFrom ?? null
          : undefined,
        fertileWindowTo: settings.shareFertileWindow
          ? input.prediction.fertileWindowTo ?? null
          : undefined,
      }
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
    prediction,
    phase: settings.sharePhase ? input.phase : null,
    periodDates: settings.sharePeriodDates ? input.periodDates : [],
    todayLog: todayLog && Object.keys(todayLog).length > 1 ? todayLog : null,
  };
}

function compact<T extends Record<string, unknown>>(value: T): Partial<T> {
  return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== undefined)) as Partial<T>;
}
