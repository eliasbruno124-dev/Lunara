# Lunara 1.0.5 – Abschlussbericht

Stand: 13. August 2026

Lunara 1.0.5 ist als selbst gehosteter Kernablauf implementiert, gebaut,
getestet und auf dem produktiven Server ausgerollt. Die API läuft gesund hinter
Pangolin und Authentik. Eine installierbare Debug-APK sowie eine unsignierte
Release-APK wurden tatsächlich erzeugt. Dieser Bericht trennt bestätigte
Ergebnisse von bewusst offenen Funktionen.

## Implementierte Architektur

- Monorepo mit nativer Kotlin-/Jetpack-Compose-App, NestJS-/Prisma-API,
  PostgreSQL 18, versionierter REST-API und generiertem Kotlin-OpenAPI-Client
- Docker-Multi-Stage-Image als UID/GID `10001:10001`, read-only Root-Dateisystem,
  Capability-Drop und `no-new-privileges`
- eine Serverinstanz für beliebig viele voneinander isolierte Beziehungen;
  intern bleibt eine UUID-Mandantengrenze erhalten, ohne „Haushalt“ in der App
  anzuzeigen
- je Beziehung genau ein `TRACKER` und ein `PARTNER`, jeweils mit eigenem
  Lunara-Konto, eigenem Passwort und serverseitig erzwungener Rolle
- Room/SQLCipher, Android Keystore, lokale Mutation Queue, clientseitige UUIDs,
  optimistische Oberfläche und WorkManager-Synchronisierung
- Pangolin als verwalteter öffentlicher Ressourcenpfad und Authentik als
  vollständiger vorgeschalteter Schutz für die gesamte Domain

## Tatsächlich umgesetzter Produktumfang

Der durchgehende Kernablauf funktioniert:

1. erste oder weitere Tracker-Beziehung anlegen
2. einmal verwendbare und bis zur Nutzung zeitlich unbegrenzte Einladung
   erzeugen, anzeigen und widerrufen
3. Partner mit eigenem Konto verbinden
4. Periodenstart offline oder online eintragen, Datum und optionales Ende
   korrigieren oder den Eintrag löschen
5. deterministische Bereichsprognose neu berechnen
6. Offline-Mutationen idempotent genau einmal synchronisieren
7. beim Partner nur ausdrücklich freigegebene Werte darstellen

Weitere bestätigte Funktionen:

- automatische Synchronisierung nach Anmeldung und Mutationen, beim Öffnen,
  regelmäßig im Vordergrund sowie sparsam über WorkManager mit begrenztem
  Backoff; manueller Sync bleibt zusätzlich verfügbar
- persönlicher Passwortwechsel in den Android-Einstellungen mit aktuellem
  Passwort; andere Lunara-Sitzungen werden dabei widerrufen
- Kontopasswörter mit 8 bis 128 Unicode-Codepoints ohne Pflicht zu
  Großbuchstaben, Ziffern oder Sonderzeichen; bestehende Zugangsdaten werden
  zur Kompatibilität mit 1 bis 128 Zeichen verifiziert. Die separate
  Exportpassphrase bleibt als Verschlüsselungsschlüssel bei 12 bis 128 Zeichen
- Argon2id, kurze Access-Tokens, rotierende und nur gehasht gespeicherte
  Refresh-Tokens, Gerätesitzungen, Login-Rate-Limit und generische
  Login-Fehlermeldungen
- Perioden, Tageslogs, eigene Symptome, verschlüsselte Freitextnotizen,
  Soft Deletes, Cursor-Sync, Versionskonflikte und Idempotenz
- granulare Partnerfreigaben, Freigabepause und privater Tag; private Tage
  verbergen tatsächliche Periodendaten, Phase, Symptome und Fertilitätsangaben
- nicht freigegebene Notizen werden serverseitig nicht entschlüsselt; der
  Android-Partnercache wird nach Freigabeentzug autoritativ bereinigt
- 71 sorgfältig formulierte Tipps in Deutsch und Englisch, kategorisiert und
  gewichtet nach freigegebener Phase, freigegebenen Symptomen, Feedback und
  Care Preferences
- acht direkt einstellbare Care Preferences: Wärme, Nähe, Raum, Snacks,
  Gespräch, Haushaltshilfe, Erinnerungen und keine ungefragten Ratschläge
