# Security

## Threat model

Lunara protects cycle, symptom, free-text, account, and relationship data against:

- Users from other internal tenants;
- A partner without the corresponding sharing permission;
- IDOR and cross-relationship access;
- Stolen or reused refresh tokens;
- Lost responses and duplicate offline mutations;
- Accidental disclosure in logs, source code, or ordinary backups;
- Loss of a locked Android device, within the protection offered by Android
  Keystore, SQLCipher, and optional screenshot protection.

The Debian host operator, root, PostgreSQL administrator, and running API
process are trusted. Lunara cannot protect against a fully compromised host,
an unlocked compromised device, malicious keyboards, screenshots taken outside
the app, or a value that was legitimately displayed and then copied externally.

## Server and tenant protection

- PostgreSQL is accessible only on the internal Docker network.
- The API runs as UID/GID `10001:10001`, with a read-only root filesystem,
  `no-new-privileges`, and no Linux capabilities.
- Helmet, restrictive CORS, DTO validation, text/body limits, and structured,
  redacted logs are enabled.
- Roles, ownership, and the internal UUID tenant boundary are checked on the
  server. Choosing a role locally does not grant permissions.
- Partner responses are generated from current sharing settings on every
  request. Notes that have not been shared are not even decrypted.
- Period changes are serialized per tracker. A partial PostgreSQL GiST
  exclusion constraint prevents overlapping active date ranges, including
  concurrent changes from multiple devices.
- The idempotency receipt, mutation, `SyncChange`, and any necessary prediction
  recalculation are completed together in one transaction.

Free-text notes are stored encrypted with AES-256-GCM in PostgreSQL.
`APP_MASTER_KEY` is a canonical Base64 key containing exactly 32 random bytes
and must be backed up separately for the long term. Encryption protects note
contents in a dump, without protecting structured metadata such as dates,
record counts, or roles. Root or a compromised API process can read the key
and decrypted values.

## Gateway modes

### Renewal and retries from 1.1.5

The Android app automatically renews access and refresh tokens. Lost refresh
responses can be retried using the same random request UUID persisted under
Keystore protection. `DeviceSession` still contains only the refresh hash.
The time-limited replay receipt instead contains an AES-GCM-encrypted
replacement credential, never a plaintext token or access JWT. A purpose-specific
key is derived from the master key for this. The note decryption path cannot
open this receipt.

The request, original session, user, relationship, and expiry are bound together.
The replacement session must remain active, and OIDC still requires the linked
external identity. A known late retry cannot revoke a newer session. Unknown
reuse remains a security error. The master key now also protects these
short-lived replay receipts. The database and master key remain highly
sensitive backup components.

Explicit reauthentication is allowed only for the same server, user ID, and
role. Cancellation, the wrong account, and network errors delete neither the
SQLCipher database nor pending mutations. Sessions already revoked or ended
by the identity provider are not restored automatically. Never clear app data
or uninstall to recover entries that have not yet synchronized.

Device revocation from a still-valid session blocks the entire affected
device family, including concurrent rotations. Administrative session
revocation uses the same user lock. HTTP logout still requires a valid access
token. If a device has only an already-consumed token, server-side sign-out
can fail. In that case, revoke the session from another signed-in device or
through the admin tool. A local sign-out alone does not confirm server-side revocation.

### Direct

Traefik routes directly to Lunara. Personal Lunara access tokens are accepted
as bearer tokens, while the API continues to check the account, role, and
ownership. OpenAPI is disabled by default and, when enabled, must be protected
separately with a strong documentation password.

### Authentik

The Authentik proxy protects all paths except:

- `/api/v1/meta`: uncached, nonsensitive client discovery;
- `/.well-known/assetlinks.json`: public Android app-link association.
- `/invite`: static, uncached capture of the fragment before the Authentik
  redirect, without API or account access. Invitations are single-use,
  revocable bearer codes. Do not share QR photos or codes publicly.

Existing individual accounts can accept an invitation only with explicit
confirmation. The current membership is rechecked on the server. If health
data or individual settings exist, the change is rejected instead of silently
merging them. After successful linking, clients discard the old relationship
cache and reload the role and sharing permissions. This does not protect
against screenshots or data previously exported by the partner.

The Android app and PWA perform Authorization Code with PKCE S256 and replay
protection in the system browser. There is no client secret in the APK or web
bundle, and no OIDC sign-in in a WebView.

After successful linking, a request carries two separate tokens:

```text
Authorization: Bearer <Authentik-Access-Token>
X-Lunara-Authorization: Bearer <Lunara-Access-Token>
```

The Authentik token is sent exclusively to the exact same HTTPS origin and
renewed before the proxy request when necessary. OIDC-based Lunara refreshes
also require a fresh external token for the exact same linked identity.
The inner Lunara token remains authoritative for all application permissions.

