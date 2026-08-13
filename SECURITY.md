# Sicherheit

## Bedrohungsmodell

Lunara schützt sensible Zyklus-, Symptom-, Freitext-, Konto- und
Beziehungsdaten gegen andere interne Beziehungsmandanten, unberechtigte
Partneransichten, gestohlene Refresh-Tokens, verlorene Geräte und
versehentliche Offenlegung in Logs oder Backups. Der Serveradministrator und
der Debian-Host gelten als vertrauenswürdig. Gegen einen vollständig
kompromittierten Root-Host oder ein kompromittiertes Android-Betriebssystem
kann die Anwendung nicht schützen.

## Verteidigungsschichten

- TLS endet am Traefik/Pangolin-Eingang. Der API-Container besitzt keinen
  veröffentlichten Host-Port; der einzige öffentliche Router zeigt auf den
  Authentik-Proxy-Outpost.
- Authentik schützt die gesamte Domain ohne Pfadausnahme. Auch Meta, Setup,
  Login, Refresh, Sync, Health und OpenAPI verlangen den gemeinsamen
  technischen App-Zugang. Ein anonymer Aufruf wird vor Erreichen der API zum
  Authentik-Login umgeleitet.
- Es gibt genau eine neutrale Authentik-Serviceidentität mit genau einem nicht
  ablaufenden App-Passwort. Sie sendet außen `Authorization: Basic …` und ist
  die einzige Identität der Lunara-Gateway-Gruppe; menschliche Authentik-Konten
  besitzen keinen Lunara-Zugriff.
- Der äußere App-Zugang ersetzt keine persönliche Anmeldung. Nach dem Login
  trägt `X-Lunara-Authorization: Bearer …` das kurze Lunara-Access-Token;
  öffentliche Lunara-Endpunkte sind lediglich innerhalb der bereits
  authentifizierten Gateway-Schicht öffentlich. Alle geschützten Endpunkte
  prüfen zusätzlich Lunara-Token, Rolle und Eigentum.
- Lunara verwendet getrennte App-Konten, Argon2id-Passworthashes, kurze
  signierte Access-Tokens und rotierende, nur als Pepper-Hash gespeicherte
  Refresh-Tokens pro Gerät.
- Rollen, unsichtbare interne Beziehungsgrenze und Eigentümerschaft werden
  serverseitig geprüft. Pro Beziehung sind genau ein `TRACKER` und ein
  `PARTNER` vorgesehen. Partnerantworten entstehen aus der jeweils aktuellen
  Freigabekonfiguration.
- Freitextnotizen werden anwendungsseitig mit AES-256-GCM verschlüsselt.
- Android speichert die Room-Datenbank mit SQLCipher; Datenbankschlüssel,
  Token und das getrennt von der Basisadresse abgelegte Gateway-Credential
  liegen hinter einem nicht exportierbaren Android-Keystore-Schlüssel.

## Authentifizierung und Sitzungen

Der nicht ablaufende Authentik-App-Zugang ist ein gemeinsames Geräte-
Vorgeheimnis und identifiziert keine Person. Sein Verlust öffnet nur die äußere
Proxy-Schicht; Gesundheits- und Kontodaten benötigen weiterhin persönliche
Lunara-Anmeldedaten. Trotzdem ist er wie ein Passwort zu behandeln, weil er
Angriffsfläche bis zu Login und Rate Limits freigibt. Bei Verdacht wird das
App-Passwort zentral rotiert, das alte sofort ungültig gemacht und die neue
Header-Auth-Adresse allen berechtigten Installationen einmalig über einen
sicheren Kanal bereitgestellt. Die Rotation betrifft zwangsläufig alle Geräte.

Login ist rate-limitiert und liefert dieselbe Fehlermeldung für unbekannte
Benutzer und falsche Passwörter. Wiederverwendung eines bereits rotierten
Refresh-Tokens widerruft dessen Token-Familie. Sitzungen lassen sich einzeln
oder über die Admin-CLI vollständig widerrufen. Die API stellt die
Gerätesitzungen bereit; eine vollständige Geräteverwaltung ist in der
Android-Oberfläche noch nicht umgesetzt.

