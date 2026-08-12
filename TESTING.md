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

Die neun Unit-Suites prüfen insbesondere Prognosealgorithmus,
Freigabeprojektion, Tippfilter, App-Konfiguration, Symptomkatalog und sichere
Admin-CLI-Argumente, den billigen Einladungscode-Preflight und die
zeitzonensichere, fail-closed Partnerphase.

Der End-to-End-Lauf verwendet eine echte, kurzlebige PostgreSQL-18-Instanz:

```bash
./scripts/e2e.sh
```

Das Skript verwendet einen zufälligen Compose-Projektnamen, ein internes
Testnetz und `tmpfs`; beim Beenden entfernt es ausschließlich dieses eindeutig
benannte Testprojekt. Der derzeitige Test bildet in einer zusammenhängenden
Suite unter anderem ab:

1. Setup-Status und einmalige Bootstrap-Sperre
2. generische Loginfehler sowie Refresh-Rotation und Replay-Widerruf
3. Tracker, Einmal-Einladung, Partner und Rollenprüfung
4. Fremdhaushalts-IDOR bei Perioden, Sync und eigenen Symptomen
5. Perioden, deterministische Prognose und Partnerfreigaben
6. Verschlüsselung einer Notiz im Datenbankzustand
7. Tipp-/Care-Filter ohne Rückschluss auf gesperrte Symptome
8. JSON-Export, Importvorschau und Duplikaterkennung
9. Soft Delete, Offline-Sync-Idempotenz und Versionskonflikt
10. strikte Sync-Payloads und Recovery einer abgelaufenen
    In-Progress-Reservation

Eine Test-Suite ist nicht dasselbe wie ein Testfall; Jest berichtet die genaue
Anzahl bei jedem Lauf.

## Android

```bash
cd apps/android
./gradlew --no-daemon --max-workers=1 testLocalDebugUnitTest
./gradlew --no-daemon --max-workers=1 lintLocalDebug lintProductionDebug
./gradlew --no-daemon --max-workers=1 assembleProductionDebug
./gradlew --no-daemon --max-workers=1 compileProductionDebugAndroidTestKotlin
```

Die JVM-Tests prüfen Server-URL/Userinfo-Sanitizing, Authentik-/Lunara-Header,
rollenabhängige Navigation, Serverwechselregeln, Mutation-Queue und den
garantierten lokalen Wipe. Der Produktions-Debug-Build wird geprüft, weil nur
diese Variante als `artifacts/lunara-debug.apk` ausgeliefert wird; sie erlaubt
kein Klartext-HTTP.

Compose-Instrumentierung für Onboarding, Serverwechsel, Periodenstart,
Partner-Dashboard und Tipp-Nullzustand ist vorhanden und wurde erfolgreich
kompiliert. Sie wurde auf diesem Host **nicht ausgeführt**, weil kein Android-
Emulator/KVM-Gerät bereitstand. Entsprechend sind reale
Keystore-/SQLCipher-/WorkManager-Interaktionen auf einem Gerät noch manuell zu
prüfen.

## OpenAPI-Client

```bash
npm run openapi:generate
npm run openapi:check
cd packages/openapi-client/generated/kotlin
./gradlew --no-daemon compileKotlin
```

`openapi:check` vergleicht die generierte Spezifikation, Generatorparameter und
den Kotlin-Ausgabebaum mit gespeicherten Hashes. Der erzeugte Client ist ein
separates Vertragsartefakt; die Android-App nutzt ihre eigene typisierte
Retrofit-Schicht und bindet diesen Client in Version 1 nicht direkt ein.

## Container

Eine gefahrlose statische Compose-Prüfung und ein Image-Build lauten:

```bash
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example build api
docker image inspect lunara-api:1.0.0
```

`.env.example` enthält nur Platzhalter und eignet sich **nicht** zum Starten
einer Instanz. `docker compose up` wird ausschließlich mit einer außerhalb von
Git erzeugten echten `.env` und gemäß `DEPLOYMENT.md` verwendet.

## Gateway und Betrieb

Die beiden additiven Infrastrukturhelfer besitzen einen read-only
`--check-only`-Modus. Vor jeder Live-Änderung werden damit Zielcontainer,
Versionen, Schema und Namenskonflikte geprüft. Der öffentliche Gatewaytest liest
die Zugangsdaten aus einer lokalen Datei, ohne sie in Prozessargumente oder
Ausgabe zu schreiben:

```bash
sudo /docker/lunara/scripts/configure-authentik.sh --check
sudo /docker/lunara/scripts/configure-pangolin.sh --check
sudo /docker/lunara/scripts/verify-gateway.sh /docker/lunara/gateway-a.url
```

Ein produktiver Restore wird nicht als Test verwendet. Er ist destruktiv und
erfordert den dokumentierten expliziten Bestätigungsschalter; ein Restore-Test
gehört in eine separat benannte Instanz mit Kopien von Dump und Master-Key.
