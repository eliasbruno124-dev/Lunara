# Deployment

Diese Anleitung gilt für Debian 12, Docker Engine mit Compose Plugin und
Traefik 3. Lunara veröffentlicht weder PostgreSQL noch die API direkt als
Host-Port. TLS endet am vorhandenen Reverse Proxy.

Es gibt zwei unterstützte Gateway-Modi:

- `direct`: Traefik leitet direkt zu Lunara; Lunara verwaltet die persönlichen
  Konten und Tokens selbst.
- `authentik`: Traefik oder Pangolin leitet über einen Authentik-Proxy. Lunara
  kann zusätzlich persönlichen OIDC-Login anbieten.

Der Modus wird einmal bewusst gewählt. `scripts/compose.sh` lädt dafür die
richtigen Compose-Dateien und im Authentik-Modus optional die serverlokale
`authentik-oidc.env`.

## 1. Verzeichnis, Rechte und Secrets

Empfohlener Pfad:

```bash
install -d -m 0750 /docker/lunara
cd /docker/lunara
./scripts/generate-secrets.sh /docker/lunara/.env
chmod 0600 .env
```

Der Generator verweigert ein bereits vorhandenes Ziel und überschreibt keine
Live-Secrets. Seine Beispiel-Domain danach bewusst durch die eigene Domain
ersetzen. Die echte Datei gehört `root:root` und besitzt Modus `0600`.
Mindestens diese Werte müssen eindeutig gesetzt sein:

```dotenv
PUBLIC_BASE_URL=https://lunara.example.com
PUBLIC_HOST=lunara.example.com
GATEWAY_MODE=direct
PROXY_NETWORK=proxy

POSTGRES_PASSWORD=<zufällig>
APP_MASTER_KEY=<exakt 32 zufällige Bytes, kanonisch Base64>
AUTH_SIGNING_KEY=<zufällig, mindestens 32 Zeichen>
REFRESH_TOKEN_PEPPER=<zufällig, mindestens 32 Zeichen>
BOOTSTRAP_TOKEN=<einmalig, 20 bis 256 Zeichen>
```

`APP_MASTER_KEY` muss getrennt in einem verschlüsselten Offline-
Passwortspeicher gesichert und über die gesamte Lebensdauer der Daten
beibehalten werden. Ohne ihn sind verschlüsselte Notizen aus einem
Datenbankdump nicht lesbar. Echte `.env`, Keystores, Tokens und Dumps gehören
nie nach GitHub.

Das externe Proxy-Netz muss bereits existieren:

```bash
docker network inspect proxy >/dev/null
# Nur auf einer neuen Installation, falls es wirklich fehlt:
docker network create proxy
```

Auf einem Pangolin-System kann das Netz anders heißen; dann
`PROXY_NETWORK=pangolin` verwenden. PostgreSQL hängt ausschließlich am
internen Netz `lunara_internal`.

## 2. Direct-Modus

In `.env`:

```dotenv
GATEWAY_MODE=direct
OIDC_ENABLED=false
OPENAPI_ENABLED=false
OPENAPI_TRUST_AUTHENTIK=false
```

Dann ausschließlich über den Wrapper prüfen und starten:

```bash
./scripts/secret-scan.sh
./scripts/compose.sh config --quiet
./scripts/compose.sh build api
./scripts/compose.sh up -d
./scripts/compose.sh ps --all
./scripts/compose.sh logs --tail=100 migrate api
```

`infra/docker/docker-compose.direct.yml` aktiviert den Traefik-Router, TLS und
Sicherheitsheader. Eine alternative dateibasierte Traefik-Konfiguration steht
in `infra/traefik/dynamic-direct.example.yml`; nicht beide Router parallel
verwenden.

Im Direct-Modus authentifizieren sich Android und PWA mit Lunara-
Benutzername/-Passwort. `/api/v1/meta` ist absichtlich öffentlich und
cachefrei. OpenAPI bleibt standardmäßig aus. Wird sie benötigt, muss sie mit
einem eigenen langen Dokumentationspasswort geschützt werden.

## 3. Authentik-Modus

### 3.1 Sicherheitsvertrag

Im unterstützten Zielzustand sind genau zwei Endpunkte ohne Authentik-Sitzung
erreichbar:

- `/api/v1/meta` für nicht sensible Server-/OIDC-Erkennung;
- `/.well-known/assetlinks.json` für die Android-Domainverifikation.

PWA, Login, Refresh, Sync, Health, Ready und OpenAPI laufen durch den
Authentik-Proxy. Moderne Clients führen einmalig den OIDC Authorization Code
Flow mit PKCE S256, State und Nonce im Systembrowser aus. Danach gelten zwei
getrennte Schutzschichten:

```text
Authorization: Bearer <Authentik-Access-Token>
X-Lunara-Authorization: Bearer <Lunara-Access-Token>
```

