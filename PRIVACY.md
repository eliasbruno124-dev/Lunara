# Datenschutz

Lunara speichert Kerndaten ausschließlich auf dem selbst betriebenen Server und
in der verschlüsselten lokalen Android-Datenbank. Es gibt keine Werbung,
Telemetrie, externe Nutzungsanalyse, Marketing-SDKs, Firebase- oder
Google-Play-Services-Pflicht für Kernfunktionen.

## Gespeicherte Daten

- Konto: Benutzername, Anzeigename, Sprache, Rolle und Gerätesitzungen
- Beziehung: eine für Benutzer unsichtbare interne Mandanten-ID,
  Mitgliedschaften, Einladungen, Freigaben und Care Preferences
- Gesundheit: Periodenstart/-ende, Tageslogs, Intensitäten, Temperatur,
  Ovulationstest-Markierung, Symptome und verschlüsselte optionale Notizen
- abgeleitete Daten: Bereichsprognosen, Statistik, Erklärung sowie nur nach
  Opt-in eine grobe Schätzung des fruchtbaren Fensters
- Unterstützung: Tippfeedback, eigene Tipps und zeitlich begrenzte Check-ins
- Sicherheit: erfolgreiche/fehlgeschlagene Anmeldung, neues Gerät,
  Sitzungs-/Einladungswiderruf, Freigabeänderung, Export und Kontolöschung

Audit-Logs enthalten keine Gesundheitswerte oder Freitextnotizen.

Bei `REGISTRATION_ENABLED=true` können mehrere Paare dieselbe Serverinstanz
verwenden. Eine neue öffentliche Registrierung erzeugt ausschließlich ein
`TRACKER`-Konto in einer neuen, getrennten Beziehung. Das zugehörige
`PARTNER`-Konto entsteht nur über einen permanenten, einmal verwendbaren und
widerrufbaren Einladungscode. Pro Beziehung ist jede Rolle höchstens einmal
belegt; serverseitige Abfragen werden auf diese interne Beziehungs-ID begrenzt.

Notizen in Tageslogs sind anwendungsseitig verschlüsselt. Kurze Check-in-
Prompts und Antworten liegen in Version 1 dagegen als strukturierter Klartext in
der zugriffsbeschränkten PostgreSQL-Datenbank und deren Backups; dort sollten
keine ausführlichen Gesundheitsdetails eingetragen werden.

## Partnerfreigaben

Standardmäßig sieht `PARTNER` nur den erwarteten Zeitraum. Notizen, Symptome,
Blutungsstärke, Schmerzen, Stimmung, Energie, Schlaf, tatsächliche
Periodendaten, geschätzte Phase und Fruchtbarkeitsdaten sind aus. Partner-
Endpoints filtern bei jeder Anfrage anhand der aktuellen Freigaben, einer
globalen Pause und eines optionalen privaten Datums. Wird eine Freigabe
deaktiviert, erscheint der Wert in der nächsten Serverantwort nicht mehr.

Tipps dürfen gesperrte Symptome weder nennen noch indirekt verraten. Sind Tipps
deaktiviert oder ist kein Tipp zulässig, zeigt Android nur einen neutralen
Nullzustand. Die 71 deutschen und englischen Tipps berücksichtigen eine
geschätzte Phase nur, wenn diese freigegeben ist. Care Preferences und
symptomabhängige Tipps werden ausschließlich verwendet, wenn die dafür nötigen
Einstellungen dies erlauben. Ein privater Tag entfernt aktuelle Phase,
tatsächliche Periodendaten und Fertilitätsangaben aus der Partnerprojektion;
eine allgemein freigegebene zukünftige Periodenprognose darf bestehen bleiben.

Die Android-Einstellungsansicht 1.0.7 bietet die Freigaben für Prognose,
Periodendaten, Phase, Blutungsstärke, Schmerzen, Stimmung, Energie, Schlaf,
Symptome, Notizen, Fertilitätsfenster, Tipps und Check-ins sowie
symptombasierte Tipps. Die trackende Person kann zudem die Care Preferences
Wärme, Nähe, Raum, Snacks, Gespräch, Haushaltshilfe, Erinnerungen und „keine
ungefragten Ratschläge“ direkt pflegen. Freigabepause, permanente
Einmal-Einladungen samt Widerruf, Lunara-Passwortwechsel und Screenshot-Schutz
sind ebenfalls integriert. Eine Partner-Trennen- oder Beziehung-verlassen-UI
ist noch nicht vorhanden.

Die Fertilitätsschätzung ist standardmäßig aus. Sie erfordert ein ausdrückliches
Opt-in im Zyklusprofil und mindestens drei vollständige Zyklen. Eine getrennte
Freigabe entscheidet über die Partneransicht; das Deaktivieren der Schätzung
widerruft diese Freigabe serverseitig. Der Zeitraum ist nur eine statistische
Schätzung, bestätigt keine Ovulation und eignet sich nicht zur Verhütung.

Bereits außerhalb von Lunara gespeicherte Screenshots oder Abschriften können
nicht nachträglich zurückgerufen werden.

