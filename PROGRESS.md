# Lunara – Fortschritt

Stand: 16. August 2026, Android 1.0.8 und Server 1.0.7

## Android-Buildkorrektur 1.0.8

- [x] targetSdk auf den tatsächlich im stabilen SDK-Kanal verfügbaren
  API-Level 36 korrigiert. compileSdk bleibt wegen der gepinnten aktuellen
  AndroidX-Versionen auf API 37; Android wurde auf VersionCode 9/VersionName
  1.0.8 angehoben und bleibt mit Server/API 1.0.7 kompatibel.
- [x] GitHub-Runner installieren das benötigte Preview-Compile-SDK explizit;
  PostgreSQL-E2E läuft mit der gepinnten Node-Version des Runners statt in
  einem internen Container ohne erreichbaren Prisma-Engine-Download. Der
  Testport bleibt ausschließlich an Loopback gebunden und ist überschreibbar.
- [x] Der Gitleaks-Fehlalarm ist über seinen exakten Finding-Fingerprint
  begrenzt; alle übrigen Regeln, Commits und Dateien bleiben im Historien-Scan
  aktiv.
- [x] Android 1.0.8 lokal real gebaut und geprüft: beide Varianten jeweils
  27 Suites/132 Tests ohne Fehler, Local-Lint 0 Fehler/39 Warnungen,
  Production-Lint 0 Fehler/38 Warnungen und Instrumentierung kompiliert.
- [x] Installierbare Debug-APK (24.531.309 Bytes, SHA-256
  `66098c59fadc58c84d463e68447b325435aa84d45e13c2bb27761283b958191d`)
  sowie unsigned Release-APK (19.841.583 Bytes, SHA-256
  `caa4575e6e782cdeb2639b83a17d475b0f319a6e2a9fd260a30583daef8749f1`)
  gebaut; ZIP, Manifest, SDK-Stufen und Signaturstatus geprüft.

## Verifizierter Release 1.0.7

- [x] Rückwirkende Periodenerfassung direkt im Monatskalender mit Start- und
  optionalem Enddatum; vorhandene Zeiträume antippbar, korrigierbar und nach
  Bestätigung löschbar. Partnerkalender bleibt ohne Schreibaktionen.
- [x] Tracker-Hauptaktion wechselt aus lokalem Room-Zustand zwischen „Periode
  begonnen“, „Periode heute beenden“ und der bestätigten Endzustandsanzeige.
  Speicherung, Pending-Zustand und erfolgreiche Synchronisierung bleiben
  sichtbar; Dialoge schließen erst nach tatsächlich erfolgreicher lokaler
  Transaktion.
- [x] Historischer Zeitraum wird atomar als genau ein lokaler Datensatz und
  genau eine idempotente Mutation gespeichert. Bestehender automatischer Sync
  nach Mutation/Anmeldung, im Vordergrund und über WorkManager bleibt aktiv.
- [x] Dialogentwürfe und Pending-Zustand rotationsfest; Mehrfach-Submit,
  Dismiss und Bearbeitung während Create/Update/Delete gesperrt. Nach Fehlern
  bleibt der Entwurf erhalten, nach Erfolg schließt er erst mit der Room-
  Bestätigung.
- [x] Clientseitige Überschneidungsprüfung und konstante Kalender-
  Speichernutzung ergänzt; sehr alte offene Bereiche erzeugen keine
  materialisierte Liste hunderttausender Tage.
- [x] Serverweite Perioden-Invariante ergänzt: Advisory-Lock pro Tracker,
  409-Konfliktantwort sowie partielle PostgreSQL-GiST-Exclusion-Constraint für
  aktive inklusive Datumsbereiche. Direkte API und `/sync` nutzen denselben
  geschützten Servicepfad.
- [x] Sync-Receipt, Perioden-/Tageslogmutation, SyncChange und notwendige
  Prognoseneuberechnung atomar in einer PostgreSQL-Transaktion; sichere
  Recovery alter Pending-Receipts, race-sichere Wiederverwendung abgelaufener
  Keys und importseitige Überschneidungsprüfung.
- [x] Android final: 27 Suites, 132 Tests, 0 Fehler/0 übersprungen;
  `lintLocalDebug` 0 Fehler/38 Warnungen, Production-Lint 0 Fehler/37
  Warnungen; Production-Instrumentierung kompiliert, mangels Gerät nicht
  ausgeführt.
- [x] Backend final: 20 Suites und 105 Tests erfolgreich, TypeScript-Typecheck,
  vollständiges ESLint, Prisma-Validierung, Nest-/OpenAPI-Build und echtes
  Docker-Image erfolgreich.
