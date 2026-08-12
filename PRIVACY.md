# Datenschutz

Lunara speichert Kerndaten ausschließlich auf dem selbst betriebenen Server und
in der verschlüsselten lokalen Android-Datenbank. Es gibt keine Werbung,
Telemetrie, externe Nutzungsanalyse, Marketing-SDKs, Firebase- oder
Google-Play-Services-Pflicht für Kernfunktionen.

## Gespeicherte Daten

- Konto: Benutzername, Anzeigename, Sprache, Rolle und Gerätesitzungen
- Haushalt: Mitgliedschaften, Einladungen, Freigaben und Care Preferences
- Gesundheit: Periodenstart/-ende, Tageslogs, Intensitäten, Temperatur,
  Ovulationstest-Markierung, Symptome und verschlüsselte optionale Notizen
- abgeleitete Daten: Bereichsprognosen, Statistik und Erklärung
- Unterstützung: Tippfeedback, eigene Tipps und zeitlich begrenzte Check-ins
- Sicherheit: erfolgreiche/fehlgeschlagene Anmeldung, neues Gerät,
  Sitzungs-/Einladungswiderruf, Freigabeänderung, Export und Kontolöschung

Audit-Logs enthalten keine Gesundheitswerte oder Freitextnotizen.

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
Nullzustand. Care Preferences und symptomabhängige Tipps werden ausschließlich
verwendet, wenn die dafür nötigen Einstellungen dies erlauben.

Die Android-Einstellungsansicht bietet derzeit die wichtigsten Freigaben,
Freigabepause, Einladungen und Screenshot-Schutz. Weitere serverseitig
vorhandene Schalter – unter anderem Flow, Stimmung, Energie, Schlaf,
Tipps/Check-ins, privates Datum und symptomabhängige Tipps – müssen in Version 1
noch per API verwaltet werden. Eine Partner-Trennen- oder Haushalt-verlassen-UI
ist noch nicht vorhanden.

Bereits außerhalb von Lunara gespeicherte Screenshots oder Abschriften können
nicht nachträglich zurückgerufen werden.

## Gateway-Zugangsdaten

Authentik-App-Passwörter sind Infrastrukturzugänge, keine Lunara-
Gesundheitsdaten. Android akzeptiert sie im Userinfo-Teil einer Setup-URL,
entfernt sie danach sofort aus der Serveradresse und speichert sie
Keystore-geschützt. Trotzdem können Zwischenablage oder Tastaturverlauf die
ursprüngliche Eingabe sehen. QR-Codes dürfen keine Zugangsdaten enthalten.

## Benachrichtigungen

Version 1 fordert keine Benachrichtigungsberechtigung an und versendet keine
Push-Nachrichten. Lokale Erinnerungen und die optionale selbst gehostete
ntfy-Anbindung sind noch nicht implementiert. `FLAG_SECURE` blockiert im
aktivierten Datenschutzmodus Screenshots und Vorschauen der App.

## Export, Import und Löschung

Die API kann folgende Formate exportieren:

- JSON mit Konto-Grunddaten, Zyklusprofil, Freigaben, Care Preferences,
  Perioden und Tageslogs
- CSV der Perioden oder Tageslogs
- dasselbe JSON als passwortverschlüsseltes Lunara-Backupformat

„Vollständig“ bezieht sich in Version 1 auf diese Tracker-Kerndaten; Haushalts-
mitglieder, Geräte, Einladungen, Check-ins, Tippfeedback, eigene Tipps,
Audit-Ereignisse und Sync-Metadaten sind nicht Teil des Exports.

Der Import akzeptiert Perioden und Tageslogs aus einer Lunara-JSON-Struktur,
validiert Grenzen, zeigt eine Vorschau und überspringt Duplikate. Ein generischer
CSV-Zuordnungsassistent, direkter Import des verschlüsselten Exportformats und
ein API-Endpunkt zum Rückgängigmachen eines Import-Batches fehlen noch. Die
Android-Oberfläche bindet Export, Import und Kontolöschung noch nicht ein.

Die Kontolöschungs-API verlangt das Passwort erneut, widerruft durch die
Datenbanklöschung alle zugehörigen Sitzungen und erzwingt bei der Tracker-Rolle
eine bewusste Löschung des Haushalts. Serverbackups können gelöschte Daten bis
zum Ablauf der selbst festgelegten Aufbewahrungsfrist enthalten.
