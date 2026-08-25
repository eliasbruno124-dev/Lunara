# Commercial Readiness und Self-hosting

Dieses Dokument beschreibt, wie Lunara als seriöses Self-hosted-Produkt und
Android-/Web-Angebot ausgeliefert werden kann. Es ist weder eine Behauptung
vollständiger Marktreife noch Rechts-, Steuer- oder Medizinprodukteberatung.
Jeder Release benötigt die technischen Nachweise aus `FINAL_REPORT.md` und
eine separate geschäftliche/rechtliche Freigabe.

## Verständliche Produktvarianten

| Variante | Speicherort | Konto/Server | Übertragung auf neues Gerät | Partnerzugriff |
| --- | --- | --- | --- | --- |
| Solo lokal | verschlüsselte Android-Datenbank auf einem Gerät | kein Konto, kein Server | manuell verschlüsselte Lunara-Backupdatei | nein |
| Paar Self-hosted | lokaler Android-Cache plus eigene Lunara-Instanz | getrennte Konten auf eigenem Server | nach Anmeldung und Sync; Serverbackup bleibt Betreiberaufgabe | granular freigegebene Ansicht |
| PWA Solo | passphrasenverschlüsselter Browser-Vault | kein Konto, kein Server | portables Lunara-Backup | nein |
| PWA Self-hosted | Browsercache plus eigene Lunara-Instanz | dieselbe API und dieselben Konten | servergebunden; Browsercache allein ist kein Backup | rollenabhängig wie vom Server erlaubt |

Der Solo-Modus ist der niedrigste Einstieg: Es werden keine Domain, kein
Docker und kein Konto benötigt. Die Daten bleiben auf dem Android-Gerät. Weil
Android-Systembackup für Lunara bewusst deaktiviert ist, gibt es **keine
automatische Google-Cloud-Wiederherstellung**. Der vorgesehene Umzug ist ein
expliziter, verschlüsselter Export:

1. Auf dem alten Gerät unter Einstellungen ein portables Lunara-Backup
   erstellen und eine eigene starke Passphrase vergeben. Der Android-
   Systemdateidialog kann lokal, USB oder einen installierten Anbieter wie
   Google Drive/Nextcloud wählen; Lunara integriert dafür kein Google-SDK.
2. Die Datei über einen selbst gewählten sicheren Weg auf das neue Gerät
   übertragen. Der Übertragungsdienst kann eigene Datenverarbeitung haben.
3. Auf dem neuen Gerät Solo-Modus wählen, Backup importieren und Einträge,
   Einstellungen sowie Prognosegrundlage prüfen.
4. Erst danach alte Datei oder altes Gerät löschen. Eine zusätzliche
   verschlüsselte Kopie schützt vor Geräteverlust.

Die Backupdatei ist Keystore-unabhängig und passphrasenverschlüsselt. Die
Passphrase wird nicht von Lunara gespeichert und kann nicht wiederhergestellt
werden. Verlust der Passphrase bedeutet Verlust des Backupzugriffs. Der
manuelle Export ist kein laufender Cloud-Sync und keine Garantie gegen ein
defektes Gerät vor dem nächsten Export. Der Import validiert den vollständigen
Inhalt vor dem Ersetzen und verwendet ein persistentes Journal, um eine nach
dem Datenbankcommit unterbrochene Einstellungswiederherstellung beim nächsten
Start idempotent zu vollenden.

