# Lunara 1.3.2 betreiben

Diese Anleitung richtet sich an Betreiber einer eigenen Instanz.
Die bebilderte [App-Einrichtung](docs/SETUP.md) beschreibt die Schritte auf
den Geräten.

Referenz: Debian 12, Docker Engine mit Compose Plugin und Traefik 3.
Lunara bringt weder einen Reverse Proxy noch Authentik mit. PostgreSQL und
die API veröffentlichen im mitgelieferten Compose keine Host-Ports;
HTTPS endet am bereits vorhandenen Proxy.

## 1. Betriebsart und Voraussetzungen

| Modus | Öffentlicher Weg | Persönliche Anmeldung |
| --- | --- | --- |
| `direct` | Traefik → Lunara | Lunara-Benutzername und -Passwort |
| `authentik` | Reverse Proxy → Authentik-Proxy → Lunara | OIDC; optional bewusst weiterbetriebene Lunara-Passwortanmeldung |

Vor dem Start werden benötigt:

- eine kontrollierte Domain, beispielsweise `lunara.example.com`, mit
  gültigem HTTPS-Zertifikat;
- ein vorhandener Reverse Proxy und dessen externes Docker-Netz;
- ausreichend Speicherplatz für Images und PostgreSQL sowie ein Backupziel;
- Zugriff auf den Quellcode und die passende signierte Release-APK.

Der Stack baut **lokal** das Image `lunara-api:1.3.2`.
Ein veröffentlichtes GHCR-Image wird nicht vorausgesetzt.
Die API liefert auch die Web-App und die APK aus. Die APK wird beim Build
in das Image kopiert, nicht über einen zusätzlichen Host-Mount eingebunden.

Die folgenden Start-/Update-Befehle gelten für eine eigenständig
Compose-verwaltete Installation. Wird der Stack durch Portainer verwaltet,
müssen Konfiguration, Migration und Redeploy über den bestehenden
Verwaltungsweg koordiniert werden. Nicht daneben per `compose up` einen
zweiten Besitzer erzeugen oder fremde Stacks verändern.

## 2. Quellcode und Android-Artefakt

In einem eigenen Projektverzeichnis:

```bash
git clone https://github.com/eliasbruno124-dev/Lunara.git
cd Lunara
```

Verwende den Quellstand und die APK desselben Releases. Das Repository kann
Zugriffsberechtigungen voraussetzen; keine Tokens in die Clone-URL schreiben.

