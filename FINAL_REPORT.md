# Lunara 1.3.3 – Prüfstand

Stand: 28. Oktober 2026. Dieser Bericht beschreibt ausgeführte Prüfungen;
ältere Berichte und Artefakte bleiben in der Git-Historie erhalten.

## Änderungen

- Beginn und weitere Tage der geschätzten Periode verwenden eine gemeinsame
  amberfarbene, gestrichelte Kategorie. Erfasste Tage bleiben rosé und das
  geschätzte fruchtbare Fenster türkis. Kalenderdaten werden nicht zusammengelegt.
- Die Web-Offlineschätzung verwendet dieselbe Auswahl der letzten zwölf
  abgeschlossenen positiven Periodendauern wie API und Android. Zulässige
  Beobachtungsdauer und Profil-Fallback werden nicht mehr verwechselt.
  Gelöschte Einträge werden ausgeschlossen, `variationDays` bezeichnet überall
  die halbe Unsicherheitsbreite statt im Web die doppelte Breite.
- Phasenverbraucher verwenden denselben Mittelpunkt und dieselbe eingestellte
  Lutealphasenlänge wie die bestehende Prognose. Eine freigegebene tatsächliche
  Menstruation wird nicht mehr allein wegen fehlender Prognose unterdrückt.
- Überholte oder durch wartende Serveränderungen ungültige Zusammenfassungen
  erscheinen nicht als nächste Periode. Mitternacht und Rückkehr in die App
  aktualisieren die Einordnung; historische Kalenderbereiche bleiben erhalten.
- Die Partnerphase heißt ehrlich „Zuletzt geteilte Zyklusphase“. Verspätete
  Web-Antworten überschreiben weder neuere Freigabeänderungen noch deren Cache.
  Kontoprüfung und Reihenfolge der Cache-Schreibvorgänge bleiben getrennt abgesichert.

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
| API | 340 Tests in 32 Suites; Lint und Build bestanden |
| Web | 232 Tests in 23 Dateien; TypeScript und Produktionsbuild bestanden |
| Android JVM | 327 Tests in 58 Suites; keine Fehler, keine übersprungenen Tests |
| Android Gerät | Je 56 Oberflächentests auf Deutsch und Englisch bestanden; Kalender, Freigaben, Datenübertragung, Aktualität und große Schrift |
| Browser | 165 Assertions mit synthetischen Daten; Import, Export, lokaler Tresor, Partnerkalender, Freigabewiderruf und 18 Ansichten in Hell/Dunkel |
| Kalenderbreite | 48 Chromium-Layouts mit zwei Sprachen, Breiten von 280 bis 700 px und drei Schriftgrößen; zusätzlich Forced Colors und negative Darstellungskontrolle |
| Schätzungsvergleich | 72 synthetische API/Web-Vergleichsfälle sowie unabhängige Grenzwert- und Phasenprüfungen bestanden |
| Android Release | Signierte APK und AAB gebaut; Android-Debug-Lint: 0 Fehler, 95 Warnungen |
| Update-Kompatibilität | Paket de.lunara.app, Version 1.3.3, versionCode 23, unverändertes Signierzertifikat |
| Paketierung | Docker-Image enthält exakt die geprüften Web-Assets und die signierte APK |
| Produktion | API-only-Update; Health/Readiness, authentifizierter HTTPS-Download, Prüfsumme und HTTP Range geprüft |
| Datenbestand | Prüfsummen für Perioden, Freigaben und Benutzer unverändert; Datenbankcontainer nicht neu erstellt |

Die Geräteprüfung lief auf einem isolierten Android-Emulator, nicht auf den
physischen Geräten der Nutzer. Browser- und Gerätetests verwenden ausschließlich
synthetische Daten. Der Live-Download wurde zusätzlich über HTTPS geprüft.
Die früheren Korrekturen für schmale Kalender und große Schrift bleiben erhalten.
Vier Tests im erweiterten englischen Kalenderlauf verwendeten noch deutsche
Texte. Sie nutzen nun lokalisierte Ressourcen; anschließend wurden beide Sprachen
vollständig erneut erfolgreich geprüft. Die fachlichen Assertions blieben erhalten.

Der Live-Container `local/lunara-api:1.3.3-prediction-20261028` wurde aus
Quellrevision `eb710fb` gebaut. API und Web sind gesund. Ausgelieferte Web-Assets
und APK stimmen bytegenau mit den geprüften Artefakten überein. Der HTTPS-Download
bleibt durch die vorhandene Authentifizierung geschützt; Gateway-Zugang allein
gewährt keinen Zugriff auf Gesundheitsdaten. Die Datenbank wurde nicht neu erstellt.
Das Container-Image enthält die APK, ohne zusätzlichen APK-Bind-Mount.

Nur der Lunara-API-Container wurde neu erstellt; andere Dienste blieben unverändert.
Ein eng begrenztes, serverlokales Backup liegt vor. Der Testemulator wurde nach
den bestandenen Läufen beendet; es wurden keine zusätzlichen Testcontainer erstellt.

Die zusätzlichen PostgreSQL-Integrationstests und der damalige Abhängigkeits-Audit
gehören zum vorangegangenen 1.3.1-Prüflauf, nicht zu einer erneuten vollständigen
Datenbank- oder Sicherheitsprüfung dieser Änderung. Es wurden keine
Server-Datenmodelle oder gespeicherten Partnerfreigaben geändert.

## Aussagekraft der Schätzungen

Geprüft wurden API, Android offline und Web offline: Eingabefilter,
Zyklus-/Periodendauer, Start-/Endbereiche, Unsicherheit, Fruchtbarkeitsfreigabe,
Phasen, Tipps, Cache, Tagesgrenzen, Erinnerungsverbraucher und erfasster Verlauf.
Die Prüfungen zeigen Rechen- und Integrationskonsistenz, keine medizinische
Trefferquote. Es gibt keine prospektive klinische Validierung dieser App.

- „Hohe Sicherheit“ ist eine Modellheuristik, kein kalibriertes Prozentmaß.
  Ausgeschlossene lange Abstände senken sie nicht automatisch; das fruchtbare
  Fenster wird bei solchen Daten dagegen unterdrückt.
- Der Endbereich verschiebt den Startbereich um die typische Periodendauer;
  eine eigene Streuung der Blutungsdauer wird nicht modelliert.
- Das fruchtbare Fenster nutzt Kalenderdaten und eine eingestellte bzw.
  standardmäßige Lutealphasenlänge. Temperatur, LH-Tests und Symptome werden
  nicht zur Bestätigung des Eisprungs verrechnet.
- Android nutzt im Servermodus die bekannte Profilzeitzone der trackenden
  Person. Web und Partneransicht kennen sie nicht und verwenden Gerätezeit
  für die aktuelle Zusammenfassung. Rund um Mitternacht kann diese Einordnung
  daher vom Server abweichen; gespeicherte Periodendaten bleiben unverändert.

Die bestehenden medizinischen Annahmen wurden nicht durch unvalidierte neue
Formeln ersetzt. Ein nächster sinnvoller Entwicklungsschritt wäre ein ausdrücklich
datenschutzgerecht geplanter Vergleich früherer Vorhersagen mit später tatsächlich
erfassten Anfängen, einschließlich Fehlermaßen und Intervallabdeckung. Er wurde
nicht als neue Datenerhebung aktiviert. Die Einordnung wurde mit den öffentlichen
Fachinformationen von NHS (Natural family planning) und ASRM (Optimizing natural
fertility, Committee Opinion) abgeglichen.

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