Der äußere Token öffnet nur den Proxy. Der innere Token bestimmt Konto, Rolle,
Mandant und Freigaben. Ein technischer Basic-App-Zugang bleibt für ältere
Clients kompatibel, ist aber nicht der empfohlene neue Einrichtungsweg.

### 3.2 Voraussetzung und Vorprüfung

`scripts/configure-authentik-oidc.sh` ist absichtlich fail-closed. Es wurde für
Authentik 2026.5.3 und einen bereits vorhandenen, exakt geprüften Lunara-
Proxy-Basisvertrag geschrieben. Es übernimmt keine beliebige fremde
Authentik-Konfiguration. Bei Abweichung stoppt `--check` ohne Änderung.

Vor jeder Änderung:

```bash
docker inspect authentik-server postgresql >/dev/null
docker network inspect pangolin >/dev/null
sudo LUNARA_RUNTIME_DIR=/docker/lunara \
  LUNARA_PUBLIC_HOST=lunara.example.com \
  AUTHENTIK_PUBLIC_URL=https://auth.example.com \
  LUNARA_AUTHENTIK_USERS=alice,bob \
  LUNARA_ANDROID_APP_LINK_HOST=publisher-callback.example.com \
  AUTHENTIK_CONTAINER=authentik-server \
  AUTHENTIK_DB_CONTAINER=postgresql \
  /docker/lunara/scripts/configure-authentik-oidc.sh --check
```

Das Skript verlangt, dass alle aufgelisteten Authentik-Nutzer bereits als
aktive interne Konten existieren. Die Gruppe erhält keine Adminrollen.

### 3.3 Android App Link und Zertifikat

Der Production-Flavor verwendet immer den beim Build konfigurierten,
verifizierten Callback:

```text
https://publisher-callback.example.com/auth/android-callback
```

Dieser Callback ist bewusst unabhängig von der Self-hosting-API- und
Authentik-Issuer-Domain. Jeder Betreiber registriert exakt die feste
Publisher-Redirect-URI in seinem OIDC-Provider. Android akzeptiert sie für
`de.lunara.app` nur, wenn `/.well-known/assetlinks.json` **auf dem Callback-
Host** den SHA-256-Fingerprint des tatsächlich auf dem Gerät verwendeten
App-Signing-Zertifikats enthält.

- Bei direkter APK-Verteilung ist der Fingerprint des Release-Zertifikats
  relevant.
- Bei Google Play ist nach Aktivierung von Play App Signing der Fingerprint des
  **Play App Signing Key**, nicht nur der Upload-Key, relevant.
- Während einer kontrollierten Übergangsphase können bis zu acht eindeutige
  Fingerprints konfiguriert werden.

Der Production-Provisioner registriert ausschließlich zwei verifizierbare
HTTPS-Redirects: den festen Publisher-App-Link für Android und den PWA-
Callback. Er registriert keinerlei Custom Scheme oder Debug-Redirect.

Das paketgebundene Custom Scheme existiert nur im Android-`local`-Flavor für
einen **getrennten lokalen Entwicklungsprovider mit eigener client_id**. PKCE
schützt zwar einen abgefangenen Code, authentifiziert aber keinen öffentlichen
OAuth-Client: Eine fremde App könnte sonst mit derselben öffentlichen client_id
und eigenem PKCE-Verifier einen eigenen Flow starten. Deshalb darf ein Custom-
Scheme-Redirect niemals in den Production-Provider aufgenommen werden.

Betreiber einer eigenen App-Distribution setzen beim Build
`LUNARA_APP_LINK_HOST` auf ihren kontrollierten Callback-Host, hosten dort die
passende `assetlinks.json` und registrieren ausschließlich diesen HTTPS-
Redirect. Das Production-Manifest einschließlich `ProductionDebug` enthält
keinen Custom-Scheme-Intent-Filter. Wer die offizielle/Play-App verwendet,
nutzt dagegen den festen Publisher-Host auch dann, wenn API und Authentik auf
einer ganz anderen Domain laufen.

Der Fingerprint ist öffentlich und kein privater Schlüssel. Keystore und
Passwörter bleiben ausschließlich serverlokal.

### 3.4 Anwenden

Erst nach erfolgreichem Check und einem separat geprüften Fingerprint:

```bash
sudo LUNARA_RUNTIME_DIR=/docker/lunara \
  LUNARA_PUBLIC_HOST=lunara.example.com \
  AUTHENTIK_PUBLIC_URL=https://auth.example.com \
  LUNARA_AUTHENTIK_USERS=alice,bob \
  LUNARA_ANDROID_APP_LINK_HOST=publisher-callback.example.com \
  LUNARA_ANDROID_APP_PACKAGE=de.lunara.app \
  LUNARA_ANDROID_CERT_SHA256='<SHA-256-MIT-DOPPELPUNKTEN>' \
  AUTHENTIK_CONTAINER=authentik-server \
  AUTHENTIK_DB_CONTAINER=postgresql \
  /docker/lunara/scripts/configure-authentik-oidc.sh
```

