# Lunara

**Private, selbst gehostete Zyklus-App für eine Person und ihren Partner.**
Android-App, installierbare Web-App und eigener Server – ohne Werbung, Tracking,
Telemetrie oder Google-Dienste.

[![Release](https://img.shields.io/github/v/release/eliasbruno124-dev/Lunara)](https://github.com/eliasbruno124-dev/Lunara/releases/latest)
[![Image](https://img.shields.io/badge/ghcr.io-eliasbruno124--dev%2Flunara-blue)](https://github.com/eliasbruno124-dev/Lunara/pkgs/container/lunara)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

<p>
  <img src="docs/screenshots/hd/03-tracker-heute.png" width="24%" alt="Heute-Ansicht mit erwartetem Periodenbeginn">
  <img src="docs/screenshots/hd/04b-tracker-kalender-oktober.png" width="24%" alt="Kalender mit erfassten Periodentagen">
  <img src="docs/screenshots/hd/04-tracker-kalender.png" width="24%" alt="Kalender mit geschätzter Periode">
  <img src="docs/screenshots/hd/06-partner-heute.png" width="24%" alt="Partneransicht mit Ausblick und Tagesidee">
</p>

> Lunara ist **kein Medizinprodukt**, diagnostiziert nichts, bestätigt keine
> Ovulation und darf **nicht zur Verhütung** verwendet werden.

## Schnellstart (eigener Server)

```bash
mkdir lunara && cd lunara
BASE=https://raw.githubusercontent.com/eliasbruno124-dev/Lunara/main/examples
curl -fsSLO $BASE/docker-compose.yml && curl -fsSLO $BASE/Caddyfile
curl -fsSL $BASE/.env.example -o .env && chmod 600 .env
# .env ausfüllen: Domain + Geheimnisse (Befehle stehen in der Datei)
docker compose --profile caddy up -d
```

Danach `https://deine-domain` öffnen. Die **[Installationsanleitung mit Bildern](docs/INSTALL.md)**
erklärt jeden Schritt: HTTPS, erstes Konto, Android-App, Partner einladen,
Updates und Backup.

**Nur allein auf Android?** Dafür brauchst du keinen Server. Lade
`lunara-release.apk` aus dem [neuesten Release](https://github.com/eliasbruno124-dev/Lunara/releases/latest)
und wähle **Nur auf diesem Gerät** (Android 8+).

## So kannst du Lunara nutzen

| Betriebsart | Speicherung | Gemeinsam nutzen |
| --- | --- | --- |
| Android, nur auf diesem Gerät | Mit SQLCipher und Android Keystore verschlüsselt | Nein, kein Serverkonto |
| Web-App, nur auf diesem Gerät | Passphrasenverschlüsselter lokaler Tresor | Nein, kein Serverkonto |
| Eigener Server | PostgreSQL; Android und Web-App synchronisieren mit derselben API | Getrennte Tracker-/Partnerkonten, nur ausdrücklich freigegebene Angaben |
| Eigener Server mit Authentik | Derselbe Lunara-Server hinter dem Gateway | Persönliche OIDC-Anmeldung statt eines zusätzlichen Lunara-Passworts möglich |

Für den Android-Solomodus genügt die APK aus GitHub: **kein eigener Server,
kein Docker, kein Authentik und kein Konto** sind erforderlich.
Die Web-App muss zum ersten Laden
erreichbar sein; lokale Daten werden dadurch nicht zum Server synchronisiert.
Für die gemeinsame Nutzung braucht ihr eine eigene HTTPS-Instanz.
Authentik ist optional und kein Bestandteil des Lunara-Compose-Stacks.

## Was enthalten ist

- Perioden, Tagesprotokolle, Kalender und Verlauf auf Android; Periodeneinträge
  und Kalender auch in der Web-App.
- Offline-Erfassung mit wartenden Änderungen und automatischer Synchronisation.
- Deutsch und Englisch, Hell-/Dunkelmodus und installierbare Web-App.
- Getrennte Rollen: Die trackende Person verwaltet Daten und Freigaben, der
  Partner sieht ausschließlich erlaubte Angaben.
- Granulare Freigaben, Pausen und private Tage; freiwillige Unterstützungstipps.
- Verschlüsselte Solo-Backups für den Gerätewechsel und serverseitige
  Backup-/Restore-Werkzeuge.
- Export eigener Perioden als CSV/JSON und Dateiimport mit Vorschau und
  Bestätigung in Android und Web; keine garantierte Kompatibilität mit
  beliebigen Drittanbieterformaten.

Eine Partnerverbindung schaltet keine Datenfreigabe automatisch ein.
„Nicht freigegeben“ bedeutet weder „keine Einträge vorhanden“ noch „Daten
gelöscht“.

## Downloads

| Was | Wo |
| --- | --- |
| Android-App (signiert) | `lunara-release.apk` im [Release](https://github.com/eliasbruno124-dev/Lunara/releases/latest), Prüfsummen in `SHA256SUMS` |
| Server-Image (amd64 + arm64) | `ghcr.io/eliasbruno124-dev/lunara:<version>` bzw. `:latest` |
| Beispielkonfiguration | [`examples/`](examples/) |

Jede laufende Instanz liefert die passende APK außerdem selbst aus:
`https://deine-domain/api/v1/downloads/lunara.apk` (in der Web-App unter
**Einstellungen → App & Verbindung**).

## Aktualisieren und Daten behalten

Eine neue APK als Update **über die vorhandene Installation** installieren.
Nicht zuerst deinstallieren und keine App-Daten löschen. Android akzeptiert
ein Update nur mit kompatibler Paket-ID, höherer Versionsnummer und passender
Signatur; ein selbst signierter Build ersetzt nicht die veröffentlichte APK.

Die Anmeldung wird automatisch erneuert, ist aber nicht unbegrenzt
garantiert. Widerrufene oder tatsächlich abgelaufene Provider-Zugangsdaten,
gelöschte Konten und verlorene Geräteschlüssel erfordern eine neue Anmeldung.
Bei **Anmeldung erneuern** dasselbe Konto verwenden; wartende lokale Einträge
bleiben dabei erhalten. Details: [Einrichtung und Hilfe](docs/SETUP.md).

## Entwicklung

Node.js 24+, npm 11+; für Android ein passendes JDK und Android SDK.
Die App verwendet Compose/Kotlin, die Web-App TypeScript/Vite und die API
NestJS/Prisma mit PostgreSQL.

```bash
npm ci
npm run prisma:generate
npm run prisma:validate
npm run lint
npm test
npm run build
npm run openapi:check
```

Android-Aufgaben und Geräteprüfungen stehen in [TESTING.md](TESTING.md).
Vor Änderungen [CODE_GUIDE.md](docs/CODE_GUIDE.md) lesen; nach Änderungen den
generierten Code-Map-Stand prüfen. Entwicklungs-, Debug- und unsignierte
Artefakte sind keine gleichwertigen Nutzer-Updates.

## Dokumentation

- [Installation auf dem eigenen Server (mit Bildern)](docs/INSTALL.md)
- [Einrichtung, Freigaben, Backup und Gerätewechsel in der App](docs/SETUP.md)
- [Fortgeschrittenes Deployment: Traefik, Authentik/OIDC, Skripte](DEPLOYMENT.md)
- [Sicherheit](SECURITY.md) und [Datenschutz](PRIVACY.md)
- [Prognoseverfahren und Grenzen](ALGORITHM.md), [Periodenende](docs/PERIOD-ENDS.md)
- [Partner-Tipps](docs/PARTNER_TIPS.md) und [Gestaltung](docs/DESIGN.md)
- [Testverfahren](TESTING.md) und [Code-Navigation](docs/CODE_GUIDE.md)
- [Android-Signierung und Play-Vorbereitung](docs/GOOGLE_PLAY_RELEASE.md)
- [Änderungen](CHANGELOG.md)

## Lizenz

[MIT](LICENSE). Sicherheitslücken bitte nicht als öffentliches Issue, sondern
wie in [SECURITY.md](SECURITY.md) beschrieben melden.