- [x] PostgreSQL 18 final: sieben Migrationen auf frischer temporärer
  Tmpfs-Datenbank und 1/1 vollständiger E2E-Kernablauf erfolgreich; der Test
  bestätigt unter anderem Beziehungstrennung, Partnerfreigaben und genau einen
  Datensatz nach wiederholtem Offline-Sync.
- [x] Debug-APK (24.531.305 Bytes) und unsigned Release-APK (19.841.579 Bytes)
  tatsächlich gebaut, ZIP/Manifest/SDK/Version/Signaturstatus und SHA-256
  geprüft.
- [x] Eng benanntes und mit `pg_restore --list` validiertes Live-Backup
  erstellt; Bestandsprüfung ergab keine ungültigen oder überlappenden aktiven
  Perioden. Migration angewendet und ausschließlich die Lunara-API auf 1.0.7
  ersetzt.
- [x] Live verifiziert: Migration/Constraint/Extension vorhanden, API non-root
  und read-only, `/health`, `/ready` und Meta jeweils 200, Version 1.0.7,
  Authentik-Gateway vollständig geschützt. Finale npm-freie Runtime lokal mit
  0 fixbaren HIGH/CRITICAL-Trivy-Funden geprüft. Fremddienste blieben
  unverändert.

## Implementiert

- [x] Eigenständiges Monorepo unter `/root/Lunara`; keine Änderung am Emby-
  Plugin oder an Medien-Diensten.
- [x] NestJS-/Prisma-/PostgreSQL-Backend mit Argon2id, rotierenden
  Refresh-Tokens, Gerätesitzungen, Rollen und strikter Isolation mehrerer
  Beziehungen.
- [x] Persistenter einmaliger Bootstrap, optionale Registrierung beliebig
  vieler voneinander getrennter Tracker-Beziehungen und je Beziehung genau ein
  `TRACKER`-/`PARTNER`-Konto.
- [x] Permanente, einmal verwendbare und widerrufbare Partner-Einladungen mit
  einmaliger Rohcodeausgabe und passwortfreiem QR-Code.
- [x] Perioden, Tageslogs, eigene Symptome, verschlüsselte Notizen,
  deterministische Bereichsprognose, Soft Deletes, Versionskonflikte und
  idempotente Offline-Synchronisierung.
- [x] Automatische Synchronisierung nach Anmeldung und lokaler Mutation, beim
  Öffnen, regelmäßig im Vordergrund und sparsam über WorkManager; begrenzter
  Backoff und manueller Sync bleiben vorhanden.
- [x] Lokale, ausdrücklich aktivierbare Android-Benachrichtigungen für den
  geschätzten Periodenzeitraum, das optional grob geschätzte fruchtbare
  Fenster, Tracker-Eingaben und Partner-Tipps; je Gerät mit Wunschzeit,
  Ruhezeit, Tageslimit und neutraler Sperrbildschirmvorschau.
- [x] Tägliche Erinnerungsarbeit bei jedem Lauf an lokaler Uhrzeit neu geplant;
  verschlüsselte Prognose-/Zustellcaches, Zustell-Commit erst nach Übergabe an
  Android und strikt unterdrückte Partner-Hinweise ohne aktuelle
  freigabefilterte Serverantwort.
- [x] Persönlicher Passwortwechsel mit aktuellem Passwort, Argon2id, Rate
  Limit, Audit und Widerruf aller anderen Lunara-Sitzungen.
- [x] Kontopasswort-Regel zentral auf 8 bis 128 Unicode-Zeichen ohne
  verpflichtende Zeichenklassen vereinheitlicht; bestehende Passwörter bleiben
  mit 1 bis 128 Zeichen verifizierbar. Die unabhängige Exportpassphrase bleibt
  bewusst bei 12 bis 128 Zeichen.
- [x] Unicode-Länge zwischen Android, API und OpenAPI explizit auf Codepoints
  vereinheitlicht und mit Variationsselektoren an Minimum und Maximum getestet.
- [x] Periodenstart nachträglich ändern oder bestätigt löschen; beide Aktionen
  laufen offlinefähig über die automatische Mutation Queue.
- [x] Granulare Partnerfreigaben, Freigabepause, privater Tag und
  serverseitige fail-closed Projektion. Private Tage verbergen Phase,
  tatsächliche Periodendaten und Fertilitätsangaben.
- [x] Nicht freigegebene Notizen werden nicht entschlüsselt; der lokale
  Partner-Cache wird durch autoritative Antworten ersetzt, damit entzogene
  Tipps nicht sichtbar bleiben.
