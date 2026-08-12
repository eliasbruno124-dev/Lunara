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
Ein normaler Start darf keine offene Registrierung einschalten. Ein beendeter
`migrate`-Container mit Exitcode 0 ist erwarteter Zustand und kein Fehler.

Auf dem aktuellen Server wird Lunara als eigener Compose-/Portainer-Stack
betrieben. Kein Befehl in dieser Anleitung richtet sich gegen Stack 20.

## 4. Authentik Proxy Provider und Pangolin-Ressource

Authentik unterstützt eingehende HTTP-Basic-Authentifizierung an einem Proxy
Provider. Dabei muss das Passwort ein Authentik-**App-Passwort** sein; ein
normales Kontopasswort wird absichtlich nicht verwendet. Das mitgelieferte
Skript erstellt einen eng gebundenen Provider, die Gruppenrichtlinie, zwei
getrennte Service-Konten und deren App-Passwörter. Siehe auch die offizielle
Dokumentation:
https://docs.goauthentik.io/add-secure-apps/providers/proxy/header_authentication

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
- Intercept header authentication: aktiviert
- Anwendung: ausschließlich die vorgesehenen Lunara-Gateway-Konten/-Gruppe
- Outpost: vorhandener eingebetteter Authentik-Outpost

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
- Pangolin-eigene Basic Auth: deaktiviert; Authentik prüft den Header

Kontrolliere die Ressource anschließend in Pangolin. Die Compose-Labels von
Lunara sind absichtlich deaktiviert; es darf keinen zweiten Traefik-Router geben,
der direkt auf `lunara-api:3000` zeigt und Authentik umgeht.

Die API nutzt getrennte Lunara-Konten zusätzlich zur äußeren Authentik-Schranke.
Android sendet anfangs bzw. nach Ablauf der Outpost-Sitzung:

```text
Authorization: Basic <gateway credentials>
X-Lunara-Authorization: Bearer <Lunara access token>
```

Nach erfolgreicher Basic-Prüfung entfernt Authentik den Basic-Header, liefert
einen Session-Cookie und leitet die Anfrage weiter. Android persistiert diesen
Cookie; spätere Requests können so ohne erneute Passwortprüfung laufen. Das
App-Passwort wird dennoch Keystore-geschützt behalten, um eine abgelaufene
Outpost-Sitzung automatisch neu aufzubauen.

Die App akzeptiert beim Setup bequem
`https://gateway-user:gateway-pass@lunara.3li361.de`, entfernt Userinfo jedoch
sofort aus der gespeicherten/angezeigten URL und legt die Zugangsdaten
Keystore-geschützt separat ab. QR-Codes enthalten niemals Passwörter.

Die Header-Prüfung ist echte Authentik-Authentifizierung, aber keine MFA: Ein
App-Passwort ist ein langlebiges Maschinen-Zugangsmittel. Redirect-basierte
Authentik-Anmeldung bleibt Browser-Fallback und ersetzt die Lunara-Anmeldung
nicht.

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
4. Nach DNS-Auflösung beide getrennten Gateway-Zugänge testen:

```bash
sudo /docker/lunara/scripts/verify-gateway.sh /docker/lunara/gateway-a.url
sudo /docker/lunara/scripts/verify-gateway.sh /docker/lunara/gateway-b.url
```

`verify-gateway.sh` liest die Datei nur lokal, verlangt HTTPS und restriktive
Dateirechte und legt Zugangsdaten weder in Prozessargumente noch in die Ausgabe.
Es prüft zuerst Basic-Authentifizierung und eine gültige Lunara-Metaantwort und
verwendet danach den von Authentik gesetzten Sitzungscookie erneut. Nach dem
Cloudflare-DNS-Eintrag muss die Prüfung ohne Resolve- oder TLS-Ausnahme
erfolgreich sein.

Passwörter gehören weder in Cloudflare-Regeln noch in DNS-Werte.

## 6. Bootstrap und zweites Konto

1. Android-App öffnen und die Serveradresse eingeben.
2. `/api/v1/meta` muss `setupRequired: true` melden.
3. Einmaligen `BOOTSTRAP_TOKEN` lokal aus `.env` verwenden und das erste Konto
   mit Rolle `TRACKER` anlegen.
4. Erneut `/meta` prüfen: `setupRequired` muss nun `false` sein.
5. Als Tracker eine ablaufende Einladung erzeugen.
6. Partnerperson richtet mit eigenem Benutzernamen und eigenem Passwort das
   `PARTNER`-Konto ein. Einladung und QR enthalten kein Passwort.
7. Bootstrap-Token nach erfolgreichem Setup in der Live-Konfiguration durch einen
   neuen Zufallswert ersetzen; serverseitig bleibt Bootstrap dennoch gesperrt.

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

Ein Schema-Rollback erfolgt nicht durch eine rückwärts laufende Migration,
sondern durch geprüften Code-Rollback plus Restore des unmittelbar davor
erstellten Dumps. Das Update-Skript führt diesen Rollback nicht automatisch aus;
bei fehlgeschlagener Readiness bleiben Backup und Logs zur bewussten Diagnose
erhalten.
