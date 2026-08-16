# Tests

Erfolgsaussagen beziehen sich ausschließlich auf tatsächlich ausgeführte
Befehle. Die endgültigen Ergebnisse, Artefaktgrößen, Prüfsummen, Image-IDs und
Live-Prüfungen von Server 1.0.7 und Android 1.0.8 stehen in
`FINAL_REPORT.md`. Diese Datei beschreibt die verbindliche Prüfmatrix und
behauptet nicht allein aufgrund vorhandener Dateien einen erfolgreichen Build.

## Tatsächlich ausgeführter Server 1.0.7 / Android 1.0.8

Backend:

- 20/20 Jest-Suites und 105/105 Unit-Tests erfolgreich; zusätzliche Tests decken
  atomare Sync-Receipts, Legacy-Pending-Recovery für Perioden und Tageslogs
  sowie abgelaufene/race-sicher neu beanspruchte Receipts,
  Importüberschneidungen, Lock und Prognoseneuberechnung ab
- TypeScript-Gesamtprüfung inklusive erweiterter E2E-Datei erfolgreich
- vollständiges ESLint der Backend-Quellen erfolgreich; ein vorheriger
  vollständiger Lint-Versuch im 768-MiB-
  Testcontainer endete ausschließlich mit V8-Heap-OOM und zählt nicht als
  Erfolg
- Prisma-Schema validiert, Nest-Build und reproduzierbarer OpenAPI-/Kotlin-
  Client-Hashcheck erfolgreich; Multi-Stage-Image `lunara-api:1.0.7`
  tatsächlich gebaut und als UID/GID `10001:10001` ausgeführt
- PostgreSQL 18: sieben Migrationen auf einer frischen temporären Tmpfs-
  Datenbank erfolgreich angewendet; 1/1 E2E-Suite und 1/1 durchgehender Test
  erfolgreich. Der Lauf deckt Setup, Registrierung mehrerer isolierter
  Beziehungen, Einladung/Partner, Rollen, Perioden/Prognose, Freigabefilter,
  Import/Export, Passwortwechsel, Soft Deletes, konkurrierende
  Überschneidungen und exakt-einmal Sync-Retries ab.
- Zwei vorherige E2E-Versuche werden nicht als Erfolg gezählt: Der erste fand
  den anschließend behobenen Ablauf-Key-Konflikt; der zweite erreichte im
  langen Test bewusst das gemeinsame Login-Rate-Limit. Die abschließenden
  Passwort-Login-Assertions verwenden nun eine eigene Test-App-Instanz mit
  frischem In-Memory-Bucket, während der primäre Ablauf das Produktionslimit
  weiterhin prüft.

Android:

- `testLocalDebugUnitTest` und `testProductionDebugUnitTest`: jeweils 27
  Suites, 132 Tests, 0 Fehler, 0 übersprungen
- `lintLocalDebug`: erfolgreich, 0 Fehler und 39 Warnungen
- `lintProductionDebug` mit bereits im Local-Lint geprüften Testanalysen
  ausgeschlossen: erfolgreich, 0 Fehler und 38 Warnungen
- `compileProductionDebugAndroidTestKotlin`: erfolgreich; die Compose-
  Gerätetests wurden mangels Emulator/Gerät kompiliert, aber nicht ausgeführt
- `assembleProductionDebug`: erfolgreich
- `assembleProductionRelease` nach ehrlich fehlgeschlagenen ressourcenarmen
  D8-/`lintVital`-Läufen im gezielten Wiederholungslauf erfolgreich; der
  vollständige Production-Lint war zuvor separat erfolgreich
- APK-Prüfung: VersionCode 9, VersionName 1.0.8, minSdk 26, targetSdk 36,
  compileSdk 37, fehlerfreie ZIP-Struktur; Debug mit demselben Zertifikat wie
  1.0.7 gültig v2-signiert, Release erwartbar unsigned

Live:

- aggregierte Vorprüfung: ein aktiver, gültiger offener Zeitraum und keine
  Überschneidungspaare; keine Gesundheitsinhalte ausgegeben
- PostgreSQL-Custom-Dump vor Migration erstellt und per `pg_restore --list`
  validiert
- Migration `20260816120000_period_overlap_exclusion`, GiST-Constraint und
  `btree_gist` erfolgreich geprüft
- API 1.0.7: `/health`, `/ready`, `/api/v1/meta` jeweils HTTP 200, Healthcheck
  healthy, RestartCount 0, non-root und read-only
- Gateway-Prüfung erfolgreich: technischer Header-Auth-Zugang funktioniert,
  anonyme Root-/Health-/OpenAPI-/Meta-/Login-Zugriffe bleiben durch Authentik
  gesperrt
