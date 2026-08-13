# Lunara – Abschlussbericht

Stand: 12. August 2026

Dieser Bericht enthält die Ergebnisse der finalen Prüfkette und des
Live-Deployments. Aussagen mit „erfolgreich“ beruhen ausschließlich auf wirklich
ausgeführten Befehlen. Der geprüfte Quellstand und die APK sind zusätzlich in
einem privaten GitHub-Repository veröffentlicht.

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
- App-Version: `1.0.1` (`versionCode` 2)
- Standardserver: `https://lunara.3li361.de/`
- Klartext-HTTP: deaktiviert
- Größe: 24.219.939 Bytes
- SHA-256: `df487a30b937f11109ba735418455cfcafd19cd6583b49cba7f55e70f33b488f`
- `sha256sum -c artifacts/SHA256SUMS`: erfolgreich
- APK-ZIP-Struktur und APK-Signature-Scheme-v2-Signatur: erfolgreich geprüft

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
- Authentik-Provider und Pangolin-Ressource wurden additiv eingerichtet. Der
  Cloudflare-CNAME `lunara.3li361.de` wurde auf beiden autoritativen
  Nameservern verifiziert. Let’s Encrypt stellte ein Zertifikat mit passendem
  SAN aus, gültig vom 12. August bis 10. November 2026. Ohne Anmeldung
  antwortet der öffentliche Gateway mit HTTP 302 zu Authentik; beide getrennten
  Gateway-Konten bestanden anschließend den vollständigen Test ohne lokale
  DNS- oder TLS-Ausnahme bis zur gültigen Lunara-Metaantwort.
- Dunjas vorhandenes Authentik-Konto `Dune` und das Konto `Elias` wurden nach
  einem validierten Authentik-Datenbankbackup explizit für die Lunara-Anwendung
  freigegeben. Frische Policy-Engine-Prüfungen lieferten für beide `allowed`.
  Die beiden getrennten geheimen Header-Auth-URLs wurden lokal mit Modus `0600`
  abgelegt und jeweils Ende-zu-Ende geprüft. Es war kein Containerneustart
  erforderlich.
- Ein echtes Backup wurde erstellt, seine Archivprüfsummen wurden validiert und
  `pg_restore --list` konnte den Dump vollständig lesen. Ein destruktiver Restore
  wurde auf der Live-Instanz bewusst nicht ausgeführt.
- Es wurde kein manueller Emby-/Mediencontainer-Neustart ausgeführt und keine
  Emby-Datei geändert. Während des Android-Builds löste der Kernel jedoch am
  12. August 2026 um 21:02 Uhr CEST einen globalen OOM aus und beendete
  `EmbyServer`. Docker startete den Dienst durch dessen bestehende
  `restart: always`-Policy automatisch; der öffentliche Emby-Info-Endpunkt
  lieferte danach wieder HTTP 200. Der parallele Gradle-/Docker-Build war sehr
  wahrscheinlich der unmittelbare zusätzliche Speicherdruck. Als Folgemaßnahme
  besitzt `scripts/build-android.sh` nun einen 4-GiB-Hostspeicher-Vorabcheck,
  einen einzelnen Gradle-Worker sowie feste CPU-, RAM- und Swap-Grenzen für den
  Buildcontainer.
- Die erste ACME-Anforderung war vor dem DNS-Eintrag erwartungsgemäß an
  `NXDOMAIN` gescheitert. Nach einem konsistenten Pangolin-Datenbankbackup wurde
  ausschließlich die neue Lunara-Ressource kurz deaktiviert und wieder
  aktiviert. Dadurch wurde die Zertifikatsanforderung ohne Containerneustart
  erneut ausgelöst und erfolgreich abgeschlossen.

### Privates GitHub-Repository

Das Repository `https://github.com/eliasbruno124-dev/Lunara` wurde vor dem
ersten Push als privat geprüft. Das private Release `v1.0.1` enthält die
korrigierte `lunara-debug.apk` mit 24.219.939 Bytes. Echte `.env`, Gateway-
Zugangsdaten, Tokens, Datenbankdumps und Infrastruktur-Backups wurden nicht
hochgeladen.

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

1. Eine der lokalen `gateway-a.url`/`gateway-b.url`-Dateien ausschließlich auf
   dem Server öffnen und in Android eingeben; niemals teilen oder in QR kodieren.
2. Den `BOOTSTRAP_TOKEN` lokal aus `/docker/lunara/.env` verwenden und das erste
   Tracker-Konto anlegen.
3. Über die App einen Einmalcode erzeugen und das Partnerkonto mit eigenen
   Zugangsdaten anlegen.
4. Gateway- und Lunara-Passwörter getrennt sichern, `APP_MASTER_KEY` separat
   verschlüsselt offline verwahren und das erste Backup prüfen.
