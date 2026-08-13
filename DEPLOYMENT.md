# Deployment

Diese Anleitung richtet sich an Debian 12 mit Docker Engine, Compose Plugin und
Traefik 3. Auf dem aktuellen Zielserver übernimmt Pangolin die dynamische
Traefik-Konfiguration; Stack 20 bleibt vollständig unangetastet.

## 1. Verzeichnisse und Dateirechte

Empfohlener Live-Pfad ist `/docker/lunara`. Klone oder kopiere nur die geprüften
Projektdateien dorthin. Die echte `.env` muss `root:root` gehören und Modus
`0600` haben. Datenbankdaten liegen im benannten Volume
`lunara_postgres_data`; sie werden nicht in OneDrive oder Git kopiert.

```bash
install -d -m 0750 /docker/lunara
cd /docker/lunara
./scripts/generate-secrets.sh /docker/lunara/.env
chmod 0600 /docker/lunara/.env
```

Die Ausgabe des Secret-Generators zeigt keine Werte an. Sichere den
`APP_MASTER_KEY` separat in einem verschlüsselten Offline-Passwortspeicher. Er
muss über die gesamte Lebensdauer der verschlüsselten Daten unverändert bleiben.
Für die Infrastrukturhelfer werden auf dem Host außerdem `curl`, `jq` und
`sqlite3` benötigt. Der optionale Pre-DNS-Test benötigt zusätzlich `python3`
zur kanonischen IP-Prüfung. Die Skripte geben keine Zugangsdaten aus.

## 2. Öffentliche Adresse und Netz

Setze in `.env`:

```dotenv
PUBLIC_BASE_URL=https://lunara.3li361.de
PUBLIC_HOST=lunara.3li361.de
PROXY_NETWORK=pangolin
```

Das bestehende externe Netz kann read-only mit `docker network inspect pangolin`
geprüft werden. Auf einem neuen System wird es einmalig mit
`docker network create pangolin` angelegt. Die Datenbank hängt ausschließlich am
internen Netz `lunara_internal`; es gibt keinen veröffentlichten DB-Port.

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

Der eng erlaubte `.dockerignore`-Kontext enthält nur die für das API-Image
benötigten Workspace-Manifeste, API-Quellen und Prisma-Dateien. Echte `.env`-,
Git-, Android-, Backup- und Laufzeitdaten gelangen nicht zum Docker-Builder.

Beim ersten `up` startet Compose PostgreSQL, führt danach den einmaligen,
nicht-privilegierten Dienst `migrate` aus und startet `api` nur, wenn die
Migration erfolgreich beendet wurde. Das API-Image selbst verändert beim Start
kein Schema. `/health` prüft den Prozess; `/ready` prüft zusätzlich PostgreSQL.
`REGISTRATION_ENABLED=false` ist der sichere Standard. Soll dieselbe Instanz
mehrere voneinander getrennte Paare aufnehmen, muss der Betreiber nach dem
ersten Bootstrap bewusst `REGISTRATION_ENABLED=true` in `.env` setzen und nur
den Lunara-API-Dienst über den dokumentierten Update-Ablauf neu laden. Dadurch
kann jede neue zyklustrackende Person ein eigenes `TRACKER`-Konto mit einer
neuen internen Beziehungs-/Mandantengrenze anlegen; offene Partnerkonten
entstehen weiterhin nur per Einladung. Ein beendeter `migrate`-Container mit
Exitcode 0 ist erwarteter Zustand und kein Fehler.

Auf dem aktuellen Server wird Lunara als eigenständiges Compose-Projekt ohne
Portainer-Ownership betrieben. Kein Befehl in dieser Anleitung richtet sich
gegen den Portainer-Hauptstack 20.

## 4. Authentik Proxy Provider und Pangolin-Ressource

