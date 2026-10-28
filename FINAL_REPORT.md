# Lunara 1.3.1 – Prüfstand

Stand: 28. Oktober 2026. Dieser Bericht beschreibt ausgeführte Prüfungen;
ältere Berichte und Artefakte bleiben in der Git-Historie erhalten.

## Änderungen

- Kalender und Partner-Einstellungen unterscheiden fehlende Freigaben,
  Ladefehler, leere Einträge und nicht verfügbare Schätzungen.
- Das freigegebene fruchtbare Fenster wird unabhängig von einer Freigabe
  der allgemeinen Periodenprognose angezeigt. Die Berechnungsformel bleibt unverändert.
- CSV/JSON-Import und -Export eigener Perioden in Android und Web, auch im
  lokalen Modus: Vorschau, ausdrückliche Bestätigung, Dublettenprüfung,
  Konfliktabweisung und Schutz beim Kontowechsel.
- Robustere Sitzungsverlängerung und verschlüsselte Speicherung; abgelaufene
  oder widerrufene Provider-Zugangsdaten erfordern weiterhin eine Anmeldung.
- APK-Download in den Web-Einstellungen; signierte APK direkt im API-Image.
- Überarbeitete Einstellungen, bebilderte Einrichtung und eine automatisch
  prüfbare Code-Karte mit getrennt gepflegten Ablaufbeschreibungen.

## Ausgeführt

| Bereich | Ergebnis |
| --- | --- |
| API | 326 Tests in 32 Suites; Lint und Build bestanden |
| Web | 156 Tests in 19 Dateien; TypeScript und Produktionsbuild bestanden |
| PostgreSQL | Integrationstests einschließlich Schutz vor unsicheren Datenbankzielen auf dem Standardport und einem expliziten Testport bestanden |
| Android JVM | 309 Tests in 55 Suites, Variante LocalDebug, bestanden |
| Android Gerät | Je 39 Oberflächentests auf Deutsch und Englisch sowie 4 Screenshotfälle bestanden |
| Browser | 43 Assertions in 3 getrennten synthetischen Kontexten; Import, Export, lokaler Tresor, Partnerkalender und Freigabewiderruf |
| Android Release | Signierte APK und AAB gebaut; ProductionRelease-Lint bestanden |
| Update-Kompatibilität | Paket de.lunara.app, Version 1.3.1, versionCode 21, gleiches Signierzertifikat |
| Paketierung | Docker-Image enthält exakt die geprüften Web-Assets und die signierte APK |
| Produktion | API-only-Update; Health/Readiness, Download-Hash, HTTP Range und Authentifizierungsgrenzen geprüft |
| Datenbestand | Prüfsummen für Perioden, Freigaben und Benutzer vor/nach Update identisch; Datenbankcontainer unverändert |
| Abhängigkeiten | npm-Audit der Produktionsabhängigkeiten ohne gemeldete Schwachstellen zum Prüfzeitpunkt |

Die Geräteprüfung lief auf einem isolierten Android-Emulator, nicht auf den
physischen Geräten der Nutzer. Browser- und Gerätetests verwenden ausschließlich
synthetische Daten. Der Live-Download wurde zusätzlich über HTTPS geprüft.
Ein anfangs fehlgeschlagener Wiederholungslauf nutzte eine bereits gefüllte
Testdatenbank; nach korrekt begrenztem Zurücksetzen ausschließlich dieser
Testdatenbank bestand die Suite. Drei Kalendertests wurden korrigiert, weil
sie Text am Elternelement statt am tatsächlichen sichtbaren Kind prüften.
Der CI-Lauf deckte außerdem einen fest codierten Testport auf; der Schutz folgt
jetzt dem ausdrücklich konfigurierten Port, ohne andere Datenbankziele zuzulassen.
Der Containerbau aktualisiert `perl-base` gezielt aus den Debian-Paketquellen
und erhält in CI die tatsächliche Quellrevision statt eines veralteten Labels.

## Datenübertragung und Grenzen

CSV unterstützt Komma, Semikolon und Tabulator sowie erkannte deutsche und
englische Spaltennamen. UTF-8 und UTF-16 mit BOM, eindeutige Datumsschreibweisen
und mehrere JSON-Hüllen werden verarbeitet. Grenze: 2 MiB und 5.000 Perioden.
Mehrdeutige Daten werden nicht geraten. Ein fehlendes Enddatum bedeutet eine
laufende Periode. Fremde Zusatzfelder werden nicht als Kontoeinstellungen übernommen.

Exportiert wird der aktuell lokal geladene Stand eigener Perioden, einschließlich
noch wartender Änderungen. Für den neuesten Serverstand vorher synchronisieren.
CSV/JSON sind unverschlüsselte Gesundheitsdaten. Ein vollständiges verschlüsseltes
Backup bleibt ein eigener Vorgang. Nicht jedes proprietäre Drittanbieterformat
ist kompatibel; eine konkrete Fremd-App wird ohne Prüfung ihrer Exportdatei
nicht als unterstützt bezeichnet.

Partnerfreigaben werden niemals automatisch eingeschaltet. Eine Partnerverbindung
allein erlaubt keinen Zugriff auf Periodendaten oder das fruchtbare Fenster.
Kalenderschätzungen bestätigen keinen Eisprung und sind nicht zur Verhütung geeignet.

## Wiederholen

Die regulären Prüfungen stehen in [TESTING.md](TESTING.md), die Einrichtung in
[docs/SETUP.md](docs/SETUP.md). Die Code-Karte wird mit `npm run code-map:write`
erzeugt und mit `npm run code-map:check` geprüft. Syntaxinventar und verlinkte
Symbole ersetzen keine Verhaltensprüfung.

Die SHA-256-Werte der Auslieferungsdateien stehen in [artifacts/SHA256SUMS](artifacts/SHA256SUMS).
Die APK über die vorhandene Installation installieren, nicht vorher deinstallieren.