## Serveradresse und Authentik

Authentik schützt die gesamte Domain einschließlich aller API-, Meta-, Health-
und Dokumentationspfade. Genau ein gemeinsamer technischer App-Zugang öffnet
die äußere Proxy-Schicht mit `Authorization: Basic …`; er ist kein persönliches
Konto. Persönliche Lunara-Authentifizierung und Rollenprüfung erfolgen separat
über `X-Lunara-Authorization: Bearer …`.

Android 1.0.7 startet mit leerem Serverfeld. Eine HTTPS-Adresse mit eingebetteten
Gateway-Zugangsdaten darf ausschließlich bei der ersten Einrichtung einmalig
eingegeben werden. Die App trennt die Zugangsdaten sofort von der sichtbaren
Basisadresse und speichert sie Keystore-gestützt. Die dauerhaft sichtbare
Serveradresse und sämtliche QR-Codes enthalten keine Credentials. Der
gemeinsame App-Zugang bleibt dennoch ein Infrastrukturgeheimnis und muss bei
Verdacht zentral für alle Installationen rotiert werden. Die Header-Auth-Adresse
darf nicht in Zwischenablagehistorien, Chat, Git, Dokumentation oder Cloudflare-
DNS gelangen.

Neue Lunara-Kontopasswörter sind auf 8 bis 128 Unicode-Zeichen begrenzt und
benötigen keine vorgeschriebenen Zeichenklassen. Bestehende Passwörter werden
für Login, Kontolöschung und Passwortänderung mit 1 bis 128 Zeichen geprüft,
damit ältere Konten nicht allein durch eine neue Richtlinie ausgesperrt werden.
Passwörter werden nur als Argon2id-Hash gespeichert. Die Passphrase eines
verschlüsselten Exports ist davon unabhängig und bleibt 12 bis 128 Zeichen
lang.

## Benachrichtigungen

Android 1.0.7 bietet ausschließlich lokale, standardmäßig deaktivierte
Erinnerungen. Nach dem Einschalten fragt die App auf unterstützten
Android-Versionen ausdrücklich nach der Benachrichtigungsberechtigung. Es gibt
keine Firebase-, Werbe-, Analyse- oder externe Push-Verbindung; die optionale
selbst gehostete ntfy-Anbindung ist weiterhin nicht implementiert.

Pro Gerät lassen sich Periodenzeitraum am Folgetag, grob geschätztes
fruchtbares Fenster am Folgetag, Tracker-Eingabe beziehungsweise Partner-Tipp,
Uhrzeit, Ruhezeit und maximale Tageshäufigkeit getrennt festlegen. Der
Sperrbildschirm zeigt standardmäßig nur einen neutralen Hinweis. Android darf
lokale Arbeit aus Energiespargründen verzögern; die gewählte Uhrzeit ist daher
ein Wunschzeitpunkt, keine Weckergarantie.

Tracker-Prognosen werden nach erfolgreicher Abfrage Keystore-gestützt
verschlüsselt zwischengespeichert. Partner-Hinweise verwenden ausschließlich
eine aktuelle, serverseitig nach Freigaben gefilterte Antwort. Ohne
Serververbindung werden für Partner weder Prognose-/Fertilitätshinweise noch
alte Tagestipps erzeugt. Beim sicheren lokalen Löschen werden
Benachrichtigungen, Zustellzustand und Prognosecache entfernt. `FLAG_SECURE`
blockiert im aktivierten Datenschutzmodus Screenshots und Vorschauen der App.

## Export, Import und Löschung

Die API kann folgende Formate exportieren:

- JSON mit Konto-Grunddaten, Zyklusprofil, Freigaben, Care Preferences,
  Perioden und Tageslogs
- CSV der Perioden oder Tageslogs
- dasselbe JSON als passwortverschlüsseltes Lunara-Backupformat

„Vollständig“ bezieht sich in Version 1 auf diese Tracker-Kerndaten; Beziehungs-
mitglieder, Geräte, Einladungen, Check-ins, Tippfeedback, eigene Tipps,
Audit-Ereignisse und Sync-Metadaten sind nicht Teil des Exports.

Der Import akzeptiert Perioden und Tageslogs aus einer Lunara-JSON-Struktur,
validiert Grenzen, zeigt eine Vorschau und überspringt Duplikate. Ein generischer
CSV-Zuordnungsassistent, direkter Import des verschlüsselten Exportformats und
ein API-Endpunkt zum Rückgängigmachen eines Import-Batches fehlen noch. Die
Android-Oberfläche bindet Export, Import und Kontolöschung noch nicht ein.

Die Kontolöschungs-API verlangt das Passwort erneut, widerruft durch die
Datenbanklöschung alle zugehörigen Sitzungen und erzwingt bei der Tracker-Rolle
eine bewusste Löschung des gesamten internen Beziehungsmandanten. Dieses
technische Modell ist kein sichtbares Haushaltskonzept, verhindert aber den
Zugriff anderer Paare. Serverbackups können gelöschte Daten bis zum Ablauf der
selbst festgelegten Aufbewahrungsfrist enthalten.
