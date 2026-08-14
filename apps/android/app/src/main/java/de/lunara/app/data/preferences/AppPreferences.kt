package de.lunara.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lunara.app.core.model.AppSession
import de.lunara.app.core.model.UserRole
import de.lunara.app.notifications.NotificationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lunaraDataStore: DataStore<Preferences> by preferencesDataStore(name = "lunara_preferences")

data class AppPreferencesState(
    val baseUrl: String? = null,
    val serverName: String? = null,
    val serverVersion: String? = null,
    val session: AppSession? = null,
    val privacyMode: Boolean = true,
    val useSystemTheme: Boolean = true,
    val darkMode: Boolean = false,
    val lastSyncAt: String? = null,
    val notifications: NotificationPreferences = NotificationPreferences(),
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val state: Flow<AppPreferencesState> = context.lunaraDataStore.data.map { values ->
        val role = values[ROLE]?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
        val userId = values[USER_ID]
        AppPreferencesState(
            baseUrl = values[BASE_URL],
            serverName = values[SERVER_NAME],
            serverVersion = values[SERVER_VERSION],
            session = if (role != null && userId != null) {
                AppSession(
                    userId = userId,
                    displayName = values[DISPLAY_NAME].orEmpty().ifBlank { values[USERNAME].orEmpty() },
                    role = role,
                    serverName = values[SERVER_NAME].orEmpty(),
                    serverVersion = values[SERVER_VERSION].orEmpty(),
                )
            } else null,
            privacyMode = values[PRIVACY_MODE] ?: true,
            useSystemTheme = values[USE_SYSTEM_THEME] ?: true,
            darkMode = values[DARK_MODE] ?: false,
            lastSyncAt = values[LAST_SYNC],
            notifications = NotificationPreferences(
                master = values[NOTIFICATION_MASTER] ?: false,
                periodTomorrow = values[NOTIFICATION_PERIOD_TOMORROW] ?: true,
                fertileTomorrow = values[NOTIFICATION_FERTILE_TOMORROW] ?: false,
                dailyLogReminder = values[NOTIFICATION_DAILY_LOG] ?: false,
                partnerTip = values[NOTIFICATION_PARTNER_TIP] ?: false,
                reminderHour = (values[NOTIFICATION_HOUR] ?: 9).coerceIn(0, 23),
                reminderMinute = (values[NOTIFICATION_MINUTE] ?: 0).coerceIn(0, 59),
                quietStartHour = (values[NOTIFICATION_QUIET_START_HOUR] ?: 22).coerceIn(0, 23),
                quietStartMinute = (values[NOTIFICATION_QUIET_START_MINUTE] ?: 0).coerceIn(0, 59),
                quietEndHour = (values[NOTIFICATION_QUIET_END_HOUR] ?: 8).coerceIn(0, 23),
                quietEndMinute = (values[NOTIFICATION_QUIET_END_MINUTE] ?: 0).coerceIn(0, 59),
                hideSensitiveContent = values[NOTIFICATION_HIDE_SENSITIVE] ?: true,
                maxPerDay = (values[NOTIFICATION_MAX_PER_DAY] ?: 2).coerceIn(1, 3),
            ),
        )
    }

    suspend fun saveConnection(baseUrl: String, serverName: String, serverVersion: String) {
        context.lunaraDataStore.edit {
            it[BASE_URL] = baseUrl
            it[SERVER_NAME] = serverName
            it[SERVER_VERSION] = serverVersion
        }
    }

    suspend fun saveSession(userId: String, username: String, displayName: String, role: UserRole) {
        context.lunaraDataStore.edit {
            it[USER_ID] = userId
            it[USERNAME] = username
            it[DISPLAY_NAME] = displayName
            it[ROLE] = role.name
        }
    }

    suspend fun setPrivacyMode(enabled: Boolean) {
        context.lunaraDataStore.edit { it[PRIVACY_MODE] = enabled }
    }

    suspend fun setTheme(useSystem: Boolean, dark: Boolean) {
        context.lunaraDataStore.edit {
            it[USE_SYSTEM_THEME] = useSystem
            it[DARK_MODE] = dark
        }
    }

    suspend fun setLastSync(instant: String) {
        context.lunaraDataStore.edit { it[LAST_SYNC] = instant }
    }

    suspend fun setNotificationPreferences(
        expectedUserId: String,
        requested: NotificationPreferences,
    ) {
        context.lunaraDataStore.edit { values ->
            values.writeNotificationPreferencesForSession(expectedUserId, requested)
        }
    }

    suspend fun clearAccount() {
        context.lunaraDataStore.edit { values ->
            listOf(USER_ID, USERNAME, DISPLAY_NAME, ROLE, BASE_URL, SERVER_NAME, SERVER_VERSION, LAST_SYNC)
                .forEach(values::remove)
        }
    }

    suspend fun clearAll() {
        context.lunaraDataStore.edit { it.clear() }
    }

    internal companion object {
        val BASE_URL = stringPreferencesKey("server.base_url")
        val SERVER_NAME = stringPreferencesKey("server.name")
        val SERVER_VERSION = stringPreferencesKey("server.version")
        val USER_ID = stringPreferencesKey("session.user_id")
        val USERNAME = stringPreferencesKey("session.username")
        val DISPLAY_NAME = stringPreferencesKey("session.display_name")
        val ROLE = stringPreferencesKey("session.role")
        val PRIVACY_MODE = booleanPreferencesKey("privacy.flag_secure")
        val USE_SYSTEM_THEME = booleanPreferencesKey("appearance.system")
        val DARK_MODE = booleanPreferencesKey("appearance.dark")
        val LAST_SYNC = stringPreferencesKey("sync.last_success")
        val NOTIFICATION_MASTER = booleanPreferencesKey("notifications.master")
        val NOTIFICATION_PERIOD_TOMORROW = booleanPreferencesKey("notifications.period_tomorrow")
        val NOTIFICATION_FERTILE_TOMORROW = booleanPreferencesKey("notifications.fertile_tomorrow")
        val NOTIFICATION_DAILY_LOG = booleanPreferencesKey("notifications.daily_log")
        val NOTIFICATION_PARTNER_TIP = booleanPreferencesKey("notifications.partner_tip")
        val NOTIFICATION_HOUR = intPreferencesKey("notifications.hour")
        val NOTIFICATION_MINUTE = intPreferencesKey("notifications.minute")
        val NOTIFICATION_QUIET_START_HOUR = intPreferencesKey("notifications.quiet_start_hour")
        val NOTIFICATION_QUIET_START_MINUTE = intPreferencesKey("notifications.quiet_start_minute")
        val NOTIFICATION_QUIET_END_HOUR = intPreferencesKey("notifications.quiet_end_hour")
        val NOTIFICATION_QUIET_END_MINUTE = intPreferencesKey("notifications.quiet_end_minute")
        val NOTIFICATION_HIDE_SENSITIVE = booleanPreferencesKey("notifications.hide_sensitive")
        val NOTIFICATION_MAX_PER_DAY = intPreferencesKey("notifications.max_per_day")
    }
}

