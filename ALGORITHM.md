# Prediction algorithm

Algorithm identifier: `lunara-robust-v3`

Lunara calculates a cautious, understandable date range. The result is
not a medical prediction, evidence of ovulation, or a method of
contraception.

## Changes in 1.6.0

Version 3 widens the MAD-based spread with a factor of 1.28, rejects a
start range with a half-width above 14 days or a beginning before the last
recorded start, and caps confidence at `MEDIUM` after a recent excluded
interval longer than 90 days. The center, minimum widths and safety-day
additions are unchanged. The factor broadens the heuristic; it does not
establish a calibrated 80% coverage probability or clinical accuracy.

## Accuracy review of September 21, 2026

### Presentation from 1.1.6 onward

The range is explicitly labeled as the **expected period start**,
not the duration of bleeding. The interface states the inclusive number of
possible start days and complete observed cycles. With fewer than three cycles,
the limited data basis appears directly beside the prediction. For example,
the 9th–16th includes eight possible start days. An actual start on the last
day falls within the window, but does not demonstrate high precision.

1.1.6 does not change the formula. The range is neither adjusted to a start
that becomes known afterward nor narrowed merely for presentation. Missing
synchronization can also mean that the server and device have different inputs.
This must be resolved before assessing an individual's prediction accuracy.

The regression tests check the calculation steps described here, not clinical
accuracy. There is no prospective validation, measured personal error rate,
or calibrated coverage probability for the displayed window. `HIGH` is an
internal heuristic and does not mean “medically very precise.”

The independent review reproduced the following limitations in version 2:

- Six 28-day cycles followed by a 365-day gap in recording could
  produce `HIGH` and a half-width of two days. Version 3 retains the
  half-width but caps confidence at `MEDIUM` while that gap is the newest
  or second-newest raw interval. Fertility remains unavailable.
- The sequence 15, 90, 15 days produces `LOW`, but because MAD = 0, its
  half-width is only three days. The robust range is not an interval with
  a guaranteed probability of containing the actual start.
- The end range accounts for the median bleeding duration, not its
  own variability. Bleeding episodes of different lengths therefore do not widen it.

The zero-MAD and bleeding-duration limitations remain in version 3.
The changes in 1.6.0 are implemented consistently in the server, Android
solo algorithm and PWA; a calibrated uncertainty display still requires
suitable validation data.

## Inputs

- The tracker's non-deleted period starts as local calendar dates
- The individually specified typical cycle length, if provided
- Completed period durations
- Special mode, pause settings, and fertility settings
- Optional luteal phase, restricted to a plausible configuration range
- The current local calendar date in the time zone stored in the cycle
  profile

Time zones do not shift period days because starts and ends are stored as
SQL `date` and Android `LocalDate` values.

## Cycle selection

Observed cycle intervals are calculated from chronologically consecutive
period starts. The statistics window contains at most the last twelve
intervals. If fewer than twelve exist, all available intervals are used.

All positive intervals remain visible, unchanged, in the raw statistics.
However, intervals below 15 or above 90 days are not considered reliable
representations of exactly one cycle. They may, for example, result from an
incorrect entry or periods that were not recorded between two entries.
These values are therefore transparently marked as excluded and used
neither for the model center nor for a fertility estimate. They are not
deleted and remain traceable in the mean, raw median, minimum,
maximum, and list of observed intervals.

## Robust center and weighting

1. Calculate the model median and median absolute deviation (MAD) of usable
   intervals from 15 to 90 days.
2. Statistical outliers within this range remain in the model. MAD
   prevents a single value from disproportionately inflating the prediction
   range. The spread between usable intervals still affects confidence.
   Excluded raw values remain visible separately.
3. The oldest length receives a weight of 1.0. The weight increases linearly
   to 2.0 for the newest length, giving recent data more relevance. With at
   least three usable cycles, the newest value cannot dominate the result
   by itself.
4. The typical length is the weighted median, rounded to whole days.