- Partner-Check-ins, Tageslogs, Profile, Exporte/Importvorschau,
  Sitzungsverwaltung, sicherheitsrelevante Audits, SSE und Admin-CLI im Backend

### Prognose und geschätztes fruchtbares Fenster

Die Prognose verwendet keine pauschalen 28 Tage. Sie wertet bevorzugt die
letzten vollständigen Zyklen robust und mit höherem Gewicht für neuere Zyklen
aus, zeigt grundsätzlich einen Zeitraum und verbreitert ihn bei hoher
Streuung. Unter drei vollständigen Zyklen wird die Aussage als unsicher
gekennzeichnet. Sondermodi und pausierte Prognosen unterdrücken irreführende
Standardwerte.

Die Fertilitätsschätzung ist standardmäßig aus. Sie erfordert ein persönliches
Opt-in, eine ausdrückliche Sicherheitsbestätigung und mindestens drei
vollständige Zyklen. Die Partneranzeige braucht zusätzlich eine eigene
Freigabe. Beim Abschalten des persönlichen Opt-ins wird die Partnerfreigabe
atomar widerrufen. Die App bezeichnet das Fenster immer als geschätzt und weist
darauf hin, dass es weder eine Ovulation bestätigt noch zur Verhütung geeignet
ist. Dies ist eine vorsichtige Kalenderschätzung, keine medizinische
Vorhersage.

## Authentik, Header Auth und Pangolin

Der produktive Zielzustand wurde nach einem validierten Authentik-Datenbankdump
erfolgreich eingerichtet:

- genau eine neutrale Serviceidentität `lunara-native-app`
- genau ein nicht ablaufendes App-Passwort
- keine Dune-/Elias- oder A-/B-Konten in der Lunara-Gateway-Gruppe
- leeres `skip_path_regex`: Root, Health, Ready, Meta, Login und OpenAPI sind
  ohne Authentik-Zugang geschützt
- alte A-/B-Header-Credentials sind serverseitig ungültig und ihre Dateien in
  ein root-only Archiv verschoben
- die Pangolin-Ressource ist weiterhin in der Pangolin-Oberfläche der
  bestehenden Organisation verwaltbar; es existiert kein paralleler direkter
  API-Pfad

Die einzige aktive Header-Auth-URL liegt ausschließlich serverlokal in:

```text
/docker/lunara/lunara-app-headerauth.url
```

Dateimodus: `0600`. Das Geheimnis wird absichtlich nicht in Git, APK,
Dokumentation oder Chat ausgegeben.

Beim ersten Start ist das Serverfeld leer. Für die einmalige Eingabe gilt:

```text
https://lunara-native-app:APP-PASSWORT@lunara.3li361.de
```

Die konkrete vollständige Adresse steht in der genannten root-only Datei. Die
App trennt Benutzer und App-Passwort unmittelbar von der URL, speichert das
Gateway-Credential Keystore-gestützt und behält als sichtbare Basisadresse nur
`https://lunara.3li361.de/`. Server- und Einladungs-QRs enthalten niemals
Authentik- oder Lunara-Passwörter. Redirects mit dem Basic Credential werden
nicht verfolgt.

Es gibt damit genau **einen gemeinsamen Header-Auth-Zugang** für die native
App. Zusätzlich bleibt zwingend das persönliche Lunara-Token erhalten: außen
`Authorization: Basic …` für Authentik, innen
`X-Lunara-Authorization: Bearer …` für Konto, Rolle und Datenisolation. Ein
einziger gemeinsamer Zugang allein dürfte niemals Tracker- und Partnerrechte
ersetzen.

Tatsächlich ausgeführte öffentliche Prüfungen:

- gemeinsamer Header-Zugang auf Meta und OpenAPI: erfolgreich
- ohne Header-Zugang: alle geprüften öffentlichen Pfade durch Authentik
  abgefangen
- Registrierung mit einem temporären, acht Zeichen langen und ausschließlich
  kleingeschriebenen Passwort: erfolgreich
- zusätzlicher Unicode-Grenztest mit vier Herz-Darstellungssequenzen, also
  acht Codepoints einschließlich Variationsselektoren: erfolgreich; das Konto
  wurde ebenfalls vollständig gelöscht
