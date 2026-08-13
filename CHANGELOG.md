# Changelog

## 1.0.2 – 2026-08-13

- Produktions-Onboarding startet ohne vorausgefüllte Domain; die persönliche
  Authentik-Header-Auth-URL muss bewusst eingegeben werden.
- Onboarding- und rollenbezogene In-Memory-Zustände werden nach Anmeldung,
  Abmeldung und Kontowechsel sicher zurückgesetzt. Dadurch erscheint ein
  bereits abgeschlossener Bootstrap nicht mehr aus einem alten App-Cache.
- Bootstrap-, Benutzername-, Passwort-, Haushalts- und Einladungseingaben
  werden mit feldgenauen, servergleichen Regeln erklärt; falsche
  Anmeldedaten erhalten eine eigene verständliche Meldung.
- Authentik-Cookies werden vor jedem ausdrücklichen Verbindungstest entfernt,
  auch wenn URL und Zugangsdaten unverändert sind.
- Der dynamische Meta-/Bootstrap-Status trägt `Cache-Control: no-store` und
  kann dadurch nicht mehr als veraltete Proxy-Antwort wiederverwendet werden.
- Automatische Synchronisierung nach Anmeldung und lokalen Änderungen,
  beim Öffnen der App, einmal pro Minute im Vordergrund sowie periodisch im
  Hintergrund ergänzt. Gleichzeitige Worker und Refresh-Rotation sind
  serialisiert; dauerhafte eindeutige WorkManager-Aufträge verhindern verlorene
  Trigger und unbeschränkt wachsende Retry-Ketten. Nach acht Fehlversuchen ist
  ein manueller, ausdrücklich ausgelöster Neuversuch erforderlich.
- Partner-Dashboard und heutiger Tipp werden nach erfolgreichen Syncs reaktiv
  aktualisiert; rollenbezogene Cache-Daten sind an die aktuelle User-ID
  gebunden.
- Sicheren Lunara-Passwortwechsel in den Einstellungen ergänzt. Er verlangt
  das aktuelle Passwort, nutzt Argon2id und widerruft alle anderen
  Lunara-Serversitzungen, ohne das getrennte Authentik-App-Passwort zu
  verändern. Offline-Daten auf anderen Geräten bleiben ausdrücklich erhalten.
- Login, Refresh-Rotation und Passwortwechsel werden pro Konto in PostgreSQL
  serialisiert, damit kein paralleler Login mit dem alten Passwort eine neue
  Sitzung nach dem Passwortwechsel anlegen kann.
- Passwortwechsel-Auditereignisse, Rate-Limit, E2E-Abdeckung und aktualisierten
  OpenAPI-/Kotlin-Vertrag ergänzt.

## 1.0.1 – 2026-08-13

- Authentik-Header-Auth nach einem anonymen Verbindungsversuch repariert:
  anonyme Proxy-Cookies unterdrücken den erforderlichen Basic-Header nicht mehr.
- Gateway-Zugangsdaten strikt auf Ursprungsschema, Host und Port des
  konfigurierten Servers begrenzt; Browser-Login-Weiterleitungen werden nicht
  mehr als API-Antwort verfolgt.
- Veraltete Authentik-Cookies bei Server- oder Zugangsdatenwechsel entfernt und
  eine verständliche Gateway-Fehlermeldung ergänzt.
- Eingefügten `/api/v1/meta`-Testpfad automatisch auf die Serverbasis
  normalisiert und das vorausgefüllte URL-Schema im Onboarding verdeutlicht.
- Debug-Signierschlüssel für wiederholbare lokale APK-Updates persistent und
  Git-ausgeschlossen gemacht.

## 1.0.0 – 2026-08-12

- Separates Lunara-Monorepo mit NestJS-/Prisma-API, PostgreSQL 18 und nativer
  Android-Compose-App erstellt.
- Sicheren Bootstrap, getrennte Rollen/Konten, Einladungen,
  Haushaltsisolation, Perioden-/Tageslog, robuste Prognose und granulare
  Partnerprojektion implementiert.
- Offline-Sync, SQLCipher/Keystore, vollständigen lokalen Wipe,
  50 zweisprachige Tipps, Care Preferences, Check-ins und Datenverwaltung
  ergänzt.
- Produktionsnahe Docker-, CI-, Backup-, Restore-, Admin-, Authentik- und
  Pangolin-Konfiguration angelegt.
- HTTPS-only Production-Debug-APK und versionierte OpenAPI-Artefakte gebaut.
- Noch offene Komfort-/Zusatzfunktionen und reale Gerätetests in
  `FINAL_REPORT.md` und `PROGRESS.md` transparent dokumentiert.
