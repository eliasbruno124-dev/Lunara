# Partner tips: seed data and privacy rules

Lunara's runtime source is `services/api/src/tips/tip-catalog.ts` with text
revisions in `tip-editorial.ts`. `docs/partner-tips.seed.json` is a generated
snapshot, not a separate database seed. Its keys are the actual stable UUIDs.
After building the API, run `node scripts/export-tip-catalog.mjs`; add `--check`
to verify that the snapshot still matches. Built-in records are immutable from
the user's perspective; editorial updates preserve IDs, feedback and suppression.
See [TIP_QUALITY.md](TIP_QUALITY.md) for personal wishes, sources and limitations.

The tips are deliberately small, optional suggestions. They do not diagnose,
prescribe treatment, infer emotions from a cycle phase, or replace a direct
conversation. The default principle is: ask first, then act.

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
   `MENSTRUAL` requires `sharePeriodDates`,
   `PREMENSTRUAL_ESTIMATE` derived from the prediction requires
   `sharePrediction`, and any other phase-specific item requires
   `sharePhase`. The phase tag itself grants no permission.
7. Exclude suggestions that conflict with care preferences or a custom
   unhelpful-action rule. Prefer explicitly liked actions, but never infer a
   preference from health data.
8. Exclude dismissed tips. Helpful feedback, explicitly liked actions and
   permitted phase/symptom context qualify a suggestion for the relevant pool.
   There is currently no independent partner-side per-category frequency control.
9. Put explicitly shared personal wishes first. Rotate the permitted catalogue
   deterministically each day, giving the relevant pool two days out of three
   and general ideas the third day when both are available. `variant=0..100`
   browses the eligible deck without changing feedback. Return one tip.
10. If no data-dependent item is eligible, use a general item with empty
    `requiredSharedFields`, `ANY`, and no symptoms. Returning no tip is also
    valid when tips are disabled.

The stable deck uses a SHA-256 hash of the authenticated user UUID and tip
UUID, then rotates by local date. It is variety, not cryptography for an
authorization decision. Only eligible candidates participate. A change to
sharing or personal wishes can change the deck; ordering never grants access.

## Non-negotiable privacy behavior

- A tip with a failed sharing requirement must not be returned, hinted at,
  counted, or used to alter visible ranking.
- Symptom matching is allowed only when both the data field and
  `allowSymptomBasedTips` are enabled. For pain, mood, flow, energy, and
  sleep, the corresponding dedicated sharing switch is also required.
- The API response contains the selected localized copy and safe metadata.
  `selectionReason` is only a broad label: personal request, care preference,
  shared symptom/context, or general idea. It includes no symptom keys, pain
  level, log values, notes or excluded candidates. A source link is attached only
  to the two matching global warmth tips, never inferred for a user-authored tip.
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
- avoid medical advice, diagnoses, medication suggestions, or claims about
  ovulation, fertility, pregnancy, or emotional state;
- call predictions estimates and never present them as exact;
- avoid implying that a cycle phase explains behavior;
- remain useful when read without hidden health context.

User-defined tips are validated for length and plain text, rendered without
HTML, and visible only inside their relationship tenant. Android 1.1.6 supports
creating and disabling them; the API also supports editing. They are never
promoted into the global seed. Existing private notes are not used as wishes.

## Localization

The server selects the account's preferred language, with German as the product
default and English as the supported fallback. A seed migration must fail if
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
- pain, mood, flow, energy, and sleep tips require their dedicated switches;
- private-day and sharing-pause modes remove all data-dependent candidates;
- changing a permission prevents a previously eligible tip from being returned
  immediately;
- an authenticated member cannot influence or retrieve another relationship
  tenant's candidate set;
- the response and structured logs contain no unshared symptom, note or severity;
  only the documented coarse selection label is returned, not the matched value.
