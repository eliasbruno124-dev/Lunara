# Lunara 1.1.4 – Einladungskorrektur

Stand: 8. September 2026. Server/PWA produktiv aktualisiert;
Android-Build einschließlich Signierung erfolgreich und im privaten
GitHub-Release v1.1.4 veröffentlicht. Alle sechs hochgeladenen Dateien wurden
gegen ihre lokalen Größen und SHA-256-Prüfsummen geprüft.

## Ergebnis und Architektur

Die vorhandene NestJS-/Prisma-/PostgreSQL-API, installierbare PWA und native
Compose-App bleiben erhalten. Einladungen funktionieren zusätzlich mit bereits
vorhandenen, leeren Einzelkonten: Anmeldung authentifiziert die Identität,
eine getrennte ausdrückliche Bestätigung verbindet anschließend den Partner.
Es werden keine bestehenden Gesundheitsdaten stillschweigend zusammengeführt
oder gelöscht. Rolle und Beziehung werden im Server geprüft und nicht aus
lokalen UI-Werten oder alten Token-Rollen übernommen.

Die statische `/invite`-Landeseite bewahrt den Code vor dem Authentik-Redirect
im lokalen Sitzungsspeicher. Der Code steht im Fragment, nicht in einem
Query-Parameter. Android unterstützt Code-/QR-Eingabe in Einstellungen und
speichert eine laufende OIDC-Einladung Keystore-gestützt. Lokale ungelöste
Mutationen verhindern die Annahme. Nach Erfolg laden beide Clients `/me` neu
und wechseln den lokalen Beziehungscache kontrolliert.

## Tatsächlich ausgeführte Prüfungen

- `npm run lint`: API und PWA erfolgreich.
- `npm test`: API 28 Suites / 198 Tests; PWA abschließend 13 Dateien / 50 Tests.
- `npm run prisma:migrate:deploy && npm run test:e2e`: acht Migrationen und
  zwei Szenarien auf einer frischen, isolierten PostgreSQL-18-Datenbank ohne
  veröffentlichte Ports erfolgreich. Keine Testdaten im produktiven Server.
- `npm run build`: API und PWA erfolgreich.
- OpenAPI-Snapshot exportiert, Kotlin-Client mit Generator 7.24.0 erneuert;
  `npm run openapi:check` erfolgreich.
- `npm audit`: keine bekannten Treffer nach gezielten Overrides für drei
  transitive Abhängigkeiten. Das ist keine Garantie auf Schwachstellenfreiheit.
- `docker build -t lunara-api:1.1.4 -f infra/docker/api.Dockerfile .`: erfolgreich.
- `scripts/secret-scan.sh`: gezielter und High-Entropy-Scan erfolgreich;
  kein lokales gitleaks installiert.
- Android: beide JVM-Testvarianten je 42 Suites / 205 Tests ohne Fehler;
  beide Lint-Varianten ohne Fehler, 63 beziehungsweise 62 Warnungen.
- Gradle: `testLocalDebugUnitTest testProductionDebugUnitTest lintLocalDebug
  lintProductionDebug compileProductionDebugAndroidTestKotlin
  assembleProductionDebug assembleProductionRelease bundleProductionRelease`
  erfolgreich. Anschließend signierter Release-Build mit dem vorhandenen,
  ausschließlich serverlokalen Upload-Key ebenfalls erfolgreich.

Während der Prüfung wurden ein ungültiger E2E-Testcode, gemeinsam verbrauchte
Test-Rate-Limits, Compose-Aufrufkontexte, eine Test-Typsignatur, ein zu neuer
URLDecoder-Overload und Parallelzugriffe bei der Rollenänderung korrigiert.
Produktive Rate-Limits und die Android-Mindestversion wurden nicht abgesenkt.
Ein alter Gradle-Transform ohne Metadaten wurde recoverbar in den privaten
Backupordner verschoben. Der anschließende Release-Build passierte den zuvor
fehlgeschlagenen DEX-Schritt erfolgreich.

## Android-Artefakte

Tatsächlich erzeugte Dateien im Arbeitsbaum
`/root/Lunara-invitation-fix/artifacts/`:

