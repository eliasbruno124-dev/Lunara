# Lunara

Lunara ist eine private Zyklus- und Perioden-App mit drei klar getrennten
Betriebsarten:

- **Solo:** auf Android mit SQLCipher/Keystore oder in der PWA mit einem
  passphrasenverschlüsselten lokalen Vault, jeweils ohne Konto oder Server;
- **Self-hosted als Paar:** Android und installierbare Web-App nutzen dieselbe
  eigene API, aber getrennte Konten mit den Rollen `TRACKER` und `PARTNER`;
- **Self-hosted mit Authentik:** optionaler persönlicher OIDC-Login vor derselben
  Lunara-API. Ohne Authentik bleibt Lunara vollständig funktionsfähig.

Lunara enthält keine Werbung, Telemetrie, Analyse-SDKs, Firebase- oder
Google-Play-Services-Pflicht. Die App ist kein Medizinprodukt, diagnostiziert
nichts, bestätigt keine Ovulation und darf nicht als Verhütungsmethode
verwendet werden.

## Release 1.1.0

Version 1.1.0 ergänzt den bisherigen Server-/Android-Kern um:

- einen echten lokalen Android-Solomodus mit SQLCipher, Android Keystore,
  lokaler Prognose, Kalender, Verlauf und lokalen Benachrichtigungen;
- ein portables, passphrasenverschlüsseltes Solo-Backup zum Gerätewechsel;
- eine installierbare PWA für moderne iOS-, Android- und Desktop-Browser mit
  Self-hosting- und passphrasengeschütztem lokalen Solomodus;
- optionalen Authentik-OIDC-Login über Authorization Code und PKCE S256 mit
  einem festen Android-verifizierten Publisher-App-Link, unabhängig von der
  gewählten Self-hosting-API-Domain;
- einen direkt über Traefik betreibbaren Modus ohne Authentik;
- einen wischbaren Monatskalender bis fünf Jahre in die Zukunft mit eindeutig
  unterscheidbaren tatsächlichen und geschätzten Periodentagen;
- den robusten, deterministischen Prognosealgorithmus `lunara-robust-v2`.

Der bestehende Kern bleibt erhalten: PostgreSQL/Prisma, Argon2id, kurze
Access-Tokens, rotierende gehashte Refresh-Tokens, Gerätesitzungen,
idempotenter Cursor-Sync, Soft Deletes, strikte Mandantenisolation, granulare
Partnerfreigaben, 71 deutsch/englische Partner-Tipps, Care Preferences,
Check-ins, lokale Erinnerungen sowie JSON-/CSV-Export.

## Solo nutzen und auf ein neues Handy übertragen

Beim ersten Android-Start kann **Nur auf diesem Gerät** gewählt werden. Dieser
Modus erzeugt kein Serverkonto und führt keine Netzwerk-Synchronisierung aus.
Die Room-Datenbank ist mit SQLCipher verschlüsselt; ihr Schlüssel ist an den
Android Keystore des Geräts gebunden.

Für einen Gerätewechsel wird unter **Einstellungen → Portables Backup** eine
`.lunara`-Datei erstellt. Sie enthält Profil, Einstellungen, Perioden und
Tageslogs und ist mit PBKDF2-HMAC-SHA256 und AES-256-GCM geschützt. Über den
Android-Systemdateidialog kann als Ziel auch ein installierter Anbieter wie
Google Drive, Nextcloud, USB-Speicher oder ein lokaler Ordner gewählt werden.
Lunara integriert dafür kein Google-SDK und erhält keinen allgemeinen Zugriff
auf das Cloudkonto.

Auf dem neuen Gerät wird erneut der Solomodus gewählt und die Datei über
**Backup importieren** geöffnet. Ein Import-Journal vollendet einen nach dem
Room-Commit unterbrochenen Einstellungsimport beim nächsten Start idempotent.
Die Passphrase wird nicht gespeichert und kann nicht wiederhergestellt werden.
Android-Cloudbackup und automatische Gerät-zu-Gerät-Übertragung sind bewusst
deaktiviert, damit kein unbrauchbarer lokaler Zustand ohne den nicht
exportierbaren Keystore-Schlüssel entsteht.

