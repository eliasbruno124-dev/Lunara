# Lunara 1.0.3 – Abschlussbericht

Stand: 13. August 2026

Der Backend-, Datenbank-, Gateway- und Android-Stand 1.0.3 ist gebaut und
geprüft; Backend und Datenbank sind produktiv ausgerollt. Eine installierbare,
v2-signierte Production-Debug-APK wurde tatsächlich erzeugt. Der aktuelle
1.0.3-Stand samt APK wurde im privaten GitHub-Branch
`agent/authentik-human-access` veröffentlicht. Ein separates GitHub-Release-Tag
für 1.0.3 wurde nicht erzeugt.

## Architektur

- Monorepo mit nativer Kotlin-/Compose-App, NestJS-/Prisma-API,
  OpenAPI-Kotlin-Referenzclient, Docker-/Traefik-Infrastruktur, CI und
  Betriebsdokumentation
- PostgreSQL 18 als einzige erforderliche Serverabhängigkeit; Redis ist nicht
  notwendig
- eine Serverinstanz für mehrere Paare: Jede neue Tracker-Registrierung erhält
  eine eigene, unsichtbare Mandanten-/Beziehungs-ID; sämtliche Datenzugriffe
  werden serverseitig daran gebunden
- pro Beziehung genau ein `TRACKER` und ein `PARTNER`; beide besitzen getrennte
  Lunara-Konten und Passwörter
- kein sichtbares „Haushalt“-Konzept in der Android-Oberfläche; die bestehende
  interne Datenstruktur bleibt ausschließlich als Sicherheitsgrenze erhalten
- API ohne Host-Port, öffentlich ausschließlich über Pangolin und den
  Authentik-Proxy erreichbar
- Room/SQLCipher, Android Keystore, WorkManager, lokale Mutation Queue und
  clientseitige UUIDs für Offline-Nutzung

## Tatsächlich umgesetzt

Der serverseitige Kernablauf umfasst leeren Server, einmaligen Bootstrap,
Tracker-Konto, optionale weitere Tracker-Registrierungen, Partner-Einladung,
Partnerkonto, Periodeneintrag, Bereichsprognose, Freigabeprojektion,
idempotenten Offline-Sync und Partner-Dashboard. Hinzu kommen Tageslogs,
vordefinierte und eigene Symptome, Profile, Care Preferences, mindestens 50
DE/EN-Tipps, Check-ins, Export/Import, Sitzungsverwaltung, Audit-Ereignisse,
SSE und Admin-CLI.

Partner-Einladungen besitzen standardmäßig kein Ablaufdatum. Sie sind trotzdem
nur einmal verwendbar, jederzeit widerrufbar und werden ausschließlich als
gehashter Wert gespeichert. Der QR-Code enthält nur den Einladungscode, nie
Server-, Lunara- oder Authentik-Passwörter. Gleichzeitige Erzeugung und
Einlösung werden innerhalb der Beziehung serialisiert; ein Widerruf greift
serverseitig.

Im Android-Quellstand 1.0.3 sind folgende neue Abläufe implementiert:

- normale Serverbasis `https://lunara.3li361.de` ohne Header-Auth-Daten in der
  URL
- Registrierung weiterer Tracker und automatische Anlage ihrer unsichtbar
  getrennten Beziehung
- Onboarding ohne sichtbares Haushaltsfeld
- permanente Partner-Einladung mit QR-Code, Anzeige aktiver Einladung und
  Widerruf
- rollenabhängige Oberfläche, automatische Synchronisierung nach Anmeldung und
  Mutation, beim Öffnen, minütlich im Vordergrund und sparsam per WorkManager
- persönlicher Passwortwechsel in den Einstellungen
- Korrektur und bestätigtes Löschen eines Periodenstarts in „Heute“ und
  „Verlauf“, einschließlich lokalem Soft Delete und Offline-Warteschlange
- gehärtete Reihenfolge mehrerer Mutationen desselben Datensatzes sowie Schutz
  davor, dass veraltete Serverantworten eine lokale Löschung wiederbeleben
- sichtbare, bestätigungspflichtige Konfliktauflösung: lokale Fassungen bleiben
  bis zur Entscheidung erhalten; während des Server-Neuladens sind betroffene
  Einträge schreibgeschützt
- fortlaufender automatischer Queue-Drain ohne Retry-Verzögerung nach Erfolg
  sowie korrektes serverseitiges Leeren optionaler Tageslog-Felder