Authentik bleibt der einzige vorgeschaltete öffentliche Proxy. Für native
App-Aufrufe setzt der Provider jedoch exakt
`skip_path_regex = ^/api/v1(?:/|$)`: Authentik leitet diese Pfade ohne
Browser-Login an Lunara weiter. Das ist keine ungeschützte zweite Route. Die
Lunara-API erzwingt für alle nicht ausdrücklich öffentlichen Endpunkte weiterhin
Access-Token, Rollen, Eigentum und die interne Beziehungs-/Mandantengrenze.

Browser- und OpenAPI-Pfade wie `/api-docs` und `/api-docs-json` werden nicht
übersprungen und bleiben Authentik-geschützt. Die in
`LUNARA_AUTHENTIK_HUMAN_USERS` aufgeführten bestehenden Authentik-Konten sowie
lokal verwaltete technische Prüfzugänge sind ausschließlich für diese
administrative Browser-/OpenAPI-Schicht bestimmt. Die Android-App benötigt
weder einen Authentik-Benutzernamen noch ein Authentik-App-Passwort.

Prüfe zunächst Namen und Pfade read-only. Die Standardwerte entsprechen dem
aktuellen Zielserver und können über die genannten Variablen überschrieben
werden:

```bash
docker inspect authentik-server postgresql >/dev/null
test -f /docker/pangolin/config/db/db.sqlite
docker network inspect pangolin >/dev/null
```

Richte dann Authentik ein. Das Skript legt vor der Änderung einen
PostgreSQL-Dump unter `/var/backups/lunara/infrastructure` an.
Gateway-Zugangsdaten werden nur lokal unter `/docker/lunara` mit Modus `0600`
gespeichert und sind wie Passwörter zu behandeln:

```bash
sudo LUNARA_RUNTIME_DIR=/docker/lunara \
  LUNARA_PUBLIC_HOST=lunara.3li361.de \
  AUTHENTIK_CONTAINER=authentik-server \
  AUTHENTIK_DB_CONTAINER=postgresql \
  /docker/lunara/scripts/configure-authentik.sh --check
```

Wenn der Check ohne Konflikt endet, denselben Befehl ohne `--check` ausführen:

```bash
sudo LUNARA_RUNTIME_DIR=/docker/lunara \
  LUNARA_PUBLIC_HOST=lunara.3li361.de \
  AUTHENTIK_CONTAINER=authentik-server \
  AUTHENTIK_DB_CONTAINER=postgresql \
  /docker/lunara/scripts/configure-authentik.sh
```

Das Ergebnis entspricht folgender Provider-Konfiguration:

- Modus: Proxy
- External host: `https://lunara.3li361.de`
- Internal host: `http://lunara-api:3000`
- Skip path regex: `^/api/v1(?:/|$)`
- Intercept header authentication: für die geschützte Browser-/OpenAPI-Prüfung
  aktiviert
- Anwendung: ausschließlich lokal verwaltete technische Prüfzugänge sowie die
  explizit in `LUNARA_AUTHENTIK_HUMAN_USERS` freigegebenen Konten
- Outpost: vorhandener eingebetteter Authentik-Outpost

App und Browser verwenden dieselbe Domain `https://lunara.3li361.de`; es gibt
absichtlich keine zweite Subdomain und keinen direkten öffentlichen API-Port.
Das Skript kann lokale URL-Dateien für die geschützte OpenAPI-Prüfung erzeugen.
Diese Dateien haben Modus `0600`, sind Zugangsdaten und dürfen weder in die App
noch in Git, Dokumentation, Chat, QR-Codes oder Cloudflare übernommen werden.

Lege anschließend genau die Lunara-Ressource in Pangolin an. Das Skript prüft
verifizierte Domain und aktive Site, verweigert das Überschreiben einer
abweichenden Ressource und erstellt vor der SQLite-Transaktion ein konsistentes
Backup. Bei abweichender Installation müssen `PANGOLIN_DB`,
`PANGOLIN_DOMAIN_ID`, `PANGOLIN_SITE_ID` und `PANGOLIN_ORG_ID` bewusst gesetzt
werden:

