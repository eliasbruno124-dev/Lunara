# Changelog

## 1.5.0

- **Sign in with Authentik made easy:** two lines in `.env` (`AUTHENTIK_URL`, `AUTHENTIK_CLIENT_ID`) are enough; the image derives the OIDC settings. New step-by-step guide with screenshots: `docs/AUTHENTIK.md`, tested against Authentik 2026.8.
- **Connect Authentik in the web app:** Settings → App & connection → Connect Authentik, confirmed with the Lunara password. Afterwards "Sign in with Authentik" works for that account; the password keeps working.
- After a reload the web app knows the server's sign-in options again, so the settings always show the right choices.
- Installation guide: help with domains and router ports, step-by-step Nginx Proxy Manager, Traefik and Portainer.
- Removed Google Play, store-listing and internal report documents; the repository now focuses on the app and its guides.

## 1.4.0

- **One-file installation:** `examples/docker-compose.yml` only needs your domain. Passwords and keys are created automatically on the first start and kept in the `lunara_secrets` volume; HTTPS comes from the built-in Caddy. `docker-compose.proxy.yml` covers existing reverse proxies. Explicit environment variables still take precedence, so existing installations are unaffected.
- **Set up Lunara in the browser:** a new server shows a setup form; the one-time setup code is printed in the server log only while setup is still open.
- **Partners can join in the browser:** "I have an invitation" creates a partner account directly in the web app, so couples without Android (e.g. iPhone) can use Lunara completely.
- The image reports its own version; the key volume is never partially regenerated.
- Simpler, friendlier README and installation guide. Old releases were removed from GitHub; only the current APK is offered.

## 1.3.5

- The Android app and the web app now start in English on new installations, regardless of the device language. German stays available on the first screen, in the web settings and (Android 13+) in the system's per-app language settings. Existing installations keep their language.
- New accounts created without an explicit language now default to English on the server (schema default only; stored languages are unchanged).
- All documentation, example configuration and script output are now in English.

## 1.3.4

- First public release under the MIT license.
- Ready-to-use server image `ghcr.io/eliasbruno124-dev/lunara` for amd64 and arm64, built automatically for every version tag.
- New installation guide with HD screenshots (`docs/INSTALL.md`) and example configuration with optional automatic HTTPS (`examples/`).
- Periods without a recorded end are assumed to have ended after five calendar days, including past periods. Confirmed end dates are preserved.
- The calendar, new entries, import and restore use the same five-day rule. Automatic assumptions do not replace confirmed health observations.
- Overly broad legacy fertile windows are no longer displayed. Expired estimates disappear from the current overview.

## 1.3.3

- One shared calendar category for the estimated period in the app and on the web.
- Consistent period duration calculation on the web, on Android and on the server.
- Corrected phase boundaries and visible recorded periods even when no prediction is available yet.
- No stale next-period estimate after the date changes or while synchronization is pending.
- Delayed web responses can no longer overwrite newer sharing changes.

## 1.3.2

- Aligned the web interface with the Android app, including colors, cards, navigation and settings.
- Clearly distinct calendar colors for recorded periods, predictions and the estimated fertile window.
- The same calendar legend in the app and on the web, with symbols and readable light and dark themes.

## 1.3.1

- Clear calendar status and visible sharing choices in the partner view of the app and web interface.
- Display the fertile window independently of the general period prediction.
- Export periods as CSV/JSON and import after a preview, with safe handling of duplicates and overlaps.
- APK download in web settings, bundled directly in the server image.
- Illustrated setup guide for local-only mode and a self-hosted server.

## 1.3.0

- More reliable Authentik sign-in, with safe renewal across screen changes,
  temporary connection failures and longer periods of inactivity.
- Encrypted sessions are saved atomically. Failed writes and delayed responses
  cannot switch accounts.
- A calmer home screen, clearer loading and offline states, and better
  usability on small displays and with large text.
- Automatically generated code map with verified entry points and a
  freshness check for future changes.
- Security updates for the server's HTTP, upload and YAML dependencies.

## 1.2.1

- Partner home screen shows the shared prediction before the suggestion on Android and the web.
- More natural German and English text with brief context and a practical idea.
  Stable catalog IDs and personal wishes are preserved.
- Permitted phase and symptom context is no longer displaced by broad general
  preferences. Personal wishes and sharing choices remain authoritative.
