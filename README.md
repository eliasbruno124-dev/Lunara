# Lunara

Lunara ist eine private Zyklus- und Perioden-App mit drei klar getrennten
Betriebsarten:

- **Solo:** auf Android mit SQLCipher/Keystore oder in der PWA mit einem
  passphrasenverschlüsselten lokalen Vault, jeweils ohne Konto oder Server;
- **Self-hosted als Paar:** Android und installierbare Web-App nutzen dieselbe
  eigene API, aber getrennte Konten mit den Rollen `TRACKER` und `PARTNER`;
- **Self-hosted mit Authentik:** optionaler persönlicher OIDC-Login vor derselben
  Lunara-API. Ohne Authentik bleibt Lunara vollständig funktionsfähig.

Lunara enthält keine Werbung, Telemetrie, Analyse-SDKs, Firebase- oder
Google-Play-Services-Pflicht. Die App ist kein Medizinprodukt, diagnostiziert
nichts, bestätigt keine Ovulation und darf nicht als Verhütungsmethode
verwendet werden.

## Release 1.2.1

Auf der Partner-Startseite steht der freigegebene Ausblick jetzt vor dem Tipp,
auf Android und im Browser. Ohne Freigabe erscheint weiterhin kein privater
Zeitraum. Die Tipps verbinden kurze Einordnung und eine kleine Idee für euch;
medizinische Hintergrundlinks öffnen nur auf Wunsch eine geprüfte öffentliche
Quelle. Es gibt keine automatisch erzeugten Tagesdiagnosen oder Gefühle aus
einer Kalenderschätzung. Die Prognoseformel bleibt unverändert.

