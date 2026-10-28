# Tests

Diese Datei trennt tatsächlich ausgeführte Prüfungen von nur vorhandenen
Testquellen. Der verifizierte aktuelle Prüfstand wird in `FINAL_REPORT.md`
ausgewiesen; laufende oder noch nicht ausgewertete Prüfungen zählen nicht als
bestanden. Die Matrix unten ist als historische 1.1.0-Prüfung erhalten.

## Zusätzliche Prüfungen für 1.3.2

`CalendarColorsTest` und `CalendarDayKindTest` prüfen die semantischen Farben,
Textkontraste und Kategoriepriorität. `CalendarColorsComposeTest` rendert beide
Themes mit normaler und großer Schrift und kontrolliert Textlayout, echte
Füllfarben sowie die Legende. Alle Fixtures verwenden synthetische Daten.

Im Web prüfen `calendar-presentation.test.ts` und `presentation.test.ts` dieselben
Prognosegrenzen, Legendentexte, Kategorieprioritäten und Theme-Farben.
Bei einer visuellen Prüfung müssen **alle sieben Spalten innerhalb ihrer
eigenen Rasterplätze** liegen: Eine scrollbare Kalenderkarte kann abgeschnittene
Zellen verbergen, obwohl die Webseite selbst keinen horizontalen Überlauf hat.
Zusätzlich Start, Kalender und Einstellungen bei 320px, Handy- und Desktopbreite
in beiden Themes prüfen; Navigationsbeschriftungen nicht mitten im Wort trennen.

## Zusätzliche Prüfungen für 1.3.1

`CalendarStatusPolicyTest` und `CalendarSharingComposeTest` prüfen tatsächliche
Perioden, getrennte Freigaben, Schätzungsstatus, Ladefehler und Widerruf.
`PeriodExportFormatTest` prüft CSV/JSON, Unicode, offene Enddaten, Grenzen,
Mehrdeutigkeiten, Dubletten und Überschneidungen. Die Geräteklassen
`PeriodExportComposeTest` und `PeriodImportAtomicDeviceTest` prüfen die
Bestätigung und atomare Room-Transaktionen einschließlich Rücknahme bei
Kontowechsel oder fehlgeschlagener Queue-Erstellung.

Web-Regressionsfälle liegen in `period-transfer.test.ts`,
`session-boundary.test.ts`, `partner-calendar.test.ts` und
`fertility-presentation.test.ts`. Der reale PostgreSQL-Kernablauf prüft
Vorschau ohne Schreibzugriff, atomare Konfliktablehnung, wiederholten Import
ohne Duplikate und abgewiesenen Partnerzugriff. `SetupScreenshotsTest`
erzeugt ausschließlich synthetische Einrichtungsbilder.

```bash
npm test
npm run lint
npm run build
npm run openapi:check
cd apps/android
./gradlew testLocalDebugUnitTest lintProductionRelease
./gradlew assembleProductionDebug assembleProductionDebugAndroidTest
```

Gerätetests benötigen einen isolierten Emulator; niemals echte Gesundheitsdaten
in Testfixtures oder Screenshots übernehmen. Die Backend-Integration benötigt
eine ausschließlich für Tests eingerichtete PostgreSQL-Datenbank. Der
Sitzungspersistenztest verweigert absichtlich andere Datenbankziele.

## Zusätzliche Prüfungen für 1.2.1

`RedesignComposeTest` prüft den Ausblick im ersten sichtbaren Bereich der
Partneransicht und die darunter per Scroll erreichbare Tippkarte. Ein eigener
Fall prüft die private Platzhalteransicht ohne freigegebene Prognose. Beide
werden auf Deutsch und Englisch als echte Compose-Ansichten aufgenommen.

`TipSourcePolicyTest` und `tip-source.test.ts` prüfen die exakte öffentliche
Quellenliste gegen HTTP, ähnliche Fremddomains, URL-Zugangsdaten sowie private
Query-/Fragmentanhänge. Quellen werden nicht im Hintergrund angefragt. Die
Browser-Sichtprüfung misst zusätzlich die echte Reihenfolge im DOM und die
vertikalen Kartenpositionen zwischen 320 und 1280 Pixeln. Laufende Prüfungen
werden erst nach vollständigem Ergebnis in `FINAL_REPORT.md` gezählt.

