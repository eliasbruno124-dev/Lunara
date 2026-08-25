# Lunara – Fortschritt

Stand: 25. August 2026, Release 1.1.0 veröffentlicht

## 1.1.0 umgesetzt

### Android Solo und Gerätewechsel

- [x] Beim ersten Start ist **Nur auf diesem Gerät** prominent wählbar.
- [x] Der Solomodus benötigt kein Konto, keine Domain und keinen Server.
- [x] Stabile zufällige lokale Tracker-ID; keine Server-, Auth- oder
  Sync-Aufrufe im `LOCAL`-Modus.
- [x] SQLCipher/Keystore, lokaler Kalender, Periodenerfassung,
  Korrektur/Löschung, Tageslogs, Verlauf, Prognose und Benachrichtigungen.
- [x] Portabler SAF-Export/-Import für Perioden, Tageslogs, Profil und
  Einstellungen.
- [x] Format: PBKDF2-HMAC-SHA256 mit 310.000 Iterationen plus AES-256-GCM;
  strikte Größen-, Schema-, Datums-, UUID- und Überschneidungsprüfung.
- [x] Crash-tolerantes Import-Journal: nach Room-Commit unterbrochene
  Einstellungswiederherstellung wird idempotent vollendet.
- [x] Android-Cloudbackup und systemseitiger Gerätetransfer für alle App-
  Domänen ausgeschlossen; Gerätewechsel erfolgt bewusst über die
  passphrasenverschlüsselte Datei.
- [x] Android-Systemdateidialog erlaubt lokale Ordner und installierte Anbieter
  wie Google Drive oder Nextcloud, ohne Google-SDK oder automatischen Upload.

### Kalender und Prognose

- [x] Monatswechsel per horizontalem Wischen statt Zurück-/Weiter-Buttons.
- [x] Monate bis fünf Jahre in der Zukunft sichtbar; Zukunftstage nicht als
  tatsächliche Periode editierbar.
- [x] Tatsächliche Perioden, Zwei-Tipp-Auswahl und geschätzter Zeitraum sind
  visuell und semantisch unterscheidbar; tatsächliche Daten dominieren.
- [x] Wählbare Start-/Endtage, offene Periode, Neu-Auswahl, Ende entfernen und
  bestätigtes Löschen bleiben offlinefähig.
- [x] Algorithmus `lunara-robust-v2`: bis zu zwölf aktuelle Zyklen, gewichteter
  Median, robuste MAD-Ausreißerbehandlung, transparente Erklärung,
  Mindestunsicherheit und stale/fail-closed Verhalten.
- [x] Geschätztes fruchtbares Fenster nur nach Opt-in, ausreichenden Daten und
  in zulässigem Sondermodus; weiterhin keine Ovulationsbestätigung oder
  Verhütungsmethode.
- [x] Kotlin- und TypeScript-Implementierung anhand gemeinsamer
  Referenzvektoren geprüft.

### PWA

- [x] Installierbare DE-/EN-Web-App mit Hell/Dunkel, Tracker-/Partneransicht,
  Kalender, Prognose und Partner-Tipp.
- [x] IndexedDB-Offlinedaten, idempotente Mutation Queue und automatische
  Synchronisierung.
- [x] Kontospezifische Caches und Queues werden bei Identitätswechsel, Logout
  und 401 getrennt beziehungsweise gelöscht.
- [x] Service Worker cached keine API-, Health-, Ready- oder OpenAPI-Pfade und
  verwendet sie nicht als SPA-Fallback.
- [x] Portable Backupkompatibilität mit dem Android-Solodatenformat.
- [x] PWA-Solomodus mit AES-256-GCM-Vault, 600.000 PBKDF2-Iterationen und nur
  im Seitenspeicher gehaltenem Schlüssel; Klartext-Legacydaten werden entfernt
  und der Vault sperrt bei versteckter Seite, `pagehide` oder 15 Minuten
  Inaktivität.
- [x] PWA-Bundle wird im API-Multi-Stage-Image gebaut und als SPA nur auf
  zulässigen Browserpfaden ausgeliefert.

