# Tests

This file distinguishes checks that were actually performed from test source
files that merely exist. The currently verified results are listed in
`FINAL_REPORT.md`. Checks that are still running or have not been evaluated
do not count as passed. The matrix below is retained as the historical 1.1.0 verification.

## Additional checks for 1.3.2

`CalendarColorsTest` and `CalendarDayKindTest` check semantic colors,
text contrast, and category priority. `CalendarColorsComposeTest` renders both
themes with normal and large text and checks text layout, actual fill colors,
and the legend. All fixtures use synthetic data.

On the web, `calendar-presentation.test.ts` and `presentation.test.ts` check the
same prediction boundaries, legend text, category priorities, and theme colors.
During visual verification, **all seven columns must remain within their own
grid slots**. A scrollable calendar card can hide clipped cells even when the
page itself has no horizontal overflow. Also check Today, Calendar, and Settings
at 320px, phone, and desktop widths in both themes. Navigation labels must not
break in the middle of a word.

## Additional checks for 1.3.1

`CalendarStatusPolicyTest` and `CalendarSharingComposeTest` check actual periods,
separate sharing permissions, estimate status, loading errors, and revocation.
`PeriodExportFormatTest` checks CSV/JSON, Unicode, open end dates, limits,
ambiguities, duplicates, and overlaps. The device test classes
`PeriodExportComposeTest` and `PeriodImportAtomicDeviceTest` check confirmation
and atomic Room transactions, including rollback during an account change
or failed queue creation.

Web regression cases are in `period-transfer.test.ts`,
`session-boundary.test.ts`, `partner-calendar.test.ts`, and
`fertility-presentation.test.ts`. The real PostgreSQL core flow checks a
read-only preview, atomic conflict rejection, repeated import without
duplicates, and rejected partner access. `SetupScreenshotsTest` generates
only synthetic setup screenshots.

```bash
npm test
npm run lint
npm run build
npm run openapi:check
cd apps/android
./gradlew testLocalDebugUnitTest lintProductionRelease
./gradlew assembleProductionDebug assembleProductionDebugAndroidTest
```

Device tests require an isolated emulator. Never include real health data
in test fixtures or screenshots. Backend integration requires a PostgreSQL
database dedicated exclusively to testing. The session persistence test
intentionally rejects other database targets.

## Additional checks for 1.2.1

`RedesignComposeTest` checks the outlook in the first visible area of the
partner view and the tip card below it that can be reached by scrolling.
A separate case checks the private placeholder view without a shared prediction.
Both are captured in German and English as real Compose views.

`TipSourcePolicyTest` and `tip-source.test.ts` check the exact public source
allowlist against HTTP, similar third-party domains, URL credentials, and
private query/fragment suffixes. Sources are not requested in the background.
Browser visual verification also measures the actual DOM order and vertical
card positions between 320 and 1280 pixels. Running checks are counted in
`FINAL_REPORT.md` only after their complete results are available.

## Additional checks for 1.2.0

The following sections describe the existing test sources and their coverage,
without reporting results for a build or device test that is still running.

`SettingsSectionsTest` checks the available settings categories for the tracker,
partner, and local-only mode. Tracker sections remain role-restricted, while
local-only mode retains account/backup and cycle settings without a server connection.
`JournalPredictionPolicyTest` checks display of a valid estimate window and
hiding it when predictions are paused, data is missing or invalid, or dates
are in reverse order.

`RedesignComposeTest` uses only synthetic data. Its instrumentation cases
check, among other things, the accessible period action, the outlook in dark
mode, partner connection and tip card, calendar swiping, recorded history
lengths, entering and leaving a settings category, and the visible local-only
option during onboarding. Additional cases use a font scale of 1.6 and check
the period action and expandable explanation of tip selection. Screenshots
are captured from the actual Compose interfaces using synthetic accounts.
This does not replace a complete device/language matrix, TalkBack audit,
or accessibility audit.

The API test sources supplement the 71 stable IDs and complete DE/EN texts
with specific preference-to-action mappings, unchanged personal wishes,
category variety without losing eligible IDs, and exclusions before ranking.
Revoked symptom sharing and private days must not produce dependent tips.
Private days also exclude custom wishes that depend on fields, even without
phase/symptom tags. Notes, numeric pain scores, mood, energy, and sleep are
not reinterpreted as shared symptom keys. Text patterns with positive and
negative controls are limited regression tests, without establishing medical
or editorial quality.

