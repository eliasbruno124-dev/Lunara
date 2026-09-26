# Changelog

## 1.2.0

- Neue Journal-Gestaltung für Android und Web: Petrol/Elfenbein, redaktionelle
  Typografie, eigenständige Hauptansichten und zusammenpassender Dunkelmodus.
- Android-Einstellungen in erreichbare Kategorien gegliedert; Today-Aktionen,
  Partnerunterstützung und Prognose klarer gewichtet.
- Tipps als konkrete Alltagshandlungen statt allgemeiner Sinnsprüche;
  stabile IDs, persönliche Wünsche und Freigabefilter bleiben erhalten.
- Kalenderweiterentwicklung ohne Änderung vorhandener Zeitraum-/Syncdaten.
- Zusätzliche Darstellungs- und Interaktionsprüfungen; genaue Ergebnisse im
  Abschlussbericht. Vorhersageformel und Konten werden nicht geändert.

## 1.1.6 – 2026-09-21

- Partnerverknüpfung für beide Rollen unabhängig von Einladungen abrufen;
  unbekannter Status und Abruffehler werden nicht als Trennung angezeigt.
- Verschlüsselter, server-/kontogebundener Beziehungscache mit erkennbarem
  Zeitstempel, ohne Änderung vorhandener Konten oder Freigaben.
- Klarer Server-/Solomodus und Details zum letzten Abgleich; getrennte App-
  und Serverversionsanzeige statt zweier unbeschrifteter Versionsnummern.
- 50 überarbeitete deutsche/englische Unterstützungstipps, abwechslungsreiche
  Auswahl, konkrete Vorlieben statt pauschaler Kategoriezuordnung und
  „Anderer Vorschlag“ ohne unbeabsichtigtes Ausblenden.
- Eigene, ausdrücklich geteilte Wünsche in Android erstellen/deaktivieren.
  Unveränderte Worte haben Vorrang; Quellen/Grundlage der Auswahl sind sichtbar.
- Optionaler PayPal.me-Link in zentralem Branding, explizite Bestätigung,
  externer Browser, keine Zahlungsintegration oder Gesundheitsdatenübertragung.
- Startfenster und Anzahl zugrunde liegender Zyklen deutlicher erklären;
  keine Änderung oder behauptete höhere Präzision der Prognoseformel.
- Einstellungs-Schalter über die ganze beschriftete Zeile bedienbar und für
  TalkBack zusammengefasst; vollständige Instrumentierung im Emulator ausgeführt.

## 1.1.5 – 2026-09-21

- Android erneuert Anmeldungen automatisch mit einer dauerhaft gespeicherten
  Anfrage-ID. Die API kann verlorene Refresh-Antworten sicher wiederholen,
  ohne eine absichtlich widerrufene Sitzung wiederzubeleben.
- Bereits abgelaufene Anmeldungen lassen sich im selben Konto über Banner
  oder Einstellungen erneuern; lokale Daten, Mutation-IDs, Cursor und
  Freigaben bleiben erhalten. Falsche Identitäten werden nicht übernommen.
- Netz-/Serverfehler werden mit Backoff wiederholt. Verzögerte Fehler einer
  alten Anfrage sperren keine inzwischen erfolgreich erneuerten Zugangsdaten.
- Partnerdaten werden auch bei fehlgeschlagenem optionalem Tippabruf
  aktualisiert; veraltete Daten und letzte Aktualisierung werden sichtbar.
- Gerätewiderruf und administrative Abmeldung sind gegen parallele
  Refresh-Rotation serialisiert. Wiederholungsbelege verwenden eine von
  Gesundheitsnotizen kryptografisch getrennte Verschlüsselung.
- Gezielt gepatchte Abhängigkeiten: Multer 2.3.0, js-yaml 3.15.2/4.3.2 und
  Sharp 0.35.4. Keine automatische Major-Migration des Backend-Frameworks.
- Grenzen der Prognosegenauigkeit dokumentiert; die Formel bleibt unverändert
  und ist nicht klinisch validiert.

## 1.1.4 – 2026-09-08

- Partner-Einladungen bleiben bei der Authentik-Anmeldung erhalten. Die PWA
  verwendet eine eng begrenzte öffentliche Landeseite mit Fragment-Übergabe.
- Bestehende leere Konten lassen sich nach ausdrücklicher Bestätigung verbinden,
  ohne ein zweites Benutzerkonto anzulegen. Vorhandene Daten bleiben geschützt.
