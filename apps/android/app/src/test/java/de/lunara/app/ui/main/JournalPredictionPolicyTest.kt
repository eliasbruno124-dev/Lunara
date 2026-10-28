package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.PredictionDto
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JournalPredictionPolicyTest {
    private val valid = PredictionDto(confidence = "MEDIUM", predictedStartFrom = "2026-10-01", predictedStartTo = "2026-10-04")
    private val today = LocalDate.of(2026, 10, 4)
    @Test fun validWindowIsPresented() { assertTrue(JournalPredictionPolicy.hasStartWindow(valid, today)) }
    @Test fun pausedCannotShowOldDates() { assertFalse(JournalPredictionPolicy.hasStartWindow(valid.copy(available = false, reason = "PREDICTION_PAUSED"), today)) }
    @Test fun missingAndMalformedDatesFailClosed() {
        assertFalse(JournalPredictionPolicy.hasStartWindow(null, today))
        assertFalse(JournalPredictionPolicy.hasStartWindow(valid.copy(predictedStartFrom = null), today))
        assertFalse(JournalPredictionPolicy.hasStartWindow(valid.copy(predictedStartTo = "invalid"), today))
    }
    @Test fun reversedWindowIsNotPresented() { assertFalse(JournalPredictionPolicy.hasStartWindow(valid.copy(predictedStartTo = "2026-09-30"), today)) }
    @Test fun expiredCachedWindowIsNotPresented() { assertFalse(JournalPredictionPolicy.hasStartWindow(valid, today.plusDays(1))) }
}