| Datei | Bytes | Zweck |
|---|---:|---|
| `lunara-release.apk` | 20.176.195 | signierte Nutzerinstallation |
| `lunara-debug.apk` | 25.036.200 | separate Testinstallation |
| `lunara-release-unsigned.apk` | 20.120.518 | eigene Signierung |
| `lunara-release-unsigned.aab` | 16.152.797 | unsigniertes Bundle |
| `lunara-play-upload.aab` | 16.184.357 | signiertes Upload-Bundle |

Release-Paket: `de.lunara.app`, Version 1.1.4, VersionCode 15, minSdk 26,
targetSdk 36, compileSdk 37. APK-Signatur mit `apksigner verify` erfolgreich
geprüft, AAB mit `jarsigner -verify`; kein Play-Store-Upload durchgeführt.
Die APK besitzt dieselbe Signatur wie die vorherige produktive Release-APK.
Bei der AAB-Prüfung weist JDK 21 auf das selbst signierte Zertifikat, fehlende
Zeitstempel und unterschiedliche Manifest-Reihenfolge bei ZIP-Stream-Lesern
hin. Die JAR-Signatur wird verifiziert; eine Play-Console-Prüfung dieses
Bundles wurde nicht durchgeführt. Empfohlenes Nutzerartefakt ist die APK.
SHA-256 der signierten Nutzer-APK:

`033af11fb40a14c2a29d75ac7f56729be65f1f2d6f9b5cc665ec3d2738c23d5f`

Die abschließende Sicherheitsprüfung lautet **ship**; keine bekannten
blockierenden Befunde verbleiben in diesem Änderungssatz.

## GitHub-Veröffentlichung

Quellstand und Artefakte: Commit `a7d591b`, Tag `v1.1.4`. Das Repository bleibt
privat. Builds/Tests wurden lokal ausgeführt; der gebündelte Quellcommit
verwendet `[skip ci]`, um keine zusätzlichen automatischen Build-Mails
auszulösen. Keine Pull Requests, Diskussionen oder Kommentare wurden angelegt.

Release:
https://github.com/eliasbruno124-dev/Lunara/releases/tag/v1.1.4

Empfohlene APK (GitHub-Anmeldung mit Repository-Zugriff erforderlich):
https://github.com/eliasbruno124-dev/Lunara/releases/download/v1.1.4/lunara-release.apk

Keine offenen Implementierungs- oder Veröffentlichungsaufgaben für diese
Einladungskorrektur. Die oben genannten Geräte-/Play-Validierungsgrenzen
bleiben ausdrücklich bestehen.

## Produktive Änderungen und Rückfallmöglichkeit

- Nur `lunara-api-1` wurde über den Lunara-Compose-Wrapper mit
  `up -d --no-deps api` erneuert. PostgreSQL und Authentik wurden nicht neu
  gestartet; der Hauptstack und Emby blieben unverändert.
- `/health` und `/ready` intern 200; Container gesund, UID/GID 10001,
  schreibgeschütztes Root-Dateisystem. Meta meldet Version 1.1.4.
- Authentik erlaubt anonym exakt Meta, Asset-Links und `/invite`. Externe
  Prüfungen liefern für `/`, `/api/v1/me`, `/invite/private` und `/health`
  weiterhin den Authentik-Redirect, nicht die geschützte Antwort.
- Angeforderte Kalenderwiederherstellung mit eng begrenzter Transaktion,
  geprüftem Dump, Sync-Änderungen und anschließender Entschlüsselungsprüfung.
  Ursprüngliche Datensätze wurden nicht verändert. Personenbezogene Inhalte
  und Einladungscodes sind nicht Teil dieses Repositorys.
- Root-only Sicherungen von Datenbank, Konfiguration und vorherigem Quellstand
  liegen in einem eng benannten Wiederherstellungsordner unter
  `/var/backups/lunara/` und nicht im Repository.
  Die Dumps wurden mit `pg_restore --list` geprüft. Das vorige API-Image bleibt
  als `lunara-api:pre-invitations-20260908` verfügbar; Rollback-Konfiguration
  bleibt ebenfalls ausschließlich auf dem Server.