The server verifies OIDC using a pinned HTTPS issuer, audience, subject, `iat`,
`exp`, maximum token age, clock tolerance, and an explicit list of asymmetric
algorithms. Keys come from the configured JWKS endpoint. A token with an
incorrect issuer, audience, `azp`, algorithm, age, or subject is rejected
with a generic error.

Lunara does not automatically provision or link accounts based on email,
display name, or username. Self-linking requires an active Lunara session
and the current Lunara password. Administrative linking requires the exact
UUID, issuer, subject, and a derived confirmation value. Optionally, the old
password sign-in is disabled and all sessions are revoked.

The Authentik provisioner uses a public client, strict redirect URIs,
explicit consent, `openid offline_access`, short-lived codes, 15-minute
access tokens, 30-day refresh tokens, and a narrowly restricted user group
without roles. It rejects unknown or partially existing configurations and
creates a validated database dump before making a change.

## Android app link

Production always uses the verifiable HTTPS publisher callback configured at
build time, independently of the API and issuer domains. Android associates
it with `de.lunara.app` only if `assetlinks.json` on that exact callback host
contains the SHA-256 fingerprint of the installed app-signing certificate.

Production configuration may be enabled only after the fingerprint of the
certificate actually distributed has been entered. With Google Play, this
is the Play app-signing key. The upload key alone is insufficient.
An incorrect or old fingerprint causes Android domain verification to fail
closed. The production OIDC provider registers only the Android HTTPS and
PWA HTTPS redirects.

A package-specific custom scheme remains exclusive to the Android `local`
flavor with a separate local development provider and its own client_id.
PKCE protects an intercepted code, but does not authenticate a public client.
If the same custom scheme were registered on the production client, a
third-party app could initiate a new sign-in using the public client_id and
its own PKCE verifier while impersonating Lunara to the user. This redirect
is therefore deliberately absent from production provisioning. The production
manifest, including the production debug/test build, contains no custom-scheme
filter at all. This test build cannot complete the production OIDC flow.

An operator with their own distribution sets `LUNARA_APP_LINK_HOST` to a
callback host they control and publishes asset links there for their own
signing certificate.

The certificate fingerprint is public. Private keystores, keys, alias values,
and passwords remain exclusively on the server and are passed to Gradle
only through `LUNARA_UPLOAD_*`.

## Lunara passwords and sessions

New passwords contain 8 to 128 Unicode code points, without mandatory character
classes. A long, unique passphrase remains recommended. Existing passwords
are verified with 1 to 128 code points for migration compatibility. Newly set
passwords must still contain at least eight.

- Passwords are hashed with Argon2id.
- Sign-in and sensitive authentication actions are rate-limited and return
  generic errors.
- Access tokens are short-lived.
- Refresh tokens are stored in PostgreSQL only as peppered hashes.
- Rotation replaces the token. Replay revokes the token family.
- Sessions are device-specific and can be revoked individually or in full.
- Changing a password requires the current password and revokes all other sessions.

The bootstrap token proves only possession of a fresh instance. After the
first setup, completion is stored permanently in `ServerState`. Subsequent
account deletion does not reopen bootstrap setup. Remove the token from the
live configuration afterward.

An invitation code is single-use, revocable, and stored only as a hash.
Its raw value is issued once and contains no password.

## Android local data and local-only backup

Room uses SQLCipher. The database key, server refresh token, and OIDC state
are protected by a non-exportable Android Keystore key. In local-only mode,
the app generates a random local tracker ID and performs neither sign-in
nor server synchronization.

Android cloud backup and system-managed device transfer are excluded for all
Lunara app domains. This prevents encrypted database or DataStore remnants
from being copied to a new device without the deliberately nontransferable
Keystore key.

The intended transfer method is a portable `.lunara` file:

- PBKDF2-HMAC-SHA256 with 310,000 iterations and a 16-byte salt;
- AES-256-GCM with a 12-byte nonce, 128-bit tag, and format AAD;
- Passphrase length of 12 to 128 Unicode code points;
- Maximum file size of 16 MiB or plaintext size of 12 MiB;
- Strict schema, size, UUID, date, range, and overlap validation before writing;
- Passphrases and temporary plaintext/key buffers are overwritten on a
  best-effort basis and never stored.

Import replaces local-only data in a Room transaction and writes an import
journal. Settings are restored afterward. If the process stops between the
database commit and settings commit, the journal is completed idempotently
at the next startup. Room and DataStore do not share an operating-system
transaction, so the journal forms the deliberately implemented recovery boundary.
The contents of an imported backup should still be checked on the new device.

