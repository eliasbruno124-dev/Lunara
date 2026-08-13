package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PartnerTipDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.remote.dto.FertilityEstimateDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PredictionPresentationPolicyTest {
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