- Public background sources are offered only from an exact reviewed allowlist,
  without private data in links or automatic requests.
- An unavailable PWA suggestion is shown as unavailable instead of being
  replaced with unsolicited general advice.
- Regression tests for private views and safe source links.

## 1.2.0

- New journal design for Android and the web, with teal and ivory colors,
  editorial typography, distinct main views and a matching dark theme.
- Android settings organized into accessible categories, with clearer emphasis
  on Today actions, partner support and predictions.
- Suggestions describe concrete everyday actions instead of generic sayings.
  Stable IDs, personal wishes and sharing filters are preserved.
- Calendar improvements preserve existing date ranges and synchronization data.
- Additional presentation and interaction checks, with exact results in the
  final report. The prediction formula and accounts are unchanged.

## 1.1.6 – 2026-09-21

- Retrieve the partner connection for both roles independently of invitations.
  Unknown status and retrieval failures are not shown as a disconnection.
- Encrypted relationship cache bound to the server and account, with a visible
  timestamp and no changes to existing accounts or sharing choices.
- Clear server and local-only modes and details of the last synchronization,
  with separate app and server version labels instead of two unlabeled numbers.
- 50 revised German and English support suggestions, varied selection,
  specific preferences instead of broad category assignment, and
  "Another suggestion" without unintentionally hiding a suggestion.
- Create and deactivate explicitly shared personal wishes on Android.
  Original wording takes priority, and selection sources and reasons are visible.
- Optional PayPal.me link in central branding, with explicit confirmation,
  an external browser, and no payment integration or health data transmission.
- Clearer explanations of the start window and the number of underlying cycles,
  without changes to the prediction formula or claims of greater precision.
- Settings switches can be operated through the entire labeled row and are
  grouped for TalkBack. Full instrumentation was run in the emulator.

## 1.1.5 – 2026-09-21

- Android renews sign-ins automatically using a durably stored request ID.
  The API can safely replay lost refresh responses without reviving an
  intentionally revoked session.
- Expired sign-ins can be renewed for the same account through a banner or
  settings. Local data, mutation IDs, cursors and sharing choices are preserved.
  Incorrect identities are rejected.
- Network and server failures are retried with backoff. Delayed failures from
  an old request do not invalidate credentials that have since been renewed.
- Partner data refreshes even if the optional suggestion request fails.
  Stale data and the last update time are made visible.
- Device revocation and administrative sign-out are serialized against
  concurrent refresh rotation. Replay records use encryption that is
  cryptographically separate from health notes.
- Targeted dependency patches for Multer 2.3.0, js-yaml 3.15.2/4.3.2 and
  Sharp 0.35.4, without an automatic backend framework major-version migration.
- Documented the limits of prediction accuracy. The formula is unchanged
  and has not been clinically validated.

## 1.1.4 – 2026-09-08

- Partner invitations survive Authentik sign-in. The PWA uses a narrowly
  scoped public landing page that passes the invitation in a URL fragment.
- Existing empty accounts can be connected after explicit confirmation,
  without creating a second user account. Existing data remains protected.
- Android supports code and QR entry directly in settings and confirmation
  after Authentik sign-in. Old local relationship data is discarded during
  the switch, and the server-side role is loaded again.
- Interrupted OIDC flows retain active invitations in encrypted storage.
  Canceled sign-ins do not adopt an outdated invitation code.
- Invitations include a canonical sharing URL, remain single-use and can
  optionally be issued without expiry while remaining revocable.
- Added PostgreSQL integration tests for existing accounts, partner permissions
  and repeated acceptance. Updated the OpenAPI/Kotlin client.
- Three targeted npm overrides address known transitive vulnerabilities.

## 1.1.0 – 2026-08-25

- Added a fully local Android mode without an account, domain or server.
  Periods, daily logs, local predictions, history and reminders remain in
  the device's SQLCipher database. Server, partner and synchronization paths
  fail closed in local-only mode.
- Added portable `.lunara` backups protected with PBKDF2-HMAC-SHA256 and
  AES-256-GCM through the Android Storage Access Framework. This supports
  an intentional device transfer using local storage, USB or a provider
  chosen by the user, such as Google Drive/Nextcloud, without a Google SDK
  or automatic cloud backup. An import journal completes interrupted
  settings restoration idempotently.
