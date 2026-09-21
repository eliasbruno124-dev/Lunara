import { CyclePhase, TipCategory } from '@prisma/client';
import { carePreferenceScore, eligibleTips } from './tip-filter.js';

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
});
