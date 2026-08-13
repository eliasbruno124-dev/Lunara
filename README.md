# Lunara

Lunara ist eine selbst gehostete Zyklus- und Perioden-App für eine
zyklustrackende Person und eine unterstützende Partnerperson. Beide verwenden
dieselbe native Android-App, haben getrennte Konten und erhalten ausschließlich
die serverseitig erlaubte Rollenansicht.

Die Anwendung enthält keine Werbung, Telemetrie, Analyse-SDKs, Firebase- oder
Google-Play-Services-Abhängigkeit für Kernfunktionen. Sie ist kein
Medizinprodukt, stellt keine Diagnose und darf nicht als Verhütungsmethode
verwendet werden.

Der Quellcode liegt im privaten Repository
[`eliasbruno124-dev/Lunara`](https://github.com/eliasbruno124-dev/Lunara). Der
Produktionsstack läuft getrennt unter `/docker/lunara`; Authentik und Pangolin
sind eingerichtet. Die öffentliche Domain, das Let’s-Encrypt-Zertifikat und
beide getrennten Gateway-Konten wurden vollständig geprüft.

## Umgesetzter Kern

- NestJS-/Prisma-API mit PostgreSQL, Argon2id, kurzen Access-Tokens,
  rotierenden gehashten Refresh-Tokens und getrennten Gerätesitzungen
- sicherer Bootstrap, Rollen `TRACKER`/`PARTNER`, Einmal-Einladungen und
  serverseitige Haushaltsisolation
- Perioden, Tageslogs, vordefinierte und eigene Symptome, Prognosen,
  Freigaben, Care Preferences, mindestens 50 zweisprachige Tipps und Check-ins
- verschlüsselte Freitextnotizen, Soft Deletes, Cursor-Sync, optimistische
  Versionskonflikte und idempotente Offline-Mutationen
- JSON-/CSV-Export und ein per AES-256-GCM passwortgeschütztes
  `.lunarabackup`-JSON-Format; kein ZIP-Container
- feldvalidierter JSON-Import mit Vorschau und Erkennung bereits vorhandener
  aktiver Perioden und Tageslogs
- native Compose-App mit deutschem/englischem Onboarding,
  Authentik-Header-Login, SQLCipher/Keystore, Room, WorkManager,
  rollenabhängigen Ansichten, Basis-Tageslog, Kalender, Verlauf, Freigaben und
  `FLAG_SECURE`
- gehärtete Multi-Stage-Container, PostgreSQL 18, Backup-/Restore-/Update- und
  Admin-Skripte sowie additive Authentik-/Pangolin-Konfiguration
- versionierte OpenAPI-Spezifikation und daraus erzeugter Kotlin-Referenzclient;
  die App nutzt zur Laufzeit ihre typisierten Retrofit-Schnittstellen

Der tatsächlich verifizierte Stand und bewusst offene Erweiterungen stehen in
[`FINAL_REPORT.md`](FINAL_REPORT.md) und [`PROGRESS.md`](PROGRESS.md).

## Verifizierter Live-Stand

- API und PostgreSQL laufen gesund unter `/docker/lunara`; zwei Migrationen
  sind angewendet.
- Weder API noch Datenbank veröffentlichen einen Host-Port. Die API läuft als
  UID/GID `10001:10001` mit read-only Root-Dateisystem und ohne Linux-
  Capabilities.
- Authentik-Provider und Pangolin-Ressource wurden additiv eingerichtet. Der
  Cloudflare-CNAME und das Let’s-Encrypt-Zertifikat sind aktiv. Beide getrennten
  Gateway-Konten lieferten ohne DNS- oder TLS-Ausnahme eine gültige
  Lunara-Metaantwort; ein nicht authentifizierter Aufruf endet mit HTTP 302 zu
  Authentik.
- Ein PostgreSQL-Backup wurde erstellt, seine Archivprüfsummen und die
  `pg_restore`-Inhaltsliste wurden erfolgreich validiert. Ein destruktiver
  Voll-Restore wurde nicht auf der Live-Instanz ausgeführt.
- Lunara bleibt vollständig außerhalb von Emby und dessen Plugins. Während des
  Android-Builds beendete der Kernel Emby einmal wegen globalen Speichermangels;
  Docker stellte den Dienst automatisch wieder her. Der Buildablauf besitzt
  deshalb nun einen Hostspeicher-Vorabcheck und feste Containergrenzen. Details
  stehen im Abschlussbericht.

Die Bereitstellungsschritte sind abgeschlossen.

## APK

Die installierbare Produktions-Debug-APK liegt unter:

```text
artifacts/lunara-debug.apk
```

Für berechtigte GitHub-Benutzer steht sie außerdem im privaten
[`v1.0.1-Release`](https://github.com/eliasbruno124-dev/Lunara/releases/tag/v1.0.1)
als direkter Download bereit.

Sie erzwingt HTTPS und verwendet standardmäßig
`https://lunara.3li361.de/`. Vor der Installation den Hash prüfen:

```bash
cd artifacts
sha256sum -c SHA256SUMS
```

Die APK ist mit einem lokal dauerhaft abgelegten Android-Debugschlüssel
signiert, der von Git ausgeschlossen ist. Die frühere 1.0.0-Test-APK verwendete
noch einen anderen flüchtigen Schlüssel und muss vor der Installation von
1.0.1 einmal deinstalliert werden. Ab 1.0.1 lassen sich Builds von diesem Host
mit demselben Debug-Schlüssel aktualisieren. Ein eigener Release-Build ist in
[`docs/ANDROID_SIGNING.md`](docs/ANDROID_SIGNING.md) beschrieben; private
Signierschlüssel liegen absichtlich nicht im Repository.

## Verbindung über Authentik

Beim ersten Start akzeptiert die App die vom Server lokal erzeugte URL in dieser
Form:

```text
https://{BENUTZERNAME}:{AUTHENTIK-APP-PASSWORT}@lunara.3li361.de
```

Die HTTPS-URL wird dazu um die URL-Benutzerinfo
`gateway-benutzer:authentik-app-passwort@lunara.3li361.de` ergänzt. Die fertige
Adresse beginnt wie gewohnt mit `https://`.

Die App entfernt Benutzername und Passwort sofort aus der angezeigten und
gespeicherten Server-URL und legt sie Keystore-geschützt getrennt ab. Dabei muss
ein Authentik-**App-Passwort**, nicht das normale Kontopasswort, verwendet
werden. URLs mit Zugangsdaten gehören nicht in QR-Codes, Chats oder
Cloudflare-DNS-Einträge und können vor dem Einfügen in Zwischenablage- oder
Tastaturverläufen sichtbar sein.

Version 1.0.1 sendet die Gateway-Anmeldung auch nach einem vorherigen anonymen
Verbindungsversuch zuverlässig und ausschließlich an den konfigurierten
Server-Ursprung. Ein versehentlich eingefügter `/api/v1/meta`-Testpfad wird auf
die Serverbasis normalisiert.

## Entwicklung und Prüfung

Auf einem Docker-Host startet der folgende Befehl ausschließlich eine zufällig
benannte, kurzlebige PostgreSQL-Testumgebung und führt den API-Kernablauf aus:

```bash
./scripts/e2e.sh
```

Weitere Befehle:

```bash
npm ci
npm run lint
npm test
npm run build
npm run prisma:validate
npm run openapi:check

cd apps/android
./gradlew testLocalDebugUnitTest
./gradlew lintLocalDebug lintProductionDebug
./gradlew assembleProductionDebug
```

Die Produktionskonfiguration veröffentlicht absichtlich weder API- noch
Datenbankports. Verwende für den produktiven Start ausschließlich die geprüfte
Anleitung in [`DEPLOYMENT.md`](DEPLOYMENT.md), nicht den Test-Stack.

## Projektstruktur

```text
apps/android/             native Android-App
services/api/             NestJS-/Prisma-API
packages/openapi-client/  OpenAPI-Spezifikation und generierter Kotlin-Client
infra/docker/             Containerfiles und isolierte Testkonfiguration
infra/traefik/            Proxy-Beispiele
scripts/                  Betrieb, Backup, Restore, Update, Gateway und E2E
docs/                     ergänzende Dokumentation und Tippkatalog
artifacts/                tatsächlich erzeugte installierbare APK
```

Produktname, Servername/-version und Android-Brandwerte sind an zentralen
Konfigurationsstellen gebündelt. Package-ID, Übersetzungstexte und Vektor-Assets
müssen bei einer vollständigen Umbenennung zusätzlich angepasst werden.

## Weiterführende Dokumentation

- [`DEPLOYMENT.md`](DEPLOYMENT.md): Debian 12, Docker, Authentik, Pangolin,
  Cloudflare, Backup und Update
- [`SECURITY.md`](SECURITY.md): Bedrohungsmodell, Schlüssel, Gerätesicherheit
  und bekannte Grenzen
- [`PRIVACY.md`](PRIVACY.md): gespeicherte und geteilte Daten
- [`ALGORITHM.md`](ALGORITHM.md): deterministische Prognose
- [`TESTING.md`](TESTING.md): ausgeführte und noch nicht ausführbare Tests

## Lizenz

Nicht zur öffentlichen Weitergabe lizenziert. Alle Rechte verbleiben beim
Repository-Eigentümer.
