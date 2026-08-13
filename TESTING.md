# Tests

Erfolgsaussagen beziehen sich ausschließlich auf tatsächlich ausgeführte
Befehle. Den finalen Lauf mit Ergebnissen enthält `FINAL_REPORT.md`.

## Backend

```bash
npm ci
npm run lint
npm test
npm run build
npm run prisma:validate
npm run openapi:check
npm audit --omit=dev
```

Die 14 Unit-Suites prüfen insbesondere Prognosealgorithmus,
Freigabeprojektion, Tippfilter, App-Konfiguration, Symptomkatalog und sichere
Admin-CLI-Argumente, den billigen Einladungscode-Preflight, die
zeitzonensichere, fail-closed Partnerphase sowie Passwortwechsel und
Sitzungswiderruf. Hinzu kommen der cachefreie Meta-Endpunkt und der persistente
Bootstrap-Status.

Aktueller Lauf für Version 1.0.3: **14/14 Suites und 59/59 Tests erfolgreich**.
Prisma-Client-Erzeugung, Prisma-Validierung, ESLint und TypeScript-/Nest-Build
wurden auf demselben Quellstand ebenfalls erfolgreich abgeschlossen.
`npm audit --omit=dev` meldete **0 bekannte Schwachstellen**.

Der End-to-End-Lauf verwendet eine echte, kurzlebige PostgreSQL-18-Instanz:

```bash
./scripts/e2e.sh
```

Das Skript verwendet einen zufälligen Compose-Projektnamen, ein internes
Testnetz und `tmpfs`; beim Beenden entfernt es ausschließlich dieses eindeutig
benannte Testprojekt. Der für 1.0.3 erweiterte Test bildet in einer
zusammenhängenden Suite unter anderem ab:

1. Setup-Status und einmalige Bootstrap-Sperre
2. generische Loginfehler sowie Refresh-Rotation und Replay-Widerruf
3. Passwortwechsel mit Prüfung des aktuellen Passworts, Widerruf anderer
   Lunara-Sitzungen, Erhalt der aktuellen Tokenfamilie und Audit-Ereignissen
4. Erst-Tracker, permanente Einmal-Einladung, Partner und Rollenprüfung
5. optionale Registrierung eines weiteren Trackers mit eigener Beziehung
6. Isolation fremder Beziehungs-IDs bei Perioden, Pull-/Push-Sync und eigenen
   Symptomen
7. gleichzeitiges Erstellen/Beanspruchen von Einladungen, Widerruf und
   garantierte Einmalverwendung
8. Perioden, deterministische Prognose und Partnerfreigaben
9. Verschlüsselung einer Notiz im Datenbankzustand
10. Tipp-/Care-Filter ohne Rückschluss auf gesperrte Symptome
11. JSON-Export, Importvorschau und Erkennung bereits vorhandener aktiver
   Datensätze
12. Soft Delete, Offline-Sync-Idempotenz und Versionskonflikt
13. strikte Sync-Payloads und Recovery einer abgelaufenen
    In-Progress-Reservation
14. persistenter Bootstrap-Abschluss nach Löschung der letzten Beziehung sowie
    weiterhin mögliche öffentliche Tracker-Registrierung

Eine Test-Suite ist nicht dasselbe wie ein Testfall; Jest berichtet die genaue
Anzahl bei jedem Lauf.

Der letzte PostgreSQL-18-Lauf für einen früheren 1.0.3-Zwischenstand bestand mit
**1/1 Suite und 1/1 zusammenhängendem E2E-Test**. Er entstand vor der
`ServerState`-Migration und dem finalen Unique-Constraint für aktive
Periodenstarts und ist daher ausdrücklich kein E2E-Abschlussnachweis für den
aktuellen Quellstand. Der Test wurde inzwischen um beide Fälle erweitert, aber
danach **nicht erneut ausgeführt**. Das Test-Timeout ist bewusst auf fünf
Minuten gesetzt, weil Argon2id und die gesamte Kernstrecke auch unter den festen
CPU-/RAM-Grenzen des Testcontainers zuverlässig abgeschlossen werden sollen.

Gezielte Unit-/E2E-Regressionen decken unter anderem strikte Sync-Payloads,
Recovery einer abgelaufenen Idempotenz-Reservation, Beziehungsisolation im
Pull-/Push-Sync, Create-ID-Payloadkonflikte, die serverseitige
Einladungslaufzeit-Policy, CSV-Formelschutz, den gültigen Einsatz der
Tracker-IANA-Zeitzone und die fail-closed Partnerphase ab. Der Importtest erkennt
Duplikate gegen den vorhandenen aktiven Datenbestand; Duplikate innerhalb eines
einzelnen neuen Import-Payloads sind damit nicht abgedeckt.

## Android

