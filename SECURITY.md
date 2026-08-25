# Sicherheit

## Bedrohungsmodell

Lunara schützt Zyklus-, Symptom-, Freitext-, Konto- und Beziehungsdaten gegen:

- Benutzer anderer interner Mandanten;
- eine Partnerperson ohne entsprechende Freigabe;
- IDOR- und Cross-Relationship-Zugriffe;
- gestohlene oder wiederverwendete Refresh-Tokens;
- Antwortverlust und doppelte Offline-Mutationen;
- versehentliche Offenlegung in Logs, Quellcode oder normalen Backups;
- Verlust eines gesperrten Android-Geräts innerhalb der Grenzen von Android
  Keystore, SQLCipher und optionalem Screenshot-Schutz.

Der Betreiber des Debian-Hosts, Root, PostgreSQL-Administrator und laufende
API-Prozess sind vertrauenswürdig. Gegen einen vollständig kompromittierten
Host, ein entsperrtes kompromittiertes Endgerät, bösartige Tastaturen,
Screenshots außerhalb der App oder einen bereits berechtigt angezeigten und
extern kopierten Wert kann Lunara nicht schützen.

## Server- und Mandantenschutz

- PostgreSQL ist nur im internen Docker-Netz erreichbar.
- Die API läuft als UID/GID `10001:10001`, mit read-only Root-Dateisystem,
  `no-new-privileges` und ohne Linux-Capabilities.
- Helmet, restriktives CORS, DTO-Validierung, Text-/Body-Limits und
  strukturierte, redigierte Logs sind aktiv.
- Rollen, Eigentum und die interne UUID-Mandantengrenze werden serverseitig
  geprüft. Eine lokal gewählte Rolle erzeugt keine Rechte.
- Partnerantworten werden bei jeder Anfrage aus aktuellen Freigaben erzeugt;
  nicht freigegebene Notizen werden nicht einmal entschlüsselt.
- Periodenänderungen werden pro Tracker serialisiert. Eine partielle
  PostgreSQL-GiST-Exclusion-Constraint verhindert überlappende aktive
  Datumsbereiche auch bei konkurrierenden Geräten.
- Idempotenz-Receipt, Mutation, `SyncChange` und nötige Prognoseneuberechnung
  werden gemeinsam transaktional abgeschlossen.

Freitextnotizen liegen mit AES-256-GCM verschlüsselt in PostgreSQL.
`APP_MASTER_KEY` ist ein kanonischer Base64-Schlüssel aus exakt 32 zufälligen
Bytes und muss dauerhaft getrennt gesichert werden. Die Verschlüsselung schützt
Notizinhalte in einem Dump, nicht strukturierte Metadaten wie Datum,
Datensatzanzahl oder Rolle. Root beziehungsweise ein kompromittierter
API-Prozess kann den Schlüssel und entschlüsselte Werte auslesen.

## Gateway-Modi

### Direct

Traefik leitet direkt zu Lunara. Persönliche Lunara-Access-Tokens werden als
Bearer-Token akzeptiert; die API prüft weiterhin Konto, Rolle und Eigentum.
OpenAPI ist standardmäßig deaktiviert und muss bei Aktivierung separat mit
einem starken Dokumentationspasswort geschützt werden.

### Authentik

Der Authentik-Proxy schützt alle Pfade außer:

- `/api/v1/meta`: cachefreie, nicht sensible Client-Erkennung;
- `/.well-known/assetlinks.json`: öffentliche Android-App-Link-Bindung.

Die Android-App und PWA führen Authorization Code mit PKCE S256 und
Replay-Schutz im Systembrowser aus. Es gibt kein Client Secret im APK oder
Web-Bundle und keinen OIDC-Login in einer WebView.

Nach erfolgreicher Verknüpfung besitzt eine Anfrage zwei getrennte Tokens:

```text
Authorization: Bearer <Authentik-Access-Token>
X-Lunara-Authorization: Bearer <Lunara-Access-Token>
```

Der Authentik-Token wird ausschließlich an exakt denselben HTTPS-Ursprung
gesendet und vor dem Proxyaufruf bei Bedarf erneuert. OIDC-basierte Lunara-
Refreshes verlangen zusätzlich einen frischen externen Token derselben exakt
verknüpften Identität. Der innere Lunara-Token bleibt für alle fachlichen
Rechte maßgeblich.

Der Server prüft OIDC mit gepinntem HTTPS-Issuer, Audience, Subject, `iat`,
`exp`, maximalem Tokenalter, Uhrtoleranz und einer expliziten asymmetrischen
Algorithmusliste. Schlüssel kommen aus dem konfigurierten JWKS-Endpunkt. Ein
Token mit falschem Issuer, Audience, `azp`, Algorithmus, Alter oder Subject
wird generisch abgelehnt.

