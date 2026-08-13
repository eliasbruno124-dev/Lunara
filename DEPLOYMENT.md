# Deployment

Diese Anleitung richtet sich an Debian 12 mit Docker Engine, Compose Plugin und
Traefik 3. Auf dem aktuellen Zielserver übernimmt Pangolin die dynamische
Traefik-Konfiguration; der Portainer-Hauptstack 20 bleibt unangetastet.

## 1. Verzeichnisse und Geheimnisse

Empfohlener Live-Pfad ist `/docker/lunara`. Die echte `.env` muss `root:root`
gehören und Modus `0600` besitzen. Datenbankdaten liegen im benannten Volume
`lunara_postgres_data` und werden nicht nach Git oder OneDrive kopiert.

```bash
install -d -m 0750 /docker/lunara
cd /docker/lunara
./scripts/generate-secrets.sh /docker/lunara/.env
chmod 0600 /docker/lunara/.env
```

Der Generator zeigt keine Secret-Werte an. `APP_MASTER_KEY` muss über die
gesamte Lebensdauer der verschlüsselten Daten unverändert bleiben und getrennt
in einem verschlüsselten Offline-Passwortspeicher gesichert werden. Für die
Infrastrukturhelfer werden `curl`, `jq`, `python3` und `sqlite3` benötigt.

## 2. Öffentliche Adresse und Netze

In `.env` konfigurieren:

```dotenv
PUBLIC_BASE_URL=https://lunara.3li361.de
PUBLIC_HOST=lunara.3li361.de
PROXY_NETWORK=pangolin
```

Die Werte bleiben auf anderen Installationen frei konfigurierbar. Das externe
Netz kann mit `docker network inspect pangolin` geprüft und auf einem neuen
System einmalig mit `docker network create pangolin` angelegt werden. Die
Datenbank hängt ausschließlich an `lunara_internal`; weder PostgreSQL noch API
veröffentlichen einen Host-Port.

## 3. Konfiguration prüfen und starten

```bash
./scripts/secret-scan.sh
docker compose --env-file .env config --quiet
docker compose --env-file .env build api
docker compose --env-file .env up -d
docker compose --env-file .env ps --all
docker compose --env-file .env logs --tail=100 migrate api
docker compose --env-file .env exec -T api \
  node -e "fetch('http://127.0.0.1:3000/ready').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
```

Beim ersten Start wartet `api` auf PostgreSQL und den einmaligen,
nicht-privilegierten `migrate`-Dienst. Ein beendeter Migrator mit Exitcode 0 ist
erwartet. `/health` prüft den Prozess, `/ready` zusätzlich PostgreSQL.
`REGISTRATION_ENABLED=false` ist der sichere Standard. Soll eine Instanz
mehrere getrennte Paare aufnehmen, aktiviert der Betreiber nach dem ersten
Bootstrap bewusst `REGISTRATION_ENABLED=true`; Partnerkonten entstehen
weiterhin ausschließlich über Einladungen.

## 4. Authentik: vollständiger Domain-Schutz

Authentik ist der einzige öffentliche Proxy. Es gibt keine zweite App- oder
Header-Auth-Domain und keinen direkten API-Port. Der Proxy schützt ohne
Pfadausnahme die gesamte Domain, insbesondere:

- `/api/v1/meta`, Setup, Registrierung, Login, Refresh und Sync
- `/health` und `/ready`
- `/api-docs` und `/api-docs-json`
- alle übrigen Browser- und API-Pfade

Der Sollzustand besitzt genau einen neutralen technischen Authentik-Benutzer
`lunara-native-app` mit genau einem nicht ablaufenden App-Passwort. Nur diese
Identität ist Mitglied der Lunara-Gateway-Gruppe. Persönliche beziehungsweise
menschliche Authentik-Konten erhalten keinen Lunara-Zugriff. Der Provider hat
einen leeren `skip_path_regex`; eine Ausnahme für `/api/v1` darf nicht
konfiguriert sein.

Prüfe zunächst die bestehenden Ziele read-only:

```bash
docker inspect authentik-server postgresql >/dev/null
test -f /docker/pangolin/config/db/db.sqlite
docker network inspect pangolin >/dev/null
```