## Bekannte Grenzen

- Kein interaktiver Test mit zwei Handys, Kamera und Authentik-Browser in dieser
  Umgebung. Instrumentierung wird kompiliert, nicht als ausgeführter
  Gerätetest behauptet.
- Andere offline befindliche Geräte können dem Server nicht mitteilen, welche
  ungesendeten Änderungen sie besitzen. Vor einem Beziehungswechsel alle
  Geräte synchronisieren oder die betreffenden Daten exportieren.
- Einladungen ohne Ablauf bleiben bis zur Nutzung oder zum Widerruf gültige
  Bearer-Geheimnisse. Nicht öffentlich teilen.
- Bereits kopierte Partnerdaten lassen sich nicht aus fremden Screenshots
  oder Backups zurückrufen. Zyklus-/Fruchtbarkeitswerte bleiben Schätzungen,
  keine Ovulationsbestätigung und kein Verhütungsmittel.

## Erster Start beziehungsweise Update

Bestehende Installation: neue signierte Release-APK über die vorhandene App
installieren, nicht vorher deinstallieren. Mit dem eigenen Konto anmelden,
unter **Einstellungen → Partner verbinden** den Code eingeben oder den QR-Code
scannen und bewusst bestätigen. Das Profil der zyklustrackenden Person wird
nicht auf dem Partnergerät angemeldet. Für neue Self-hosting-Installationen
gelten die konfigurierbaren Domain-/Direct-/Authentik-Anleitungen in
`DEPLOYMENT.md`; echte Secrets und Signierschlüssel bleiben außerhalb von Git.

---

# Historischer Abschlussbericht 1.1.0

Stand: 25. August 2026

Lunara 1.1.0 ist als Android-/Server-/PWA-Release implementiert, gebaut,
produktiv ausgerollt und im privaten GitHub-Repository veröffentlicht. Dieser
Bericht nennt ausschließlich tatsächlich ausgeführte Prüfungen; frühere
produktive 1.0.x-Prüfungen werden nicht als Nachweis für 1.1.0 umetikettiert.

## Implementierte Architektur

```text
Android (Solo oder Server)          installierbare PWA
             \                         /
              \         HTTPS         /
               Traefik direkt ODER Authentik Proxy
                            |
                   NestJS-/Prisma-API
                     + PWA-Auslieferung
                            |
              PostgreSQL 18 im internen Netz
```

- Monorepo mit Kotlin/Compose, TypeScript-PWA und NestJS-/Prisma-API
- Docker-Multi-Stage-Image, Non-root UID/GID `10001:10001`, read-only Root,
  Capability-Drop und `no-new-privileges`
- eine Instanz für mehrere streng getrennte Beziehungen; je Beziehung genau
  ein `TRACKER` und ein `PARTNER` mit eigenen Zugangsdaten
- Direct-Gateway ohne Authentik oder optionaler Authentik-Proxy mit
  persönlichem OIDC
- API v1, OpenAPI-Snapshot, PostgreSQL-Transaktionen, Soft Deletes,
  Cursor-Sync und idempotente Offline-Mutationen
- Room/SQLCipher, Android Keystore, WorkManager und optimistische lokale UI

## Tatsächlich umgesetzte Funktionen

### Self-hosted Kernablauf

Der durchgehende Serverkern umfasst:

1. frischen Server mit Bootstrap einmalig initialisieren;
2. Tracker-Konto beziehungsweise weitere isolierte Beziehung registrieren;
3. einmal verwendbare Partner-Einladung erstellen;
4. Partner mit eigenem Konto verbinden;
5. Periode online oder offline erfassen, korrigieren oder löschen;
6. robuste Bereichsprognose neu berechnen;
7. Mutation idempotent genau einmal synchronisieren;
8. dem Partner ausschließlich aktuell freigegebene Werte liefern.

Enthalten sind außerdem Tageslogs, eigene Symptome, verschlüsselte Notizen,
71 deutsche/englische Partner-Tipps, Care Preferences, Check-ins, Freigabepause,
privater Tag, Sitzungen, Passwortwechsel, JSON/CSV/verschlüsselter Export,
Importvorschau, Kontolöschungs-API, Audit, SSE, Admin-CLI und lokale Android-
Benachrichtigungen ohne Firebase.