Die Android-Prüfkette einschließlich APK-Erzeugung wurde auf diesem Quellstand
tatsächlich ausgeführt. Instrumentierte UI-Tests wurden kompiliert, mangels
Gerät oder Emulator aber nicht ausgeführt.

## Ausgeführte Prüfungen

### Backend

Auf dem finalen 1.0.3-Quellstand wurden die entsprechenden npm-Skripte für
Prisma-Erzeugung/-Validierung, ESLint, Jest und Nest-Build tatsächlich
ausgeführt:

- Prisma-Client: erfolgreich erzeugt
- Prisma-Schema: erfolgreich validiert
- ESLint: erfolgreich
- Jest: **14/14 Suites, 59/59 Tests**, erfolgreich
- TypeScript-/Nest-Build: erfolgreich
- `npm audit --omit=dev`: **0 bekannte Schwachstellen**

Der erweiterte End-to-End-Test über `./scripts/e2e.sh` prüft unter anderem:

- Setup-Modus und dauerhafte Bootstrap-Sperre nach der Ersteinrichtung
- Login, Refresh-Rotation, Passwortwechsel und Sitzungswiderruf
- parallele Registrierungen mehrerer Tracker mit strikt getrennten Beziehungen
- Isolation bei Perioden, eigenen IDs sowie Pull-/Push-Sync
- parallele Einladungserstellung, Widerruf und Schutz gegen doppelte Einlösung
- Partnerrolle ohne Schreibrecht auf Periodendaten
- Periodeneintrag, deterministische Prognose und Freigabeprojektion
- Notizverschlüsselung, Care-/Tippfilter und Export/Import
- Soft Deletes, Sync-Idempotenz und Versionskonflikte

Ein früherer 1.0.3-Zwischenstand bestand den echten PostgreSQL-18-Lauf mit
**1/1 Suite und 1/1 zusammenhängendem Test**. Dieser Lauf entstand vor den
finalen Migrationen für den persistenten Serverstatus und den eindeutigen
aktiven Periodenstart. Der inzwischen erweiterte E2E-Test wurde auf dem
abschließenden Quellstand **nicht erneut ausgeführt**; das frühere Ergebnis ist
daher kein finaler E2E-Abschlussnachweis.

### OpenAPI

Die Spezifikation 1.0.3 wurde exportiert und der Kotlin-Referenzclient mit dem
festgelegten OpenAPI Generator **7.24.0** neu erzeugt. `npm run openapi:check`
bestätigte reproduzierbar die gespeicherten Spezifikations-, Konfigurations-
und Ausgabebaum-Hashes.

OpenAPI-Spezifikation SHA-256: `e478d9a0474b401555736cbaf4444db6d3555e3c02fa922c1a4dd27f12e190ec`.

Der separate Compile-Versuch des generierten Kotlin-Referenzclients in einem
auf 768 MiB begrenzten Container war **nicht erfolgreich**. memcg beendete den
Gradle-Daemon wegen Speichermangels. Es wurde dabei kein Dienst beeinträchtigt
oder neu gestartet. Dieser Referenzclient ist kein Laufzeitbestandteil der
Android-App; diese nutzt ihre eigene typisierte Retrofit-Schicht.

### Android

Der 1.0.3-Quellstand trägt `versionName` 1.0.3 und `versionCode` 4. Sein
Production-Standardserver ist `https://lunara.3li361.de`, Klartext-HTTP bleibt
deaktiviert.

Die ressourcenbegrenzte 1.0.3-Prüfkette wurde tatsächlich ausgeführt:

- `testLocalDebugUnitTest`: **63/63 JVM-Tests**, erfolgreich
- `lintLocalDebug`: erfolgreich
- `lintProductionDebug`: erfolgreich
- `compileProductionDebugAndroidTestKotlin`: erfolgreich
- `assembleProductionDebug`: erfolgreich
- APK-ZIP-Struktur: erfolgreich geprüft
- APK-Signatur: APK Signature Scheme v2, erfolgreich geprüft

Das erzeugte Android-Artefakt liegt unter:

```text
/root/Lunara/artifacts/lunara-debug.apk
```

