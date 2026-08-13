# Changelog

## 1.0.5 – 2026-08-13

- Kontopasswort-Regel für Bootstrap, Registrierung, Partnerbeitritt und
  Passwortänderung auf 8 bis 128 Unicode-Codepoints vereinheitlicht. Android,
  API und OpenAPI zählen auch Variationsselektoren jetzt identisch.
- Verpflichtende Zeichenklassen entfernt: Neue Kontopasswörter benötigen keine
  erzwungenen Großbuchstaben, Ziffern oder Sonderzeichen. Lange, einzigartige
  Passphrasen bleiben empfohlen.
- Bestehende Passwörter bei Login, Kontolöschung und der Eingabe des aktuellen
  Passworts mit 1 bis 128 Zeichen kompatibel gehalten, damit spätere
  Richtlinienänderungen keine vorhandenen Konten aussperren. Neu gesetzte
  Passwörter bleiben auf mindestens acht Zeichen begrenzt.
- Argon2id-Hashing, Login-/Passwortwechsel-Rate-Limits, generische
  Anmeldefehler, getrennte Gerätesitzungen und der vollständige Authentik-
  Domainschutz unverändert beibehalten.
- Verschlüsselte Exportpassphrase bewusst nicht gelockert; sie bleibt getrennt
  von Kontopasswörtern auf 12 bis 128 Zeichen begrenzt.
- API-, Android- und OpenAPI-Version auf 1.0.5 angehoben. Tatsächlich
  ausgeführte Build-, Test-, Container- und Live-Ergebnisse werden weiterhin
  ausschließlich in den dafür vorgesehenen Nachweisdokumenten festgehalten.

## 1.0.4 – 2026-08-13

- Authentik auf vollständigen Schutz der gesamten Lunara-Domain umgestellt.
  `/api/v1`, Meta, Login, Health und OpenAPI besitzen keine Skip-Ausnahme mehr.
- Frühere menschliche und getrennte A/B-Gateway-Bindungen durch genau eine
  neutrale Serviceidentität `lunara-native-app` mit genau einem gemeinsamen,
  nicht ablaufenden App-Passwort ersetzt. Alte technische Credentials werden
  bei der Migration ungültig und ihre lokalen Dateien serverseitig archiviert.
- Zwei klar getrennte Authentifizierungsschichten umgesetzt: außen
  `Authorization: Basic …` für Authentik, innen das persönliche kurze Lunara-
  Token als `X-Lunara-Authorization: Bearer …`. Persönliche Konten, Rollen und
  Freigaben bleiben damit trotz des gemeinsamen äußeren App-Zugangs getrennt.
- Android-Onboarding startet ohne vorausgefüllte URL. Eine HTTPS-Userinfo-
  Adresse kann einmalig eingegeben werden; die App extrahiert und verschlüsselt
  das Gateway-Credential und speichert/zeigt danach nur die bereinigte
  Basisadresse. Credential-URLs in QR-Codes werden abgelehnt.
- Basic Header strikt auf den exakten konfigurierten Ursprung begrenzt und
  Browser-Redirects deaktiviert, damit Zugangsdaten nicht an andere Hosts
  gelangen. Der gemeinsame Zugang wird zentral für alle Installationen rotiert.
- Fertilitätsschätzung als bewusstes Opt-in ergänzt. Sie erfordert mindestens
  drei vollständige Zyklen, fällt bei ungeeigneten Sondermodi geschlossen aus,
  trägt explizite Sicherheitsmetadaten und wird nie als Ovulationsbestätigung
  oder Verhütungsmethode dargestellt.
- Separate Partnerfreigabe für das geschätzte fruchtbare Fenster gehärtet:
  Freigabe ist nur bei aktivem persönlichen Opt-in möglich; Deaktivieren der
  Schätzung widerruft sie atomar. Parallele Profil-/Freigabeänderungen werden
  pro Beziehung serialisiert.
- Partnerprojektion weiter minimiert: privater Tag verbirgt aktuelle Phase,
  tatsächliche Periodendaten und Fertilitätsdaten. Nicht freigegebene Notizen
  werden nicht mehr vorsorglich entschlüsselt.
- Partner-Tippkatalog auf 71 respektvolle deutsche und englische Tipps
  erweitert. Auswahl berücksichtigt nur freigegebene Phase/Symptome, Care
  Preferences, Feedback und Priorität; gesperrte Gesundheitsdaten bleiben
  fail-closed.
- Android-Einstellungen um die vollständige Freigabematrix, Fertilitäts-Opt-in
  mit Sicherheitshinweis und acht direkt pflegbare Care Preferences erweitert.
- Partner-Cache bei jeder autoritativen Serverantwort vollständig ersetzt,
  damit ein zuvor sichtbarer Tipp nach Freigabeentzug oder privatem Tag nicht
  lokal weiter angezeigt wird.
- Automatische Vordergrund-/Hintergrundsynchronisierung, persönlicher
  Passwortwechsel sowie Korrektur und Löschen eines Periodenstarts beibehalten
  und in die neue Gateway-Authentifizierung eingebunden.