### Solo auf Android

**Nur auf diesem Gerät** funktioniert ohne Konto, Server oder Domain. Die App
erzeugt eine lokale Trackeridentität und sperrt Partner-, Auth-, Server- und
Syncpfade. Kalender, Perioden, Tageslogs, Verlauf, lokale Prognose und
Erinnerungen arbeiten auf der SQLCipher-Datenbank.

Der Gerätewechsel erfolgt über ein portables `.lunara`-Backup:

- PBKDF2-HMAC-SHA256, 310.000 Iterationen;
- AES-256-GCM mit authentifiziertem Format;
- 12 bis 128 Unicode-Codepoints Passphrase;
- Android Storage Access Framework für eine bewusst gewählte Datei;
- strikte Validierung und Room-Transaktion;
- persistentes Import-Journal zur Wiederaufnahme nach Prozessabbruch.

Google Drive kann nur als vom Benutzer ausgewählter Android-Dateianbieter
auftauchen. Lunara integriert kein Google-SDK, kennt kein Google-Konto und
lädt nichts automatisch hoch. Android-Systembackup und Gerätetransfer sind
absichtlich vollständig ausgeschlossen.

### Kalender und Prognose

Der Kalender wechselt Monate durch horizontales Wischen, zeigt bis zu fünf
Jahre Zukunft und unterscheidet tatsächliche, ausgewählte und geschätzte
Zeiträume nicht nur über Farbe. Vergangene Perioden werden durch Antippen von
Start und Ende erfasst; vorhandene Bereiche lassen sich neu wählen, öffnen,
beenden oder nach Bestätigung löschen. Partner bleiben schreibgeschützt.

`lunara-robust-v2` verwendet bis zu zwölf aktuelle vollständige Zyklen,
gewichtet neuere Daten, nutzt gewichteten Median und MAD, erklärt
ausgeschlossene Ausreißer und verbreitert unsichere Bereiche. Veraltete
Beobachtungen und Sondermodi fallen geschlossen aus. Das geschätzte fruchtbare
Fenster ist standardmäßig aus, benötigt Opt-in und ausreichende Daten und ist
weder Ovulationsbestätigung noch Verhütung.

### PWA

Die PWA ist installierbar, deutsch/englisch, hell/dunkel und bietet
rollenabhängige Startseiten, Kalender, Prognose, Partner-Tipp sowie eine
IndexedDB-Mutation Queue. Zusätzlich besitzt sie einen lokalen Solomodus, der
Profil, Perioden und Tageslogs als AES-256-GCM-Vault mit 600.000
PBKDF2-HMAC-SHA256-Iterationen versiegelt; die Passphrase und der abgeleitete
Schlüssel werden nicht persistiert. Der Vault sperrt bei versteckter Seite,
`pagehide` oder spätestens 15 Minuten Inaktivität. Kontoidentitätswechsel
bereinigen Cache und Queue. Der Service Worker cached keine API-/Health-/Ready-/OpenAPI-
Antworten. Das Web-Bundle wird im API-Container gebaut und ausgeliefert.

### Authentik und Direct-Modus

Ohne Authentik verwendet Lunara die eigenen Konten hinter dem direkten
Traefik-Router. Mit Authentik bleiben nur Meta und Android Asset Links anonym.
OIDC verwendet Authorization Code, PKCE S256, State, Nonce, explizite
Zustimmung und keinen Client Secret.

Moderne authentifizierte Anfragen besitzen:

```text
Authorization: Bearer <Authentik-Access-Token>
X-Lunara-Authorization: Bearer <Lunara-Access-Token>
```

Der äußere Token öffnet den Proxy, der innere bestimmt Lunara-Rechte. Beide
werden nur same-origin gesendet. Der Authentik-Token wird vor Ablaufbedarf
erneuert; ein OIDC-basierter Lunara-Refresh verlangt einen frischen Token
derselben externen Identität.