- Android unterstützt Code-/QR-Eingabe direkt in Einstellungen und die
  Bestätigung nach Authentik-Login; alte lokale Beziehungsdaten werden beim
  Wechsel verworfen und die serverseitige Rolle erneut geladen.
- Unterbrochene OIDC-Abläufe bewahren aktive Einladungen verschlüsselt;
  abgebrochene Anmeldungen übernehmen keinen veralteten Einladungscode.
- Einladungen enthalten eine kanonische Share-URL, bleiben einmal verwendbar
  und sind auf Wunsch ohne zeitlichen Ablauf widerrufbar.
- PostgreSQL-Integrationstests für bestehende Konten, Partnerrechte und
  wiederholte Annahme ergänzt; OpenAPI-/Kotlin-Client aktualisiert.
- Drei gezielte npm-Overrides schließen bekannte transitive Schwachstellen.

## 1.1.0 – 2026-08-25

- Vollständig lokalen Android-Solomodus ohne Konto, Domain oder Server
  ergänzt. Perioden, Tageslogs, lokale Prognose, Verlauf und Erinnerungen
  bleiben in der SQLCipher-Datenbank des Geräts; Server-, Partner- und
  Synchronisierungswege fallen im Solomodus geschlossen aus.
- Portables, mit PBKDF2-HMAC-SHA256 und AES-256-GCM geschütztes
  `.lunara`-Backup über Android Storage Access Framework ergänzt. Dadurch ist
  ein bewusster Gerätewechsel über lokalen Speicher, USB oder einen vom
  Benutzer gewählten Anbieter wie Google Drive/Nextcloud möglich, ohne
  Google-SDK oder automatisches Cloudbackup. Ein Import-Journal vollendet
  unterbrochene Einstellungswiederherstellung idempotent.
- Installierbare TypeScript-PWA mit Deutsch/Englisch, Hell/Dunkel,
  rollenabhängigen Dashboards, wischbarem Kalender, Offline-Queue,
  automatischem Sync und portablem Backup ergänzt. Ihr lokaler Solomodus
  versiegelt Gesundheitsdaten passphrasenbasiert in einem AES-GCM-Vault;
  Konto-Caches sind isoliert und API-/Health-/OpenAPI-Antworten werden nicht
  vom Service Worker gecacht.
- Optionalen persönlichen Authentik-OIDC-Login als Authorization Code Flow mit
  PKCE S256, State, Nonce und externem Tokenrefresh ergänzt. Das bestehende
  Lunara-Konto wird nur nach expliziter Reauthentifizierung beziehungsweise
  bestätigter Adminmigration mit exakt geprüftem Issuer/Subject verknüpft; es
  gibt kein Auto-Provisioning anhand von E-Mail oder Benutzername.
- Dualen Authvertrag eingeführt: Authentik-Bearer außen in `Authorization`,
  persönlicher Lunara-Bearer innen in `X-Lunara-Authorization`. Beide werden
  nur same-origin gesendet und erfüllen getrennte Aufgaben.
- Android-Production-OIDC verwendet unabhängig von der Self-hosting-API-Domain
  immer den festen, beim Build konfigurierten und verifizierten Publisher-App-
  Link. Der Production-Provider registriert ausschließlich Android-HTTPS und
  PWA-HTTPS. Ein Custom Scheme existiert ausschließlich im Android-`local`-
  Flavor für einen getrennten Entwicklungsprovider mit eigener client_id;
  Production einschließlich Production-Debug enthält keinen solchen Filter.
- Direct-Gatewaymodus ohne Authentik ergänzt. `scripts/compose.sh` lädt den
  korrekten Direct-Override beziehungsweise die Authentik-OIDC-Env; Backup,
  Restore, Update und Admin verwenden denselben Moduswrapper.
- Authentik-Provisioner ergänzt: öffentliche OIDC-Anwendung ohne Client
  Secret, eng begrenzte Benutzergruppe, explizite Zustimmung, asymmetrisches
  RS256, strikte Redirects, öffentliche Meta-/Asset-Link-Ausnahmen und
  validiertes Infrastrukturbackup vor Änderungen.
- Öffentlichen `/.well-known/assetlinks.json`-Endpoint mit strikter
  Package-/Zertifikatsvalidierung ergänzt. Google-Play-App-Signing-Key und
  serverlokaler Upload-Key sind getrennt dokumentiert.
- Kalender auf horizontales Monatswischen umgestellt, fünf zukünftige Jahre
  sichtbar gemacht und geschätzte Periodentage klar von tatsächlichen
  Einträgen unterschieden.