- Added an installable TypeScript PWA with German and English, light and dark
  themes, role-based dashboards, a swipeable calendar, an offline queue,
  automatic synchronization and portable backup. Its local-only mode seals
  health data in a passphrase-protected AES-GCM vault. Account caches are
  isolated, and the service worker does not cache API, health or OpenAPI responses.
- Added optional personal Authentik OIDC sign-in using Authorization Code Flow
  with PKCE S256, state, nonce and external token refresh. An existing Lunara
  account is linked only after explicit reauthentication or a confirmed
  administrative migration with exact issuer/subject verification. There is
  no automatic provisioning based on an email address or username.
- Introduced a dual authentication contract, with the outer Authentik bearer
  in `Authorization` and the personal inner Lunara bearer in
  `X-Lunara-Authorization`. Both are sent only to the same origin and serve
  separate purposes.
- Android production OIDC always uses the fixed publisher app link configured
  and verified at build time, independently of the self-hosted API domain.
  The production provider registers only Android HTTPS and PWA HTTPS redirects.
  A custom scheme exists only in the Android `local` flavor for a separate
  development provider with its own client_id. Production builds, including
  production debug builds, do not contain such an intent filter.
- Added direct gateway mode without Authentik. `scripts/compose.sh` loads the
  correct direct override or Authentik OIDC environment. Backup, restore,
  update and administration use the same mode wrapper.
- Added an Authentik provisioner with a public OIDC application without a client
  secret, a narrowly scoped user group, explicit consent, asymmetric RS256,
  strict redirects, public metadata/app-link exceptions and a validated
  infrastructure backup before changes.
- Added the public `/.well-known/assetlinks.json` endpoint with strict package
  and certificate validation. The Google Play app signing key and the
  server-local upload key are documented separately.
- Switched the calendar to horizontal month swiping, made five future years
  visible and clearly distinguished estimated period days from actual entries.
- Hardened predictions with `lunara-robust-v2`, using up to twelve recent
  complete cycles, a weighted median, robust and explained outlier handling,
  minimum uncertainty, the current observation boundary and fail-closed special modes.
- Updated the Android release configuration to VersionCode 11/VersionName 1.1.0.
  Upload signing is enabled only with complete external `LUNARA_UPLOAD_*`
  configuration. Secrets and keystores remain outside the repository.
- Expanded the backend/PWA multi-stage build, PostgreSQL 18 migrations,
  JVM/web tests, Android lints, instrumentation compilation and APK/AAB build
  matrix. Instrumented device tests explicitly remain outstanding.

## 1.0.9 – 2026-08-20

- Select period date ranges without error-prone text entry. The first calendar
  day sets the start, and the second sets the end. The same day can define a
  one-day period, while tapping an earlier second day clearly resets the start.
- The new visual range selector marks the start, all intervening days and the
  end, shows inclusive duration, supports month changes and offers an
  explicitly labeled action for a period that is still ongoing.
- Creating and editing use the same calendar-based flow. Overlaps are shown
  and blocked before saving. During editing, the current record is correctly
  excluded from the overlap check.
- GitHub validation workflows now run for feature branches only through the
  pull request and for pushes only on `main`. Release tags no longer trigger
  a second identical validation run, substantially reducing notifications.
- Centrally pinned Prisma's transitive `deepmerge-ts` dependency to 8.0.1
  following a newly published HIGH-severity security advisory. Lockfile
  installation, npm audit, Prisma, backend tests, the container build and
  the Trivy scan all passed again with the corrected version.

## 1.0.8 – 2026-08-16

- Corrected Android target behavior to API level 36, available in the stable
  SDK channel. Compilation still uses API 37 from the preview channel because
  the currently pinned AndroidX libraries require that compile SDK. This
  does not enable Android 17 runtime behavior.
- Updated Android to VersionCode 9/VersionName 1.0.8. The app remains compatible
  with the unchanged Lunara API v1 and production server 1.0.7.
- GitHub CI now explicitly installs the required preview compile SDK.
  Backend E2E uses Node 24.18.0 on the runner against a PostgreSQL 18 test
  instance bound exclusively to `127.0.0.1`. The test port can be overridden
  locally and is fixed on the isolated GitHub runner. The ephemeral test
  bridge network allows only the required runner access through this loopback binding.