### Manual device workflow on a hosted runner

On GitHub, select the branch to check under **Actions → Android CI → Run workflow**
and enable **ui_only**. The manual `workflow_dispatch` run then starts only the
`device-validation` job from `.github/workflows/android.yml`. The separate
lint, JVM test, and release build jobs do not run as part of that execution.

The device job builds the production debug and instrumentation APKs, starts
an API-36 emulator on an ephemeral GitHub-hosted runner, and executes
`scripts/android-ui-ci.sh`. The script first checks all instrumentation with
the app language set to German and, if successful, runs `RedesignComposeTest`
again in English. Logs and synthetic screenshots are stored in the workflow
artifact `android-device-validation`. `scripts/verify-android-instrumentation.mjs`
checks fully reported test results instead of accepting only the `adb`
exit code. Screenshots must also be reviewed visually.

Do not start a local emulator on the production home server. The device script
refuses to run outside a GitHub-hosted runner. Real accounts and production
databases are not test fixtures. A successful emulator run would not establish
that physical camera testing, interactive Authentik browser sign-in, or testing
on all manufacturers' devices has been completed. Run ID, commit, result,
and manual visual review belong in the final verification report. No pending
run is recorded here as passed.

## Additional checks for 1.1.6

`RelationshipPolicyTest` checks unknown/connected/stale states, prevention of
self-linking, and cache isolation between servers and accounts.
`ConnectionPresentationTest` checks status priority, separate versions,
inclusive start windows, and safe PayPal.me URLs with negative controls.
`PersonalTipDraftPolicyTest` checks unchanged personal text and limits.
`ConnectionQualityComposeTest` checks the corresponding actual Compose
interfaces, including explicit sharing of a custom wish.

New API tests check rotation of the eligible tip set, personal wishes before
general tips, the specific warmth preference rather than a touch preference,
variants without hiding, and safe updates to existing catalog text.
The PostgreSQL suite also checks variant validation and retrieving the actual
relationship as a partner. These tests explicitly do not establish clinical
effectiveness of the tips or prospective prediction accuracy.

## Additional checks for 1.1.5

`services/api/test/refresh-replay.e2e-spec.ts` checks lost responses,
concurrent identical refreshes, replacement-session revocation, late known
replays, unknown token reuse, device-family isolation, and concurrent sign-outs
against a real, isolated PostgreSQL instance. The end-to-end case repeats
the same offline mutation and checks that exactly one period entry is stored
and shared with the partner.

This additional suite explicitly requires `NODE_ENV=test` and the standard
E2E database `lunara_test` or a dedicated `lunara_sync115_test` database with
an optional numeric run suffix. The E2E runner builds the API before the
integrated admin CLI test. The suite creates and removes only its own
synthetic fixtures. Do not use a production database as a test target.

New Android policy tests check identity/server boundaries, cancellation states,
authentication errors versus network errors, preserved mutation IDs, stale
responses after token rotation, and partner refresh without tips.
`SessionSecretsTest` checks that the refresh request ID remains stable across
restarts. These are JVM/Robolectric tests, without a verified run on the actual
phone. Instrumentation sources are compiled. Browser sign-in, power-saving
mode, and two-device synchronization must also be checked on the installed devices.

After a successful API update, update both APKs, renew any old sign-in prompt
once using the same account, create an entry offline, enable the network,
and check the shared partner state. Do not sign out while local entries
still need to be uploaded.

## Verified 1.1.0 matrix

### Backend

- **26/26 Jest suites, 177/177 unit tests:** passed
- TypeScript/Nest build: passed
- ESLint on API sources: passed
- Prisma generation and schema validation: passed
- OpenAPI snapshot and generated Kotlin tree: reproducibly verified
- Real temporary PostgreSQL-18 instance: all **eight migrations** applied
- Core end-to-end: **1/1 suite, 1/1 complete test** passed

The first 1.1.0 E2E attempt intentionally exposed the new stale/fail-closed
prediction boundary. The test anchor was corrected to use a current observation,
after which the same complete core flow passed. This first run is not counted
as a success.

The backend suites specifically check:

- Bootstrap and permanently disabled setup mode;
- Argon2id, sign-in limits, generic errors, password changes,
  refresh rotation, and replay revocation;
- OIDC issuer/audience/subject/`iat`/`exp`/`azp`, JWKS,
  algorithm restrictions, linking, and external identity during refresh;
