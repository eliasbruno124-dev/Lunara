import { createHash } from 'node:crypto';
import { TipCategory } from '@prisma/client';
import { carePreferenceScore, SelectableTip, TipSelectionContext } from './tip-filter.js';

/** Caller MUST apply eligibleTips first: ranking can never grant a sharing permission. */
export function selectPartnerTip(
  candidates: SelectableTip[], userId: string, date: string,
  context: TipSelectionContext, variant = 0,
): SelectableTip | undefined {
  if (!candidates.length) return undefined;
  const day = Math.floor(Date.parse(`${date}T00:00:00Z`) / 86_400_000);
  if (!Number.isFinite(day)) throw new Error('Invalid tip date');
  // A stable per-person deck rotates daily without repeating until its pool is
  // exhausted. Viewing another suggestion is read-only; it never dismisses a tip.
  const ordered = [...candidates].sort((a, b) => {
    const key = (tip: SelectableTip) => createHash('sha256').update(`${userId}:${tip.id}:tip-deck-v3`).digest('hex');
    return key(a).localeCompare(key(b)) || a.id.localeCompare(b.id);
  });
  const personal = ordered.filter((tip) => tip.category === TipCategory.USER_DEFINED);
  const contextual = (tip: SelectableTip) => Boolean(tip.applicablePhase || tip.applicableSymptoms.length);
  const preferred = (tip: SelectableTip) => carePreferenceScore(tip, context.carePreferences) > 0 || tip.feedback.some((f) => f.helpful === true);
  const builtIns = ordered.filter((tip) => tip.category !== TipCategory.USER_DEFINED);
  const shared = spreadCategories(builtIns.filter(contextual));
  const preferences = spreadCategories(builtIns.filter((tip) => !contextual(tip) && preferred(tip)));
  const general = spreadCategories(builtIns.filter((tip) => !contextual(tip) && !preferred(tip)));
  // A broad preference such as household help must not crowd a currently
  // shared cycle/symptom context out of its two daily slots. Preferences still
  // rank first when there is no shared context, and remain in the variant deck.
  const relevant = shared.length ? shared : preferences;
  const rest = shared.length ? preferences : [];
  const rotate = (pool: SelectableTip[], offset: number) => {
    if (!pool.length) return pool;
    const index = ((offset % pool.length) + pool.length) % pool.length;
    return [...pool.slice(index), ...pool.slice(0, index)];
  };
  // Context/preferences get two days out of three; general suggestions keep
  // the relationship broader than a cycle. All entries remain explicitly optional.
  const slot = ((day % 3) + 3) % 3;
  const primary = slot === 2 && general.length ? general : relevant.length ? relevant : general;
  const secondary = primary === relevant ? general : relevant;
  const index = relevant.length && general.length
    ? Math.floor(day / 3) * (primary === relevant ? 2 : 1) + (primary === relevant ? Math.min(slot, 1) : 0)
    : day;
  const deck = [...rotate(personal, day), ...rotate(primary, index), ...rotate(rest, day), ...rotate(secondary, day)];
  return deck[variant % deck.length];
}

/** Interleave each eligible pool, preserving the per-person hash order within
 * categories. Do not peek at excluded candidates: even their count is private.
 * Repeated categories remain possible when one category outnumbers the rest.
 */
function spreadCategories(ordered: SelectableTip[]): SelectableTip[] {
  const buckets = new Map<TipCategory, SelectableTip[]>();
  for (const tip of ordered) {
    const bucket = buckets.get(tip.category) ?? [];
    bucket.push(tip);
    buckets.set(tip.category, bucket);
  }
  const result: SelectableTip[] = [];
  let previous: TipCategory | undefined;
  while (result.length < ordered.length) {
    const available = [...buckets.entries()].filter(([, tips]) => tips.length > 0);
    const alternatives = available.filter(([category]) => category !== previous);
    const [category, tips] = (alternatives.length ? alternatives : available)
      .sort((a, b) => b[1].length - a[1].length)[0]!;
    result.push(tips.shift()!);
    previous = category;
  }
  return result;
}
