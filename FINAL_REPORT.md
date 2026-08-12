# Lunara – Abschlussbericht

Stand: 12. August 2026

Dieser Bericht enthält die Ergebnisse der finalen Prüfkette und des
Pre-DNS-Live-Deployments. Aussagen mit „erfolgreich“ beruhen ausschließlich auf
wirklich ausgeführten Befehlen. Offen sind der Cloudflare-DNS-Eintrag und die
Veröffentlichung in einem privaten GitHub-Repository.

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
mindestens 50 DE/EN-Tipps, Check-ins, Export/Import, Sitzungsverwaltung,
Audit-Ereignisse, SSE und Admin-CLI. Bei der Kontolöschung kann ein Partner das
eigene Konto löschen; ein Tracker kann in Version 1 nur den gesamten Haushalt
löschen, weil noch keine Rollenübertragung existiert.

Ein abschließender Security-Review fand und behob vor dem Deployment drei hohe
Befunde: verwaiste Gesundheitsdaten nach Haushaltslöschung, ein Race bei
gleichzeitiger Refresh-Wiederverwendung und teures Argon2-Hashing vor der
Prüfung eines öffentlichen Einladungscodes. Alle drei besitzen
Regressionstests.

Android implementiert Onboarding und Serverprüfung, Authentik-URL-Header-Auth,
Setup/Login/Einladung, rollenabhängige Hauptnavigation, Periodenstart,
Basis-Tageslog für Blutung, Schmerz, Stimmung, Energie, Schlaf und Symptome,
Offline-Queue, Sync, Partner-Dashboard, Check-in-Anfrage, Kalender, Verlauf,
Einladung/Freigaben, Theme und Screenshot-Schutz. Logout und bestätigter
Serverwechsel löschen lokale Gesundheitsdaten und Secrets auch bei fehlender
Serververbindung.

## Ausgeführte Prüfungen

### Backend

- ESLint: erfolgreich
- Jest Unit: 12 Suites, 35 Tests, erfolgreich nach dem finalen Security-Hardening
- TypeScript-/Nest-Build: erfolgreich
- Prisma-Schema: erfolgreich validiert
- echter PostgreSQL-18-E2E-Kernablauf: 1 Suite/1 zusammenhängender Test,
  erfolgreich; enthält parallelen Refresh, vollständige Haushaltslöschung und
  erneuten Setup-Status
- `npm audit --omit=dev`: 0 bekannte Meldungen
- vollständiger Abhängigkeitsbaum: eine moderate transitive `ajv`-Meldung nur
  über Entwicklungs-/Lint-/CLI-Abhängigkeiten
- OpenAPI-Ausgabe reproduzierbar geprüft; der aktuelle generierte Kotlin-Client
  wurde nach dem letzten Schema-Hardening mit JDK 21 erfolgreich kompiliert.

Gezielte Unit-/E2E-Regressionen prüfen strikte Sync-Payloadvalidierung, die kurze
In-Progress-Idempotenz-Lease mit Recovery, Haushaltsisolation im Pull-/Push-Sync,
Create-Payload-Konflikte, Einladungslaufzeit-Policy, CSV-Formelschutz, den
gültigen Einsatz der Tracker-IANA-Zeitzone und die fail-closed Partnerphase. Der
finale OpenAPI-Spec-Hash lautet
`9868fb2f45fa2f3ed76e9f9acfa726b16591872f7e16b77a42444c6a760bfb7d`.

Backend-Lint, Unit-Tests, Build, Prisma-Validierung, OpenAPI-Hashprüfung und E2E
wurden nach diesen Änderungen erneut erfolgreich ausgeführt. Prisma meldete im
schlanken Node-Testcontainer lediglich eine OpenSSL-Erkennungswarnung; Build,
Validierung und E2E waren erfolgreich.

### Android

- `testLocalDebugUnitTest`: 17/17 JVM-Tests erfolgreich
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
- Größe: 24.219.463 Bytes
- SHA-256: `8ec7dab3fee58a65452393887c60e82c61ac27a2d49e0c7966d810d832d19533`
- `sha256sum -c artifacts/SHA256SUMS`: erfolgreich

Ein unsigned Production-Release-Build wurde versucht, konnte auf dem Host wegen
wiederholter Gradle-Daemon-/Speicherabbrüche aber nicht erfolgreich beendet
werden. Es existiert deshalb kein `lunara-release-unsigned.apk`. Die
Production-Debug-APK ist tatsächlich installierbar, besitzt aber den Android-
Debugsignierschlüssel und ist nicht für einen öffentlichen Store gedacht.

### Container und Live-Deployment