- Prognose auf `lunara-robust-v2` gehärtet: bis zu zwölf aktuelle vollständige
  Zyklen, gewichteter Median, robuste und erklärte Ausreißerbehandlung,
  Mindestunsicherheit, aktuelle Beobachtungsgrenze und fail-closed Sondermodi.
- Android-Releasekonfiguration auf VersionCode 11/VersionName 1.1.0
  aktualisiert. Upload-Signing wird nur mit vollständiger externer
  `LUNARA_UPLOAD_*`-Konfiguration aktiviert; Secrets und Keystores bleiben
  außerhalb des Repositorys.
- Backend/PWA-Multi-Stage-Build, PostgreSQL-18-Migrationen, JVM-/Webtests,
  Android-Lints, Instrumentierungskompilierung und APK/AAB-Buildmatrix
  erweitert. Instrumentierte Gerätetests bleiben ausdrücklich noch offen.

## 1.0.9 – 2026-08-20

- Periodenzeiträume werden ohne fehleranfällige Datumstexteingabe gewählt:
  erster Kalendertag = Beginn, zweiter Kalendertag = Ende. Derselbe Tag ist
  für eine eintägige Periode möglich; ein früherer zweiter Tap setzt den
  Beginn verständlich neu.
- Der neue visuelle Bereichswähler markiert Start, alle Zwischentage und Ende,
  zeigt die inklusive Dauer, unterstützt Monatswechsel und bietet eine
  ausdrücklich bezeichnete Aktion für eine noch laufende Periode.
- Erstellen und Bearbeiten verwenden denselben kalenderbasierten Ablauf.
  Überschneidungen werden vor dem Speichern angezeigt und blockiert; beim
  Bearbeiten wird der eigene Datensatz korrekt von der Prüfung ausgenommen.
- GitHub-Validierungsworkflows laufen bei Feature-Branches nur noch über den
  Pull Request und bei Pushes nur auf `main`. Release-Tags starten dadurch
  keine zweite identische Prüfrunde und erzeugen deutlich weniger Meldungen.
- Die von Prisma transitiv verwendete Bibliothek `deepmerge-ts` ist nach einer
  neu veröffentlichten HIGH-Sicherheitsmeldung zentral auf 8.0.1 fixiert.
  Lockfile-Installation, npm-Audit, Prisma, Backend-Tests, Container-Build und
  Trivy-Scan wurden mit der korrigierten Version erneut erfolgreich geprüft.

## 1.0.8 – 2026-08-16

- Android-Zielverhalten auf den im stabilen SDK-Kanal verfügbaren API-Level
  36 korrigiert. Kompiliert wird weiterhin mit API 37 aus dem Preview-Kanal,
  weil die aktuell gepinnten AndroidX-Bibliotheken dieses Compile-SDK bereits
  voraussetzen; dadurch wird kein Android-17-Laufzeitverhalten aktiviert.
- Android auf VersionCode 9/VersionName 1.0.8 angehoben. Die App bleibt mit
  der unveränderten Lunara-API v1 und dem produktiven Server 1.0.7 kompatibel.
- GitHub-CI richtet das benötigte Preview-Compile-SDK nun explizit ein;
  Backend-E2E verwendet Node 24.18.0 auf dem Runner gegen eine ausschließlich
  an `127.0.0.1` gebundene PostgreSQL-18-Testinstanz. Der Testport ist lokal
  überschreibbar und im isolierten GitHub-Runner fest definiert. Das flüchtige
  Test-Bridge-Netz erlaubt dabei ausschließlich den benötigten Runner-Zugriff
  über diese Loopback-Bindung.
- Die geheime Historienprüfung ignoriert exakt den Fingerprint eines
  dokumentierten, veröffentlichten OpenAPI-SHA-256-Fehlalarms, ohne andere
  Regeln, Commits oder Dateien von der Suche auszunehmen.

## 1.0.7 – 2026-08-16

- Im Kalender können Tracker vergangene Perioden vollständig mit Start- und
  optionalem Enddatum nachtragen. Ein vorhandener Zeitraum lässt sich durch
  Antippen korrigieren oder nach Bestätigung löschen; Partner bleiben
  vollständig schreibgeschützt.
- Die Hauptaktion reagiert auf den tatsächlichen lokalen Zustand: „Periode
  begonnen“, während einer offenen Periode „Periode heute beenden“ und nach
  dem Abschluss eine sichtbare Bestätigung. Snackbar und Sync-Status bestätigen
  Speicherung beziehungsweise ausstehende Übertragung.