Im Paarmodus betreibt die Kundschaft die API, PostgreSQL, Domain, TLS, Backups
und Updates selbst oder beauftragt dafür einen Dienstleister. Android und die
installierbare PWA greifen auf dieselbe rollenprüfende API zu. So kann eine
Partnerperson auch über einen kompatiblen iPhone-/iPad-Browser teilnehmen,
ohne dass die PWA als native iOS-App dargestellt wird. Browser-
Benachrichtigungen, Hintergrundlaufzeit und lokaler Schutz können sich von
Android unterscheiden und müssen je Plattform dokumentiert werden. In 1.1.0
sind PWA-Screenshot-Schutz und Browserbenachrichtigungen nicht ausführbar;
importierte Vorgaben bleiben verschlüsselt erhalten, werden aber als nicht
anwendbar angezeigt. Der Solo-Vault sperrt bei versteckter Seite, `pagehide`
oder 15 Minuten Inaktivität und verlangt danach erneut die Passphrase.

Authentik bleibt optional. Der portable Standard ist ein direkter Traefik-
Modus mit Lunara-Konten. Bei aktiviertem Authentik läuft persönlicher OIDC-
Login mit PKCE über den Systembrowser. Die Production-/Play-App verwendet
unabhängig von der Self-hosting-API-Domain den festen verifizierten Publisher-
App-Link. Eigene Distributionen bauen mit einem eigenen App-Link-Host und
Signing-Zertifikat. Ein Custom Scheme existiert ausschließlich im Android-
`local`-Flavor für einen getrennten Entwicklungsprovider und darf nicht am
Production-OIDC-Client registriert werden.

Eine direkte Bluetooth- oder lokale WLAN-Kopplung ist aktuell kein
vermarktbarer Funktionsumfang und darf nicht beworben werden. Peer-to-Peer-
Sync würde eigenständige Geräteauthentifizierung, Schlüsselrotation,
Konfliktauflösung, Hintergrundlimits und Verlust-/Widerrufsabläufe erfordern.
Der jetzige klare Umfang ist Solo plus manueller Transfer oder Self-hosted
Server plus Sync.

## GitHub als Auslieferungskanal

Ein privates GitHub-Repository eignet sich für Entwicklung und kontrollierten
Kundenzugang, ist aber kein anonym erreichbarer Downloadshop: Private Release-
Links funktionieren nur für berechtigte GitHub-Konten. Vor Vermarktung ist
deshalb ein Distributionsmodell festzulegen:

- **Privates Quellrepository:** Kundinnen und Kunden werden gezielt einer
  Organisation/einem Team zugeordnet; Releases und Supportzugang sind an eine
  Berechtigung gebunden.
- **Öffentliches Distributionsrepository:** nur freigegebene Images,
  Prüfsummen, SBOM und Dokumentation sind öffentlich; der Quellcode kann privat
  bleiben. Nutzungsrechte müssen trotzdem ausdrücklich geregelt sein.
- **Öffentliches Quellrepository:** erst nach Lizenz-, Secret-, Historien- und
  Markenprüfung. „Source available“ und „Open Source“ sind nicht dasselbe.
- **Eigener Download-/Registry-Kanal:** besser für zahlende Kundschaft,
  Zugangsverwaltung und Supportverträge; GitHub kann weiter die Entwicklung
  hosten.

In diesem Repository wird absichtlich keine Lizenzentscheidung vorweggenommen
und keine `LICENSE` angelegt. Ohne ausdrückliche Lizenz bestehen für Dritte
nicht automatisch die nötigen Rechte zum Kopieren, Ändern, Betreiben oder
Weiterverkaufen. Vor der ersten Weitergabe muss der Betreiber bewusst eine der
folgenden Richtungen auswählen und passend prüfen lassen:

- OSI-konforme Open-Source-Lizenz plus optional bezahlter Support/Hosting;
- Source-available-Lizenz mit klar definierten Nutzungsgrenzen;
- proprietäre Endkunden-/Self-hosting-Lizenz;
- Dual Licensing für Community und kommerzielle Nutzung.

Die Entscheidung gehört dem künftigen Publisher. Dasselbe gilt für kostenlos,
Einmalkauf, Abonnement, bezahlte Updates, Supportvertrag oder Managed Hosting.
Preise, Steuern, Widerruf, Gewährleistung, Play-Billing und lokale
Verbraucherschutzpflichten sind vor Verkauf gesondert zu klären.

