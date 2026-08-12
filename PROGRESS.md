# Lunara – Fortschritt

Stand: 12. August 2026

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
  sprachabhängigen Check-in abgeglichen; 17 JVM-Tests und beide Lintvarianten
  danach erneut erfolgreich ausgeführt.
- [x] Produktions-Debug-APK unter `artifacts/lunara-debug.apk` abgelegt und
  SHA-256-Prüfung ausgeführt.
- [x] Produktions-API-Image als nicht privilegierter Benutzer gebaut und
  Produktionsabhängigkeiten ohne bekannte npm-Audit-Meldung geprüft.
- [x] Drei hohe Auditbefunde zu Haushaltslöschung, parallelem Refresh und
  Einladungscode-DoS behoben; Regressionen mit 35 Unit-Tests und echtem
  PostgreSQL-E2E erneut erfolgreich geprüft.
- [x] Sync-Payloadvalidierung/Lease-Recovery, Create-Payload-Idempotenz,
  Einladungslaufzeit, CSV-Formelschutz, IANA-Zeitzone und fail-closed
  Partnerphase nachgehärtet.
- [x] Finales Security-Review eingearbeitet und komplette Backend-Prüfkette
  erneut ausgeführt.
- [x] Eigenen Live-Stack unter `/docker/lunara` gestartet: API/PostgreSQL gesund,
  zwei Migrationen angewendet, keine Hostports, API als UID/GID `10001:10001`
  mit read-only Root-Dateisystem und Capability-Drop verifiziert.
- [x] Authentik-Provider und Pangolin-Ressource additiv angelegt und beide
  getrennten Gateway-Konten lokal vor DNS erfolgreich geprüft; ohne Anmeldung
  antwortet der Gateway mit HTTP 302.
- [x] Live-Backup erstellt, Archivprüfsummen und `pg_restore`-Inhaltsliste
  validiert; bestehende Dienste einschließlich Emby blieben ohne Neustart.
- [x] Tatsächliche Live-Ergebnisse in `FINAL_REPORT.md` festgehalten.

## In Arbeit

- [ ] Cloudflare-DNS-Eintrag für `lunara.3li361.de` anlegen und beide
  Gateway-Zugänge danach ohne lokale Resolve-/TLS-Ausnahme erneut prüfen.
- [ ] Privates GitHub-Repository erstellen und secret-geprüften Commit pushen.

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