```bash
sudo LUNARA_PUBLIC_HOST=lunara.3li361.de \
  PANGOLIN_DB=/docker/pangolin/config/db/db.sqlite \
  PANGOLIN_DOMAIN_ID=domain1 \
  PANGOLIN_SITE_ID=1 \
  PANGOLIN_ORG_ID=mannheim \
  /docker/lunara/scripts/configure-pangolin.sh --check
```

Erst danach mit denselben Werten ohne `--check` schreiben:

```bash
sudo LUNARA_PUBLIC_HOST=lunara.3li361.de \
  PANGOLIN_DB=/docker/pangolin/config/db/db.sqlite \
  PANGOLIN_DOMAIN_ID=domain1 \
  PANGOLIN_SITE_ID=1 \
  PANGOLIN_ORG_ID=mannheim \
  /docker/lunara/scripts/configure-pangolin.sh
```

Die erzeugte HTTPS-Ressource besitzt folgende Route:

- Name: `Lunara`
- Domain: `lunara.3li361.de`
- Ziel: `http://authentik-server:9000` (eingebetteter Proxy-Outpost)
- TLS: Let's Encrypt über den vorhandenen Resolver
- WebSocket ist nicht erforderlich; der Server-Sent-Events-Endpoint wird durch
  den HTTP-Proxy unterstützt, wird von Android Version 1 aber noch nicht genutzt
- Pangolin-eigene Basic Auth: deaktiviert; Authentik schützt die nicht
  übersprungenen Browserpfade

Kontrolliere die Ressource anschließend in Pangolin. Die Compose-Labels von
Lunara sind absichtlich deaktiviert; es darf keinen zweiten Traefik-Router geben,
der direkt auf `lunara-api:3000` zeigt und Authentik umgeht.

Die Android-App wird ausschließlich mit der normalen Basisadresse eingerichtet:

```text
https://lunara.3li361.de
```

Sie verwendet danach einen Lunara-Bearer-Access-Token und die rotierende
Lunara-Refresh-Sitzung. URLs mit eingebetteten Zugangsdaten gehören nicht in
die App. Ein Partner-QR enthält nur den einmal verwendbaren Lunara-
Einladungscode, niemals Server-, Lunara- oder Authentik-Passwörter.

Optional kann der Gateway-Pfad schon vor dem DNS-Eintrag gegen die kanonische
IPv4- oder IPv6-Adresse des erreichbaren Traefik-/Pangolin-HTTPS-Eingangs geprüft
werden. Ersetze die reservierte Beispieladresse; IPv6 wird ohne eckige Klammern
angegeben. Das Skript erzeugt intern ausschließlich
`--resolve lunara.3li361.de:443:IP` und übernimmt die Option für beide Requests:

```bash
sudo LUNARA_GATEWAY_RESOLVE_IP=192.0.2.10 \
  /docker/lunara/scripts/verify-gateway.sh /docker/lunara/gateway-a.url
```

Falls vor DNS noch kein vertrauenswürdiges Zertifikat ausgeliefert wird, kann
die Zertifikatsprüfung nur für diesen bewussten Test deaktiviert werden. Das
Skript zeigt dann eine deutliche Warnung; ohne die Variable bleibt TLS strikt:

```bash
sudo LUNARA_GATEWAY_RESOLVE_IP=192.0.2.10 \
  LUNARA_GATEWAY_ALLOW_UNTRUSTED_TLS=true \
  /docker/lunara/scripts/verify-gateway.sh /docker/lunara/gateway-a.url
```

## 5. Cloudflare – einziger externer Schritt

Nachdem Container und Pangolin-Ressource gesund sind:

1. In der DNS-Zone `3li361.de` einen CNAME `lunara` anlegen.
2. Ziel: `3li361.de`.
3. Zunächst „DNS only“ verwenden, entsprechend der bestehenden Hauptdomain.
4. Nach DNS-Auflösung die geschützte OpenAPI-Oberfläche und anschließend den
   nativen Metaendpoint testen:

