package de.lunara.app.data.prediction

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPredictionEngineTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-26T12:00:00Z"), ZoneOffset.UTC)

    @Test fun unconfirmedHistoricalEndsDoNotBecomeObservedFiveDaySamples() {
        val periods = periodsForIntervals(listOf(28, 28, 28))
        val input = LocalPredictionEngine.Input(periods, periods.last().startDate, fertilityPredictionEnabled = true)
        val raw = LocalPredictionEngine.calculate(input, clock)
        assertEquals(0, raw.statistics?.get("completedPeriodDurationCount"))
        assertEquals(emptyList<Int>(), raw.statistics?.get("allObservedPeriodDurations"))
        assertEquals("default-prior", raw.statistics?.get("periodDurationDataBasis"))
        val manual = periods.first().copy(endDate = LocalDate.parse(periods.first().startDate).plusDays(7).toString())
        val corrected = LocalPredictionEngine.calculate(input.copy(periods = listOf(manual) + periods.drop(1)), clock)
        assertEquals(1, corrected.statistics?.get("completedPeriodDurationCount"))
        assertEquals(listOf(8), corrected.statistics?.get("allObservedPeriodDurations"))
        assertEquals(8, corrected.typicalPeriodLength)
        assertNotNull(FertilityWindowPolicy.validated(raw))
    }

    @Test fun legacyWideCycleFixtureRemainsSuppressedByUnchangedV2Formula() {
        val periods = periodsForIntervals(listOf(20, 28, 36))
        val result = LocalPredictionEngine.calculate(LocalPredictionEngine.Input(periods, periods.last().startDate, fertilityPredictionEnabled = true), clock)
        assertFalse(requireNotNull(result.fertilityEstimate).available)
        assertNull(result.fertileWindowFrom)
        assertNull(FertilityWindowPolicy.validated(result))
    }

    @Test
    fun canonicalVectorMatchesServerContract() {
        val result = LocalPredictionEngine.calculate(
            LocalPredictionEngine.Input(
                periods = listOf("2026-01-01", "2026-01-29", "2026-02-26", "2026-03-26")
                    .map { LocalPredictionEngine.HistoricalPeriod(it) },
                referenceDate = "2026-03-26",
                fertilityPredictionEnabled = true,
            ),
            clock,
        )

        assertTrue(result.available)
        assertEquals("2026-04-20", result.predictedStartFrom)
        assertEquals("2026-04-26", result.predictedStartTo)
        assertEquals("2026-04-24", result.predictedEndFrom)
        assertEquals("2026-04-30", result.predictedEndTo)
        assertEquals("2026-04-01", result.fertileWindowFrom)
        assertEquals("2026-04-13", result.fertileWindowTo)
        assertEquals("MEDIUM", result.confidence)
        assertEquals(3, result.basedOnCycleCount)
        assertEquals(28, result.typicalCycleLength)
        assertEquals(5, result.typicalPeriodLength)
        assertEquals(3, result.variationDays)
        assertEquals("default-prior", result.statistics?.get("periodDurationDataBasis"))
        assertEquals(LocalPredictionEngine.ALGORITHM_VERSION, result.algorithmVersion)
    }

    @Test
    fun staleHistoryFailsClosed() {
        val result = LocalPredictionEngine.calculate(
            LocalPredictionEngine.Input(
                periods = listOf("2026-01-01", "2026-01-29", "2026-02-26", "2026-03-26")
                    .map { LocalPredictionEngine.HistoricalPeriod(it) },
                referenceDate = "2026-05-01",
                fertilityPredictionEnabled = true,
            ),
            clock,
        )

        assertFalse(result.available)
        assertEquals("STALE_HISTORY", result.reason)
        assertNull(result.fertileWindowFrom)
        assertNull(result.fertileWindowTo)
    }

    @Test
    fun oneDayIntervalsCannotCreatePredictionOrFertilityWindow() {
        val result = LocalPredictionEngine.calculate(
            LocalPredictionEngine.Input(
                periods = listOf("2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04")
                    .map { LocalPredictionEngine.HistoricalPeriod(it) },
                referenceDate = "2026-01-04",
                fertilityPredictionEnabled = true,
            ),
            clock,
        )

        assertFalse(result.available)
        assertEquals("INSUFFICIENT_DATA", result.reason)
        assertEquals(emptyList<Int>(), result.statistics?.get("modelCycleLengths"))
        assertEquals(listOf(1, 1, 1), result.statistics?.get("excludedCycleLengths"))
    }

    @Test
    fun excludedGapRemainsVisibleAndClosesFertility() {
        val periods = periodsForIntervals(listOf(28, 28, 365, 28))
        val result = LocalPredictionEngine.calculate(
            LocalPredictionEngine.Input(
                periods = periods,
                referenceDate = periods.last().startDate,
                fertilityPredictionEnabled = true,
            ),
            clock,
        )

        assertTrue(result.available)
        assertEquals(listOf(28, 28, 28), result.statistics?.get("modelCycleLengths"))
        assertEquals(listOf(365), result.statistics?.get("excludedCycleLengths"))
        assertFalse(requireNotNull(result.fertilityEstimate).available)
        assertEquals("UNRELIABLE_DATA", result.fertilityEstimate.reason)
    }

    @Test
    fun periodDurationSelectionIsIndependentOfInputOrder() {
        val periods = periodsForIntervals(List(12) { 28 }).mapIndexed { index, period ->
            period.copy(endDate = LocalDate.parse(period.startDate).plusDays(if (index < 6) 0 else 13).toString())
        }
        fun calculate(values: List<LocalPredictionEngine.HistoricalPeriod>) = LocalPredictionEngine.calculate(
            LocalPredictionEngine.Input(values, periods.last().startDate),
            clock,
        )

        assertEquals(14, calculate(periods).typicalPeriodLength)
        assertEquals(14, calculate(periods.reversed()).typicalPeriodLength)
    }

    @Test
    fun observedSixteenDayDurationAndLatestTwelveMatchWebRegressionFixtures() {
        val longPeriods = periodsForIntervals(List(3) { 28 }).map { period ->
            period.copy(endDate = LocalDate.parse(period.startDate).plusDays(15).toString())
        }
        assertEquals(16, LocalPredictionEngine.calculate(LocalPredictionEngine.Input(longPeriods, longPeriods.last().startDate, typicalPeriodLength = 7), clock).typicalPeriodLength)
        val periods = periodsForIntervals(List(24) { 28 }).mapIndexed { index, period ->
            period.copy(endDate = LocalDate.parse(period.startDate).plusDays(if (index < 13) 1 else 7).toString())
        }
        for (ordered in listOf(periods, periods.reversed(), periods.filterIndexed { index, _ -> index % 2 == 0 } + periods.filterIndexed { index, _ -> index % 2 != 0 })) {
            assertEquals(8, LocalPredictionEngine.calculate(LocalPredictionEngine.Input(ordered, periods.last().startDate), clock).typicalPeriodLength)
        }
    }

    @Test
    fun oversizedDurationExclusionHappensAfterSelectingTheLatestTwelve() {
        val periods = periodsForIntervals(List(13) { 60 }).mapIndexed { index, period ->
            period.copy(endDate = LocalDate.parse(period.startDate).plusDays(if (index < 2) 1 else 39).toString())
        }
        val input = LocalPredictionEngine.Input(periods, periods.last().startDate)
        assertEquals(5, LocalPredictionEngine.calculate(input, clock).typicalPeriodLength)
        assertEquals(9, LocalPredictionEngine.calculate(input.copy(typicalPeriodLength = 9), clock).typicalPeriodLength)
    }

    @Test
    fun fallbackRangesAndConfidenceMatchWebRegressionFixtures() {
        val single = LocalPredictionEngine.Input(periodsForIntervals(emptyList()), "2024-01-02")
        for (fallback in listOf(0, 14, 91)) {
            assertEquals("INSUFFICIENT_DATA", LocalPredictionEngine.calculate(single.copy(typicalCycleLength = fallback), clock).reason)
        }
        for (fallback in listOf(15, 90)) {
            val result = LocalPredictionEngine.calculate(single.copy(typicalCycleLength = fallback), clock)
            assertTrue(result.available)
            assertEquals("LOW", result.confidence)
            assertEquals(4, result.variationDays)
        }
        for ((intervals, confidence, halfWidth) in listOf(Triple(1, "LOW", 4), Triple(3, "MEDIUM", 3), Triple(6, "HIGH", 2))) {
            val periods = periodsForIntervals(List(intervals) { 28 })
            val input = LocalPredictionEngine.Input(periods, periods.last().startDate, fertilityPredictionEnabled = true)
            val result = LocalPredictionEngine.calculate(input, clock)
            assertEquals(confidence, result.confidence)
            assertEquals(halfWidth, result.variationDays)
            assertEquals(intervals >= 3, result.fertilityEstimate?.available)
            assertFalse(requireNotNull(LocalPredictionEngine.calculate(input.copy(fertilityPredictionEnabled = false), clock).fertilityEstimate).available)
        }
    }

    @Test
    fun latestStartEqualityRemainsAvailableBeforeTheNextDayExpires() {
        val periods = periodsForIntervals(List(3) { 28 })
        val input = LocalPredictionEngine.Input(periods, periods.last().startDate)
        val end = requireNotNull(LocalPredictionEngine.calculate(input, clock).predictedStartTo)
        assertTrue(LocalPredictionEngine.calculate(input.copy(referenceDate = end), clock).available)
        assertEquals("STALE_HISTORY", LocalPredictionEngine.calculate(input.copy(referenceDate = LocalDate.parse(end).plusDays(1).toString()), clock).reason)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidIsoDateIsRejectedInsteadOfTimezoneNormalized() {
        LocalPredictionEngine.calculate(
            LocalPredictionEngine.Input(
                periods = listOf(LocalPredictionEngine.HistoricalPeriod("2026-02-30")),
                referenceDate = "2026-02-28",
            ),
            clock,
        )
    }

    private fun periodsForIntervals(intervals: List<Int>): List<LocalPredictionEngine.HistoricalPeriod> {
        var cursor = LocalDate.parse("2024-01-01")
        return buildList {
            add(LocalPredictionEngine.HistoricalPeriod(cursor.toString()))
            intervals.forEach { interval ->
                cursor = cursor.plusDays(interval.toLong())
                add(LocalPredictionEngine.HistoricalPeriod(cursor.toString()))
            }
        }
    }
}
