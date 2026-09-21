# Prognosealgorithmus

Algorithmuskennung: `lunara-robust-v2`

Lunara berechnet eine vorsichtige, nachvollziehbare Zeitspanne. Das Ergebnis ist
keine medizinische Prognose, kein Ovulationsnachweis und keine
Verhütungsmethode.

## Genauigkeitsprüfung vom 21. September 2026

### Darstellung ab 1.1.6

Der Zeitraum ist ausdrücklich als **erwarteter Periodenbeginn** beschriftet,
nicht als Dauer der Blutung. Die Oberfläche nennt die inklusive Anzahl
möglicher Starttage und vollständiger beobachteter Zyklen. Unter drei Zyklen
steht die geringe Datenbasis unmittelbar an der Prognose. Beispiel:
9.–16. umfasst acht mögliche Starttage. Ein tatsächlicher Beginn am letzten
Tag ist ein Treffer innerhalb des Fensters, aber kein Beleg für hohe Präzision.

1.1.6 verändert die Formel nicht. Die Spanne wird weder an einen nachträglich
bekannten Beginn angepasst noch rein optisch verengt. Fehlende Synchronisation
kann zudem bedeuten, dass Server und Gerät unterschiedliche Eingaben besitzen;
dies muss vor einer individuellen Genauigkeitsbewertung geklärt sein.

Die Regressionstests prüfen die beschriebenen Rechenschritte, nicht eine
klinische Treffergenauigkeit. Es gibt keine prospektive Validierung, gemessene
persönliche Fehlerrate oder kalibrierte Abdeckungswahrscheinlichkeit des
angezeigten Zeitfensters. `HIGH` ist eine interne Heuristik und bedeutet nicht
„medizinisch sehr präzise“.

Beim unabhängigen Review wurden folgende Grenzen reproduziert:

- Sechs 28-Tage-Zyklen und anschließend eine 365-Tage-Erfassungslücke können
  weiterhin `HIGH` und eine Halbbreite von zwei Tagen ergeben: Der große
  Abstand wird vor der Vertrauensberechnung aus dem Modell ausgeschlossen.
  Fruchtbarkeit wird in diesem Fall korrekt nicht geschätzt.
- Die Folge 15, 90, 15 Tage liefert zwar `LOW`, aber wegen MAD = 0 nur eine
  Halbbreite von drei Tagen. Der robuste Bereich ist kein Intervall mit
  zugesicherter Trefferwahrscheinlichkeit.
- Die Endspanne berücksichtigt den Median der Blutungsdauer, nicht deren
  eigene Streuung. Unterschiedlich lange Blutungen verbreitern sie daher nicht.

Diese Einschränkungen sind in 1.1.5 nicht durch Änderungen der Formel behoben.
Das Update betrifft die Anmeldung und Synchronisierung. Eine künftig besser
kalibrierte Unsicherheitsdarstellung benötigt getrennte, übereinstimmende
Änderungen an Server, Android-Soloalgorithmus und PWA sowie passende Testdaten.

## Eingaben

- nicht gelöschte Periodenstarts der Tracker-Person als lokale Kalenderdaten
- optional die individuell angegebene typische Zykluslänge
- abgeschlossene Periodendauern
- Sondermodus, Pause- und Fruchtbarkeitseinstellungen
- optionale Lutealphase, begrenzt auf einen plausiblen Konfigurationsbereich
- das aktuelle lokale Kalenderdatum aus der im Zyklusprofil gespeicherten
  Zeitzone

Zeitzonen verschieben Periodentage nicht, weil Start und Ende als SQL-`date` und
Android-`LocalDate` gespeichert werden.

## Auswahl der Zyklen

Aus chronologisch aufeinanderfolgenden Periodenstarts entstehen beobachtete
Zyklusintervalle. Das Statistikfenster enthält höchstens die letzten zwölf
Intervalle; bei weniger als zwölf werden alle vorhandenen verwendet.

