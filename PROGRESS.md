# Lunara – Fortschritt

Stand: 13. August 2026

## Erledigt

- [x] Repository und produktiven Debian-Host vor Änderungen read-only geprüft.
- [x] Lunara als eigenes Projekt unter `/root/Lunara` umgesetzt; das Emby-
  Plugin und alle Medien-Dienste bleiben vollständig getrennt und unangetastet.
- [x] Architektur und Reihenfolge in `IMPLEMENTATION_PLAN.md` dokumentiert.
- [x] NestJS-/Prisma-/PostgreSQL-Backend mit Auth, Rollen, interner
  Beziehungsisolation, Perioden, Tageslogs, Symptomen, Prognose, Freigaben,
  Care Preferences, Tipps, Check-ins, Sync, Export/Import und Admin-CLI gebaut.
- [x] 50 zweisprachige Partner-Tipps plus sichere eigene Tipps implementiert.
- [x] Native Android-App mit Compose, DE/EN, Onboarding,
  HTTPS-Serververbindung, Room/SQLCipher, Keystore, WorkManager,
  rollenabhängigen Oberflächen, Kalender, Verlauf und Einstellungen gebaut.
- [x] Vollständigen lokalen Wipe bei Logout und bestätigtem Serverwechsel
  implementiert und als JVM-Logik getestet.
- [x] Docker-/CI-/Backup-/Restore-/Update-/Gateway-Skripte und Dokumentation
  erstellt.
- [x] Für Version 1.0.2 Backend-Lint, Unit-Tests, Build,
  Prisma-Validierung, OpenAPI-Reproduzierbarkeit, Kotlin-Client-Compile und
  echten PostgreSQL-18-E2E-Kernablauf erfolgreich ausgeführt.
- [x] Für Version 1.0.2 Android-JVM-Tests, Local-/Production-Lint,
  Instrumentation-Kompilierung und Production-Debug-APK-Build erfolgreich
  ausgeführt.
- [x] Android-/API-Verträge für Einladungslaufzeit, Tageslog-Textgrenzen und
  sprachabhängigen Check-in abgeglichen; die abschließende Version 1.0.2
  bestand 35 JVM-Tests und beide Lintvarianten.
- [x] Produktions-Debug-APK unter `artifacts/lunara-debug.apk` abgelegt und
  SHA-256-Prüfung ausgeführt.
- [x] Produktions-API-Image als nicht privilegierter Benutzer gebaut und
  Produktionsabhängigkeiten ohne bekannte npm-Audit-Meldung geprüft.
- [x] Drei hohe Auditbefunde zur vollständigen Beziehungslöschung, parallelem
  Refresh und Einladungscode-DoS behoben; die erweiterte Regression bestand
  abschließend 13 Backend-Suites mit 38 Tests und den echten
  PostgreSQL-E2E-Ablauf.
- [x] Sync-Payloadvalidierung/Lease-Recovery, Create-Payload-Idempotenz,
  Einladungslaufzeit, CSV-Formelschutz, IANA-Zeitzone und fail-closed
  Partnerphase nachgehärtet.
- [x] Finales Security-Review eingearbeitet und komplette Backend-Prüfkette
  erneut ausgeführt.
- [x] Eigenen Live-Stack unter `/docker/lunara` gestartet: API/PostgreSQL gesund,
  drei Migrationen angewendet, keine Hostports, API als UID/GID `10001:10001`
  mit read-only Root-Dateisystem und Capability-Drop verifiziert.
- [x] Authentik-Provider und Pangolin-Ressource additiv angelegt und beide
  getrennten Gateway-Konten lokal vor DNS erfolgreich geprüft; ohne Anmeldung
  antwortet der Gateway mit HTTP 302.
- [x] Cloudflare-CNAME auf beiden autoritativen Nameservern geprüft,
  Let’s-Encrypt-Zertifikat ausgestellt und beide Gateway-Zugänge anschließend
  ohne Resolve- oder TLS-Ausnahme erfolgreich bis zur API getestet.
- [x] Authentik-Zugriff für Dunjas bestehendes Konto `Dune` und `Elias`
  freigegeben; beide Policy-Entscheidungen sowie beide getrennten Header-Auth-
  Zugänge gegen `https://lunara.3li361.de` erfolgreich geprüft. Vor der
  Änderung wurde ein validierter PostgreSQL-Custom-Dump erstellt; kein Dienst
  musste neu gestartet werden.
- [x] Fehlgeschlagene ACME-Erstanforderung nach eng benanntem Pangolin-Backup
  ausschließlich durch Neuladen der Lunara-Ressource erneut ausgelöst; kein
  Containerneustart erforderlich.