## Self-hosted Architektur

```text
Android / PWA
      |
      | HTTPS
      v
Traefik direkt  ODER  Traefik/Pangolin -> Authentik Proxy
      |
      v
NestJS API + ausgelieferte PWA
      |
      v
PostgreSQL 18 (nur internes Docker-Netz)
```

Eine Serverinstanz kann beliebig viele voneinander isolierte Beziehungen
aufnehmen. Intern bildet `Household` die UUID-basierte Autorisierungsgrenze;
dieses technische Modell wird in der Oberfläche nicht als „Haushalt“
präsentiert. Jede Beziehung besitzt in Version 1 genau einen `TRACKER` und
einen über einen einmal verwendbaren Einladungscode verbundenen `PARTNER`.

## Gateway- und Anmeldemodi

| Modus | Öffentlicher Weg | Persönliche Anmeldung |
| --- | --- | --- |
| `direct` | Traefik leitet direkt zur API/PWA | Lunara-Benutzername und -Passwort |
| `authentik` | Traefik/Pangolin leitet über den Authentik-Proxy | Authentik-OIDC oder weiterhin bewusst aktivierter Lunara-Passwortweg |

Das Serverfeld bleibt leer und erwartet nur eine credentialfreie HTTPS-Adresse,
beispielsweise:

```text
https://lunara.example.com
```

Im Authentik-Modus sind nur `/api/v1/meta` und
`/.well-known/assetlinks.json` anonym erreichbar. Meta enthält keine Konto-
oder Gesundheitsdaten und teilt dem Client mit, ob OIDC angeboten wird. Nach
dem Browserlogin sendet der Client auf exakt denselben Ursprung:

- außen `Authorization: Bearer <Authentik-Access-Token>` für den Proxy;
- innen `X-Lunara-Authorization: Bearer <Lunara-Access-Token>` für Konto,
  Rolle, Eigentum und Freigaben.

Die beiden Tokens sind nicht austauschbar. OIDC legt kein Lunara-Konto anhand
von E-Mail oder Benutzername automatisch an. Ein bestehendes Konto muss nach
erneuter Passwortprüfung in den Einstellungen oder gezielt über die Admin-CLI
mit exakt geprüftem Issuer und Subject verknüpft werden. Der frühere technische
Basic-Header bleibt nur als kontrollierte Kompatibilitätsmöglichkeit erhalten;
neue Installationen verwenden die normale Serveradresse und OIDC.

## Schnellstart ohne Authentik

Voraussetzungen sind Debian 12, Docker Engine, das Compose Plugin, Traefik 3,
ein externes Proxy-Netz und eine HTTPS-Domain.

```bash
cp .env.example .env
chmod 0600 .env
# Secrets und PUBLIC_BASE_URL/PUBLIC_HOST in .env setzen,
# dann GATEWAY_MODE=direct wählen.
./scripts/compose.sh config --quiet
./scripts/compose.sh build api
./scripts/compose.sh up -d
./scripts/compose.sh ps --all
```

Für Authentik gelten die zusätzlichen, fail-closed Schritte in
[`DEPLOYMENT.md`](DEPLOYMENT.md). Das Wrapper-Skript `scripts/compose.sh`
verhindert, dass versehentlich die falsche Compose-Kombination für den
gewählten Gateway-Modus verwendet wird.

## Entwicklung

```bash
npm ci
npm run prisma:generate
npm run prisma:validate
npm run lint
npm test
npm run build
npm run openapi:check
./scripts/e2e.sh

cd apps/android
./gradlew testLocalDebugUnitTest testProductionDebugUnitTest
./gradlew lintLocalDebug lintProductionDebug
./gradlew compileProductionDebugAndroidTestKotlin
./gradlew assembleProductionDebug assembleProductionRelease
./gradlew bundleProductionRelease
```