Alle positiven Abstände bleiben unverändert in den Rohstatistiken sichtbar.
Intervalle unter 15 oder über 90 Tagen gelten jedoch nicht als belastbare
Abbildung genau eines Zyklus: Sie können beispielsweise aus einer
Fehleingabe oder aus nicht erfassten Perioden zwischen zwei Einträgen entstehen.
Solche Werte werden deshalb transparent als ausgeschlossen gekennzeichnet und
weder für die Modellmitte noch für eine Fruchtbarkeitsschätzung verwendet. Sie
werden nicht gelöscht und bleiben in Durchschnitt, Roh-Median, Minimum,
Maximum und der Liste beobachteter Intervalle nachvollziehbar.

## Robuste Mitte und Gewichtung

1. Berechne Modell-Median und Median Absolute Deviation (MAD) der verwendbaren
   Intervalle von 15 bis 90 Tagen.
2. Statistische Ausreißer innerhalb dieses Bereichs bleiben im Modell. MAD
   verhindert, dass ein einzelner Wert den Prognosebereich unverhältnismäßig
   aufbläht; die Spannweite der verwendbaren Intervalle beeinflusst weiterhin
   das Vertrauen. Ausgeschlossene Rohwerte bleiben separat sichtbar.
3. Die älteste Länge erhält Gewicht 1,0. Das Gewicht steigt linear bis 2,0 für
   die neueste Länge, sodass aktuelle Daten relevanter sind. Ab drei
   verwendbaren Zyklen kann der neueste Wert das Ergebnis nicht allein
   dominieren.
4. Als typische Länge dient der gewichtete Median, auf volle Tage gerundet.

Bei keinem verwendbaren vollständigen Zyklus kann ausschließlich die explizit
angegebene typische Länge eine grobe Prognose ermöglichen. Ohne beides gibt es
keine Prognose. Ausgeschlossene Rohintervalle bleiben auch bei diesem Ergebnis
in der API-Statistik sichtbar. Bei ein oder zwei verwendbaren vollständigen
Zyklen ist die Sicherheit immer „niedrig“ und der Bereich wird sichtbar
verbreitert.

## Prognosezeitraum

Der Mittelpunkt ist `letzter Periodenstart + typische Zykluslänge`. Die
Halbbreite ist robust und verwendet `ceil(1,4826 × MAD) + 1`. Bei weniger als
drei Zyklen kommt ein zusätzlicher Sicherheitstag hinzu. Sie beträgt mindestens:

- 0 Zyklen plus manueller Wert: 4 Tage
- 1–2 Zyklen: 4 Tage
- 3–5 Zyklen: 3 Tage
- 6 oder mehr Zyklen: 2 Tage

Die größte dieser Größen wird verwendet. Hohe robuste Streuung verbreitert also
den Zeitraum, erzeugt aber keine künstlich exakte Tagesaussage. Die Spannweite
verwendbarer Intervalle beeinflusst die Vertrauensstufe, nicht die Bandbreite;
dadurch kann ein einzelnes ungewöhnliches Intervall den sichtbaren Bereich nicht
um Monate vergrößern. Der erwartete Beginn reicht von Mittelpunkt minus bis
Mittelpunkt plus Halbbreite.

Liegt das gesamte berechnete Startfenster vor dem lokalen Referenzdatum, wird
keine veraltete Prognose als „nächste Periode“ ausgegeben. Die API antwortet
dann mit `STALE_HISTORY`. Lunara schreibt fehlende Perioden nicht automatisch
fort, weil eine solche Annahme zusätzliche, nicht erfasste Zyklen erfinden und
die Unsicherheit verschleiern würde.

Die typische Periodendauer ist der Median abgeschlossener positiver Dauern bis
30 Tage aus den chronologisch letzten zwölf abgeschlossenen Perioden. Längere
beobachtete Dauern bleiben als ausgeschlossene Rohwerte sichtbar, werden aber
nicht stillschweigend als typische Periodendauer verwendet. Die Auswahl ist von
der Reihenfolge der API-Eingabe unabhängig. Ohne verwendbare Historie kann eine
explizite typische Dauer dienen.
Fehlt auch diese, verwendet Version 2 aus Gründen der API-Kompatibilität einen
vorsichtigen Fünf-Tage-Prior. Die Statistik kennzeichnet die Quelle zwingend als
`observed-periods`, `profile-fallback` oder `default-prior`; ein Prior darf in
der Oberfläche nicht als persönlich beobachtete typische Dauer bezeichnet
werden. Startspanne plus Dauer ergibt die Endspanne.

