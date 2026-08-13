# Lunara

Lunara ist eine selbst gehostete Zyklus- und Perioden-App für
zyklustrackende Personen und ihre jeweiligen Partnerpersonen. Alle verwenden
dieselbe native Android-App mit getrennten Konten und erhalten ausschließlich
die serverseitig erlaubte Rollenansicht. Eine Serverinstanz kann mehrere Paare
aufnehmen; Daten verschiedener Beziehungen bleiben strikt getrennt.

Die Anwendung enthält keine Werbung, Telemetrie, Analyse-SDKs, Firebase- oder
Google-Play-Services-Abhängigkeit für Kernfunktionen. Sie ist kein
Medizinprodukt, stellt keine Diagnose und darf nicht als Verhütungsmethode
verwendet werden.

Der Quellcode liegt im privaten Repository
[`eliasbruno124-dev/Lunara`](https://github.com/eliasbruno124-dev/Lunara). Der
Produktionsstack läuft getrennt unter `/docker/lunara`; Authentik und Pangolin
bilden den öffentlichen HTTPS-Eingang. Die Android-App verbindet sich ohne
Gateway-Zugangsdaten über `https://lunara.3li361.de`.

## Umgesetzter Kern

- NestJS-/Prisma-API mit PostgreSQL, Argon2id, kurzen Access-Tokens,
  rotierenden gehashten Refresh-Tokens und getrennten Gerätesitzungen
- sicherer Erstserver-Bootstrap mit dauerhaftem Datenbankstatus und optional
  öffentliche Tracker-Registrierung; der `BOOTSTRAP_TOKEN` kann nach dem ersten
  erfolgreichen Setup entfernt werden; pro intern abgegrenzter Beziehung gibt
  es genau einmal `TRACKER` und einmal `PARTNER`
- permanente, einmal verwendbare und jederzeit widerrufbare Partner-Einladungen;
  der QR-Code enthält ausschließlich den Einladungscode
- Perioden, Tageslogs, vordefinierte und eigene Symptome, Prognosen,
  Freigaben, Care Preferences, mindestens 50 zweisprachige Tipps und Check-ins
- verschlüsselte Freitextnotizen, Soft Deletes, Cursor-Sync, optimistische
  Versionskonflikte und idempotente Offline-Mutationen
- JSON-/CSV-Export und ein per AES-256-GCM passwortgeschütztes
  `.lunarabackup`-JSON-Format; kein ZIP-Container
- feldvalidierter JSON-Import mit Vorschau und Erkennung bereits vorhandener
  aktiver Perioden und Tageslogs
- native Compose-App mit deutschem/englischem Onboarding,
  normaler HTTPS-Serveradresse, SQLCipher/Keystore, Room, WorkManager,
  rollenabhängigen Ansichten, Basis-Tageslog, Kalender, Verlauf, Freigaben und
  `FLAG_SECURE`; automatische Vordergrund-/Hintergrundsynchronisierung und
  persönlicher Lunara-Passwortwechsel sind integriert; Periodenstarts lassen
  sich nachträglich korrigieren oder löschen; Versionskonflikte bleiben bis zu
  einer ausdrücklichen Entscheidung sichtbar und erhalten die lokale Fassung
- gehärtete Multi-Stage-Container, PostgreSQL 18, Backup-/Restore-/Update- und
  Admin-Skripte sowie additive Authentik-/Pangolin-Konfiguration
- versionierte OpenAPI-Spezifikation und daraus erzeugter Kotlin-Referenzclient;
  die App nutzt zur Laufzeit ihre typisierten Retrofit-Schnittstellen

Der tatsächlich verifizierte Stand und bewusst offene Erweiterungen stehen in
[`FINAL_REPORT.md`](FINAL_REPORT.md) und [`PROGRESS.md`](PROGRESS.md).

## Zuletzt verifizierter Live-Stand

Der verifizierte Serverstand trägt Version 1.0.3. Alle sechs Migrationen
einschließlich der persistenten Bootstrap-Sperre und des Schutzes vor doppelten
aktiven Periodenstarts sind live angewendet. Die Android-Prüfkette und der
Production-Debug-APK-Build 1.0.3 wurden ebenfalls tatsächlich ausgeführt.

- API und PostgreSQL laufen gesund unter `/docker/lunara`; sechs Migrationen
  sind angewendet. `/health` und `/ready` antworten mit HTTP 200, die
  Metaantwort meldet Version 1.0.3, abgeschlossenen Bootstrap und aktivierte
  Registrierung.
- Weder API noch Datenbank veröffentlichen einen Host-Port. Die API läuft als
  UID/GID `10001:10001` mit read-only Root-Dateisystem und ohne Linux-
  Capabilities.
- Authentik-Provider und Pangolin-Ressource wurden additiv eingerichtet. Der
  Cloudflare-CNAME und das Let’s-Encrypt-Zertifikat sind aktiv. Authentik reicht
  `/api/v1` ohne Browser-Login an die weiterhin durch Lunara-Tokens und Rollen
  geschützte API weiter; OpenAPI-/Browserseiten bleiben Authentik-geschützt.
- Vor API- und Authentik-Änderungen wurden eng benannte, validierte Datenbank-
  und Konfigurationsbackups angelegt. Beim Update wurde nur die Lunara-API neu
  erstellt; Emby, Authentik, Pangolin und PostgreSQL wurden nicht neu gestartet.
- Auf dem aktuellen Quellstand sind Prisma-Validierung, ESLint, 14/14
  Jest-Suites mit 59/59 Tests, Nest-Build und OpenAPI-Erzeugung/-Hashprüfung
  erfolgreich. Der letzte PostgreSQL-18-E2E-Lauf entstand vor den beiden
  finalen Migrationen und ist deshalb kein Abschlussnachweis für diesen
  Quellstand.
  Details stehen in [`TESTING.md`](TESTING.md).
- Der `BOOTSTRAP_TOKEN` wurde nach erfolgreicher Migration aus der Live-`.env`
  entfernt. Der persistente Serverstatus meldet weiterhin
  `setupRequired: false`; die offene Tracker-Registrierung bleibt aktiv.

## APK

Das tatsächlich gebaute 1.0.3-Artefakt liegt unter:

```text
artifacts/lunara-debug.apk
```

Die APK besitzt folgende geprüfte Eigenschaften:

- Paket `de.lunara.app.debug`
- `versionName` 1.0.3, `versionCode` 4
- `minSdk` 26, `targetSdk`/`compileSdk` 37
- 24.362.451 Bytes
- SHA-256
  `4dac957e020684f3d5ee5699fdc584a0f68393a58a76452801a21831616dd416`
- gültige ZIP-Struktur und APK Signature Scheme v2
- Signaturzertifikat SHA-256
  `f53698206074d4e2b0ff7b6e7b134a58919a358f2054fb7d4ba35df77ac69328`

Der geprüfte 1.0.3-Stand ist im privaten Branch
[`agent/authentik-human-access`](https://github.com/eliasbruno124-dev/Lunara/tree/agent/authentik-human-access)
veröffentlicht. Berechtigte, bei GitHub angemeldete Benutzer können die
[APK 1.0.3 direkt herunterladen](https://github.com/eliasbruno124-dev/Lunara/raw/refs/heads/agent/authentik-human-access/artifacts/lunara-debug.apk).
Der Implementierungscommit ist
[`9a9b35a5beadb2664935517feba5c9bfeadb0b9e`](https://github.com/eliasbruno124-dev/Lunara/commit/9a9b35a5beadb2664935517feba5c9bfeadb0b9e).

Der 1.0.3-Quellstand erzwingt für die Produktionsvariante HTTPS und verwendet
standardmäßig die änderbare Basisadresse `https://lunara.3li361.de`. Er erwartet
weder Benutzername noch Passwort in der URL. Lokal sind 63/63 JVM-Tests, beide
Lintvarianten, die Kompilierung der Production-Instrumentierungstests und der
Production-Debug-Build erfolgreich. Instrumentierte Tests wurden mangels
Gerät/Emulator nicht ausgeführt. Vor der Installation eines veröffentlichten
Artefakts den zugehörigen Hash prüfen:

```bash
cd artifacts
sha256sum -c SHA256SUMS
```

Die APK ist mit einem lokal dauerhaft abgelegten
Android-Debugschlüssel
signiert, der von Git ausgeschlossen ist. Nur die frühe 1.0.0-Test-APK
verwendete noch einen anderen flüchtigen Schlüssel und muss vor jeder Version
ab 1.0.1 einmal deinstalliert werden. Installationen ab 1.0.1 lassen sich bei
identischem Schlüssel direkt aktualisieren. Ein eigener Release-Build ist in
[`docs/ANDROID_SIGNING.md`](docs/ANDROID_SIGNING.md) beschrieben; private
Signierschlüssel liegen absichtlich nicht im Repository.

## Verbindung und Authentik

In der App genügt die normale Basisadresse:

```text
https://lunara.3li361.de
```

Authentik bleibt der einzige öffentliche Reverse-Proxy-Pfad, überspringt seinen
Browser-Login aber gezielt für `^/api/v1(?:/|$)`. Öffentliche Endpunkte wie
`/api/v1/meta`, Setup, Registrierung und Login besitzen eigene enge Regeln;
alle übrigen API-Aufrufe verlangen ein gültiges Lunara-Access-Token und prüfen
Rolle, Eigentum und interne Beziehungsgrenze serverseitig. `/api-docs` und
`/api-docs-json` bleiben durch Authentik geschützt.

Lokale Header-Auth-URL-Dateien dienen ausschließlich der administrativen
Prüfung der geschützten Browser-/OpenAPI-Oberfläche. Sie dürfen nicht in die
App, einen QR-Code, Git, Chat oder Cloudflare-DNS übernommen werden.

Der Bootstrap ist nur für das allererste `TRACKER`-Konto einer noch nie
eingerichteten Serverinstanz bestimmt. Sein erfolgreicher Abschluss wird
dauerhaft in PostgreSQL gespeichert und öffnet sich auch nach dem Löschen aller
Konten nicht erneut. Der `BOOTSTRAP_TOKEN` kann danach aus der Live-
Konfiguration entfernt werden. Bei `REGISTRATION_ENABLED=true` können weitere
Tracker jeweils eine neue, unsichtbar abgegrenzte Beziehung beginnen.
„Haushalt“ ist kein sichtbares Produktkonzept; die gleichnamige interne
Datenstruktur bleibt ausschließlich als Mandanten-/Autorisierungsgrenze
bestehen. Jede Beziehung erhält höchstens eine Tracker- und eine Partnerrolle.

## Entwicklung und Prüfung

Auf einem Docker-Host startet der folgende Befehl ausschließlich eine zufällig
benannte, kurzlebige PostgreSQL-Testumgebung und führt den API-Kernablauf aus:

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

Die Produktionskonfiguration veröffentlicht absichtlich weder API- noch
Datenbankports. Verwende für den produktiven Start ausschließlich die geprüfte
Anleitung in [`DEPLOYMENT.md`](DEPLOYMENT.md), nicht den Test-Stack.

## Projektstruktur

```text
apps/android/             native Android-App
services/api/             NestJS-/Prisma-API
packages/openapi-client/  OpenAPI-Spezifikation und generierter Kotlin-Client
infra/docker/             Containerfiles und isolierte Testkonfiguration
infra/traefik/            Proxy-Beispiele
scripts/                  Betrieb, Backup, Restore, Update, Gateway und E2E
docs/                     ergänzende Dokumentation und Tippkatalog
artifacts/                tatsächlich erzeugte installierbare APK
```

Produktname, Servername/-version und Android-Brandwerte sind an zentralen
Konfigurationsstellen gebündelt. Package-ID, Übersetzungstexte und Vektor-Assets
müssen bei einer vollständigen Umbenennung zusätzlich angepasst werden.

## Weiterführende Dokumentation

- [`DEPLOYMENT.md`](DEPLOYMENT.md): Debian 12, Docker, Authentik, Pangolin,
  Cloudflare, Backup und Update
- [`SECURITY.md`](SECURITY.md): Bedrohungsmodell, Schlüssel, Gerätesicherheit
  und bekannte Grenzen
- [`PRIVACY.md`](PRIVACY.md): gespeicherte und geteilte Daten
- [`ALGORITHM.md`](ALGORITHM.md): deterministische Prognose
- [`TESTING.md`](TESTING.md): ausgeführte und noch nicht ausführbare Tests

## Lizenz

Nicht zur öffentlichen Weitergabe lizenziert. Alle Rechte verbleiben beim
Repository-Eigentümer.