- The secret history scan ignores exactly the fingerprint of a documented,
  public OpenAPI SHA-256 false positive, without excluding other rules,
  commits or files from the scan.

## 1.0.7 – 2026-08-16

- Trackers can add past periods in the calendar with a start and optional
  end date. Tapping an existing range allows correction or deletion after
  confirmation. Partners retain strictly read-only access.
- The main action reflects the actual local state, showing "Period started",
  "End period today" during an open period, and a visible confirmation after
  completion. A snackbar and synchronization status confirm local storage
  or a pending transfer.
- Historical ranges are created atomically as one Room record and a single
  idempotent offline mutation. Automatic WorkManager/foreground sync,
  cursor pulls, backoff and manual synchronization remain active.
- Calendar dialogs retain drafts across Activity recreation, prevent repeated
  submission during the local transaction and close only after the actual
  Room change is observed. Errors leave the draft open for an intentional retry.
- Hardened calendar rendering for constant memory use. Even extremely old
  open records are no longer materialized day by day.
- Overlapping active periods are now also serialized transactionally per
  tracker on the server and rejected with HTTP 409. A partial PostgreSQL
  GiST exclusion constraint additionally protects against concurrent devices
  and direct database races. Soft deletes remain exempt.
- Idempotency receipts, offline mutations, SyncChange and the prediction
  cache are completed atomically for periods and daily logs. Old pending
  receipts can be safely recovered only when the owner, final state,
  version and SyncChange match exactly.
- Expired idempotency keys are reclaimed atomically without races, even if
  they belonged to a previous request or household. Active keys remain
  protected. An old request proven to have completed is recovered before
  it can be executed again.
- Period imports detect overlaps during preview and commit, replan under
  the same tracker lock and update the prediction atomically.
- Updated Android to VersionCode 8/VersionName 1.0.7 and the server,
  container and OpenAPI metadata to 1.0.7.
- The complete core flow passed against a real fresh PostgreSQL 18 database
  after all seven migrations.
- The production runtime image uses a minimal Debian stage with only the
  copied Node binary. npm, npx and Corepack are no longer included.
  Migrations run directly through the pinned Prisma CLI.

## 1.0.6 – 2026-08-14

- Added local Android notifications as an explicit opt-in, without Firebase,
  advertising, analytics or external push dependencies.
- Independently selectable reminders for the estimated period window on the
  following day, the only roughly estimated fertile window on the following
  day, a daily tracker entry and a partner support suggestion.
- Added device-specific settings for timing, quiet hours, a maximum of one
  to three reminders per day and a neutral lock-screen preview.
- Implemented reminder scheduling as local work rescheduled daily to account
  for local time, time zone changes and daylight saving time. Android may
  still delay execution to save power.
- Encrypted prediction and delivery state using the Keystore, marked reminders
  as delivered only after successful handoff to Android, and strictly suppressed
  partner reminders when no current server response is available.
- Partner reminders use only the dashboard already filtered by sharing choices
  on the server. Fertility reminders are suppressed without opt-in, with
  insufficient data, in unsuitable special modes, after sharing is revoked
  or while offline.
- Updated the Android client to 1.0.6/VersionCode 7. The compatible server API
  remains at 1.0.5. This client update does not require a server restart.

## 1.0.5 – 2026-08-13

- Unified account password rules for bootstrap, registration, partner joining
  and password changes at 8 to 128 Unicode code points. Android, the API and
  OpenAPI now also count variation selectors consistently.
- Removed mandatory character classes. New account passwords no longer require
  uppercase letters, digits or special characters. Long, unique passphrases
  remain recommended.
- Preserved compatibility with existing passwords of 1 to 128 characters for
  sign-in, account deletion and current-password entry, so later policy
  changes do not lock out existing accounts. Newly set passwords still
  require at least eight characters.
- Preserved Argon2id hashing, sign-in/password-change rate limits, generic
  authentication errors, separate device sessions and full Authentik domain protection.
- Deliberately retained the stricter encrypted export passphrase requirement.
  It remains separate from account passwords at 12 to 128 characters.
- Updated API, Android and OpenAPI versions to 1.0.5. Results of builds,
  tests, containers and live checks actually performed continue to be
  recorded only in the designated evidence documents.

## 1.0.4 – 2026-08-13

- Changed Authentik to protect the entire Lunara domain. `/api/v1`, metadata,
  sign-in, health and OpenAPI no longer have bypass exceptions.
