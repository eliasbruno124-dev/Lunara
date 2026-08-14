# Lunara – Implementierungsplan

Stand: 13. August 2026

## 1. Ausgangslage und Leitplanken

- Lunara entsteht als vollständig separates, privates GitHub-Repository unter
  `/root/Lunara`. Das vorhandene Emby-Plugin bleibt unangetastet.
- Produktive Laufzeitdaten und echte Secrets liegen ausschließlich unter
  `/docker/lunara`; sie werden weder in Git noch in Chat-Ausgaben übernommen.
- Der Host besitzt Docker 29.7.2 und Compose 5.4.0, aber keine lokale Node-,
  Java-, Gradle- oder Android-SDK-Installation. Builds laufen deshalb zunächst
  reproduzierbar in Containern. Ein echter Android-Build wird trotzdem versucht.
- Stack 20 bleibt Portainer-owned und wird nicht mit rohem Compose verändert.
  Lunara erhält einen eigenen Stack und ein eigenes internes Datenbanknetz.
- Das gemeinsame Reverse-Proxy-Netz heißt auf diesem Server `pangolin`.
  Die API hängt zusätzlich an diesem Netz, ist dort aber ausschließlich für den
  Authentik-Outpost adressierbar; PostgreSQL bleibt im internen Lunara-Netz und
  veröffentlicht keinen Host-Port.
- Vorgesehene, weiterhin konfigurierbare Domain: `lunara.3li361.de`.

## 2. Versionen und Toolchain

Die Versionsnummern werden in Gradle-Dateien, npm-Lockfile und Container-Tags
fixiert. Ausgangspins nach Prüfung der offiziellen Release-Quellen:

- Node.js 24.18.0 LTS
- NestJS 11.1.29 (am Implementierungstag direkt als npm-`latest` bestätigt)
- Prisma / Prisma Client / PostgreSQL-Adapter 7.9.1
- PostgreSQL 18.4
- TypeScript 5.9.x
- Android compileSdk/targetSdk 37, minSdk 26
- Android Gradle Plugin 9.1.1, Gradle 9.3.1, JDK 17
- Kotlin und Compose-Compiler-Plugin 2.4.10
- Compose BOM 2026.06.01
- SQLCipher for Android 4.17.0 und AndroidX SQLite 2.6.2

Falls eine Kombination im realen Build nicht auflösbar oder binär inkompatibel
ist, wird nur auf die nächste bestätigte stabile Kombination zurückgegangen und
die Abweichung mit dem tatsächlich beobachteten Buildfehler dokumentiert.

## 3. Zielarchitektur

### Monorepo

```text
apps/android                 native Kotlin-/Compose-App
services/api                 NestJS-REST-API und SSE
packages/openapi-client      generierter Kotlin-Client / OpenAPI-Artefakte
infra/docker                 produktive Container-Konfiguration
infra/traefik                dokumentierte Router-/Gateway-Integration
scripts                      Backup, Restore, Update, Admin und E2E
docs                         Screenshots, ADRs und Zusatzdokumentation
.github/workflows            CI für Backend, Android, Container und SBOM
artifacts                    nur nachweislich erzeugte Build-Artefakte
```

### Backend

- Modularer NestJS-Monolith mit REST unter `/api/v1`, Swagger/OpenAPI,
  `/health`, `/ready` und Server-Sent Events für geöffnete Apps.
- PostgreSQL und Prisma mit UUIDs, lokalen Kalenderdaten (`date`), Soft Deletes,
  Versionsfeldern, Mutation-Idempotenz und cursorbasierter Änderungsabfrage.
- Argon2id, kurze JWT-Access-Tokens, gehashte rotierende Refresh-Tokens,
  Geräte-Sitzungen, Login-Rate-Limit, serverseitige Rollenprüfung und Audit-Logs
  ohne Gesundheitsdaten.
