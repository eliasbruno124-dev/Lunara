# Lunara 1.3.2 – Prüfstand

Stand: 28. Oktober 2026. Dieser Bericht beschreibt ausgeführte Prüfungen;
ältere Berichte und Artefakte bleiben in der Git-Historie erhalten.

## Änderungen

- Die Weboberfläche folgt dem Design der Android-App: gemeinsame Farbwelt,
  Karten, Typografie-Hierarchie, Navigation und Einstellungsbereiche. Große
  Bildschirme behalten ein angepasstes Desktop-Layout.
- Erfasste Perioden sind rosé, Prognosen amber und das geschätzte fruchtbare
  Fenster türkis. Beide Oberflächen verwenden dieselbe Legende und zusätzliche
  Kennzeichen. Auswahl und heutiger Tag überschreiben diese Farben nicht.
- Schmale Kalender, große Schrift und Hell-/Dunkelmodus wurden nachgeprüft.
  Die Webansicht zeigt das gültige Prognoseintervall wie die Android-App;
  die Berechnung selbst bleibt unverändert.

Die zuvor im selben unveröffentlichten Update ergänzten Funktionen bleiben enthalten:

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
| Web | 185 Tests in 21 Dateien; TypeScript und Produktionsbuild bestanden |
| Android JVM | 32 gezielte Regressionstests bestanden; vollständige 309er-Suite zuvor für 1.3.1 geprüft |
| Android Gerät | Je 45 Oberflächentests auf Deutsch und Englisch bestanden, einschließlich Kalenderfarben, Überlauf und Partnerfreigaben |
| Browser | 165 Assertions mit synthetischen Daten; Import, Export, lokaler Tresor, Partnerkalender, Freigabewiderruf und 18 Ansichten in Hell/Dunkel |
| Kalenderbreite | 18 zusätzliche Chromium-Layouts mit Breiten von 280 bis 700 px und drei Schriftgrößen; Gegenprobe erkennt den ursprünglichen Überlauf |
| Android Release | Signierte APK und AAB gebaut; Lint ohne Fehler, 96 Warnungen |
| Update-Kompatibilität | Paket de.lunara.app, Version 1.3.2, versionCode 22, gleiches Signierzertifikat |
| Paketierung | Docker-Image enthält exakt die geprüften Web-Assets und die signierte APK |
| Produktion | API-only-Update; Health/Readiness, Download-Hash, HTTP Range und Authentifizierungsgrenzen geprüft |
| Datenbestand | Prüfsummen für Perioden, Freigaben und Benutzer vor/nach Update identisch; Datenbankcontainer unverändert |
| Dashy | Gemeinsame Lunara-Kachel mit vorhandenem Logo; Sichtbarkeit und Zugangsrichtlinien für alle sechs normalen Benutzer geprüft |

Die Geräteprüfung lief auf einem isolierten Android-Emulator, nicht auf den
physischen Geräten der Nutzer. Browser- und Gerätetests verwenden ausschließlich
synthetische Daten. Der Live-Download wurde zusätzlich über HTTPS geprüft.
Die kleinen Weblayouts deckten einen Überlauf durch das Zusammenspiel von
Seitenverhältnis und Mindesthöhe auf; die Tagesfelder passen jetzt in ihre
jeweilige Spalte. In Android wurde die Textbreite der Tageskennzeichen mit
der Zellbreite abgeglichen, ohne die strengen Überlaufprüfungen abzuschwächen.

Der Live-Container `local/lunara-api:1.3.2-calendar-20261028` wurde aus der
Quellrevision `9853dd3` gebaut. Der Datenbankcontainer wurde nicht neu erstellt.
API und Web sind gesund; ausgelieferte Web-Assets und APK stimmen bytegenau
mit den geprüften Artefakten überein. Der HTML-Abruf berücksichtigt wie ein
Browser den `Accept: text/html`-Header; ohne ihn liefert die Wurzelroute die
API-Auskunft statt der Weboberfläche.

Die zusätzlichen PostgreSQL-Integrationstests und der damalige Abhängigkeits-Audit
gehören zum vorangegangenen 1.3.1-Prüflauf, nicht zu einer erneuten vollständigen
Datenbank- oder Sicherheitsprüfung dieser Designänderung. Es wurden keine
Server-Datenmodelle oder Partnerfreigaben geändert.

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