Lunara führt kein Auto-Provisioning und kein Linking anhand von E-Mail,
Anzeigename oder Benutzername durch. Selbstverknüpfung verlangt eine aktive
Lunara-Sitzung und das aktuelle Lunara-Passwort. Administrative Verknüpfung
verlangt exakte UUID, Issuer, Subject und einen abgeleiteten Bestätigungswert;
optional wird der alte Passwortlogin deaktiviert und alle Sitzungen werden
widerrufen.

Der Authentik-Provisioner verwendet einen öffentlichen Client, strikte
Redirect-URIs, explizite Zustimmung, `openid offline_access`, kurze Codes,
15-minütige Access-Tokens, 30-tägige Refresh-Tokens und eine eng begrenzte
Benutzergruppe ohne Rollen. Er verweigert unbekannte oder teilweise bestehende
Konfigurationen und legt vor einer Mutation einen validierten Datenbankdump an.

## Android App Link

Production verwendet unabhängig von API- und Issuer-Domain immer den beim
Build konfigurierten, verifizierbaren HTTPS-Publisher-Callback. Android bindet
ihn nur an `de.lunara.app`, wenn `assetlinks.json` auf genau diesem Callback-
Host den SHA-256-Fingerprint des installierten App-Signing-Zertifikats enthält.

Die Produktionskonfiguration darf erst aktiviert werden, nachdem der
Fingerprint des tatsächlich ausgelieferten Zertifikats eingetragen wurde. Bei
Google Play ist das der Play-App-Signing-Key; der Upload-Key allein reicht
nicht. Ein falscher oder alter Fingerprint führt fail-closed dazu, dass Android
die Domain nicht verifiziert. Der Production-OIDC-Provider registriert nur den
Android-HTTPS- und den PWA-HTTPS-Redirect.

Ein paketgebundenes Custom Scheme bleibt ausschließlich für den Android-
`local`-Flavor mit einem getrennten lokalen Entwicklungsprovider und eigener
client_id. PKCE schützt einen abgefangenen Code, authentifiziert aber einen
öffentlichen Client nicht. Wäre dasselbe Custom Scheme am Production-Client
registriert, könnte eine fremde App mit der öffentlichen client_id und einem
eigenen PKCE-Verifier einen neuen Login starten und sich gegenüber dem Nutzer
als Lunara ausgeben. Deshalb ist dieser Redirect im Production-Provisioning
absichtlich nicht vorhanden. Das Production-Manifest – auch der
Production-Debug/Testbuild – enthält überhaupt keinen Custom-Scheme-Filter;
dieser Testbuild kann den Production-OIDC-Flow nicht abschließen.

Ein Betreiber mit eigener Distribution setzt `LUNARA_APP_LINK_HOST` auf einen
eigenen kontrollierten Callback-Host und veröffentlicht dort Asset Links für
sein eigenes Signing-Zertifikat.

Der Zertifikatsfingerprint ist öffentlich. Private Keystores, Schlüssel,
Alias- und Passwortwerte bleiben ausschließlich serverlokal und werden nur
über `LUNARA_UPLOAD_*` an Gradle übergeben.

## Lunara-Passwörter und Sitzungen

Neue Passwörter enthalten 8 bis 128 Unicode-Codepoints, ohne vorgeschriebene
Zeichenklassen. Eine lange, einzigartige Passphrase bleibt empfehlenswert.
Bestehende Passwörter werden zur Migrationskompatibilität mit 1 bis 128
Codepoints verifiziert; neu setzen kann man weiterhin nichts unter acht.

- Passwörter werden mit Argon2id gehasht.
- Login und sensible Authaktionen sind rate-limitiert und liefern generische
  Fehler.
- Access-Tokens sind kurzlebig.
- Refresh-Tokens liegen nur als Pepper-Hash in PostgreSQL.
- Rotation ersetzt den Token; Replay widerruft die Tokenfamilie.
- Sitzungen sind gerätebezogen und einzeln oder vollständig widerrufbar.
- Ein Passwortwechsel verlangt das aktuelle Passwort und widerruft alle
  anderen Sitzungen.

Der Bootstrap-Token beweist nur den Besitz einer frischen Instanz. Nach dem
ersten Setup wird der Abschluss dauerhaft in `ServerState` gespeichert; das
spätere Löschen von Konten öffnet Bootstrap nicht erneut. Danach den Token aus
der Live-Konfiguration entfernen.