OIDC verknüpft nur exakten Issuer und Subject mit einem bestehenden Konto.
Keine E-Mail-/Benutzernamen-Heuristik und kein Auto-Provisioning sind aktiv.
Selbstverknüpfung verlangt das aktuelle Passwort; die Adminmigration verlangt
exakte Werte und einen Bestätigungsfingerprint und kann den alten Passwortweg
gezielt deaktivieren.

Production-Android nutzt unabhängig von der Self-hosting-API-/Issuer-Domain
immer den festen BuildConfig-Publisher-App-Link. Der Releasehost
`lunara.3li361.de` wurde mit dem Release-Zertifikat verifiziert. Der
Production-Provider registriert ausschließlich den Android-HTTPS- und PWA-
HTTPS-Callback. Ein Custom Scheme bleibt ausschließlich dem Android-`local`-
Flavor mit getrenntem Entwicklungsprovider und eigener client_id vorbehalten.
Production einschließlich Production-Debug enthält keinen Custom-Scheme-
Intent-Filter und kann damit Production-Authentik bewusst nicht verwenden.
Eigene Distributionen bauen mit einem
eigenen App-Link-Host und veröffentlichen dessen Asset Links.

## Ausgeführte Befehlsgruppen und Ergebnisse

### Backend/PWA

Ausgeführt wurden die entsprechenden Releasebefehle für:

```text
npm ci
Prisma generate + validate
API-/PWA-Lint und Typecheck
Jest und Vitest
Nest-/TypeScript-/Vite-/PWA-Build
OpenAPI generate/check
temporärer PostgreSQL-18-E2E-Lauf
Docker-Multi-Stage-Build
```

Ergebnisse:

- Backend: **26 Suites, 177 Tests**, erfolgreich
- PWA: **11 Dateien, 39 Tests**, erfolgreich
- vollständiges und Production-npm-Audit der PWA: jeweils 0 gemeldete
  Schwachstellen
- Backend plus PWA: **216 erfolgreiche Unit-/Komponententests**; E2E separat
- PostgreSQL E2E: **1/1 Core-Flow**, erfolgreich; **acht Migrationen**
- API-/PWA-Lint, Typecheck und Builds: erfolgreich
- Multi-Stage-Containerbuild mit PWA: erfolgreich

Ein erster E2E-Lauf traf erwartungsgemäß die neu eingeführte stale-
Prognosegrenze. Der Test verwendete danach eine aktuelle Referenzbeobachtung;
nur der abschließende vollständige Lauf wird als bestanden gezählt.

### Android

Ausgeführt wurden die Releaseaufgaben für beide Unit-Testflavors, beide
Debug-Lints, Production-Instrumentierungskompilierung, Production-Debug,
Production-Release und Production-Bundle.

Ergebnisse:

- Local: **36 Suites, 174 Tests**, erfolgreich
- Production: **36 Suites, 174 Tests**, erfolgreich
- zusammen **348 Unit-Test-Ausführungen**
- Local-Lint: **0 Fehler, 64 Warnungen, 1 Information**
- Production-Lint: **0 Fehler, 63 Warnungen, 1 Information**
- Production-AndroidTest-Kotlin: erfolgreich kompiliert
- Instrumentierung mangels Emulator/Gerät **nicht ausgeführt**
- VersionCode 11, VersionName 1.1.0, minSdk 26, targetSdk 36,
  compileSdk 37
- APK-/AAB-Struktur und erwartete Signaturzustände geprüft

### GitHub Actions

Die finalen GitHub-Prüfungen waren erfolgreich:

- Backend CI: Build, Prisma, Lint und Tests
- Android CI: Unit-Tests, Lint, Debug-APK sowie unsigned Release-APK/AAB
- Dependency Review
- Container-/Dependency-Sicherheit: redigierter Gitleaks-History-Scan,
  npm-Audit, Image-Build, CycloneDX-SBOM und Trivy-Scan

Der erste Gitleaks-Lauf wertete einen rein erklärenden Satz in
`.env.example` als generischen API-Key. Der Inhalt war kein Geheimnis. Die
Formulierung wurde geändert und exakt dieser redigierte historische
Fingerabdruck dokumentiert. Anschließend waren sowohl die lokale Reproduktion
mit Gitleaks 8.24.3 über 23 Commits als auch der erneute GitHub-Lauf grün.

