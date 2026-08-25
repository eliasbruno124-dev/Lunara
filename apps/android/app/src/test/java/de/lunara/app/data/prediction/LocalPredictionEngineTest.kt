package de.lunara.app.data.prediction

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPredictionEngineTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-26T12:00:00Z"), ZoneOffset.UTC)

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