Ein Einladungscode ist einmal verwendbar, widerrufbar und nur als Hash
gespeichert. Sein Rohwert wird einmal ausgegeben und enthält kein Passwort.

## Android: lokale Daten und Solo-Backup

Room nutzt SQLCipher. Der Datenbankschlüssel, Server-Refresh-Token und OIDC-
Zustand werden durch einen nicht exportierbaren Android-Keystore-Schlüssel
geschützt. Im Solomodus erzeugt die App eine zufällige lokale Tracker-ID und
führt weder Login noch Server-Sync aus.

Android-Cloudbackup und systemseitiger Gerätetransfer sind für alle Lunara-
App-Domänen ausgeschlossen. So wird kein verschlüsselter Datenbank- oder
DataStore-Rest ohne den bewusst nicht übertragbaren Keystore-Schlüssel auf ein
neues Gerät kopiert.

Der vorgesehene Transfer ist eine portable `.lunara`-Datei:

- PBKDF2-HMAC-SHA256 mit 310.000 Iterationen, 16-Byte-Salt;
- AES-256-GCM mit 12-Byte-Nonce, 128-Bit-Tag und Format-AAD;
- 12 bis 128 Unicode-Codepoints Passphrasenlänge;
- maximal 16 MiB Datei beziehungsweise 12 MiB Klartext;
- strikte Schema-, Größen-, UUID-, Datums-, Bereichs- und
  Überschneidungsvalidierung vor dem Schreiben;
- Passphrase und temporäre Klartext-/Schlüsselpuffer werden bestmöglich
  überschrieben und nie gespeichert.

Der Import ersetzt Solodaten in einer Room-Transaktion und schreibt dabei ein
Import-Journal. Einstellungen werden danach wiederhergestellt; bleibt der
Prozess zwischen Datenbankcommit und Einstellungscommit stehen, wird das
Journal beim nächsten Start idempotent vollendet. Room und DataStore besitzen
keine gemeinsame Betriebssystemtransaktion, weshalb das Journal die bewusst
implementierte Recovery-Grenze bildet. Ein importiertes Backup sollte auf dem
neuen Gerät trotzdem inhaltlich geprüft werden.

Der Android Storage Access Framework erlaubt die bewusste Auswahl eines
Dateianbieters wie Google Drive oder Nextcloud. Lunara integriert dabei kein
Google-SDK, kennt keine Cloud-Anmeldedaten und führt keinen automatischen
Upload oder Hintergrundsync aus. Der gewählte Anbieter bleibt eine eigene
Vertrauens- und Datenschutzgrenze.

Beim Logout oder bestätigten Serverwechsel werden Room-Tabellen,
Mutation-Queue, lokale Caches, DataStore, Cookies, Tokens, Benachrichtigungen
und zugehörige Keystore-Aliase gelöscht. Nicht synchronisierte Daten gehen
dabei absichtlich verloren; die Oberfläche warnt davor.

## PWA

Die PWA speichert Offline-Daten und Sync-Mutationen in origin-gebundenem
IndexedDB. Session-Tokens werden mit einem nicht exportierbaren Web-Crypto-
Schlüssel und AES-GCM gekapselt. Der lokale PWA-Solomodus versiegelt Profil,
Perioden und Tageslogs zusätzlich als AES-256-GCM-Vault mit einem aus der
Solo-Passphrase und 600.000 PBKDF2-HMAC-SHA256-Iterationen abgeleiteten
Schlüssel. Schlüssel und Passphrase werden nicht persistiert; nach einem
Seitenneustart muss der Vault erneut entsperrt werden. Legacy-Klartext-
Soloeinträge werden in derselben IndexedDB-Transaktion entfernt.

Kontoidentitätswechsel, Logout und 401 bereinigen kontogebundene Caches und
verhindern, dass Mutationen eines Kontos unter einem anderen wiederholt
werden.

Der Service Worker behandelt `/api`, `/health`, `/ready` und `/api-docs` weder
als Navigation-Fallback noch als cachefähige App-Ressource. API-Anfragen sind
same-origin, `no-store` und verweigern Redirects.

Im Self-hosted PWA-Modus können synchronisierte Serverdaten als normaler
origin-gebundener Offlinecache in IndexedDB liegen; dieser Cache ist nicht wie
der Solo-Vault zusätzlich passphrasenverschlüsselt. Er vertraut auf
Browserprofil, Gerätesperre und Betriebssystemspeicher. Ein gemeinsam genutztes
oder kompromittiertes Browserprofil liegt außerhalb der Schutzgrenze. Für
besonders sensible Servernutzung ist die native Android-App mit SQLCipher
vorzuziehen.