- Paket: `de.lunara.app.debug`
- `versionName`: 1.0.3
- `versionCode`: 4
- `minSdk`: 26; `targetSdk` und `compileSdk`: 37
- 24.362.451 Bytes
- SHA-256: `4dac957e020684f3d5ee5699fdc584a0f68393a58a76452801a21831616dd416`
- Signaturzertifikat SHA-256:
  `f53698206074d4e2b0ff7b6e7b134a58919a358f2054fb7d4ba35df77ac69328`

Die instrumentierten Compose-Tests wurden mangels Emulator oder physischem
Testgerät nicht ausgeführt. Reale Keystore-, SQLCipher- und WorkManager-
Interaktionen bleiben deshalb auf einem Gerät zu prüfen.

## Container und Live-Deployment

Vor dem finalen Update wurde folgender eng benannter PostgreSQL-Custom-Dump
erstellt und mit `pg_restore --list` validiert:

```text
/var/backups/lunara/lunara-pre-server-state-20260813T152814Z.dump
```

- Größe: 62.151 Bytes
- SHA-256: `1279c7a00f14c42f0528c1dd5b67370d7f5274af707a708e705a28a12135f540`

Anschließend wurde das API-Image `lunara-api:1.0.3` mit einer Obergrenze von
1 GiB RAM und 0,5 CPU tatsächlich gebaut. Image-ID:
`sha256:235794dfed4abaf3a0eb055d196738c56530445f742c7a601cfac5989340e06d`.
Das Audit der Produktionsabhängigkeiten meldete 0 bekannte Schwachstellen.

Alle sechs Prisma-Migrationen einschließlich persistentem `ServerState` und
eindeutigem aktivem Periodenstart je Person/Datum wurden angewendet. Nur der
Lunara-API-Container wurde für das Update neu erstellt. Der verifizierte
Live-Stand lautet:

- `/health`: HTTP 200
- `/ready`: HTTP 200
- `/api/v1/meta`: Version 1.0.3, `setupRequired: false`,
  `registrationEnabled: true`
- sechs angewendete Migrationen
- ein vorhandener Benutzer, eine Mitgliedschaft und ein Periodeneintrag
- Dunja weiterhin aktiv mit Rolle `TRACKER`
- API und PostgreSQL ohne veröffentlichte Host-Ports
- API als UID/GID `10001:10001`, read-only Root-Dateisystem,
  `no-new-privileges` und vollständiger Capability-Drop
- interner `/`-Aufruf leitet auf `/api/v1/meta` weiter
- `BOOTSTRAP_TOKEN` aus der Live-`.env` entfernt; die vorherige Konfiguration
  bleibt ausschließlich serverlokal als Datei mit Modus `0600` gesichert
- ein syntaktisch gültiger Dummy-Setupversuch ohne Token endet mit HTTP 401;
  `/meta` bleibt bei `setupRequired: false`

Der vorhandene Datenbestand blieb erhalten. Ein destruktiver Voll-Restore
wurde auf der Live-Instanz bewusst nicht als Test ausgeführt.

Es gab bei diesem Update **keinen Neustart von Emby, Authentik, Pangolin oder
PostgreSQL**. Die Container-IDs dieser Dienste blieben unverändert; nur die
Lunara-API wurde gezielt neu erstellt.

## Domain, Authentik und Anmeldung

Die Android-App verwendet ausschließlich diese normale Basisadresse:

```text
https://lunara.3li361.de
```

In der App stehen weder Benutzername noch Passwort in der URL. Authentik bleibt
im öffentlichen Pfad, überspringt seine Browser-Anmeldung aber ausschließlich
für `/api/v1`. Dort erzwingt Lunara selbst je nach Endpoint Rate-Limits,
Passwort- oder Bearer-Authentifizierung, Rolle, Eigentum und Beziehungsgrenze.
OpenAPI und Browserseiten bleiben Authentik-geschützt.

Vor der Provideränderung wurde die Authentik-Datenbank gesichert und validiert;
geändert wurde nur der Lunara-spezifische Skip-Ausdruck. Die öffentliche
Prüfung ergab:

- `/api/v1/meta` ohne Authentik-Anmeldung: HTTP 200
- `/api/v1/me` ohne Lunara-Token: HTTP 401 und keine Daten
- OpenAPI ohne Authentik-Anmeldung: HTTP 302 zu Authentik
- getrennte serverlokale administrative Header-Prüfungen für Dune und Elias:
  jeweils HTTP 200 auf der geschützten Dokumentation
- tatsächlicher Lunara-Login von Dunja, Profilabruf und Logout: erfolgreich

