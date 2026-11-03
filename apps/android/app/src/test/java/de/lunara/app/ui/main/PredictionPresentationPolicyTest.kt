package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PartnerTipDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.remote.dto.FertilityEstimateDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import java.time.LocalDate
import org.junit.Test

class PredictionPresentationPolicyTest {
    @Test fun `current fertility expires separately from historical calendar and pending edits hide it`() {
        val prediction = PredictionDto(confidence = "UNKNOWN", available = false,
            fertileWindowFrom = "2026-11-01", fertileWindowTo = "2026-11-19",
            fertilityEstimate = FertilityEstimateDto(enabled = true, available = true, estimated = true,
                reason = "AVAILABLE", notContraception = true, doesNotConfirmOvulation = true))
        assertNotNull(PredictionPresentationPolicy.currentFertileWindow(prediction, LocalDate.parse("2026-11-19")))
        assertNull(PredictionPresentationPolicy.currentFertileWindow(prediction, LocalDate.parse("2026-11-20")))
        assertNull(PredictionPresentationPolicy.currentFertileWindow(prediction, LocalDate.parse("2026-11-01"), true))
        assertNotNull(PredictionPresentationPolicy.fertileWindow(prediction))
        assertNull(PredictionPresentationPolicy.fertileWindow(prediction.copy(fertileWindowTo = "2026-11-20")))
        assertNull(PredictionPresentationPolicy.fertileWindow(prediction.copy(fertileWindowTo = "2026-12-03")))
    }
    @Test
    fun `fertile window is shown only when the server provides a complete valid range`() {
        val shared = PredictionDto(
            available = true,
            confidence = "MEDIUM",
            fertileWindowFrom = "2026-09-10",
            fertileWindowTo = "2026-09-16",
            fertilityEstimate = FertilityEstimateDto(
                enabled = true,
                available = true,
                estimated = true,
                reason = "AVAILABLE",
                basedOnCycleCount = 6,
                notContraception = true,
                doesNotConfirmOvulation = true,
            ),
        )
        assertEquals(
            PredictionDateRange("2026-09-10", "2026-09-16"),
            PredictionPresentationPolicy.fertileWindow(shared),
        )
        assertNull(PredictionPresentationPolicy.fertileWindow(shared.copy(fertileWindowTo = null)))
        assertNull(PredictionPresentationPolicy.fertileWindow(shared.copy(fertileWindowTo = "2026-09-01")))
        assertNull(
            PredictionPresentationPolicy.fertileWindow(
                shared.copy(fertilityEstimate = shared.fertilityEstimate?.copy(notContraception = false)),
            ),
        )
    }

    @Test
    fun `partner phase is never inferred locally from prediction dates`() {
        val predictionOnly = PartnerDashboardDto(
            prediction = PredictionDto(
                available = true,
                confidence = "HIGH",
                predictedStartFrom = "2026-09-18",
                predictedStartTo = "2026-09-21",
            ),
        )
        assertNull(PredictionPresentationPolicy.disclosedPartnerPhase(predictionOnly))
        assertNull(PredictionPresentationPolicy.phaseAwareTipPhase(predictionOnly))
    }

    @Test
    fun `tip is labelled phase aware only for a server disclosed known phase`() {
        val tip = PartnerTipDto("tip", "Ask first", "Body", "Action", "COMMUNICATION", "LUTEAL_ESTIMATE")
        val dashboard = PartnerDashboardDto(phase = "LUTEAL_ESTIMATE", tip = tip)
        assertEquals("LUTEAL_ESTIMATE", PredictionPresentationPolicy.disclosedPartnerPhase(dashboard))
        assertEquals("LUTEAL_ESTIMATE", PredictionPresentationPolicy.phaseAwareTipPhase(dashboard))
        assertNull(PredictionPresentationPolicy.phaseAwareTipPhase(dashboard.copy(tip = tip.copy(applicablePhase = null))))
        assertNull(
            PredictionPresentationPolicy.disclosedPartnerPhase(
                dashboard.copy(phase = "UNTRUSTED_PHASE"),
            ),
        )
    }
}
