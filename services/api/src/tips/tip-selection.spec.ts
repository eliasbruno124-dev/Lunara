import { CyclePhase, TipCategory } from '@prisma/client';
import { TIP_CATALOG } from './tip-catalog.js';
import { EDITORIAL_COPY } from './tip-editorial.js';
import { eligibleTips, SelectableTip, TipSelectionContext } from './tip-filter.js';
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
    expect(chosen.filter((tip) => tip?.category === TipCategory.PRACTICAL_HELP)).toHaveLength(2);
    expect(eligibleTips(candidates, { ...sharing as object, sharingPaused: true } as never)).toEqual([]);
  });
});