- [x] 71 respektvolle deutsche und englische Partner-Tipps mit Kategorien,
  freigabebewusster Phasen-/Symptomlogik, Feedback, Priorität und Care-
  Preferences-Auswahl.
- [x] Acht Care Preferences direkt in den deutschen und englischen Android-
  Einstellungen pflegbar: Wärme, Nähe, Raum, Snacks, Gespräch,
  Haushaltshilfe, Erinnerungen und keine ungefragten Ratschläge.
- [x] Optionale Fertilitätsschätzung als ausdrückliches Opt-in, erst ab drei
  vollständigen Zyklen, fail-closed in ungeeigneten Sondermodi und mit
  permanentem Hinweis: keine Ovulationsbestätigung und keine Verhütung.
- [x] Getrennte Partnerfreigabe für das geschätzte fruchtbare Fenster;
  Aktivierung nur bei persönlichem Opt-in und atomarer Widerruf beim
  Deaktivieren.
- [x] Android-Quellstand auf 1.0.7/VersionCode 8 angehoben; Serverfeld beim
  ersten Start wirklich leer und ohne fest eingebaute Produktionsdomain.
- [x] Einmalige HTTPS-Userinfo-Eingabe implementiert. Gateway-Credential wird
  von der Basisadresse getrennt, Keystore-gestützt gespeichert und nie in
  Server- oder Einladungs-QRs übernommen.
- [x] Zwei Header-Schichten implementiert: gemeinsames äußeres
  `Authorization: Basic …` für Authentik und persönliches
  `X-Lunara-Authorization: Bearer …` für Lunara. Basic Auth bleibt auf den
  exakten HTTPS-Ursprung begrenzt; Redirects werden nicht verfolgt.
- [x] Authentik-Migrationsskript für vollständigen Domain-Schutz vorbereitet:
  genau eine neutrale Serviceidentität, genau ein nicht ablaufendes
  App-Passwort, leeres Skip-RegEx und keine menschlichen Gateway-Mitglieder.
- [x] Sichere Gateway-Prüfung ergänzt: authentifizierte Meta/OpenAPI-Antworten
  sowie anonyme Sperre von Root, Health, OpenAPI, Meta und Login, ohne Secret in
  Argumenten, temporären Dateien oder Ausgabe.
- [x] Docker-, Migrations-, Backup-/Restore-/Update-, Admin-, Pangolin- und
  Secret-Scan-Werkzeuge sowie deutsche Betriebs-/Sicherheitsdokumentation auf
  das aktuelle Gateway- und Mehrbeziehungsmodell aktualisiert.

## Verifizierte Android-Ergänzung: 1.0.6

- [x] 25 JVM-Suites und 114 Tests erfolgreich; darunter 28 neue
  Benachrichtigungstests für Policy, verschlüsselten Cache, Deduplizierung,
  Ruhezeit, Doze, DST und Zeitzonenwechsel.
- [x] Vollständiger Local-Lint einschließlich Testquellen und separater
  Production-Hauptcode-Lint ohne Error/Fatal; Android-Instrumentation final
  kompiliert, mangels Emulator/Gerät nicht ausgeführt.
- [x] Installierbare Production-Debug-APK 1.0.6 und unsignierte Release-APK
  tatsächlich gebaut; ZIP, Manifest, Version, SDKs, Signaturstatus, Größen und
  SHA-256 geprüft.
- [x] Ressourcenintensive Sammelläufe bei den vereinbarten Speichergrenzen
  kontrolliert beendet und Restschritte einzeln begrenzt abgeschlossen. Keine
  Wiedergabe, kein Host-/Produktivdienst-OOM und kein Restart; Lunara,
  PostgreSQL, Authentik,
  Pangolin, Traefik und Emby blieben während der Überwachung stabil.
- [x] Reines Android-Update: API, Datenbank und produktive Container bleiben
  unverändert auf dem geprüften Serverstand 1.0.5; kein Live-Neustart nötig.
- [x] Android-1.0.6-Quellstand und beide geprüften APKs in den privaten Branch
  gepusht, Draft-PR 1 aktualisiert und privaten Release `v1.0.6` veröffentlicht.

## Vollständig verifizierter Server-/Gesamtrelease: 1.0.5

- [x] Finale Backend-Prüfkette für 1.0.5: Prisma, ESLint, Nest-Build,
  OpenAPI-Reproduzierbarkeit und Audits erfolgreich; 19 Suites und 83 Tests.