## Branding und geistiges Eigentum

- Produktname, Publishername, Domain, Supportadresse, Farben, Logo und Texte
  müssen vor Veröffentlichung als zusammenhängende Marke festgelegt werden.
- Vor kommerzieller Nutzung sind Namens- und Markenrecherche in den
  Zielmärkten erforderlich. Dieses Repository belegt keine Markenfreiheit.
- Eigenes Logo, eigene Screenshots und eigene Texte verwenden; keine Assets,
  Formulierungen oder visuelle Gesamtwirkung kommerzieller Perioden-Apps
  übernehmen.
- App-ID `de.lunara.app` und Signing-Identität sind nach Veröffentlichung
  langfristige Produktidentitäten. Änderungen erfordern in der Regel eine neue
  App statt eines normalen Updates.
- Branding-Tokens sollen zentral bleiben. White-Label-Builds benötigen
  getrennte Paket-IDs, Schlüssel, Datenschutztexte, OIDC-Redirects und
  Supportverantwortung.

## Release- und Lieferkette

Ein kommerzieller Release ist eine nachvollziehbare Einheit aus Git-Commit,
Android-AAB/APK, Containerimage, Web-Bundle, Datenbankschema, Dokumentation und
Prüfergebnissen. Mindeststandard:

- SemVer, Changelog, Upgrade-/Rollback-Hinweise und API-/Client-
  Kompatibilitätsmatrix;
- signierte Android-Artefakte, unveränderliche Container-Digests und SHA-256-
  Prüfsummen;
- serverlokaler Upload-Key, von Play verwalteter separater App-Signing-Key und
  veröffentlichter App-Link-Fingerprint des tatsächlich ausgelieferten
  Zertifikats;
- SBOM im CycloneDX- oder SPDX-Format für Android, Node/Web und Container;
- automatischer Secret-, Abhängigkeits-, Lizenz- und Container-Scan;
- reproduzierbarer Build soweit praktisch, geschützte Release-Umgebung und
  Vier-Augen-Freigabe für Produktion;
- Datenbankmigration zunächst auf restauriertem Backup testen; keine
  automatisch destruktiven Updates;
- definierter Zeitraum, in dem alte App-/Serverkombinationen unterstützt
  werden, sowie klarer End-of-Life-Prozess.

Die SBOM ist eine Bestandsaufnahme, keine Sicherheitsgarantie. Jede
Abhängigkeit muss zusätzlich auf Lizenzverträglichkeit und bekannte
Schwachstellen geprüft werden. Debug-Keys, `.env`, Authentik-Credentials,
Signierschlüssel, Datenbankdumps und echte Gesundheitsdaten dürfen nie in
GitHub-Releases gelangen.

## Betrieb, Support und Sicherheit

Für Self-hosting müssen die vorhandenen Deployment-, Backup-, Restore- und
Updateanleitungen für eine frische Debian-12-Umgebung reproduzierbar sein.
Produktionsimages werden versioniert statt `latest` verteilt. Jede
unterstützte Version benötigt:

- dokumentierte Mindestanforderungen, Domain-/TLS-Schema und Healthchecks;
- sichere Secret-Erzeugung und Erklärung, dass `APP_MASTER_KEY` für die
  Wiederherstellung verschlüsselter Notizen unverzichtbar ist;
- Upgradepfad mit Backup, Migration, Verifikation und Rollbackentscheidung;
- Supportkanal, Reaktionsziel und klare Grenze zwischen Lunara-Support und
  individueller Reverse-Proxy-/DNS-/Docker-Administration;
- veröffentlichtes Sicherheitskontaktverfahren, Schweregrade,
  Patchprozess und unterstützte Versionslinie;
- regelmäßige Wiederherstellungstests. Ein vorhandener Dump allein ist kein
  bestätigtes Backup.