- Roles and cross-relationship/IDOR isolation;
- Invitation, partner joining, and server-side sharing filters;
- 71 tip texts, Care Preferences, and fail-closed symptom filters;
- `lunara-robust-v2`, robust outlier handling, stale boundary,
  special modes, and optional fertile window;
- Period overlap, import, export, soft deletes, cursor sync,
  idempotency, conflicts, and recovery of old receipts;
- Public uncached metadata contract, asset links, and safe
  SPA route boundaries.

### PWA

- TypeScript lint/typecheck: passed
- Vite/PWA production build: passed
- **11 test files, 39 Vitest tests:** passed
- `npm audit --omit=dev` and the complete `npm audit`: 0 reported
  vulnerabilities each

The tests check calendar presentation, the local prediction vector, the offline
queue, account-specific cache/queue isolation, delayed refresh/sync responses,
deletion against concurrent vault writes, same-origin dual headers, the
portable backup format, and the passphrase-encrypted local-only vault. The
service worker excludes API, health, ready, OpenAPI, Authentik outpost, and
`.well-known` paths from caching and the navigation fallback.

The backend and PWA therefore total **216 successful unit/component tests**
(177 + 39). The separate PostgreSQL core E2E test is not included.

The backup created immediately before the production 1.1.0 rollout was
actually restored rather than merely cataloged. `database.dump` was fully
restored in an isolated temporary PostgreSQL-18 container with
`pg_restore --exit-on-error`. Seven backed-up migrations, one user, three
periods, and one daily log were present. The container was automatically
removed afterward. The new eighth migration was then successfully applied
to production.

### Android

- `testLocalDebugUnitTest`: **36 suites, 174 tests**, 0 failures
- `testProductionDebugUnitTest`: **36 suites, 174 tests**, 0 failures
- **348 unit test executions** in total across both flavors
- `lintLocalDebug`: **0 errors, 64 warnings**
- `lintProductionDebug`: **0 errors, 63 warnings**
- Both lint runs additionally included **1 informational finding** each,
  with no errors
- `compileProductionDebugAndroidTestKotlin`: passed
- Production instrumentation sources: compiled, but **not executed on an
  emulator or device**
- Production debug APK, signed/unsigned release APKs, and signed/unsigned
  production AABs: built
- Manifest, version/SDK levels, ZIP/bundle structure, and expected
  signature states: checked

The Android JVM suites cover, among other things:

- A genuinely empty server address and URL/origin validation;
- Direct, technical compatibility, and OIDC dual headers;
- Authentik PKCE/refresh states and strict manifest separation between the
  production app link and the custom scheme restricted to the `local` flavor;
- Local identity and complete network/sync blocking in local-only mode;
- SQLCipher/Keystore-related policy logic, safe session deletion,
  and DataStore isolation;
- Portable backup cryptography, schema/range validation,
  and import journal recovery;
- Room mutation queue, idempotency, backoff, and automatic synchronization;
- Role-dependent navigation, sharing, and partner cache cleanup;
- Swipeable calendar, future months, actual/estimated days,
  two-tap entry, correction, open period, deletion,
  and overlap protection;
- Local prediction and fertility boundaries;
- Notification timing, quiet hours, deduplication, DST, and neutral previews.

### Container

- Multi-stage build with API and PWA: passed
- `npm ci`, API build, and PWA build within the fresh build stage: passed
- Container configuration checked for non-root UID/GID `10001:10001`,
  read-only root, capability dropping, `no-new-privileges`, healthcheck,
  and absence of host ports
- Direct Compose configuration via `scripts/compose.sh config --quiet`: passed

### App link and signing

- The production app link for `lunara.3li361.de` was verified with the
  corresponding release certificate.
- The signed production app uses the fixed verified publisher app link
  independently of the self-hosting domain. Production Authentik does not
  register a custom-scheme/debug redirect.
- The custom scheme is present only in the Android `local` flavor for a separate
  development provider with its own client_id. Production and production debug
  do not contain such an intent filter.
- The upload key exists exclusively on the server. Gradle received it through
  `LUNARA_UPLOAD_KEYSTORE`, `LUNARA_UPLOAD_KEYSTORE_PASSWORD`,
  `LUNARA_UPLOAD_KEY_ALIAS`, and `LUNARA_UPLOAD_KEY_PASSWORD`.
- The signed release APK and upload AAB were recognized as signed,
  and the two `-unsigned` artifacts as unsigned, as expected.