## Zusätzliche Prüfungen für 1.2.0

Die folgenden Abschnitte beschreiben die vorhandenen Testquellen und ihren
Prüfumfang, nicht das Ergebnis eines noch laufenden Builds oder Gerätetests.

`SettingsSectionsTest` prüft die verfügbaren Einstellungskategorien für
Tracker, Partner und Solomodus: Trackerbereiche bleiben rollenbeschränkt,
Solo behält Konto-/Backup- und Zykluseinstellungen ohne Serververknüpfung.
`JournalPredictionPolicyTest` prüft die Anzeige eines gültigen Schätzfensters
und das Ausblenden bei pausierter Prognose, fehlenden oder ungültigen Daten
sowie umgekehrter Datumsreihenfolge.

`RedesignComposeTest` verwendet ausschließlich synthetische Daten. Die
Instrumentierungsfälle prüfen unter anderem die erreichbare Periodenaktion,
den Ausblick im Dunkelmodus, Partnerverbindung und Tippkarte, Wischen im
Kalender, erfasste Verlaufslängen, Öffnen und Verlassen einer
Einstellungskategorie sowie die sichtbare Solooption beim Einstieg. Zusätzliche
Fälle verwenden Schriftfaktor 1,6 und prüfen die Periodenaktion und die
aufklappbare Begründung der Tippauswahl. Screenshots entstehen aus den echten
Compose-Oberflächen, nicht aus echten Konten. Das ersetzt weder eine
vollständige Geräte-/Sprachmatrix noch einen TalkBack- oder
Barrierefreiheitsaudit.

Die API-Testquellen ergänzen die 71 stabilen IDs und vollständigen DE/EN-Texte
um konkrete Handlungszuordnungen für Vorlieben, unveränderte persönliche
Wünsche, Kategorieabwechslung ohne Verlust zulässiger IDs und Ausschlüsse vor
dem Ranking. Widerrufene Symptomfreigaben und private Tage dürfen abhängige
Tipps nicht ausgeben; private Tage schließen auch feldabhängige eigene
Wünsche ohne Phasen-/Symptomkennzeichnung aus. Notizen, numerische Schmerzen,
Stimmung, Energie und Schlaf werden nicht zu geteilten Symptomschlüsseln
umgedeutet. Textmuster mit positiven und negativen Kontrollen sind begrenzte
Regressionstests, kein Nachweis medizinischer oder redaktioneller Qualität.

### Manueller Geräteworkflow auf einem gehosteten Runner

Auf GitHub unter **Actions → Android CI → Run workflow** den zu prüfenden
Branch wählen und **ui_only** aktivieren. Der manuelle
`workflow_dispatch`-Lauf startet dann nur den Job `device-validation` aus
`.github/workflows/android.yml`; die separaten Lint-, JVM-Test- und
Release-Build-Jobs laufen dabei nicht mit.

Der Gerätejob baut Production-Debug- und Instrumentierungs-APK, startet einen
API-36-Emulator auf einem kurzlebigen GitHub-gehosteten Runner und führt
`scripts/android-ui-ci.sh` aus. Das Skript prüft zunächst die gesamte
Instrumentierung mit deutscher App-Sprache, danach bei Erfolg
`RedesignComposeTest` nochmals auf Englisch. Protokolle und synthetische
Screenshots liegen im Workflow-Artefakt `android-device-validation`;
`scripts/verify-android-instrumentation.mjs` prüft vollständig gemeldete
Testergebnisse statt allein den Rückgabecode von `adb` zu akzeptieren.
Screenshots müssen zusätzlich angesehen werden.

Keinen lokalen Emulator auf dem produktiven Heimserver starten. Das
Geräteskript verweigert die Ausführung außerhalb eines GitHub-gehosteten
Runners. Weder echte Konten noch produktive Datenbanken sind Testfixtures.
Ein erfolgreicher Emulatorlauf würde noch keinen physischen Kameratest,
interaktiven Authentik-Browserlogin oder Test auf allen Herstellergeräten
belegen. Lauf-ID, Commit, Ergebnis und manuelle Sichtprüfung gehören in den
finalen Prüfbericht; hier wird kein ausstehender Lauf als bestanden geführt.