- final neu gebautes und ausgerolltes API-Image:
  `sha256:3e3105eb24b805c1df621b5ec5c5910ba1db4f9adc5f54937faa67f27de7f439`;
  Healthcheck healthy, RestartCount 0
- npm-freie Runtime, Node/Argon2 und direkter Prisma-CLI-Aufruf geprüft;
  sieben Migrationen mit diesem finalen Runtime-Image auf einer frischen
  PostgreSQL-18-Datenbank erfolgreich angewendet
- Trivy 0.72.0 gegen das finale Image mit HIGH/CRITICAL und
  `ignore-unfixed`: 0 fixbare Funde

## Tatsächlich ausgeführte Android-1.0.6-Ergänzung

- vollständige Local-Debug-JVM-Suite: 25 Suites, 114 Tests, 0 Fehler,
  0 übersprungen; darin 28 neue Tests für Benachrichtigungspolicy,
  verschlüsselten Prognosecache, zweiphasige Zustellmarkierung, Ruhezeit,
  Doze-Verzögerung sowie Sommer-/Winterzeit
- `lintLocalDebug` vollständig einschließlich Unit- und Instrumentationstest-
  Quellen erfolgreich; 0 Error/Fatal
- Production-Hauptcode mit `lintProductionDebug` und ausgeschlossener
  doppelter Android-/Unit-Testquellenanalyse erfolgreich; 0 Error/Fatal. Die
  gleichen Testquellen waren bereits im vollständigen Local-Lint enthalten.
- `compileProductionDebugAndroidTestKotlin` erfolgreich. Die Compose-Tests
  wurden mangels Emulator/Gerät kompiliert, aber nicht ausgeführt.
- `assembleProductionDebug` erfolgreich; `assembleProductionRelease` nach
  bereits erfolgreichem Production-Lint mit den redundanten
  `lintVital*ProductionRelease`-Tasks ausgeschlossen erfolgreich
- APK-ZIP-Struktur, VersionCode 7, VersionName 1.0.6, minSdk 26, targetSdk 37,
  gültige v2-Debugsignatur und bewusst fehlende Release-Signatur geprüft

Der zunächst kombinierte Sammellauf sowie ein zu eng begrenzter separater
Lint-/Release-Lauf wurden kontrolliert beendet beziehungsweise vom
Containerlimit beendet. Sie werden nicht als Erfolg gezählt. Die oben
aufgeführten Schritte wurden danach einzeln real erfolgreich ausgeführt. Der
Produktionsserver wurde nicht neu gestartet oder verändert.

## Tatsächlich ausgeführter 1.0.5-Abschluss

- Backend: 19/19 Jest-Suites und 83/83 Tests; Prisma, ESLint, Nest-Build,
  OpenAPI-Reproduzierbarkeit sowie vollständiges und produktives npm-Audit
  erfolgreich; beide Audits mit 0 bekannten Schwachstellen
- PostgreSQL 18: 1/1 E2E-Suite und 1/1 durchgehender Test mit sechs
  Migrationen erfolgreich
- Android: 20/20 JVM-Suites und 86/86 Tests; beide Lints ohne Error/Fatal,
  Production-Instrumentierung kompiliert, Debug- und unsignierte Release-APK
  gebaut
- Gateway: vollständiger anonymer Authentik-Schutz und gemeinsamer App-Header
  erfolgreich; die produktive Passwortgrenze wurde mit einem temporären Konto
  geprüft und dieses anschließend vollständig gelöscht

Prüfsummen, Image-ID, Live-Zustand und bewusst nicht ausgeführte Gerätetests
stehen in `FINAL_REPORT.md`.

## Backend

```bash
npm ci
npm run prisma:generate
npm run prisma:validate
npm run lint
npm test
npm run build
npm run openapi:check
npm audit
npm audit --omit=dev
```

Die Backend-Suites prüfen insbesondere:

- deterministische Periodenprognose, robuste Ausreißerbehandlung und
  verständliche Konfidenz
- optionale Fertilitätsschätzung nur nach Opt-in, mit mindestens drei
  vollständigen Zyklen und fail-closed Sondermodi
- serverseitige Fertilitätsfreigabe nur bei aktiver persönlicher Schätzung sowie
  atomaren Widerruf beim Deaktivieren
- vollständige Partnerprojektion anhand aktueller Freigaben, Sharing-Pause und
  privatem Tag; Phase, tatsächliche Perioden und Fertilitätsdaten bleiben dann
  verborgen
- kein Entschlüsseln einer Notiz, solange sie nicht tatsächlich freigegeben ist
- Auswahl aus 71 deutschen und englischen Partner-Tipps anhand freigegebener
  Phase/Symptome, Care Preferences, Feedback und Priorität
