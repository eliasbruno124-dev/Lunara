import { CyclePhase, TipCategory } from '@prisma/client';
import { carePreferenceScore, eligibleTips } from './tip-filter.js';
import { TIP_CATALOG } from './tip-catalog.js';
import { SelectableTip } from './tip-filter.js';
import { CARE_ACTION_IDS, CareAction } from './tip-care-actions.js';

describe('eligibleTips', () => {
  it('a warmth preference does not boost an unrelated cuddle or general comfort suggestion', () => {
    const care = { likesWarmth: true, customHelpfulActions: [] } as never;
    const warm = { category: TipCategory.COMFORT, callToActionDe: 'Biete eine Wärmflasche an.', callToActionEn: 'Offer a hot water bottle.' } as never;
    const cuddle = { category: TipCategory.COMFORT, callToActionDe: 'Biete eine Umarmung an.', callToActionEn: 'Offer a hug.' } as never;
    expect(carePreferenceScore(warm, care)).toBeGreaterThan(0);
    expect(carePreferenceScore(cuddle, care)).toBe(0);
  });
  const settings = {
    tipsEnabled: true,
    sharingPaused: false,
    disabledTipCategories: [],
    sharePrediction: true,
    shareSymptoms: false,
    allowSymptomBasedTips: false,
  } as never;

  it('never exposes a symptom-dependent tip without both symptom permissions', () => {
    const tips = [
      {
        id: 'symptom-tip', enabled: true, category: TipCategory.COMFORT,
        requiredSharedFields: [], applicableSymptoms: ['cramps'], feedback: [],
      },
      {
        id: 'general-tip', enabled: true, category: TipCategory.GENERAL,
        requiredSharedFields: [], applicableSymptoms: [], feedback: [],
      },
    ] as never;
    expect(eligibleTips(tips, settings).map((tip) => tip.id)).toEqual(['general-tip']);
  });

  it('requires every declared sharing flag and respects dismissal', () => {
    const tips = [
      {
        id: 'prediction', enabled: true, category: TipCategory.GENERAL,
        requiredSharedFields: ['sharePrediction'], applicableSymptoms: [], feedback: [],
      },
      {
        id: 'notes', enabled: true, category: TipCategory.GENERAL,
        requiredSharedFields: ['shareNotes'], applicableSymptoms: [], feedback: [],
      },
      {
        id: 'dismissed', enabled: true, category: TipCategory.GENERAL,
        requiredSharedFields: [], applicableSymptoms: [], feedback: [{ dismissed: true }],
      },
    ] as never;
    expect(eligibleTips(tips, settings).map((tip) => tip.id)).toEqual(['prediction']);
  });

  it('requires the actually shared matching symptom and phase', () => {
    const gatedSettings = {
      tipsEnabled: true,
      sharingPaused: false,
      disabledTipCategories: [],
      sharePrediction: true,
      shareSymptoms: true,
      allowSymptomBasedTips: true,
      sharePhase: true,
    } as never;
    const tips = [{
      id: 'matched', enabled: true, category: TipCategory.USER_DEFINED,
      requiredSharedFields: ['shareSymptoms'], applicableSymptoms: ['Krämpfe'],
      applicablePhase: CyclePhase.MENSTRUATION, feedback: [],
    }] as never;
    expect(eligibleTips(tips, gatedSettings)).toEqual([]);
    expect(eligibleTips(tips, gatedSettings, {
      applicablePhases: [CyclePhase.MENSTRUATION],
      sharedSymptoms: ['Kopfschmerzen'],
      carePreferences: null,
    })).toEqual([]);
    expect(eligibleTips(tips, gatedSettings, {
      applicablePhases: [CyclePhase.MENSTRUATION],
      sharedSymptoms: ['krämpfe'],
      carePreferences: null,
    }).map((tip) => tip.id)).toEqual(['matched']);
  });

  it('excludes explicitly unwanted actions and scores liked care categories higher', () => {
    const tips = [
      {
        id: 'space', enabled: true, category: TipCategory.SPACE,
        requiredSharedFields: [], applicableSymptoms: [], feedback: [],
        titleDe: 'Ruhe', bodyDe: 'Raum lassen', callToActionDe: 'Gib etwas Ruhe',
        titleEn: 'Space', bodyEn: 'Allow space', callToActionEn: 'Give some space',
        priority: 0,
      },
      {
        id: 'massage', enabled: true, category: TipCategory.USER_DEFINED,
        requiredSharedFields: [], applicableSymptoms: [], feedback: [],
        titleDe: 'Massage', bodyDe: 'Eine Massage', callToActionDe: 'Biete eine Massage an',
        titleEn: 'Massage', bodyEn: 'A massage', callToActionEn: 'Offer a massage',
        priority: 0,
      },
      {
        id: 'advice', enabled: true, category: TipCategory.USER_DEFINED,
        requiredSharedFields: [], applicableSymptoms: [], feedback: [],
        titleDe: 'Rat', bodyDe: 'Ein Tipp', callToActionDe: 'Gib einen Ratschlag',
        titleEn: 'Advice', bodyEn: 'A tip', callToActionEn: 'Give advice',
        priority: 0,
      },
    ] as never;
    const care = {
      likesSpace: true,
      likesWarmth: false,
      likesCuddling: false,
      likesSnacks: false,
      likesConversation: false,
      likesHouseholdHelp: false,
      likesReminders: false,
      dislikesUnsolicitedAdvice: true,
      customHelpfulActions: [],
      customUnhelpfulActions: ['Massage'],
    } as never;
    const context = { applicablePhases: [], sharedSymptoms: [], carePreferences: care };
    expect(eligibleTips(tips, settings, context).map((tip) => tip.id)).toEqual(['space']);
    expect(carePreferenceScore(tips[0], care)).toBeGreaterThan(
      carePreferenceScore(tips[1], care),
    );
  });

  const builtIns: SelectableTip[] = TIP_CATALOG.map((tip) => ({ ...tip,
    applicablePhase: tip.applicablePhase ?? null, applicableSymptoms: tip.applicableSymptoms ?? [],
    enabled: true, feedback: [], createdByUserId: null, householdId: null,
    createdAt: new Date(0), updatedAt: new Date(0),
  }));

  it('matches the actual built-in action, not a category, incidental word or warning', () => {
    const score = (number: number, preference: string) => carePreferenceScore(builtIns[number - 1]!, { [preference]: true, customHelpfulActions: [] } as never);
    expect(score(11, 'likesWarmth')).toBeGreaterThan(0);
    expect(score(48, 'likesWarmth')).toBe(0);
    expect(score(48, 'likesCuddling')).toBeGreaterThan(0);
    expect(score(3, 'likesCuddling')).toBe(0); // company is not touch
    expect(score(50, 'likesSnacks')).toBeGreaterThan(0);
    expect(score(63, 'likesSnacks')).toBe(0); // nausea offer is not a snack
    expect(score(2, 'likesConversation')).toBeGreaterThan(0);
    expect(score(5, 'likesConversation')).toBe(0); // accepting no is not an invitation to talk
    expect(score(28, 'likesReminders')).toBe(0); // remembering YOUR promise
    expect(score(56, 'likesReminders')).toBe(0);
    expect(score(32, 'likesHouseholdHelp')).toBeGreaterThan(0); // real cooking action, despite PERIOD_DAYS category
  });

  it('understands built-in action aliases for exact unhelpful phrases without treating warmth as touch', () => {
    const context = { applicablePhases: [], sharedSymptoms: [], carePreferences: { customUnhelpfulActions: ['Berührung', 'warmth'], customHelpfulActions: [] } as never };
    const allowed = eligibleTips(builtIns, settings, context);
    expect(allowed).not.toContain(builtIns[10]);
    expect(allowed).not.toContain(builtIns[47]);
    expect(allowed).toContain(builtIns[5]);
  });

  it('keeps an unset likes switch neutral rather than treating default false as a dislike', () => {
    const care = { likesWarmth: false, likesCuddling: false, likesSpace: false, likesSnacks: false, likesConversation: false, likesHouseholdHelp: false, likesReminders: false, customHelpfulActions: [], customUnhelpfulActions: [] } as never;
    expect(eligibleTips(builtIns, settings, { applicablePhases: [], sharedSymptoms: [], carePreferences: care }))
      .toEqual(eligibleTips(builtIns, settings));
  });

  it('excludes every explicitly mapped action after an unhelpful-action rule, including rewritten cards', () => {
    const allSharing = { ...settings as object, sharePeriodDates: true, sharePhase: true, shareFertileWindow: true, shareSymptoms: true, allowSymptomBasedTips: true } as never;
    const context = { applicablePhases: Object.values(CyclePhase), sharedSymptoms: ['CRAMPS', 'HEADACHE', 'FATIGUE', 'NAUSEA', 'STRESS', 'BACK_PAIN'], carePreferences: null };
    expect(eligibleTips(builtIns, allSharing, context)).toHaveLength(71);
    const aliases: Record<CareAction, string> = { warmth: 'warmth', cuddling: 'cuddle', space: 'space', snacks: 'Snack', conversation: 'conversation', household: 'household help', reminders: 'reminder' };
    for (const action of Object.keys(CARE_ACTION_IDS) as CareAction[]) {
      const allowed = eligibleTips(builtIns, allSharing, { ...context, carePreferences: { customUnhelpfulActions: [aliases[action]], customHelpfulActions: [] } as never });
      CARE_ACTION_IDS[action].forEach((id) => expect(allowed).not.toContain(builtIns[id - 1]));
    }
    expect(CARE_ACTION_IDS.conversation).toEqual(expect.arrayContaining([18, 30, 38, 70]));
    expect(CARE_ACTION_IDS.space).not.toContain(18);
    for (const id of [34, 47, 54]) expect(CARE_ACTION_IDS.household).not.toContain(id);
    expect(CARE_ACTION_IDS.household).toEqual(expect.arrayContaining([27, 39, 52]));
  });

  it('does not attach global care tags to a user-authored colliding ID', () => {
    const personal = { ...builtIns[10]!, category: TipCategory.USER_DEFINED, createdByUserId: 'tracker', householdId: 'pair', callToActionDe: 'Bitte bring Wasser.', callToActionEn: 'Please bring water.' };
    expect(carePreferenceScore(personal, { likesWarmth: true, customHelpfulActions: [] } as never)).toBe(0);
  });

  it.each([
    ['shareSymptoms', false, true], ['allowSymptomBasedTips', true, false],
  ])('withdrawal of %s hides every symptom card even with stale matching context', (_name, shareSymptoms, allowSymptomBasedTips) => {
    const context = { applicablePhases: [], sharedSymptoms: ['CRAMPS', 'HEADACHE', 'FATIGUE', 'NAUSEA', 'STRESS', 'BACK_PAIN'], carePreferences: null };
    const control = eligibleTips(builtIns, { ...settings as object, shareSymptoms: true, allowSymptomBasedTips: true } as never, context);
    expect(control.filter((tip) => tip.applicableSymptoms.length)).toHaveLength(6);
    const revoked = eligibleTips(builtIns, { ...settings as object, shareSymptoms, allowSymptomBasedTips } as never, context);
    expect(revoked.every((tip) => !tip.applicableSymptoms.length)).toBe(true);
  });

  it('phase context alone never grants permission, including a user-defined tip missing its declared gate', () => {
    const phaseTip = { ...builtIns[50]!, requiredSharedFields: [], category: TipCategory.USER_DEFINED };
    const context = { applicablePhases: [CyclePhase.FOLLICULAR_ESTIMATE], sharedSymptoms: [], carePreferences: null };
    expect(eligibleTips([phaseTip], { ...settings as object, sharePhase: true } as never, context)).toEqual([phaseTip]);
    expect(eligibleTips([phaseTip], settings, context)).toEqual([]);
  });

  it('private days reject field-only dependent wishes as well as phase/symptom cards', () => {
    const fieldOnly = { ...builtIns[0]!, id: 'private-field-wish', category: TipCategory.USER_DEFINED, requiredSharedFields: ['sharePain'] };
    const context = { applicablePhases: [CyclePhase.MENSTRUATION], sharedSymptoms: ['CRAMPS'], carePreferences: null };
    const allSharing = { ...settings as object, sharePain: true, sharePeriodDates: true, sharePhase: true, shareSymptoms: true, allowSymptomBasedTips: true } as never;
    expect(eligibleTips([fieldOnly], allSharing, context)).toEqual([fieldOnly]);
    const privateAllowed = eligibleTips([...builtIns, fieldOnly], allSharing, { ...context, privateDay: true });
    expect(privateAllowed.length).toBeGreaterThan(0);
    expect(privateAllowed.every((tip) => !tip.requiredSharedFields.length && !tip.applicablePhase && !tip.applicableSymptoms.length)).toBe(true);
  });
});