```bash
sudo /docker/lunara/scripts/verify-gateway.sh /docker/lunara/gateway-a.url
sudo /docker/lunara/scripts/verify-gateway.sh /docker/lunara/gateway-b.url
```

`verify-gateway.sh` liest die Datei nur lokal, verlangt HTTPS und restriktive
Dateirechte und legt Zugangsdaten weder in Prozessargumente noch in die Ausgabe.
Es prüft zuerst Authentik vor `/api-docs-json`, verwendet dessen Sitzung erneut
und ruft anschließend `/api/v1/meta` ohne Authentik-Zugangsdaten auf. Nach dem
Cloudflare-DNS-Eintrag muss die Prüfung ohne Resolve- oder TLS-Ausnahme
erfolgreich sein. Zusätzlich muss ein anonymer Aufruf eines nicht öffentlichen
API-Endpunkts wie `/api/v1/me` mit Lunara-HTTP-401 statt mit Daten antworten.

Passwörter gehören weder in Cloudflare-Regeln noch in DNS-Werte.

## 6. Bootstrap, Registrierung und Partnerkonto

1. Android-App öffnen; die Produktionsvariante verwendet standardmäßig
   `https://lunara.3li361.de`. Die Adresse bleibt editierbar.
2. Auf einer noch nie eingerichteten Instanz muss `/api/v1/meta`
   `setupRequired: true` melden. Ein bereits eingerichteter Server meldet
   dauerhaft `false`.
3. Einmaligen `BOOTSTRAP_TOKEN` lokal aus `.env` verwenden und das erste Konto
   mit Rolle `TRACKER` anlegen. Es gibt kein sichtbares Haushaltsfeld.
4. Erneut `/meta` prüfen: `setupRequired` muss nun `false` sein.
5. Für einen Server mit mehreren Paaren `REGISTRATION_ENABLED=true` setzen.
   Danach können weitere Personen über „Neue Beziehung starten“ jeweils ein
   isoliertes `TRACKER`-Konto anlegen. Ist der Wert `false`, bleiben nur Login
   und Einladung möglich.
6. Als Tracker eine permanente Partner-Einladung erzeugen. Sie läuft nicht
   zeitlich ab, ist aber nur einmal verwendbar und jederzeit widerrufbar. Ein
   neuer Code widerruft vorhandene offene Partnercodes derselben Beziehung.
7. Die Partnerperson richtet mit eigenem Benutzernamen und eigenem Passwort das
   `PARTNER`-Konto ein. Text und QR enthalten ausschließlich den Code.
8. `BOOTSTRAP_TOKEN` nach erfolgreichem Setup aus der Live-Konfiguration
   entfernen. Der persistente Serverstatus hält Bootstrap auch dann gesperrt,
   wenn später sämtliche Konten und Beziehungen gelöscht werden.

Der Bootstrap-Token ist ausschließlich der Besitznachweis für einen noch nie
eingerichteten Server. Er verhindert, dass eine fremde Person die erste
Beziehung anlegt. Nach dem ersten erfolgreichen Setup wird der Zustand dauerhaft
in `ServerState` gespeichert; für Registrierung, Login, Einladung und normalen
Betrieb wird der Token nicht mehr verwendet. Fehlt der Token bei einem frischen
Server, bleibt Setup sicher gesperrt. Die intern weiterhin `Household` genannte
UUID ist nur die unsichtbare Mandanten-/Autorisierungsgrenze. Pro solcher
Beziehung sind genau ein `TRACKER` und ein `PARTNER` vorgesehen; Konten
verschiedener Beziehungen können keine Daten voneinander abrufen.

Auch wenn später die letzte Beziehung einschließlich aller Konten bewusst
gelöscht wird, bleibt `setupRequired` wegen des persistenten Serverstatus
`false`. Neue Tracker-Konten entstehen dann ausschließlich über die normale,
bewusst aktivierte Registrierung. Der `BOOTSTRAP_TOKEN` darf daher nach dem
ersten Setup aus `.env` entfernt werden; er gehört niemals in die APK, in einen
QR-Code, in Chat oder GitHub.