## Zusätzliche Prüfungen für 1.1.6

`RelationshipPolicyTest` prüft unbekannte/verbundene/veraltete Zustände,
keine Selbstverknüpfung und Cache-Isolation zwischen Servern und Konten.
`ConnectionPresentationTest` prüft Statuspriorität, getrennte Versionen,
inklusive Startfenster und sichere PayPal.me-URLs mit negativen Kontrollen.
`PersonalTipDraftPolicyTest` prüft unveränderte persönliche Texte und Grenzen.
`ConnectionQualityComposeTest` prüft die entsprechenden echten Compose-
Oberflächen einschließlich bewusster Freigabe eines eigenen Wunsches.

Neue API-Tests prüfen die Rotation der zulässigen Tippmenge, persönliche
Wünsche vor allgemeinen Tipps, konkrete Wärme- statt Berührungspräferenz,
Varianten ohne Ausblenden und sichere Aktualisierung vorhandener Katalogtexte.
Die PostgreSQL-Suite prüft zusätzlich Variantenvalidierung und den Abruf der
echten Beziehung als Partner. Die klinische Wirksamkeit der Tipps und eine
prospektive Vorhersagegenauigkeit sind damit ausdrücklich nicht bewiesen.

## Zusätzliche Prüfungen für 1.1.5

`services/api/test/refresh-replay.e2e-spec.ts` prüft gegen eine echte,
isolierte PostgreSQL-Instanz Antwortverlust, parallele identische Refreshes,
Widerruf der Ersatzsitzung, späte bekannte Wiederholungen, unbekannte
Tokenwiederverwendung, Gerätefamilien-Isolation und parallele Abmeldungen.
Der End-to-End-Fall wiederholt dieselbe Offline-Mutation und prüft exakt
einen gespeicherten und für den Partner freigegebenen Periodeneintrag.

Diese zusätzliche Suite verlangt ausdrücklich `NODE_ENV=test` und die
Standard-E2E-Datenbank `lunara_test` oder eine dedizierte
`lunara_sync115_test`-Datenbank mit optionalem numerischem Laufsuffix.
Der E2E-Runner baut die API vor dem integrierten Admin-CLI-Test.
Die Suite erstellt und entfernt ausschließlich eigene
synthetische Fixtures. Keine produktive Datenbank als Testziel verwenden.

Neue Android-Policytests prüfen Identitäts-/Servergrenzen, Abbruchzustände,
Anmeldefehler gegenüber Netzfehlern, erhaltene Mutation-IDs, veraltete
Antworten nach einer Tokenrotation sowie Partneraktualisierung ohne Tipps.
`SessionSecretsTest` prüft die über Neustarts stabile Refresh-Anfrage-ID.
Es handelt sich um JVM-/Robolectric-Tests, nicht um einen bestätigten Lauf auf
dem tatsächlichen Telefon. Instrumentierungsquellen werden kompiliert;
Browserlogin, Energiesparmodus und Zwei-Geräte-Synchronisierung müssen auch
mit den installierten Geräten geprüft werden.

Nach erfolgreicher API-Aktualisierung: beide APKs aktualisieren, eine eventuell
angezeigte alte Anmeldung einmalig im selben Konto erneuern, einen Eintrag
offline anlegen, Netzwerk einschalten und den freigegebenen Partnerstand
prüfen. Nicht abmelden, solange noch lokale Einträge hochgeladen werden sollen.

## Verifizierte 1.1.0-Matrix

### Backend

- **26/26 Jest-Suites, 177/177 Unit-Tests:** erfolgreich
- TypeScript-/Nest-Build: erfolgreich
- ESLint der API-Quellen: erfolgreich
- Prisma-Generierung und Schema-Validierung: erfolgreich
- OpenAPI-Snapshot und generierter Kotlin-Baum: reproduzierbar geprüft
- echte temporäre PostgreSQL-18-Instanz: alle **acht Migrationen** angewandt
- Core-End-to-End: **1/1 Suite, 1/1 durchgehender Test** erfolgreich

Der erste 1.1.0-E2E-Versuch fiel bewusst an der neuen stale/fail-closed-
Prognosegrenze auf. Der Testanker wurde auf eine aktuelle Beobachtung
korrigiert; anschließend lief derselbe vollständige Core-Flow erfolgreich.
Dieser erste Lauf wird nicht als Erfolg gezählt.

