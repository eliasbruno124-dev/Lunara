# Privacy

Lunara contains no advertising, telemetry, external usage analytics,
marketing SDKs, or Firebase or Google Play Services requirement. Data
processing by an external reverse proxy, Authentik, DNS/CDN provider,
Google Play, the operating system, or a file service chosen by the user
is outside the Lunara application code and must be documented separately
by the respective operator.

## Operating modes and storage locations

| Mode | Primary storage | Network transfer |
| --- | --- | --- |
| Local-only Android | SQLCipher database and Keystore-protected settings on one device | No core data transfer; only a backup file provider explicitly chosen by the user |
| Self-hosted Android | Encrypted local cache plus your own Lunara instance | HTTPS to the configured domain |
| Local-only PWA | Passphrase-encrypted vault in origin-bound IndexedDB | No core data transfer |
| Self-hosted PWA | Origin-bound offline cache plus your own Lunara instance | HTTPS to the same origin and optionally to the configured OIDC endpoints |

## Local-only mode on Android

Local-only mode requires neither an account nor a server. It stores periods,
daily logs, the local prediction basis, profile settings, and notification
settings exclusively on the device. The database is encrypted with SQLCipher,
and keys and other secrets are bound to Android Keystore.

Automatic Android cloud backup and system-managed device-to-device transfer
are completely disabled. In particular, Lunara does not automatically upload
health data to a Google account.

To transfer data to another device voluntarily, the user can create a
passphrase-encrypted `.lunara` file. The Android system file picker shows all
providers installed by the user, potentially including local folders, USB,
Nextcloud, or Google Drive. Lunara receives access only to the specific file
selected, integrates no Google SDK, and knows no provider credentials.
The provider may process the encrypted file under its own terms.

The backup passphrase is not stored and cannot be recovered. After strict
validation, import replaces the existing local-only data. An import journal
completes interrupted settings restoration at the next startup. Before wiping
the old device, check the date range, daily logs, and settings on the new device.

## Data stored in self-hosted mode

Depending on use, the server stores:

- **Account:** Username, display name, language, disabled state,
  device sessions, and server-side role;
- **Internal relationship boundary:** UUID tenant, memberships, invitations,
  sharing settings, and care preferences;
- **Health:** Period start/end as local calendar dates, daily logs,
  flow level, pain, mood, energy, sleep, temperature, ovulation test markers,
  symptoms, and optional notes;
- **Derived data:** Prediction window, statistics, explanation, and, only
  after opting in, the roughly estimated fertile window;
- **Support:** Tip feedback, personal tips, and time-limited check-ins;
- **Security:** Sign-in, failed sign-in, new device, session/invitation
  revocation, OIDC linking, sharing changes, export, and account deletion.

Audit events contain no health values or note contents. Free-text notes
are encrypted with the server master key before writing. Data, dates,
roles, and other structured metadata are stored in PostgreSQL without
application-level encryption. In version 1, check-in text is likewise not
additionally encrypted with the master key.

An instance can accommodate multiple couples. Each public tracker registration
creates a new server-isolated relationship boundary. A partner joins exclusively
through a single-use code with a separate account. Internally, the technical
entity is named `Household`, but this is neither a mandatory visible concept
nor a shared sign-in.

## Authentik/OIDC

Authentik is optional. Without Authentik, Lunara uses only its own accounts.
With Authentik, `/api/v1/meta` publishes the nonsecret OIDC configuration values
so that the client can initiate sign-in.

After explicit linking, Lunara stores:

- The exact verified OIDC issuer;
- The opaque subject of the Authentik identity;
- The time of linking and last use;
- The association with the existing Lunara user.

Lunara does not automatically adopt an email address, Authentik password,
or profile. An OIDC access token is used only for verification and opening
the Authentik proxy. Lunara stores no plaintext copy of it on the server.
Authentik itself may log sign-in, IP, and security events according to
the instance operator's configuration.

`/api/v1/meta` and `/.well-known/assetlinks.json` are public in Authentik mode.
Metadata contains product/version/compatibility information, without accounts.
Asset links contain only the package ID and public certificate fingerprints.

## Partner sharing

By default, `PARTNER` sees only the expected period window. Notes, symptoms,
flow level, pain, mood, energy, sleep, actual period dates, phase, and
fertility data are disabled.

For every partner request, the server filters based on:

- Currently active individual sharing permissions;
- A global sharing pause;
- An optional private day;
- The personal fertility opt-in;
- The setting for symptom-dependent tips.

Revoking sharing affects the next server response and clears the authoritative
partner cache. It cannot recover screenshots, transcripts, or memories
previously saved outside Lunara. Tips must not mention blocked symptoms
or reveal them indirectly.

The estimated fertile window is disabled by default, requires sufficient data,
and needs separate partner sharing permission. It does not confirm ovulation
and must not be used for contraception.

## PWA and offline storage

The PWA stores periods, offline mutations, sync cursors, and session state
in origin-bound IndexedDB. Refresh and access tokens are wrapped with Web
Crypto. In local-only mode, the entire vault is sealed with AES-256-GCM
and a key derived from the unstored local-only passphrase. It must be
unlocked again after a page restart.

In self-hosted mode, synchronized health data can be stored as an ordinary
origin-bound offline cache. This server cache has no additional passphrase
encryption. Users should use a personal, locked browser profile and sign
out of the PWA on shared devices.

Screenshot protection and browser notifications currently cannot run in
the PWA. Corresponding imported settings are retained in the encrypted vault
and shown as not applicable in the browser. The local-only vault locks when
the page is hidden, on `pagehide`, and no later than 15 minutes of inactivity.
The passphrase is then required again.

Account identity changes or logout clear account-bound caches and mutation
queues. The service worker does not cache API, health, ready, or OpenAPI responses.

## Notifications

Android reminders are disabled by default on each device and run locally
through WorkManager. There is no Firebase connection. Configurable options
include the estimated period window, the roughly estimated fertile window,
tracker logging, partner tips, preferred time, quiet hours, and maximum daily
frequency. Lock-screen text is neutral by default.

Partner notifications require a current response filtered by sharing permissions
on the server. Without a current connection, no old sensitive partner
notifications are generated. Android may delay WorkManager tasks for
power-saving reasons.

## Export, import, and deletion

The server API supports JSON, period CSV, daily log CSV, and an AES-256-GCM
password-protected JSON export. Server import validates Lunara JSON, provides
a preview, and detects duplicates. A visual generic CSV mapping wizard and
complete import undo are not yet implemented.

The native local-only feature uses the separate portable `.lunara` format
and is available directly in Android Settings. After confirmation, it replaces
the local-only data on the device. Server accounts are not imported by this process.

The account deletion API requires renewed personal confirmation, revokes
sessions, and deletes the affected internal tenant according to the role.
The complete Android interface for server-side export, import, and account
deletion is not yet finished. Server backups may contain deleted data until
the retention period set by the instance operator expires.

## Responsibilities

- The app publisher is responsible for the APK/AAB, bundled SDKs, store
  information, and update channel.
- The self-hosting operator is responsible for server location, domain,
  TLS, Authentik/proxy logs, backups, retention, and administrative access.
- The person tracking their cycle determines partner sharing permissions.
- The partner may use only the projection shared by the server.
- A voluntarily chosen backup file provider is responsible for its own
  processing of the encrypted file.

This technical file does not replace a public privacy policy tailored to
the specific publisher, operator, target countries, and retention periods.
Google Play requires an anonymously accessible privacy page and an
account deletion page.