- [x] Live-Backup erstellt, Archivprüfsummen und `pg_restore`-Inhaltsliste
  validiert.
- [x] Unbeabsichtigten globalen OOM während des Android-Builds untersucht:
  Kernel beendete Emby, Docker stellte den Dienst automatisch wieder her und
  der öffentliche Info-Endpunkt liefert wieder HTTP 200. Keine Emby-Datei und
  kein Emby-Plugin wurden geändert.
- [x] Android-Build gegen erneuten Host-Speicherdruck gehärtet: Vorabcheck auf
  4 GiB verfügbaren Speicher, ein Gradle-Worker und feste Containergrenzen für
  CPU, RAM und Swap.
- [x] Tatsächliche Live-Ergebnisse in `FINAL_REPORT.md` festgehalten.
- [x] Privates GitHub-Repository vor dem ersten Push auf private Sichtbarkeit
  geprüft, vollständigen `main`-Stand gepusht und lokalen/entfernten Commit
  verglichen.
- [x] APK als privates GitHub-Release `v1.0.0` veröffentlicht und Größe sowie
  SHA-256 vor dem Upload erneut geprüft.
- [x] Android-Cachefehler nach anonymem Authentik-Aufruf behoben: anonyme
  Proxy-Cookies unterdrücken Header-Auth nicht mehr, Zugangsdaten bleiben auf
  den exakten Server-Ursprung begrenzt und Browser-Login-Redirects werden nicht
  verfolgt. Eingefügtes `/api/v1/meta` wird auf die Serverbasis normalisiert.
- [x] Version 1.0.1 mit vorausgefüllter Produktionsdomain gebaut; Unit-Tests,
  Local-/Production-Lint, APK-ZIP- und v2-Signaturprüfung erfolgreich. Größe:
  24.219.939 Bytes.
  SHA-256 (v1.0.1): `10c34b75a1833894ecae89613b9ad25ef4d038d02b25364901ad719ca2f9a5c8`.
- [x] Lokalen Debug-Signierschlüssel dauerhaft, mit Modus `0600` und außerhalb
  der Git-Erfassung abgelegt. APK- und Keystore-Fingerabdruck stimmen überein,
  sodass nach der einmaligen Ablösung der 1.0.0-Testsignatur weitere lokale
  Debug-Updates denselben Schlüssel verwenden.
- [x] Produktions-Onboarding 1.0.2 ohne vorausgefüllte Domain umgesetzt;
  veraltete Authentik-Cookies und Bootstrap-Metadaten werden fail-closed
  verworfen, ein autoritatives Setup-`409` wechselt direkt zur Anmeldung.
- [x] Automatische Synchronisierung nach Anmeldung/Mutation, beim Öffnen,
  minütlich im Vordergrund und sparsam per WorkManager ergänzt; Worker,
  Credential-Rotation und lokale Wipes gegen Parallelzugriffe gehärtet.
- [x] Persönlichen Lunara-Passwortwechsel in beiden rollenabhängigen
  Einstellungen implementiert: aktuelles Passwort, Argon2id, Rate-Limit,
  Audit und Widerruf aller anderen Lunara-Tokenfamilien.
- [x] Version 1.0.2 mit leerer Standardserveradresse gebaut; 35/35 JVM-Tests,
  beide Lints, Instrumentierungstest-Kompilierung, APK-ZIP/v2-Signatur und
  Secret-Scan erfolgreich. Größe: 24.455.313 Bytes.
  SHA-256 (v1.0.2): `1f07d1d58b97dca2b78d12959a287b91003299418bdb5f64386ff3ea04ee7604`.
- [x] Vor dem Live-Update einen geprüften PostgreSQL-Custom-Dump und die
  eng benannten Quell-/Konfigurationssicherungen angelegt, das API-Image mit
  1 GiB RAM und einer halben CPU gebaut, die dritte Migration angewendet und
  ausschließlich den Lunara-API-Container auf Version 1.0.2 aktualisiert.
- [x] Live-Prüfung von Health, Readiness, No-Store-Metaantwort,
  Passwort-Authentifizierung, beiden persönlichen Authentik-Zugängen und drei
  Migrationen bestanden. Emby lief währenddessen ohne Neustart weiter.
- [x] Geprüften Quellstand und die APK als privates GitHub-Release `v1.0.2`
  veröffentlicht.
- [x] Quellcode für 1.0.3 auf mehrere getrennte Paare erweitert: optionale
  Tracker-Registrierung über `REGISTRATION_ENABLED`, unsichtbare interne
  Beziehungs-/Mandantengrenze und genau eine `TRACKER`-/`PARTNER`-Rolle je
  Beziehung.
