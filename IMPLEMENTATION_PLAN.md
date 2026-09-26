# Lunara – Implementierungsplan

## 1.2.1 – Vorhersage zuerst, natürlichere Partner-Tipps

1. Partner-Startseite in Android und PWA umordnen: geteilter Ausblick vor Tipp.
2. Alle DE/EN-Tipps auf natürlichen Ton, nützlichen Kontext und eine kleine
   passende Handlung redigieren. Öffentlich sichtbare Beschreibungen von
   Flo for Partners dienen als Format-Inspiration, keine Premium-Inhalte
   werden übernommen. Persönliche Wünsche und Freigaben bleiben maßgeblich.
3. Reihenfolge, private Zustände, Auswahl und Texte automatisiert prüfen;
   echte Android-Geräteprüfung ausschließlich auf gehostetem Runner.
4. Signiertes Update mit bestehender Identität bauen, nach engem Backup nur
   Lunara aktualisieren und geprüfte Artefakte privat veröffentlichen.

Keine Änderung der Prognoseformel, Identitätsverknüpfung oder Gesundheitsdaten.

## 1.2.0 – Gestaltung und konkrete Partnerunterstützung

1. Bestehende Funktionsverträge und produktive Quellen vergleichen; keine
   Konten, Zyklusdaten, Verknüpfungen oder Freigaben ändern.
2. Ein eigenständiges Journal-Design umsetzen: warmes Elfenbein, tiefes Petrol,
   redaktionelle Typografie, gezielte Akzente und klare Aktionshierarchie.
3. Android-Einrichtung, Heute, Kalender, Verlauf/Unterstützung und gruppierte
   Einstellungen sowie die responsive Web-App durchgängig überarbeiten.
4. Tipps als konkrete Unterstützungssituationen redigieren; persönliche
   Wünsche, Einwilligung und Datenschutz bleiben maßgeblich. Keine Diagnosen,
   keine aus dem Zyklus abgeleiteten Gefühle, keine behauptete Fachprüfung.
5. Automatische Regressionstests, tatsächliche visuelle Prüfung in beiden
   Themes, DE/EN und Android-Build/Upgrade mit bestehender Signatur.
6. Enges Backup, ausschließlich Lunara-API aktualisieren, Dateninvarianten
   prüfen und getestete Artefakte privat ohne unnötige CI-Mails veröffentlichen.

Die medizinische Vorhersage wird für dieses Gestaltungsupdate nicht verändert.
Ein engeres Zeitfenster wäre ohne neue Evidenz keine Qualitätsverbesserung.

## 1.1.6 – Verknüpfung, verständlicher Status und bessere Unterstützung

1. Produktive Mitgliedschaften ausschließlich lesend prüfen; Anzeigeprobleme,
   fehlende Freigaben und tatsächliche Trennung unterscheiden. Keine Konten
   löschen, Daten zusammenführen oder Freigaben ungefragt erweitern.
2. Partnerverknüpfung unabhängig von Prognose und Synchronisationsstatus laden
   und verständlich darstellen; relevante Regressionsfälle automatisieren.
3. Partner-Tipps inhaltlich und hinsichtlich Auswahl, Abwechslung, Freigaben
   und Care Preferences überarbeiten; keine Gefühle aus Zyklusphasen ableiten.
4. Missverständliches „Offline“ ersetzen, App-/Serverversion klar trennen,
   einen freiwilligen externen PayPal-Unterstützungslink zentral konfigurieren.
5. API-/Android-Tests, Lint, signiertes Upgrade und visuelle Prüfung soweit
   ausführbar; danach enges Backup, API-only-Deployment und privater Release.
6. Rückmeldung zur Spanne 9.–16. prüfen: Startfenster von Periodendauer
   unterscheiden, Datenbasis und Unsicherheit erklären; keine künstliche
   Verengung oder nachträgliche Anpassung an einen bereits bekannten Start.

Aktueller Nachtrag: 21. September 2026, Synchronisierungsupdate 1.1.5.

## Reihenfolge für 1.1.5

1. Bestehende lokale und produktive Quellen ausschließlich lesend vergleichen;
   Geräte- und Beziehungsdaten nicht verändern.
2. Automatische Refresh-Wiederholungen bei verlorenen Antworten absichern:
   dauerhafte clientseitige Anfrage-UUID, verschlüsselter serverseitiger
   Wiederholungsbeleg, weiterhin gültige Geräte-/Identitätsprüfung.
3. Bereits ungültige Anmeldungen im bestehenden Konto erneuerbar machen,
   ohne lokale Daten, Queue, Cursor, Schlüssel oder Freigaben zu löschen.
4. Partneransicht unabhängig von optionalen Tipps aktualisieren und veralteten
   Stand kenntlich machen; Netzfehler automatisch mit Backoff wiederholen.