## Vertrauen

- `LOW`: weniger als drei verwendbare Zyklen, robuste Streuung über fünf Tagen
  oder der Spannweitenbeitrag über sechs Tagen
- `MEDIUM`: mindestens drei Zyklen, robuste Streuung höchstens fünf Tage und der
  Spannweitenbeitrag höchstens sechs Tage
- `HIGH`: mindestens sechs Zyklen, robuste Streuung höchstens zwei Tage und der
  Spannweitenbeitrag höchstens drei Tage

Die API liefert Stufe, beobachtete und verwendete Anzahl, typische Länge,
Roh-Median, Modell-Median, Minimum, Maximum, ausgeschlossene Intervalle,
Periodendauer samt Datenquelle, Streuung und eine Textbegründung. Clients sollen
ausgeschlossene Werte verständlich erklären und dürfen den Fünf-Tage-Prior
nicht als persönliche Beobachtung darstellen.

## Sondermodi

In `HORMONAL_CONTRACEPTION`, `PREGNANCY`, `POSTPARTUM`,
`MENOPAUSE_TRANSITION` und `PAUSED` sowie bei `predictionPaused=true` wird keine
normale Zyklusprognose ausgegeben. Historie bleibt sichtbar. Nach Rückkehr zu
`NONE` wird aus den vorhandenen, ausdrücklich bestätigten Daten neu berechnet.

## Geschätztes fruchtbares Fenster

Die Funktion ist standardmäßig deaktiviert. Sie wird nur nach ausdrücklicher
Aktivierung, bei `NONE`, nicht pausierter Prognose und mindestens drei
verwendbaren erfassten Zyklen angeboten. Zusätzlich müssen alle Intervalle im
aktuellen Statistikfenster verwendbar, das Prognosevertrauen mindestens
`MEDIUM` und die Halbbreite höchstens 14 Tage sein. Andernfalls bleibt die
Schätzung mit dem Grund `UNRELIABLE_DATA` geschlossen. Ein möglicher
Ovulationsmittelpunkt wird rein kalendarisch als erwarteter Periodenbeginn minus
konfigurierte Lutealphase (Standard 14 Tage) geschätzt. Das sichtbare Fenster
umfasst mindestens fünf Tage davor bis einen Tag danach und wird bei
Prognoseunsicherheit um deren Halbbreite erweitert.

Jede Ansicht bezeichnet es als „geschätzt“ und zeigt: Es beweist keine Ovulation,
ist bei unregelmäßigen Zyklen besonders unsicher und darf nicht als
Verhütungsmethode verwendet werden.

Die API liefert zusätzlich explizite Sicherheitsmetadaten (`enabled`,
`available`, verwendete Datenanzahl, Grund sowie die Konstanten
`notContraception` und `doesNotConfirmOvulation`). Clients zeigen den Zeitraum
nur, wenn alle positiven Sicherheitsbedingungen erfüllt sind. Fehlende oder
unbekannte Metadaten werden als „nicht verfügbar“ behandelt.

## Portierbarer Referenzvertrag für Offline-Clients

Android, PWA und Server müssen dieselben Schritte und Konstanten verwenden:

1. ISO-Daten streng als gregorianische lokale Kalenderdaten validieren und in
   ganze Epoch-Tage umrechnen. Keine Gerätezeitzone auf Periodendaten anwenden.
2. Starts deduplizieren und aufsteigend sortieren; Differenzen bilden; nur die
   letzten zwölf Rohintervalle behalten. Modellintervalle liegen inklusive
   Grenzen bei 15 bis 90 Tagen.