- [x] Sichtbares Haushaltsfeld aus dem Android-Onboarding entfernt und neutrale
  Beziehungsbezeichnungen ergänzt. Der alte optionale API-Wert wird nur noch
  aus Kompatibilitätsgründen akzeptiert und nicht als Produktname verwendet.
- [x] Permanente, einmal verwendbare und widerrufbare Partner-Einladung mit
  einmaliger Rohcodeanzeige, Hashspeicherung, QR-Code und Rollen-Sperren
  implementiert.
- [x] Periodenstart kann durch `TRACKER` in Heute und Verlauf geändert oder nach
  Bestätigung gelöscht werden; lokale Soft-Deletes und Änderungen laufen über
  die automatische Sync-Warteschlange.
- [x] Android-Produktionsadresse auf `https://lunara.3li361.de` gesetzt und
  App-Abhängigkeit von Authentik-Header-Zugangsdaten entfernt.
- [x] Authentik-Konfigurations- und Prüfskripte für einen gezielten Skip von
  `/api/v1` sowie weiterhin geschützte OpenAPI-/Browserpfade angepasst.
- [x] Aktuellen Backend-Stand 1.0.3 geprüft: Prisma-Client erzeugt,
  Prisma-Schema gültig, ESLint und Nest-Build erfolgreich sowie 14/14
  Jest-Suites mit 59/59 Tests bestanden. `npm audit --omit=dev` meldete
  0 bekannte Schwachstellen.
- [x] Android-Sync nach Release-Review weiter gehärtet: Erfolgs-Drain ohne
  Retry-Backoff, sichtbare Konflikte mit expliziter Serverfassungsentscheidung,
  Schreibsperre während der Auflösung und vollständiges Leeren optionaler
  Tageslog-Werte einschließlich Backend-Regressionstest.
- [x] Echten PostgreSQL-18-E2E-Ablauf für einen früheren 1.0.3-Zwischenstand
  als 1/1 Suite und 1/1 zusammenhängenden Test bestanden. Er umfasste parallele
  Tracker-Registrierung, getrennte Beziehungen, Einladungserstellung/-widerruf,
  Einmalverwendung, Perioden, Prognose, Freigaben und idempotenten Sync. Die
  danach ergänzte persistente Bootstrap-Sperre und der Datenbankschutz gegen
  doppelte aktive Periodenstarts erfordern einen neuen Lauf.
- [x] OpenAPI 1.0.3 mit OpenAPI Generator 7.24.0 exportiert, den Kotlin-
  Referenzclient neu erzeugt und Spezifikations-/Konfigurations-/Baumhashes
  erfolgreich reproduzierbar geprüft.
- [x] Vor dem finalen Live-Update den validierten PostgreSQL-Custom-Dump
  `/var/backups/lunara/lunara-pre-server-state-20260813T152814Z.dump`
  erstellt (62.151 Bytes, SHA-256
  `1279c7a00f14c42f0528c1dd5b67370d7f5274af707a708e705a28a12135f540`).
- [x] Finales `lunara-api:1.0.3`-Image unter 1-GiB-RAM- und 0,5-CPU-Grenze
  gebaut: `sha256:235794dfed4abaf3a0eb055d196738c56530445f742c7a601cfac5989340e06d`.
- [x] Alle sechs Migrationen einschließlich persistentem `ServerState` und
  eindeutigem aktivem Periodenstart angewendet und ausschließlich die
  Lunara-API neu erstellt.
- [x] Finalen Live-Stand 1.0.3 verifiziert: Health, Readiness und Meta HTTP
  200, `/api/v1/me` anonym HTTP 401, OpenAPI anonym HTTP 302,
  `setupRequired: false`, Registrierung aktiv und sechs Migrationen. Der
  unveränderte Bestand umfasst einen Benutzer, eine Mitgliedschaft und einen
  Periodeneintrag; Dunja ist aktive `TRACKER`.
- [x] Dunjas persönliches Lunara-Passwort zurückgesetzt, alle bestehenden
  Lunara-Sitzungen widerrufen und das neue Passwort ausschließlich serverlokal
  unter `/docker/lunara/dunja-lunara-password.txt` mit Modus `0600` abgelegt.
  Anschließender Login-, Profil- und Logout-Test war erfolgreich; Authentik-
  Zugangsdaten und Gesundheitsdaten wurden nicht verändert.
- [x] Authentik-Datenbank vor der Änderung validiert gesichert und nur den
  Lunara-Provider-Skip für `/api/v1` gesetzt. Öffentlich geprüft: Meta HTTP
  200, geschützte API ohne Lunara-Token HTTP 401 und OpenAPI ohne Authentik-
  Anmeldung HTTP 302. Die beiden serverlokalen administrativen Zugänge
  bestanden ihre getrennten Ende-zu-Ende-Prüfungen.