Android's Storage Access Framework allows an explicit choice of a file provider
such as Google Drive or Nextcloud. Lunara integrates no Google SDK, knows no
cloud credentials, and performs no automatic uploads or background sync.
The chosen provider remains a separate trust and privacy boundary.

On logout or a confirmed server change, Room tables, the mutation queue,
local caches, DataStore, cookies, tokens, notifications, and associated
Keystore aliases are deleted. Unsynchronized data is deliberately lost
in the process, and the interface warns about this.

## PWA

The PWA stores offline data and sync mutations in origin-bound IndexedDB.
Session tokens are wrapped with a non-exportable Web Crypto key and AES-GCM.
Local-only PWA mode additionally seals profiles, periods, and daily logs
in an AES-256-GCM vault using a key derived from the local-only passphrase
and 600,000 PBKDF2-HMAC-SHA256 iterations. The key and passphrase are not
persisted. The vault must be unlocked again after a page restart.
Legacy plaintext local-only entries are removed in the same IndexedDB transaction.

Account identity changes, logout, and 401 clear account-bound caches and
prevent one account's mutations from being replayed under another account.

The service worker treats `/api`, `/health`, `/ready`, and `/api-docs` as
neither navigation fallbacks nor cacheable app resources. API requests are
same-origin, use `no-store`, and reject redirects.

In self-hosted PWA mode, synchronized server data can be stored as an ordinary
origin-bound offline cache in IndexedDB. This cache does not have the additional
passphrase encryption used by the local-only vault. It relies on the browser
profile, device lock, and operating-system storage. A shared or compromised
browser profile lies outside the protection boundary. For particularly
sensitive server use, the native Android app with SQLCipher is preferable.

Screenshot protection and browser notifications cannot run in the PWA.
Privacy/notification preferences imported from a portable backup remain in
the encrypted vault, but are explicitly shown as not applicable in the browser.
The vault locks when the page is hidden, on `pagehide`, and no later than
15 minutes of inactivity. A new PWA process does not have the nonpersisted
key and requires the passphrase again. Recovery is impossible without it.

## Logs, notifications, and backups

Passwords, tokens, gateway headers, note contents, and health values are
not logged. Audit events contain only security-relevant actions without
health values.

Android notifications are disabled by default, scheduled locally, and neutral
on the lock screen by default. Partner notifications are generated only from
a current partner projection filtered on the server and fail closed without
a current response. Android may delay WorkManager for power-saving reasons.
Reminders do not provide an exact alarm clock.

Server backups contain health data. `scripts/backup.sh` excludes secrets.
Back up the dump and original `APP_MASTER_KEY` separately with encryption,
use file permissions `0600` and directory permissions `0700`, and test
restoration only in an isolated instance.

## Lost device

1. Revoke the affected Lunara sessions or all sessions.
2. Change the Lunara password if someone may know it.
3. Revoke the OIDC session in Authentik and check the password/MFA there.
4. Revoke outstanding invitations.
5. Use the operating system's remote wipe feature.
6. Review the security audit. It contains no health values.

A local-only device has no server session. Without a previously exported,
readable backup, its data cannot be restored after loss or failure.

## Known limitations of 1.1.0

- Android instrumentation was compiled, but not executed because no emulator
  or physical device was available. Keystore, SQLCipher, WorkManager,
  app-link, and accessibility behavior therefore also require physical device tests.
- The local-only PWA vault is passphrase-encrypted. The offline cache of
  self-hosted PWA mode has no additional application-level encryption.
- Biometric app locking, ntfy, a widget, and an Android SSE client are not
  fully implemented.
- There is no Authentik provisioner for arbitrary Authentik versions and
  freely differing existing configurations. The script is pinned to the
  documented contract.
- The public metadata response and `assetlinks.json` are intentional
  exceptions. They must contain no account or health data.
- Rate limits are stored in memory in the individual API process.
  Horizontal scaling requires a shared persistent limiter.
- Check-in contents in PostgreSQL are not additionally encrypted with the
  master key and should not contain detailed health-related free text.
- The note format authenticates ciphertext, but in version 1 it does not
  bind it to the user ID and record ID using AAD.
- The fertile window is only a rough opt-in estimate based on sufficient data.
  It does not confirm ovulation and provides neither diagnosis nor contraception.
- Maintenance and admin scripts delegate to the mode-aware
  `scripts/compose.sh` wrapper. A restore remains a destructive operation
  and requires the documented explicit confirmation switch.

## Reporting vulnerabilities

Do not put live configurations, health data, tokens, keystores, or exploit
secrets in public issues. Submit security reports with redacted, reproducible
steps through this repository's GitHub **Security → Report a vulnerability**
(Private Vulnerability Reporting).