- [x] Echter PostgreSQL-18-E2E-Ablauf auf dem finalen 1.0.5-Quellstand:
  1 Suite und 1 durchgehender Test erfolgreich.
- [x] Finale Android-Prüfkette: 20 Suites und 86 JVM-Tests, beide Lints ohne
  Error/Fatal, Instrumentierung kompiliert und Production-Debug gebaut.
- [x] Installierbare Debug-APK 1.0.5 sowie unsignierte Release-APK unter
  `artifacts/` abgelegt; ZIP, Manifest, Signaturstatus, Größen und SHA-256
  geprüft.
- [x] API-Image `lunara-api:1.0.5` gebaut und Non-Root-/Read-only-Eigenschaften
  geprüft.
- [x] Eng benanntes, validiertes Live-Datenbank- und Konfigurationsbackup vor
  dem Update angelegt.
- [x] Nur die Lunara-API auf 1.0.5 aktualisiert; Health, Readiness, Version,
  Migrationen und unveränderte Fremdcontainer geprüft.
- [x] Authentik nach Backup auf vollständigen Domain-Schutz mit genau einem
  technischen App-Zugang migriert; alte A/B-Zugänge ungültig.
- [x] Ende-zu-Ende geprüft: anonyme Pfade gesperrt; Header-Auth auf allen
  geprüften Pfaden; acht kleingeschriebene Passwortzeichen produktiv
  akzeptiert, sieben abgelehnt und das temporäre Konto wieder gelöscht. Ein
  zusätzlicher Variationsselektor-Grenzfall mit acht Codepoints wurde auf dem
  finalen Live-Image akzeptiert und ebenfalls vollständig bereinigt.
- [x] Nach dem Live-Test sind Benutzer, Beziehungen/Mandanten,
  Mitgliedschaften, Sitzungen, Perioden und Tageslogs jeweils leer; Dunjas
  früheres Konto `dune` und die aktive Passwortdatei bleiben gelöscht.
- [x] Zehnminütige Build-Überwachung mit 30 Stichproben: kein ungesunder
  Dienst, mindestens rund 1,87 GiB verfügbarer Arbeitsspeicher und keine
  geänderten IDs von PostgreSQL, Authentik, Pangolin, Traefik oder Emby.
- [x] Finalen gezielten und High-Entropy-Secret-Scan sowie `git diff --check`
  ohne Treffer beziehungsweise Fehler ausgeführt; `gitleaks` ist lokal nicht
  installiert und bleibt Aufgabe des CI-History-Scans.
- [x] OpenAPI-Snapshot aktuell; generierten Kotlin-Client nach einem
  dokumentierten 1-GiB-Daemonabbruch mit 1,5 GiB erfolgreich kompiliert.
- [x] Geprüften 1.0.5-Stand einschließlich beider APKs als Commit
  `3c3ced280203a4108bbf665b1a2b0a63c700c278` in den privaten Branch gepusht,
  Draft-PR 1 aktualisiert und privaten Release `v1.0.5` mit beiden bestätigten
  Artefakten veröffentlicht.

Der lokale, gebaute, live geprüfte und privat veröffentlichte 1.0.5-Stand ist
vollständig belegt.

## Bewusst offen nach dem produktionsnahen Kern

- [ ] Instrumentierte Android-Tests auf realem Gerät oder Emulator ausführen.
- [ ] Eigenen signierten/minifizierten Release-Build erstellen; private
  Signierschlüssel bleiben absichtlich außerhalb des Repositorys.
- [ ] Android-UIs für Gerätesitzungen, eigene Symptome/Tipps,
  Check-in-Antworten, Export/Import, Kontolöschung und Beziehung trennen.
- [ ] Basis-Tageslog um Notiz, Basaltemperatur und Ovulationstest sowie
  Tageslog-Korrektur/-Löschung erweitern.
- [ ] Serverseitige Rollenübertragung beziehungsweise Tracker-Einzellöschung
  ergänzen; aktuell löscht ein Tracker den gesamten internen Mandanten.
- [ ] Optionale ntfy-Anbindung, Biometrie, SSE-Client, Widget, Demo-Modus und
  reduzierte Bewegungen. Lokale Benachrichtigungen sind umgesetzt.
- [ ] Generischen CSV-Zuordnungsassistenten, Import-Rollback und direkten
  Import verschlüsselter Backupdateien ergänzen.
- [ ] Tiefere Statistiken und Charts ergänzen.
- [ ] Vollständige Umbenennbarkeit von Package-ID, Übersetzungstexten und
  Vektor-Assets über einen einzigen Generator.
