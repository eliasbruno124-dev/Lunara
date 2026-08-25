package de.lunara.app.data.prediction

import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.preferences.AppPreferencesState
import de.lunara.app.data.remote.dto.FertilityEstimateDto
import de.lunara.app.data.remote.dto.PredictionDto
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Portable Android implementation of the documented `lunara-robust-v2` contract. */
object LocalPredictionEngine {
    const val ALGORITHM_VERSION = "lunara-robust-v2"

    private const val MIN_MODEL_CYCLE_LENGTH = 15
    private const val MAX_MODEL_CYCLE_LENGTH = 90
    private const val MAX_FERTILITY_VARIATION_DAYS = 14
    private const val DEFAULT_PERIOD_DURATION = 5
    private val strictIsoDate = DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT)
    private val isoDatePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    data class HistoricalPeriod(val startDate: String, val endDate: String? = null)

    data class Input(
        val periods: List<HistoricalPeriod>,
        val referenceDate: String,
        val typicalCycleLength: Int? = null,
        val typicalPeriodLength: Int? = null,
        val lutealLength: Int? = null,
        val fertilityPredictionEnabled: Boolean = false,
        val predictionPaused: Boolean = false,
        val specialMode: String = "NONE",
    )

    fun calculate(input: Input, clock: Clock = Clock.systemUTC()): PredictionDto {
        if (input.predictionPaused) return unavailable("PREDICTION_PAUSED", clock)
        if (input.specialMode != "NONE") return unavailable("SPECIAL_MODE", clock)

        val starts = input.periods
            .map { strictDate(it.startDate) }
            .distinct()
            .sorted()
        if (starts.isEmpty()) return unavailable("NO_PERIOD_START", clock)

        val observedCycleLengths = starts.zipWithNext { first, second ->
            ChronoUnit.DAYS.between(first, second).toInt()
        }.also { values ->
            require(values.all { it > 0 }) { "Period starts must form positive cycle intervals" }
        }.takeLast(12)
        val modelCycleLengths = observedCycleLengths.filter { it in MIN_MODEL_CYCLE_LENGTH..MAX_MODEL_CYCLE_LENGTH }
        val excludedCycleLengths = observedCycleLengths.filterNot { it in MIN_MODEL_CYCLE_LENGTH..MAX_MODEL_CYCLE_LENGTH }
        val fallbackCycleLength = input.typicalCycleLength?.takeIf { it in MIN_MODEL_CYCLE_LENGTH..MAX_MODEL_CYCLE_LENGTH }

        val modelMedian = modelCycleLengths.takeIf { it.isNotEmpty() }?.let(::median)
        val observedMedian = observedCycleLengths.takeIf { it.isNotEmpty() }?.let(::median)
        val mad = modelMedian?.let { center ->
            medianDoubles(modelCycleLengths.map { kotlin.math.abs(it - center) })
        }
        val robustSpread = mad?.let { ceil(it * 1.4826).toInt() } ?: 0
        val rangeSignal = modelCycleLengths.takeIf { it.isNotEmpty() }?.let { values ->
            ceil((values.max() - values.min()) / 4.0).toInt()
        } ?: 0
        val variationDays = when (modelCycleLengths.size) {
            in 0..2 -> maxOf(4, robustSpread + 2)
            in 3..5 -> maxOf(3, robustSpread + 1)
            else -> maxOf(2, robustSpread + 1)
        }

        val observedPeriodDurations = input.periods
            .sortedBy { strictDate(it.startDate) }
            .mapNotNull { period ->
                val end = period.endDate?.let(::strictDate) ?: return@mapNotNull null
                ChronoUnit.DAYS.between(strictDate(period.startDate), end).toInt() + 1
            }
            .filter { it > 0 }
            .takeLast(12)
        val modelPeriodDurations = observedPeriodDurations.filter { it <= 30 }
        val excludedPeriodDurations = observedPeriodDurations.filter { it > 30 }
        val fallbackPeriodLength = input.typicalPeriodLength?.takeIf { it in 1..14 }
        val periodDurationDataBasis = when {
            modelPeriodDurations.isNotEmpty() -> "observed-periods"
            fallbackPeriodLength != null -> "profile-fallback"
            else -> "default-prior"
        }
        val typicalPeriodLength = when {
            modelPeriodDurations.isNotEmpty() -> median(modelPeriodDurations).roundToInt()
            fallbackPeriodLength != null -> fallbackPeriodLength
            else -> DEFAULT_PERIOD_DURATION
        }

        val statistics = linkedMapOf<String, Any?>(
            "averageCycleLength" to observedCycleLengths.takeIf { it.isNotEmpty() }
                ?.average()?.let { (it * 10.0).roundToInt() / 10.0 },
            "medianCycleLength" to observedMedian,
            "shortestCycle" to observedCycleLengths.minOrNull(),
            "longestCycle" to observedCycleLengths.maxOrNull(),
            "typicalPeriodDuration" to typicalPeriodLength,
            "medianAbsoluteDeviation" to mad,
            "variationDays" to variationDays,
            "consideredCycleCount" to modelCycleLengths.size,
            "allObservedCycleLengths" to observedCycleLengths,
            "observedCycleCount" to observedCycleLengths.size,
            "modelCycleLengths" to modelCycleLengths,
            "excludedCycleLengths" to excludedCycleLengths,
            "excludedCycleCount" to excludedCycleLengths.size,
            "modelMedianCycleLength" to modelMedian,
            "completedPeriodDurationCount" to modelPeriodDurations.size,
            "allObservedPeriodDurations" to observedPeriodDurations,
            "excludedPeriodDurations" to excludedPeriodDurations,
            "periodDurationDataBasis" to periodDurationDataBasis,
        )

        if (modelCycleLengths.isEmpty() && fallbackCycleLength == null) {
            return unavailable(
                reason = "INSUFFICIENT_DATA",
                clock = clock,
                basedOnCycleCount = 0,
                statistics = statistics,
            )
        }

        val typicalCycleLength = if (modelCycleLengths.isNotEmpty()) {
            weightedMedian(modelCycleLengths)
        } else {
            requireNotNull(fallbackCycleLength)
        }
        val center = starts.last().plusDays(typicalCycleLength.toLong())
        val startFrom = center.minusDays(variationDays.toLong())
        val startTo = center.plusDays(variationDays.toLong())
        val confidence = confidence(modelCycleLengths.size, robustSpread, rangeSignal)
        val referenceDate = strictDate(input.referenceDate)
        if (startTo.isBefore(referenceDate)) {
            return unavailable(
                reason = "STALE_HISTORY",
                clock = clock,
                basedOnCycleCount = modelCycleLengths.size,
                statistics = statistics,
            )
        }

        val fertilityEnabled = input.fertilityPredictionEnabled
        val fertilityHasEnoughData = modelCycleLengths.size >= 3
        val fertilityDataReliable = excludedCycleLengths.isEmpty() &&
            confidence != "LOW" && variationDays <= MAX_FERTILITY_VARIATION_DAYS
        val fertilityAvailable = fertilityEnabled && fertilityHasEnoughData && fertilityDataReliable
        val lutealLength = input.lutealLength?.takeIf { it in 8..20 } ?: 14
        val ovulationCenter = center.minusDays(lutealLength.toLong())
        val fertileFrom = if (fertilityAvailable) {
            ovulationCenter.minusDays((5 + variationDays).toLong()).toString()
        } else null
        val fertileTo = if (fertilityAvailable) {
            ovulationCenter.plusDays((1 + variationDays).toLong()).toString()
        } else null

        return PredictionDto(
            available = true,
            predictedStartFrom = startFrom.toString(),
            predictedStartTo = startTo.toString(),
            predictedEndFrom = startFrom.plusDays((typicalPeriodLength - 1).toLong()).toString(),
            predictedEndTo = startTo.plusDays((typicalPeriodLength - 1).toLong()).toString(),
            fertileWindowFrom = fertileFrom,
            fertileWindowTo = fertileTo,
            fertilityEstimate = FertilityEstimateDto(
                enabled = fertilityEnabled,
                available = fertilityAvailable,
                estimated = true,
                reason = when {
                    fertilityAvailable -> "AVAILABLE"
                    !fertilityEnabled -> "DISABLED"
                    !fertilityHasEnoughData -> "INSUFFICIENT_DATA"
                    else -> "UNRELIABLE_DATA"
                },
                basedOnCycleCount = modelCycleLengths.size,
                notContraception = true,
                doesNotConfirmOvulation = true,
            ),
            confidence = confidence,
            basedOnCycleCount = modelCycleLengths.size,
            explanation = linkedMapOf(
                "method" to "recency-weighted-median-with-mad",
                "dataBasis" to if (modelCycleLengths.isNotEmpty()) "observed-cycles" else "profile-fallback",
                "cyclesUsed" to modelCycleLengths.size,
                "outliersRetained" to true,
                "excludedImplausibleIntervalsFromModel" to excludedCycleLengths.size,
                "uncertainty" to when (confidence) {
                    "HIGH" -> "low"
                    "MEDIUM" -> "medium"
                    else -> "high"
                },
                "note" to when {
                    modelCycleLengths.size < 3 ->
                        "Fewer than three usable complete cycles are available; the estimate is deliberately broad."
                    excludedCycleLengths.isNotEmpty() ->
                        "Implausible intervals remain visible in statistics but are excluded from the model and fertility estimate."
                    else ->
                        "The interval uses recent observed cycles, a robust median and median absolute deviation."
                },
            ),
            typicalCycleLength = typicalCycleLength,
            typicalPeriodLength = typicalPeriodLength,
            variationDays = variationDays,
            statistics = statistics,
            calculatedAt = clock.instant().toString(),
            algorithmVersion = ALGORITHM_VERSION,
        )
    }

    fun calculate(
        periods: List<PeriodEntity>,
        preferences: AppPreferencesState,
        referenceDate: LocalDate = LocalDate.now(),
        clock: Clock = Clock.systemUTC(),
    ): PredictionDto = calculate(
        Input(
            periods = periods
                .asSequence()
                .filter { it.deletedAt == null && it.userId == preferences.session?.userId }
                .map { HistoricalPeriod(it.startDate, it.endDate) }
                .toList(),
            referenceDate = referenceDate.toString(),
            typicalCycleLength = preferences.localTypicalCycleLength,
            typicalPeriodLength = preferences.localTypicalPeriodLength,
            lutealLength = preferences.localLutealLength,
            fertilityPredictionEnabled = preferences.localFertilityPredictionEnabled,
            predictionPaused = preferences.localPredictionPaused,
            specialMode = preferences.localSpecialMode,
        ),
        clock,
    )

    private fun strictDate(value: String): LocalDate {
        require(isoDatePattern.matches(value)) { "Invalid local date: $value" }
        return runCatching { LocalDate.parse(value, strictIsoDate) }
            .getOrElse { throw IllegalArgumentException("Invalid local date: $value", it) }
    }

    private fun median(values: List<Int>): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle].toDouble()
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    private fun medianDoubles(values: List<Double>): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    private fun weightedMedian(values: List<Int>): Int {
        require(values.isNotEmpty())
        val weighted = values.mapIndexed { index, value ->
            val weight = if (values.size == 1) 1.0 else 1.0 + index.toDouble() / (values.size - 1)
            WeightedValue(value, weight)
        }.sortedBy(WeightedValue::value)
        val threshold = weighted.sumOf(WeightedValue::weight) / 2.0
        var accumulated = 0.0
        weighted.forEach { item ->
            accumulated += item.weight
            if (accumulated >= threshold) return item.value
        }
        return weighted.last().value
    }

    private fun confidence(cycleCount: Int, robustSpread: Int, rangeSignal: Int): String = when {
        cycleCount < 3 -> "LOW"
        cycleCount >= 6 && robustSpread <= 2 && rangeSignal <= 3 -> "HIGH"
        robustSpread <= 5 && rangeSignal <= 6 -> "MEDIUM"
        else -> "LOW"
    }

    private fun unavailable(
        reason: String,
        clock: Clock,
        basedOnCycleCount: Int = 0,
        statistics: Map<String, Any?>? = null,
    ) = PredictionDto(
        available = false,
        confidence = "UNAVAILABLE",
        basedOnCycleCount = basedOnCycleCount,
        reason = reason,
        statistics = statistics,
        calculatedAt = clock.instant().toString(),
        algorithmVersion = ALGORITHM_VERSION,
    )

    private data class WeightedValue(val value: Int, val weight: Double)
}