- API-, Android- und OpenAPI-Version auf 1.0.4 angehoben. Tatsächlich
  ausgeführte Build-, Test-, Container- und Live-Ergebnisse werden getrennt in
  `FINAL_REPORT.md` dokumentiert.

## 1.0.3 – 2026-08-13

- Sichtbares Haushaltsfeld und entsprechende Produkttexte aus Onboarding und
  Einstellungen entfernt. Die intern weiterhin vorhandene Beziehungs-ID dient
  ausschließlich als serverseitige Mandanten- und Autorisierungsgrenze.
- Optionale öffentliche Tracker-Registrierung ergänzt. Bei
  `REGISTRATION_ENABLED=true` können beliebig viele voneinander isolierte Paare
  dieselbe Instanz verwenden; jede Beziehung erlaubt genau ein `TRACKER`- und
  ein `PARTNER`-Konto.
- Permanente Partner-Einladungen ergänzt: zwölf Zeichen, nur einmal verwendbar,
  jederzeit widerrufbar und als passwortfreier QR-Code teilbar. Der Rohcode
  wird nur bei der Erstellung ausgegeben und serverseitig ausschließlich als
  Hash gespeichert. Ein neuer offener Partnercode widerruft ältere Codes
  derselben Beziehung.
- Gleichzeitiges Registrieren, Einladen und Beitreten durch
  transaktionsgebundene Beziehungssperren gegen doppelt vergebene Rollen und
  mehrfach beanspruchte Codes gehärtet.
- Erfolgreichen Erst-Bootstrap dauerhaft in einem singletonartigen
  `ServerState` gespeichert. Das Löschen aller Konten öffnet Setup nicht mehr;
  bestehende Installationen werden bei der additiven Migration als bereits
  eingerichtet erkannt und der `BOOTSTRAP_TOKEN` kann danach entfernt werden.
- App-Verbindung auf die normale HTTPS-Basisadresse
  `https://lunara.3li361.de` umgestellt. Die App benötigt keine Authentik-
  Header-Zugangsdaten mehr.
- Authentik-Proxy so vorbereitet, dass `/api/v1` ohne Browser-Login zur
  weiterhin durch Lunara-Token, Rollen und Eigentum geschützten API gelangt;
  `/api-docs` und `/api-docs-json` bleiben Authentik-geschützt.
- Bereits in 1.0.2 ergänzten Passwortwechsel in der rollenabhängigen
  Einstellungsansicht beibehalten.
- Tracker-Oberfläche um nachträgliches Bearbeiten des Startdatums und
  bestätigtes Löschen eines fälschlich gestarteten Periodeneintrags ergänzt.
  Beide Aktionen verwenden die Offline-Mutationswarteschlange und werden
  automatisch synchronisiert.
- Synchronisationswarteschlange pro Datensatz geordnet und gegen das
  Überschreiben neuer lokaler Folgeänderungen durch ältere Bestätigungen oder
  Serverstände gehärtet.
- Erfolgreiche Offline-Mutationen werden ohne künstlichen Retry-Backoff bis zu
  einer fairen 100er-Grenze geleert; echte Versionskonflikte bleiben sichtbar
  und behalten die lokale Fassung bis zu einer ausdrücklichen Entscheidung.
- Konfliktauflösung lädt die bestätigte Serverfassung neu und sperrt betroffene
  Einträge bis dahin gegen weitere Änderungen. Bewusst geleerte Tageswerte
  werden als explizite `null`-Werte serverseitig zuverlässig entfernt.
- Explizite JSON-`null`-Werte in der Android-Sync-Schicht erhalten, damit
  bewusst geleerte optionale Tageswerte serverseitig zuverlässig gelöscht
  werden; Abwesenheit eines Felds bleibt weiterhin von `null` unterscheidbar.
- Datenbankconstraint ergänzt, das pro Person und Kalenderdatum höchstens einen
  nicht gelöschten Periodenstart erlaubt.
- API-, Android- und OpenAPI-Version auf 1.0.3 angehoben. Final bestanden:
  Prisma-Erzeugung/-Validierung, Backend-Lint und -Build, 14/14 Suites mit
  59/59 Tests, OpenAPI-Hashprüfung sowie 63/63 Android-JVM-Tests, beide Lints,
  Instrumentierungstest-Kompilierung und Production-Debug-APK-Build.
- Sechs Migrationen produktiv angewendet, `BOOTSTRAP_TOKEN` nach bestätigtem
  persistentem Setupstatus aus der Live-Konfiguration entfernt und nur die
  Lunara-API aktualisiert. Die vorhandenen Emby-, Authentik-, Pangolin- und
  PostgreSQL-Container wurden nicht neu gestartet.
- Production-Debug-APK 1.0.3 erzeugt: 24.362.451 Bytes, SHA-256
  `4dac957e020684f3d5ee5699fdc584a0f68393a58a76452801a21831616dd416`.
  Instrumentierte Tests wurden mangels Gerät/Emulator nur kompiliert.