- Replaced earlier human and separate A/B gateway bindings with exactly one
  neutral service identity, `lunara-native-app`, and exactly one shared,
  non-expiring app password. Old technical credentials become invalid during
  migration, and their local files are archived on the server.
- Implemented two clearly separate authentication layers, with outer
  `Authorization: Basic …` for Authentik and the short-lived personal inner
  Lunara token as `X-Lunara-Authorization: Bearer …`. Personal accounts,
  roles and sharing choices remain separate despite the shared outer app access.
- Android onboarding starts without a prefilled URL. An HTTPS userinfo URL
  can be entered once. The app extracts and encrypts the gateway credential,
  then stores and displays only the sanitized base URL. Credential URLs in
  QR codes are rejected.
- Strictly restricted the Basic header to the exact configured origin and
  disabled browser redirects to prevent credentials reaching other hosts.
  Shared access is rotated centrally for all installations.
- Added fertility estimation as an explicit opt-in. It requires at least
  three complete cycles, fails closed in unsuitable special modes, carries
  explicit safety metadata and is never presented as confirmation of ovulation
  or a contraceptive method.
- Hardened separate partner sharing for the estimated fertile window.
  Sharing requires active personal opt-in. Disabling the estimate revokes
  sharing atomically. Concurrent profile and sharing changes are serialized
  per relationship.
- Further minimized the partner projection. A private day hides the current
  phase, actual period dates and fertility data. Unshared notes are no
  longer decrypted preemptively.
- Expanded the partner suggestion catalog to 71 respectful German and English
  suggestions. Selection considers only shared phase/symptom context, care
  preferences, feedback and priority. Restricted health data remains fail-closed.
- Expanded Android settings with the full sharing matrix, fertility opt-in
  with a safety notice, and eight directly editable care preferences.
- Fully replace the partner cache on each authoritative server response so
  a previously visible suggestion does not remain locally visible after
  sharing is revoked or a day is marked private.
- Preserved automatic foreground/background synchronization, personal password
  changes, and correction and deletion of period starts, and integrated
  them with the new gateway authentication.
- Updated API, Android and OpenAPI versions to 1.0.4. Results of builds,
  tests, containers and live checks actually performed are documented
  separately in `FINAL_REPORT.md`.

## 1.0.3 – 2026-08-13

- Removed the visible household field and related product text from onboarding
  and settings. The internal relationship ID remains solely a server-side
  tenant and authorization boundary.
- Added optional public tracker registration. With `REGISTRATION_ENABLED=true`,
  any number of mutually isolated couples can use the same instance.
  Each relationship allows exactly one `TRACKER` and one `PARTNER` account.
- Added permanent partner invitations with twelve characters, single use,
  revocation at any time and sharing as a password-free QR code. The raw code
  is returned only at creation and stored on the server solely as a hash.
  A new open partner code revokes older codes for the same relationship.
- Hardened concurrent registration, invitations and joining using
  transaction-scoped relationship locks to prevent duplicate roles and
  repeated claims of the same code.
- Persisted successful initial bootstrap in a singleton-like `ServerState`.
  Deleting all accounts no longer reopens setup. Existing installations are
  recognized as already initialized by the additive migration, after which
  the `BOOTSTRAP_TOKEN` can be removed.
- Switched the app connection to the normal HTTPS base URL
  `https://lunara.3li361.de`. The app no longer needs Authentik header credentials.
- Prepared the Authentik proxy to route `/api/v1` without browser sign-in to
  the API, which remains protected by Lunara tokens, roles and ownership.
  `/api-docs` and `/api-docs-json` remain protected by Authentik.
- Preserved the password change added in 1.0.2 in the role-based settings view.
- Added retrospective start-date editing and confirmed deletion of an
  accidentally started period entry in the tracker interface. Both actions
  use the offline mutation queue and synchronize automatically.
- Ordered the synchronization queue per record and hardened it against older
  acknowledgments or server states overwriting newer subsequent local changes.
- Successful offline mutations are drained without artificial retry backoff
  up to a fair limit of 100. Real version conflicts remain visible and retain
  the local version until an explicit decision is made.
- Conflict resolution reloads the confirmed server version and prevents
  further changes to affected entries until it arrives. Intentionally cleared
  daily values are reliably removed on the server as explicit `null` values.