```bash
cd apps/android
./gradlew --no-daemon --max-workers=1 testLocalDebugUnitTest
./gradlew --no-daemon --max-workers=1 lintLocalDebug lintProductionDebug
./gradlew --no-daemon --max-workers=1 assembleProductionDebug
./gradlew --no-daemon --max-workers=1 compileProductionDebugAndroidTestKotlin
```

Die JVM-Tests prüfen Server-URL-Validierung, Lunara-Authentifizierung,
rollenabhängige Navigation, Serverwechselregeln, Mutation-Queue und den
garantierten lokalen Wipe. Hinzu kommen servergleiche Bootstrap-/Formregeln,
Passwortwechselregeln, Sync-Authentifizierungsentscheidung, Serialisierung
paralleler Worker, Credential-Generationsschutz, alte Cookie-Generationen,
terminale Queue-Fehler mit manuellem Neuversuch und ein fail-closed Schutz gegen
sitzungsfremde UI-Caches. Ein autoritatives Setup-`409` schaltet die Oberfläche
trotz einer unerwartet alten Metaantwort unmittelbar auf Anmeldung um.
Der Produktions-Debug-Build wird geprüft, weil nur diese Variante als
`artifacts/lunara-debug.apk` ausgeliefert wird; sie erlaubt kein Klartext-HTTP.

Aktueller finaler Lauf für Version 1.0.3:

- `testLocalDebugUnitTest`: **63/63 JVM-Tests**, erfolgreich
- `lintLocalDebug`: erfolgreich
- `lintProductionDebug`: erfolgreich
- `compileProductionDebugAndroidTestKotlin`: erfolgreich
- `assembleProductionDebug`: erfolgreich
- APK-ZIP-Prüfung: erfolgreich
- APK Signature Scheme v2: erfolgreich

Die Production-Debug-APK unter `artifacts/lunara-debug.apk` ist 24.362.451
Bytes groß und besitzt SHA-256
`4dac957e020684f3d5ee5699fdc584a0f68393a58a76452801a21831616dd416`.
Geprüft wurden Paket `de.lunara.app.debug`, `versionName` 1.0.3,
`versionCode` 4, `minSdk` 26 sowie `targetSdk`/`compileSdk` 37. Das
Signaturzertifikat besitzt SHA-256
`f53698206074d4e2b0ff7b6e7b134a58919a358f2054fb7d4ba35df77ac69328`.
Der Production-Standardserver ist die weiterhin änderbare HTTPS-Basisadresse
`https://lunara.3li361.de`.

Die ausgeführten neuen 1.0.3-Regressionen betreffen öffentliche Tracker-
Registrierung, rollenabhängige Navigation ohne sichtbares Haushaltsfeld,
permanente Partner-Einladung und Widerruf, Periodenstart-Korrektur/-Löschung
sowie die Reihenfolge mehrerer Offline-Mutationen desselben Datensatzes. Hinzu
kommen der fortlaufende Queue-Drain, Schutz sichtbarer Konflikte vor Remote-
Überschreiben, die Schreibsperre während einer bestätigten Server-Neuladung
und explizite `null`-Werte zum Leeren optionaler Tagesangaben.

Compose-Instrumentierung für Onboarding, Serverwechsel, Periodenstart,
Periodenkorrektur/-löschung, Partner-Dashboard, Tipp-Nullzustand und das lokale
Ausfüllen des Passwortwechseldialogs ist vorhanden. Sie wurde auf diesem Host
**nicht ausgeführt**, weil kein Android-Emulator/KVM-Gerät bereitstand.
Entsprechend sind reale Keystore-/SQLCipher-/WorkManager-Interaktionen auf
einem Gerät noch manuell zu prüfen.

## OpenAPI-Client

```bash
npm run openapi:generate
npm run openapi:check
cd packages/openapi-client/generated/kotlin
./gradlew --no-daemon compileKotlin
```

`openapi:check` vergleicht die generierte Spezifikation, Generatorparameter und
den Kotlin-Ausgabebaum mit gespeicherten Hashes. Spezifikation, Client und
Metadaten wurden für Version 1.0.3 mit OpenAPI Generator **7.24.0**
reproduzierbar neu erzeugt; der Hashvergleich war erfolgreich.

Spezifikations-SHA-256:
`e478d9a0474b401555736cbaf4444db6d3555e3c02fa922c1a4dd27f12e190ec`.

Der separate anschließende Kotlin-Compile-Versuch im auf 768 MiB begrenzten
Container war **nicht erfolgreich**: memcg beendete den Gradle-Daemon wegen
Speichermangels. Dabei wurde kein produktiver Dienst beeinträchtigt oder neu
gestartet. Der erzeugte Client ist ein separates Vertragsartefakt; die
Android-App nutzt ihre eigene typisierte Retrofit-Schicht und bindet ihn in
Version 1 nicht direkt ein.

## Container