Das Skript:

- erzeugt vor einer Authentik-Mutation einen validierten PostgreSQL-Custom-
  Dump;
- erstellt einen öffentlichen OIDC-Client ohne Client Secret;
- erlaubt nur Authorization Code und Refresh Token, `openid` und
  `offline_access`, asymmetrisches RS256 sowie explizite Zustimmung;
- bindet ausschließlich die konfigurierte Benutzergruppe;
- setzt die genau zwei öffentlichen Bypass-Pfade;
- schreibt nur öffentliche API-/OIDC-Werte als root-only Datei
  `/docker/lunara/authentik-oidc.env`.

`LUNARA_ANDROID_APP_LINK_HOST` muss exakt dem Host entsprechen, der in der
installierten Production-APK kompiliert ist; er ist nicht automatisch
`LUNARA_PUBLIC_HOST`. Bei der offiziellen Distribution ist dies der feste
Publisher-Callback-Host. Nur wenn Callback- und API-Host identisch sind, kann
die lokale Lunara-API selbst die maßgebliche `assetlinks.json` ausliefern.
Andernfalls hostet der Publisher diese Datei zentral auf dem Callback-Host;
eine zusätzliche Asset-Link-Antwort der Self-hosting-API ist für Android ohne
Bedeutung.

Danach in `.env` setzen:

```dotenv
GATEWAY_MODE=authentik
```

und Lunara mit dem Wrapper neu erstellen:

```bash
./scripts/compose.sh config --quiet
./scripts/compose.sh build api
./scripts/compose.sh run --rm migrate
./scripts/compose.sh up -d --no-deps api
./scripts/compose.sh exec -T api \
  node -e "fetch('http://127.0.0.1:3000/ready').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
```

### 3.5 OIDC-Konto verknüpfen

OIDC erstellt nie automatisch anhand von E-Mail, Anzeigename oder Benutzername
ein Lunara-Konto. Zwei sichere Wege sind vorgesehen:

1. Mit dem bestehenden Lunara-Konto anmelden und in den Einstellungen nach
   erneuter Eingabe des aktuellen Passworts „Authentik verknüpfen“ wählen.
2. Eine bestehende Identität administrativ mit exaktem Lunara-UUID, exakt
   konfiguriertem Issuer, exaktem Authentik-Subject und dem von der CLI
   erwarteten Bestätigungsfingerprint verknüpfen.

Die zweite Variante ist eine gezielte Migration. `--disable-password true`
setzt nur den Passwort-Hash dieses Kontos auf `null`, widerruft dessen alte
Sitzungen und lässt Perioden, Logs, Freigaben, Rolle und Einstellungen
unverändert. Vorher Datenbankbackup und read-only Datensatzzählung anlegen.

## 4. Öffentliche Verifikation

Unabhängig vom Modus:

```bash
curl --fail --silent --show-error \
  https://lunara.example.com/api/v1/meta
```

Die Antwort muss `version`, `apiVersion`, `setupRequired`,
`registrationEnabled`, `authenticationMethods` und eine aktuelle `serverTime`
enthalten. Sie trägt `Cache-Control: no-store`.

Bei aktiviertem App Link:

```bash
curl --fail --silent --show-error \
  https://publisher-callback.example.com/.well-known/assetlinks.json
```

Die Antwort darf nur Package-ID, Relation und öffentliche Zertifikats-
Fingerprints enthalten. Fehlt eine valide App-Link-Konfiguration, antwortet
die API absichtlich mit 404.

Im Authentik-Modus zusätzlich:

```bash
sudo /docker/lunara/scripts/configure-authentik-oidc.sh --check
sudo /docker/lunara/scripts/verify-gateway.sh
```

`verify-gateway.sh` setzt den kompatiblen technischen Credential-Vertrag und
gegebenenfalls aktivierte OpenAPI-Dokumentation voraus. Es liest Credentials
nur aus einer eigenen `0600`-Datei und gibt sie nicht aus. Zusätzlich den
echten OIDC-Login, die App-Link-Rückkehr und beide erlaubten Personen auf einem
Testgerät prüfen.

## 5. Bootstrap, Registrierung und Partner

1. Die leere Instanz meldet über Meta `setupRequired: true`.
2. Das erste `TRACKER`-Konto wird mit `BOOTSTRAP_TOKEN` angelegt.
3. Der Server speichert den Bootstrap-Abschluss dauerhaft in PostgreSQL.
4. Danach Token aus der aktiven Konfiguration entfernen und API neu erstellen.
5. Mit `REGISTRATION_ENABLED=true` darf jede weitere Tracker-Registrierung eine
   neue isolierte Beziehung beginnen.
