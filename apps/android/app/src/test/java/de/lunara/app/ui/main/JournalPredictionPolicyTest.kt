package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.PredictionDto
import org.junit.Assert.*
import org.junit.Test

class JournalPredictionPolicyTest {
    private val valid = PredictionDto(confidence = "MEDIUM", predictedStartFrom = "2026-10-01", predictedStartTo = "2026-10-04")
    @Test fun validWindowIsPresented() { assertTrue(JournalPredictionPolicy.hasStartWindow(valid)) }
    @Test fun pausedCannotShowOldDates() { assertFalse(JournalPredictionPolicy.hasStartWindow(valid.copy(available = false, reason = "PREDICTION_PAUSED"))) }
    @Test fun missingAndMalformedDatesFailClosed() {
        assertFalse(JournalPredictionPolicy.hasStartWindow(null))
        assertFalse(JournalPredictionPolicy.hasStartWindow(valid.copy(predictedStartFrom = null)))
        assertFalse(JournalPredictionPolicy.hasStartWindow(valid.copy(predictedStartTo = "invalid")))
    }
    @Test fun reversedWindowIsNotPresented() { assertFalse(JournalPredictionPolicy.hasStartWindow(valid.copy(predictedStartTo = "2026-09-30"))) }
}
