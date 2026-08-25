# Datenschutz

Lunara enthält keine Werbung, Telemetrie, externe Nutzungsanalyse,
Marketing-SDKs, Firebase- oder Google-Play-Services-Pflicht. Welche Daten ein
externer Reverse Proxy, Authentik, DNS-/CDN-Anbieter, Google Play, das
Betriebssystem oder ein vom Benutzer ausgewählter Dateidienst verarbeitet,
liegt außerhalb des Lunara-Anwendungscodes und muss der jeweilige Betreiber
gesondert dokumentieren.

## Betriebsarten und Speicherorte

| Modus | Primärer Speicher | Netzwerkübertragung |
| --- | --- | --- |
| Android Solo | SQLCipher-Datenbank und Keystore-geschützte Einstellungen auf einem Gerät | keine Kernübertragung; nur ein vom Benutzer bewusst gewählter Backup-Dateianbieter |
| Self-hosted Android | verschlüsselter lokaler Cache plus eigene Lunara-Instanz | HTTPS zur konfigurierten Domain |
| PWA Solo | passphrasenverschlüsselter Vault in origin-gebundenem IndexedDB | keine Kernübertragung |
| Self-hosted PWA | origin-gebundener Offlinecache plus eigene Lunara-Instanz | HTTPS zur gleichen Origin und optional zu den konfigurierten OIDC-Endpunkten |

## Solo auf Android

Der Solomodus benötigt weder Konto noch Server. Er speichert Perioden,
Tageslogs, lokale Prognosegrundlage, Profil- und Benachrichtigungseinstellungen
ausschließlich auf dem Gerät. Die Datenbank ist mit SQLCipher verschlüsselt;
Schlüssel und weitere Secrets sind an Android Keystore gebunden.

Automatisches Android-Cloudbackup und systemseitige Gerät-zu-Gerät-
Übertragung sind vollständig deaktiviert. Insbesondere lädt Lunara keine
Gesundheitsdaten automatisch in ein Google-Konto.

Für einen freiwilligen Gerätewechsel kann der Benutzer eine
passphrasenverschlüsselte `.lunara`-Datei erzeugen. Der Android-Systemdateidialog
zeigt alle vom Benutzer installierten Anbieter, darunter gegebenenfalls lokale
Ordner, USB, Nextcloud oder Google Drive. Lunara erhält dabei nur Zugriff auf
die konkret gewählte Datei, integriert kein Google-SDK und kennt keine
Anmeldedaten des Anbieters. Der Anbieter kann die verschlüsselte Datei nach
seinen eigenen Bedingungen verarbeiten.

Die Backup-Passphrase wird nicht gespeichert und ist nicht wiederherstellbar.
Ein Import ersetzt nach strikter Prüfung die vorhandenen Solodaten. Ein
Import-Journal vollendet unterbrochene Einstellungswiederherstellung beim
nächsten Start. Vor dem Löschen des alten Geräts müssen Zeitraum, Tageslogs und
Einstellungen auf dem neuen Gerät geprüft werden.

## Self-hosted gespeicherte Daten

Je nach Verwendung speichert der Server:

- **Konto:** Benutzername, Anzeigename, Sprache, deaktivierter Zustand,
  Gerätesitzungen und serverseitige Rolle;
- **interne Beziehungsgrenze:** UUID-Mandant, Mitgliedschaften, Einladungen,
  Freigaben und Care Preferences;
- **Gesundheit:** Periodenstart/-ende als lokales Kalenderdatum, Tageslogs,
  Blutungsstärke, Schmerz, Stimmung, Energie, Schlaf, Temperatur,
  Ovulationstest-Markierung, Symptome und optionale Notizen;
- **Ableitungen:** Prognosezeitraum, Statistik, Erklärung und nur nach Opt-in
  das grob geschätzte fruchtbare Fenster;
- **Unterstützung:** Tippfeedback, eigene Tipps und zeitlich begrenzte
  Check-ins;