- dieselbe Registrierung mit sieben Zeichen: erwartungsgemäß HTTP 400
- temporäres Testkonto anschließend über den vorgesehenen Account-Endpunkt
  gelöscht; Benutzer, Beziehungen/Mandanten, Mitgliedschaften, Sitzungen,
  Perioden und Tageslogs danach jeweils bei 0
- Pangolin-Konfigurations- und Eigentumsprüfung: erfolgreich

Dunjas früheres Konto mit dem technischen Benutzernamen `dune` bleibt wie
beauftragt gelöscht. Es gibt aktuell kein aktives Dunja-Konto und kein aktives
Dunja-Passwort. Die obsolete Passwortdatei wurde zuvor in ein root-only Archiv
verschoben und wird nicht mehr als Zugang verwendet.

## Ausgeführte Tests und Builds

### Backend und OpenAPI

Auf dem finalen 1.0.5-Quellstand wurden Prisma-Erzeugung/-Validierung, ESLint,
Jest, Nest-Build, OpenAPI-Export/-Reproduzierbarkeitsprüfung und Audits
tatsächlich ausgeführt:

- Jest: **19/19 Suites, 83/83 Tests**, erfolgreich
- ESLint: erfolgreich
- Nest-/TypeScript-Build: erfolgreich
- echter PostgreSQL-18-E2E-Lauf: **1/1 Suite, 1/1 durchgehender Test**,
  erfolgreich; sechs Migrationen wurden auf der temporären Datenbank angewendet
- `npm audit` für den vollständigen und den Produktionsbaum: jeweils
  **0 bekannte Schwachstellen**
- OpenAPI-Snapshot und generierter Kotlin-Baum: reproduzierbar aktuell
- OpenAPI-SHA-256:
  `8618f15f759748bafedc7396e8ce4e4591de82b7f22c22fc4afe95b61eece932`
- generierter Kotlin-Client: nach einem Abbruch des 1-GiB-Gradle-Daemons mit
  1,5 GiB erfolgreich kompiliert

Der E2E-Test umfasst Setup/Bootstrap-Sperre, getrennte Beziehungen,
Einladungen, Rollenprüfung, Perioden, Prognose, Freigabeentzug,
Notizverschlüsselung, Tipps/Care Preferences, Refresh-Rotation, Sync-Pull und
-Push, Idempotenz, Soft Deletes, Konflikte, Export/Import und direkte
Cross-Relationship-Angriffsversuche.

### Android

Die finale Android-Prüfkette wurde tatsächlich ausgeführt:

- JVM-Tests: **20/20 Suites, 86/86 Tests**, erfolgreich
- `lintLocalDebug`: 33 Hinweise, **0 Error/Fatal**
- `lintProductionDebug`: 32 Hinweise, **0 Error/Fatal**
- Production-Instrumentierungscode: erfolgreich kompiliert
- Production-Debug-APK: erfolgreich assembliert
- unsignierte Release-APK: erfolgreich assembliert
- beide APK-ZIP-Strukturen: ohne Fehler geprüft

Die Debug-APK ist installierbar:

```text
/root/Lunara/artifacts/lunara-debug.apk
```

- Paket: `de.lunara.app.debug`
- `versionName`: 1.0.5; `versionCode`: 6
- `minSdk`: 26; `targetSdk`/`compileSdk`: 37
- Größe: 24.406.431 Bytes
- SHA-256:
  `193a562553be284f114e932748a14779710367ec8ef8a041e93241296eef48d2`
- APK-v2-Signatur erfolgreich verifiziert
- Debug-Zertifikat SHA-256:
  `f53698206074d4e2b0ff7b6e7b134a58919a358f2054fb7d4ba35df77ac69328`

Die unsignierte Release-APK liegt unter:

```text
/root/Lunara/artifacts/lunara-release-unsigned.apk
```

- Paket: `de.lunara.app`
- `versionName`: 1.0.5; `versionCode`: 6
- Größe: 19.749.539 Bytes
- SHA-256:
  `e104ff2f946d732a6157d4a5d712d37167e1aee8898a9fe17bf4da640ecdf508`
- erwartungsgemäß **nicht signiert** und daher vor Verteilung selbst zu
  signieren

