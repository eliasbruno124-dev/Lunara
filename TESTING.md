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

Die 13 Unit-Suites prüfen insbesondere Prognosealgorithmus,
Freigabeprojektion, Tippfilter, App-Konfiguration, Symptomkatalog und sichere
Admin-CLI-Argumente, den billigen Einladungscode-Preflight, die
zeitzonensichere, fail-closed Partnerphase sowie Passwortwechsel und
Sitzungswiderruf.

Finaler Lauf für Version 1.0.2: **13/13 Suites und 38/38 Tests erfolgreich**.

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
3. Passwortwechsel mit Prüfung des aktuellen Passworts, Widerruf anderer
   Lunara-Sitzungen, Erhalt der aktuellen Tokenfamilie und Audit-Ereignissen
4. Tracker, Einmal-Einladung, Partner und Rollenprüfung
5. Fremdhaushalts-IDOR bei Perioden, Pull-/Push-Sync und eigenen Symptomen
6. Perioden, deterministische Prognose und Partnerfreigaben
7. Verschlüsselung einer Notiz im Datenbankzustand
8. Tipp-/Care-Filter ohne Rückschluss auf gesperrte Symptome
9. JSON-Export, Importvorschau und Erkennung bereits vorhandener aktiver
   Datensätze
10. Soft Delete, Offline-Sync-Idempotenz und Versionskonflikt
11. strikte Sync-Payloads und Recovery einer abgelaufenen
    In-Progress-Reservation

Eine Test-Suite ist nicht dasselbe wie ein Testfall; Jest berichtet die genaue
Anzahl bei jedem Lauf.

Der finale PostgreSQL-18-Lauf für Version 1.0.2 bestand mit **1/1 Suite und
1/1 zusammenhängendem E2E-Test**. Das Test-Timeout ist bewusst auf fünf Minuten
gesetzt, weil Argon2id und die gesamte Kernstrecke auch unter den festen
CPU-/RAM-Grenzen des Testcontainers zuverlässig abgeschlossen werden sollen.

Gezielte Unit-/E2E-Regressionen decken unter anderem strikte Sync-Payloads,
Recovery einer abgelaufenen Idempotenz-Reservation, Haushaltsisolation im
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

Die JVM-Tests prüfen Server-URL/Userinfo-Sanitizing, Authentik-/Lunara-Header,
rollenabhängige Navigation, Serverwechselregeln, Mutation-Queue und den
garantierten lokalen Wipe. Hinzu kommen servergleiche Bootstrap-/Formregeln,
Passwortwechselregeln, Sync-Authentifizierungsentscheidung, Serialisierung
paralleler Worker, Credential-Generationsschutz, alte Cookie-Generationen,
terminale Queue-Fehler mit manuellem Neuversuch und ein fail-closed Schutz gegen
sitzungsfremde UI-Caches. Ein autoritatives Setup-`409` schaltet die Oberfläche
trotz einer unerwartet alten Metaantwort unmittelbar auf Anmeldung um.
Der Produktions-Debug-Build wird geprüft, weil nur diese Variante als
`artifacts/lunara-debug.apk` ausgeliefert wird; sie erlaubt kein Klartext-HTTP.

Finaler Lauf für Version 1.0.2: **35/35 JVM-Tests**, Local-Lint,
Production-Lint, Kompilierung der Production-Instrumentierungstests und
`assembleProductionDebug` erfolgreich. Die erzeugte Production-Konfiguration
enthält eine leere `DEFAULT_API_URL`; die Serveradresse wird beim ersten Start
bewusst eingegeben.

Compose-Instrumentierung für Onboarding, Serverwechsel, Periodenstart,
Partner-Dashboard, Tipp-Nullzustand und das lokale Ausfüllen des
Passwortwechseldialogs ist vorhanden. Sie wurde auf diesem Host **nicht
ausgeführt**, weil kein Android-Emulator/KVM-Gerät bereitstand. Entsprechend
sind reale
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
den Kotlin-Ausgabebaum mit gespeicherten Hashes. Spezifikation, Client und
Metadaten wurden für Version 1.0.2 reproduzierbar neu erzeugt und erfolgreich
geprüft. Der Kotlin-Referenzclient wurde anschließend mit JDK 21 erfolgreich
kompiliert. Der erzeugte Client ist ein
separates Vertragsartefakt; die Android-App nutzt ihre eigene typisierte
Retrofit-Schicht und bindet diesen Client in Version 1 nicht direkt ein.

## Container

Eine gefahrlose statische Compose-Prüfung und ein Image-Build lauten:

```bash
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example build api
docker image inspect lunara-api:1.0.2
```

`.env.example` enthält nur Platzhalter und eignet sich **nicht** zum Starten
einer Instanz. `docker compose up` wird ausschließlich mit einer außerhalb von
Git erzeugten echten `.env` und gemäß `DEPLOYMENT.md` verwendet.

Auf dem Zielserver wurde der Stack anschließend unter `/docker/lunara`
tatsächlich gestartet und auf Version 1.0.2 aktualisiert: API und PostgreSQL
sind gesund, drei Migrationen sind angewendet, es existieren keine
veröffentlichten Host-Ports und die API läuft als UID/GID `10001:10001` mit
read-only Root-Dateisystem und vollständigem Capability-Drop. Health,
Readiness, die nicht cachebare Metaantwort und der geschützte Passwort-Endpunkt
wurden danach live geprüft.

## Gateway und Betrieb

Die beiden additiven Infrastrukturhelfer besitzen einen read-only
`--check`-Modus. Vor jeder Live-Änderung werden damit Zielcontainer,
Versionen, Schema und Namenskonflikte geprüft. Der öffentliche Gatewaytest liest
die Zugangsdaten aus einer lokalen Datei, ohne sie in Prozessargumente oder
Ausgabe zu schreiben:

```bash
sudo /docker/lunara/scripts/configure-authentik.sh --check
sudo /docker/lunara/scripts/configure-pangolin.sh --check
sudo /docker/lunara/scripts/verify-gateway.sh /docker/lunara/gateway-a.url
```

Authentik und Pangolin wurden auf dem Zielserver additiv eingerichtet. Beide
getrennten Gateway-Konten bestanden den lokalen Pre-DNS-Test bis zur gültigen
Lunara-Metaantwort; ein Aufruf ohne Anmeldung antwortete mit HTTP 302. Dabei
wurde kein bestehender Dienst einschließlich Emby neu gestartet.

Ein produktiver Restore wird nicht als Test verwendet. Er ist destruktiv und
erfordert den dokumentierten expliziten Bestätigungsschalter; ein Restore-Test
gehört in eine separat benannte Instanz mit Kopien von Dump und Master-Key.
Ein echtes Live-Backup wurde jedoch erstellt, die Archivprüfsummen wurden
geprüft und `pg_restore --list` konnte den Dump vollständig lesen.