Das Konfigurationsskript erkennt den exakten Alt- oder Sollzustand. Vor einer
Änderung erzeugt es einen validierten Authentik-PostgreSQL-Dump unter
`/var/backups/lunara/infrastructure`. Alte technische A/B-Zugänge werden bei
der Migration ungültig; alte lokale Secret-Dateien wandern in ein datiertes,
auf `0700` begrenztes Archiv. Menschliche Authentik-Konten selbst werden nicht
gelöscht, sind danach aber nicht mehr für Lunara gebunden.

```bash
sudo LUNARA_RUNTIME_DIR=/docker/lunara \
  LUNARA_PUBLIC_HOST=lunara.3li361.de \
  AUTHENTIK_CONTAINER=authentik-server \
  AUTHENTIK_DB_CONTAINER=postgresql \
  /docker/lunara/scripts/configure-authentik.sh --check
```

Nur wenn der Check ohne Konflikt endet, mit denselben Variablen schreiben:

```bash
sudo LUNARA_RUNTIME_DIR=/docker/lunara \
  LUNARA_PUBLIC_HOST=lunara.3li361.de \
  AUTHENTIK_CONTAINER=authentik-server \
  AUTHENTIK_DB_CONTAINER=postgresql \
  /docker/lunara/scripts/configure-authentik.sh
```

Erwartete Providerwerte:

- Modus: Proxy
- External host: `https://lunara.3li361.de`
- Internal host: `http://lunara-api:3000`
- Skip path regex: leer
- Intercept header authentication: aktiviert
- Anwendung/Gruppe: ausschließlich der technische native App-Zugang
- Outpost: vorhandener eingebetteter Authentik-Outpost

Das Skript schreibt die einzige Header-Auth-Adresse und eine maschinenlesbare
Credential-Datei ausschließlich serverlokal, jeweils Modus `0600`. Die Werte
sind Passwörter: niemals ausgeben, committen, in Tickets kopieren oder in
Cloudflare hinterlegen. Der Gateway-Test liest die URL lokal und hält sie aus
Prozessargumenten, temporären Dateien und Standardausgabe heraus:

```bash
sudo /docker/lunara/scripts/configure-authentik.sh --check
sudo /docker/lunara/scripts/verify-gateway.sh
```

`verify-gateway.sh` bestätigt sowohl die authentifizierte Meta- und OpenAPI-
Antwort als auch die Authentik-Sperre für anonyme Zugriffe auf Root, Health,
OpenAPI, Meta und Login. Eine anonyme `200`-Antwort auf einen dieser Pfade ist
ein Fehler.

## 5. Pangolin und Cloudflare

Die Pangolin-Ressource leitet ausschließlich zum eingebetteten Authentik-
Outpost weiter. Bei abweichender Installation müssen Datenbank-, Domain-,
Site- und Organisations-ID bewusst angepasst werden:

```bash
sudo LUNARA_PUBLIC_HOST=lunara.3li361.de \
  PANGOLIN_DB=/docker/pangolin/config/db/db.sqlite \
  PANGOLIN_DOMAIN_ID=domain1 \
  PANGOLIN_SITE_ID=1 \
  PANGOLIN_ORG_ID=mannheim \
  /docker/lunara/scripts/configure-pangolin.sh --check
```

Erst danach mit denselben Werten ohne `--check` schreiben. Das Skript prüft die
verifizierte Domain und aktive Site, verweigert abweichende vorhandene Ziele
und legt vor der SQLite-Transaktion ein konsistentes Backup an.

Erwartete Ressource:

- Name: `Lunara`
- Domain: `lunara.3li361.de`
- Ziel: `http://authentik-server:9000`
- TLS: Let's Encrypt über den vorhandenen Resolver
- Pangolin-eigene Basic Auth: deaktiviert; Authentik übernimmt den Schutz

Die Compose-Labels von Lunara sind absichtlich deaktiviert. Es darf keinen
zweiten Traefik-Router geben, der direkt auf `lunara-api:3000` zeigt.

In Cloudflare wird nur DNS konfiguriert:

