import { PartnerTip, TipCategory } from '@prisma/client';

export type CareAction = 'warmth' | 'cuddling' | 'space' | 'snacks' | 'conversation' | 'household' | 'reminders';

/** Internal editorial metadata, never persisted or returned in the API. Match
 * actions, not category labels or words in a warning ("do not assume touch").
 * A preference qualifies an offer; it is never consent to perform the action.
 */
export const CARE_ACTION_IDS: Record<CareAction, readonly number[]> = {
  warmth: [11, 60],
  cuddling: [48],
  space: [14, 16, 17, 19, 20, 49, 61],
  snacks: [50],
  conversation: [2, 18, 26, 29, 30, 38, 42, 58, 67, 70],
  household: [1, 4, 6, 7, 8, 9, 10, 16, 19, 22, 23, 24, 25, 27, 31, 32, 36, 39, 43, 45, 46, 52, 62, 66, 68, 69],
  // Setting your OWN reminder is not a reminder sent to the tracker.
  reminders: [],
};

const ACTION_ALIASES: Record<CareAction, readonly string[]> = {
  warmth: ['Wärme', 'Wärmflasche', 'warmth', 'hot water bottle'],
  cuddling: ['Umarmung', 'Kuscheln', 'Berührung', 'hug', 'cuddle', 'touch'],
  space: ['Ruhe', 'Zeit allein', 'space', 'quiet', 'time alone'],
  snacks: ['Snack'],
  conversation: ['Gespräch', 'Zuhören', 'conversation', 'listening'],
  household: ['Haushaltshilfe', 'household help'],
  reminders: ['Erinnerung', 'reminder'],
};

export function catalogCareActions(tip: Pick<PartnerTip, 'id' | 'category' | 'householdId' | 'createdByUserId'>): CareAction[] | null {
  if (tip.category === TipCategory.USER_DEFINED || tip.householdId !== null || tip.createdByUserId !== null) return null;
  const match = /^10000000-0000-4000-8000-0{10}(\d{2})$/.exec(tip.id);
  const number = match ? Number(match[1]) : 0;
  if (number < 1 || number > 71) return null;
  return (Object.keys(CARE_ACTION_IDS) as CareAction[]).filter((action) => CARE_ACTION_IDS[action].includes(number));
}

export function catalogActionAliases(tip: Parameters<typeof catalogCareActions>[0]): string {
  return catalogCareActions(tip)?.flatMap((action) => ACTION_ALIASES[action]).join(' ') ?? '';
}
