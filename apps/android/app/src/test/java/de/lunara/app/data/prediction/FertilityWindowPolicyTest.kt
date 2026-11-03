package de.lunara.app.data.prediction

import de.lunara.app.data.remote.dto.FertilityEstimateDto
import de.lunara.app.data.remote.dto.PredictionDto
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FertilityWindowPolicyTest {
    private val prediction = PredictionDto(
        confidence = "MEDIUM", fertileWindowFrom = "2026-11-01", fertileWindowTo = "2026-11-19",
        fertilityEstimate = FertilityEstimateDto(enabled = true, available = true, estimated = true,
            reason = "AVAILABLE", notContraception = true, doesNotConfirmOvulation = true),
    )

    @Test fun acceptsNineteenButRejectsTwentyAndLegacyThirtyThreeDaysWithoutClipping() {
        assertEquals(FertilityWindow(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-19")),
            FertilityWindowPolicy.validated(prediction))
        for (end in listOf("2026-11-20", "2026-12-03")) {
            assertNull(FertilityWindowPolicy.validated(prediction.copy(fertileWindowTo = end)))
        }
    }

    @Test fun ownInclusiveEndExpiresCurrentButNeverHistoricalCalendarRange() {
        assertNotNull(FertilityWindowPolicy.current(prediction, LocalDate.parse("2026-11-19")))
        assertNull(FertilityWindowPolicy.current(prediction, LocalDate.parse("2026-11-20")))
        assertNotNull(FertilityWindowPolicy.validated(prediction))
        assertNull(FertilityWindowPolicy.current(prediction, LocalDate.parse("2026-11-01"), pendingChanges = true))
    }

    @Test fun independentFertilityDoesNotRequireWithheldPartnerPeriodForecast() {
        assertNotNull(FertilityWindowPolicy.validated(prediction.copy(available = false, confidence = "UNKNOWN", reason = "NOT_SHARED")))
    }

    @Test fun malformedMissingReversedOrIncompleteSafetyFailsClosed() {
        val safety = requireNotNull(prediction.fertilityEstimate)
        for (bad in listOf(
            prediction.copy(fertileWindowFrom = "2026-11-30"), prediction.copy(fertileWindowTo = null),
            prediction.copy(fertileWindowFrom = "2026-02-29"), prediction.copy(fertileWindowFrom = "2026-1-01"),
            prediction.copy(fertilityEstimate = null), prediction.copy(fertilityEstimate = safety.copy(enabled = false)),
            prediction.copy(fertilityEstimate = safety.copy(available = false)), prediction.copy(fertilityEstimate = safety.copy(estimated = false)),
            prediction.copy(fertilityEstimate = safety.copy(notContraception = false)),
            prediction.copy(fertilityEstimate = safety.copy(doesNotConfirmOvulation = false)),
            prediction.copy(fertilityEstimate = safety.copy(reason = "UNRELIABLE_DATA")),
        )) assertNull(FertilityWindowPolicy.validated(bad))
    }
}
