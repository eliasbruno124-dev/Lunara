# Lunara – Abschlussbericht

Stand: 13. August 2026

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

Für Version 1.0.2 wurden zusätzlich Login, Refresh-Rotation und
Passwortwechsel pro Benutzer transaktionsgebunden serialisiert. Dadurch kann
kein bereits mit dem alten Passwort geprüfter Login nach einem Passwortwechsel
noch eine Sitzung anlegen. Android schützt Anmeldung, Refresh, Logout,
Serverwechsel und lokale Sync-Worker über gemeinsame Credential-/Sync-Gates;
alte Netzwerkantworten können weder Secrets noch Authentik-Cookies nach einem
Wipe wiederherstellen.

Android implementiert Onboarding und Serverprüfung, Authentik-URL-Header-Auth,
Setup/Login/Einladung, rollenabhängige Hauptnavigation, Periodenstart,
Basis-Tageslog für Blutung, Schmerz, Stimmung, Energie, Schlaf und Symptome,
Offline-Queue, automatische Vordergrund-/WorkManager-Synchronisierung,
Partner-Dashboard, Check-in-Anfrage, Kalender, Verlauf, Einladung/Freigaben,
Theme, Screenshot-Schutz und persönlichen Lunara-Passwortwechsel in den
Einstellungen. Logout und bestätigter
Serverwechsel löschen lokale Gesundheitsdaten und Secrets auch bei fehlender
Serververbindung.

## Ausgeführte Prüfungen

### Backend

- ESLint: erfolgreich
- Jest Unit: 13 Suites, 38 Tests, erfolgreich nach dem finalen Security-Hardening
- TypeScript-/Nest-Build: erfolgreich
- Prisma-Schema: erfolgreich validiert
- echter PostgreSQL-18-E2E-Kernablauf: 1 Suite/1 zusammenhängender Test,
  erfolgreich; enthält parallelen Refresh, vollständige Haushaltslöschung und
  erneuten Setup-Status sowie Passwortwechsel, Audit und den Erhalt der
  aktuellen Tokenfamilie
- `npm audit --omit=dev`: 0 bekannte Meldungen
- vollständiger Abhängigkeitsbaum: eine moderate transitive `ajv`-Meldung nur
  über Entwicklungs-/Lint-/CLI-Abhängigkeiten
- OpenAPI-Ausgabe reproduzierbar geprüft; der aktuelle generierte Kotlin-Client
  wurde nach dem letzten Schema-Hardening mit JDK 21 erfolgreich kompiliert.

Gezielte Unit-/E2E-Regressionen prüfen strikte Sync-Payloadvalidierung, die kurze
In-Progress-Idempotenz-Lease mit Recovery, Haushaltsisolation im Pull-/Push-Sync,
Create-Payload-Konflikte, Einladungslaufzeit-Policy, CSV-Formelschutz, den
gültigen Einsatz der Tracker-IANA-Zeitzone und die fail-closed Partnerphase.
OpenAPI-Spezifikation SHA-256: `74d69a46297d954fa46b42b3a9c4d20911486af3205090c4c1a5afab48d1e5bc`.

Backend-Lint, Unit-Tests, Build, Prisma-Validierung, OpenAPI-Hashprüfung und E2E
wurden nach diesen Änderungen erneut erfolgreich ausgeführt. Prisma meldete im
schlanken Node-Testcontainer lediglich eine OpenSSL-Erkennungswarnung; Build,
Validierung und E2E waren erfolgreich.

### Android

- `testLocalDebugUnitTest`: 35/35 JVM-Tests erfolgreich
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
- App-Version: `1.0.2` (`versionCode` 3)
- Standardserver: leer; die persönliche Header-Auth-URL wird bewusst beim
  ersten Start eingegeben