- Zentrale Kontopasswort-Richtlinie: neue und geänderte Passwörter enthalten 8
  bis 128 Unicode-Zeichen ohne erzwungene Zeichenklassen. Bestehende
  Passwörter werden für Login, Kontolöschung und Passwortwechsel mit 1 bis 128
  Zeichen akzeptiert, um Richtlinienänderungen ohne Kontosperre zu erlauben.
  Die getrennte Verschlüsselungspassphrase für Exporte bleibt bei 12 bis 128
  Zeichen. Argon2id, Rate Limits und die äußere Authentik-Schicht bleiben
  unabhängig davon aktiv.
- AES-256-GCM-Anwendungsverschlüsselung für Freitext mit versioniertem
  Ciphertext-Format und ausschließlich extern bereitgestelltem Master-Key.
- Partnerprojektion wird serverseitig aus Freigaben aufgebaut. Rohobjekte werden
  nie erst an den Client gesendet und dort gefiltert.
- Deterministischer robuster Prognosealgorithmus (gewichteter Median,
  MAD-basierte Unsicherheit) mit Erklärung und Sondermodus-Sperren.

### Android

- Eine App für beide Rollen; serverseitige Membership bestimmt Navigation und
  Schreibrechte. Compose Material 3, zentrale Branding-/Theme-Tokens, DE/EN,
  Hell/Dunkel und barrierearme Komponenten.
- Schichten: Compose UI → ViewModels → Repositories → Room/Retrofit. Hilt,
  Coroutines/Flow, WorkManager, SQLCipher und Android Keystore.
- Offline-first: lokale UUIDs, Mutation Queue, optimistische UI, Backoff,
  Cursor-Sync, Konfliktanzeige, manueller Sync und lokales Scheduling.
- Onboarding beginnt mit einem leeren Serverfeld und prüft `/api/v1/meta`, TLS
  und API-Kompatibilität. Für die vollständig geschützte Domain akzeptiert es
  eine HTTPS-URL mit Authentik-App-Zugang im Userinfo-Teil, trennt diese
  Zugangsdaten sofort von der gespeicherten Basisadresse und legt sie
  Keystore-gestützt ab. Ein Partner-QR enthält weiterhin nur den einmal
  verwendbaren Einladungscode; Passwörter, Bootstrap-Token und der gemeinsame
  Gateway-Zugang werden nie eingebettet.
- Jede Anfrage führt zwei getrennte Schutzschichten: Authentik erhält
  ausschließlich am exakt gleichen HTTPS-Ursprung den äußeren Basic-Header;
  das persönliche Lunara-JWT wird bei aktivem Gateway als
  `X-Lunara-Authorization` übertragen. Weiterleitungen werden nicht verfolgt,
  Klartext-HTTP mit Zugangsdaten ist immer verboten.

### Deployment und äußerer Zugriff

- Eigener Portainer-/Compose-Stack: `lunara-api` und `lunara-postgres`.
- API: non-root, read-only root filesystem, tmpfs, Healthcheck, Ressourcenlimits,
  keine Hostports; DB: persistentes Volume, Healthcheck, internes Netz.
- `lunara.3li361.de` wird über Pangolin zum eingebetteten Authentik Proxy Outpost
  geroutet; dessen Proxy Provider leitet intern an `lunara-api:3000` weiter.
- Authentik schützt die vollständige Domain einschließlich `/api/v1`, Health
  und OpenAPI. Genau ein neutraler technischer App-Benutzer mit genau einem
  nicht ablaufenden App-Passwort ist Mitglied der Gateway-Gruppe; persönliche
  Authentik-Konten erhalten darüber keinen Zugriff. Hinter dieser äußeren
  Schranke erzwingt Lunara weiterhin eigene Passwörter, kurzlebige JWTs,
  Rollen, Eigentum und die unsichtbare Beziehungsgrenze.
- Lunara-Konten bleiben vollständig von Authentik-Konten getrennt. Der eine
  technische Authentik-App-Zugang ist ein gemeinsam zu schützendes
  Infrastrukturgeheimnis: nur serverlokal und im Android Keystore, niemals in
  QR-Codes, Logs, Git, Cloudflare oder Dokumentation.