### Optionales Authentik-OIDC und Direct-Modus

- [x] `GATEWAY_MODE=direct` mit Traefik-Override; Lunara funktioniert ohne
  Authentik ausschließlich mit eigenen Konten.
- [x] `GATEWAY_MODE=authentik` mit öffentlicher cachefreier Metaerkennung und
  öffentlicher `assetlinks.json`; alle anderen Pfade bleiben geschützt.
- [x] OIDC Authorization Code + PKCE S256 + State/Nonce im Systembrowser;
  kein WebView-Login und kein Client Secret in APK/PWA.
- [x] Production-OIDC verwendet unabhängig von der Self-hosting-Domain immer
  den festen verifizierten Publisher-App-Link. Production-Provisioning
  registriert nur Android-HTTPS und PWA-HTTPS, keine Custom Schemes.
- [x] Custom Scheme ausschließlich im Android-`local`-Flavor für einen
  getrennten Entwicklungsprovider mit eigener client_id; gegen Production-
  Authentik bewusst nicht nutzbar. Production einschließlich Production-Debug
  besitzt keinen Custom-Scheme-Intent-Filter.
- [x] Äußerer Authentik-Bearer in `Authorization`, innerer persönlicher
  Lunara-Bearer in `X-Lunara-Authorization`, jeweils same-origin.
- [x] Authentik-Access-Token wird vor dem Proxyaufruf bei Bedarf erneuert;
  OIDC-Lunara-Refresh verlangt dieselbe externe Identität.
- [x] Serverprüfung von Issuer, Audience, Subject, `iat`, `exp`, `azp`,
  Algorithmus und JWKS; kein Auto-Provisioning und kein E-Mail-Linking.
- [x] Selbstverknüpfung nach Passwort-Reauth sowie bestätigungspflichtige
  Adminmigration mit optionalem Deaktivieren des Passwortlogins.
- [x] Fail-closed Authentik-Provisioner mit exakter Gruppe, expliziter
  Zustimmung, validiertem Vorab-Dump und root-only öffentlicher OIDC-Env.
- [x] `scripts/compose.sh` lädt Direct-Override beziehungsweise Authentik-OIDC-
  Env. Backup, Update, Restore und Admin delegieren ebenfalls an diesen
  Wrapper.

### Android Release/Play

- [x] VersionCode 11, VersionName 1.1.0, minSdk 26, targetSdk 36,
  compileSdk 37.
- [x] Optionaler externer Upload-Key ausschließlich über die vier
  `LUNARA_UPLOAD_*`-Umgebungsvariablen; Teilkonfiguration bricht ab.
- [x] Production-Debug/Testinstallation, signierte und unsignierte Release-APK
  sowie signiertes und unsigniertes AAB tatsächlich gebaut. Die signierte
  Release-APK ist die empfohlene Nutzer-/App-Link-Variante.
- [x] ZIP-/Bundle-Struktur, Manifest und erwartete Signaturzustände geprüft.
- [x] Upload-Key und Passwörter bleiben serverlokal; Play App Signing und der
  getrennte Play-App-Signing-Key sind dokumentiert.

## Verifiziert

- [x] Backend: 26 Jest-Suites und 177 Unit-Tests erfolgreich.
- [x] PWA: 11 Vitest-Dateien und 39 Tests erfolgreich; TypeScript/Lint,
  Produktionsbuild, vollständiges und Production-npm-Audit ebenfalls
  erfolgreich, beide Audits mit 0 gemeldeten Schwachstellen.
- [x] Backend plus PWA: insgesamt 216 erfolgreiche Unit-/Komponententests;
  PostgreSQL-E2E separat 1/1.
- [x] Backend-/PWA-Typecheck, Lint und Builds erfolgreich.
- [x] PostgreSQL 18: alle acht Migrationen und 1/1 vollständiger Core-E2E-
  Ablauf erfolgreich.
- [x] Android: je Flavor 36 JVM-Suites und 174 Tests, insgesamt 348
  Testausführungen, ohne Fehler.
