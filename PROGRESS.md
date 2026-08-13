# Lunara – Fortschritt

Stand: 13. August 2026

## Erledigt

- [x] Repository und produktiven Debian-Host vor Änderungen read-only geprüft.
- [x] Lunara als eigenes Projekt unter `/root/Lunara` umgesetzt; das Emby-
  Plugin und alle Medien-Dienste bleiben vollständig getrennt und unangetastet.
- [x] Architektur und Reihenfolge in `IMPLEMENTATION_PLAN.md` dokumentiert.
- [x] NestJS-/Prisma-/PostgreSQL-Backend mit Auth, Rollen,
  Haushaltsisolation, Perioden, Tageslogs, Symptomen, Prognose, Freigaben,
  Care Preferences, Tipps, Check-ins, Sync, Export/Import und Admin-CLI gebaut.
- [x] 50 zweisprachige Partner-Tipps plus sichere eigene Tipps implementiert.
- [x] Native Android-App mit Compose, DE/EN, Onboarding,
  Authentik-URL-Header-Auth, Room/SQLCipher, Keystore, WorkManager,
  rollenabhängigen Oberflächen, Kalender, Verlauf und Einstellungen gebaut.
- [x] Vollständigen lokalen Wipe bei Logout und bestätigtem Serverwechsel
  implementiert und als JVM-Logik getestet.
- [x] Docker-/CI-/Backup-/Restore-/Update-/Gateway-Skripte und Dokumentation
  erstellt.
- [x] Backend-Lint, Unit-Tests, Build, Prisma-Validierung,
  OpenAPI-Reproduzierbarkeit, Kotlin-Client-Compile und echten
  PostgreSQL-18-E2E-Kernablauf erfolgreich ausgeführt.
- [x] Android-JVM-Tests, Local-/Production-Lint, Instrumentation-Kompilierung
  und Production-Debug-APK-Build erfolgreich ausgeführt.
- [x] Android-/API-Verträge für Einladungslaufzeit, Tageslog-Textgrenzen und
  sprachabhängigen Check-in abgeglichen; die abschließende Version 1.0.2
  bestand 35 JVM-Tests und beide Lintvarianten.
- [x] Produktions-Debug-APK unter `artifacts/lunara-debug.apk` abgelegt und
  SHA-256-Prüfung ausgeführt.
- [x] Produktions-API-Image als nicht privilegierter Benutzer gebaut und
  Produktionsabhängigkeiten ohne bekannte npm-Audit-Meldung geprüft.
- [x] Drei hohe Auditbefunde zu Haushaltslöschung, parallelem Refresh und
  Einladungscode-DoS behoben; die erweiterte Regression bestand abschließend
  13 Backend-Suites mit 38 Tests und den echten PostgreSQL-E2E-Ablauf.
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

## In Arbeit

Keine offenen Bereitstellungsschritte.

## Bewusst offen nach dem produktionsnahen Kern

- [ ] Instrumentierte Android-Tests auf realem Gerät/Emulator ausführen.
- [ ] Eigener signierter/minifizierter Release-Build; privater Signing-Key ist
  absichtlich nicht vorhanden.
- [ ] Android-UIs für Gerätesitzungen, vollständige Freigabematrix, Care-
  Preferences, eigene Symptome/Tipps, Check-in-Antworten, Export/Import,
  Kontolöschung und Haushalt verlassen.
- [ ] Android-Basis-Tageslog um Notiz, Basaltemperatur und Ovulationstest sowie
  Korrektur/Löschen erweitern; Zyklusprofil, Sondermodi, Vorhersagepause und
  Fertilitätsaktivierung als UI ergänzen.
- [ ] Serverseitige Rollenübertragung beziehungsweise Tracker-Einzellöschung
  ergänzen; aktuell kann ein Partner sein Konto und ein Tracker nur den gesamten
  Haushalt löschen.
- [ ] Benachrichtigungen, optionale ntfy-Anbindung, Biometrie, SSE-Client,
  Widget, Demo-Modus und reduzierte Bewegungen.
- [ ] Generischer CSV-Zuordnungsassistent, Import-Rollback-Endpoint und direkter
  Import verschlüsselter Backupdateien.
- [ ] Monatwechsel/Korrektur/Löschen in der Android-Kalenderoberfläche sowie
  tiefere Statistiken und Charts.
- [ ] Vollständige Umbenennbarkeit von Package-ID, allen Übersetzungstexten und
  Vektor-Assets über einen einzigen Generator.