- Der eigene Stack läuft unter `/docker/lunara`; API und PostgreSQL sind gesund.
- Zwei Prisma-Migrationen wurden erfolgreich angewendet. Der abgeschlossene
  Migrator mit Exitcode 0 ist der erwartete Zustand.
- API und PostgreSQL veröffentlichen keine Host-Ports. Die API läuft als
  UID/GID `10001:10001`, mit read-only Root-Dateisystem,
  `no-new-privileges` und vollständigem Capability-Drop.
- Authentik-Provider und Pangolin-Ressource wurden additiv eingerichtet. Beide
  getrennten Gateway-Konten bestanden den lokalen Pre-DNS-Test bis zur gültigen
  Lunara-Metaantwort; ohne Anmeldung antwortet der Gateway mit HTTP 302.
- Ein echtes Backup wurde erstellt, seine Archivprüfsummen wurden validiert und
  `pg_restore --list` konnte den Dump vollständig lesen. Ein destruktiver Restore
  wurde auf der Live-Instanz bewusst nicht ausgeführt.
- Kein bestehender Heimserver-Dienst einschließlich Emby wurde neu gestartet.
- Noch nicht geprüft ist der Zugriff über die normale Domain ohne lokale
  Resolve-Hilfe; dafür fehlt ausschließlich der Cloudflare-DNS-Eintrag.

### Privates GitHub-Repository

Wird nach erfolgreicher GitHub-Authentifizierung ergänzt. Echte `.env`,
Gateway-Zugangsdaten, Tokens, Datenbankdumps und Infrastruktur-Backups werden
nicht hochgeladen.

## Bekannte Funktionsgrenzen

- Keine Android-UIs für Gerätesitzungen, Care Preferences, eigene
  Symptome/Tipps, Check-in-Antworten, vollständige Freigabematrix,
  Export/Import, Kontolöschung oder Haushalt verlassen
- Der Android-Basis-Tageslog umfasst Blutung, Schmerz, Stimmung, Energie, Schlaf
  und Symptome. Notiz, Basaltemperatur, Ovulationstest sowie Korrektur/Löschen
  fehlen in der UI; ebenso Zyklusprofil, Sondermodi, Vorhersagepause und
  Fertilitätsaktivierung.
- Kalender zeigt den aktuellen Monat; Korrektur/Löschen und Monatwechsel sind
  noch nicht als vollständige UI vorhanden
- Keine lokalen Benachrichtigungen, ntfy, Biometrie, Widget, Demo-Modus oder
  reduzierte-Bewegungs-Einstellung
- Android konsumiert SSE noch nicht und nutzt WorkManager/manuellen Sync
- Der JSON-Export deckt Tracker-Kerndaten, aber nicht sämtliche Haushalts-,
  Sitzungs-, Audit-, Tipp- und Check-in-Datensätze ab
- Der Import verarbeitet Perioden/Tageslogs aus JSON und erkennt Duplikate
  gegen bereits vorhandene aktive Datensätze. Duplikate innerhalb desselben
  neuen Import-Payloads werden nicht gesondert zusammengeführt.
- Der passwortgeschützte Export ist ein AES-256-GCM-verschlüsseltes
  `.lunarabackup`-JSON-Format, kein ZIP. Es gibt keinen generischen CSV-
  Zuordnungsassistenten, verschlüsselten Direktimport oder Undo-Endpoint.
- Serverseitig kann ein Partner das eigene Konto löschen; ein Tracker kann nur
  den gesamten Haushalt löschen. Rollenübertragung, Tracker-Einzellöschung und
  ein eigener Haushalt-verlassen-Ablauf fehlen.
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

## Verbleibende Schritte zum ersten produktiven App-Start

1. Cloudflare-DNS für `lunara.3li361.de` gemäß `DEPLOYMENT.md` ergänzen.
2. Beide Gateway-Konten danach ohne lokale Resolve- oder TLS-Ausnahme erneut
   prüfen.
3. Eine der lokalen `gateway-a.url`/`gateway-b.url`-Dateien ausschließlich auf
   dem Server öffnen und in Android eingeben; niemals teilen oder in QR kodieren.
4. Den `BOOTSTRAP_TOKEN` lokal aus `/docker/lunara/.env` verwenden und das erste
   Tracker-Konto anlegen.
5. Über die App einen Einmalcode erzeugen und das Partnerkonto mit eigenen
   Zugangsdaten anlegen.
6. Gateway- und Lunara-Passwörter getrennt sichern, `APP_MASTER_KEY` separat
   verschlüsselt offline verwahren und das erste Backup prüfen.
