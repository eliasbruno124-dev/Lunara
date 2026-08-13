# Sicherheit

## Bedrohungsmodell

Lunara schützt sensible Zyklus-, Symptom-, Freitext-, Konto- und
Beziehungsdaten gegen fremde Haushalte, unberechtigte Partneransichten,
gestohlene Refresh-Tokens, verlorene Geräte und versehentliche Offenlegung in
Logs oder Backups. Der Serveradministrator und der Debian-Host gelten als
vertrauenswürdig. Gegen einen vollständig kompromittierten Root-Host oder ein
kompromittiertes Android-Betriebssystem kann die Anwendung nicht schützen.

## Verteidigungsschichten

- TLS endet am Traefik/Pangolin-Eingang. Der API-Container besitzt keinen
  veröffentlichten Host-Port; der einzige öffentliche Router zeigt auf den
  Authentik-Proxy-Outpost.
- Authentik-Header-Authentifizierung verwendet pro Person ein eigenes,
  widerrufbares App-Passwort. Sie ist eine zusätzliche Gateway-Schranke, keine
  Ersatzanmeldung und keine Mehrfaktorprüfung.
- Lunara verwendet getrennte App-Konten, Argon2id-Passworthashes, kurze
  signierte Access-Tokens und rotierende, nur als Pepper-Hash gespeicherte
  Refresh-Tokens pro Gerät.
- Rollen, Haushalt und Eigentümerschaft werden serverseitig geprüft. Partner-
  Antworten entstehen aus der jeweils aktuellen Freigabekonfiguration.
- Freitextnotizen werden anwendungsseitig mit AES-256-GCM verschlüsselt.
- Android speichert die Room-Datenbank mit SQLCipher; Datenbankschlüssel,
  Tokens, Cookies und optionale Gateway-Zugangsdaten liegen hinter einem
  nicht exportierbaren Android-Keystore-Schlüssel.

## Authentifizierung und Sitzungen

Login ist rate-limitiert und liefert dieselbe Fehlermeldung für unbekannte
Benutzer und falsche Passwörter. Wiederverwendung eines bereits rotierten
Refresh-Tokens widerruft dessen Token-Familie. Sitzungen lassen sich einzeln
oder über die Admin-CLI vollständig widerrufen. Die API stellt die
Gerätesitzungen bereit; eine vollständige Geräteverwaltung ist in der
Android-Oberfläche noch nicht umgesetzt.

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
auditiert; reine DTO-Formatfehler erreichen den Auth-Service nicht. Das
getrennte Authentik-App-Passwort wird dabei nicht verändert.

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
werden mit PBKDF2-SHA-256 abgeleitet und der Export mit AES-256-GCM geschützt.

Beim Logout oder bestätigten Serverwechsel führt Android den lokalen Wipe auch
dann vollständig aus, wenn die Serververbindung scheitert: Room-Tabellen,
Mutation-Queue, WAL/Cache, DataStore, Cookies, Token-/Gateway-Secrets und der
alte Keystore-Alias werden entfernt. Nicht synchronisierte Daten gehen dabei
absichtlich verloren; die UI warnt vorher. Dieser Ablauf ist auf JVM-Ebene
getestet, aber noch nicht auf einem realen Android-Gerät instrumentiert worden.

## HTTP, Zertifikate und URL-Zugangsdaten

Der ausgelieferte Produktions-Debug-Build erzwingt HTTPS und vertraut nur dem
normalen Android-Zertifikatsspeicher. Selbst signierte Zertifikate werden nicht
umgangen; die unsichere HTTP-Option existiert ausschließlich in der separaten
`local`-Entwicklungsvariante.

Eine Setup-URL darf temporär URL-Benutzerinfo in der Form
`user:app-passwort@host` hinter dem `https://`-Schema enthalten. Die App trennt
die Userinfo sofort von der Server-URL und speichert sie
Keystore-geschützt. Vor diesem Zeitpunkt können Zwischenablage, Tastatur oder
Bildschirmaufzeichnung die Eingabe sehen. Deshalb App-Passwörter nie per Chat
versenden, nicht in QR-Codes einbetten und bei Verdacht in Authentik widerrufen.

Helmet, Body-Limit, DTO-Validierung und restriktives CORS sind aktiv. Tokens,
Passwörter, Header, Freitextnotizen und Gesundheitswerte werden nicht
protokolliert. Der API-Container läuft als UID 10001, ohne Capabilities, mit
read-only Root-Dateisystem und `no-new-privileges`.

## Verlorenes Gerät

1. Über ein verbleibendes Konto oder die Admin-CLI die betroffene bzw. alle
   Lunara-Sitzungen widerrufen.
2. Das Authentik-App-Passwort dieses Geräts/Kontos widerrufen und neu erzeugen.
3. Lunara-Kontopasswort ändern, wenn seine Kenntnis möglich ist.
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
  strikter Haushalt-Autorisierung, sollten aber nicht für ausführliche sensible
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
- Die Proxy-IP-/Rate-Limit-Auswertung nutzt eine feste Trust-Proxy-Hopzahl. Nach
  Änderungen an Pangolin/Authentik muss geprüft werden, dass der erwartete
  Client-IP-Hop weiterhin stimmt und kein direkt erreichbarer Peer Header
  vortäuschen kann.
- Check-in-Erzeugung und der passwortgeschützte Export besitzen noch keine
  datenbankseitige Einzigkeitsgarantie bzw. ausgelagerten KDF-Worker; die
  vorhandenen API-Limits reduzieren, beseitigen diese Grenzen aber nicht.
- Das AES-GCM-Notizformat authentifiziert den Ciphertext, bindet ihn in Version
  1 jedoch nicht mit AAD an Benutzer-/Datensatz-ID.
- Prognosen sind Schätzungen, keine Diagnosen, keine Ovulationsfeststellung und
  keine Verhütungsmethode.

## Schwachstellen melden

Keine Live-Konfigurationen, Gesundheitsdaten oder Exploit-Secrets in öffentliche
Issues schreiben. Das Repository ist privat; Meldungen über einen privaten Kanal
des Eigentümers mit redigierten reproduzierbaren Schritten einreichen.
