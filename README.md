# Lunara

Private Zyklus- und Periodenbegleitung für Android und den Browser.
Lunara 1.3.1 funktioniert allein auf dem Gerät oder auf einem eigenen Server
mit getrennten Konten für die trackende Person und ihren Partner.

Keine Werbung, Telemetrie oder Pflicht zu Google Play Services.
Periodenprognosen und das optionale fruchtbare Zeitfenster sind unsichere
Kalenderschätzungen: **kein Medizinprodukt, kein Nachweis eines Eisprungs
und keine Verhütungsmethode.**

[Einrichtung mit Bildern](docs/SETUP.md) ·
[Server installieren](DEPLOYMENT.md) ·
[Releases und APK](https://github.com/eliasbruno124-dev/Lunara/releases) ·
[Änderungen](CHANGELOG.md)

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

Neu in 1.3.1: Der Partnerkalender unterscheidet fehlende Freigaben von nicht
verfügbaren Daten. Die Partner-Einstellungen zeigen den zuletzt bekannten
Freigabestatus für Periodendaten und das fruchtbare Zeitfenster.
Unter **Web-Einstellungen → App & Verbindung** bleibt ein nativer
Android-APK-Download erreichbar – auch in einer bereits installierten PWA.

Eine Partnerverbindung schaltet keine Datenfreigabe automatisch ein.
„Nicht freigegeben“ bedeutet weder „keine Einträge vorhanden“ noch „Daten
gelöscht“.

## Einrichten

1. Android: die signierte `lunara-release.apk` aus den
   [GitHub-Releases](https://github.com/eliasbruno124-dev/Lunara/releases)
   installieren; Android 8 oder neuer ist erforderlich.
   Alternativ die HTTPS-Adresse eures Lunara-Servers im Browser öffnen.
2. **Nur auf diesem Gerät** für Solo wählen oder **Server oder Partner
   verbinden** für Self-hosting.
3. Auf dem Server anmelden beziehungsweise zuerst das Trackerkonto einrichten.
   Den Partner mit einer persönlichen Einladung verbinden.
4. Die trackende Person wählt ihre Datenschutzfreigaben selbst.
   Partner können diese nur ansehen.

Die [bebilderte Anleitung](docs/SETUP.md) führt durch diese Schritte,
Backups und häufige Stolperstellen.

<p>
  <img src="docs/screenshots/setup/01-mode-selection.png" width="270" alt="Lunara-Einrichtung: Solo oder eigenen Server wählen">
  <img src="docs/screenshots/setup/04-partner-sharing-status.png" width="270" alt="Partner-Einstellungen: lesbare Freigabestatus für Periodendaten und fruchtbares Zeitfenster">
</p>

Die Setup-Abbildungen stammen aus der tatsächlichen Oberfläche mit
synthetischen Testkonten, nicht aus privaten Konten.

## Eigenen Server betreiben

Voraussetzungen: Linux mit Docker Engine und Compose Plugin, eine eigene
HTTPS-Domain, ein vorhandener Reverse Proxy und sein externes Docker-Netz.
Die dokumentierte Referenz verwendet Debian 12 und Traefik 3.

Der Quellcode baut das Image lokal als `lunara-api:1.3.1`.
Ein öffentlich bereitgestelltes GHCR-Image wird hier nicht vorausgesetzt.
Die API liefert die Web-App und die gebündelte APK aus; PostgreSQL bleibt im
internen Docker-Netz. Es wird kein separater APK-Bind-Mount benötigt.

[DEPLOYMENT.md](DEPLOYMENT.md) beschreibt Konfiguration, Secrets,
Direct-/Authentik-Modus, erste Einrichtung und Updates. Bei einem
Portainer-verwalteten Stack müssen Änderungen über dessen Verwaltungsweg
erfolgen; keine zweite Compose-Installation über denselben Stack starten.

Der Download auf einer eigenen Instanz lautet:

```text
https://lunara.example.com/api/v1/downloads/lunara.apk
```

Im Authentik-Modus bleibt der Download durch das Gateway geschützt. Er gehört
nicht in dessen Liste öffentlicher Ausnahmen.

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

- [Einrichtung mit Bildern](docs/SETUP.md)
- [Deployment, Backup und Updates](DEPLOYMENT.md)
- [Sicherheit](SECURITY.md) und [Datenschutz](PRIVACY.md)
- [Prognoseverfahren und Grenzen](ALGORITHM.md)
- [Partner-Tipps](docs/PARTNER_TIPS.md) und [Gestaltung](docs/DESIGN.md)
- [Testverfahren](TESTING.md) und [Code-Navigation](docs/CODE_GUIDE.md)
- [Android-Signierung und Play-Vorbereitung](docs/GOOGLE_PLAY_RELEASE.md)

## Lizenzstatus

Im Repository ist derzeit keine Weitergabelizenz festgelegt.
Eine Veröffentlichung des Quellcodes ersetzt keine Lizenzentscheidung;
das Projekt wird hier nicht als Open Source bezeichnet.