internal fun MutablePreferences.writeNotificationPreferencesForSession(
    expectedUserId: String,
    requested: NotificationPreferences,
): Boolean {
    if (this[AppPreferences.USER_ID] != expectedUserId) return false

    val value = requested.copy(
        reminderHour = requested.reminderHour.coerceIn(0, 23),
        reminderMinute = requested.reminderMinute.coerceIn(0, 59),
        quietStartHour = requested.quietStartHour.coerceIn(0, 23),
        quietStartMinute = requested.quietStartMinute.coerceIn(0, 59),
        quietEndHour = requested.quietEndHour.coerceIn(0, 23),
        quietEndMinute = requested.quietEndMinute.coerceIn(0, 59),
        maxPerDay = requested.maxPerDay.coerceIn(1, 3),
    )
    this[AppPreferences.NOTIFICATION_MASTER] = value.master
    this[AppPreferences.NOTIFICATION_PERIOD_TOMORROW] = value.periodTomorrow
    this[AppPreferences.NOTIFICATION_FERTILE_TOMORROW] = value.fertileTomorrow
    this[AppPreferences.NOTIFICATION_DAILY_LOG] = value.dailyLogReminder
    this[AppPreferences.NOTIFICATION_PARTNER_TIP] = value.partnerTip
    this[AppPreferences.NOTIFICATION_HOUR] = value.reminderHour
    this[AppPreferences.NOTIFICATION_MINUTE] = value.reminderMinute
    this[AppPreferences.NOTIFICATION_QUIET_START_HOUR] = value.quietStartHour
    this[AppPreferences.NOTIFICATION_QUIET_START_MINUTE] = value.quietStartMinute
    this[AppPreferences.NOTIFICATION_QUIET_END_HOUR] = value.quietEndHour
    this[AppPreferences.NOTIFICATION_QUIET_END_MINUTE] = value.quietEndMinute
    this[AppPreferences.NOTIFICATION_HIDE_SENSITIVE] = value.hideSensitiveContent
    this[AppPreferences.NOTIFICATION_MAX_PER_DAY] = value.maxPerDay
    return true
}
