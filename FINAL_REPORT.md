# Lunara 1.3.3 – verification report

As of October 28, 2026. This report describes checks that were performed.
Earlier reports and artifacts remain available in the Git history.

## Changes

- The start and subsequent days of the estimated period use one shared
  amber, dashed category. Recorded days remain rose and the estimated fertile
  window remains teal. Calendar data is not merged.
- The web offline estimate uses the same selection of the last twelve
  completed positive period durations as the API and Android. The permitted
  observation duration and profile fallback are no longer confused.
  Deleted entries are excluded, and `variationDays` consistently denotes
  half the uncertainty width instead of twice that width on the web.
- Phase consumers use the same center and configured luteal phase length
  as the existing prediction. A shared actual menstruation is no longer
  suppressed solely because a prediction is unavailable.
- Outdated summaries or summaries invalidated by pending server changes
  do not appear as the next period. Midnight and returning to the app
  refresh the classification, while historical calendar ranges remain available.
- The partner phase is accurately labeled “Last shared cycle phase”. Delayed
  web responses overwrite neither newer sharing changes nor their cache.
  Account checks and cache write ordering remain protected separately.

Features added earlier in the same unpublished update remain included:

- The calendar and partner settings distinguish missing sharing permissions,
  loading errors, empty entries, and unavailable estimates.
- The shared fertile window is displayed independently of sharing the
  general period prediction. The calculation formula remains unchanged.
- CSV/JSON import and export of your own periods on Android and the web,
  including local mode, with a preview, explicit confirmation, duplicate checks,
  conflict rejection, and protection during account changes.
- More robust session renewal and encrypted storage. Expired or revoked
  provider credentials continue to require sign-in.
- APK download in web settings, with the signed APK directly in the API image.
- Revised settings, illustrated setup, and an automatically verifiable
  code map with separately maintained flow descriptions.

## Checks performed

| Area | Result |
| --- | --- |
| API | 340 tests in 32 suites; lint and build passed |
| Web | 232 tests in 23 files; TypeScript and production build passed |
| Android JVM | 327 tests in 58 suites; no failures or skipped tests |
| Android device | 56 UI tests passed in each of German and English; calendar, sharing, data transfer, freshness, and large text |
| Browser | 165 assertions with synthetic data; import, export, local vault, partner calendar, sharing revocation, and 18 light/dark views |
| Calendar width | 48 Chromium layouts with two languages, widths from 280 to 700 px, and three text sizes; also forced colors and a negative rendering control |
| Estimate comparison | 72 synthetic API/web comparison cases and independent boundary and phase checks passed |
| Android release | Signed APK and AAB built; Android debug lint: 0 errors, 95 warnings |
| Update compatibility | Package de.lunara.app, version 1.3.3, versionCode 23, unchanged signing certificate |
| Packaging | Docker image contains exactly the verified web assets and signed APK |
| Production | API-only update; health/readiness, authenticated HTTPS download, checksum, and HTTP Range verified |
| Stored data | Checksums for periods, sharing settings, and users unchanged; database container not recreated |

Device verification ran on an isolated Android emulator rather than the
users' physical devices. Browser and device tests use only synthetic data.
The live download was additionally checked over HTTPS.
Earlier fixes for narrow calendars and large text remain included.
Four tests in the extended English calendar run still used German text.
They now use localized resources, and both languages were then fully
rechecked successfully. The behavioral assertions were preserved.

The live container `local/lunara-api:1.3.3-prediction-20261028` was built from
source revision `eb710fb`. The API and web app are healthy. The served web assets
and APK match the verified artifacts byte for byte. The HTTPS download
remains protected by the existing authentication. Gateway access alone
provides no access to health data. The database was not recreated.
The container image includes the APK without an additional APK bind mount.

Only the Lunara API container was recreated. Other services were unchanged.
A narrowly scoped, server-local backup is available. The test emulator was
stopped after the successful runs. No additional test containers were created.

The additional PostgreSQL integration tests and the dependency audit at that
point belong to the preceding 1.3.1 verification run, rather than a repeated
full database or security review of this change. No server data models or
stored partner sharing settings were changed.

## What the estimates establish

The API, Android offline mode, and web offline mode were checked for input
filters, cycle/period duration, start/end ranges, uncertainty, fertility
sharing, phases, tips, cache, day boundaries, reminder consumers, and recorded
history. These checks establish calculation and integration consistency,
without establishing medical accuracy. This app has no prospective clinical validation.

- The “high” confidence label is a model heuristic, without a calibrated percentage.
  Excluded long intervals do not automatically lower it, while the fertile
  window is suppressed for such data.
- The end range shifts the start range by the typical period duration.
  Variability in bleeding duration is not modeled separately.
- The fertile window uses calendar data and a configured or default
  luteal phase length. Temperature, LH tests, and symptoms are not combined
  to confirm ovulation.
- In server mode, Android uses the known profile time zone of the tracking
  person. The web app and partner view do not know it and use device time
  for the current summary. Around midnight, this classification can therefore
  differ from the server, while stored period data remains unchanged.

The existing medical assumptions were not replaced with unvalidated new
formulas. A sensible next development step would be an explicitly
privacy-conscious comparison of earlier predictions with subsequently
recorded actual starts, including error measures and interval coverage.
This was not enabled as new data collection. The assessment was checked
against public guidance from NHS (Natural family planning) and ASRM
(Optimizing natural fertility, Committee Opinion).

## Data transfer and limits

CSV supports commas, semicolons, and tabs, as well as recognized German and
English column names. UTF-8 and UTF-16 with BOM, unambiguous date formats,
and multiple JSON wrappers are handled. Limits are 2 MiB and 5,000 periods.
Ambiguous data is not guessed. A missing end date means an ongoing period.
Additional third-party fields are not adopted as account settings.

The export contains the currently loaded local state of your own periods,
including pending changes. Synchronize first to obtain the latest server state.
CSV/JSON contain unencrypted health data. A complete encrypted backup remains
a separate operation. Not every proprietary third-party format is compatible.
A specific third-party app is not described as supported until its export
file has been checked.

Partner sharing is never enabled automatically. A partner connection alone
does not authorize access to period data or the fertile window.
Calendar estimates do not confirm ovulation and are unsuitable for contraception.

## Reproduce the checks

The regular checks are documented in [TESTING.md](TESTING.md), and setup in
[docs/SETUP.md](docs/SETUP.md). Generate the code map with `npm run code-map:write`
and check it with `npm run code-map:check`. A syntax inventory and linked
symbols do not replace behavioral verification.

The SHA-256 values of the delivered files are listed in [artifacts/SHA256SUMS](artifacts/SHA256SUMS).
Install the APK over the existing installation without uninstalling first.