- **Sicherheit:** Anmeldung, fehlgeschlagene Anmeldung, neues Gerät,
  Sitzungs-/Einladungswiderruf, OIDC-Verknüpfung, Freigabeänderung, Export und
  Kontolöschung.

Audit-Ereignisse enthalten keine Gesundheitswerte oder Notizinhalte.
Freitextnotizen werden vor dem Schreiben mit dem Server-Master-Key
verschlüsselt. Daten, Datum, Rolle und sonstige strukturierte Metadaten liegen
nicht anwendungsseitig verschlüsselt in PostgreSQL. Check-in-Texte sind in
Version 1 ebenfalls nicht zusätzlich mit dem Master-Key verschlüsselt.

Eine Instanz kann mehrere Paare aufnehmen. Jede öffentliche Tracker-
Registrierung erzeugt eine neue serverseitig isolierte Beziehungsgrenze. Eine
Partnerperson tritt ausschließlich über einen einmal verwendbaren Code mit
eigenem Konto bei. Die technische Entität heißt intern `Household`, ist aber
kein sichtbares Pflichtkonzept und keine gemeinsame Anmeldung.

## Authentik/OIDC

Authentik ist optional. Ohne Authentik verwendet Lunara nur die eigenen
Konten. Mit Authentik veröffentlicht `/api/v1/meta` die nicht geheimen OIDC-
Konfigurationswerte, damit der Client den Login starten kann.

Nach bewusster Verknüpfung speichert Lunara:

- den exakt geprüften OIDC-Issuer;
- das opaque Subject der Authentik-Identität;
- Zeitpunkt der Verknüpfung und letzten Nutzung;
- die Zuordnung zum bestehenden Lunara-Benutzer.

Lunara übernimmt keine E-Mail-Adresse, kein Authentik-Passwort und kein Profil
automatisch. Ein OIDC-Zugriffstoken wird nur zur Prüfung und zum Öffnen des
Authentik-Proxys verwendet; Lunara speichert serverseitig keine Klartextkopie
davon. Authentik selbst kann Login-, IP- und Sicherheitsereignisse nach der
Konfiguration des Instanzbetreibers protokollieren.

`/api/v1/meta` und `/.well-known/assetlinks.json` sind im Authentik-Modus
öffentlich. Meta enthält Produkt-/Versions-/Kompatibilitätsdaten, keine
Konten. Asset Links enthält nur Package-ID und öffentliche
Zertifikatsfingerprints.

## Partnerfreigaben

Standardmäßig sieht `PARTNER` nur den erwarteten Periodenzeitraum. Notizen,
Symptome, Blutungsstärke, Schmerzen, Stimmung, Energie, Schlaf, tatsächliche
Periodendaten, Phase und Fertilitätsdaten sind aus.

Bei jeder Partneranfrage filtert der Server anhand:

- der aktuell aktiven Einzelfreigaben;
- einer globalen Freigabepause;
- eines optionalen privaten Tags;
- des persönlichen Fertilitäts-Opt-ins;
- der Einstellung für symptomabhängige Tipps.

Ein Freigabeentzug wirkt auf die nächste Serverantwort und bereinigt den
autoritativen Partnercache. Er kann keine zuvor außerhalb von Lunara
gespeicherten Screenshots, Abschriften oder Erinnerungen zurückholen. Tipps
dürfen gesperrte Symptome weder nennen noch indirekt verraten.

Das geschätzte fruchtbare Fenster ist standardmäßig deaktiviert, benötigt
ausreichende Daten und eine getrennte Partnerfreigabe. Es bestätigt keine
Ovulation und darf nicht zur Verhütung verwendet werden.

## PWA und Offline-Speicher

Die PWA speichert Perioden, Offline-Mutationen, Sync-Cursor und Sitzungszustand
in origin-gebundenem IndexedDB. Refresh- und Access-Tokens werden mit Web
Crypto gekapselt. Im lokalen Solomodus liegt der komplette Vault mit
AES-256-GCM und einem aus der nicht gespeicherten Solo-Passphrase abgeleiteten
Schlüssel versiegelt vor; nach einem Seitenneustart ist erneutes Entsperren
nötig.