5. Unabhängiger Sicherheitsreview, echte PostgreSQL-Integration, Android-
   Unit-Tests/Lint/Build und Vergleich mit dem bisherigen Signierzertifikat.
6. Enges Serverbackup, ausschließlich API aktualisieren, Healthchecks prüfen
   und einen privaten GitHub-Release mit signiertem Upgrade veröffentlichen.

Keine allgemeine Authentik-Umgehung, keine Wiederbelebung widerrufener Geräte,
keine Änderung bestehender Datenschutzfreigaben. Android-Hintergrundarbeit
bleibt vom Betriebssystem abhängig. Ein bereits widerrufenes Konto benötigt
einmalig eine bewusste Anmeldung; gewöhnliche Erneuerungen erfolgen automatisch.

Die folgenden Architekturabschnitte dokumentieren die fortgeführte Basis 1.1.0.

## Zielbild

Lunara ist ein Monorepo für:

- eine native Android-App mit `TRACKER`-/`PARTNER`-Ansichten und einem
  vollständig lokalen Solomodus;
- eine installierbare PWA für Self-hosted Browsernutzung;
- eine NestJS-/Prisma-/PostgreSQL-API mit Offline-Sync und strikt
  freigabefilterter Partnerprojektion;
- Docker-Compose-Deployment hinter Traefik direkt oder optional hinter
  Authentik/Pangolin.

Alle Kernfunktionen arbeiten ohne Firebase, Google Play Services, Werbung,
Analyse oder eine externe Cloud. Lunara ist kein Medizinprodukt und die
Fertilitätsschätzung keine Verhütungsmethode.

## Architekturentscheidungen

### Gemeinsamer Serverkern

- Node.js 24 LTS, TypeScript, NestJS 11 und Prisma 7
- PostgreSQL 18, UUIDs und lokales Kalenderdatum für Periodendaten
- REST unter `/api/v1`, OpenAPI-Snapshot und Kotlin-Referenzclient
- kurze Access-Tokens, rotierende gehashte Refresh-Tokens, Argon2id
- clientseitige UUIDs, Soft Deletes, Versionen, Cursor-Sync und idempotente
  Mutationsreceipts
- PostgreSQL-Transaktionen und Constraints als autoritative Invarianten
- interne `Household`-UUID als Mandantengrenze, aber kein sichtbares
  Haushalts-Pflichtkonzept

### Android

- Kotlin, Compose/Material 3, Hilt, Retrofit/OkHttp, Coroutines/Flow
- Room mit SQLCipher, Android Keystore und WorkManager
- minSdk 26, targetSdk 36, compileSdk 37, DE/EN sowie Hell/Dunkel
- servergebundener Modus mit Offline-Queue und lokaler Solomodus ohne
  Netzwerk-/Syncpfad
- portables AES-256-GCM-Backup über Android Storage Access Framework
- Authentik über AppAuth, Systembrowser, PKCE S256 und Production App Link

### Web/PWA

- TypeScript, Vite, Web App Manifest und Service Worker
- DE/EN, Hell/Dunkel, rollenabhängige Ansichten, Offline-Queue und
  installierbarer Kalender
- lokaler Solomodus als passphrasenverschlüsselter AES-GCM-Vault; Servermodus
  und Solozustand bleiben getrennt
- origin-gebundenes IndexedDB und Web-Crypto-Kapselung der Session-Tokens
- same-origin API, OIDC Authorization Code/PKCE und kein API-Cache im Service
  Worker

### Gateway

- `direct`: Traefik-Router aus `docker-compose.direct.yml`
- `authentik`: Authentik Proxy plus optionales OIDC; nur Meta und Android Asset
  Links sind öffentlich
- moderne Authentik-Anfragen besitzen äußeren Authentik-Bearer und inneren
  Lunara-Bearer; Lunara-Rechte bleiben serverseitig
- `scripts/compose.sh` ist der gemeinsame Modus-Router für Start, Migration,
  Update, Restore und Adminoperationen

## Ausgeführte Reihenfolge

1. **Repository und Betriebssicherheit** – Monorepo, Gitignore, Env-Beispiel,
   Containergrenzen, Secret-Scan und Dokumentationsstruktur. `abgeschlossen`
2. **Datenmodell und Auth** – Prisma-Schema, Bootstrap, getrennte Konten,
   Einladungen, Rollen, Sessions und Audits. `abgeschlossen`
3. **Tracking und Freigaben** – Perioden, Tageslogs, Care Preferences,
   Sharing, Partnerdashboard und Check-ins. `abgeschlossen`
4. **Prognose** – robuste, deterministische Bereichsberechnung mit
   Sicherheitsgrenzen und optionalem fruchtbarem Fenster. `abgeschlossen`
5. **Offline-Sync** – Room, Mutation Queue, WorkManager, Cursor,
   Idempotenz, Soft Deletes und Konflikte. `abgeschlossen`
