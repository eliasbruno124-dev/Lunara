# Partner tips: seed data and privacy rules

Lunara's built-in partner tips are stored in
`docs/partner-tips.seed.json`. The seed contains stable, language-neutral
keys and complete German and English copy. Built-in records are immutable from
the user's perspective; feedback and suppression are stored separately.

The tips are deliberately small, optional suggestions. They do not diagnose,
prescribe treatment, infer emotions from a cycle phase, or replace a direct
conversation. The default principle is: ask first, then act.

## Seed schema

Each item contains:

- `key`: stable unique identifier used for seeding and feedback references.
- `category`: one of `COMMUNICATION`, `PRACTICAL_HELP`, `COMFORT`,
  `SPACE`, `FOOD_AND_DRINK`, `EMOTIONAL_SUPPORT`, `PERIOD_DAYS`,
  `PREMENSTRUAL_ESTIMATE`, `GENERAL`, or `USER_DEFINED`.
- `priority`: integer from 0 to 100; higher values are considered first after
  privacy filtering. Priority never bypasses eligibility rules.
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

The server must select a tip from a privacy-filtered view, not directly from a
tracker's health records. The recommended order is:

1. Resolve the authenticated partner's active relationship membership. Never
   accept a relationship/tenant or owner ID supplied by the client as
   authorization.
2. Read the current tracker sharing settings, private-day state, sharing pause,
   category preferences, care preferences, quiet hours, and dismissed-tip
   feedback.
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
8. Exclude dismissed tips and reduce the weight of categories the partner has
   marked as less frequent. Honor per-day and per-category frequency limits.
9. Rank the remaining candidates by priority, preference match, recent
   repetition penalty, and a deterministic daily tie-breaker scoped to the
   internal relationship tenant. Select at most the configured number of tips.
10. If no data-dependent item is eligible, use a general item with empty
    `requiredSharedFields`, `ANY`, and no symptoms. Returning no tip is also
    valid when tips are disabled.

The deterministic tie-breaker should use a keyed server-side hash of the
internal relationship ID, local date, and tip key. It must not expose
identifiers and must not make private data observable through ordering.

## Non-negotiable privacy behavior

- A tip with a failed sharing requirement must not be returned, hinted at,
  counted, or used to alter visible ranking.
- Symptom matching is allowed only when both the data field and
  `allowSymptomBasedTips` are enabled. For pain, mood, flow, energy, and
  sleep, the corresponding dedicated sharing switch is also required.
- The API response must contain only the selected localized copy and safe
  metadata. It must not include the match reason, raw symptom keys, pain level,
  log values, or excluded candidates.
- Copy must not interpolate a person's name, a symptom value, a severity, a
  note, a predicted emotion, or an unshared event. Built-in copy remains static.
- A sharing change, partner disconnect, private day, or global sharing pause
  takes effect on the next authorization check. Partner-dashboard and tip
  caches must be invalidated immediately and must never outlive authorization.
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
HTML, and visible only inside their relationship tenant. The tracker can edit
or disable them at any time. They must never be promoted into the global seed.

## Localization

The server selects the account's preferred language, with German as the product
default and English as the supported fallback. A seed migration must fail if
either locale or any of `title`, `body`, and `callToAction` is missing.
Clients must not combine fields from different locales in a single tip.

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
- the response and structured logs contain no unshared symptom, note, severity,
  or matching reason.
