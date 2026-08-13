# Lunara – Fortschritt

Stand: 13. August 2026, Quellstand 1.0.5

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
- [x] Android-Quellstand auf 1.0.5/VersionCode 6 angehoben; Serverfeld beim
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

## Zuletzt vollständig verifizierter Release: 1.0.5

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

Der lokale, gebaute und live geprüfte 1.0.5-Stand ist vollständig belegt. Ein
konkreter neuer GitHub-Commit wird erst nach dem tatsächlich ausgeführten Push
im Repository nachgewiesen und hier nicht vorweggenommen.

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
- [ ] Benachrichtigungen, optionale ntfy-Anbindung, Biometrie, SSE-Client,
  Widget, Demo-Modus und reduzierte Bewegungen.
- [ ] Generischen CSV-Zuordnungsassistenten, Import-Rollback und direkten
  Import verschlüsselter Backupdateien ergänzen.
- [ ] Monatwechsel und direkte Korrektur/Löschung in der Kalenderansicht sowie
  tiefere Statistiken und Charts ergänzen.
- [ ] Vollständige Umbenennbarkeit von Package-ID, Übersetzungstexten und
  Vektor-Assets über einen einzigen Generator.