`SECURITY.md`, `PRIVACY.md`, `DEPLOYMENT.md`, `TESTING.md` und
`FINAL_REPORT.md` müssen pro Release konsistent sein. Aussagen wie „keine
Telemetrie“ sind nur für den von Lunara kontrollierten Code und nur nach einem
aktuellen SDK-/Traffic-Audit zulässig. Google Play, das Betriebssystem,
Authentik, Reverse Proxy, DNS/CDN und der jeweilige Serverbetreiber haben
eigene Protokollierung und Datenschutzpflichten.

## Datenschutz-Verantwortung im Self-hosting

Das Produkt muss technisch und textlich unterscheiden:

- Der **Publisher** verantwortet App-Binary, Store-Angaben, eingebaute SDKs,
  Updatekanal und seine Supportprozesse.
- Der **Instanzbetreiber** bestimmt Serverstandort, Konten, Backups,
  Proxy-/Authentik-Logs, Aufbewahrung und Zugriffsberechtigte.
- Die **trackende Person** bestimmt die granularen Partnerfreigaben.
- Eine **Partnerperson** darf nur die serverseitig freigegebene Projektion
  erhalten.
- Ein gewählter Datei-, Mail- oder Cloud-Dienst verarbeitet möglicherweise
  eine manuell exportierte Backupdatei nach seinen eigenen Bedingungen.

Welche rechtlichen Rollen daraus in einem konkreten Land entstehen, muss der
Betreiber mit qualifizierter Beratung festlegen. Weder Self-hosting noch
Verschlüsselung beseitigt automatisch Informations-, Lösch- oder
Aufbewahrungspflichten.

## Offene Betreiberentscheidungen vor Verkauf

| Entscheidung | Muss vor Marktstart feststehen |
| --- | --- |
| Publisher | verifizierte Person/Organisation, öffentliche Kontakte, D-U-N-S soweit erforderlich |
| Lizenz | Nutzungs-, Änderungs-, Weitergabe- und Hostingrechte; kein stillschweigendes Modell |
| Preis | kostenlos, Einmalkauf, Abo, Support oder Managed Hosting; Abrechnungskanal |
| Marke | endgültiger Name, Domains, Logo, Paket-ID und zulässige White-Labels |
| Support | Sprachen, Zeiten, Reaktionsziele, unterstützte Versionen und Self-hosting-Grenzen |
| Datenschutz | Publisher-/Betreiberrollen, öffentliche Policy, Löschung, Aufbewahrung, Subprozessoren |
| Sicherheit | Disclosure-Adresse, Patch-SLA, SBOM, Schlüsselverwaltung, Incident-Kommunikation |
| Regionen | Zielländer, Altersgruppe und dort nötige Produkt-/Verbraucher-/Gesundheitsprüfung |

## Commercial Go/No-Go

- [ ] Lizenz- und Preismodell schriftlich entschieden und fachlich geprüft.
- [ ] Marke, Paket-ID, Domains und Signing-Key-Strategie final.
- [ ] Self-hosting von einem sauberen System reproduzierbar; Upgrade und Restore
      praktisch getestet.
- [ ] Solo-Backup auf ein zweites echtes Gerät exportiert, importiert und
      inhaltlich geprüft.
- [ ] PWA auf den unterstützten Browser-/OS-Versionen installiert und gegen
      Rollen-/Freigabegrenzen getestet.
- [ ] Release-SBOM, Prüfsummen, Security- und Lizenzscan vorhanden.
- [ ] Keine Secrets oder privaten Daten in Git-Historie, Images, Logs,
      Screenshots und Releases.
- [ ] Support-, Security-, Datenschutz-, Lösch- und EOL-Verfahren erreichbar.
- [ ] Google-Play-Checkliste vollständig, sofern über Play vertrieben wird.
- [ ] Marketing beschreibt nur nachweislich gebaute und getestete Funktionen.