Beim separaten generierten OpenAPI-Kotlin-Client endete der erste
`compileKotlin`-Versuch mit einem Abbruch des auf 1 GiB begrenzten
Gradle-Daemons. Die Wiederholung mit 1,5 GiB war erfolgreich. Beide Android-
APKs wurden anschließend tatsächlich gebaut; kein anderer Dienst wurde dafür
neu gestartet.

Die instrumentierten Compose-Tests wurden mangels angeschlossenem Gerät oder
Emulator **nur kompiliert, nicht ausgeführt**. Reale Keystore-, SQLCipher-,
WorkManager-, Accessibility- und UI-Interaktionen sind deshalb noch auf einem
Android-Gerät zu prüfen.

## Container und produktiver Server

Vor dem Update wurde dieses eng benannte Backup angelegt:

```text
/var/backups/lunara/release-1.0.5-pre-20260813T232500Z/
```

- Verzeichnis `0700`, Dateien `0600`
- PostgreSQL-Custom-Dump: 61.665 Bytes
- Dump-SHA-256:
  `aa45ba8940bc0919e91a9bfb137f1ce7af349406f75fe6dff31ac9e6d747858d`
- Dump mit `pg_restore --list` validiert
- Compose- und Env-Sicherung enthalten; Secrets werden nicht dokumentiert

Vor der Authentik-Migration wurde zusätzlich ein lesbarer PostgreSQL-Custom-
Dump unter
`/var/backups/lunara/infrastructure/authentik-before-lunara-single-app-20260813T181943Z.nS8hcE.dump`
angelegt. Die alten Header-Dateien liegen in einem `0700`-Archiv. Ein Restore
der produktiven Datenbanken wurde bewusst nicht ausgeführt, da dies
destruktiv wäre.

Das finale Image wurde tatsächlich gebaut:

```text
lunara-api:1.0.5
sha256:fe12d3ecf3473d3df7360d649bd868d0b9f285e69317c14cab8d09baff7e3afe
```

Live bestätigt:

- API-Container `f049893c…`: running und healthy
- `/health`: HTTP 200; `/ready`: HTTP 200
- `/api/v1/meta`: Version 1.0.5, `setupRequired: false`,
  `registrationEnabled: true`
- sechs Prisma-Migrationen angewendet
- API als `10001:10001`, read-only Root-Dateisystem, Capability-Drop und
  `no-new-privileges`
- PostgreSQL ohne veröffentlichten Host-Port, weiterhin healthy
- eine zehnminütige Beobachtung mit 30 Stichproben zeigte keinen ungesunden
  Dienst; der freie Arbeitsspeicher lag nie unter rund 1,87 GiB
- die Container-IDs von PostgreSQL, Authentik, Pangolin, Traefik und Emby
  blieben während Build und Update unverändert

Der Bootstrap wird auf diesem eingerichteten Server nicht mehr benötigt.
`setupRequired: false` ist persistent; der produktive Bootstrap-Token wurde aus
der aktiven Lunara-Konfiguration entfernt. Weitere Personen verwenden die
normale Registrierung, wobei jede Tracker-Registrierung eine eigene isolierte
Beziehung erhält.

## Datenschutz- und Sicherheitsgrenzen

- Wer die gemeinsame Header-Auth-URL kennt, kann den äußeren Authentik-Schutz
  passieren. Gesundheitsdaten bleiben durch das zusätzliche persönliche
  Lunara-Konto geschützt. Die gemeinsame URL muss trotzdem wie ein Passwort
  behandelt und bei Verlust in Authentik rotiert werden.
- Ein kompromittierter Server oder gestohlener `APP_MASTER_KEY` kann
  serverseitig verschlüsselte Notizen offenlegen. Datenbankbackup und passender
  Master-Key müssen getrennt, verschlüsselt und gemeinsam wiederherstellbar
  gesichert werden.
- Der interne Hop im privaten Docker-Netz ist nicht zusätzlich TLS-verschlüsselt.
- Kein Zertifikat-Pinning ist erzwungen, damit selbst gehostete Zertifikate
  erneuert werden können.
- Die Android-Instrumentierung wurde nicht auf realer Hardware ausgeführt.
- Die Datei `services/api/core.102` ist ein ignorierter lokaler Core-Dump und
  kein Produktartefakt. Sie wurde ohne ausdrückliche Löschfreigabe nicht
  entfernt.

## Bekannte Funktionsgrenzen