1. In der Zone `3li361.de` einen CNAME `lunara` anlegen.
2. Ziel auf den bestehenden öffentlichen Pangolin-/Traefik-Eingang setzen.
3. Zunächst entsprechend der Hauptdomain „DNS only“ verwenden.
4. DNS, Zertifikat und anschließend `verify-gateway.sh` prüfen.

Zugangsdaten gehören weder in DNS-Werte noch Cloudflare-Regeln.

## 6. Android-App mit Header Auth verbinden

Das Serverfeld ist in Android 1.0.5 beim Erststart leer. Der Betreiber
übermittelt die vom Authentik-Skript erzeugte Header-Auth-Adresse einmalig über
einen sicheren Kanal. Ihr allgemeines Schema ist HTTPS, gefolgt von Gateway-
Benutzer, Doppelpunkt, App-Passwort, `@` und Server-Host. Die echten Bestandteile
stehen ausschließlich in der serverlokalen `0600`-Datei.

Sonderzeichen müssen URL-kodiert sein. Eingebettete Zugangsdaten werden nur
über HTTPS akzeptiert. Die App trennt sie nach dem erfolgreichen Verbindungstest
sofort von der Serveradresse und speichert sie Keystore-gestützt. Danach zeigt
und speichert sie als Basisadresse nur:

```text
https://<server-host>
```

Die Header haben getrennte Aufgaben:

1. `Authorization: Basic …` enthält den gemeinsamen technischen Authentik-
   App-Zugang und öffnet ausschließlich den äußeren Proxy.
2. `X-Lunara-Authorization: Bearer …` enthält nach persönlichem Login das kurze
   Lunara-Access-Token. Die API prüft weiterhin Konto, Rolle, Eigentum,
   Freigaben und interne Beziehungsgrenze.

Die App sendet den Basic Header präemptiv nur an exakt denselben HTTPS-Ursprung
und folgt keinen Browser-Login-Weiterleitungen. Die gespeicherte Basisadresse,
Server-QRs und Partner-Einladungs-QRs enthalten keine Zugangsdaten. Insbesondere
darf die einmalige Header-Auth-Adresse nicht in die Zwischenablagehistorie,
Screenshots, Chat, Git oder Dokumentation gelangen.

Der technische App-Zugang ist bewusst gemeinsam und nicht ablaufend. Deshalb
gilt eine Rotation für alle Geräte. Es gibt derzeit keinen automatischen
`--rotate`-Schalter: Das App-Passwort muss kontrolliert und manuell in Authentik
ersetzt, die lokale `0600`-Credential-Datei neu erzeugt und die neue Adresse
allen berechtigten Installationen einmalig sicher bereitgestellt werden. Erst
nach erfolgreicher Neuausrollung alte lokale Kopien sicher archivieren.
Persönliche Lunara-Passwörter und Gerätesitzungen sind davon unabhängig.

## 7. Bootstrap, Registrierung und Partnerkonto

1. App öffnen und die einmalige HTTPS-Header-Auth-Adresse eingeben.
2. Auf einer noch nie eingerichteten Instanz meldet die authentifizierte
   `/api/v1/meta`-Antwort `setupRequired: true`.
3. `BOOTSTRAP_TOKEN` lokal aus `.env` verwenden und das erste Konto als
   `TRACKER` anlegen. Es gibt kein sichtbares Haushaltsfeld.
4. Danach muss `setupRequired` dauerhaft `false` sein.
5. Für mehrere Paare `REGISTRATION_ENABLED=true` setzen. Weitere Personen
   starten dann jeweils eine isolierte Beziehung mit einem `TRACKER`-Konto.
6. Tracker erzeugt eine permanente, einmal verwendbare und widerrufbare
   Partner-Einladung. Ein neuer Code widerruft offene ältere Codes derselben
   Beziehung.
7. Die Partnerperson legt mit Einladungscode, eigenem Benutzernamen und eigenem
   Passwort ein `PARTNER`-Konto an.
8. `BOOTSTRAP_TOKEN` nach erfolgreichem Setup aus der Live-Konfiguration
   entfernen.