- Die einzige verbleibende externe Aktion soll der Cloudflare-DNS-Eintrag sein.

## 4. Implementierungsreihenfolge

1. Repository, zentrale Branding-Konfiguration, Workspace, Linting, Lockfiles,
   Dokumentationsgerüst und sichere `.gitignore`/`.env.example`.
2. Prisma-Datenmodell und Migration; Meta/Health/Ready, Setup und Auth inklusive
   Sitzungsrotation, Rollen und Einladung.
3. Perioden, Tageslogs, Profile, Freigaben, Care Preferences, Prognose und strikt
   gefiltertes Partner-Dashboard; Kernablauf als Integrationstest.
4. Cursor-Sync, Idempotenz, Konflikte, SSE, Check-ins, Tipps mit mindestens 50
   redaktionell gepflegten DE-/EN-Paaren sowie Feedback.
5. Export/Import/Kontolöschung und sichere Admin-CLI.
6. Android-Grundprojekt, Designsystem, Onboarding/Auth, verschlüsselte lokale
   Datenhaltung, Sync und rollenabhängige Hauptansichten.
7. Kalender, Verlauf, Kernfreigaben, Einstellungen, Screenshot-Schutz und
   QR-Flows. Lokale, rollen- und freigabebewusste Benachrichtigungen werden nach
   dem Kernablauf ergänzt; vollständige Care-/Session-UIs und Biometrie werden
   bei Nichterreichen als offene Funktionen ausgewiesen.
8. Docker-/Traefik-Konfiguration, Backup/Restore/Update, CI, SBOM und vollständige
   Betriebs-, Sicherheits-, Datenschutz-, Algorithmus- und Testdokumentation.
9. Backend-Lint/Test/Build, echte PostgreSQL-Integrationstests, Docker-Build und
   Laufzeittest, Android-Lint/Unit-Test/APK-Build und Artefaktkopie nur bei Erfolg.
10. Eng benanntes Live-Deployment außerhalb Stack 20, Health-/Log-Prüfung und
    Vorbereitung des Routers, sodass nur Cloudflare-DNS offen bleibt.
11. High-Entropy-Secret-Scan, absichtlicher Commit/Push in ein neues privates
    GitHub-Repository und abschließender `FINAL_REPORT.md` mit realen Ergebnissen.

## 5. Test- und Abnahmestrategie

- Nach jedem Backend-Abschnitt: ESLint, Unit-Tests, TypeScript-Build.
- Integration/E2E gegen eine echte temporäre PostgreSQL-Instanz; insbesondere
  Setup, Refresh-Rotation, Einladung, IDOR/Beziehungsisolation, Freigabeentzug,
  Soft Delete, idempotenter Offline-Sync und Konflikte.
- Android: JVM-Tests für URL/Auth/Prediction-Sichtmodelle/Queue/Navigation und
  instrumentierte Compose-Tests, soweit Emulator/SDK dies hier ausführen lassen.
- Deployment: `docker compose config`, Image-Build, Migration, Health/Ready,
  Kernablauf per HTTP und Backupprüfung. Ein Restore wird nur auf einer separat
  benannten Testinstanz ausgeführt; keine bestehende Produktivdatenbank wird
  dafür verwendet.
- Fortschritt und jede reale Einschränkung werden fortlaufend in `PROGRESS.md`
  und abschließend in `FINAL_REPORT.md` festgehalten.

## 6. Bewusst abgegrenzte Punkte

- Kein vollständiger Messenger und keine medizinische Diagnose.
- Ntfy bleibt optional und standardmäßig deaktiviert.
- Ein Homescreen-Widget, Demo-Modus und generischer visueller CSV-Mapping-Assistent
  folgen erst nach dem nachweislich funktionierenden Kernablauf.
- Die GitHub-Remote-Erstellung hängt von einer schreibberechtigten GitHub-
  Verbindung ab; das lokale Repository und der Secret-Scan sind davon unabhängig.
