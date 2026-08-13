# Changelog

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
