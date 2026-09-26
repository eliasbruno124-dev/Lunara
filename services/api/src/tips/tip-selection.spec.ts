import { CyclePhase, TipCategory } from '@prisma/client';
import { TIP_CATALOG } from './tip-catalog.js';
import { EDITORIAL_COPY } from './tip-editorial.js';
import { carePreferenceScore, eligibleTips, SelectableTip, TipSelectionContext } from './tip-filter.js';
import { selectPartnerTip } from './tip-selection.js';

const context: TipSelectionContext = { applicablePhases: [], sharedSymptoms: [], carePreferences: null };
const candidates = TIP_CATALOG.map((tip) => ({
  ...tip, applicablePhase: tip.applicablePhase ?? null, applicableSymptoms: tip.applicableSymptoms ?? [],
  enabled: true, feedback: [], createdByUserId: null, householdId: null,
  createdAt: new Date(0), updatedAt: new Date(0),
})) as SelectableTip[];
const sharing = { tipsEnabled: true, sharingPaused: false, disabledTipCategories: [], sharePrediction: true } as never;

describe('partner suggestion deck', () => {
  it('puts explicit personal wishes ahead of catalog suggestions and preserves their exact words', () => {
    const personal = { ...candidates[0]!, id: 'personal', category: TipCategory.USER_DEFINED, createdByUserId: 'tracker', householdId: 'pair', callToActionDe: 'Bitte bring mir meinen Lieblingstee.' };
    for (let day = 21; day <= 24; day++) {
      expect(selectPartnerTip([...eligibleTips(candidates, sharing, context), personal], 'p', `2026-09-${day}`, context)).toBe(personal);
    }
  });
  it('keeps 71 stable unique translated IDs and updates only existing editorial slots', () => {
    expect(TIP_CATALOG).toHaveLength(71);
    expect(Object.keys(EDITORIAL_COPY)).toHaveLength(71);
    expect(new Set(TIP_CATALOG.map((tip) => tip.id)).size).toBe(71);
    expect(Object.keys(EDITORIAL_COPY).every((key) => Number(key) >= 1 && Number(key) <= 71)).toBe(true);
    for (const tip of TIP_CATALOG) {
      for (const field of ['titleDe', 'bodyDe', 'callToActionDe', 'titleEn', 'bodyEn', 'callToActionEn'] as const) {
        expect(tip[field].trim().length).toBeGreaterThan(field.startsWith('title') ? 3 : 15);
      }
    }
  });

  it('is deterministic and offers different suggestions without dismissal', () => {
    const permitted = eligibleTips(candidates, sharing, context);
    const deck = permitted.map((_, variant) => selectPartnerTip(permitted, 'p', '2026-09-21', context, variant)?.id);
    expect(new Set(deck).size).toBe(permitted.length);
    expect(selectPartnerTip(permitted, 'p', '2026-09-21', context)?.id).toBe(deck[0]);
    expect(candidates.every((tip) => tip.feedback.length === 0)).toBe(true);
  });

  it('has no daily repeats across a full general-only pool', () => {
    const permitted = eligibleTips(candidates, sharing, context);
    const dates = permitted.map((_, day) => new Date(Date.UTC(2026, 0, 1 + day)).toISOString().slice(0, 10));
    expect(new Set(dates.map((date) => selectPartnerTip(permitted, 'p', date, context)?.id)).size).toBe(permitted.length);
  });

  it('selects only permitted general tips on private days and after withdrawal', () => {
    const permitted = eligibleTips(candidates, sharing, context);
    for (let variant = 0; variant <= 100; variant++) {
      const tip = selectPartnerTip(permitted, 'p', '2026-09-21', context, variant)!;
      expect(tip.applicablePhase).toBeNull();
      expect(tip.applicableSymptoms).toEqual([]);
      expect(tip.requiredSharedFields).toEqual([]);
    }
  });

  it('prioritises matching shared phase on two days of three without inferring mood', () => {
    const selection = { ...context, applicablePhases: [CyclePhase.MENSTRUATION] };
    const allowed = eligibleTips(candidates, { ...sharing as object, sharePeriodDates: true } as never, selection);
    const count = [0, 1, 2].map((day) => selectPartnerTip(allowed, 'p', `2026-09-${21 + day}`, selection))
      .filter((tip) => tip?.applicablePhase === CyclePhase.MENSTRUATION).length;
    expect(count).toBe(2);
  });

  it('uses actual care preferences, excludes unwanted actions and respects paused sharing', () => {
    const selection = { ...context, carePreferences: { likesHouseholdHelp: true, customUnhelpfulActions: ['Spülmaschine'], customHelpfulActions: [] } as never };
    const allowed = eligibleTips(candidates, sharing, selection);
    expect(allowed.some((tip) => tip.callToActionDe.includes('Spülmaschine'))).toBe(false);
    const chosen = [0, 1, 2].map((day) => selectPartnerTip(allowed, 'p', `2026-09-${21 + day}`, selection));
    expect(chosen.filter((tip) => tip && carePreferenceScore(tip, selection.carePreferences) > 0)).toHaveLength(2);
    expect(eligibleTips(candidates, { ...sharing as object, sharingPaused: true } as never)).toEqual([]);
  });

  it('interleaves categories inside the general deck without duplicating or losing IDs', () => {
    const balanced = [TipCategory.COMFORT, TipCategory.SPACE, TipCategory.PRACTICAL_HELP].flatMap((category) =>
      Array.from({ length: 4 }, (_, i) => ({ ...candidates[0]!, id: `${category}-${i}`, category })),
    );
    const deck = balanced.map((_, variant) => selectPartnerTip(balanced, 'p', '2026-09-21', context, variant)!);
    expect(new Set(deck.map((tip) => tip.id)).size).toBe(balanced.length);
    for (let i = 1; i < deck.length; i++) expect(deck[i]!.category).not.toBe(deck[i - 1]!.category);
  });

  it('adding excluded health candidates cannot change visible ranking for any variant', () => {
    const general = candidates.filter((tip) => !tip.requiredSharedFields.length && !tip.applicablePhase && !tip.applicableSymptoms.length);
    const hidden = candidates.filter((tip) => !general.includes(tip)).map((tip) => ({ ...tip, feedback: [{ helpful: true, dismissed: false }] } as SelectableTip));
    const allowed = eligibleTips([...general, ...hidden], sharing, context);
    expect(allowed.map((tip) => tip.id)).toEqual(general.map((tip) => tip.id));
    for (let variant = 0; variant <= 100; variant++) {
      expect(selectPartnerTip(allowed, 'p', '2026-09-21', context, variant)?.id)
        .toBe(selectPartnerTip(general, 'p', '2026-09-21', context, variant)?.id);
    }
  });

  it('helpful feedback increases relevance but cannot override a dismissal or disabled category', () => {
    const helpful = { ...candidates[0]!, feedback: [{ helpful: true, dismissed: false }] } as SelectableTip;
    const general = { ...candidates[5]!, id: 'general-control' };
    const selected = [21, 22, 23].map((day) => selectPartnerTip([helpful, general], 'p', `2026-09-${day}`, context));
    expect(selected.filter((tip) => tip === helpful)).toHaveLength(2);
    const dismissed = { ...helpful, feedback: [{ helpful: true, dismissed: true }] } as SelectableTip;
    expect(eligibleTips([dismissed, general], sharing, context)).toEqual([general]);
    expect(eligibleTips([helpful, general], { ...sharing as object, disabledTipCategories: [helpful.category] } as never, context)).toEqual([general]);
  });

  it('rotates multiple personal wishes first without rewriting or mixing their language slots', () => {
    const wishes = ['Bitte um 18 Uhr.', 'Bitte erst morgen.'].map((text, i) => ({
      ...candidates[0]!, id: `wish-${i}`, category: TipCategory.USER_DEFINED,
      householdId: 'pair', createdByUserId: 'tracker', callToActionDe: text, callToActionEn: text,
    }));
    const selected = [21, 22].map((day) => selectPartnerTip([...candidates.slice(0, 2), ...wishes], 'p', `2026-09-${day}`, context)!);
    expect(new Set(selected.map((tip) => tip.id)).size).toBe(2);
    selected.forEach((tip) => {
      expect(wishes).toContain(tip);
      expect(tip.callToActionDe).toBe(tip.callToActionEn);
    });
  });
});