## Android-Buildartefakte

Die tatsächlich erzeugten Dateien liegen absolut unter:

| Zweck | Pfad | Bytes | Signaturstatus |
| --- | --- | ---: | --- |
| Production-Debug/Testinstallation | `/root/Lunara/artifacts/lunara-debug.apk` | 24.846.230 | Debugsignatur verifiziert; HTTPS-only, Passwort/Headerauth-Test, kein verifizierter Production-App-Link |
| empfohlene Nutzer-/App-Link-APK | `/root/Lunara/artifacts/lunara-release.apk` | 20.129.262 | serverlokaler Upload-Key, verifiziert |
| Referenz-Release-APK | `/root/Lunara/artifacts/lunara-release-unsigned.apk` | 20.074.290 | erwartungsgemäß unsigniert |
| Google-Play-Upload | `/root/Lunara/artifacts/lunara-play-upload.aab` | 16.145.883 | serverlokaler Upload-Key, verifiziert |
| Referenz-Bundle | `/root/Lunara/artifacts/lunara-release-unsigned.aab` | 16.114.856 | erwartungsgemäß unsigniert |

Finale Prüfsummen:

`lunara-debug.apk`

- SHA-256: `d884bf62efe56786fe2acceed52e1ad25a13ebd492eeb9832453ba1afd3f0db8`

`lunara-release.apk`

- SHA-256: `7edeac8b6ffe052c2b6b3ac33ed2db2dc27618c95474d85dc5f21fcb64cf953f`

`lunara-release-unsigned.apk`

- SHA-256: `177868fab7d37d82b106175c3ffa2d688a5aea6337d76210ce578aec8bff28c9`

`lunara-play-upload.aab`

- SHA-256: `0b9504ce250dacc5066fac9d6b56aebb802197c303144d06465c26339dab0496`

`lunara-release-unsigned.aab`

- SHA-256: `acb2ed5734f6ccaaf81042bbba2724aa3e6628301ec04fe612def39e85f2ae4f`

Dieselben finalen Werte stehen in
`/root/Lunara/artifacts/SHA256SUMS`; `sha256sum -c SHA256SUMS` wurde im
Artefaktverzeichnis für alle fünf Dateien erfolgreich ausgeführt. Ältere
Hashes aus 1.0.x sind für 1.1.0 ungültig.

Die sechs Release-Assets wurden im privaten GitHub-Release veröffentlicht.
Die dort gespeicherte signierte APK wurde erneut heruntergeladen und ihr
SHA-256-Wert bytegenau gegen das lokale Artefakt bestätigt.

Der private Upload-Keystore, Alias und Passwörter liegen nicht im Repository.
Gradle liest sie nur über:

```text
LUNARA_UPLOAD_KEYSTORE
LUNARA_UPLOAD_KEYSTORE_PASSWORD
LUNARA_UPLOAD_KEY_ALIAS
LUNARA_UPLOAD_KEY_PASSWORD
```

Für Google Play ist `lunara-play-upload.aab` ein technischer Upload-
Releasekandidat. Play App Signing muss in der Play Console aktiviert werden;
Google signiert ausgelieferte APKs mit einem getrennten App-Signing-Key. Nach
dessen Zuweisung ist der Fingerprint in `assetlinks.json` zu aktualisieren und
der App Link mit einem internen Play-Build erneut zu prüfen.

## Docker-/Live-Ergebnis

Der finale 1.1.0-Multi-Stage-Build wurde lokal tatsächlich erstellt. API/PWA-
Buildstufe, Non-root-Benutzer `10001:10001`, read-only Runtime, Healthcheck,
Direct-Override und Moduswrapper wurden geprüft. Das produktive Image läuft
als `lunara-api:1.1.0`, ist gesund und hat `RestartCount=0`.

