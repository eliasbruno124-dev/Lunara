# Lunara – Abschlussbericht

Stand: 12. August 2026

Dieser Bericht wird nach der finalen Prüfkette und dem Live-Deployment mit den
tatsächlichen Laufzeitwerten vervollständigt. Aussagen mit „erfolgreich“ beruhen
ausschließlich auf wirklich ausgeführten Befehlen.

## Architektur

- Monorepo mit nativer Kotlin-/Compose-App, NestJS-/Prisma-API,
  OpenAPI-Kotlin-Referenzclient, Docker-/Traefik-Infrastruktur, CI und
  Betriebsdokumentation
- PostgreSQL 18 als einzige erforderliche Serverabhängigkeit; Redis ist nicht
  notwendig
- API ohne Host-Port, intern über Authentik Proxy Provider und Pangolin/
  Traefik 3 erreichbar
- getrennte Lunara-Konten und Rollen zusätzlich zu zwei getrennten äußeren
  Authentik-App-Passwörtern
- Room/SQLCipher, Android Keystore, WorkManager und clientseitige UUIDs für
  Offline-Nutzung

## Tatsächlich umgesetzt

Der vollständige serverseitige Kernablauf ist implementiert: leerer Server,
Tracker-Setup, Einmal-Einladung, Partnerkonto, Periodeneintrag,
Bereichsprognose, Freigabeprojektion, idempotenter Offline-Sync und
Partner-Dashboard. Hinzu kommen Tageslogs, Symptome, Profile, Care Preferences,
mindestens 50 DE/EN-Tipps, Check-ins, Export/Import, Kontolöschung,
Sitzungsverwaltung, Audit-Ereignisse, SSE und Admin-CLI.

Ein abschließender Security-Review fand und behob vor dem Deployment drei hohe
Befunde: verwaiste Gesundheitsdaten nach Haushaltslöschung, ein Race bei
gleichzeitiger Refresh-Wiederverwendung und teures Argon2-Hashing vor der
Prüfung eines öffentlichen Einladungscodes. Alle drei besitzen
Regressionstests.

Android implementiert Onboarding und Serverprüfung, Authentik-URL-Header-Auth,
Setup/Login/Einladung, rollenabhängige Hauptnavigation, Periodenstart,
Tageslog, Offline-Queue, Sync, Partner-Dashboard, Check-in-Anfrage, Kalender,
Verlauf, Einladung/Freigaben, Theme und Screenshot-Schutz. Logout und bestätigter
Serverwechsel löschen lokale Gesundheitsdaten und Secrets auch bei fehlender
Serververbindung.

## Ausgeführte Prüfungen

### Backend

- ESLint: erfolgreich
- Jest Unit: 9 Suites, 30 Tests, erfolgreich nach dem finalen Security-Hardening
- TypeScript-/Nest-Build: erfolgreich
- Prisma-Schema: erfolgreich validiert
- echter PostgreSQL-18-E2E-Kernablauf: 1 Suite/1 zusammenhängender Test,
  erfolgreich; enthält parallelen Refresh, vollständige Haushaltslöschung und
  erneuten Setup-Status
- `npm audit --omit=dev`: 0 bekannte Meldungen
- vollständiger Abhängigkeitsbaum: eine moderate transitive `ajv`-Meldung nur
  über Entwicklungs-/Lint-/CLI-Abhängigkeiten
- OpenAPI-Ausgabe reproduzierbar geprüft; generierter Kotlin-Client mit JDK 21
  erfolgreich kompiliert

Zusätzlich geprüft sind strikte Sync-Payloadvalidierung, eine kurze
In-Progress-Idempotenz-Lease mit Recovery, Create-Payload-Konflikte,
Einladungslaufzeit-Policy, CSV-Formelschutz, IANA-Zeitzonen und eine
fail-closed Partnerphase. Der finale OpenAPI-Spec-Hash lautet
`9868fb2f45fa2f3ed76e9f9acfa726b16591872f7e16b77a42444c6a760bfb7d`.

Die komplette Backend-Prüfkette wurde nach diesen Änderungen erneut erfolgreich
ausgeführt. Prisma meldete im schlanken Node-Testcontainer lediglich eine
OpenSSL-Erkennungswarnung; Build, Validierung und E2E waren erfolgreich.

### Android

- `testLocalDebugUnitTest`: 15/15 JVM-Tests erfolgreich
- `lintLocalDebug`: erfolgreich
- `lintProductionDebug`: erfolgreich
- `assembleProductionDebug`: erfolgreich
- `compileProductionDebugAndroidTestKotlin`: erfolgreich
- Compose-Instrumentierung: kompiliert, mangels Emulator/Gerät nicht ausgeführt