- Preserved explicit JSON `null` values in the Android sync layer so
  intentionally cleared optional daily values are reliably deleted on the
  server. An absent field remains distinguishable from `null`.
- Added a database constraint allowing at most one non-deleted period start
  per person and calendar date.
- Updated API, Android and OpenAPI versions to 1.0.3. Final checks passed
  Prisma generation/validation, backend lint and build, 14/14 suites with
  59/59 tests, OpenAPI hash verification and 63/63 Android JVM tests, both
  lints, instrumentation test compilation and the production debug APK build.
- Applied six migrations in production, removed `BOOTSTRAP_TOKEN` from the
  live configuration after confirming persistent setup status, and updated
  only the Lunara API. Existing Emby, Authentik, Pangolin and PostgreSQL
  containers were not restarted.
- Built production debug APK 1.0.3 with 24.362.451 bytes and SHA-256
  `4dac957e020684f3d5ee5699fdc584a0f68393a58a76452801a21831616dd416`.
  Instrumented tests were compiled only because no device or emulator was available.
- Published source and APK 1.0.3 in the private GitHub branch
  `agent/authentik-human-access`. The APK download requires authorized GitHub
  sign-in. No separate release tag was created.
- The real PostgreSQL E2E flow was not rerun after the final two migrations.
  A successful run of an earlier intermediate 1.0.3 state is not counted
  as final verification of the completed version.

## 1.0.2 – 2026-08-13

- Production onboarding starts without a prefilled domain. The personal
  Authentik header-auth URL must be entered deliberately.
- Onboarding and role-specific in-memory state is safely reset after sign-in,
  sign-out and account changes. A completed bootstrap no longer reappears
  from an old app cache.
- Bootstrap, username, password, household and invitation inputs are explained
  with field-specific rules matching the server. Incorrect credentials
  receive their own understandable message.
- Authentik cookies are removed before every explicit connection test,
  even when the URL and credentials are unchanged.
- Dynamic metadata/bootstrap status carries `Cache-Control: no-store`,
  preventing its reuse as a stale proxy response.
- Added automatic synchronization after sign-in and local changes, when
  opening the app, once per minute in the foreground and periodically in
  the background. Concurrent workers and refresh rotation are serialized.
  Durable unique WorkManager jobs prevent lost triggers and unbounded retry
  chains. After eight failures, an explicit manual retry is required.
- The partner dashboard and today's suggestion update reactively after
  successful synchronization. Role-specific cached data is bound to the current user ID.
- Added secure Lunara password changes in settings. The operation requires
  the current password, uses Argon2id and revokes all other Lunara server
  sessions without changing the separate Authentik app password. Offline
  data on other devices is explicitly preserved.
- Sign-in, refresh rotation and password changes are serialized per account
  in PostgreSQL so a concurrent sign-in with the old password cannot create
  a new session after the password has changed.
- Added password-change audit events, rate limiting, E2E coverage and an
  updated OpenAPI/Kotlin contract.

## 1.0.1 – 2026-08-13

- Fixed Authentik header authentication after an anonymous connection attempt.
  Anonymous proxy cookies no longer suppress the required Basic header.
- Strictly limited gateway credentials to the scheme, host and port of the
  configured server. Browser sign-in redirects are no longer followed as API responses.
- Removed stale Authentik cookies when the server or credentials change and
  added a clear gateway error message.
- Automatically normalized a pasted `/api/v1/meta` test path to the server
  base URL and clarified the prefilled URL scheme in onboarding.
- Made the debug signing key persistent and excluded it from Git for
  repeatable local APK updates.

## 1.0.0 – 2026-08-12

- Created a separate Lunara monorepo with a NestJS/Prisma API, PostgreSQL 18
  and a native Android Compose app.
- Implemented secure bootstrap, separate roles/accounts, invitations,
  household isolation, period/daily logs, robust predictions and a granular
  partner projection.
- Added offline synchronization, SQLCipher/Keystore, a complete local wipe,
  50 bilingual suggestions, care preferences, check-ins and data management.
- Added production-oriented Docker, CI, backup, restore, administration,
  Authentik and Pangolin configuration.
- Built an HTTPS-only production debug APK and versioned OpenAPI artifacts.
- Documented outstanding convenience/additional features and real device
  tests in the reports at the time. Earlier states remain in Git history.
