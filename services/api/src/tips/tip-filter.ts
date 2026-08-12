import {
  CarePreferences,
  CyclePhase,
  PartnerTip,
  SharingSettings,
  TipCategory,
  TipFeedback,
} from '@prisma/client';

export type SelectableTip = PartnerTip & { feedback: TipFeedback[] };

export interface TipSelectionContext {
  applicablePhases: CyclePhase[];
  sharedSymptoms: string[];
  carePreferences: CarePreferences | null;
}

const EMPTY_CONTEXT: TipSelectionContext = {
  applicablePhases: [],
  sharedSymptoms: [],
  carePreferences: null,
};

export function eligibleTips(
  tips: SelectableTip[],
  sharing: SharingSettings,
  context: TipSelectionContext = EMPTY_CONTEXT,
): SelectableTip[] {
  if (!sharing.tipsEnabled || sharing.sharingPaused) return [];
  const fields = sharing as unknown as Record<string, unknown>;
  const sharedSymptoms = new Set(context.sharedSymptoms.map(normalize));
  return tips.filter((tip) => {
    if (!tip.enabled || sharing.disabledTipCategories.includes(tip.category)) return false;
    if (tip.feedback.some((feedback) => feedback.dismissed)) return false;
    if (!tip.requiredSharedFields.every((field) => fields[field] === true)) return false;
    if (tip.applicablePhase && !context.applicablePhases.includes(tip.applicablePhase)) {
      return false;
    }
    if (tip.applicableSymptoms.length > 0) {
      if (!sharing.allowSymptomBasedTips || !sharing.shareSymptoms) return false;
      if (!tip.applicableSymptoms.some((symptom) => sharedSymptoms.has(normalize(symptom)))) {
        return false;
      }
    }
    if (contradictsCarePreferences(tip, context.carePreferences)) return false;
    return true;
  });
}

export function carePreferenceScore(
  tip: SelectableTip,
  care: CarePreferences | null,
): number {
  if (!care) return 0;
  let score = 0;
  if (tip.category === TipCategory.COMFORT && (care.likesWarmth || care.likesCuddling)) score += 2;
  if (tip.category === TipCategory.SPACE && care.likesSpace) score += 3;
  if (tip.category === TipCategory.FOOD_AND_DRINK && care.likesSnacks) score += 3;
  if (
    ([TipCategory.COMMUNICATION, TipCategory.EMOTIONAL_SUPPORT] as TipCategory[]).includes(
      tip.category,
    ) &&
    care.likesConversation
  ) {
    score += 2;
  }
  if (tip.category === TipCategory.PRACTICAL_HELP && care.likesHouseholdHelp) score += 3;
  if (care.likesReminders && textContainsAny(tipText(tip), ['erinner', 'remind'])) score += 2;

  for (const helpful of stringArray(care.customHelpfulActions)) {
    if (phrasesOverlap(actionText(tip), helpful)) score += 4;
  }
  return score;
}

function contradictsCarePreferences(
  tip: SelectableTip,
  care: CarePreferences | null,
): boolean {
  if (!care) return false;
  if (
    care.dislikesUnsolicitedAdvice &&
    tip.category === TipCategory.USER_DEFINED &&
    textContainsAny(actionText(tip), ['ratschlag', 'rat geben', 'beratung', 'advice', 'recommend']) &&
    !textContainsAny(actionText(tip), ['frag', 'wenn gewünscht', 'ask', 'if requested'])
  ) {
    return true;
  }
  return stringArray(care.customUnhelpfulActions).some((unhelpful) =>
    phrasesOverlap(actionText(tip), unhelpful),
  );
}

function tipText(tip: SelectableTip): string {
  return [
    tip.titleDe,
    tip.bodyDe,
    tip.callToActionDe,
    tip.titleEn,
    tip.bodyEn,
    tip.callToActionEn,
  ].join(' ');
}

function actionText(tip: SelectableTip): string {
  return `${tip.callToActionDe} ${tip.callToActionEn}`;
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
    : [];
}

function phrasesOverlap(text: string, phrase: string): boolean {
  const normalizedText = normalize(text);
  const normalizedPhrase = normalize(phrase);
  return normalizedPhrase.length >= 3 && normalizedText.includes(normalizedPhrase);
}

function textContainsAny(text: string, phrases: string[]): boolean {
  const normalized = normalize(text);
  return phrases.some((phrase) => normalized.includes(normalize(phrase)));
}

function normalize(value: string): string {
  return value
    .normalize('NFKC')
    .toLocaleLowerCase('de-DE')
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();
}