If there are no usable complete cycles, only an explicitly specified typical
length can enable a rough prediction. If neither is available, there is no
prediction. Excluded raw intervals remain visible in the API statistics
in this case as well. With one or two usable complete cycles, confidence is
always “low” and the range is visibly widened.

## Prediction range

The center is `last period start + typical cycle length`. The
half-width is robust and uses `ceil(1.28 × 1.4826 × MAD) + 1`. With fewer than
three cycles, one additional safety day is added. Its minimum is:

- 0 cycles plus a manual value: 4 days
- 1–2 cycles: 4 days
- 3–5 cycles: 3 days
- 6 or more cycles: 2 days

The largest of these quantities is used. High robust variability therefore
widens the range without producing an artificially exact prediction for a
single day. The range of usable intervals affects the confidence level,
not the width of the prediction window. This prevents a single unusual
interval from expanding the visible window by months. The expected start
runs from the center minus the half-width to the center plus the half-width.

If the half-width exceeds 14 days, or the calculated beginning precedes the
last recorded period start, the result is unavailable with reason
`UNRELIABLE_RANGE`. No start, end or fertility dates are returned for this
case. The interface explains: "Your cycles vary too much for a useful estimate
yet." Statistics and the number of model cycles remain available. This check
takes precedence over `STALE_HISTORY`; the range is not clipped to look more
precise.

If the entire calculated start window is before the local reference date,
an outdated prediction is not returned as the “next period.” The API then
responds with `STALE_HISTORY`. Lunara does not automatically extrapolate
missing periods because that assumption would invent additional,
unrecorded cycles and obscure the uncertainty.

The typical period duration is the median of positive completed durations up
to 30 days from the chronologically last twelve completed periods. Longer
observed durations remain visible as excluded raw values, but are not silently
used as the typical period duration. Selection is independent of the order
of the API input. Without usable history, an explicit typical duration can
be used.
If that is also absent, version 3 retains the cautious five-day prior for API
compatibility. The statistics must identify the source as
`observed-periods`, `profile-fallback`, or `default-prior`. The interface must
not describe a prior as a personally observed typical duration.
The start range plus the duration gives the end range.

## Confidence

- `LOW`: fewer than three usable cycles, robust variability above five days,
  or a range contribution above six days
- `MEDIUM`: at least three cycles, robust variability no greater than five days,
  and a range contribution no greater than six days
- `HIGH`: at least six cycles, robust variability no greater than two days,
  and a range contribution no greater than three days

Robust variability here means `ceil(1.28 × 1.4826 × MAD)`. If either of the
last two **raw** intervals in the statistics window is longer than 90 days,
`HIGH` is capped at `MEDIUM`. `LOW` remains `LOW`. Older excluded intervals
and intervals below 15 days do not trigger this cap; any excluded interval
still prevents a fertility estimate.

The API returns the level, observed and used counts, typical length,
raw median, model median, minimum, maximum, excluded intervals,
period duration with its data source, variability, and a text explanation.
Clients should explain excluded values clearly and must not present the
five-day prior as a personal observation.

## Special modes

No normal cycle prediction is returned in `HORMONAL_CONTRACEPTION`,
`PREGNANCY`, `POSTPARTUM`, `MENOPAUSE_TRANSITION`, or `PAUSED`, or when
`predictionPaused=true`. History remains visible. After returning to
`NONE`, the prediction is recalculated from the available, explicitly confirmed data.

## Estimated fertile window

The feature is disabled by default. It is offered only after explicit
enabling, in `NONE`, with predictions not paused and at least three
usable recorded cycles. In addition, every interval in the current
statistics window must be usable, prediction confidence must be at least
`MEDIUM`, and the half-width must be no greater than 14 days. Otherwise,
the estimate remains unavailable with reason `UNRELIABLE_DATA`. A possible
ovulation center is estimated purely from calendar dates as the expected
period start minus the configured luteal phase (14 days by default).
The visible window extends at least from five days before to one day after
that center, and is widened by the prediction's half-width when uncertain.