Die geheimen Header-Auth-URLs bleiben ausschließlich serverlokal und werden
weder in der App noch in diesem Bericht ausgegeben.

## Dunjas Passwort-Reset

Dunjas persönliches **Lunara-Passwort** wurde zurückgesetzt. Zuvor wurde ein
validierter Lunara-Datenbank-Dump erstellt. Alle vorhandenen Lunara-Sitzungen
wurden widerrufen; Rolle und Gesundheitsdaten blieben unverändert. Das neue
Passwort liegt ausschließlich serverlokal und root-lesbar unter:

```text
/docker/lunara/dunja-lunara-password.txt
```

Dateimodus: `0600`. Passwort und Hash werden nicht in Git, Dokumentation oder
Chat ausgegeben. Dunjas Authentik-Passwort wurde nicht geändert.

## Privates GitHub-Repository

Das Repository ist privat:

```text
https://github.com/eliasbruno124-dev/Lunara
```

Der geprüfte 1.0.3-Quellstand und die neue 1.0.3-APK wurden mit
Implementierungscommit `9a9b35a5beadb2664935517feba5c9bfeadb0b9e` im privaten Branch
`agent/authentik-human-access` veröffentlicht:

```text
https://github.com/eliasbruno124-dev/Lunara/tree/agent/authentik-human-access
https://github.com/eliasbruno124-dev/Lunara/raw/refs/heads/agent/authentik-human-access/artifacts/lunara-debug.apk
```

Der Download erfordert wegen des privaten Repositorys eine GitHub-Anmeldung mit
Zugriffsrecht. Ein formales GitHub-Release `v1.0.3` wurde nicht angelegt; die
APK im Branch ist jedoch das lokal gebaute und hashgeprüfte Artefakt. Echte `.env`-Dateien,
Authentik-Zugangsdaten, Tokens, Passwörter, Datenbankdumps und
Infrastruktur-Backups sind nicht für Git vorgesehen. Der projektinterne
Secret-Scan bestand ohne Fund; `gitleaks` war auf dem Host nicht installiert
und wird daher nicht als ausgeführt bezeichnet.

### GitHub-Actions-Status nach der Veröffentlichung

Für den finalen Branch-Commit `ea06eeb0fe0f467a194d4be32c840064b22ef6da`
wurden die vorhandenen Workflows tatsächlich gestartet. Dependency Review war
erfolgreich; Container and dependency security lief beim Abschluss dieses
Berichts noch. Android CI brach vor Build, Lint und Tests ab, weil auf dem
GitHub-Runner `sdkmanager` nicht im Pfad vorhanden war. Backend CI brach beim
Lint ab, weil die Prisma-Typen laut Workflow erst nach dem Lint generiert
werden; der getrennte E2E-Job fand im Testcontainer die Prisma-CLI nicht.
Diese roten Workflow-Läufe werden nicht als Produkttestfehler umgedeutet und
auch nicht verschwiegen. Die oben dokumentierten lokalen Backend-/Android-
Prüfungen wurden tatsächlich erfolgreich ausgeführt. Die CI-Workflows wurden
ohne ausdrückliche Freigabe nicht verändert.

## Bekannte Funktionsgrenzen

- Instrumentierte Android-Tests wurden nur kompiliert, nicht auf einem Gerät
  oder Emulator ausgeführt.
- Keine Android-UIs für Gerätesitzungen, vollständige Freigabematrix, Care
  Preferences, eigene Symptome/Tipps, Check-in-Antworten, Export/Import,
  Kontolöschung oder das Trennen einer Beziehung
- Der Android-Basis-Tageslog umfasst Blutung, Schmerz, Stimmung, Energie,
  Schlaf und Symptome. Notiz, Basaltemperatur, Ovulationstest sowie
  Tageslog-Korrektur/-Löschung fehlen in der UI; ebenso Zyklusprofil,
  Sondermodi, Vorhersagepause und Fertilitätsaktivierung.
- Periodenstarts lassen sich in „Heute“ und „Verlauf“ korrigieren und löschen;
  in der Kalenderansicht fehlen diese direkten Aktionen und der Monatwechsel
  noch.
- Keine lokalen Benachrichtigungen, ntfy, Biometrie, Widget, Demo-Modus oder
  Einstellung für reduzierte Bewegungen