Als Format-Inspiration dienen die öffentlich beschriebenen kurzen Erklärungen
und praktischen Ideen von [Flo for Partners](https://flo.health/product-tour/flo-for-partners).
Lunara übernimmt keine Premium-Texte und behauptet weder Zugang zur
Bezahlschranke noch eine Zusammenarbeit mit Flo.

### Journal-Gestaltung seit 1.2.0

Android und PWA erhalten eine zusammenhängende Journal-Gestaltung mit warmem
Elfenbein, tiefem Petrol und zurückhaltendem Korallakzent. Serifentitel und
klare Daten- und Aktionsbereiche geben Heute, Kalender und Verlauf eine neue
Hierarchie; Hell- und Dunkelmodus verwenden ausschließlich lokale Schriften.
Gestaltung und Grenzen: [DESIGN.md](docs/DESIGN.md).

Die Android-Einstellungen öffnen zuerst benannte Kategorien statt einer
langen Liste. Je nach Rolle und Betriebsmodus sind Darstellung,
Zyklus & Schätzungen, Partner verbinden, Datenschutzfreigaben, Was mir hilft,
Benachrichtigungen, Konto und Sicherheit sowie Diese Android-App erreichbar.
**Alle Einstellungen** oder System-Zurück führt aus einer Kategorie zur
Übersicht. Die PWA gruppiert Einstellungen in aufklappbaren Bereichen.

Alle 71 Katalogtipps sind auf Deutsch und Englisch redaktionell überarbeitet:
kurzer verständlicher Kontext und eine praktische, freiwillige Idee.
Stabile IDs erhalten vorhandene Ausblendungen
und Feedback; persönliche Wünsche bleiben unverändert und vorrangig.
Vorlieben beziehen sich bei Katalogtipps auf die tatsächliche Handlung:
Wärme ist keine Berührung, eine eigene Erinnerung keine Erinnerung an die
Tracker-Person. Private Tage schließen auch eigene Wünsche aus, die nur an
eine Datenfreigabe gebunden sind. Phasen- und Symptomfreigaben werden vor der
Auswahl geprüft. Quellen und Einschränkungen der Worterkennung stehen in
[Qualität der Tipps](docs/TIP_QUALITY.md) und [PARTNER_TIPS.md](docs/PARTNER_TIPS.md).

Konten, Offline-Daten und Freigaben werden durch die Neugestaltung nicht
migriert. Den tatsächlich verifizierten Build-, Test- und Auslieferungsstand
dokumentiert [FINAL_REPORT.md](FINAL_REPORT.md); vorhandene Testquellen allein
belegen keinen bestandenen Geräte- oder Barrierefreiheitstest.

### Weiterhin enthalten: Verbindungsstatus aus 1.1.6

Eure Partnerverknüpfung wird unabhängig vom Datenabgleich geprüft; ein
Abruffehler bedeutet nicht „getrennt“. Bestätigte Verbindungen bleiben
verschlüsselt zwischengespeichert und werden offline als letzter bekannter
Stand gekennzeichnet. Der Statusknopf erläutert Betriebsmodus, letzte
Aktualisierung, offene Änderungen und nötige Anmeldung. App- und Serverversion
sind getrennt beschriftet.

In 1.1.6 wurden zunächst 50 der 71 deutschen/englischen Katalogtipps konkreter
formuliert.
**Was mir wirklich hilft** erlaubt ausdrücklich freigegebene eigene Wünsche,
die unverändert und vorrangig erscheinen. Ein anderer Vorschlag ist per Button
abrufbar. Quellen und Grenzen: [Qualität der Tipps](docs/TIP_QUALITY.md).

Der freiwillige PayPal-Button öffnet nach Bestätigung einen externen Link.
Keine Zahlungs-SDKs, keine automatische Zahlung, keine Daten im Link. Für eine
eigene Marke `donation_url` in `apps/android/app/src/main/res/values/brand.xml`
ändern oder leeren. Der voreingestellte PayPal.me-Name stammt vom Betreiber;
die Kontoinhaberschaft wurde nicht durch eine Zahlung verifiziert.

### Automatische Synchronisierung seit 1.1.5

Android erneuert die Anmeldung und synchronisiert automatisch. Verlorene
Erneuerungsantworten werden sicher wiederholt; Netzfehler behalten ihre
wartenden Einträge. Ist eine frühere Sitzung bereits ungültig, erlaubt
**Anmeldung erneuern** die Anmeldung im selben Konto ohne Datenverlust.

Die signierte `artifacts/lunara-release.apk` als Update auf beiden Geräten
installieren, **nicht vorher deinstallieren oder App-Daten löschen**. Eine
geänderte Freigabe wird nicht automatisch aktiviert. Genauigkeitsgrenzen der
Zyklusprognose stehen in [ALGORITHM.md](ALGORITHM.md).

### Weiterhin enthalten: Einladungen aus 1.1.4

Partner-Einladungen bleiben bei Authentik-Anmeldungen erhalten und lassen sich
auch direkt in Android unter **Einstellungen → Partner verbinden** eingeben
oder scannen. Bestehende leere Konten werden erst nach Bestätigung verbunden;
vorhandene Daten und ausstehende Offline-Einträge werden nicht still gelöscht.
Installationen lassen sich mit der signierten Release-APK aktualisieren.

### Funktionen seit 1.1.0

Version 1.1.0 ergänzt den bisherigen Server-/Android-Kern um:

- einen echten lokalen Android-Solomodus mit SQLCipher, Android Keystore,
  lokaler Prognose, Kalender, Verlauf und lokalen Benachrichtigungen;
- ein portables, passphrasenverschlüsseltes Solo-Backup zum Gerätewechsel;
- eine installierbare PWA für moderne iOS-, Android- und Desktop-Browser mit
  Self-hosting- und passphrasengeschütztem lokalen Solomodus;
- optionalen Authentik-OIDC-Login über Authorization Code und PKCE S256 mit
  einem festen Android-verifizierten Publisher-App-Link, unabhängig von der
  gewählten Self-hosting-API-Domain;
- einen direkt über Traefik betreibbaren Modus ohne Authentik;
- einen wischbaren Monatskalender bis fünf Jahre in die Zukunft mit eindeutig
  unterscheidbaren tatsächlichen und geschätzten Periodentagen;
- den robusten, deterministischen Prognosealgorithmus `lunara-robust-v2`.

Der bestehende Kern bleibt erhalten: PostgreSQL/Prisma, Argon2id, kurze
Access-Tokens, rotierende gehashte Refresh-Tokens, Gerätesitzungen,
idempotenter Cursor-Sync, Soft Deletes, strikte Mandantenisolation, granulare
Partnerfreigaben, 71 deutsch/englische Partner-Tipps, Care Preferences,
Check-ins, lokale Erinnerungen sowie JSON-/CSV-Export.

## Solo nutzen und auf ein neues Handy übertragen

Beim ersten Android-Start kann **Nur auf diesem Gerät** gewählt werden. Dieser
Modus erzeugt kein Serverkonto und führt keine Netzwerk-Synchronisierung aus.
Die Room-Datenbank ist mit SQLCipher verschlüsselt; ihr Schlüssel ist an den
Android Keystore des Geräts gebunden.

Für einen Gerätewechsel wird unter **Einstellungen → Konto und Sicherheit →
Portables verschlüsseltes Backup → Backup erstellen** eine
`.lunara`-Datei erstellt. Sie enthält Profil, Einstellungen, Perioden und
Tageslogs und ist mit PBKDF2-HMAC-SHA256 und AES-256-GCM geschützt. Über den
Android-Systemdateidialog kann als Ziel auch ein installierter Anbieter wie
Google Drive, Nextcloud, USB-Speicher oder ein lokaler Ordner gewählt werden.
Lunara integriert dafür kein Google-SDK und erhält keinen allgemeinen Zugriff
auf das Cloudkonto.

Auf dem neuen Gerät wird erneut der Solomodus gewählt und die Datei im selben
Einstellungsbereich über **Backup wiederherstellen** geöffnet. Ein
Import-Journal vollendet einen nach dem Room-Commit unterbrochenen
Einstellungsimport beim nächsten Start idempotent.
Die Passphrase wird nicht gespeichert und kann nicht wiederhergestellt werden.
Android-Cloudbackup und automatische Gerät-zu-Gerät-Übertragung sind bewusst
deaktiviert, damit kein unbrauchbarer lokaler Zustand ohne den nicht
exportierbaren Keystore-Schlüssel entsteht.

## Self-hosted Architektur

```text
Android / PWA
      |
      | HTTPS
      v
Traefik direkt  ODER  Traefik/Pangolin -> Authentik Proxy
      |
      v
NestJS API + ausgelieferte PWA
      |
      v
PostgreSQL 18 (nur internes Docker-Netz)
```

Eine Serverinstanz kann beliebig viele voneinander isolierte Beziehungen
aufnehmen. Intern bildet `Household` die UUID-basierte Autorisierungsgrenze;
dieses technische Modell wird in der Oberfläche nicht als „Haushalt“
präsentiert. Jede Beziehung besitzt in Version 1 genau einen `TRACKER` und
einen über einen einmal verwendbaren Einladungscode verbundenen `PARTNER`.

## Gateway- und Anmeldemodi

| Modus | Öffentlicher Weg | Persönliche Anmeldung |
| --- | --- | --- |
| `direct` | Traefik leitet direkt zur API/PWA | Lunara-Benutzername und -Passwort |
| `authentik` | Traefik/Pangolin leitet über den Authentik-Proxy | Authentik-OIDC oder weiterhin bewusst aktivierter Lunara-Passwortweg |

Das Serverfeld bleibt leer und erwartet nur eine credentialfreie HTTPS-Adresse,
beispielsweise:

```text
https://lunara.example.com
```

Im Authentik-Modus sind `/api/v1/meta`, `/.well-known/assetlinks.json` und die
statische Einladungs-Landeseite `/invite` anonym erreichbar. Meta enthält keine Konto-
oder Gesundheitsdaten und teilt dem Client mit, ob OIDC angeboten wird. Nach
dem Browserlogin sendet der Client auf exakt denselben Ursprung:

- außen `Authorization: Bearer <Authentik-Access-Token>` für den Proxy;
- innen `X-Lunara-Authorization: Bearer <Lunara-Access-Token>` für Konto,
  Rolle, Eigentum und Freigaben.

Die beiden Tokens sind nicht austauschbar. OIDC legt kein Lunara-Konto anhand
von E-Mail oder Benutzername automatisch an. Ein bestehendes Konto muss nach
erneuter Passwortprüfung in den Einstellungen oder gezielt über die Admin-CLI
mit exakt geprüftem Issuer und Subject verknüpft werden. Der frühere technische
Basic-Header bleibt nur als kontrollierte Kompatibilitätsmöglichkeit erhalten;
neue Installationen verwenden die normale Serveradresse und OIDC.

## Schnellstart ohne Authentik

Voraussetzungen sind Debian 12, Docker Engine, das Compose Plugin, Traefik 3,
ein externes Proxy-Netz und eine HTTPS-Domain.

```bash
cp .env.example .env
chmod 0600 .env
# Secrets und PUBLIC_BASE_URL/PUBLIC_HOST in .env setzen,
# dann GATEWAY_MODE=direct wählen.
./scripts/compose.sh config --quiet
./scripts/compose.sh build api
./scripts/compose.sh up -d
./scripts/compose.sh ps --all
```

Für Authentik gelten die zusätzlichen, fail-closed Schritte in
[`DEPLOYMENT.md`](DEPLOYMENT.md). Das Wrapper-Skript `scripts/compose.sh`
verhindert, dass versehentlich die falsche Compose-Kombination für den
gewählten Gateway-Modus verwendet wird.

## Entwicklung

```bash
npm ci
npm run prisma:generate
npm run prisma:validate
npm run lint
npm test
npm run build
npm run openapi:check
./scripts/e2e.sh

cd apps/android
./gradlew testLocalDebugUnitTest testProductionDebugUnitTest
./gradlew lintLocalDebug lintProductionDebug
./gradlew compileProductionDebugAndroidTestKotlin
./gradlew assembleProductionDebug assembleProductionRelease
./gradlew bundleProductionRelease
```

Die tatsächlich ausgeführte Prüfmatrix steht in [`TESTING.md`](TESTING.md)
und [`FINAL_REPORT.md`](FINAL_REPORT.md). Kompilierung, Emulatorläufe und
Prüfungen auf tatsächlichen Handys werden dort getrennt ausgewiesen.

## Android-Artefakte

Release-Artefakte werden unter `artifacts/` bereitgestellt. Die tatsächlich
geprüfte Version und der Buildstatus stehen in [FINAL_REPORT.md](FINAL_REPORT.md):

- `lunara-debug.apk` – Production-Debug/Testinstallation mit Debugsignatur und
  HTTPS-only; sie kann Passwort-/Headerauth testen, ihr Debugzertifikat erfüllt
  den Production-App-Link jedoch nicht und sie ist kein universeller OIDC-
  Testbuild;
- `lunara-release.apk` – empfohlene Nutzerinstallation, mit einem
  ausschließlich serverlokalen Upload-Key signiert und für den konfigurierten
  HTTPS App Link gebaut;
- `lunara-release-unsigned.apk` – bewusst unsignierte Release-APK;
- `lunara-play-upload.aab` – mit dem Upload-Key signiertes Bundle für Play;
- `lunara-release-unsigned.aab` – bewusst unsigniertes Referenz-Bundle.

Prüfsummen stehen ausschließlich in `artifacts/SHA256SUMS`. Keystore, Alias,
Passwörter und private Schlüssel liegen nicht im Repository. Für Google Play
ist zusätzlich Play App Signing vorgesehen; Details und noch offene
Publisher-/Policy-Schritte stehen in
[`docs/GOOGLE_PLAY_RELEASE.md`](docs/GOOGLE_PLAY_RELEASE.md).

## Projektstruktur

```text
apps/android/             native Kotlin-/Compose-App
apps/web/                 installierbare TypeScript-PWA
services/api/             NestJS-/Prisma-API und PWA-Auslieferung
packages/openapi-client/  OpenAPI-Snapshot und Kotlin-Referenzclient
infra/docker/             Container und Compose-Ergänzungen
infra/traefik/            direkte und Authentik-bezogene Beispiele
scripts/                  Betrieb, Backup, Restore, Update, Admin und E2E
docs/                     Release-, Store- und Produktdokumentation
artifacts/                tatsächlich gebaute Android-Artefakte
```

## Dokumentation

- [`DEPLOYMENT.md`](DEPLOYMENT.md) – Direct-/Authentik-Betrieb, Migrationen,
  Backup und Update
- [`SECURITY.md`](SECURITY.md) – Bedrohungsmodell, OIDC, Schlüssel und Grenzen
- [`PRIVACY.md`](PRIVACY.md) – Solo-, PWA- und Serverdaten
- [`ALGORITHM.md`](ALGORITHM.md) – exakte Prognoseberechnung
- [`TESTING.md`](TESTING.md) – reale Prüfungen und nicht ausgeführte Tests
- [`docs/GOOGLE_PLAY_RELEASE.md`](docs/GOOGLE_PLAY_RELEASE.md) – Signierung,
  Play App Signing, App Links und Einreichungscheckliste
- [`docs/STORE_LISTING_DE.md`](docs/STORE_LISTING_DE.md) und
  [`docs/STORE_LISTING_EN.md`](docs/STORE_LISTING_EN.md) – vorbereitete,
  vor Einreichung abzugleichende Storetexte
- [`docs/COMMERCIAL_READINESS.md`](docs/COMMERCIAL_READINESS.md) – Self-hosting
  und Vermarktungsgrenzen

## Lizenz

Derzeit ist keine Weitergabelizenz festgelegt. Alle Rechte verbleiben beim
Repository-Eigentümer. Vor einer öffentlichen oder kommerziellen Distribution
muss bewusst ein Lizenzmodell gewählt werden.
