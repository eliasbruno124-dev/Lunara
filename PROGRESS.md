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
- [x] Produktions-Debug-APK unter `artifacts/lunara-debug.apk` abgelegt und
  SHA-256-Prüfung ausgeführt.
- [x] Produktions-API-Image als nicht privilegierter Benutzer gebaut und
  Produktionsabhängigkeiten ohne bekannte npm-Audit-Meldung geprüft.
- [x] Drei hohe Auditbefunde zu Haushaltslöschung, parallelem Refresh und
  Einladungscode-DoS behoben; Regressionen mit 30 Unit-Tests und echtem
  PostgreSQL-E2E erneut erfolgreich geprüft.
- [x] Sync-Payloadvalidierung/Lease-Recovery, Create-Payload-Idempotenz,
  Einladungslaufzeit, CSV-Formelschutz, IANA-Zeitzone und fail-closed
  Partnerphase nachgehärtet.

## In Arbeit

- [ ] Finales Security-Review einarbeiten und komplette Prüfkette erneut
  ausführen.
- [ ] Eigenen Live-Stack `/docker/lunara` starten und Health/Readiness prüfen.
- [ ] Authentik-Provider und Pangolin-Ressource additiv anlegen, beide getrennten
  Gateway-Zugänge vor DNS prüfen.
- [ ] Privates GitHub-Repository erstellen und secret-geprüften Commit pushen.
- [ ] Tatsächliche Live-Ergebnisse in `FINAL_REPORT.md` festhalten.

## Bewusst offen nach dem produktionsnahen Kern

- [ ] Instrumentierte Android-Tests auf realem Gerät/Emulator ausführen.
- [ ] Eigener signierter/minifizierter Release-Build; privater Signing-Key ist
  absichtlich nicht vorhanden.
- [ ] Android-UIs für Gerätesitzungen, vollständige Freigabematrix, Care-
  Preferences, eigene Symptome/Tipps, Check-in-Antworten, Export/Import,
  Kontolöschung und Haushalt verlassen.
- [ ] Benachrichtigungen, optionale ntfy-Anbindung, Biometrie, SSE-Client,
  Widget, Demo-Modus und reduzierte Bewegungen.
- [ ] Generischer CSV-Zuordnungsassistent, Import-Rollback-Endpoint und direkter
  Import verschlüsselter Backupdateien.
- [ ] Monatwechsel/Korrektur/Löschen in der Android-Kalenderoberfläche sowie
  tiefere Statistiken und Charts.
- [ ] Vollständige Umbenennbarkeit von Package-ID, allen Übersetzungstexten und
  Vektor-Assets über einen einzigen Generator.