- Klartext-HTTP: deaktiviert
- Größe: 24.455.313 Bytes
- SHA-256: `1f07d1d58b97dca2b78d12959a287b91003299418bdb5f64386ff3ea04ee7604`
- `sha256sum -c artifacts/SHA256SUMS`: erfolgreich
- APK-ZIP-Struktur und APK-Signature-Scheme-v2-Signatur: erfolgreich geprüft
- Der lokale Debug-Schlüssel liegt mit Modus `0600` unter dem Git-ignorierten
  Buildzustand; APK- und Keystore-Fingerabdruck wurden identisch geprüft. Die
  frühere 1.0.0-Test-APK hatte noch eine flüchtige andere Debug-Signatur und
  muss deshalb einmal deinstalliert werden. Eine installierte 1.0.1 kann wegen
  des identischen Zertifikats direkt auf 1.0.2 aktualisiert werden.
  Zertifikat SHA-256: `f53698206074d4e2b0ff7b6e7b134a58919a358f2054fb7d4ba35df77ac69328`.
- Ein zusätzlicher Scan der dekomprimierten APK bestätigte, dass keine der
  beiden echten Header-Auth-URLs und keine Secret-Zuweisung eingebettet ist.

Ein unsigned Production-Release-Build war zuvor versucht worden, konnte auf dem
Host wegen wiederholter Gradle-Daemon-/Speicherabbrüche aber nicht erfolgreich
beendet werden. Während der finalen 1.0.2-Bereitstellung lief eine aktive
Emby-Transkodierung und der freie Hostspeicher lag unter der neu eingeführten
4-GiB-Sicherheitsgrenze; deshalb wurde kein weiterer schwerer Android-Build
parallel erzwungen. Es existiert kein `lunara-release-unsigned.apk`. Die
Production-Debug-APK ist tatsächlich installierbar, besitzt aber den Android-
Debugsignierschlüssel und ist nicht für einen öffentlichen Store gedacht.

### Container und Live-Deployment

- Der eigene Stack läuft unter `/docker/lunara`; API und PostgreSQL sind gesund.
- Das ressourcenbegrenzte API-Image `lunara-api:1.0.2` wurde erfolgreich gebaut,
  drei Prisma-Migrationen wurden angewendet und ausschließlich der API-Container
  neu erstellt. Health und Readiness antworteten danach mit HTTP 200.
- Die Metaantwort meldete Version 1.0.2 und `setupRequired: false`, setzte
  `Cache-Control: no-store, max-age=0` sowie `Pragma: no-cache`; der neue
  Passwort-Endpunkt antwortete ohne Lunara-Sitzung erwartungsgemäß mit HTTP 401.
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
Push als privat geprüft. Das private Release `v1.0.2` enthält die geprüfte
`lunara-debug.apk` mit 24.455.313 Bytes.

- SHA-256: `1f07d1d58b97dca2b78d12959a287b91003299418bdb5f64386ff3ea04ee7604`.

Echte `.env`, Gateway-Zugangsdaten, Tokens, Datenbankdumps und
Infrastruktur-Backups wurden nicht hochgeladen.

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
- Android konsumiert SSE noch nicht. Es synchronisiert automatisch nach
  Anmeldung und lokalen Änderungen, beim Öffnen, einmal pro Minute im
  Vordergrund sowie alle sechs Stunden über WorkManager; der manuelle Knopf
  bleibt als ausdrücklicher Neuversuch nach terminalen Fehlern erhalten.
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

1. Die eigene lokale Datei `/docker/lunara/dunja-headerauth.url` oder
   `/docker/lunara/elias-headerauth.url` ausschließlich auf dem Server öffnen
   und in Android eingeben; niemals teilen oder in einen QR-Code aufnehmen.
2. Der Live-Server ist bereits eingerichtet (`setupRequired: false`). Mit dem
   persönlichen Lunara-Konto anmelden; der Bootstrap-Token wird hier nicht
   mehr benötigt.
3. Falls das zweite Lunara-Konto noch fehlt, über das Tracker-Konto einen
   Einmalcode erzeugen und mit eigenem Benutzernamen sowie eigenem Passwort
   beitreten.
4. Nur auf einer absichtlich vollständig leeren Neuinstallation wird einmalig
   der lokale `BOOTSTRAP_TOKEN` zur Anlage des ersten Tracker-Kontos benötigt.
5. Gateway- und Lunara-Passwörter getrennt sichern, `APP_MASTER_KEY` separat
   verschlüsselt offline verwahren und das erste Backup prüfen.