Der Docker-Build benötigt `artifacts/lunara-release.apk`. Falls sie im
Quellpaket nicht enthalten ist, lade die signierte APK und `SHA256SUMS` aus
dem entsprechenden [GitHub-Release](https://github.com/eliasbruno124-dev/Lunara/releases)
und lege sie unter `artifacts/` ab. Anschließend:

```bash
test -s artifacts/lunara-release.apk
(cd artifacts && sha256sum --check --ignore-missing SHA256SUMS)
```

Kein Debug- oder unsigniertes APK als Nutzer-Download einsetzen.
Für eigene Android-Builds gelten zusätzlich die
[Signierungs- und App-Link-Vorgaben](docs/GOOGLE_PLAY_RELEASE.md).
Private Schlüssel und Keystores gehören nicht in den Buildkontext.

## 3. Konfiguration und Secrets

Nur für eine neue Installation ohne vorhandene `.env`:

```bash
./scripts/generate-secrets.sh "$(pwd)/.env"
chmod 0600 .env
```

Der Generator verweigert ein bereits vorhandenes Ziel. Er ersetzt keine
vorhandenen Schlüssel. Öffne die Datei danach **lokal** im Editor und setze
die öffentlichen Einstellungen passend zu deinem Server:

```dotenv
APP_VERSION=1.3.2
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

Proxy-Netz, EntryPoint und Zertifikatsresolver müssen zu deinem vorhandenen
Traefik passen; die Namen oben sind Beispiele. Im Authentik-Modus wird der
Direct-Override nicht geladen.

Der Generator erstellt zufällige Werte für `POSTGRES_PASSWORD`,
`APP_MASTER_KEY`, `AUTH_SIGNING_KEY`, `REFRESH_TOKEN_PEPPER` und
`BOOTSTRAP_TOKEN`. Niemals Beispielwerte aus `.env.example` als echte
Secrets verwenden. Die Datei muss nur für den Betreiber lesbar sein und
bleibt außerhalb von Git, Screenshots, Tickets und Chats.

`APP_MASTER_KEY` besteht aus exakt 32 zufälligen Bytes in kanonischem Base64.
Sichere ihn getrennt in einem verschlüsselten Offline-Passwortspeicher und
behalte ihn bei Updates bei. Ohne denselben Schlüssel lassen sich
verschlüsselte Datenbankfelder aus einem Backup nicht lesen.

Prüfe das konfigurierte Proxy-Netz, beispielsweise:

```bash
docker network inspect proxy >/dev/null
```

Falls es fehlt, richte es bewusst zusammen mit deinem Reverse Proxy ein;
der Lunara-Wrapper erstellt kein externes Proxy-Netz. PostgreSQL bleibt
ausschließlich im internen Netz `lunara_internal`.

## 4. Direct-Modus starten

Der Wrapper [scripts/compose.sh](scripts/compose.sh) liest `GATEWAY_MODE`
aus `.env`. Im Direct-Modus ergänzt er
[infra/docker/docker-compose.direct.yml](infra/docker/docker-compose.direct.yml).

```bash
./scripts/compose.sh config --quiet
./scripts/compose.sh build api
./scripts/compose.sh up -d
./scripts/compose.sh ps --all
./scripts/compose.sh logs --tail=100 migrate api
```

`config --quiet` prüft die Konfiguration, ohne expandierte Secrets
auszugeben. Logs nur lokal prüfen und bei Weitergabe gezielt redigieren.
Der Migrationsdienst muss erfolgreich enden, PostgreSQL und API müssen
gesund sein. Für die Readiness-Prüfung:

```bash
./scripts/compose.sh exec -T api \
  node -e "fetch('http://127.0.0.1:3000/ready').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
curl --fail --silent --show-error https://lunara.example.com/api/v1/meta
```

Meta muss die erwartete Version, `apiVersion`, `setupRequired`,
`authenticationMethods` und `serverTime` enthalten.
Die Antwort ist öffentlich, enthält keine Gesundheitsdaten und wird nicht
gecacht. Die API-Dokumentation bleibt standardmäßig deaktiviert.

Eine alternative dateibasierte Traefik-Konfiguration liegt unter
[infra/traefik/dynamic-direct.example.yml](infra/traefik/dynamic-direct.example.yml).
Nicht zusätzlich zum Compose-Router denselben Host doppelt routen.

## 5. Erstes Konto und Partner

1. Auf einem leeren Server meldet Meta `setupRequired: true`.
2. In Android die Serveradresse verbinden und **Lunara erstmals einrichten**
   wählen. Das erste Trackerkonto mit dem lokal verwahrten
   `BOOTSTRAP_TOKEN` erstellen; nur dessen Wert in das dafür vorgesehene
   App-Feld eingeben.
3. Nach erfolgreicher Einrichtung den Bootstrap-Token aus der aktiven
   Serverkonfiguration entfernen und die API über den zuständigen
   Verwaltungsweg neu erstellen.
4. Weitere eigene Tracker-Beziehungen sind nur bei
   `REGISTRATION_ENABLED=true` möglich. Für eine private Paarinstanz muss
   die öffentliche Registrierung nicht aktiviert bleiben.
5. Die trackende Person erstellt eine Einladung. Der Partner meldet sich
   mit einem eigenen Konto an und löst die Einladung ausdrücklich ein.
   Bei OIDC kann die Einladung an die geprüfte Authentik-Identität gebunden
   werden, ohne ein zweites Lunara-Passwort anzulegen.
6. Datenschutzfreigaben werden von der trackenden Person festgelegt –
   tatsächliche Periodendaten, erwarteter Zeitraum und fruchtbares
   Zeitfenster sind getrennte Einstellungen.

Bootstrap wird in PostgreSQL dauerhaft als abgeschlossen gespeichert.
Das Löschen aller Konten öffnet ihn nicht erneut.
Ein bestehendes Konto mit Gesundheitsdaten wird nicht stillschweigend in
eine andere Beziehung verschoben.

## 6. Optional: Authentik

### Gateway und persönliche Anmeldung getrennt halten

Der Authentik-Proxy schützt den äußeren Zugriff. Lunara prüft zusätzlich
Konto, Rolle und Freigaben. Für moderne Clients sind die Header getrennt:

```text
Authorization: Bearer <Authentik-Access-Token>
X-Lunara-Authorization: Bearer <Lunara-Access-Token>
```

Diese Platzhalter sind keine Konfigurationswerte; echte Tokens niemals
in die Dokumentation oder eine APK schreiben. Der technische Basic-Zugang
bleibt eine kontrollierte Altclient-Kompatibilität, nicht der neue Standard.

Der bestehende Proxy-Vertrag erlaubt genau diese anonymen Pfade:

- `/api/v1/meta`
- `/.well-known/assetlinks.json`
- `/invite`

Die exakte Ausnahme lautet
`^/(?:api/v1/meta|\.well-known/assetlinks\.json|invite)$`.
Keine breite Ausnahme für `/`, `/api`, Unterpfade oder den APK-Download
einrichten. Einladungen tragen den Code im Fragment
`https://lunara.example.com/invite#invite=CODE`; der Code soll nicht
Teil einer Provider-Redirect-URL werden.

### Android: API-Domain ist nicht der Callback-Host

Die Production-APK verwendet einen beim Build festgelegten HTTPS-App-Link:

```text
https://publisher-callback.example.com/auth/android-callback
```

Der Host ist hier nur ein Beispiel. Eine bereits veröffentlichte APK behält
ihren kompilierten Publisher-Host auch dann, wenn deine API unter einer
anderen Domain läuft. Im OIDC-Provider muss exakt dieser Redirect
registriert sein. Den tatsächlichen Host des verwendeten Builds vor der
Einrichtung in dessen Release-/Buildkonfiguration prüfen; die
[Gradle-Konfiguration](apps/android/app/build.gradle.kts) enthält den
Quellcode-Standard und die möglichen Build-Overrides.

Auf **diesem Callback-Host** muss `/.well-known/assetlinks.json` die
Paket-ID und den SHA-256-Fingerprint des installierten App-Zertifikats
enthalten. Eine Datei nur auf einer anderen API-Domain reicht nicht.

Bei eigener Distribution:

- `LUNARA_APP_LINK_HOST` beim Build auf einen kontrollierten Host setzen;
- dort die passende `assetlinks.json` bereitstellen;
- exakt den HTTPS-Android-Redirect und
  `https://lunara.example.com/auth/callback` für die Web-App registrieren.

Für direkt verteilte APKs zählt das Release-Zertifikat. Bei Play App Signing
zählt das App-Signing-Zertifikat von Google, nicht nur der Upload-Key.
Production akzeptiert keine Custom-Scheme-Redirects. Solche Redirects sind
ausschließlich für den `local`-Flavor mit einem getrennten
Entwicklungsprovider vorgesehen. Fingerprints sind öffentlich; der private
Signaturschlüssel ist es nicht.

### Provisionierung nur für den passenden Bestandsvertrag

[scripts/configure-authentik-oidc.sh](scripts/configure-authentik-oidc.sh)
ist kein allgemeiner Authentik-Installer. Es prüft einen vorhandenen,
konkreten Lunara-Proxy-Vertrag und Authentik 2026.5.3.
Abweichungen müssen zuerst fachlich geprüft werden; die Vorprüfung darf
nicht durch Entfernen ihrer Sicherheitskontrollen umgangen werden.

Beispiel einer **read-only Vorprüfung**, nach Anpassung aller öffentlichen
Namen und des tatsächlichen APK-Callback-Hosts:

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

Alle genannten Nutzer müssen bereits als aktive interne Authentik-Konten
existieren. Erst nach erfolgreicher Vorprüfung, geprüftem Zertifikat und
bewusster Freigabe der Änderung den Provisioner ohne `--check` ausführen.
Dabei zusätzlich `LUNARA_ANDROID_APP_PACKAGE=de.lunara.app` und
`LUNARA_ANDROID_CERT_SHA256` mit dem geprüften öffentlichen Fingerprint
angeben. Das Skript erstellt vorher einen validierten Datenbankdump.

Es konfiguriert Authorization Code mit PKCE, Refresh Tokens,
`openid offline_access`, RS256, explizite Zustimmung und die zugelassene
Gruppe. Öffentliche OIDC-Konfigurationswerte landen lokal in der
`authentik-oidc.env`. Mit `GATEWAY_MODE=authentik` lädt der Wrapper diese
Datei zusätzlich; anschließend API über den zuständigen Verwaltungsweg
neu erstellen und den vollständigen Browserlogin einschließlich Rückkehr
in die signierte Android-App prüfen.

Ein bestehendes Lunara-Konto wird nicht anhand gleicher E-Mail oder
gleichem Namen automatisch verknüpft. Die Verknüpfung verlangt eine
erneute Passwortprüfung oder eine gezielte Admin-Migration mit geprüftem
Issuer und Subject. Neue OIDC-Trackerprofile erfordern die ausdrückliche
Registrierungswahl und aktivierte Registrierung; Partner benötigen eine
Einladung.

### Sitzungsdauer ehrlich einplanen

Der Provisioner setzt 15 Minuten für Access-Tokens und 365 Tage für
erneuerbare Authentik-Refresh-Tokens. Bereits ausgegebene Zugangsdaten
bekommen durch eine spätere Konfigurationsänderung nicht rückwirkend eine
neue Laufzeit.

Lunara kann eine nicht widerrufene OIDC-Gerätesitzung nach längerer
Inaktivität erneuern, wenn ein frischer Provider-Nachweis exakt derselben
verknüpften Identität vorliegt. Passwortsitzungen behalten ihr begrenztes
Erneuerungsfenster, standardmäßig 30 Tage. Provider-Widerruf, tatsächlich
abgelaufene Provider-Zugangsdaten, Kontolöschung und verlorene
Geräteschlüssel sind keine Netzfehler und werden nicht umgangen.

## 7. APK-Download prüfen

Die beim Imagebau gebündelte APK ist über den stabilen Pfad erreichbar:

```text
https://lunara.example.com/api/v1/downloads/lunara.apk
```

Im Browser: **Einstellungen → App & Verbindung → Android-App herunterladen
(.apk)**. Eine installierte PWA bietet denselben Link. Im Authentik-Modus
muss eine gültige Gateway-Anmeldung bestehen; der Download ist keine
öffentliche Proxy-Ausnahme. Der Browser lädt die Datei direkt herunter,
ohne sie als großes JavaScript-Blob zwischenzuspeichern.

Nach jedem APK-Update das API-Image mit dem neuen Artefakt bauen und
ausrollen. Ein bloßes Austauschen einer Hostdatei ändert die bereits im
Image enthaltene APK nicht. Zusätzlich APK-Version, Signatur und
Release-Prüfsumme prüfen. GitHub-Releases bleiben ein eigener Downloadweg.

## 8. Backup und Updates

Die folgenden Pfade setzen beispielhaft eine Installation unter
`/docker/lunara` voraus. Ersetze sie durch dein tatsächliches
Projektverzeichnis und führe `./scripts/update.sh` aus diesem Verzeichnis aus.

Vor Konfigurationsänderungen und Migrationen:

```bash
sudo LUNARA_ENV_FILE=/docker/lunara/.env \
  /docker/lunara/scripts/backup.sh
```

Das Archiv enthält einen PostgreSQL-Custom-Dump, Prüfsummen, die
Compose-Datei und ausgewählte öffentliche Konfigurationswerte.
Es ist **nicht automatisch verschlüsselt**. Gesundheitsdaten und
Sicherungen nur auf einem geschützten, verschlüsselten Backupziel lagern.
Keine Inline-Secrets in Compose schreiben: Die Compose-Datei wird mitgesichert.
Master-Key und andere Secrets werden nicht aus `.env` übernommen und
müssen separat sicher verwahrt werden.

Restore regelmäßig in einer isolierten Testinstanz üben.
[scripts/restore.sh](scripts/restore.sh) ersetzt die Datenbank und verlangt
`--confirm-replace-lunara-database`. Ein produktiver Restore ist eine
bewusste, destruktive Wartungsmaßnahme, kein normaler Update-Schritt.

Für eine ausschließlich Compose-verwaltete Installation, nach Übernahme
des geprüften Quellstands und der passenden APK:

```bash
sudo LUNARA_ENV_FILE=/docker/lunara/.env ./scripts/update.sh
```

Das Skript sperrt parallele Wartungsoperationen, erstellt ein Backup,
baut das Image, führt Migrationen aus, ersetzt die API und prüft Readiness.
Es führt keinen Git-Pull und keinen automatischen APK-Release-Download aus.
Für Portainer den entsprechenden bestehenden Updateweg verwenden.

Nach jeder Änderung Readiness, Meta-Version, Containerstatus und relevante
Logs prüfen. Bei Authentik zusätzlich Login und App-Link-Rückkehr testen.
Ein älteres Image ist nach einer Datenbankmigration nicht automatisch ein
sicherer Rollback; dafür muss die Migration rückwärtskompatibel sein oder
ein geplanter Restore erfolgen.

## 9. Betrieb und weiterführende Unterlagen

- `/health`: Prozessprüfung.
- `/ready`: zusätzlich Datenbankprüfung.
- `/api/v1/meta`: öffentliche Version und unterstützte Anmeldemethoden.

Die API läuft als UID/GID `10001:10001` mit read-only Root-Dateisystem,
`no-new-privileges` und ohne Linux-Capabilities.
Nicht „zur Fehlersuche“ dauerhaft privilegieren oder die Datenbank ins
Internet veröffentlichen.

[Sicherheit](SECURITY.md) · [Datenschutz](PRIVACY.md) ·
[Android-Signierung und Play](docs/GOOGLE_PLAY_RELEASE.md) ·
[Code-Navigation](docs/CODE_GUIDE.md)