Die Backend-Suites prüfen insbesondere:

- Bootstrap und dauerhaft deaktivierten Setupmodus;
- Argon2id, Loginlimits, generische Fehler, Passwortwechsel,
  Refresh-Rotation und Replay-Widerruf;
- OIDC-Issuer/Audience/Subject/`iat`/`exp`/`azp`, JWKS,
  Algorithmusbegrenzung, Linking und externe Identität bei Refresh;
- Rollen und Cross-Relationship-/IDOR-Isolation;
- Einladung, Partnerbeitritt und serverseitige Freigabefilter;
- 71 Tipptexte, Care Preferences und fail-closed Symptomfilter;
- `lunara-robust-v2`, robuste Ausreißerbehandlung, Stale-Grenze,
  Sondermodi und optionales Fertilitätsfenster;
- Periodenüberschneidung, Import, Export, Soft Deletes, Cursor-Sync,
  Idempotenz, Konflikte und Recovery alter Receipts;
- öffentlichen cachefreien Meta-Vertrag, Asset Links und sichere
  SPA-Routenabgrenzung.

### PWA

- TypeScript-Lint/Typecheck: erfolgreich
- Vite-/PWA-Produktionsbuild: erfolgreich
- **11 Testdateien, 39 Vitest-Tests:** erfolgreich
- `npm audit --omit=dev` und vollständiges `npm audit`: jeweils 0 gemeldete
  Schwachstellen

Geprüft werden Kalenderdarstellung, lokaler Prognosevektor, Offline-Queue,
kontospezifische Cache-/Queue-Isolation, verspätete Refresh-/Sync-Antworten,
Löschen gegen parallele Vault-Schreibvorgänge, Dual-Header same-origin,
portables Backupformat und passphrasenverschlüsselter Solo-Vault. Der Service
Worker schließt API-, Health-, Ready-, OpenAPI-, Authentik-Outpost- und
`.well-known`-Pfade vom Cache und vom Navigation-Fallback aus.

Backend und PWA ergeben damit **216 erfolgreiche Unit-/Komponententests**
(177 + 39); der separate PostgreSQL-Core-E2E-Test ist darin nicht enthalten.

Das unmittelbar vor dem produktiven 1.1.0-Rollout erstellte Backup wurde nicht
nur katalogisiert: `database.dump` wurde in einem isolierten temporären
PostgreSQL-18-Container vollständig mit `pg_restore --exit-on-error`
wiederhergestellt. Sieben gesicherte Migrationen, ein Benutzer, drei Perioden
und ein Tageslog waren vorhanden; der Container wurde danach automatisch
entfernt. Die neue achte Migration wurde anschließend auf Live erfolgreich
ausgeführt.

### Android

- `testLocalDebugUnitTest`: **36 Suites, 174 Tests**, 0 Fehler
- `testProductionDebugUnitTest`: **36 Suites, 174 Tests**, 0 Fehler
- insgesamt **348 Unit-Test-Ausführungen** über beide Flavors
- `lintLocalDebug`: **0 Fehler, 64 Warnungen**
- `lintProductionDebug`: **0 Fehler, 63 Warnungen**
- beide Lintläufe enthielten zusätzlich je **1 Information**, aber keine
  Fehler
- `compileProductionDebugAndroidTestKotlin`: erfolgreich
- Production-Instrumentierungsquellen: kompiliert, aber **nicht auf einem
  Emulator oder Gerät ausgeführt**
- Production-Debug-, signierte/unsignierte Release-APK sowie
  signiertes/unsigniertes Production-AAB: gebaut
- Manifest, Version/SDK-Stufen, ZIP-/Bundle-Struktur und erwartete
  Signaturzustände: geprüft

Die Android-JVM-Suites decken unter anderem ab:

- wirklich leere Serveradresse und URL-/Origin-Validierung;
- Direct-, technischer Kompatibilitäts- und OIDC-Dual-Header;
- Authentik-PKCE-/Refreshzustände sowie feste Manifesttrennung von Production-
  App-Link und ausschließlich lokalem Custom Scheme;
