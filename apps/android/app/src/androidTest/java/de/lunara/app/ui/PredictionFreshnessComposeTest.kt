package de.lunara.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.lunara.app.R
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.ui.main.PredictionHero
import de.lunara.app.ui.main.PartnerDashboard
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import de.lunara.app.ui.theme.LunaraTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/** Synthetic reference dates simulate midnight/offline resume without changing device time. */
class PredictionFreshnessComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val prediction = PredictionDto(
        confidence = "MEDIUM", basedOnCycleCount = 3,
        predictedStartFrom = "2026-04-20", predictedStartTo = "2026-04-26",
    )

    @Test fun sameCachedForecastExpiresAndClosesItsDetailsAfterTheDateAdvances() {
        val referenceDate = mutableStateOf(LocalDate.parse("2026-04-26"))
        compose.setContent { LunaraTheme { PredictionHero(prediction, referenceDate = referenceDate.value) } }
        compose.onNodeWithTag("prediction-details").assertIsDisplayed().performClick()
        compose.runOnIdle { referenceDate.value = referenceDate.value.plusDays(1) }
        compose.onNodeWithTag("prediction-details").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.journal_prediction_stale)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.close)).assertDoesNotExist()
    }

    @Test fun queuedChangesDoNotPresentAnOldServerForecastAsCurrent() {
        val pending = mutableStateOf(true)
        compose.setContent { LunaraTheme { PredictionHero(prediction, referenceDate = LocalDate.parse("2026-04-26"), pendingChanges = pending.value) } }
        compose.onNodeWithTag("prediction-details").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.journal_prediction_pending)).assertIsDisplayed()
        compose.runOnIdle { pending.value = false }
        compose.onNodeWithTag("prediction-details").assertIsDisplayed()
    }

    @Test fun disclosedRecordedPhaseRemainsLastSharedWithoutInferringAPrediction() {
        compose.setContent {
            LunaraTheme {
                PartnerDashboard("Demo", PartnerDashboardDto(phase = "MENSTRUATION"), true, {})
            }
        }
        val label = compose.activity.getString(R.string.current_estimated_phase)
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithTag("prediction-hero").assertDoesNotExist()
    }
}