Im Self-hosted Modus können synchronisierte Gesundheitsdaten als normaler
origin-gebundener Offlinecache gespeichert werden. Dieser Servercache ist
nicht zusätzlich passphrasenverschlüsselt. Nutzer sollen ein persönliches,
gesperrtes Browserprofil verwenden und die PWA auf geteilten Geräten
abmelden.

Screenshot-Schutz und Browserbenachrichtigungen sind in der PWA derzeit nicht
ausführbar. Entsprechende importierte Einstellungen werden nicht verworfen,
sondern im verschlüsselten Vault erhalten und als im Browser nicht anwendbar
angezeigt. Der Solo-Vault sperrt bei versteckter Seite, `pagehide` und nach
spätestens 15 Minuten Inaktivität; danach ist die Passphrase erneut nötig.

Beim Kontoidentitätswechsel oder Logout werden kontogebundene Caches und
Mutationsqueues bereinigt. Der Service Worker cached keine API-, Health-,
Ready- oder OpenAPI-Antworten.

## Benachrichtigungen

Android-Erinnerungen sind pro Gerät standardmäßig aus und laufen lokal über
WorkManager. Es gibt keine Firebase-Verbindung. Konfigurierbar sind unter
anderem der geschätzte Periodenzeitraum, das nur grob geschätzte fruchtbare
Fenster, Tracker-Eingabe, Partner-Tipp, Wunschzeit, Ruhezeit und maximale
Tageshäufigkeit. Der Sperrbildschirmtext ist standardmäßig neutral.

Partnerhinweise benötigen eine aktuelle serverseitig freigabefilterte
Antwort. Ohne aktuelle Verbindung werden keine alten sensiblen Partnerhinweise
erzeugt. Android darf WorkManager-Aufgaben aus Energiespargründen verzögern.

## Export, Import und Löschung

Die Server-API unterstützt JSON, Perioden-CSV, Tageslog-CSV sowie einen
AES-256-GCM-passwortgeschützten JSON-Export. Der Serverimport validiert Lunara-
JSON, bietet eine Vorschau und erkennt Duplikate. Ein visueller generischer
CSV-Zuordnungsassistent und vollständiges Import-Undo sind noch nicht
implementiert.

Die native Solofunktion verwendet das separate portable `.lunara`-Format und
ist direkt in den Android-Einstellungen verfügbar. Es ersetzt nach Bestätigung
den lokalen Solo-Datenbestand; Serverkonten werden dadurch nicht importiert.

Die Kontolöschungs-API verlangt eine erneute persönliche Bestätigung,
widerruft Sessions und löscht den betroffenen internen Mandanten entsprechend
der Rolle. Die vollständige Android-Oberfläche für serverseitigen Export,
Import und Kontolöschung ist noch nicht abgeschlossen. Serverbackups können
gelöschte Daten bis zum Ablauf der vom Instanzbetreiber festgelegten
Aufbewahrungsfrist enthalten.

## Verantwortlichkeiten

- Der App-Publisher verantwortet APK/AAB, eingebaute SDKs, Store-Angaben und
  Updatekanal.
- Der Self-hosting-Betreiber verantwortet Serverstandort, Domain, TLS,
  Authentik-/Proxy-Logs, Backups, Aufbewahrung und Adminzugriff.
- Die trackende Person bestimmt die Partnerfreigaben.
- Die Partnerperson darf nur die serverseitig freigegebene Projektion nutzen.
- Ein freiwillig gewählter Backup-Dateianbieter verantwortet seine eigene
  Verarbeitung der verschlüsselten Datei.

Diese technische Datei ersetzt keine auf den konkreten Publisher, Betreiber,
Zielländer und Aufbewahrungsfristen zugeschnittene öffentliche
Datenschutzerklärung. Für Google Play werden eine anonym erreichbare
Datenschutzseite und eine Kontolöschungsseite benötigt.