Der erste 1.1.0-Startversuch deckte einen echten Packagingfehler auf: npm hatte
`jose` als nicht gehobene Workspace-Produktionsabhängigkeit unter
`services/api/node_modules` abgelegt, während das Runtime-Image zunächst nur
die Root-Abhängigkeiten kopierte. Dieser Versuch startete nicht. Der
Dockerfile kopiert nun auch den nicht gehobenen Produktionsbaum; ein gezielter
Runtime-Require, der erneute Multi-Stage-Build und der anschließende Live-Start
waren erfolgreich. Der Fehler wird hier bewusst nicht verschwiegen.

Vor dem Rollout wurden zwei validierte Sicherungen erstellt:

- Lunara-Archiv im Verzeichnis `/var/backups/lunara/`, Zeitstempel 16:56:53 UTC
- Authentik-Dump unter `/var/backups/lunara/infrastructure/`, Zeitstempel 16:57:50 UTC

Archivprüfsummen und `pg_restore --list` waren erfolgreich. Der Lunara-Dump
wurde zusätzlich in einem isolierten temporären PostgreSQL-18-Container
tatsächlich wiederhergestellt; sieben gesicherte Migrationen sowie ein User,
drei Perioden und ein Tageslog wurden unverändert nachgewiesen. Der temporäre
Container wurde anschließend automatisch entfernt. Die achte Migration wurde
auf Live erfolgreich angewandt.

Produktiv verifiziert:

- ausschließlich die Lunara-API wurde ersetzt; PostgreSQL, Authentik,
  Pangolin und Traefik wurden nicht neu gestartet und blieben gesund;
- `/health` und `/ready` antworten intern mit 200;
- `/api/v1/meta` und `/.well-known/assetlinks.json` antworten ohne Cookie oder
  Zugangsdaten mit 200, JSON und `Cache-Control: no-store, max-age=0`;
- `/` und `/api/v1/me` bleiben ohne Authentik-Zugang mit 302 geschützt;
- der technische Header-Auth-App-Zugang, geschützte OpenAPI-Antwort und die
  authentifizierte PWA-Shell wurden praktisch geprüft;
- OIDC-Issuer, Discovery, JWKS/Endpoints, PKCE-S256, exakte HTTPS-Callbacks,
  Dune/Elias-Gruppe und Authentik-Proxy-Sollzustand wurden nachgeprüft;
- Pangolin enthält genau die verwaltbare Lunara-Ressource mit gepinntem
  Authentik-Ziel.

Der bestehende Lunara-Benutzer `dunja` wurde nicht gelöscht, sondern
transaktional mit der externen Identität des Authentik-Benutzers `Dune`
verknüpft. Passwortlogin wurde deaktiviert und eine alte Gerätesitzung
widerrufen. Nachgewiesen erhalten blieben TRACKER-Rolle, drei Perioden, ein
Tageslog, Zyklusprofil, Freigaben und Care Preferences. Ein interaktiver Login
mit dem persönlichen Authentik-Passwort wurde nicht automatisiert, weil dieses
Passwort dem Releaseprozess absichtlich nicht vorliegt.

## Bekannte Fehler und Grenzen

- Instrumentierte Android-Tests wurden nicht auf realer Hardware ausgeführt.
- Der PWA-Solo-Vault ist passphrasenverschlüsselt. Synchronisierte
  Self-hosted-Daten können jedoch als nicht zusätzlich
  anwendungsverschlüsselter Offlinecache in IndexedDB liegen; Browserprofil
  und Betriebssystem bilden dafür die lokale Schutzgrenze.
- Safari/iOS-/iPadOS-Installations-, Hintergrund- und Upgradeverhalten ist
  noch nicht über eine vollständige reale Browsermatrix geprüft.
- Screenshot-Schutz und Browserbenachrichtigungen sind in der PWA nicht
  ausführbar. Importierte Privacy-/Notification-Einstellungen bleiben im
  verschlüsselten Vault erhalten und werden als aktuell nicht anwendbar
  gekennzeichnet.
- Der PWA-Solo-Vault muss in jedem neuen Browserprozess erneut mit der
  Passphrase entsperrt werden; Lunara kann eine verlorene Passphrase nicht
  wiederherstellen.
- Das ausschließlich lokale Custom-Scheme-OIDC darf nicht mit der Production-
  client_id provisioniert werden. PKCE verhindert Client-Impersonation bei
  einem selbst gestarteten fremden Flow nicht; Production verwendet deshalb
  ausschließlich den verifizierten HTTPS App Link und enthält keinen Custom-
  Scheme-Intent-Filter.
