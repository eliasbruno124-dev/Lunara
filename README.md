# Lunara

**A private, self-hosted cycle tracker for one person and their partner.**
Android app, installable web app and your own server – no ads, no tracking,
no telemetry and no Google services.

[![Release](https://img.shields.io/github/v/release/eliasbruno124-dev/Lunara)](https://github.com/eliasbruno124-dev/Lunara/releases/latest)
[![Image](https://img.shields.io/badge/ghcr.io-eliasbruno124--dev%2Flunara-blue)](https://github.com/eliasbruno124-dev/Lunara/pkgs/container/lunara)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

<p>
  <img src="docs/screenshots/hd/03-tracker-today.png" width="24%" alt="Today view with the expected period start">
  <img src="docs/screenshots/hd/05-tracker-calendar-recorded.png" width="24%" alt="Calendar with recorded period days">
  <img src="docs/screenshots/hd/04-tracker-calendar-estimate.png" width="24%" alt="Calendar with an estimated period">
  <img src="docs/screenshots/hd/07-partner-today.png" width="24%" alt="Partner view with outlook and today's suggestion">
</p>

> Lunara is **not a medical device**. It does not diagnose anything, does not
> confirm ovulation and must **not be used for contraception**.

## Quick start (your own server)

```bash
mkdir lunara && cd lunara
BASE=https://raw.githubusercontent.com/eliasbruno124-dev/Lunara/main/examples
curl -fsSLO $BASE/docker-compose.yml && curl -fsSLO $BASE/Caddyfile
curl -fsSL $BASE/.env.example -o .env && chmod 600 .env
# fill in .env: domain + secrets (the commands are in the file)
docker compose --profile caddy up -d
```

Then open `https://your-domain`. The **[installation guide with screenshots](docs/INSTALL.md)**
explains every step: HTTPS, first account, Android app, inviting a partner,
updates and backups.

**Only on your own Android phone?** You don't need a server. Download
`lunara-release.apk` from the [latest release](https://github.com/eliasbruno124-dev/Lunara/releases/latest)
and choose **Only on this device** (Android 8+).

Lunara starts in English. German is available as a second language.

## Ways to use Lunara

| Mode | Storage | Sharing |
| --- | --- | --- |
| Android, only on this device | Encrypted with SQLCipher and the Android Keystore | No, no server account |
| Web app, only on this device | Passphrase-encrypted local vault | No, no server account |
| Your own server | PostgreSQL; Android and the web app synchronize with the same API | Separate tracker/partner accounts, only explicitly shared details |
| Your own server with Authentik | The same Lunara server behind the gateway | Personal OIDC sign-in instead of an additional Lunara password |

For Android solo mode the APK from GitHub is all you need: **no server, no
Docker, no Authentik and no account**. The web app has to be reachable for the
first load; local data is not synchronized to the server because of that.
Sharing requires your own HTTPS instance. Authentik is optional and not part
of the Lunara Compose stack.

## Features

- Periods, daily logs, calendar and history on Android; period entries and
  calendar in the web app as well.
- Offline recording with pending changes and automatic synchronization.
- English and German, light/dark mode and an installable web app.
- Separate roles: the tracking person manages data and sharing, the partner
  only sees what is allowed.
- Granular sharing, pauses and private days; optional supportive tips.
- Encrypted solo backups for changing devices and server-side backup/restore
  tools.
- Export of your own periods as CSV/JSON and file import with preview and
  confirmation on Android and the web; no guaranteed compatibility with
  arbitrary third-party formats.

A partner connection never turns on sharing automatically. "Not shared" means
neither "no entries" nor "data deleted".

## Downloads

| What | Where |
| --- | --- |
| Android app (signed) | `lunara-release.apk` in the [release](https://github.com/eliasbruno124-dev/Lunara/releases/latest), checksums in `SHA256SUMS` |
| Server image (amd64 + arm64) | `ghcr.io/eliasbruno124-dev/lunara:<version>` or `:latest` |
| Example configuration | [`examples/`](examples/) |

Every running instance also serves the matching APK itself:
`https://your-domain/api/v1/downloads/lunara.apk` (in the web app under
**Settings → App & connection**).

## Updating and keeping your data

Install a new APK as an update **over the existing installation**. Do not
uninstall first and do not clear the app data. Android only accepts an update
with a compatible package ID, a higher version number and a matching
signature; a self-signed build does not replace the published APK.

Sign-in is renewed automatically but not guaranteed forever. Revoked or
genuinely expired provider credentials, deleted accounts and lost device keys
require signing in again. Use the same account for **Renew sign-in**; pending
local entries are kept. Details: [setup and help](docs/SETUP.md).

## Development

Node.js 24+, npm 11+; for Android a suitable JDK and the Android SDK.
The app uses Compose/Kotlin, the web app TypeScript/Vite and the API
NestJS/Prisma with PostgreSQL.

```bash
npm ci
npm run prisma:generate
npm run prisma:validate
npm run lint
npm test
npm run build
npm run openapi:check
```

Android tasks and device checks are described in [TESTING.md](TESTING.md).
Read [CODE_GUIDE.md](docs/CODE_GUIDE.md) before making changes and check the
generated code map afterwards. Development, debug and unsigned artifacts are
not equivalent user updates.

## Documentation

- [Installing on your own server (with screenshots)](docs/INSTALL.md)
- [Setup, sharing, backup and changing devices in the app](docs/SETUP.md)
- [Advanced deployment: Traefik, Authentik/OIDC, scripts](DEPLOYMENT.md)
- [Security](SECURITY.md) and [privacy](PRIVACY.md)
- [Prediction method and limits](ALGORITHM.md), [period end](docs/PERIOD-ENDS.md)
- [Partner tips](docs/PARTNER_TIPS.md) and [design](docs/DESIGN.md)
- [Testing](TESTING.md) and [code navigation](docs/CODE_GUIDE.md)
- [Android signing and Google Play preparation](docs/GOOGLE_PLAY_RELEASE.md)
- [Changelog](CHANGELOG.md)

## License

[MIT](LICENSE). Please do not report security issues as public issues; follow
[SECURITY.md](SECURITY.md) instead.
