# Partner tips: seed data and privacy rules

Lunara's runtime source is `services/api/src/tips/tip-catalog.ts` with complete
DE/EN copy for all 71 stable identities in `tip-editorial.ts`. There is no
legacy-copy fallback. Internal action/preference mappings live in
`tip-care-actions.ts`; they do not change the database or public DTO.
`tip-sources.ts` assigns four fixed primary-source URLs to 23 built-in cards,
using the existing nullable source field. It never attributes personal content.
`docs/partner-tips.seed.json` is a generated
snapshot, not a separate database seed. Its keys are the actual stable UUIDs.
After building the API, run `node scripts/export-tip-catalog.mjs`; add `--check`
to verify that the snapshot still matches. Built-in records are immutable from
the user's perspective; editorial updates preserve IDs, feedback and suppression.
See [TIP_QUALITY.md](TIP_QUALITY.md) for personal wishes, sources and limitations.

The tips are deliberately small, optional suggestions. They do not diagnose,
prescribe treatment, infer emotions from a cycle phase, or replace a direct
conversation. Follow an existing agreement for your own/shared routine jobs;
ask before touch, personal belongings, spending or changing another person's
plans. Do not turn every card into another question or a task for the tracker.
Every card has short context and a concrete invitation or action. Phase-sharing
cards include a brief educational fact; original date ideas are not presented
as hormone-based prescriptions. Boundaries are stated where needed, without
repeating a lecture on every card. Public Flo descriptions inspired the format,
not copied Premium content or a claim of equivalent medical review.
If it does not fit today, skip it. See the editorial rubric and sourced limits
in [TIP_QUALITY.md](TIP_QUALITY.md).

## Seed schema

Each item contains:

- `key`: stable unique identifier used for seeding and feedback references.
- `category`: one of `COMMUNICATION`, `PRACTICAL_HELP`, `COMFORT`,
  `SPACE`, `FOOD_AND_DRINK`, `EMOTIONAL_SUPPORT`, `PERIOD_DAYS`,
  `PREMENSTRUAL_ESTIMATE`, `GENERAL`, or `USER_DEFINED`.
- `priority`: retained catalogue metadata from 0 to 100. Since 1.1.6 it does
  not override the daily rotating selection or any eligibility rule.
- `requiredSharedFields`: sharing switches that must all currently be true.
- `applicablePhase`: eligible estimated or recorded phases. `ANY` means the
  item does not use cycle phase as an input.
- `applicableSymptoms`: symptom keys that may make the item eligible. An empty
  array means that no symptom data is used.
- `enabled`: global seed default.
- `translations.de` and `translations.en`: complete `title`, `body`,
  and `callToAction` values.

`USER_DEFINED` is intentionally absent from the built-in seed. It is reserved
for text authored by the tracker and scoped to that internal relationship
tenant.

## Eligibility and selection

The server selects a tip from a privacy-filtered context. Current behaviour:

1. Resolve the authenticated partner's active relationship membership. Never
   accept a relationship/tenant or owner ID supplied by the client as
   authorization.
2. Read the current tracker sharing settings, private-day state, sharing pause,
   category preferences, care preferences, and dismissed-tip feedback. Quiet
   hours and notification frequency are enforced separately by notifications;
   they do not prevent explicitly opening a tip in the foreground.
3. Discard disabled tips and disabled categories.
4. Require every value in `requiredSharedFields` to be true. A missing or
   unknown field is false (fail closed).
5. For a non-empty `applicableSymptoms`, require both `shareSymptoms` and
   `allowSymptomBasedTips`, then match only against the already filtered set
   of shared symptom keys.
6. Apply phase matching only after its disclosure gate is satisfied:
   `MENSTRUATION` requires the declared `sharePeriodDates` or `sharePhase`,
   `PREMENSTRUAL_ESTIMATE` derived from the prediction requires
   `sharePrediction`, and any other phase-specific item requires
   `sharePhase`; `OVULATION_ESTIMATE` always needs `shareFertileWindow`.
   The phase tag itself grants no permission, even for a custom tip that omits
   `requiredSharedFields`. A private day excludes every dependent tip,
   including field-only custom wishes without a phase/symptom tag.
7. Exclude suggestions that conflict with care preferences or a custom
   unhelpful-action rule. Prefer explicitly liked actions, but never infer a
   preference from health data. Built-in actions are explicitly mapped; the
   words in a warning or a broad category are not evidence of a liked action.
   A false likes flag is unspecified, not an explicit prohibition. Free-text
   unhelpful actions use normalized substring matching against the CTA and
   known action aliases; this does not understand arbitrary synonyms/negations.
8. Exclude dismissed tips. Permitted phase/symptom context forms the contextual
   pool. Helpful feedback and explicitly liked actions form a separate preferred
   pool, so broad care preferences cannot crowd out shared cycle context.
   There is currently no independent partner-side per-category frequency control.