Eine gefahrlose statische Compose-Prüfung und ein Image-Build lauten:

```bash
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example build api
docker image inspect lunara-api:1.0.3
```

`.env.example` enthält nur Platzhalter und eignet sich **nicht** zum Starten
einer Instanz. `docker compose up` wird ausschließlich mit einer außerhalb von
Git erzeugten echten `.env` und gemäß `DEPLOYMENT.md` verwendet.

Auf dem Zielserver wurde das finale Image `lunara-api:1.0.3` unter einer
1-GiB-RAM- und 0,5-CPU-Grenze tatsächlich gebaut. Image-ID:
`sha256:235794dfed4abaf3a0eb055d196738c56530445f742c7a601cfac5989340e06d`.
Auch dessen Produktionsabhängigkeitsbaum meldete im npm-Audit 0 bekannte
Schwachstellen. Vor der Migration wurde
`/var/backups/lunara/lunara-pre-server-state-20260813T152814Z.dump` erstellt,
mit `pg_restore --list` validiert und als 62.151-Byte-Datei mit SHA-256
`1279c7a00f14c42f0528c1dd5b67370d7f5274af707a708e705a28a12135f540`
geprüft.

Alle sechs Migrationen einschließlich `ServerState` und eindeutigem aktiven
Periodenstart wurden angewendet; ausschließlich die Lunara-API wurde neu
erstellt. API und PostgreSQL sind gesund, veröffentlichen keine Host-Ports und
die API läuft als UID/GID `10001:10001` mit read-only Root-Dateisystem und
vollständigem Capability-Drop. Live geprüft wurden `/health`, `/ready` und
`/api/v1/meta` mit HTTP 200, `/api/v1/me` anonym mit HTTP 401 sowie die
OpenAPI-Oberfläche anonym mit HTTP 302. Meta meldet Version 1.0.3,
`setupRequired: false` und aktivierte Registrierung. Der `BOOTSTRAP_TOKEN`
wurde aus der Live-`.env` entfernt; ein Dummy-Setupversuch endete mit HTTP 401.
Der Bestand blieb bei einem Benutzer, einer Mitgliedschaft und einem
Periodeneintrag; Dunja blieb aktive `TRACKER`.

## Gateway und Betrieb

Die beiden additiven Infrastrukturhelfer besitzen einen read-only
`--check`-Modus. Vor jeder Live-Änderung werden damit Zielcontainer,
Versionen, Schema und Namenskonflikte geprüft. Der öffentliche Gatewaytest liest
die Zugangsdaten für die weiterhin Authentik-geschützte OpenAPI-Oberfläche aus
einer lokalen Datei, ohne sie in Prozessargumente oder Ausgabe zu schreiben:

```bash
sudo /docker/lunara/scripts/configure-authentik.sh --check
sudo /docker/lunara/scripts/configure-pangolin.sh --check
sudo /docker/lunara/scripts/verify-gateway.sh /docker/lunara/gateway-a.url
```

Der 1.0.3-Zielzustand wurde mit drei getrennten öffentlichen Prüfungen
bestätigt:

1. `/api/v1/meta` antwortet ohne Authentik-Zugangsdaten mit HTTP 200.
2. `/api/v1/me` antwortet ohne Lunara-Token mit HTTP 401 und ohne Daten.
3. `/api-docs-json` bleibt ohne Authentik-Anmeldung gesperrt und liefert mit
   dem lokalen Prüfzugang eine gültige OpenAPI-Antwort.

Nach einem validierten Authentik-Datenbankbackup wurde ausschließlich der
Skip-Ausdruck des Lunara-Providers geändert. Alle drei Prüfungen bestanden:
Meta HTTP 200, `/api/v1/me` anonym HTTP 401 und OpenAPI anonym HTTP 302. Die
getrennten serverlokalen administrativen Zugänge für Dune und Elias erreichten
die geschützte Dokumentation jeweils mit HTTP 200, ohne Zugangsdaten
auszugeben. Ein echter
Lunara-Login von Dunja sowie `/me` und Logout wurden erfolgreich verifiziert.
Emby, Authentik, Pangolin und PostgreSQL wurden nicht neu gestartet.

## Secret-Scan

Der projektinterne Secret-Scan lief auf dem finalen Quellstand ohne Fund.
`gitleaks` war auf dem Host nicht installiert; ein gitleaks-Ergebnis wird daher
nicht behauptet.

Ein produktiver Restore wird nicht als Test verwendet. Er ist destruktiv und
erfordert den dokumentierten expliziten Bestätigungsschalter; ein Restore-Test
gehört in eine separat benannte Instanz mit Kopien von Dump und Master-Key.
Ein echtes Live-Backup wurde jedoch erstellt, die Archivprüfsummen wurden
geprüft und `pg_restore --list` konnte den Dump vollständig lesen.