- `sha256sum -c artifacts/SHA256SUMS` confirmed all five final
  release-1.1.0 artifacts.
- For Google Play, app-link verification must be repeated with the separate
  Play app-signing certificate after enabling Play App Signing.

## Checks not claimed as passed (historical 1.1.0 results)

- Instrumented Compose/Room/WorkManager tests were not executed on a device.
- A complete physical device/OS matrix is missing for app links,
  separate local development OIDC, Keystore, SQLCipher, WorkManager, TalkBack,
  text sizes, and reduced motion.
- PWA installation, offline upgrades, and browser background behavior were
  not fully checked on physical iOS/iPadOS/Safari devices.
- Screenshot protection and browser notifications cannot run in the PWA.
  Imported privacy/notification preferences remain securely stored in the
  vault, and the web app indicates that they do not apply.
- A new PWA process requires the local-only vault passphrase again.
  The local vault cannot be restored without it.
- A production database restore was not used as a test for safety reasons.
  It belongs in an isolated restoration instance.
- The production 1.1.0 rollout is documented as passed only after backup,
  migration, Authentik/OIDC application, identity migration, and a final healthcheck.

## Reproduction commands

### API and PWA

```bash
npm ci
npm run prisma:generate
npm run prisma:validate
npm run lint
npm test
npm run build
npm run openapi:check
./scripts/e2e.sh
```

### Android

```bash
cd apps/android
./gradlew --no-daemon --max-workers=1 \
  testLocalDebugUnitTest testProductionDebugUnitTest
./gradlew --no-daemon --max-workers=1 \
  lintLocalDebug lintProductionDebug
./gradlew --no-daemon --max-workers=1 \
  compileProductionDebugAndroidTestKotlin
./gradlew --no-daemon --max-workers=1 assembleProductionDebug
./gradlew --no-daemon --max-workers=1 assembleProductionRelease
./gradlew --no-daemon --max-workers=1 bundleProductionRelease
```

For a signed release, all four `LUNARA_UPLOAD_*` variables must be set from
a protected environment. Deliberately, a build without all four is unsigned.
Providing only a subset fails the build.

### Compose

```bash
./scripts/compose.sh config --quiet
./scripts/compose.sh build api
./scripts/compose.sh run --rm migrate
./scripts/compose.sh up -d
./scripts/compose.sh ps --all
```

`.env.example` contains only placeholders and must not be used as a production
configuration.

### Artifacts

```bash
cd artifacts
sha256sum -c SHA256SUMS
unzip -t lunara-release.apk
unzip -t lunara-play-upload.aab
```

Also check APKs with `apksigner verify --verbose --print-certs` and AABs with
`jarsigner -verify -strict`. Private certificate details and keystore passwords
do not belong in the documentation.

## Interpretation

An existing APK/AAB file does not prove a successful build. The decisive
information is the executed command, its exit status, the subsequent structure
and signature checks, and the checksum of the unchanged artifact. Similarly,
successfully compiling Android instrumentation does not establish its execution.
# Verification results for 1.1.4 – September 8, 2026

- API: 28 suites, 198 tests; PWA: 13 files, 50 tests.
- Two end-to-end scenarios on fresh, isolated PostgreSQL 18 after all eight
  migrations: the core flow and the confirmed conversion of an existing empty
  account into a partner. Production accounts were not used as test accounts.
- Android local debug and production debug: 42 suites and 205 tests each.
  Both lint variants had no errors (63 and 62 warnings, respectively).
- Production debug instrumentation code compiled. No emulator or device tests
  executed. In particular, interactive Authentik browser sign-in and the physical
  camera have not been fully tested here.
- Docker API/PWA build and OpenAPI client verification passed. Live health and
  readiness are healthy. Only metadata, asset links, and the static `/invite`
  page are anonymously accessible at the outer proxy.

Android builder used: Temurin JDK 21.0.12, official SDK package
`platforms;android-37.0` revision 2, Build-Tools 36.0.0, Gradle 9.3.1.
After review, an old incomplete Gradle transform was moved into the private
backup folder without being deleted. Neither host Java nor a host SDK had
to be installed.

The tests also check explicit acceptance, repeated acceptance without a
duplicate, an occupied partner role, soft deletes, stale tracker permissions
during import and settings changes, resumption after AppAuth, cancellation
without an old invitation, scope changes without pending offline mutations,
and delayed responses after a local sign-out.

The following sections describe general test commands and historical
verification results.
