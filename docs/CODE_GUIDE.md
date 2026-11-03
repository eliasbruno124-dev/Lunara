# Lunara change guide

Start with [CODE_MAP.md](CODE_MAP.md). It contains every non-ignored repository file, generated source hashes, syntax declarations and verified links for the main flows. The map is a navigation aid; it does not claim to resolve runtime calls or prove behavior.

## Where to change behavior

| Change | Main location | Check before shipping |
|---|---|---|
| Stay signed in / renew a session | `AuthentikAuthManager`, `DurableSessionRenewal`, `ConnectionRepository`, API `AuthService` | Cancellation, offline retry, rotation replay, provider rejection, logout and account switch |
| Store credentials | `SessionSecrets`, `KeystoreSecretStore` | Atomic migration, synchronous encrypted commit, stale generation rejection |
| Server address / request headers | `ApiClientFactory`, `ServerAddressParser` | Exact scheme/host/port match; no bearer sent to another origin |
| Home screen and settings | `MainShell.kt`, `JournalComponents.kt`, `LunaraComponents.kt` | Both roles, light/dark, narrow display, large text, offline/empty/loading states |
| Branding | `BrandTokens.kt`, `LunaraTheme.kt`, Android resources | Contrast and touch targets; no remote assets required |
| Calendar and local edits | `PeriodRepository`, `LunaraDao`, `MainViewModel` | Preserve queued edits, date validation and account ownership |
| Automatic period end | Android `PeriodEndPolicy`, web `period-end.ts`, API `period-policy.ts`; [period-end contract](PERIOD-ENDS.md) | Five inclusive days for missing ends, both overlap operands, manual correction after day five, raw-null transfer/statistics and matching database exclusion |
| Calendar appearance | Android `CalendarColors`, `CalendarDayKind`, `CalendarLegend`; web `calendar-presentation.ts`, `calendar.ts`, `styles.css` | Same rose/amber/teal colors and three legend entries; one estimated-period category; actual entries win overlaps; complete prediction boundaries, narrow screens and large text |
| Shared app design | Android `LunaraTheme` and `JournalComponents`; web `styles.css`, `ui-icons.ts`, `main.ts` | Android is the visual reference; keep responsive desktop, all seven calendar columns and unbroken navigation labels |
| Period import/export | `PeriodExportFormat`, `PeriodExportRepository`, web `period-transfer.ts`, API `data-management/` | CSV/JSON roundtrip, preview, duplicates, overlaps, atomic writes and account changes during file selection |
| Partner calendar visibility | `CalendarStatusPolicy`, web `partner-calendar.ts`, API `partner-projection.ts` | Separate consent, loading, empty data and unavailable estimates; never infer or override sharing |
| APK download in web settings | web `apk-download.ts`, API `web-app.ts`, `infra/docker/api.Dockerfile` | Real file response, compatible signing key, authenticated download, HTTP Range and no APK host mount |
| Synchronization | `SyncWorker`, `SyncScheduler`, API `sync/` | No overlapping batches, idempotency, backoff and logout race |
| Web refresh ordering | `LatestRequestCoordinator`, `IdentityEpochCoordinator`, `refreshData` | Older responses and in-flight cache writes must not restore revoked sharing; current errors still apply; account changes remain a separate guard |
| Partner access | `HouseholdRepository`, `SharingRepository`, API `household/`, `partner/`, `sharing/` | Only explicitly shared fields; no data from previous account |
| Forecast and tips | Android `LocalPredictionEngine`, web `local-prediction.ts`, API `prediction/` and `tips/` | Compare recent duration selection, model fallbacks, uncertainty half-width and consent; share the existing model center for phase estimates; no medical certainty |
| Forecast freshness | Android `PredictionFreshnessPolicy` and `MainViewModel`, web `prediction-freshness.ts` and `main.ts` | Inclusive end of the predicted start interval; pending server edits; foreground midnight and resume; keep historical calendar data and account isolation |
| Fertility presentation | Android `FertilityWindowPolicy`, web `fertility-presentation.ts`, API `fertility-policy.ts` | Reject malformed or wider-than-19-day legacy envelopes; current summaries expire at their own end; preserve independent sharing and valid calendar history |
| Account lifecycle | API `data-management/` | Existing production lifecycle overlay, identity unlinking and revocation |
| API contract | `services/api/src/`, `packages/openapi-client/` | Generate/check the client; preserve installed Android clients |
| APK | `apps/android/app/build.gradle.kts`, `scripts/build-android.sh` | Higher versionCode, same application ID and signing certificate |

## Session boundaries

Authentik provides the outer identity credential; Lunara creates its own device session. A valid credential for one layer does not make the other layer valid. Temporary networking or signing-key retrieval errors must remain retryable. A revoked session, deleted identity or wrong user must not recover silently.

Renewal is serialized and completes its encrypted credential commit even if the caller leaves the screen. The account generation is checked before committing results. Explicit logout can therefore invalidate a request already in flight. No password is embedded in the APK.

Authentik provisioning uses a 365-day renewable refresh lifetime and 15-minute access tokens. A previous refresh token keeps its existing expiry until renewed. Explicit revocation, cleared app storage, loss of the Android Keystore or a refresh credential that really expires still requires login. An inner OIDC session can renew past its local idle expiry only with a fresh verified proof for its existing external identity and unrevoked family. Lost-response replay recovery remains bounded by the API's existing encrypted retry-record lifetime.

## Keep the map current

Run `npm run code-map:write` after source edits, then `npm run code-map:check`. JDK 17+ and the repository's Gradle wrapper are required for Kotlin PSI parsing. Install the locked npm dependencies first. Kotlin and TypeScript are parsed as syntax trees; generated client code is inventoried by file and hash. A new or deleted file, changed content, changed declaration or stale/ambiguous flow anchor fails the check.

`docs/code-map-flows.json` holds the reviewed behavioral entry points. Change it when adding a new cross-cutting flow. The generator can prove that a named declaration exists uniquely and that the snapshot matches the code. A reviewer must still verify that a behavioral explanation is correct. CI runs the freshness check; do not hand-edit generated maps.

Use synthetic accounts and data for screenshots and tests. Do not include real health data, credentials, provider responses or deployment secrets in the map.