Für Bootstrap, öffentliche Tracker-Registrierung, Partnerbeitritt und
Passwortänderung gilt dieselbe Kontopasswort-Regel: 8 bis 128 Unicode-Zeichen,
ohne verpflichtende Großbuchstaben, Ziffern oder Sonderzeichen. Eine lange,
einzigartige Passphrase ist dennoch vorzuziehen. Die Prüfung eines bereits
existierenden Passworts bei Login, Kontolöschung und Passwortänderung lässt 1
bis 128 Zeichen zu, damit ältere Konten nach einer Richtlinienänderung nicht
ausgesperrt werden. Diese Kompatibilitätsregel senkt nicht die Mindestlänge für
neu gesetzte Passwörter. Passwortgeschützte Exporte verwenden eine getrennte
Passphrase mit weiterhin 12 bis 128 Zeichen.

Der Bootstrap-Token ist nur der Besitznachweis für eine frische Datenbank. Der
Abschluss liegt dauerhaft in `ServerState`; selbst die spätere Löschung aller
Konten öffnet Setup nicht erneut. Einladungscode und QR enthalten niemals
Server-, Authentik-, Lunara- oder Bootstrap-Passwörter.

## 8. Fertilität und Partner-Unterstützung einrichten

Die Fertilitätsschätzung ist standardmäßig deaktiviert und muss von der
trackenden Person ausdrücklich aktiviert werden. Sie wird erst mit mindestens
drei vollständigen Zyklen verfügbar. Die App bezeichnet den Zeitraum stets als
„geschätzt“, zeigt, dass er weder tatsächliche Ovulation bestätigt noch zur
Verhütung geeignet ist, und unterdrückt ihn in ungeeigneten Sondermodi.

Eine getrennte Freigabe entscheidet, ob der Partner das geschätzte Fenster
sieht. Deaktivieren der persönlichen Fertilitätsschätzung widerruft diese
Freigabe serverseitig. Partner-Tipps berücksichtigen ausschließlich aktuell
freigegebene Daten sowie die von der trackenden Person gepflegten Care
Preferences; nicht freigegebene Symptome dürfen auch indirekt nicht verraten
werden.

## 9. Backup, Restore und Updates

```bash
sudo LUNARA_ENV_FILE=/docker/lunara/.env /docker/lunara/scripts/backup.sh
sudo LUNARA_ENV_FILE=/docker/lunara/.env /docker/lunara/scripts/update.sh
```

Backups enthalten einen PostgreSQL-Custom-Dump und nicht-sensible
Konfiguration, aber keine Secrets. Ohne den ursprünglichen `APP_MASTER_KEY`
sind verschlüsselte Notizen nicht wiederherstellbar. Dump und Master-Key
getrennt verschlüsselt sichern.

Ein Restore ist destruktiv, erstellt zuerst ein eng benanntes Vorab-Backup und
verlangt einen expliziten Schalter. Er betrifft nur Lunara:

```bash
sudo LUNARA_ENV_FILE=/docker/lunara/.env \
  /docker/lunara/scripts/restore.sh \
  --confirm-replace-lunara-database \
  /var/backups/lunara/lunara-YYYYMMDDTHHMMSSZ.tar.gz
```

Anschließend `/ready`, relevante Logs und die Entschlüsselung einer bekannten
Testnotiz prüfen. Restore regelmäßig in einer isolierten Testinstanz erproben.

## 10. Migrationen und Rollback

Migrationen sind vorwärtsgerichtet. `scripts/update.sh` erstellt zuerst ein
Backup, baut das gemeinsame API-/Migrator-Image, führt den Migrator aus,
ersetzt nur die API und prüft Readiness. Migrationen müssen nach dem
Expand/Contract-Verfahren bis zum API-Austausch mit der Vorgängerversion
kompatibel bleiben.

`ServerState` hält den Bootstrap-Abschluss persistent. Der partielle
Datenbankconstraint erlaubt pro Benutzer und lokalem Startdatum höchstens einen
nicht gelöschten Periodenstart und bricht bei widersprüchlichem Altbestand ab,
statt Daten still zu verändern. Ein Restore muss den ServerState-Datensatz und
den passenden `APP_MASTER_KEY` gemeinsam erhalten.