- Quellstand und APK 1.0.3 im privaten GitHub-Branch
  `agent/authentik-human-access` veröffentlicht; der APK-Download erfordert
  eine berechtigte GitHub-Anmeldung. Ein separates Release-Tag wurde nicht
  erzeugt.
- Der echte PostgreSQL-E2E-Lauf wurde nach den beiden finalen Migrationen nicht
  erneut ausgeführt; ein erfolgreicher Lauf des früheren 1.0.3-Zwischenstands
  wird nicht als Abschlussnachweis für den finalen Stand gewertet.

## 1.0.2 – 2026-08-13

- Produktions-Onboarding startet ohne vorausgefüllte Domain; die persönliche
  Authentik-Header-Auth-URL muss bewusst eingegeben werden.
- Onboarding- und rollenbezogene In-Memory-Zustände werden nach Anmeldung,
  Abmeldung und Kontowechsel sicher zurückgesetzt. Dadurch erscheint ein
  bereits abgeschlossener Bootstrap nicht mehr aus einem alten App-Cache.
- Bootstrap-, Benutzername-, Passwort-, Haushalts- und Einladungseingaben
  werden mit feldgenauen, servergleichen Regeln erklärt; falsche
  Anmeldedaten erhalten eine eigene verständliche Meldung.
- Authentik-Cookies werden vor jedem ausdrücklichen Verbindungstest entfernt,
  auch wenn URL und Zugangsdaten unverändert sind.
- Der dynamische Meta-/Bootstrap-Status trägt `Cache-Control: no-store` und
  kann dadurch nicht mehr als veraltete Proxy-Antwort wiederverwendet werden.
- Automatische Synchronisierung nach Anmeldung und lokalen Änderungen,
  beim Öffnen der App, einmal pro Minute im Vordergrund sowie periodisch im
  Hintergrund ergänzt. Gleichzeitige Worker und Refresh-Rotation sind
  serialisiert; dauerhafte eindeutige WorkManager-Aufträge verhindern verlorene
  Trigger und unbeschränkt wachsende Retry-Ketten. Nach acht Fehlversuchen ist
  ein manueller, ausdrücklich ausgelöster Neuversuch erforderlich.
- Partner-Dashboard und heutiger Tipp werden nach erfolgreichen Syncs reaktiv
  aktualisiert; rollenbezogene Cache-Daten sind an die aktuelle User-ID
  gebunden.
- Sicheren Lunara-Passwortwechsel in den Einstellungen ergänzt. Er verlangt
  das aktuelle Passwort, nutzt Argon2id und widerruft alle anderen
  Lunara-Serversitzungen, ohne das getrennte Authentik-App-Passwort zu
  verändern. Offline-Daten auf anderen Geräten bleiben ausdrücklich erhalten.
- Login, Refresh-Rotation und Passwortwechsel werden pro Konto in PostgreSQL
  serialisiert, damit kein paralleler Login mit dem alten Passwort eine neue
  Sitzung nach dem Passwortwechsel anlegen kann.
- Passwortwechsel-Auditereignisse, Rate-Limit, E2E-Abdeckung und aktualisierten
  OpenAPI-/Kotlin-Vertrag ergänzt.

## 1.0.1 – 2026-08-13

- Authentik-Header-Auth nach einem anonymen Verbindungsversuch repariert:
  anonyme Proxy-Cookies unterdrücken den erforderlichen Basic-Header nicht mehr.
- Gateway-Zugangsdaten strikt auf Ursprungsschema, Host und Port des
  konfigurierten Servers begrenzt; Browser-Login-Weiterleitungen werden nicht
  mehr als API-Antwort verfolgt.
- Veraltete Authentik-Cookies bei Server- oder Zugangsdatenwechsel entfernt und
  eine verständliche Gateway-Fehlermeldung ergänzt.
- Eingefügten `/api/v1/meta`-Testpfad automatisch auf die Serverbasis
  normalisiert und das vorausgefüllte URL-Schema im Onboarding verdeutlicht.
- Debug-Signierschlüssel für wiederholbare lokale APK-Updates persistent und
  Git-ausgeschlossen gemacht.

## 1.0.0 – 2026-08-12

- Separates Lunara-Monorepo mit NestJS-/Prisma-API, PostgreSQL 18 und nativer
  Android-Compose-App erstellt.
- Sicheren Bootstrap, getrennte Rollen/Konten, Einladungen,
  Haushaltsisolation, Perioden-/Tageslog, robuste Prognose und granulare
  Partnerprojektion implementiert.
- Offline-Sync, SQLCipher/Keystore, vollständigen lokalen Wipe,
  50 zweisprachige Tipps, Care Preferences, Check-ins und Datenverwaltung
  ergänzt.
- Produktionsnahe Docker-, CI-, Backup-, Restore-, Admin-, Authentik- und
  Pangolin-Konfiguration angelegt.
- HTTPS-only Production-Debug-APK und versionierte OpenAPI-Artefakte gebaut.
- Noch offene Komfort-/Zusatzfunktionen und reale Gerätetests in
  `FINAL_REPORT.md` und `PROGRESS.md` transparent dokumentiert.
