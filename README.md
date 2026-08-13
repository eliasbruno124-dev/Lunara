# Lunara

Lunara ist eine private, selbst gehostete Zyklus- und Perioden-App für
zyklustrackende Personen und ihre Partnerpersonen. Beide verwenden dieselbe
native Android-App mit eigenen Konten, unterschiedlichen Rollenansichten und
serverseitig erzwungenen Freigaben. Eine Instanz kann mehrere voneinander
isolierte Beziehungen aufnehmen.

Lunara enthält keine Werbung, Telemetrie, Analyse-SDKs, Firebase- oder
Google-Play-Services-Pflicht für Kernfunktionen. Die Anwendung ist kein
Medizinprodukt, stellt keine Diagnose, bestätigt keine Ovulation und darf nicht
als Verhütungsmethode verwendet werden.

Der Quellcode liegt im privaten Repository
[`eliasbruno124-dev/Lunara`](https://github.com/eliasbruno124-dev/Lunara). Der
Produktionsstack läuft getrennt unter `/docker/lunara`; Authentik und Pangolin
bilden den einzigen öffentlichen HTTPS-Eingang. Die gesamte Domain ist durch
Authentik geschützt, einschließlich `/api/v1`, `/health`, `/ready` und der
OpenAPI-Dokumentation.

## Umgesetzter Kern

- NestJS-/Prisma-API mit PostgreSQL, Argon2id, kurzen Access-Tokens,
  rotierenden gehashten Refresh-Tokens und getrennten Gerätesitzungen
- sicherer Erstserver-Bootstrap mit dauerhaftem Datenbankstatus sowie optional
  öffentliche Tracker-Registrierung; pro interner Beziehungsgrenze gibt es
  genau einmal `TRACKER` und einmal `PARTNER`
- permanente, einmal verwendbare und jederzeit widerrufbare Partner-
  Einladungen; der QR-Code enthält ausschließlich den Einladungscode
- Perioden, Tageslogs, vordefinierte und eigene Symptome, nachvollziehbare
  Bereichsprognosen, granulare Freigaben und Check-ins
- 71 sorgfältig formulierte Partner-Tipps auf Deutsch und Englisch; Auswahl
  anhand freigegebener Daten, geschätzter Phase, Care Preferences und Feedback
- optionale Schätzung des fruchtbaren Fensters nur nach ausdrücklichem Opt-in,
  mit mindestens drei vollständigen Zyklen und ständig sichtbarem
  Sicherheitshinweis; niemals als Ovulationsbestätigung oder Verhütung
- verschlüsselte Freitextnotizen, Soft Deletes, Cursor-Sync, optimistische
  Versionskonflikte und idempotente Offline-Mutationen
- automatische Synchronisierung nach Anmeldung und lokalen Änderungen, beim
  Öffnen, regelmäßig im Vordergrund und sparsam über WorkManager; ein manueller
  Sync bleibt zusätzlich verfügbar
- JSON-/CSV-Export und ein per AES-256-GCM passwortgeschütztes
  `.lunarabackup`-JSON-Format
- native Compose-App 1.0.5 mit Deutsch/Englisch, Room/SQLCipher, Keystore,
  rollenabhängigen Ansichten, Kalender, Verlauf, vollständigen Freigaben,
  Care-Preferences-Einstellungen, Passwortwechsel und `FLAG_SECURE`
- nachträgliche Korrektur und Löschung eines Periodenstarts über dieselbe
  offlinefähige, automatisch synchronisierte Mutation Queue
- gehärtete Multi-Stage-Container, PostgreSQL 18, Backup-/Restore-/Update- und
  Admin-Skripte sowie additive Authentik-/Pangolin-Konfiguration
- versionierte OpenAPI-Spezifikation und daraus erzeugter Kotlin-Referenzclient;
  die Android-App nutzt zur Laufzeit ihre typisierten Retrofit-Schnittstellen

Der tatsächlich verifizierte Build- und Live-Stand, ausgeführte Befehle,
Prüfsummen und bewusst offene Erweiterungen stehen in
[`FINAL_REPORT.md`](FINAL_REPORT.md), [`TESTING.md`](TESTING.md) und
[`PROGRESS.md`](PROGRESS.md). Diese Dateien sind die maßgebliche Quelle für
Erfolgsaussagen; ein vorhandener Dateiname allein gilt nicht als Buildnachweis.

## Verbindung und Authentifizierung

Das Serverfeld ist beim ersten Start von Android 1.0.5 absichtlich leer. Für
die erste Verbindung wird die vom Betreiber separat und sicher übermittelte
Header-Auth-Adresse einmalig eingegeben. Ihr Schema ist HTTPS, gefolgt von
Gateway-Benutzer, Doppelpunkt, App-Passwort, `@` und Server-Host. Die konkreten
Bestandteile liefert ausschließlich der Betreiber über einen sicheren Kanal.

Sonderzeichen in Benutzername oder App-Passwort müssen URL-kodiert sein. Die
App akzeptiert eingebettete Gateway-Zugangsdaten ausschließlich über HTTPS,
trennt sie sofort von der Basisadresse und legt sie Keystore-gestützt ab. In
der sichtbaren und dauerhaft gespeicherten Serveradresse bleibt danach nur:

```text
https://<server-host>
```

Es existiert genau ein gemeinsamer, nicht ablaufender technischer Authentik-
App-Zugang für alle Lunara-App-Installationen. Er authentifiziert nur den
äußeren Proxyzugang mit `Authorization: Basic …`. Persönliche Lunara-Konten,
Rollen und Datensicht bleiben getrennt: Nach dem Login sendet die App den
persönlichen Lunara-Access-Token als
`X-Lunara-Authorization: Bearer …`. Der äußere Header ist für jeden öffentlichen
Domainaufruf erforderlich; persönliche geschützte API-Aufrufe benötigen danach
zusätzlich das Lunara-Token.

Der gemeinsame Zugang ist ein Infrastrukturgeheimnis. Seine URL darf weder in
Git, Dokumentation, Chat, Cloudflare, Screenshots noch einen QR-Code gelangen.
Server- und Einladungs-QR-Codes enthalten keine Passwörter. Wird der gemeinsame
App-Zugang manuell in Authentik rotiert und neu ausgerollt, müssen alle
autorisierten App-Installationen einmalig die neue Header-Auth-Adresse
erhalten; die persönlichen Lunara-Passwörter bleiben davon unberührt.
Menschliche Authentik-Konten sind nicht für Lunara freigeschaltet.

Neue Lunara-Kontopasswörter müssen 8 bis 128 Unicode-Zeichen lang sein. Es
gibt keine vorgeschriebenen Großbuchstaben, Ziffern oder Sonderzeichen; eine
lange, nur für Lunara verwendete Passphrase bleibt empfehlenswert. Bei Login,
Kontolöschung und der Eingabe des aktuellen Passworts akzeptiert die API aus
Kompatibilitätsgründen bestehende Passwörter mit 1 bis 128 Zeichen. Neue oder
geänderte Passwörter müssen immer die aktuelle Mindestlänge erfüllen. Die
Passphrase für einen verschlüsselten Export ist davon getrennt und bleibt auf
12 bis 128 Zeichen begrenzt.

Der Bootstrap ist ausschließlich für das erste `TRACKER`-Konto einer noch nie
eingerichteten Instanz bestimmt. Sein erfolgreicher Abschluss wird dauerhaft
in PostgreSQL gespeichert. Danach kann der `BOOTSTRAP_TOKEN` entfernt werden.
Bei `REGISTRATION_ENABLED=true` können weitere Tracker jeweils eine neue,
unsichtbar abgegrenzte Beziehung beginnen. „Haushalt“ ist kein sichtbares
Produktkonzept; die interne Datenstruktur dient nur als Mandanten- und
Autorisierungsgrenze.

## APK

Die tatsächlich erzeugte, installierbare Production-Debug-APK liegt hier:

```text
artifacts/lunara-debug.apk
```

Im privaten GitHub-Repository kann sie nach Anmeldung direkt über den
[Release Lunara 1.0.5](https://github.com/eliasbruno124-dev/Lunara/releases/tag/v1.0.5)
heruntergeladen werden.

Zusätzlich wurde eine noch selbst zu signierende Release-APK erzeugt:

```text
artifacts/lunara-release-unsigned.apk
```

Vor Installation immer Version, Signatur und die zugehörige Prüfsumme aus
`FINAL_REPORT.md` beziehungsweise `artifacts/SHA256SUMS` vergleichen:

```bash
cd artifacts
sha256sum -c SHA256SUMS
```

Der Debugschlüssel liegt lokal und Git-ausgeschlossen. Ein eigener
Release-Build ist in [`docs/ANDROID_SIGNING.md`](docs/ANDROID_SIGNING.md)
beschrieben; private Signierschlüssel gehören nicht ins Repository.

## Entwicklung und Prüfung

Der E2E-Befehl startet ausschließlich eine zufällig benannte, kurzlebige
PostgreSQL-Testumgebung:

```bash
./scripts/e2e.sh
```

Weitere Befehle:

```bash
npm ci
npm run lint
npm test
npm run build
npm run prisma:validate
npm run openapi:check

cd apps/android
./gradlew testLocalDebugUnitTest
./gradlew lintLocalDebug lintProductionDebug
./gradlew compileProductionDebugAndroidTestKotlin
./gradlew assembleProductionDebug
```

Die Produktionskonfiguration veröffentlicht weder API- noch Datenbankports.
Verwende für einen produktiven Start ausschließlich
[`DEPLOYMENT.md`](DEPLOYMENT.md), nicht den Test-Stack.

## Projektstruktur

```text
apps/android/             native Android-App
services/api/             NestJS-/Prisma-API
packages/openapi-client/  OpenAPI-Spezifikation und generierter Kotlin-Client
infra/docker/             Containerfiles und isolierte Testkonfiguration
infra/traefik/            Proxy-Beispiele
scripts/                  Betrieb, Backup, Restore, Update, Gateway und E2E
docs/                     ergänzende Dokumentation und Tippkatalog
artifacts/                erfolgreich erzeugte installierbare APKs
```

## Weiterführende Dokumentation

- [`DEPLOYMENT.md`](DEPLOYMENT.md): Debian 12, Docker, Authentik, Pangolin,
  Cloudflare, Bootstrap, Backup und Updates
- [`SECURITY.md`](SECURITY.md): Bedrohungsmodell, Schlüssel, Gateway-Credential,
  Gerätesicherheit und bekannte Grenzen
- [`PRIVACY.md`](PRIVACY.md): gespeicherte und freigegebene Daten
- [`ALGORITHM.md`](ALGORITHM.md): deterministische Prognose
- [`TESTING.md`](TESTING.md): ausgeführte und noch nicht ausführbare Tests

## Lizenz

Nicht zur öffentlichen Weitergabe lizenziert. Alle Rechte verbleiben beim
Repository-Eigentümer.
