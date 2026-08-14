package de.lunara.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.lunara.app.core.model.UserRole
import de.lunara.app.notifications.NotificationPreferences
import de.lunara.app.ui.main.NotificationControls
import de.lunara.app.ui.theme.LunaraTheme
import org.junit.Rule
import org.junit.Test

class NotificationSettingsComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun trackerCanChoosePeriodFertilityAndLoggingCategories() {
        var preferences by mutableStateOf(NotificationPreferences())
        compose.setContent {
            LunaraTheme {
                NotificationControls(
                    role = UserRole.TRACKER,
                    preferences = preferences,
                    fertilityPredictionEnabled = true,
                    onUpdate = { preferences = it },
                )
            }
        }

        compose.onNodeWithTag("notification-period-tomorrow").assertIsOn()
        compose.onNodeWithTag("notification-fertile-tomorrow").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("notification-daily-log").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("notification-partner-tip").assertDoesNotExist()
    }

    @Test
    fun partnerGetsSupportTipChoiceButNoPrivateLoggingReminder() {
        var preferences by mutableStateOf(NotificationPreferences())
        compose.setContent {
            LunaraTheme {
                NotificationControls(
                    role = UserRole.PARTNER,
                    preferences = preferences,
                    fertilityPredictionEnabled = false,
                    onUpdate = { preferences = it },
                )
            }
        }

        compose.onNodeWithTag("notification-partner-tip").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("notification-daily-log").assertDoesNotExist()
    }
}