Für neue Konten und Passwortänderungen gelten 8 bis 128 Unicode-Zeichen. Es
werden keine bestimmten Zeichenklassen wie Großbuchstaben, Ziffern oder
Sonderzeichen erzwungen; die empfohlene Wahl ist eine lange, einzigartige
Passphrase. Die Verifikation eines bestehenden Passworts akzeptiert 1 bis 128
Zeichen, damit ein älteres Konto nach einer späteren Richtlinienänderung noch
für Login, Kontolöschung und Passwortwechsel verwendbar bleibt. Dieses
Kompatibilitätsfenster erlaubt kein neues Passwort unter acht Zeichen.
Argon2id, die Login- und Passwortwechsel-Rate-Limits, generische
Anmeldefehlermeldungen und der vorgeschaltete Authentik-Schutz bleiben
unverändert aktiv.

Bei aktivierter öffentlicher Tracker-Registrierung wird für jedes neue Konto
serverseitig eine eigene interne Beziehungs-/Mandantengrenze erzeugt. Diese ist
nicht als „Haushalt“ in der Oberfläche sichtbar. Registrierung vergibt nur die
`TRACKER`-Rolle; ein `PARTNER` kann ausschließlich mit einem einmal verwendbaren
und widerrufbaren Einladungscode beitreten. Permanente Codes besitzen kein
Ablaufdatum, sind deshalb bis zur Verwendung oder zum Widerruf wie
Bearer-Zugangsdaten zu schützen. Der Server speichert nur ihren Hash und gibt
den Rohcode genau einmal aus.

Der `BOOTSTRAP_TOKEN` autorisiert ausschließlich das erste Setup einer noch nie
eingerichteten Datenbank. Nach dem erfolgreichen Setup speichert der Server den
Abschluss dauerhaft in `ServerState`; auch das spätere Löschen aller Konten
öffnet Bootstrap nicht wieder. Der Token kann und soll danach aus der Live-
Konfiguration entfernt werden. Auf einer frischen Datenbank ohne konfigurierten
Token bleibt Setup fail-closed gesperrt. Die additive Migration markiert eine
bestehende Installation mit vorhandenen Konten automatisch als eingerichtet.

Der Passwortwechsel in den Android-Einstellungen verlangt das aktuelle
Lunara-Passwort, ist separat rate-limitiert und hasht das neue Passwort mit
Argon2id. Die aktuelle Gerätesitzung bleibt bestehen; alle anderen Lunara-
Serversitzungen werden atomar widerrufen. Bereits synchronisierte oder offline
gespeicherte Daten auf diesen Geräten können dadurch nicht aus der Ferne
gelöscht werden; bei Geräteverlust ist zusätzlich der Remote-Wipe des
Betriebssystems zu verwenden. Login, Refresh-Rotation und Passwortwechsel
werden pro Benutzer durch eine transaktionsgebundene PostgreSQL-Sperre
serialisiert; dadurch kann kein bereits mit dem alten Passwort geprüfter Login
nachträglich eine neue Sitzung anlegen. Erfolg sowie abgelehnte Autorisierungs-,
Gleichheits- und Race-Versuche werden ohne Passwort- oder Gesundheitsdaten
auditiert; reine DTO-Formatfehler erreichen den Auth-Service nicht.

Mutierende Offline-Sync-Aufrufe verwenden dauerhafte
`clientMutationId`-Werte, clientseitige UUIDs und serverseitige
Idempotenzspeicherung. Create-Endpunkte für Perioden, Tageslogs, eigene
Symptome und Tipps akzeptieren ebenfalls stabile Client-IDs. Der allgemeine
HTTP-Header `Idempotency-Key` ist für API-Kompatibilität zugelassen, wird in
Version 1 aber nicht für jeden einzelnen mutierenden REST-Endpoint
serverseitig persistiert. Solche Aufrufe dürfen daher nicht blind wiederholt
werden; die Android-Offlineschicht verwendet den Sync-Endpoint.

## Verschlüsselung und Schlüssel

`APP_MASTER_KEY` ist ein 32-Byte-Base64-Schlüssel und muss über die gesamte
Lebensdauer der verschlüsselten Daten unverändert verfügbar sein. Er liegt
während des Betriebs im API-Prozessspeicher. Root oder ein kompromittierter
Container-Host kann ihn auslesen. Ein Austausch erfordert eine geplante
Re-Encryption-Migration; niemals einfach einen neuen Wert eintragen.