- Android konsumiert SSE noch nicht. Der implementierte 1.0.3-Quellstand
  synchronisiert nach Anmeldung und Mutation, beim Öffnen, minütlich im
  Vordergrund sowie alle sechs Stunden über WorkManager.
- Der JSON-Export deckt Tracker-Kerndaten, aber nicht sämtliche Sitzungs-,
  Audit-, Tipp- und Check-in-Datensätze ab.
- Der Import verarbeitet Perioden/Tageslogs aus JSON und erkennt Duplikate
  gegen bereits vorhandene aktive Datensätze. Duplikate innerhalb desselben
  neuen Import-Payloads werden nicht gesondert zusammengeführt.
- Der passwortgeschützte Export ist ein AES-256-GCM-verschlüsseltes
  `.lunarabackup`-JSON-Format, kein ZIP. Generischer CSV-
  Zuordnungsassistent, verschlüsselter Direktimport und Undo-Endpoint fehlen.
- Serverseitig kann ein Partner das eigene Konto löschen; ein Tracker kann in
  Version 1 nur die gesamte interne Beziehung löschen. Rollenübertragung und
  Tracker-Einzellöschung fehlen.
- Der OpenAPI-Kotlin-Referenzclient wurde erzeugt und reproduzierbar geprüft;
  sein separater Compile-Versuch scheiterte jedoch an der
  768-MiB-Speichergrenze.
- Check-in-/Freigabe-/Profiländerungen erzeugen nicht in jedem Fall einen
  eigenen SSE-Eintrag; Check-in-Freitext ist nicht zusätzlich mit dem
  `APP_MASTER_KEY` verschlüsselt.
- Vollständige Branding-Umbenennung benötigt Anpassung von Package-ID,
  Übersetzungen und Assets zusätzlich zu den zentralen Tokens.

## Bekannte Sicherheitsgrenzen

Siehe `SECURITY.md`. Besonders relevant sind die notwendige Sicherung von
`APP_MASTER_KEY` zusammen mit Datenbankbackups, fehlende Android-Biometrie, der
unverschlüsselte interne Docker-Hop, Cursor-Aktivitätsmetadaten und die noch
nicht auf einem realen Gerät geprüften Keystore-/SQLCipher-/Wipe-Abläufe.

Die unbeabsichtigt entstandene Datei `services/api/core.102` ist über
`.gitignore` von Git ausgeschlossen. Sie bleibt bis zu einer ausdrücklichen
Löschfreigabe auf dem Server liegen und gehört nicht zum Produktartefakt.

Die aktivierte Tracker-Registrierung besitzt bewusst keine feste Kontenzahl,
bleibt aber rate-limitiert und erzeugt für jedes Konto eine neue serverseitige
Beziehungsgrenze. Diese Registrierung ersetzt keine administrative
Kapazitätsüberwachung oder regelmäßige Backups.

## Erster produktiver App-Start

1. Die geprüfte APK aus `artifacts/lunara-debug.apk` installieren und als
   Serveradresse ausschließlich `https://lunara.3li361.de` verwenden. Keine
   Header-Auth-URL und keine Zugangsdaten an die Domain anhängen.
2. Ein vorhandenes Lunara-Konto meldet sich mit eigenem Benutzernamen und
   eigenem Lunara-Passwort an. Für Dunja liegt das neu gesetzte Passwort in der
   oben genannten serverlokalen Datei.
3. Eine neue zyklustrackende Person wählt „Registrieren“. Der Server legt
   automatisch ihre unsichtbar isolierte Beziehung und die Rolle `TRACKER` an;
   ein Haushaltsname ist nicht erforderlich.
4. Der Tracker erzeugt in der App eine Partner-Einladung. Der Code/QR läuft
   zeitlich nicht ab, ist aber nur einmal verwendbar und kann jederzeit
   widerrufen werden.
5. Die Partnerperson verwendet denselben Server, den Einladungscode und ein
   eigenes Konto mit eigenem Passwort. Sie erhält ausschließlich die Rolle
   `PARTNER` in genau dieser Beziehung.
6. Der Bootstrap-Token wurde auf diesem bereits eingerichteten Server entfernt.
   Er ist nur für das allererste Tracker-Konto einer vollständig leeren
   Neuinstallation vorgesehen.
7. Nach der Anmeldung Freigaben prüfen, das erste aktuelle Backup kontrollieren
   und `APP_MASTER_KEY` getrennt verschlüsselt offline sichern.
