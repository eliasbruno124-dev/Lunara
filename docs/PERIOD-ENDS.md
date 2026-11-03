# Automatisches Periodenende

Ohne selbst eingetragenes Enddatum nimmt Lunara fünf Kalendertage an. Der
Starttag zählt als erster Tag: Ein Beginn am 1. November endet damit angenommen
am 5. November; ab dem 6. November gilt die Periode nicht mehr als laufend.
Das gilt auch für ältere Einträge und funktioniert ohne laufenden Hintergrundjob.

Ein selbst eingetragenes Ende hat Vorrang, auch wenn die Periode länger dauert.
Das Ende kann nachträglich korrigiert werden. Tatsächlich überlappende Einträge
werden weiterhin abgewiesen. App, Web, Server und die Datenbankprüfung verwenden
dieselbe Grenze.

Ein angenommenes Ende ist keine bestätigte Beobachtung. Der ursprüngliche
Eintrag bleibt erhalten; Export, Synchronisation und verschlüsselte Sicherungen
bewahren ein nicht eingetragenes Ende als unbekannt. Die Auswertung lernt daraus
keine vermeintlich gemessene Fünf-Tage-Dauer. Im Kalender werden keine zukünftigen
angenommenen Tage als bereits erfasst dargestellt.

## Fruchtbarkeit

Die vorhandene Berechnung berücksichtigt Unsicherheit. Ihre zulässigen Ergebnisse
sind höchstens 19 Tage breit. Ältere, breitere gespeicherte Vorhersagen werden
abgewiesen, nicht auf eine scheinbar präzise Dauer gekürzt. Die aktuelle Übersicht
blendet eine Vorhersage nach ihrem eigenen Enddatum aus. Gültige historische
Kalendermarkierungen bleiben von der heutigen Übersicht getrennt.

Diese Grenze ist eine Konsistenzprüfung der Software, keine medizinische
Validierung. Kalenderbasierte Schätzungen bestätigen keinen Eisprung und sind
nicht zur Verhütung geeignet. Siehe auch die
[Informationen des NHS](https://www.nhs.uk/contraception/methods-of-contraception/natural-family-planning/).
