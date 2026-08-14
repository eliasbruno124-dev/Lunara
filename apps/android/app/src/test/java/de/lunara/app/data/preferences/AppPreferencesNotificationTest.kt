package de.lunara.app.data.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import de.lunara.app.notifications.NotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesNotificationTest {
    @Test
    fun `notification write is rejected after session owner changes`() {
        val values = mutablePreferencesOf(
            AppPreferences.USER_ID to "new-user",
            AppPreferences.NOTIFICATION_MASTER to false,
            AppPreferences.NOTIFICATION_HOUR to 7,
        )

        val written = values.writeNotificationPreferencesForSession(
            expectedUserId = "old-user",
            requested = NotificationPreferences(master = true, reminderHour = 18),
        )

        assertFalse(written)
        assertFalse(values[AppPreferences.NOTIFICATION_MASTER]!!)
        assertEquals(7, values[AppPreferences.NOTIFICATION_HOUR])
    }

    @Test
    fun `notification write for current owner persists the complete preference set`() {
        val values = mutablePreferencesOf(AppPreferences.USER_ID to "current-user")

        val written = values.writeNotificationPreferencesForSession(
            expectedUserId = "current-user",
            requested = NotificationPreferences(
                master = true,
                periodTomorrow = false,
                fertileTomorrow = true,
                dailyLogReminder = true,
                partnerTip = true,
                reminderHour = 18,
                reminderMinute = 45,
                quietStartHour = 21,
                quietStartMinute = 15,
                quietEndHour = 7,
                quietEndMinute = 30,
                hideSensitiveContent = false,
                maxPerDay = 3,
            ),
        )

        assertTrue(written)
        assertTrue(values[AppPreferences.NOTIFICATION_MASTER]!!)
        assertFalse(values[AppPreferences.NOTIFICATION_PERIOD_TOMORROW]!!)
        assertTrue(values[AppPreferences.NOTIFICATION_FERTILE_TOMORROW]!!)
        assertTrue(values[AppPreferences.NOTIFICATION_DAILY_LOG]!!)
        assertTrue(values[AppPreferences.NOTIFICATION_PARTNER_TIP]!!)
        assertEquals(18, values[AppPreferences.NOTIFICATION_HOUR])
        assertEquals(45, values[AppPreferences.NOTIFICATION_MINUTE])
        assertEquals(21, values[AppPreferences.NOTIFICATION_QUIET_START_HOUR])
        assertEquals(15, values[AppPreferences.NOTIFICATION_QUIET_START_MINUTE])
        assertEquals(7, values[AppPreferences.NOTIFICATION_QUIET_END_HOUR])
        assertEquals(30, values[AppPreferences.NOTIFICATION_QUIET_END_MINUTE])
        assertFalse(values[AppPreferences.NOTIFICATION_HIDE_SENSITIVE]!!)
        assertEquals(3, values[AppPreferences.NOTIFICATION_MAX_PER_DAY])
    }
}