## 7. Backup, Restore und Updates

```bash
sudo LUNARA_ENV_FILE=/docker/lunara/.env /docker/lunara/scripts/backup.sh
sudo LUNARA_ENV_FILE=/docker/lunara/.env /docker/lunara/scripts/update.sh
```

Backups enthalten einen PostgreSQL-Custom-Dump und nicht-sensible Konfiguration,
aber absichtlich keine Secrets. Zugriff auf `/var/backups/lunara` auf root
begrenzen. Der Master-Key muss getrennt und verschlüsselt gesichert werden.

Ein Restore ist destruktiv, erzeugt zuerst ein eng benanntes Vorab-Backup und
verlangt deshalb einen expliziten Schalter. Er stoppt ausschließlich die
Lunara-API, beendet deren verbleibende Datenbanksitzungen, spielt den Dump in
einer Transaktion ein, führt den separaten Migrator aus und startet danach die
API wieder. Kein anderer Heimserver-Dienst wird gestoppt:

```bash
sudo LUNARA_ENV_FILE=/docker/lunara/.env \
  /docker/lunara/scripts/restore.sh \
  --confirm-replace-lunara-database \
  /var/backups/lunara/lunara-YYYYMMDDTHHMMSSZ.tar.gz
```

Anschließend `/ready`, relevante Logs und die Entschlüsselung einer bekannten
Testnotiz prüfen. Restore regelmäßig auf einer isolierten Testinstanz erproben.

## 8. Migrationen und Rollback

Migrationen sind vorwärtsgerichtet. Beim normalen Stack-Start erzwingt Compose
über `service_completed_successfully`, dass `migrate` vor `api` erfolgreich war.
Bei Updates erstellt `scripts/update.sh` zuerst ein Backup, baut das gemeinsame
API-/Migrator-Image, startet `docker compose run --rm migrate`, ersetzt nur die
API und prüft deren Readiness. Produktive Migrationen müssen deshalb mit der bis
zum API-Austausch noch laufenden Vorgängerversion kompatibel sein
(Expand/Contract-Verfahren).

Die Migration `20260813150000_persistent_server_state` legt genau einen
`ServerState`-Datensatz an. Bestehende Installationen mit bereits vorhandenen
Konten werden dabei als eingerichtet markiert; nur eine wirklich frische,
leere Datenbank bleibt im Bootstrap-Zustand. Erst nachdem Migration, API-
Readiness und `setupRequired: false` geprüft wurden, den `BOOTSTRAP_TOKEN` aus
der Live-`.env` entfernen. Ein Datenbank-Restore muss den `ServerState`-
Datensatz mit wiederherstellen.

Die darauffolgende Migration
`20260813160000_unique_active_period_start` schützt die fachliche Regel auch
auf Datenbankebene: Pro Benutzer und lokalem Startdatum darf höchstens ein
nicht gelöschter Periodenstart existieren. Vor dem Update prüft die Migration
den vorhandenen Bestand und bricht bei widersprüchlichen aktiven Duplikaten
ab, statt Daten stillschweigend zusammenzuführen oder zu löschen.

Auf dem aktuellen Zielserver sind alle sechs Migrationen angewendet. Der
persistente Status meldet `setupRequired: false`, die bewusst aktivierte
Registrierung meldet `registrationEnabled: true`, und der `BOOTSTRAP_TOKEN`
wurde anschließend aus der Live-`.env` entfernt. Eine serverlokale Sicherung
der vorherigen Konfiguration besitzt Modus `0600`; sie darf nicht in Git,
Backuparchive ohne Verschlüsselung oder Supportausgaben übernommen werden.

Ein Schema-Rollback erfolgt nicht durch eine rückwärts laufende Migration,
sondern durch geprüften Code-Rollback plus Restore des unmittelbar davor
erstellten Dumps. Das Update-Skript führt diesen Rollback nicht automatisch aus;
bei fehlgeschlagener Readiness bleiben Backup und Logs zur bewussten Diagnose
erhalten.