6. Ein `PARTNER` tritt ausschließlich mit einem einmal verwendbaren,
   widerrufbaren Einladungscode und eigenem Passwort bei.

Das Löschen aller Konten öffnet Bootstrap nicht erneut. „Household“ bleibt nur
die interne Autorisierungsgrenze; es ist kein notwendiges Eingabefeld.

## 6. Backup und Restore

```bash
sudo LUNARA_ENV_FILE=/docker/lunara/.env \
  /docker/lunara/scripts/backup.sh
```

Das Archiv enthält PostgreSQL-Custom-Dump, Prüfsummen und ausschließlich
nicht sensible Konfigurationswerte. `APP_MASTER_KEY`, Datenbankpasswort,
Signing-Key und Token sind absichtlich nicht enthalten. Dump und Master-Key
getrennt verschlüsselt sichern und Restore regelmäßig in einer isolierten
Testinstanz prüfen.

Ein produktiver Restore ersetzt Daten und ist daher nur nach bewusstem
Wartungsentscheid zulässig:

```bash
sudo LUNARA_ENV_FILE=/docker/lunara/.env \
  /docker/lunara/scripts/restore.sh \
  --confirm-replace-lunara-database \
  /var/backups/lunara/lunara-YYYYMMDDTHHMMSSZ-XXXXXXXX.tar.gz
```

`backup.sh`, `update.sh`, `restore.sh` und `lunara-admin.sh` delegieren ihre
Compose-Aufrufe ebenfalls an `scripts/compose.sh`. Dadurch bleiben der Direct-
Override beziehungsweise die getrennte `authentik-oidc.env` auch bei
Wartungsoperationen aktiv. Der Restore bleibt dennoch destruktiv und muss vor
produktiver Nutzung in einer isolierten Kopie getestet werden.

## 7. Updates und Migrationen

Der standardisierte modusbewusste Ablauf ist:

```bash
sudo LUNARA_ENV_FILE=/docker/lunara/.env ./scripts/update.sh
```

Das Skript hält eine Wartungssperre, erstellt zuerst ein Backup, baut das Image,
führt Migrationen aus, ersetzt ausschließlich die API und wartet auf
Readiness. Für Diagnose und Nachprüfung weiterhin `scripts/compose.sh ps` und
`scripts/compose.sh logs` verwenden.

Migrationen sind vorwärtsgerichtet. Acht Migrationen gehören zu 1.1.0. Vor
jeder Migration Dump validieren, danach `/ready`, Meta-Version, relevante
Logs, OIDC/App Link und einen bekannten verschlüsselten Testdatensatz prüfen.
Ein Rollback des Images nach bereits angewandter Migration ist nur zulässig,
wenn die Migration ausdrücklich rückwärtskompatibel ist; sonst Restore in
einem geplanten Wartungsfenster.

## 8. Google Play und Upload-Key

Der Upload-Key liegt ausschließlich in einem serverlokalen Keystore. Gradle
liest ihn nur über vier Umgebungsvariablen:

```bash
export LUNARA_UPLOAD_KEYSTORE=/sicherer/pfad/lunara-upload.p12
export LUNARA_UPLOAD_KEYSTORE_PASSWORD='<nicht ausgeben>'
export LUNARA_UPLOAD_KEY_ALIAS='<alias>'
export LUNARA_UPLOAD_KEY_PASSWORD='<nicht ausgeben>'
cd apps/android
./gradlew bundleProductionRelease
```

Fehlt eine Variable, bricht die Konfiguration ab; fehlen alle vier, entsteht
bewusst ein unsigniertes Release. Der Keystore liegt nicht in Git. Bei Play
App Signing signiert der Upload-Key nur das hochgeladene AAB; Google signiert
die ausgelieferten APKs mit dem getrennten App-Signing-Key. Dessen
Zertifikatsfingerprint muss anschließend in `assetlinks.json` übernommen und
der App Link erneut verifiziert werden. Die vollständige Store-Checkliste steht
in `docs/GOOGLE_PLAY_RELEASE.md`.

## 9. Health und Betrieb

Intern im Container:

- `/health` prüft den Prozess;
- `/ready` prüft zusätzlich PostgreSQL;
- `/api/v1/meta` beschreibt Version und kompatible Authmethoden.

Container-Sollzustand: API als UID/GID `10001:10001`, read-only Root-
Dateisystem, `no-new-privileges`, keine Linux-Capabilities, begrenzte Logs,
PostgreSQL ohne Host-Port und nur ein bewusst gewählter öffentlicher Router.
Nach Änderungen immer Health, RestartCount und relevante Logs prüfen; fremde
Hostdienste oder Portainer-verwaltete Stacks gehören nicht zum Lunara-Update.