Every view labels it “estimated” and states that it does not confirm
ovulation, is particularly uncertain with irregular cycles, and must not
be used as a method of contraception.

The API also returns explicit safety metadata (`enabled`,
`available`, the number of data points used, a reason, and the constants
`notContraception` and `doesNotConfirmOvulation`). Clients display the range
only when every positive safety condition is met. Missing or unknown
metadata is treated as “unavailable.”

## Portable reference contract for offline clients

Android, PWA, and server must use the same steps and constants:

1. Strictly validate ISO dates as Gregorian local calendar dates and convert
   them to whole epoch days. Do not apply the device's time zone to period dates.
2. Deduplicate starts and sort them in ascending order. Calculate differences
   and keep only the last twelve raw intervals. Model intervals range from
   15 to 90 days, inclusive.
3. Model intervals retain their chronological positions. The weight is `1`
   for a single value, otherwise `1 + index/(count-1)`. For the weighted
   median, sort value/weight pairs by value and choose the first value whose
   cumulative weight reaches at least half of the total weight.
4. Model MAD is the median of absolute distances from the model median.
   `robustSpread = ceil(1.28 × 1.4826 × MAD)`. The half-width for 0–2 model cycles is
   `max(4, robustSpread+2)`, for 3–5 it is `max(3, robustSpread+1)`, and otherwise
   `max(2, robustSpread+1)`.
5. The range contribution is `ceil((modelMaximum-modelMinimum)/4)` and is
   used only for the confidence rules. After applying those rules, cap
   `HIGH` at `MEDIUM` if either of the last two raw intervals is above 90 days.
6. For period durations, first sort entries by start, then select the last
   twelve positive completed durations. Values above 30 remain in the raw
   statistics but are not part of the model basis. The median is rounded to
   whole days, followed by the profile fallback and finally the labeled
   five-day prior.
7. The start center is `lastStart + weightedMedian`. Start and end boundaries
   are inclusive. First return `UNRELIABLE_RANGE` if `halfWidth > 14` or
   `predictedStartFrom < lastStart`. Otherwise, if
   `predictedStartTo < referenceDate`, the result is `STALE_HISTORY`.
8. Fertility is available only when enabled, with at least three model cycles,
   no excluded raw intervals, confidence other than `LOW`, and a half-width
   no greater than 14. The default luteal phase is 14. Window boundaries are
   `ovulationCenter - 5 - halfWidth` and
   `ovulationCenter + 1 + halfWidth`.

`calculatedAt` is not part of mathematical equality. Canonical
test vector (no period ends, so the five-day prior is labeled):

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

Fail-closed vectors:

- The same three 28-day intervals with `referenceDate=2026-05-01` produce
  `STALE_HISTORY`.
- Raw intervals `[1,1,1]` without a profile fallback produce `INSUFFICIENT_DATA`,
  model intervals `[]`, and excluded intervals `[1,1,1]`.
- Raw intervals `[28,28,365,28]` produce model intervals `[28,28,28]`,
  excluded intervals `[365]`, a half-width of 3, and, when fertility is enabled,
  `UNRELIABLE_DATA` instead of a window.
- Raw intervals `[28,40]` produce a half-width of 14 and remain available.
  `[28,41]` produce a half-width of 15 and `UNRELIABLE_RANGE`.
- Raw intervals `[90,15]` produce `UNRELIABLE_RANGE`, rather than a next-start
  range beginning before the last recorded start, even with a stale reference date.
- Six 28-day intervals followed by `[365]` or `[365,28]` produce `MEDIUM`;
  six 28-day intervals followed by `[365,28,28]` may produce `HIGH` again.
  Fertility remains unavailable in each case while the excluded gap is in
  the statistics window.

## Limitations

Stress, illness, medication, hormonal changes, pregnancy,
breastfeeding, and many other factors can affect actual cycles. Lunara
does not diagnose, infer emotions from a cycle day, or recommend medication.
For unusual or distressing symptoms, professional medical advice is appropriate.