Die tatsächlich ausgeführte Prüfmatrix steht in [`TESTING.md`](TESTING.md)
und [`FINAL_REPORT.md`](FINAL_REPORT.md). Kompilierte Instrumentierung ist dort
ausdrücklich nicht als auf einem Gerät ausgeführter Test ausgewiesen.

## Android-Artefakte

Erfolgreich gebaute Release-1.1.0-Artefakte liegen unter `artifacts/`:

- `lunara-debug.apk` – Production-Debug/Testinstallation mit Debugsignatur und
  HTTPS-only; sie kann Passwort-/Headerauth testen, ihr Debugzertifikat erfüllt
  den Production-App-Link jedoch nicht und sie ist kein universeller OIDC-
  Testbuild;
- `lunara-release.apk` – empfohlene Nutzerinstallation, mit einem
  ausschließlich serverlokalen Upload-Key signiert und für den konfigurierten
  HTTPS App Link gebaut;
- `lunara-release-unsigned.apk` – bewusst unsignierte Release-APK;
- `lunara-play-upload.aab` – mit dem Upload-Key signiertes Bundle für Play;
- `lunara-release-unsigned.aab` – bewusst unsigniertes Referenz-Bundle.

Prüfsummen stehen ausschließlich in `artifacts/SHA256SUMS`. Keystore, Alias,
Passwörter und private Schlüssel liegen nicht im Repository. Für Google Play
ist zusätzlich Play App Signing vorgesehen; Details und noch offene
Publisher-/Policy-Schritte stehen in
[`docs/GOOGLE_PLAY_RELEASE.md`](docs/GOOGLE_PLAY_RELEASE.md).

## Projektstruktur

```text
apps/android/             native Kotlin-/Compose-App
apps/web/                 installierbare TypeScript-PWA
services/api/             NestJS-/Prisma-API und PWA-Auslieferung
packages/openapi-client/  OpenAPI-Snapshot und Kotlin-Referenzclient
infra/docker/             Container und Compose-Ergänzungen
infra/traefik/            direkte und Authentik-bezogene Beispiele
scripts/                  Betrieb, Backup, Restore, Update, Admin und E2E
docs/                     Release-, Store- und Produktdokumentation
artifacts/                tatsächlich gebaute Android-Artefakte
```

## Dokumentation

- [`DEPLOYMENT.md`](DEPLOYMENT.md) – Direct-/Authentik-Betrieb, Migrationen,
  Backup und Update
- [`SECURITY.md`](SECURITY.md) – Bedrohungsmodell, OIDC, Schlüssel und Grenzen
- [`PRIVACY.md`](PRIVACY.md) – Solo-, PWA- und Serverdaten
- [`ALGORITHM.md`](ALGORITHM.md) – exakte Prognoseberechnung
- [`TESTING.md`](TESTING.md) – reale Prüfungen und nicht ausgeführte Tests
- [`docs/GOOGLE_PLAY_RELEASE.md`](docs/GOOGLE_PLAY_RELEASE.md) – Signierung,
  Play App Signing, App Links und Einreichungscheckliste
- [`docs/STORE_LISTING_DE.md`](docs/STORE_LISTING_DE.md) und
  [`docs/STORE_LISTING_EN.md`](docs/STORE_LISTING_EN.md) – vorbereitete,
  vor Einreichung abzugleichende Storetexte
- [`docs/COMMERCIAL_READINESS.md`](docs/COMMERCIAL_READINESS.md) – Self-hosting
  und Vermarktungsgrenzen

## Lizenz

Derzeit ist keine Weitergabelizenz festgelegt. Alle Rechte verbleiben beim
Repository-Eigentümer. Vor einer öffentlichen oder kommerziellen Distribution
muss bewusst ein Lizenzmodell gewählt werden.