Erzeugtes installierbares Artefakt:

```text
/root/Lunara/artifacts/lunara-debug.apk
```

- Variante: Production-Debug
- Standardserver: `https://lunara.3li361.de/`
- Klartext-HTTP: deaktiviert
- Größe: 24.219.455 Bytes
- SHA-256: `c961d0931ccb3142d9140e2fb358a40b05964953aaf3f80d443b4552e12d317d`
- `sha256sum -c artifacts/SHA256SUMS`: erfolgreich

Ein unsigned Production-Release-Build wurde versucht, konnte auf dem Host wegen
wiederholter Gradle-Daemon-/Speicherabbrüche aber nicht erfolgreich beendet
werden. Es existiert deshalb kein `lunara-release-unsigned.apk`. Die
Production-Debug-APK ist tatsächlich installierbar, besitzt aber den Android-
Debugsignierschlüssel und ist nicht für einen öffentlichen Store gedacht.

### Container und Live-Deployment

Wird nach dem finalen Security-Patch ergänzt.

### Privates GitHub-Repository

Wird nach erfolgreicher GitHub-Authentifizierung ergänzt. Echte `.env`,
Gateway-Zugangsdaten, Tokens, Datenbankdumps und Infrastruktur-Backups werden
nicht hochgeladen.

## Bekannte Funktionsgrenzen

- Keine Android-UIs für Gerätesitzungen, Care Preferences, eigene
  Symptome/Tipps, Check-in-Antworten, vollständige Freigabematrix,
  Export/Import, Kontolöschung oder Haushalt verlassen
- Kalender zeigt den aktuellen Monat; Korrektur/Löschen und Monatwechsel sind
  noch nicht als vollständige UI vorhanden
- Keine lokalen Benachrichtigungen, ntfy, Biometrie, Widget, Demo-Modus oder
  reduzierte-Bewegungs-Einstellung
- Android konsumiert SSE noch nicht und nutzt WorkManager/manuellen Sync
- Der JSON-Export deckt Tracker-Kerndaten, aber nicht sämtliche Haushalts-,
  Sitzungs-, Audit-, Tipp- und Check-in-Datensätze ab
- Import verarbeitet Perioden/Tageslogs aus JSON; kein generischer CSV-
  Zuordnungsassistent, verschlüsselter Direktimport oder Undo-Endpoint
- Der generierte OpenAPI-Kotlin-Client ist geprüft, aber die App verwendet ihre
  eigene typisierte Retrofit-Schicht
- OpenAPI-Operationen dokumentieren den normalen Bearer-Weg; die zusätzliche
  `X-Lunara-Authorization`-Variante ist als Scheme vorhanden, aber noch nicht an
  jeder geschützten Operation als Alternative ausgezeichnet
- Check-in-/Freigabe-/Profiländerungen erzeugen nicht in jedem Fall einen
  eigenen SSE-Eintrag; Check-in-Freitext ist nicht zusätzlich mit dem
  `APP_MASTER_KEY` verschlüsselt
- Vollständige Branding-Umbenennung benötigt Anpassung von Package-ID,
  Übersetzungen und Assets zusätzlich zu den zentralen Tokens

## Bekannte Sicherheitsgrenzen

Siehe `SECURITY.md`. Besonders relevant sind die langlebigen Authentik-
App-Passwörter, Sichtbarkeit einer Zugangsdaten-URL in Zwischenablage/
Tastaturverlauf vor dem Sanitizing, fehlende Biometrie, unverschlüsselter
interner Docker-Hop, Cursor-Aktivitätsmetadaten und noch nicht gerätegetestete
Keystore-/SQLCipher-Wipe-Integration.

## Erster produktiver Start

1. Cloudflare-DNS für `lunara.3li361.de` gemäß `DEPLOYMENT.md` ergänzen.
2. Eine der lokalen `gateway-a.url`/`gateway-b.url`-Dateien ausschließlich auf
   dem Server öffnen und in Android eingeben; niemals teilen oder in QR kodieren.
3. Den `BOOTSTRAP_TOKEN` lokal aus `/docker/lunara/.env` verwenden und das erste
   Tracker-Konto anlegen.
4. Über die App einen Einmalcode erzeugen und das Partnerkonto mit eigenen
   Zugangsdaten anlegen.
5. Gateway- und Lunara-Passwörter getrennt sichern, `APP_MASTER_KEY` separat
   verschlüsselt offline verwahren und das erste Backup prüfen.