- OIDC-Provider, PKCE-Metadaten und die externe Identitätsverknüpfung sind live
  geprüft. Den ersten interaktiven Dune-/Elias-Login müssen die jeweiligen
  Kontoinhaber mit ihrem eigenen Authentik-Passwort abschließen.
- Eine hängende alte Browseranfrage kann einen Logout bis zu ihrem Abschluss
  verzögern. Generation-Gates verhindern dabei Konten- oder Datenvermischung;
  ein zusätzlicher Request-Abbruch ist noch nicht implementiert.
- Biometrische App-Sperre, Widget, ntfy und Android-SSE-Client sind nicht
  vollständig umgesetzt.
- Android-UIs fehlen noch für Teile der Gerätesitzungsverwaltung, eigene
  Symptome/Tipps, Check-in-Antworten sowie vollständigen Serverexport/-import
  und Kontolöschung.
- Generischer CSV-Zuordnungsassistent und vollständiges Import-Undo fehlen.
- Server-Check-in-Texte und Browsergesundheitscache sind nicht mit dem
  Server-Master-Key verschlüsselt.
- Ein kompromittierter Root-Host oder entsperrtes kompromittiertes Gerät liegt
  außerhalb des Schutzmodells.
- Bluetooth-/WLAN-Peer-to-Peer-Sync ist nicht implementiert. Partner-Sync
  benötigt die Self-hosted Instanz.
- Lizenz, Markenfreiheit, Publisher-/Supportmodell, öffentliche Datenschutz-
  und Löschseiten sowie Play-Erklärungen sind Betreiberentscheidungen vor
  öffentlicher oder kommerzieller Veröffentlichung.

## Sicherheitsgrenzen

`APP_MASTER_KEY` schützt Notizinhalte in Dumps, nicht Datums-/Rollenmetadaten,
und muss separat wiederherstellbar sein. Ein OIDC-Login ersetzt nicht die
Lunara-Rollenprüfung. Partnerfreigaben verhindern künftige API-Ausgabe, können
aber externe Screenshots nicht zurückholen. Prognosen sind statistische
Schätzungen, keine Diagnose; das fruchtbare Fenster bestätigt keine Ovulation
und darf nicht zur Verhütung verwendet werden.

## Erster produktiver Start

1. `.env` als root-only Datei aus `.env.example` erstellen und eindeutige
   Secrets sowie Domain setzen.
2. `GATEWAY_MODE=direct` oder `authentik` bewusst wählen.
3. Im Authentik-Modus `configure-authentik-oidc.sh --check`, validierten
   App-Signing-Fingerprint und erst danach das Apply ausführen.
4. `./scripts/compose.sh config --quiet`, Image bauen, Migrator ausführen und
   Dienste starten.
5. `/ready`, Meta, Logs und Gatewaypfade prüfen.
6. Frische Instanz einmal mit Bootstrap initialisieren und Bootstrap-Token
   danach entfernen.
7. Trackerfreigaben, Fertilitäts-Opt-in und Benachrichtigungen bewusst
   konfigurieren; Partner nur über eigenen Einmalcode verbinden.
8. Serverdump und `APP_MASTER_KEY` getrennt sichern; Restore in einer
   isolierten Instanz testen.
9. Für Solo-Geräte sofort ein verschlüsseltes portables Testbackup erstellen
   und auf einem zweiten Gerät importieren, bevor der Umzugsweg als zuverlässig
   angesehen wird.

## Repository und Veröffentlichung

Privates Repository:

```text
https://github.com/eliasbruno124-dev/Lunara
```

Release 1.1.0:

```text
https://github.com/eliasbruno124-dev/Lunara/releases/tag/v1.1.0
```

Direkte signierte APK:

```text
https://github.com/eliasbruno124-dev/Lunara/releases/download/v1.1.0/lunara-release.apk
```

Repository und Release sind privat; Download und Quellzugriff erfordern ein
berechtigtes, bei GitHub angemeldetes Konto.