- [x] Android-Lint: Local 0 Fehler/64 Warnungen/+1 Information; Production
  0 Fehler/63 Warnungen/+1 Information.
- [x] Production-Instrumentierungsquellen erfolgreich kompiliert.
- [ ] Instrumentierte Android-Tests auf Emulator/realem Gerät ausgeführt.
- [x] Android APK/AAB gebaut, Signaturzustände verifiziert und alle fünf
  finalen Hashes mit `artifacts/SHA256SUMS` erfolgreich geprüft.
- [x] Multi-Stage-Build mit API und PWA erfolgreich.
- [x] Release 1.1.0 auf dem produktiven Server ausgerollt: achte Migration,
  Non-root/read-only API gesund, öffentliche cachefreie Meta-/Asset-Link-
  Verträge, geschützte Root-/Me-Pfade, Header-Auth/PWA, Authentik-OIDC/PKCE,
  Pangolin und erhaltene Dunja-Daten nach der Dune-Verknüpfung geprüft.
- [x] Frisches Vorab-Backup mit Prüfsummen und `pg_restore --list` validiert;
  Datenbankdump zusätzlich in einem isolierten PostgreSQL-18-Container real
  wiederhergestellt und die gesicherten Kernzählwerte geprüft.
- [x] Privater GitHub-Release `v1.1.0` mit signierter APK, Debug-APK,
  Play-Upload-AAB, unsigned Referenzartefakten und Prüfsummen veröffentlicht;
  Backend-, Android-, Dependency- und Container-Sicherheits-CI grün.

## Kernumfang aus 1.0.x weiterhin vorhanden

- [x] Persistenter Bootstrap, optional beliebig viele isolierte Tracker-
  Beziehungen und genau ein Partner je Beziehung.
- [x] Eigene Zugangsdaten, Argon2id, rotierende Refresh-Tokens,
  Gerätesitzungen, Rate Limits und generische Loginfehler.
- [x] Granulare Freigaben, privater Tag, Freigabepause und serverseitig
  gefiltertes Partnerdashboard.
- [x] 71 respektvolle DE-/EN-Tipps, Care Preferences und symptomabhängige
  Auswahl nur bei Freigabe.
- [x] Perioden/Tageslogs, eigene Symptome, verschlüsselte Notizen,
  Soft Deletes, Cursor-Sync, Konflikte und idempotente Offline-Mutationen.
- [x] Automatische Android-Synchronisierung nach Login/Mutation, beim Öffnen,
  im Vordergrund und sparsam via WorkManager; manueller Sync zusätzlich.
- [x] Lokale konfigurierbare Erinnerungen ohne Firebase.
- [x] Server-JSON/CSV/verschlüsselter Export, Importvorschau, Kontolöschungs-
  API, Admin-CLI, Backup/Restore/Update und CI.

## Offen nach 1.1.0

- [ ] Reale Android-Gerätematrix einschließlich App Link, getrenntem lokalem
  Entwicklungs-OIDC, Keystore, SQLCipher, WorkManager, Rotation und TalkBack
  ausführen.
- [ ] PWA auf unterstützten Safari-/iOS-/iPadOS- und Chromium-Versionen testen.
- [ ] Android-UIs für aktive Sitzungen, eigene Symptome/Tipps,
  Check-in-Antworten und vollständige Serverexport-/Import-/Löschflows
  fertigstellen.
- [ ] Biometrische App-Sperre, Widget, ntfy, Android-SSE-Client und reduzierte
  Bewegungen fertigstellen.
- [ ] Generischen CSV-Zuordnungsassistenten und Import-Undo ergänzen.
- [ ] Lizenz, Publisher, Markenprüfung, Support, öffentliche Datenschutz- und
  Löschseiten sowie Play-Console-Erklärungen entscheiden.
- [ ] Bluetooth-/WLAN-Peer-to-Peer-Sync ist nicht implementiert und wird nicht
  als vorhandene Funktion vermarktet.
