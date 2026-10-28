package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.remote.dto.FertilityEstimateDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PredictionFreshnessPolicyTest {
    private val prediction = PredictionDto(
        confidence = "MEDIUM", predictedStartFrom = "2026-04-20", predictedStartTo = "2026-04-26",
        predictedEndFrom = "2026-04-24", predictedEndTo = "2026-04-30",
    )
    @Test fun `same cached value becomes stale only after the inclusive start window`() {
        val today = LocalDate.parse("2026-04-26")
        assertNull(PredictionFreshnessPolicy.unavailableReason(prediction, today))
        assertEquals("STALE_HISTORY", PredictionFreshnessPolicy.unavailableReason(prediction, today.plusDays(1)))
        assertTrue(requireNotNull(CalendarPredictionBand.from(prediction)).contains(today.plusDays(1)))
        assertTrue(prediction.available)
        assertEquals("2026-04-30", prediction.predictedEndTo)
    }
    @Test fun `pending changes suppress current claim without modifying dates or paused reason`() {
        val today = LocalDate.parse("2026-04-26")
        assertEquals("PENDING_CHANGES", PredictionFreshnessPolicy.unavailableReason(prediction, today, true))
        assertNull(PredictionFreshnessPolicy.unavailableReason(prediction, today, false))
        assertEquals("PREDICTION_PAUSED", PredictionFreshnessPolicy.unavailableReason(prediction.copy(available = false, reason = "PREDICTION_PAUSED"), today, true))
    }
    @Test fun `known profile timezone wins over device timezone at midnight`() {
        val now = Instant.parse("2026-04-26T22:30:00Z")
        val device = ZoneId.of("America/Los_Angeles")
        assertEquals(LocalDate.parse("2026-04-27"), PredictionFreshnessPolicy.referenceDate(now, "Europe/Berlin", device))
        assertEquals(LocalDate.parse("2026-04-26"), PredictionFreshnessPolicy.referenceDate(now, null, device))
        assertEquals(LocalDate.parse("2026-04-26"), PredictionFreshnessPolicy.referenceDate(now, "invalid", device))
    }
    @Test fun `next day delay respects DST and is never a busy polling loop`() {
        val berlin = "Europe/Berlin"
        assertEquals(23L * 60 * 60 * 1000 + 25, PredictionFreshnessPolicy.millisecondsUntilNextDay(Instant.parse("2026-03-28T23:00:00Z"), berlin))
        assertEquals(25L * 60 * 60 * 1000 + 25, PredictionFreshnessPolicy.millisecondsUntilNextDay(Instant.parse("2026-10-24T22:00:00Z"), berlin))
        assertEquals(525L, PredictionFreshnessPolicy.millisecondsUntilNextDay(Instant.parse("2026-04-26T21:59:59.500Z"), berlin))
    }
    @Test fun `hidden period dates do not revoke an independently disclosed fertility estimate`() {
        val shared = PredictionDto(
            confidence = "NONE", available = false,
            fertileWindowFrom = "2026-04-20", fertileWindowTo = "2026-04-30",
            fertilityEstimate = FertilityEstimateDto(true, true, true, "AVAILABLE", 4, true, true),
        )
        assertEquals("UNAVAILABLE", PredictionFreshnessPolicy.unavailableReason(shared, LocalDate.parse("2026-04-27")))
        assertNotNull(PredictionPresentationPolicy.fertileWindow(shared))
        assertNull(shared.predictedStartFrom)
    }
}
