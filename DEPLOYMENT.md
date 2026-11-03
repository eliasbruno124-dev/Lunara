# Running Lunara 1.3.3

This guide is for operators of their own instance.
The illustrated [app setup guide](docs/SETUP.md) describes the steps on
each device.

Reference environment: Debian 12, Docker Engine with the Compose plugin and Traefik 3.
Lunara does not include a reverse proxy or Authentik. PostgreSQL and
the API expose no host ports in the supplied Compose configuration;
HTTPS terminates at the existing proxy.

## 1. Operating mode and prerequisites

| Mode | Public route | Personal sign-in |
| --- | --- | --- |
| `direct` | Traefik → Lunara | Lunara username and password |
| `authentik` | Reverse proxy → Authentik proxy → Lunara | OIDC; optionally, Lunara password sign-in deliberately kept available |

Before starting, you need:

- a domain you control, such as `lunara.example.com`, with
  a valid HTTPS certificate;
- an existing reverse proxy and its external Docker network;
- enough storage for images and PostgreSQL, plus a backup destination;
- access to the source code and the matching signed release APK.

The stack builds the `lunara-api:1.3.3` image **locally**.
A published GHCR image is not required.
The API also serves the web app and the APK. The APK is copied into the
image during the build, rather than attached through an additional host mount.

The following start and update commands apply to an installation managed
independently with Compose. If Portainer manages the stack,
configuration, migration and redeployment must be coordinated through the
existing management process. Do not create a second owner by running
`compose up` alongside it or modify other stacks.

## 2. Source code and Android artifact

In a dedicated project directory:

```bash
git clone https://github.com/eliasbruno124-dev/Lunara.git
cd Lunara
```

Use the source revision and APK from the same release. The repository may
require access permissions; do not put tokens in the clone URL.