- fail-closed Tippfilter, damit gesperrte Symptome weder direkt noch indirekt
  erscheinen
- Argon2id-Login, generische Fehler, Refresh-Token-Rotation, Replay-Widerruf,
  Passwortwechsel und getrennte Gerätesitzungen
- einheitliche Kontopasswortgrenze von 8 bis 128 Unicode-Codepoints ohne
  vorgeschriebene Großbuchstaben, Ziffern oder Sonderzeichen; Verifikation
  bestehender Zugangsdaten weiterhin mit 1 bis 128 Zeichen sowie identische
  Zählung von Variationsselektoren in API und Android
- Bootstrap, optionale Tracker-Registrierung, permanente Einmal-Einladungen,
  Rollenprüfung und Isolation fremder Beziehungen
- JSON-/CSV-Export, Importvorschau, Soft Deletes, Sync-Idempotenz und
  Versionskonflikte
- cachefreie Metaantwort mit API-Version und dem erwarteten persönlichen
  Headernamen `X-Lunara-Authorization`

## PostgreSQL-End-to-End

```bash
./scripts/e2e.sh
```

Das Skript verwendet einen zufälligen Compose-Projektnamen, ein internes
Testnetz und `tmpfs`. Es bildet in einer zusammenhängenden echten
PostgreSQL-18-Strecke ab:

1. frischen Server und einmalige persistente Bootstrap-Sperre
2. ersten Tracker und optional weitere getrennte Tracker-Beziehungen
3. permanente Einladung, eigenen Partnerlogin, Einmalverwendung und Widerruf
4. Passwortwechsel, Sitzungswiderruf und Refresh-Rotation
5. Periodeneintrag, Bereichsprognose und Partnerprojektion
6. Freigabeentzug ohne nachträgliche Partneroffenlegung
7. verschlüsselte Notiz im Datenbankzustand
8. Care-/Tippfilter ohne Rückschluss auf nicht freigegebene Symptome
9. Offline-Push, Pull, Idempotenz, Konflikt und Soft Delete
10. Export, Importvorschau und Duplikaterkennung
11. Isolation fremder Beziehungs-IDs über direkte API- und Sync-Aufrufe
12. weiterhin gesperrten Bootstrap nach Löschung der letzten Beziehung

Der finale 1.0.5-E2E-Lauf wurde tatsächlich erfolgreich beendet. Der
detaillierte Ergebnisnachweis steht in `FINAL_REPORT.md`.

## Android

```bash
cd apps/android
./gradlew --no-daemon --max-workers=1 testLocalDebugUnitTest
./gradlew --no-daemon --max-workers=1 lintLocalDebug lintProductionDebug
./gradlew --no-daemon --max-workers=1 compileProductionDebugAndroidTestKotlin
./gradlew --no-daemon --max-workers=1 assembleProductionDebug
./gradlew --no-daemon --max-workers=1 assembleProductionRelease
```

Für Android 1.0.5 umfasst die JVM-Prüfung zusätzlich zu Offline-Sync,
Navigation, Wipe und Passwortwechsel insbesondere:

- ein wirklich leeres Serverfeld beim ersten Start
- ausschließlich HTTPS für URLs mit Gateway-Credential
- einmaliges Parsen und Dekodieren einer HTTPS-Userinfo-Adresse mit Gateway-
  Benutzer und App-Passwort bei sofortiger Sanitierung zur credentialfreien
  Basisadresse
- präemptives `Authorization: Basic …` nur auf exakt demselben Ursprung,
  unterdrückte Redirects und kein Credential-Leak an Fremdhosts
- persönliches `X-Lunara-Authorization: Bearer …`, wenn Gateway Auth aktiv ist
- dieselbe 8-bis-128-Zeichen-Grenze für Registrierung, Partnerbeitritt und
  Passwortwechsel sowie kompatible Anmeldung mit bestehenden Passwörtern
- rollenabhängige Navigation und vollständige Freigabeschalter
- autoritatives Entfernen gecachter Partner-Tipps nach Freigabeentzug
- Fertilitäts-Opt-in, Sicherheitsbestätigung, Mindestdaten und getrennte
  Partnerfreigabe
- Phasen- und Tippdarstellung nur anhand tatsächlich serverseitig gelieferter,
  freigegebener Werte
- Laden und gezieltes Patchen der acht Care-Preferences-Schalter in deutschen
  und englischen Einstellungen
- Korrektur und Löschung eines Periodenstarts über die Offline-Queue
- automatische Synchronisierung nach Mutation, beim Öffnen, im Vordergrund und
  mit WorkManager einschließlich begrenztem Backoff

