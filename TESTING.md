# Tests

Erfolgsaussagen beziehen sich ausschließlich auf tatsächlich ausgeführte
Befehle. Die endgültigen Ergebnisse, Artefaktgrößen, Prüfsummen, Image-IDs und
Live-Prüfungen des Release 1.0.4 stehen in `FINAL_REPORT.md`. Diese Datei
beschreibt die verbindliche Prüfmatrix und behauptet nicht allein aufgrund
vorhandener Dateien einen erfolgreichen Build.

## Tatsächlich ausgeführter 1.0.4-Abschluss

- Backend: 18/18 Jest-Suites und 79/79 Tests; Prisma, ESLint, Nest-Build,
  OpenAPI-Reproduzierbarkeit sowie vollständiges und produktives npm-Audit
  erfolgreich; beide Audits mit 0 bekannten Schwachstellen
- PostgreSQL 18: 1/1 E2E-Suite und 1/1 durchgehender Test mit sechs
  Migrationen erfolgreich
- Android: 19/19 JVM-Suites und 77/77 Tests; beide Lints ohne Error/Fatal,
  Production-Instrumentierung kompiliert, Debug- und unsignierte Release-APK
  gebaut
- Gateway: vollständiger anonymer Authentik-Schutz, gemeinsamer App-Header,
  persönlicher Login, `/me`, Sync-Pull/-Push und Logout erfolgreich

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

Der finale 1.0.4-E2E-Lauf wurde tatsächlich erfolgreich beendet. Der
detaillierte Ergebnisnachweis steht in `FINAL_REPORT.md`.

## Android

```bash
cd apps/android
./gradlew --no-daemon --max-workers=1 testLocalDebugUnitTest
./gradlew --no-daemon --max-workers=1 lintLocalDebug lintProductionDebug
./gradlew --no-daemon --max-workers=1 compileProductionDebugAndroidTestKotlin
./gradlew --no-daemon --max-workers=1 assembleProductionDebug
```

Für Android 1.0.4 umfasst die JVM-Prüfung zusätzlich zu Offline-Sync,
Navigation, Wipe und Passwortwechsel insbesondere:

- ein wirklich leeres Serverfeld beim ersten Start
- ausschließlich HTTPS für URLs mit Gateway-Credential
- einmaliges Parsen und Dekodieren einer HTTPS-Userinfo-Adresse mit Gateway-
  Benutzer und App-Passwort bei sofortiger Sanitierung zur credentialfreien
  Basisadresse
- präemptives `Authorization: Basic …` nur auf exakt demselben Ursprung,
  unterdrückte Redirects und kein Credential-Leak an Fremdhosts
- persönliches `X-Lunara-Authorization: Bearer …`, wenn Gateway Auth aktiv ist
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

Zusätzlich sind Manifestwerte, `versionName` 1.0.4, `versionCode` 5 und APK-v2-
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
Ausgabebaum mit gespeicherten Hashes. Der Vertrag für 1.0.4 beschreibt bei
Gateway-Betrieb das persönliche Token in `X-Lunara-Authorization`; der äußere
Basic Header bleibt Infrastruktur- und Transportvoraussetzung. Ein erfolgreicher
Hashvergleich beweist nicht automatisch, dass der separate Kotlin-Client unter
der verfügbaren Speichergrenze kompiliert wurde.

## Container

```bash
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example build api
docker image inspect lunara-api:1.0.4
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

Der erwartete 1.0.4-Zustand ist:

1. genau eine neutrale Authentik-Serviceidentität und genau ein nicht
   ablaufendes App-Passwort
2. leeres `skip_path_regex`, keine menschlichen Mitglieder der Gateway-Gruppe
3. `/api/v1/meta` mit äußerem Basic Header: HTTP 200 und gültige Metaantwort
4. `/api-docs-json` mit äußerem Basic Header: HTTP 200 und gültiges OpenAPI
5. Root, Health, OpenAPI, Meta und Login ohne Basic Header: Authentik-Redirect,
   niemals anonyme HTTP-200-APIantwort
6. persönlicher Lunara-Login mit äußerem Basic Header, anschließend `/me` mit
   zusätzlichem `X-Lunara-Authorization` und erfolgreicher Logout
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