6. **Android-Produktfluss** – Onboarding, Rollenansichten, Kalender,
   Periodenkorrektur/-löschung, Verlauf, Einstellungen und lokale
   Benachrichtigungen. `abgeschlossen für den Kern`
7. **Solo und Gerätewechsel** – lokale Identität, keine Serveraufrufe,
   lokale Prognose, SAF-Export/-Import, strikte Validierung und
   Import-Journal. `abgeschlossen`
8. **PWA** – installierbare Weboberfläche, Self-hosted Auth, Rollen,
   Kalender, Offline-Queue und Cache-Isolation. `abgeschlossen für den Kern`
9. **Optionales Authentik-OIDC** – Providerprüfung, explizites Linking,
   Dual-Header, Tokenrefresh, Gruppenbegrenzung, Meta-Discovery und
   verifizierter Android App Link. `abgeschlossen im Code und Provisioner`
10. **Build und Release** – Backend/PWA, Docker, Android Unit/Lint,
    Instrumentierungskompilierung, APK/AAB und Signaturprüfung. `abgeschlossen;
    reale Gerätetests bleiben offen`
11. **Live-Rollout** – enges Backup, Migration, nur Lunara-Dienste ersetzen,
    Authentik anwenden, Identität gezielt verknüpfen und Gateway/App Link
    prüfen. `zum Stand dieses Dokuments noch nicht als 1.1.0 verifiziert`
12. **Private Veröffentlichung** – Secret-Scan, ein gebündelter Commit/Push und
    Release-Artefakte. `nach finaler Live-/Artefaktprüfung`

## Release-1.1.0-Abnahme

Server/PWA:

- Prisma generieren und validieren
- ESLint/TypeScript, Jest/Vitest und Builds
- OpenAPI reproduzierbar prüfen
- echte PostgreSQL-18-E2E-Strecke mit allen acht Migrationen
- Multi-Stage-Container bauen und Non-root/Health prüfen
- Direct- und Authentik-Routing getrennt prüfen

Android:

- beide Flavor-JVM-Suites
- beide Debug-Lints
- Production-Instrumentierungsquellen kompilieren
- Production-Debug-APK, signierte/unsignierte Release-APK und
  signierte/unsignierte AAB bauen
- Manifest, Version, SDK-Stufen, ZIP-Struktur und Signaturen prüfen
- Production App Link mit dem real ausgelieferten Signing-Zertifikat auf
  einem Gerät verifizieren

Sicherheit/Betrieb:

- gezielter und High-Entropy-Secret-Scan
- keine Secrets in Env-Beispiel, Artefakten, Logs oder Git
- eng benannte Dumps vor Datenbank-/Authentik-Mutation
- Health, Readiness, RestartCount und relevante Logs nach Änderungen
- keine Änderung an Portainer-Hauptstack oder fremden Diensten

Die tatsächlich erreichten Ergebnisse stehen ausschließlich in
`TESTING.md` und `FINAL_REPORT.md`; dieser Plan allein ist kein Erfolgsnachweis.

## Bewusst offene Erweiterungen

- Android-Instrumentierung auf echten Geräten/Emulatoren ausführen
- Biometrie, Widget, ntfy und Android-SSE-Client fertigstellen
- vollständige Android-UIs für Gerätesitzungen, eigene Symptome/Tipps,
  Check-in-Antworten und serverseitige Export-/Import-/Kontolöschungsflows
- generischen CSV-Zuordnungsassistenten und Import-Undo ergänzen
- PWA-Browsermatrix und Offline-/Upgradepfad auf realem iOS/iPadOS prüfen
- Branding/Package-ID vollständig über einen Generator umbenennbar machen
- Lizenz, Publisher, Support, Datenschutzseiten und Play-Store-Erklärungen
  rechtlich/geschäftlich festlegen
- Bluetooth-/WLAN-Peer-to-Peer-Sync bleibt bewusst außerhalb von Version 1;
  Partner-Sync verwendet den überprüfbaren Self-hosted Server
# Einladungskorrektur 1.1.4 – 8. September 2026

1. Live-Stand und bestehende lokale Änderungen getrennt sichern; nur Lunara ändern.
2. Einladung über eine kleine öffentliche `/invite`-Landeseite vor dem Authentik-Redirect im Browserspeicher erhalten. Alle Konten-/Gesundheitsendpunkte bleiben geschützt.
3. Bereits angemeldete, leere Einzelkonten erst nach ausdrücklicher Bestätigung serverseitig als Partner verbinden. Vorhandene Gesundheitsdaten und individuelle Einstellungen verhindern einen automatischen Wechsel.
4. Gleicher Ablauf in Android: Code oder QR in Einstellungen, Bestätigung nach OIDC, neue Rolle laden und alten lokalen Beziehungscache verwerfen.
5. Echte PostgreSQL-Integrationstests, Clienttests, Sicherheitsreview, Container- und signierten Android-Build ausführen; anschließend nur Lunara aktualisieren.