- Historische Zeiträume werden atomar als ein Room-Datensatz und eine einzige
  idempotente Offline-Mutation angelegt. Automatischer WorkManager-/Foreground-
  Sync, Cursor-Pull, Backoff und manueller Sync bleiben aktiv.
- Kalenderdialoge behalten Entwürfe bei Activity-Neuerstellung, sperren
  Mehrfach-Submit während der lokalen Transaktion und schließen erst nach der
  tatsächlich beobachteten Room-Änderung. Fehler lassen den Entwurf für einen
  bewussten Wiederholungsversuch geöffnet.
- Kalenderdarstellung auf konstante Speichernutzung gehärtet: Auch extrem alte
  offene Datensätze werden nicht mehr Tag für Tag materialisiert.
- Überschneidende aktive Perioden werden nun auch serverseitig pro Tracker
  transaktional serialisiert und mit HTTP 409 abgewiesen. Eine partielle
  PostgreSQL-GiST-Exclusion-Constraint schützt zusätzlich gegen konkurrierende
  Geräte und direkte Datenbank-Races; Soft Deletes bleiben ausgenommen.
- Idempotenz-Receipt, Offline-Mutation, SyncChange und Prognosecache werden für
  Perioden und Tageslogs atomar abgeschlossen. Alte Pending-Receipts lassen
  sich nur bei exakt passendem Eigentümer, Endzustand, Version und SyncChange
  sicher wiederherstellen.
- Abgelaufene Idempotenz-Keys werden atomar und race-sicher neu beansprucht,
  auch wenn sie zu einem früheren Request oder Haushalt gehörten. Aktive Keys
  bleiben geschützt; ein nachweislich bereits ausgeführter alter Request wird
  vor einer Wiederholung wiederhergestellt.
- Periodenimporte erkennen Überschneidungen in Vorschau und Commit, planen
  unter demselben Tracker-Lock erneut und aktualisieren die Prognose atomar.
- Android auf VersionCode 8/VersionName 1.0.7 und Server, Container sowie
  OpenAPI-Metadaten auf 1.0.7 angehoben.
- Der vollständige Kernablauf wurde nach allen sieben Migrationen gegen eine
  echte frische PostgreSQL-18-Datenbank erfolgreich ausgeführt.
- Das produktive Runtime-Image basiert auf einer minimalen Debian-Stufe mit
  ausschließlich kopiertem Node-Binary; npm, npx und Corepack sind nicht mehr
  enthalten. Migrationen laufen direkt über die gepinnte Prisma-CLI.

## 1.0.6 – 2026-08-14

- Lokale Android-Benachrichtigungen als ausdrückliches Opt-in ergänzt; keine
  Firebase-, Werbe-, Analyse- oder externe Push-Abhängigkeit.
- Getrennt wählbare Hinweise für den geschätzten Periodenzeitraum am Folgetag,
  das nur grob geschätzte fruchtbare Fenster am Folgetag, die tägliche
  Tracker-Eingabe und einen Partner-Unterstützungstipp.
- Gerätebezogene Einstellungen für Uhrzeit, Ruhezeit, maximal ein bis drei
  Hinweise pro Tag und neutrale Sperrbildschirmvorschau ergänzt.
- Erinnerungsplanung als täglich neu ausgerichtete lokale Arbeit umgesetzt,
  damit lokale Uhrzeit, Zeitzonenwechsel und Sommerzeit berücksichtigt werden;
  Android darf die Ausführung aus Energiespargründen weiterhin verzögern.
- Prognose- und Zustellzustände Keystore-gestützt verschlüsselt, Hinweise erst
  nach erfolgreicher Übergabe an Android als zugestellt markiert und Partner-
  Hinweise bei fehlender aktueller Serverantwort strikt unterdrückt.
- Partner-Hinweise verwenden ausschließlich das bereits serverseitig nach
  Freigaben gefilterte Dashboard. Fertilitätshinweise bleiben bei fehlendem
  Opt-in, zu wenig Daten, ungeeigneten Sondermodi, Freigabeentzug oder
  Offlinebetrieb aus.
- Android-Client auf 1.0.6/VersionCode 7 angehoben. Die kompatible Server-API
  bleibt unverändert auf 1.0.5; für dieses Client-Update ist kein Serverneustart
  erforderlich.

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