- lokale Identität und vollständige Netz-/Syncsperre im Solomodus;
- SQLCipher-/Keystore-nahe Policylogik, sichere Sessionlöschung und
  DataStore-Isolation;
- portable Backup-Kryptografie, Schema-/Bereichsvalidierung und
  Import-Journal-Recovery;
- Room-Mutation Queue, Idempotenz, Backoff und automatische Synchronisierung;
- rollenabhängige Navigation, Freigaben und Partnercache-Bereinigung;
- wischbaren Kalender, Zukunftsmonate, tatsächliche/geschätzte Tage,
  Zwei-Tipp-Erfassung, Korrektur, offene Periode, Löschung und
  Überschneidungsschutz;
- lokale Prognose und Fertilitätsgrenzen;
- Benachrichtigungszeit, Ruhezeit, Deduplizierung, DST und neutrale Vorschau.

### Container

- Multi-Stage-Build mit API und PWA: erfolgreich
- `npm ci`, API-Build und PWA-Build innerhalb der frischen Buildstufe:
  erfolgreich
- Containerkonfiguration für Non-root UID/GID `10001:10001`, read-only Root,
  Capability-Drop, `no-new-privileges`, Healthcheck und fehlende Hostports
  geprüft
- Direct-Compose-Konfiguration über `scripts/compose.sh config --quiet`:
  erfolgreich

### App Link und Signierung

- Der Production-App-Link für `lunara.3li361.de` wurde mit dem zugehörigen
  Release-Zertifikat verifiziert.
- Die signierte Production-App verwendet den festen verifizierten Publisher-
  App-Link unabhängig von der Self-hosting-Domain. Production-Authentik
  registriert keinen Custom-Scheme-/Debug-Redirect.
- Das Custom Scheme ist nur im Android-`local`-Flavor für einen getrennten
  Entwicklungsprovider mit eigener client_id vorhanden. Production und
  Production-Debug enthalten keinen solchen Intent-Filter.
- Der Upload-Key liegt ausschließlich serverlokal. Gradle erhielt ihn über
  `LUNARA_UPLOAD_KEYSTORE`, `LUNARA_UPLOAD_KEYSTORE_PASSWORD`,
  `LUNARA_UPLOAD_KEY_ALIAS` und `LUNARA_UPLOAD_KEY_PASSWORD`.
- Die signierte Release-APK und das Upload-AAB wurden als signiert erkannt;
  die beiden `-unsigned`-Artefakte erwartungsgemäß als unsigniert.
- `sha256sum -c artifacts/SHA256SUMS` bestätigte alle fünf finalen
  Release-1.1.0-Artefakte.
- Für Google Play ist die App-Link-Prüfung nach Aktivierung von Play App
  Signing mit dem separaten Play-App-Signing-Zertifikat zu wiederholen.

## Nicht als bestanden behauptet (historischer Prüfstand 1.1.0)

- Instrumentierte Compose-/Room-/WorkManager-Tests wurden nicht auf einem
  Gerät ausgeführt.
- Eine vollständige reale Geräte-/OS-Matrix für App Link,
  getrennten lokalen Entwicklungs-OIDC, Keystore, SQLCipher, WorkManager, TalkBack,
  Schriftgrößen und reduzierte Bewegung fehlt.
- PWA-Installation, Offline-Upgrade und Browserhintergrundverhalten wurden
  nicht vollständig auf realem iOS/iPadOS/Safari geprüft.
- Screenshot-Schutz und Browserbenachrichtigungen sind in der PWA nicht
  ausführbar. Importierte Privacy-/Notification-Vorgaben bleiben sicher im
  Vault erhalten und die Web-App weist auf ihre Nichtanwendbarkeit hin.
- Ein neuer PWA-Prozess verlangt die Solo-Vault-Passphrase erneut; ohne sie ist
  der lokale Vault nicht wiederherstellbar.
- Ein produktiver Datenbank-Restore wurde aus Sicherheitsgründen nicht als
  Test verwendet; er gehört in eine isolierte Wiederherstellungsinstanz.
- Der produktive 1.1.0-Live-Rollout wird erst nach Backup, Migration,
  Authentik-/OIDC-Anwendung, Identitätsmigration und finalem Healthcheck als
  bestanden dokumentiert.