9. Put explicitly shared personal wishes first. Rotate the permitted catalogue
   deterministically each day, giving the contextual pool two days out of three
   and general ideas the third day when both are available. Without contextual
   candidates, the preferred pool receives those two slots. All remaining
   eligible entries stay in the variant deck; with only one pool, use that pool.
   Categories are
   interleaved within each pool without inspecting excluded tips. A skewed
   pool, pool boundary or personal-wish priority can still repeat a category.
   `variant=0..100`
   browses the eligible deck without changing feedback. Return one tip.
10. If no data-dependent item is eligible, use a general item with empty
    `requiredSharedFields`, `ANY`, and no symptoms. Returning no tip is also
    valid when tips are disabled.

The stable deck uses a SHA-256 hash of the authenticated user UUID and tip
UUID, interleaves categories, then rotates by local date. It is variety, not cryptography for an
authorization decision. Only eligible candidates participate. A change to
sharing or personal wishes can change the deck; ordering never grants access.

## Non-negotiable privacy behavior

- A tip with a failed sharing requirement must not be returned, hinted at,
  counted, or used to alter visible ranking.
- Symptom matching uses only explicitly shared `customSymptoms` keys and
  requires both `shareSymptoms` and `allowSymptomBasedTips`. Numeric pain,
  mood, flow, energy, sleep and notes are never converted into symptom keys,
  even when their dedicated sharing switch is enabled. A custom wish may
  explicitly require such a sharing switch, but that is a permission condition,
  not a classifier of the field's value. Explicitly shared fatigue is distinct
  from the separate private energy field.
- The API response contains the selected localized copy and safe metadata.
  `selectionReason` is only a broad label: personal request, care preference,
  shared symptom/context, or general idea. It includes no symptom keys, pain
  level, log values, notes or excluded candidates. A source link is attached only
  to the 23 mapped global cards, never inferred for user-authored or household
  content. A source supports factual context, not the original relationship idea.
- Copy must not interpolate a person's name, a symptom value, a severity, a
  note, a predicted emotion, or an unshared event. Built-in copy remains static.
- A sharing change, partner disconnect, private day, or global sharing pause
  takes effect on the next server authorization check. A disconnected phone
  cannot learn of a revocation immediately: previously delivered data and
  screenshots cannot be retroactively withdrawn. The app refreshes while open
  and uses the existing protected partner-cache policy; this is not remote erasure.
- A private day removes all day-specific inputs. Only general, data-independent
  tips may remain if the tracker has allowed general tips.
- Notes are never used for built-in tip selection, even when note sharing is
  enabled. Free text must not be classified to generate a partner tip.
- The server, not Android UI state, enforces all gates. Direct API requests
  receive the same filtered result.
- Logs and audit events may record that a tip was delivered, dismissed, or
  rated. They must not record the health-data reason for its eligibility.

## Content rules

Built-in and user-defined tips should:

- use gender-neutral language and avoid role or relationship stereotypes;
- describe options, not commands directed at the tracker;
- preserve autonomy and make declining help easy;
- avoid diagnoses, medication suggestions and individual claims about
  ovulation, fertility, pregnancy or emotional state; brief sourced general
  cycle facts and appropriately conditional help-seeking information are allowed;
- call predictions estimates and never present them as exact;
- avoid implying that a cycle phase explains behavior;
- remain useful when read without hidden health context.

User-defined tips are validated for length and plain text, rendered without
HTML, and visible only inside their relationship tenant. Android 1.1.6 supports
creating and disabling them; the API also supports editing. They are never
promoted into the global seed. Existing private notes are not used as wishes.

## Localization

The server selects the account's preferred language, with English as the product
default (since 1.3.5) and German as the second supported language. A seed migration must fail if
either locale or any of `title`, `body`, and `callToAction` is missing.
Clients must not combine fields from different locales in a single tip.
Personal wishes entered in Android preserve the author's words in both language
slots; the app does not silently translate or paraphrase them.

## Required tests

At minimum, automated tests should prove:

- every seed key is unique and every category, priority, phase, symptom, and
  sharing-field value is valid;
- every built-in tip has non-empty German and English fields;
- general tips remain eligible without health sharing;
- disabling one required field makes the dependent tip ineligible;
- symptom tips require both `shareSymptoms` and
  `allowSymptomBasedTips`;
- private notes and pain/mood/flow/energy/sleep values never produce symptom
  matches; any explicitly declared dedicated switch is required;
- private-day and sharing-pause modes remove all data-dependent candidates;
- changing a permission prevents a previously eligible tip from being returned
  immediately;
- an authenticated member cannot influence or retrieve another relationship
  tenant's candidate set;
- the response and structured logs contain no unshared symptom, note or severity;
  only the documented coarse selection label is returned, not the matched value.
- category interleaving preserves every eligible ID; inserting a disallowed
  health candidate cannot change the displayed deck, even if it has feedback;
- care preferences match concrete built-in actions, not incidental words:
  liking warmth does not favour hugs, liking conversation does not favour
  asking again after a no, and own reminders do not favour reminders to the tracker;
- known empty-action and unsafe-claim fixtures fail the editorial tripwires,
  and both warmth cards retain consent, cloth and personal temperature checks.
- broad care preferences cannot crowd eligible shared context out of its slots;
- all educational phase-sharing cards have a fixed factual source, and no
  household or personal card inherits one even if its ID collides with a built-in.
