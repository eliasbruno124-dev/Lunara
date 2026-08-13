# Prognosealgorithmus

Algorithmuskennung: `lunara-robust-v1`

Lunara berechnet eine vorsichtige, nachvollziehbare Zeitspanne. Das Ergebnis ist
keine medizinische Prognose, kein Ovulationsnachweis und keine
Verhütungsmethode.

## Eingaben

- nicht gelöschte Periodenstarts der Tracker-Person als lokale Kalenderdaten
- optional die individuell angegebene typische Zykluslänge
- abgeschlossene Periodendauern
- Sondermodus, Pause- und Fruchtbarkeitseinstellungen
- optionale Lutealphase, begrenzt auf einen plausiblen Konfigurationsbereich

Zeitzonen verschieben Periodentage nicht, weil Start und Ende als SQL-`date` und
Android-`LocalDate` gespeichert werden.

## Auswahl der Zyklen

Aus chronologisch aufeinanderfolgenden Periodenstarts entstehen vollständige
Zykluslängen. Verwendet werden höchstens die letzten zwölf vollständigen Zyklen;
bei weniger als zwölf werden alle vorhandenen verwendet. Beobachtete positive
Abstände werden nicht als Ausreißer entfernt oder heimlich geringer gewichtet.
Die technische Grenze von 15–90 Tagen gilt ausschließlich für einen manuell
eingetragenen typischen Profilwert, nicht für tatsächlich erfasste Historie.

## Robuste Mitte und Gewichtung

1. Berechne Median und Median Absolute Deviation (MAD) aller ausgewählten
   vollständigen Längen.
2. Kein beobachteter Wert wird als Ausreißer entfernt oder heimlich geringer
   gewichtet. MAD begrenzt stattdessen ausschließlich, wie präzise die App das
   Ergebnis darstellt; Minimum und Maximum bleiben sichtbar.
3. Die älteste Länge erhält Gewicht 1,0. Das Gewicht steigt linear bis 2,0 für
   die neueste Länge, sodass aktuelle Daten relevanter sind, ohne allein zu
   dominieren.
4. Als typische Länge dient der gewichtete Median, auf volle Tage gerundet.

Bei keinem vollständigen Zyklus kann ausschließlich die explizit angegebene
typische Länge eine grobe Prognose ermöglichen. Ohne beides gibt es keine
Prognose. Bei ein oder zwei vollständigen Zyklen ist die Sicherheit immer
„niedrig“ und der Bereich wird sichtbar verbreitert.

## Prognosezeitraum

Der Mittelpunkt ist `letzter Periodenstart + typische Zykluslänge`. Die
Halbbreite kombiniert `ceil(1,4826 × MAD) + 1` mit einem vorsichtigen Anteil
der beobachteten Spannweite (`ceil((Maximum-Minimum)/4)`) und beträgt mindestens:

- 0 Zyklen plus manueller Wert: 4 Tage
- 1–2 Zyklen: 4 Tage
- 3–5 Zyklen: 3 Tage
- 6 oder mehr Zyklen: 2 Tage

Die größte dieser Größen wird verwendet. Hohe Streuung verbreitert also den
Zeitraum, erzeugt aber keine künstlich exakte Tagesaussage. Der erwartete Beginn
reicht von Mittelpunkt minus bis Mittelpunkt plus Halbbreite.

Die typische Periodendauer ist der Median abgeschlossener positiver Dauern der
letzten zwölf Perioden. Ohne Historie kann eine explizite typische Dauer dienen;
sonst wird kein Ende behauptet. Startspanne plus Dauer ergibt die Endspanne.

## Vertrauen

- `LOW`: weniger als drei Zyklen, robuste Streuung über fünf Tagen oder der
  Spannweitenbeitrag über sechs Tagen
- `MEDIUM`: mindestens drei Zyklen, robuste Streuung höchstens fünf Tage und der
  Spannweitenbeitrag höchstens sechs Tage
- `HIGH`: mindestens sechs Zyklen, robuste Streuung höchstens zwei Tage und der
  Spannweitenbeitrag höchstens drei Tage

Die UI zeigt Stufe, berücksichtigte Zyklen, typische Länge, Median, Minimum,
Maximum, typische Periodendauer, Streuung und eine Textbegründung.

## Sondermodi

In `HORMONAL_CONTRACEPTION`, `PREGNANCY`, `POSTPARTUM`,
`MENOPAUSE_TRANSITION` und `PAUSED` sowie bei `predictionPaused=true` wird keine
normale Zyklusprognose ausgegeben. Historie bleibt sichtbar. Nach Rückkehr zu
`NONE` wird aus den vorhandenen, ausdrücklich bestätigten Daten neu berechnet.

## Geschätztes fruchtbares Fenster

Die Funktion ist standardmäßig deaktiviert. Sie wird nur nach ausdrücklicher
Aktivierung, bei `NONE`, nicht pausierter Prognose und mindestens drei
vollständigen erfassten Zyklen angeboten. Ein möglicher
Ovulationsmittelpunkt wird rein kalendarisch als erwarteter Periodenbeginn minus
konfigurierte Lutealphase (Standard 14 Tage) geschätzt. Das sichtbare Fenster
umfasst mindestens fünf Tage davor bis einen Tag danach und wird bei
Prognoseunsicherheit um deren Halbbreite erweitert.

Jede Ansicht bezeichnet es als „geschätzt“ und zeigt: Es beweist keine Ovulation,
ist bei unregelmäßigen Zyklen besonders unsicher und darf nicht als
Verhütungsmethode verwendet werden.

Die API liefert zusätzlich explizite Sicherheitsmetadaten (`enabled`,
`available`, Datenbasis, Grund sowie die Konstanten `notContraception` und
`doesNotConfirmOvulation`). Clients zeigen den Zeitraum nur, wenn alle positiven
Sicherheitsbedingungen erfüllt sind. Fehlende oder unbekannte Metadaten werden
als „nicht verfügbar“ behandelt.

## Grenzen

Stress, Erkrankung, Medikamente, hormonelle Veränderungen, Schwangerschaft,
Stillzeit und viele weitere Faktoren können reale Zyklen verändern. Lunara
diagnostiziert nichts, leitet keine Emotion aus einem Zyklustag ab und gibt keine
Medikamentenempfehlung. Bei ungewöhnlichen oder belastenden Beschwerden ist
professionelle medizinische Beratung sinnvoll.