## Reproduktionsbefehle

### API und PWA

```bash
npm ci
npm run prisma:generate
npm run prisma:validate
npm run lint
npm test
npm run build
npm run openapi:check
./scripts/e2e.sh
```

### Android

```bash
cd apps/android
./gradlew --no-daemon --max-workers=1 \
  testLocalDebugUnitTest testProductionDebugUnitTest
./gradlew --no-daemon --max-workers=1 \
  lintLocalDebug lintProductionDebug
./gradlew --no-daemon --max-workers=1 \
  compileProductionDebugAndroidTestKotlin
./gradlew --no-daemon --max-workers=1 assembleProductionDebug
./gradlew --no-daemon --max-workers=1 assembleProductionRelease
./gradlew --no-daemon --max-workers=1 bundleProductionRelease
```

Für einen signierten Release müssen alle vier `LUNARA_UPLOAD_*`-Variablen aus
einer geschützten Umgebung gesetzt sein. Ohne alle vier entsteht bewusst ein
unsignierter Build; eine Teilmenge bricht ab.

### Compose

```bash
./scripts/compose.sh config --quiet
./scripts/compose.sh build api
./scripts/compose.sh run --rm migrate
./scripts/compose.sh up -d
./scripts/compose.sh ps --all
```

`.env.example` enthält nur Platzhalter und darf nicht als produktive
Konfiguration verwendet werden.

### Artefakte

```bash
cd artifacts
sha256sum -c SHA256SUMS
unzip -t lunara-release.apk
unzip -t lunara-play-upload.aab
```

Zusätzlich APKs mit `apksigner verify --verbose --print-certs` und AABs mit
`jarsigner -verify -strict` prüfen. Private Zertifikatsdetails oder Keystore-
Passwörter gehören nicht in die Dokumentation.

## Interpretation

Eine vorhandene APK/AAB-Datei beweist keinen erfolgreichen Build. Maßgeblich
sind der ausgeführte Befehl, dessen Exitstatus, die nachfolgende Struktur-
und Signaturprüfung sowie die Prüfsumme des unveränderten Artefakts. Ebenso ist
das erfolgreiche Kompilieren von Android-Instrumentierung nicht deren
Ausführung.
# Prüfstand 1.1.4 – 8. September 2026

- API: 28 Suites, 198 Tests; PWA: 13 Dateien, 50 Tests.
- Zwei End-to-End-Szenarien auf frischem, isoliertem PostgreSQL 18 nach allen
  acht Migrationen: Kernablauf und bestätigter Wechsel eines bestehenden
  leeren Kontos zum Partner. Produktive Konten wurden nicht als Testkonten benutzt.
- Android Local-Debug und Production-Debug: je 42 Suites, 205 Tests; beide
  Lint-Varianten ohne Fehler (63 beziehungsweise 62 Warnungen).
- Instrumentierungscode für Production-Debug kompiliert. Keine ausgeführten
  Emulator- oder Gerätetests; insbesondere der interaktive Authentik-Browser
  und die physische Kamera sind hier nicht durchgetestet.
- Docker-API-/PWA-Build und OpenAPI-Client-Prüfung erfolgreich; Live-Health und
  Readiness gesund. Nur Meta, Asset-Links und die statische `/invite`-Seite
  sind am äußeren Proxy anonym erreichbar.

Verwendeter Android-Builder: Temurin JDK 21.0.12, offizielles SDK-Paket
`platforms;android-37.0` Revision 2, Build-Tools 36.0.0, Gradle 9.3.1.
Ein alter unvollständiger Gradle-Transform wurde nach Prüfung in den privaten
Backupordner verschoben, nicht gelöscht. Das Host-Java oder Host-SDK musste
nicht installiert werden.

Die Tests prüfen auch: explizite Annahme, wiederholte Annahme ohne Duplikat,
belegte Partnerrolle, Soft Deletes, veraltete Trackerrechte bei Import und
Einstellungen, Wiederaufnahme nach AppAuth, Abbruch ohne alte Einladung,
Scopewechsel ohne ausstehende Offline-Mutationen und verspätete Antworten
nach einem lokalen Abmelden.

Die folgenden Abschnitte beschreiben die allgemeinen Testbefehle und den
historischen Prüfstand.