- [x] Beim gesamten 1.0.3-Live-Update keinen Neustart von Emby, Authentik,
  Pangolin oder PostgreSQL ausgelöst; ihre Container-IDs blieben unverändert.
- [x] Repository-Secret-Scan ohne Fund abgeschlossen. `gitleaks` war auf dem
  Host nicht installiert und wurde daher nicht als ausgeführt bezeichnet.
- [x] Bootstrap-Abschluss in einem persistenten singletonartigen
  `ServerState` gespeichert. Das Löschen der letzten Beziehung öffnet Setup
  nicht erneut; bestehende Installationen werden durch die additive Migration
  als eingerichtet markiert und `BOOTSTRAP_TOKEN` ist danach nicht mehr nötig.
- [x] `BOOTSTRAP_TOKEN` aus der Live-`.env` entfernt und die vorherige
  Konfiguration ausschließlich serverlokal mit Modus `0600` gesichert.
  Meta blieb bei `setupRequired: false`; ein Dummy-Setupversuch endete mit
  HTTP 401.
- [x] Android-Prüfkette 1.0.3 tatsächlich ausgeführt: 63/63 JVM-Tests,
  `lintLocalDebug`, `lintProductionDebug`, Kompilierung der
  Production-Instrumentierungstests und `assembleProductionDebug` erfolgreich.
- [x] Installierbare Production-Debug-APK 1.0.3 erzeugt, ZIP-Struktur und
  APK-v2-Signatur geprüft und unter `artifacts/lunara-debug.apk` abgelegt:
  24.362.451 Bytes, SHA-256
  `4dac957e020684f3d5ee5699fdc584a0f68393a58a76452801a21831616dd416`.

## In Arbeit

- [ ] Erweiterten PostgreSQL-18-E2E-Test auf dem aktuellen Quellstand mit
  persistenter Bootstrap-Sperre und Unique-Constraint für aktive
  Periodenstarts erneut ausführen. Der letzte erfolgreiche E2E-Lauf stammt von
  einem früheren 1.0.3-Zwischenstand.
- [ ] Den separat erzeugten OpenAPI-Kotlin-Referenzclient bei ausreichendem
  Speicher kompilieren. Der bisherige 1.0.3-Versuch im auf 768 MiB begrenzten
  Container endete durch memcg-OOM/Gradle-Daemon-Abbruch; keine Dienste waren
  betroffen. Export, Generierung und Hashprüfung sind davon unabhängig
  erfolgreich.
- [ ] Geprüften 1.0.3-Quellstand und die vorhandene APK committen/pushen und
  als privates GitHub-Release mit verifiziertem Hash veröffentlichen. Bis zur
  tatsächlichen Veröffentlichung bleibt v1.0.2 das letzte GitHub-Release.

## Bewusst offen nach dem produktionsnahen Kern

- [ ] Instrumentierte Android-Tests auf realem Gerät/Emulator ausführen.
- [ ] Eigener signierter/minifizierter Release-Build; privater Signing-Key ist
  absichtlich nicht vorhanden.
- [ ] Android-UIs für Gerätesitzungen, vollständige Freigabematrix, Care-
  Preferences, eigene Symptome/Tipps, Check-in-Antworten, Export/Import,
  Kontolöschung und Beziehung trennen.
- [ ] Android-Basis-Tageslog um Notiz, Basaltemperatur und Ovulationstest sowie
  Tageslog-Korrektur/-Löschen erweitern; Zyklusprofil, Sondermodi,
  Vorhersagepause und Fertilitätsaktivierung als UI ergänzen.
- [ ] Serverseitige Rollenübertragung beziehungsweise Tracker-Einzellöschung
  ergänzen; aktuell kann ein Partner sein Konto und ein Tracker nur den gesamten
  internen Beziehungsmandanten löschen.
- [ ] Benachrichtigungen, optionale ntfy-Anbindung, Biometrie, SSE-Client,
  Widget, Demo-Modus und reduzierte Bewegungen.
- [ ] Generischer CSV-Zuordnungsassistent, Import-Rollback-Endpoint und direkter
  Import verschlüsselter Backupdateien.
- [ ] Monatwechsel und direkte Korrektur/Löschung in der Android-
  Kalenderoberfläche sowie tiefere Statistiken und Charts.
- [ ] Vollständige Umbenennbarkeit von Package-ID, allen Übersetzungstexten und
  Vektor-Assets über einen einzigen Generator.