3. Modellintervalle behalten ihre chronologische Position. Das Gewicht ist bei
   einem Wert `1`, sonst `1 + index/(anzahl-1)`. Für den gewichteten Median
   Wertepaar samt Gewicht nach Wert sortieren und den ersten Wert wählen, bei
   dem die kumulierte Summe mindestens die Hälfte des Gesamtgewichts erreicht.
4. Modell-MAD ist der Median der absoluten Abstände zum Modell-Median;
   `robustSpread = ceil(MAD × 1,4826)`. Die Halbbreite ist bei 0–2 Modellzyklen
   `max(4, robustSpread+2)`, bei 3–5 `max(3, robustSpread+1)`, sonst
   `max(2, robustSpread+1)`.
5. Der Spannweitenbeitrag ist `ceil((modellMaximum-modellMinimum)/4)` und wird
   nur für die Vertrauensregeln verwendet.
6. Für Periodendauern die Einträge zuerst nach Start sortieren, dann die letzten
   zwölf positiven abgeschlossenen Dauern auswählen. Werte über 30 bleiben
   Rohstatistik, aber nicht Modellbasis. Der Median wird auf ganze Tage
   gerundet; danach folgen Profilfallback und schließlich der gekennzeichnete
   Fünf-Tage-Prior.
7. Startmitte ist `letzterStart + gewichteterMedian`; Start- und Endgrenzen sind
   inklusive. Ist `predictedStartTo < referenceDate`, lautet das Ergebnis
   `STALE_HISTORY`.
8. Fruchtbarkeit ist nur verfügbar bei Aktivierung, mindestens drei
   Modellzyklen, keinem ausgeschlossenen Rohintervall, Vertrauen nicht `LOW`
   und Halbbreite höchstens 14. Standard-Lutealphase ist 14; Fenstergrenzen sind
   `ovulationsMitte - 5 - halbbreite` und
   `ovulationsMitte + 1 + halbbreite`.

`calculatedAt` ist kein Bestandteil mathematischer Gleichheit. Kanonischer
Testvektor (keine Periodenenden, daher gekennzeichneter Fünf-Tage-Prior):

```json
{
  "input": {
    "starts": ["2026-01-01", "2026-01-29", "2026-02-26", "2026-03-26"],
    "referenceDate": "2026-03-26",
    "fertilityPredictionEnabled": true
  },
  "expected": {
    "predictedStartFrom": "2026-04-20",
    "predictedStartTo": "2026-04-26",
    "predictedEndFrom": "2026-04-24",
    "predictedEndTo": "2026-04-30",
    "fertileWindowFrom": "2026-04-01",
    "fertileWindowTo": "2026-04-13",
    "confidence": "MEDIUM",
    "basedOnCycleCount": 3,
    "typicalCycleLength": 28,
    "typicalPeriodLength": 5,
    "periodDurationDataBasis": "default-prior",
    "variationDays": 3
  }
}
```

Fail-closed-Vektoren:

- dieselben drei 28-Tage-Intervalle mit `referenceDate=2026-05-01` ergeben
  `STALE_HISTORY`;
- Rohintervalle `[1,1,1]` ohne Profilfallback ergeben `INSUFFICIENT_DATA`,
  Modellintervalle `[]` und ausgeschlossene Intervalle `[1,1,1]`;
- Rohintervalle `[28,28,365,28]` ergeben Modellintervalle `[28,28,28]`,
  ausgeschlossene Intervalle `[365]`, Halbbreite 3 und bei aktivierter
  Fruchtbarkeit `UNRELIABLE_DATA` statt eines Fensters.

## Grenzen

Stress, Erkrankung, Medikamente, hormonelle Veränderungen, Schwangerschaft,
Stillzeit und viele weitere Faktoren können reale Zyklen verändern. Lunara
diagnostiziert nichts, leitet keine Emotion aus einem Zyklustag ab und gibt keine
Medikamentenempfehlung. Bei ungewöhnlichen oder belastenden Beschwerden ist
professionelle medizinische Beratung sinnvoll.