Screenshot-Schutz und Browserbenachrichtigungen sind in der PWA nicht
ausführbar. Aus einem portablen Backup importierte Privacy-/Notification-
Vorgaben bleiben im verschlüsselten Vault erhalten, werden aber ausdrücklich
als im Browser nicht anwendbar angezeigt. Der Vault sperrt bei versteckter
Seite, `pagehide` und spätestens nach 15 Minuten Inaktivität. Ein neuer PWA-
Prozess besitzt den nicht persistierten Schlüssel nicht und verlangt die
Passphrase erneut; ohne sie gibt es keine Wiederherstellung.

## Logs, Benachrichtigungen und Backups

Passwörter, Token, Gateway-Header, Notizinhalte und Gesundheitswerte werden
nicht protokolliert. Audit-Ereignisse enthalten nur sicherheitsrelevante
Aktionen ohne Gesundheitswerte.

Android-Benachrichtigungen sind standardmäßig aus, lokal geplant und auf dem
Sperrbildschirm standardmäßig neutral. Partnerhinweise entstehen nur aus einer
aktuellen serverseitig gefilterten Partnerprojektion und fallen ohne aktuelle
Antwort geschlossen aus. Android kann WorkManager aus Energiespargründen
verzögern; Erinnerungen sind kein exakter Wecker.

Serverbackups sind Gesundheitsdaten. `scripts/backup.sh` nimmt Secrets nicht
auf. Dump und ursprünglichen `APP_MASTER_KEY` getrennt verschlüsselt sichern,
Dateirechte `0600` und Verzeichnisrechte `0700` verwenden und Restore nur in
einer isolierten Instanz testen.

## Verlorenes Gerät

1. Betroffene oder alle Lunara-Sitzungen widerrufen.
2. Lunara-Passwort ändern, wenn dessen Kenntnis möglich ist.
3. OIDC-Sitzung in Authentik widerrufen und dort Passwort/MFA prüfen.
4. Offene Einladungen widerrufen.
5. Betriebssystem-Remote-Wipe verwenden.
6. Sicherheits-Audit prüfen; es enthält keine Gesundheitswerte.

Ein Solo-Gerät besitzt keine Serversitzung. Ohne vorher exportiertes,
lesbares Backup können seine Daten nach Verlust oder Defekt nicht
wiederhergestellt werden.

## Bekannte Grenzen von 1.1.0

- Android-Instrumentierung wurde kompiliert, aber mangels Emulator/realem
  Gerät nicht ausgeführt. Keystore-, SQLCipher-, WorkManager-, App-Link- und
  Accessibility-Verhalten benötigen deshalb zusätzlich reale Gerätetests.
- Der PWA-Solo-Vault ist passphrasenverschlüsselt; der Offlinecache des
  Self-hosted PWA-Modus ist dagegen nicht zusätzlich anwendungsverschlüsselt.
- Biometrische App-Sperre, ntfy, Widget und ein Android-SSE-Client sind nicht
  fertig implementiert.
- Ein Authentik-Provisioner für beliebige Authentik-Versionen und frei
  abweichende Bestandskonfigurationen existiert nicht; das Skript ist auf den
  dokumentierten Vertrag gepinnt.
- Die öffentliche Metaantwort und `assetlinks.json` sind beabsichtigte
  Ausnahmen. Sie dürfen keine Konto- oder Gesundheitsdaten enthalten.
- Rate-Limits liegen beim einzelnen API-Prozess im Speicher. Für horizontale
  Skalierung ist ein gemeinsamer dauerhafter Limiter erforderlich.
- Check-in-Inhalte sind in PostgreSQL nicht zusätzlich mit dem Master-Key
  verschlüsselt und sollten keine ausführlichen Gesundheitsfreitexte enthalten.
- Das Notizformat authentifiziert Ciphertext, bindet ihn in Version 1 aber
  nicht per AAD an Benutzer- und Datensatz-ID.
- Das fruchtbare Fenster ist nur eine grobe Opt-in-Schätzung ab ausreichenden
  Daten, bestätigt keine Ovulation und ist weder Diagnose noch Verhütung.
- Wartungs- und Adminskripte delegieren an den modusbewussten
  `scripts/compose.sh`-Wrapper. Ein Restore bleibt trotzdem eine destruktive
  Operation und benötigt den dokumentierten expliziten Bestätigungsschalter.

## Schwachstellen melden

Keine Live-Konfigurationen, Gesundheitsdaten, Token, Keystores oder Exploit-
Secrets in öffentliche Issues schreiben. Sicherheitsmeldungen mit redigierten
reproduzierbaren Schritten über einen privaten Kanal des Eigentümers senden.