Die Anwendungsverschlüsselung schützt Notizen in Datenbankdumps, nicht
strukturierte Metadaten wie Datum, Rolle oder Datensatzanzahl. Exportpasswörter
sind eine getrennte Verschlüsselungspassphrase, bleiben auf 12 bis 128 Zeichen
begrenzt, werden mit PBKDF2-SHA-256 abgeleitet und schützen den Export mit
AES-256-GCM. Die gelockerte Kontopasswort-Regel gilt für sie nicht.

Beim Logout oder bestätigten Serverwechsel führt Android den lokalen Wipe auch
dann vollständig aus, wenn die Serververbindung scheitert: Room-Tabellen,
Mutation-Queue, WAL/Cache, DataStore, Cookies, Tokens und der
alte Keystore-Alias werden entfernt. Nicht synchronisierte Daten gehen dabei
absichtlich verloren; die UI warnt vorher. Dieser Ablauf ist auf JVM-Ebene
getestet, aber noch nicht auf einem realen Android-Gerät instrumentiert worden.

## HTTP, Zertifikate und Serveradresse

Android 1.0.5 startet mit leerem Serverfeld. Die erste Eingabe darf einmalig
eine HTTPS-URL mit Userinfo enthalten: Gateway-Benutzer, Doppelpunkt und
App-Passwort stehen dabei vor `@` und dem Server-Host.
Sonderzeichen müssen URL-kodiert sein. Die App extrahiert das Credential,
speichert es Keystore-gestützt und entfernt Userinfo aus der sichtbaren und
dauerhaften Basisadresse. Ein Credential in einer HTTP-URL wird abgelehnt.

Der Production-Build erzwingt HTTPS und vertraut dem normalen Android-
Zertifikatsspeicher. Selbst signierte Zertifikate werden nicht umgangen; die
unsichere HTTP-Option existiert nur in der separaten `local`-Variante. Basic
Auth wird präemptiv ausschließlich an exakt denselben Ursprung aus Schema,
Host und Port gesendet. Redirects zu Browser-Login oder anderen Ursprüngen
werden nicht verfolgt.

Die einmalige Userinfo-URL selbst kann trotz der Trennung in einer
Zwischenablage-, Tastatur- oder Screenshot-Historie des Betriebssystems landen.
Sie soll deshalb nicht kopiert oder geteilt, sondern über einen kontrollierten
Kanal direkt eingegeben und anschließend aus der Quelle entfernt werden.
Server- und Partner-QR-Codes enthalten keine Gateway-Zugangsdaten. Passwörter,
Refresh-Tokens, Einladungscodes und Bootstrap-Token gehören nie in Chat, Git,
Dokumentation oder Cloudflare-Konfiguration.

Helmet, Body-Limit, DTO-Validierung und restriktives CORS sind aktiv. Tokens,
Passwörter, Header, Freitextnotizen und Gesundheitswerte werden nicht
protokolliert. Der API-Container läuft als UID 10001, ohne Capabilities, mit
read-only Root-Dateisystem und `no-new-privileges`.

## Verlorenes Gerät

1. Über ein verbleibendes Konto oder die Admin-CLI die betroffene bzw. alle
   Lunara-Sitzungen widerrufen.
2. Lunara-Kontopasswort ändern, wenn seine Kenntnis möglich ist.
3. Offene Partner-Einladungen widerrufen, wenn deren Code auf dem Gerät lag.
4. Betriebssystem-Sperre bzw. Remote-Wipe verwenden.
5. Sicherheits-Audit-Ereignisse prüfen; sie enthalten keine Gesundheitswerte.

Ein berechtigter Partner kann bereits angezeigte Daten fotografieren oder
außerhalb der App speichern. Ein späterer Freigabeentzug löscht solche externen
Kopien nicht.

## Backups

Backups sind Gesundheitsdaten. `scripts/backup.sh` nimmt echte Secrets bewusst
nicht auf. Ohne den ursprünglichen `APP_MASTER_KEY` sind verschlüsselte Notizen
im Dump nicht wiederherstellbar. Dump und Master-Key getrennt verschlüsselt
sichern, Verzeichnisrechte `0700`/Dateirechte `0600` verwenden und Restore nur
in einer isolierten Instanz testen.

## Bekannte Grenzen

- App-Sperre per Biometrie, Benachrichtigungen/ntfy und Zertifikat-Pinning sind
  nicht implementiert. Screenshot-Schutz via `FLAG_SECURE` ist vorhanden.