Compose-Instrumentierung existiert für Onboarding, Serverwechsel,
Periodenstart, Periodenkorrektur/-löschung, Partner-Dashboard,
Freigabe-/Tipp-Nullzustand, Passwortwechsel und Offline-Nachsynchronisierung.
Kompilierung ist nicht dasselbe wie die Ausführung auf einem Gerät. Reale
Keystore-, SQLCipher-, WorkManager-, Accessibility- und UI-Interaktionen werden
nur dann als bestanden gemeldet, wenn ein Emulator oder Gerät sie tatsächlich
ausgeführt hat.

Nach einem erfolgreichen APK-Build folgen mindestens:

```bash
unzip -t artifacts/lunara-debug.apk
sha256sum artifacts/lunara-debug.apk
```

Zusätzlich sind Manifestwerte, `versionName` 1.0.5, `versionCode` 6 und APK-v2-
Signatur mit Android-Buildtools zu prüfen. Die konkreten Werte gehören in
`FINAL_REPORT.md` und `artifacts/SHA256SUMS`.

## OpenAPI-Client

```bash
npm run openapi:generate
npm run openapi:check
cd packages/openapi-client/generated/kotlin
./gradlew --no-daemon compileKotlin
```

`openapi:check` vergleicht Spezifikation, Generatorparameter und Kotlin-
Ausgabebaum mit gespeicherten Hashes. Der Vertrag für 1.0.5 beschreibt bei
Gateway-Betrieb das persönliche Token in `X-Lunara-Authorization`; der äußere
Basic Header bleibt Infrastruktur- und Transportvoraussetzung. Ein erfolgreicher
Hashvergleich beweist nicht automatisch, dass der separate Kotlin-Client unter
der verfügbaren Speichergrenze kompiliert wurde. Beim 1.0.5-Abschluss wurde
`compileKotlin` nach einem dokumentierten Abbruch des 1-GiB-Gradle-Daemons mit
1,5 GiB tatsächlich erfolgreich wiederholt. Der OpenAPI-Hash lautet
`8618f15f759748bafedc7396e8ce4e4591de82b7f22c22fc4afe95b61eece932`.

## Container

```bash
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example build api
docker image inspect lunara-api:1.0.5
```

`.env.example` enthält nur Platzhalter und eignet sich nicht zum Start einer
produktiven Instanz. Vor dem Live-Austausch werden Image-Nutzer, Healthcheck,
read-only Root-Dateisystem, Capability-Drop und das Fehlen veröffentlichter
API-/Datenbankports geprüft. Migrationen werden vor dem API-Ersatz ausgeführt;
ein Live-Datenbankbackup wird mit `pg_restore --list` validiert.

## Authentik- und Gateway-Prüfung

```bash
sudo /docker/lunara/scripts/configure-authentik.sh --check
sudo /docker/lunara/scripts/configure-pangolin.sh --check
sudo /docker/lunara/scripts/verify-gateway.sh
```

Der erwartete 1.0.5-Zustand ist:

1. genau eine neutrale Authentik-Serviceidentität und genau ein nicht
   ablaufendes App-Passwort
2. leeres `skip_path_regex`, keine menschlichen Mitglieder der Gateway-Gruppe
3. `/api/v1/meta` mit äußerem Basic Header: HTTP 200 und gültige Metaantwort
4. `/api-docs-json` mit äußerem Basic Header: HTTP 200 und gültiges OpenAPI
5. Root, Health, OpenAPI, Meta und Login ohne Basic Header: Authentik-Redirect,
   niemals anonyme HTTP-200-APIantwort
6. öffentliche Registrierungsprüfung: acht kleingeschriebene Zeichen werden
   akzeptiert, sieben mit HTTP 400 abgelehnt; das temporäre Konto wird danach
   wieder gelöscht und alle fachlichen Tabellen bleiben leer
7. kein zweiter direkter Pangolin-/Traefik-Pfad zur API

`verify-gateway.sh` liest den gemeinsamen Zugang nur aus einer lokalen
`0600`-Datei und gibt ihn weder in Prozessargumenten noch Standardausgabe aus.
Menschliche Authentik-Konten und alte A/B-Gateway-Credentials dürfen den
Zielzustand nicht erreichen.

## Secret-Scan und Grenzen

```bash
./scripts/secret-scan.sh
git diff --check
```

Ein produktiver Restore wird nicht als Test verwendet. Er ist destruktiv und
gehört in eine isolierte Testinstanz mit Kopien von Dump und Master-Key. Ein
fehlendes Werkzeug, ein OOM-Abbruch oder eine nur kompilierte Instrumentierung
wird im Abschlussbericht ausdrücklich als solcher dokumentiert.