- Android-UIs fehlen noch für aktive Gerätesitzungen, eigene Symptome/Tipps,
  Check-in-Antworten, vollständigen Export/Import, Kontolöschung und Trennen
  einer Beziehung.
- Der Android-Tageslog umfasst die Kernerfassung, aber Notiz,
  Basaltemperatur, Ovulationstest sowie Tageslog-Korrektur/-Löschung sind noch
  nicht vollständig in der UI verfügbar.
- Periodenkorrektur und -löschung funktionieren in „Heute“ und „Verlauf“;
  Kalender-Direktaktionen und Monatwechsel sind noch offen.
- Lokale Benachrichtigungen, ntfy, Biometrie, Widget, Demo-Modus, SSE-Client
  und reduzierte Bewegungen sind noch nicht vollständig umgesetzt.
- Der verschlüsselte Export ist ein AES-256-GCM-geschütztes
  `.lunarabackup`-JSON-Format, kein ZIP. Generischer CSV-Zuordnungsassistent,
  direkter Import verschlüsselter Backups und Import-Undo fehlen.
- Der generierte OpenAPI-Kotlin-Referenzclient wurde reproduzierbar erzeugt;
  die Android-App nutzt zur Laufzeit ihre eigene typisierte Retrofit-Schicht.
- Vollständiges Umbenennen erfordert neben zentralen Branding-Tokens weiterhin
  Anpassungen an Package-ID, Übersetzungen und Vektor-Assets.

Lunara ist damit ein stark abgesicherter und ungewöhnlich vollständiger
self-hosted Kern für Paare. Eine objektive Behauptung, die „beste“ App am Markt
zu sein, wäre ohne unabhängige Vergleichs-, Usability- und Medizinsicherheits-
studie nicht seriös.

## Privates GitHub-Repository

Das private Repository ist:

```text
https://github.com/eliasbruno124-dev/Lunara
```

Die GitHub-Geräteanmeldung des Eigentümers und der private Branch
`agent/authentik-human-access` bestehen. Der vollständig geprüfte 1.0.5-Stand
einschließlich beider APKs wurde als Commit
`3c3ced280203a4108bbf665b1a2b0a63c700c278` erfolgreich gepusht. GitHub
bestätigte denselben Commit für Branch und Tag `v1.0.5`.

Der private Release ist:

```text
https://github.com/eliasbruno124-dev/Lunara/releases/tag/v1.0.5
```

GitHub bestätigte `lunara-debug.apk` mit 24.406.431 Bytes und
`lunara-release-unsigned.apk` mit 19.749.539 Bytes als vollständig hochgeladen.
Der bestehende Draft-Pull-Request 1 wurde auf 1.0.5 aktualisiert. Wegen des
privaten Repositorys müssen Repository, PR und Downloads mit dem berechtigten
GitHub-Konto angemeldet geöffnet werden.

Echte `.env`-Dateien, Authentik-Zugänge, Passwörter, Token, Dumps und
Serverbackups bleiben ausschließlich auf dem Server.

## Erster App-Start

1. Die geprüfte Debug-APK installieren.
2. Die vollständige einmalige Header-Auth-Adresse sicher direkt aus
   `/docker/lunara/lunara-app-headerauth.url` übernehmen. Nicht in Chat,
   GitHub oder QR-Codes kopieren.
3. Die App zeigt danach nur noch `https://lunara.3li361.de/` an.
4. Da Dunjas früheres Konto gelöscht ist, „Registrieren“ wählen und ein neues
   persönliches Konto anlegen. Das Passwort darf 8 bis 128 Zeichen lang sein;
   Großbuchstaben, Ziffern und Sonderzeichen sind nicht verpflichtend.
5. Der Server erzeugt für jede Tracker-Registrierung automatisch eine
   eigene unsichtbare Beziehungsgrenze; ein Haushaltsname ist nicht nötig.
6. In den Einstellungen Freigaben, Care Preferences und die optionalen
   Fertilitätshinweise bewusst konfigurieren.
7. Die permanente Partner-Einladung als Code oder passwortfreien QR anzeigen.
   Sie ist bis zur Verwendung gültig, nur einmal nutzbar und widerrufbar.
8. Nach dem ersten echten Gerätetest ein aktuelles Backup samt getrennt
   gesichertem `APP_MASTER_KEY` kontrollieren.