- Server-Sent Events existieren serverseitig, werden von Android Version 1 aber
  noch nicht konsumiert. Die App synchronisiert nach Anmeldung und Änderungen,
  beim Öffnen, im Vordergrund höchstens einmal pro Minute sowie sparsam per
  WorkManager im Hintergrund; der manuelle Knopf bleibt als Zusatz vorhanden.
- Check-in-Prompt und -Antwort sind in Version 1 in PostgreSQL nicht zusätzlich
  anwendungsseitig verschlüsselt. Sie unterliegen Datenbank-/Backup-Schutz und
  strikter Beziehungsautorisierung, sollten aber nicht für ausführliche sensible
  Freitexte verwendet werden.
- Der SSE-Änderungsstrom wird nur von SyncChange-Datensätzen gespeist;
  Check-in-, Freigabe- und Profiländerungen werden daher nicht für alle Clients
  sofort als eigenes Echtzeitereignis signalisiert.
- Ein Partner-Sync kann erkennen, dass sich ein Cursor verändert hat, auch wenn
  der konkrete Gesundheitsdatensatz wegen Freigaben nicht übertragen wird.
- Das vollständige npm-Entwicklungsabhängigkeits-Audit enthält derzeit eine
  moderate transitive `ajv`-Meldung aus Lint-/CLI-Werkzeugen. Der geprüfte
  Produktionsabhängigkeitsbaum enthält keine bekannte Meldung.
- Authentik-geschützte OpenAPI-Dokumentation vertraut auf den vom Outpost
  gesetzten Authentik-Header. Jeder Dienst mit Zugriff auf das gemeinsame
  Proxy-Netz ist deshalb Teil der Vertrauensgrenze; das Netz darf nur
  administrativ kontrollierte Container enthalten.
- Der gemeinsame, nicht ablaufende Authentik-App-Zugang ist ein zentraler
  Schlüssel für die äußere Proxy-Schicht. Eine Rotation sperrt alle
  Installationen, bis die neue einmalige Header-Auth-Adresse eingegeben wurde.
  Android Keystore reduziert die lokale Extraktion, schützt aber nicht gegen
  ein vollständig kompromittiertes oder entsperrtes Betriebssystem.
- Die Proxy-IP-/Rate-Limit-Auswertung nutzt eine feste Trust-Proxy-Hopzahl. Nach
  Änderungen an Pangolin/Authentik muss geprüft werden, dass der erwartete
  Client-IP-Hop weiterhin stimmt und kein direkt erreichbarer Peer Header
  vortäuschen kann.
- Die optionale öffentliche Tracker-Registrierung ist mit drei Versuchen pro
  Minute und Client-IP begrenzt, verwendet in Version 1 aber den flüchtigen
  In-Memory-Throttler des einzelnen API-Prozesses. Ein Neustart setzt Zähler
  zurück; mehrere API-Replikate würden keine gemeinsamen Zähler führen. Der
  produktive Heimserver betreibt deshalb genau eine API-Instanz. Öffentliche
  Registrierung kann CPU- und Datenbankmissbrauch reduzieren, aber nicht
  vollständig verhindern; bei Missbrauch `REGISTRATION_ENABLED=false` setzen
  und vor horizontaler Skalierung einen dauerhaften gemeinsamen Limiter
  ergänzen.
- Check-in-Erzeugung und der passwortgeschützte Export besitzen noch keine
  datenbankseitige Einzigkeitsgarantie bzw. ausgelagerten KDF-Worker; die
  vorhandenen API-Limits reduzieren, beseitigen diese Grenzen aber nicht.
- Das AES-GCM-Notizformat authentifiziert den Ciphertext, bindet ihn in Version
  1 jedoch nicht mit AAD an Benutzer-/Datensatz-ID.
- Das optionale fruchtbare Fenster wird nur nach ausdrücklichem Opt-in und mit
  mindestens drei vollständigen Zyklen berechnet. Es bleibt eine grobe
  Schätzung, bestätigt keine Ovulation und ist weder Diagnose noch
  Verhütungsmethode.

## Schwachstellen melden

Keine Live-Konfigurationen, Gesundheitsdaten oder Exploit-Secrets in öffentliche
Issues schreiben. Das Repository ist privat; Meldungen über einen privaten Kanal
des Eigentümers mit redigierten reproduzierbaren Schritten einreichen.