The Docker build requires `artifacts/lunara-release.apk`. If the source
package does not include it, download the signed APK and `SHA256SUMS` from
the corresponding [GitHub release](https://github.com/eliasbruno124-dev/Lunara/releases)
and place them under `artifacts/`. Then run:

```bash
test -s artifacts/lunara-release.apk
(cd artifacts && sha256sum --check --ignore-missing SHA256SUMS)
```

Do not use a debug or unsigned APK as a user download.
For your own Android builds, also follow the
[signing and App Link requirements](docs/GOOGLE_PLAY_RELEASE.md).
Private keys and keystores must stay outside the build context.

## 3. Configuration and secrets

Only for a new installation without an existing `.env`:

```bash
./scripts/generate-secrets.sh "$(pwd)/.env"
chmod 0600 .env
```

The generator refuses an existing destination. It does not replace
existing keys. Afterwards, open the file **locally** in an editor and set
the public settings to match your server:

```dotenv
APP_VERSION=1.3.3
PUBLIC_BASE_URL=https://lunara.example.com
PUBLIC_HOST=lunara.example.com
GATEWAY_MODE=direct
PROXY_NETWORK=proxy
TRAEFIK_ENTRYPOINT=websecure
TRAEFIK_CERT_RESOLVER=letsencrypt
OIDC_ENABLED=false
OPENAPI_ENABLED=false
REGISTRATION_ENABLED=false
```

The proxy network, entry point and certificate resolver must match your
existing Traefik setup; the names above are examples. The direct override
is not loaded in Authentik mode.

The generator creates random values for `POSTGRES_PASSWORD`,
`APP_MASTER_KEY`, `AUTH_SIGNING_KEY`, `REFRESH_TOKEN_PEPPER` and
`BOOTSTRAP_TOKEN`. Never use example values from `.env.example` as real
secrets. Only the operator must be able to read this file, and it must stay
outside Git, screenshots, tickets and chats.

`APP_MASTER_KEY` consists of exactly 32 random bytes in canonical Base64.
Back it up separately in an encrypted offline password store and
keep it across updates. Without the same key, encrypted database fields
from a backup cannot be read.

Check the configured proxy network, for example:

```bash
docker network inspect proxy >/dev/null
```

If it is missing, set it up deliberately alongside your reverse proxy;
the Lunara wrapper does not create an external proxy network. PostgreSQL
stays exclusively on the internal `lunara_internal` network.

## 4. Starting direct mode

The [scripts/compose.sh](scripts/compose.sh) wrapper reads `GATEWAY_MODE`
from `.env`. In direct mode, it adds
[infra/docker/docker-compose.direct.yml](infra/docker/docker-compose.direct.yml).

```bash
./scripts/compose.sh config --quiet
./scripts/compose.sh build api
./scripts/compose.sh up -d
./scripts/compose.sh ps --all
./scripts/compose.sh logs --tail=100 migrate api
```

`config --quiet` validates the configuration without printing expanded
secrets. Inspect logs locally and redact specific sensitive values before
sharing them. The migration service must finish successfully, and PostgreSQL
and the API must be healthy. To check readiness:

```bash
./scripts/compose.sh exec -T api \
  node -e "fetch('http://127.0.0.1:3000/ready').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
curl --fail --silent --show-error https://lunara.example.com/api/v1/meta
```

Meta must contain the expected version, `apiVersion`, `setupRequired`,
`authenticationMethods` and `serverTime`.
The response is public, contains no health data and is not cached.
API documentation stays disabled by default.

An alternative file-based Traefik configuration is available at
[infra/traefik/dynamic-direct.example.yml](infra/traefik/dynamic-direct.example.yml).
Do not route the same host twice by adding it alongside the Compose router.

## 5. First account and partner

1. On an empty server, Meta reports `setupRequired: true`.
2. In Android, connect to the server address and choose **Set up Lunara for the first time**.
   Create the first tracker account using the locally stored
   `BOOTSTRAP_TOKEN`; enter only its value in the app field provided.
3. After successful setup, remove the bootstrap token from the active
   server configuration and recreate the API through the responsible
   management process.
4. Further tracker relationships are only possible with
   `REGISTRATION_ENABLED=true`. Public registration does not need to stay
   enabled for a private instance used by a couple.
5. The person tracking their cycle creates an invitation. Their partner signs in
   with a separate account and explicitly redeems the invitation.
   With OIDC, the invitation can be linked to the verified Authentik identity
   without creating a second Lunara password.
6. The person tracking their cycle chooses the privacy sharing settings.
   Actual period dates, the predicted window and the fertile window
   are separate settings.

Completion of bootstrap setup is stored permanently in PostgreSQL.
Deleting all accounts does not reopen it.
An existing account containing health data is never silently moved to
another relationship.

## 6. Optional: Authentik

### Keep gateway access and personal sign-in separate

The Authentik proxy protects external access. Lunara additionally checks
the account, role and sharing permissions. Modern clients use separate headers:

```text
Authorization: Bearer <Authentik-Access-Token>
X-Lunara-Authorization: Bearer <Lunara-Access-Token>
```

These placeholders are not configuration values; never put real tokens
in documentation or an APK. Technical Basic access remains a controlled
compatibility option for legacy clients, rather than the new default.

The existing proxy contract permits exactly these anonymous paths:

- `/api/v1/meta`
- `/.well-known/assetlinks.json`
- `/invite`

The exact exception is
`^/(?:api/v1/meta|\.well-known/assetlinks\.json|invite)$`.
Do not create a broad exception for `/`, `/api`, subpaths or the APK download.
Invitations carry their code in the fragment
`https://lunara.example.com/invite#invite=CODE`; the code must not become
part of a provider redirect URL.

### Android: the API domain is not the callback host

The production APK uses an HTTPS App Link fixed at build time:

```text
https://publisher-callback.example.com/auth/android-callback
```

The host shown here is only an example. An already published APK keeps
its compiled publisher host even when your API runs on another domain.
Exactly this redirect must be registered in the OIDC provider.
Before setup, check the actual host of the build you use in its release
or build configuration; the
[Gradle configuration](apps/android/app/build.gradle.kts) contains the
source default and available build overrides.

On **this callback host**, `/.well-known/assetlinks.json` must contain
the package ID and SHA-256 fingerprint of the installed app's certificate.
A file hosted only on a different API domain is insufficient.

For your own distribution:

- set `LUNARA_APP_LINK_HOST` to a host you control during the build;
- publish the matching `assetlinks.json` there;
- register the exact HTTPS Android redirect and
  `https://lunara.example.com/auth/callback` for the web app.

For directly distributed APKs, the release certificate applies. With Play App
Signing, Google's app signing certificate applies, not just the upload key.
Production does not accept custom-scheme redirects. Those redirects are
reserved for the `local` flavor with a separate development provider.
Fingerprints are public; the private signing key is not.

### Provision only for the matching existing contract

[scripts/configure-authentik-oidc.sh](scripts/configure-authentik-oidc.sh)
is not a general-purpose Authentik installer. It checks an existing,
specific Lunara proxy contract and Authentik 2026.5.3.
Differences must first receive a technical review; do not bypass the
preflight check by removing its security controls.

Example of a **read-only preflight check**, after adjusting all public
names and the actual APK callback host:

```bash
sudo LUNARA_RUNTIME_DIR=/docker/lunara \
  LUNARA_PUBLIC_HOST=lunara.example.com \
  AUTHENTIK_PUBLIC_URL=https://auth.example.com \
  LUNARA_AUTHENTIK_USERS=alice,bob \
  LUNARA_ANDROID_APP_LINK_HOST=publisher-callback.example.com \
  AUTHENTIK_CONTAINER=authentik-server \
  AUTHENTIK_DB_CONTAINER=postgresql \
  /docker/lunara/scripts/configure-authentik-oidc.sh --check
```

All listed users must already exist as active internal Authentik accounts.
Only run the provisioner without `--check` after a successful preflight
check, certificate verification and deliberate approval of the change.
Also provide `LUNARA_ANDROID_APP_PACKAGE=de.lunara.app` and
`LUNARA_ANDROID_CERT_SHA256` with the verified public fingerprint.
The script first creates a validated database dump.

It configures Authorization Code with PKCE, refresh tokens,
`openid offline_access`, RS256, explicit consent and the permitted group.
Public OIDC configuration values are written locally to
`authentik-oidc.env`. With `GATEWAY_MODE=authentik`, the wrapper also loads
this file. Then recreate the API through the responsible management
process and test the complete browser sign-in, including the return to
the signed Android app.

An existing Lunara account is not linked automatically on the basis of
a matching email address or name. Linking requires another password
verification or a targeted administrator migration with a verified issuer
and subject. New OIDC tracker profiles require an explicit choice to register
and enabled registration; partners need an invitation.

### Plan session lifetimes realistically

The provisioner sets a lifetime of 15 minutes for access tokens and 365 days
for renewable Authentik refresh tokens. A later configuration change does
not retroactively extend the lifetime of credentials already issued.

Lunara can renew an unrevoked OIDC device session after a longer period of
inactivity if fresh provider proof for exactly the same linked identity
is available. Password sessions keep their limited renewal window,
30 days by default. Provider revocation, genuinely expired provider credentials,
account deletion and lost device keys are not network errors and
are not bypassed.

## 7. Checking the APK download

The APK bundled during the image build is available at the stable path:

```text
https://lunara.example.com/api/v1/downloads/lunara.apk
```

In the browser, open **Settings → App & connection → Download Android app
(.apk)**. An installed PWA provides the same link. In Authentik mode,
a valid gateway sign-in is required; the download is not a public proxy
exception. The browser downloads the file directly without caching it
as a large JavaScript blob.

After every APK update, build and deploy the API image with the new
artifact. Simply replacing a host file does not change the APK already
included in the image. Also verify the APK version, signature and
release checksum. GitHub releases remain a separate download option.

## 8. Backups and updates

The following paths assume an example installation under
`/docker/lunara`. Replace them with your actual project directory
and run `./scripts/update.sh` from that directory.

Before configuration changes and migrations:

```bash
sudo LUNARA_ENV_FILE=/docker/lunara/.env \
  /docker/lunara/scripts/backup.sh
```

The archive contains a PostgreSQL custom-format dump, checksums,
the Compose file and selected public configuration values.
It is **not encrypted automatically**. Store health data and backups
only at a protected, encrypted backup destination.
Do not put inline secrets in Compose; the Compose file is included in the backup.
The master key and other secrets are not copied from `.env` and must
be stored securely and separately.

Practise restoring regularly in an isolated test instance.
[scripts/restore.sh](scripts/restore.sh) replaces the database and requires
`--confirm-replace-lunara-database`. A production restore is a deliberate,
destructive maintenance operation rather than a normal update step.

For an installation managed exclusively with Compose, after adopting
the verified source revision and matching APK:

```bash
sudo LUNARA_ENV_FILE=/docker/lunara/.env ./scripts/update.sh
```

The script locks out concurrent maintenance operations, creates a backup,
builds the image, runs migrations, replaces the API and checks readiness.
It does not run Git pull or automatically download an APK release.
For Portainer, use the corresponding existing update process.

After every change, check readiness, the Meta version, container status
and relevant logs. With Authentik, also test sign-in and the App Link return.
After a database migration, an older image is not automatically a safe rollback;
the migration must be backward compatible, or a planned restore is required.

## 9. Operations and further documentation

- `/health`: process check.
- `/ready`: also checks the database.
- `/api/v1/meta`: public version and supported sign-in methods.

The API runs as UID/GID `10001:10001` with a read-only root filesystem,
`no-new-privileges` and no Linux capabilities.
Do not leave it privileged "for troubleshooting" or expose the database
to the internet.

[Security](SECURITY.md) · [Privacy](PRIVACY.md) ·
[Android signing and Play](docs/GOOGLE_PLAY_RELEASE.md) ·
[Code navigation](docs/CODE_GUIDE.md)
