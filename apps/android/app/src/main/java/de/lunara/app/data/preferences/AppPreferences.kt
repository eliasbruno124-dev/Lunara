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
import de.lunara.app.data.repository.SessionRecoveryPolicy
import de.lunara.app.notifications.NotificationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

private val Context.lunaraDataStore: DataStore<Preferences> by preferencesDataStore(name = "lunara_preferences")

enum class AppMode { SERVER, LOCAL }

internal object LocalIdentityPolicy {
    fun resolve(existing: String?, create: () -> UUID = UUID::randomUUID): String = existing
        ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
        ?: create().toString()
}

data class LocalSettingsSnapshot(
    val displayName: String = "Lunara",
    val privacyMode: Boolean = true,
    val useSystemTheme: Boolean = true,
    val darkMode: Boolean = false,
    val notifications: NotificationPreferences = NotificationPreferences(),
    val localFertilityPredictionEnabled: Boolean = false,
    val localTypicalCycleLength: Int? = null,
    val localTypicalPeriodLength: Int? = null,
    val localLutealLength: Int? = null,
    val localPredictionPaused: Boolean = false,
    val localSpecialMode: String = "NONE",
)

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
    /** Existing installations without this key are server-backed for backwards compatibility. */
    val appMode: AppMode = AppMode.SERVER,
    val localFertilityPredictionEnabled: Boolean = false,
    val localTypicalCycleLength: Int? = null,
    val localTypicalPeriodLength: Int? = null,
    val localLutealLength: Int? = null,
    val localPredictionPaused: Boolean = false,
    val localSpecialMode: String = "NONE",
    val reauthenticationRequired: Boolean = false,
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val state: Flow<AppPreferencesState> = context.lunaraDataStore.data.map { values ->
        val role = values[ROLE]?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
        val userId = values[USER_ID]
        val appMode = values[APP_MODE]
            ?.let { runCatching { AppMode.valueOf(it) }.getOrNull() }
            ?: AppMode.SERVER
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
            appMode = appMode,
            localFertilityPredictionEnabled = values[LOCAL_FERTILITY_ENABLED] ?: false,
            localTypicalCycleLength = values[LOCAL_TYPICAL_CYCLE_LENGTH]?.takeIf { it in 15..90 },
            localTypicalPeriodLength = values[LOCAL_TYPICAL_PERIOD_LENGTH]?.takeIf { it in 1..14 },
            localLutealLength = values[LOCAL_LUTEAL_LENGTH]?.takeIf { it in 8..20 },
            localPredictionPaused = values[LOCAL_PREDICTION_PAUSED] ?: false,
            localSpecialMode = values[LOCAL_SPECIAL_MODE].orEmpty().ifBlank { "NONE" },
            reauthenticationRequired = SessionRecoveryPolicy.markerMatches(
                appMode, values[BASE_URL], userId, role,
                values[REAUTHENTICATION_BASE_URL], values[REAUTHENTICATION_USER_ID], values[REAUTHENTICATION_ROLE],
            ),
        )
    }

    suspend fun saveConnection(baseUrl: String, serverName: String, serverVersion: String) {
        context.lunaraDataStore.edit {
            it[APP_MODE] = AppMode.SERVER.name
            it[BASE_URL] = baseUrl
            it[SERVER_NAME] = serverName
            it[SERVER_VERSION] = serverVersion
        }
    }

    suspend fun saveSession(userId: String, username: String, displayName: String, role: UserRole) {
        context.lunaraDataStore.edit {
            it[APP_MODE] = AppMode.SERVER.name
            it[USER_ID] = userId
            it[USERNAME] = username
            it[DISPLAY_NAME] = displayName
            it[ROLE] = role.name
        }
    }

    /** The marker cannot escape its exact server/account/role, including across process death. */
    suspend fun setReauthenticationRequired(
        expectedBaseUrl: String,
        expectedUserId: String,
        expectedRole: UserRole,
        required: Boolean,
    ): Boolean {
        var updated = false
        context.lunaraDataStore.edit { values ->
            val base = SessionRecoveryPolicy.normalizedBaseUrl(expectedBaseUrl)
            if (base != null && values[APP_MODE] != AppMode.LOCAL.name &&
                base == SessionRecoveryPolicy.normalizedBaseUrl(values[BASE_URL]) &&
                values[USER_ID] == expectedUserId && values[ROLE] == expectedRole.name
            ) {
                if (required) {
                    values[REAUTHENTICATION_BASE_URL] = base
                    values[REAUTHENTICATION_USER_ID] = expectedUserId
                    values[REAUTHENTICATION_ROLE] = expectedRole.name
                } else {
                    values.remove(REAUTHENTICATION_BASE_URL)
                    values.remove(REAUTHENTICATION_USER_ID)
                    values.remove(REAUTHENTICATION_ROLE)
                }
                updated = true
            }
        }
        return updated
    }

    /**
     * Creates the single-device tracker identity atomically. Its UUID remains stable across app
     * restarts and is never derived from a device identifier or account service.
     */
    suspend fun activateLocalMode(displayName: String): AppSession {
        var session: AppSession? = null
        context.lunaraDataStore.edit { values ->
            val localUserId = LocalIdentityPolicy.resolve(values[LOCAL_USER_ID])
            val safeDisplayName = displayName.trim().take(120).ifBlank { "Lunara" }
            values[LOCAL_USER_ID] = localUserId
            values[APP_MODE] = AppMode.LOCAL.name
            values[USER_ID] = localUserId
            values[USERNAME] = "local"
            values[DISPLAY_NAME] = safeDisplayName
            values[ROLE] = UserRole.TRACKER.name
            values[SERVER_NAME] = "Lunara"
            values[SERVER_VERSION] = "local"
            values.remove(BASE_URL)
            values.remove(LAST_SYNC)
            session = AppSession(
                userId = localUserId,
                displayName = safeDisplayName,
                role = UserRole.TRACKER,
                serverName = "Lunara",
                serverVersion = "local",
            )
        }
        return requireNotNull(session)
    }

    suspend fun setLocalFertilityPredictionEnabled(expectedUserId: String, enabled: Boolean) {
        context.lunaraDataStore.edit { values ->
            if (
                values[APP_MODE] == AppMode.LOCAL.name &&
                values[USER_ID] == expectedUserId &&
                values[ROLE] == UserRole.TRACKER.name
            ) {
                values[LOCAL_FERTILITY_ENABLED] = enabled
            }
        }
    }

    /** Solo mode: life phase, known lengths and prediction pause of the local tracker. */
    suspend fun setLocalCycleSettings(
        expectedUserId: String,
        specialMode: String,
        typicalCycleLength: Int?,
        typicalPeriodLength: Int?,
        predictionPaused: Boolean,
    ) {
        context.lunaraDataStore.edit { values ->
            if (
                values[APP_MODE] == AppMode.LOCAL.name &&
                values[USER_ID] == expectedUserId &&
                values[ROLE] == UserRole.TRACKER.name
            ) {
                values[LOCAL_SPECIAL_MODE] = specialMode
                values.putOrRemove(LOCAL_TYPICAL_CYCLE_LENGTH, typicalCycleLength?.takeIf { it in 15..90 })
                values.putOrRemove(LOCAL_TYPICAL_PERIOD_LENGTH, typicalPeriodLength?.takeIf { it in 1..14 })
                values[LOCAL_PREDICTION_PAUSED] = predictionPaused
            }
        }
    }

    suspend fun restoreLocalSettings(expectedUserId: String, snapshot: LocalSettingsSnapshot) {
        context.lunaraDataStore.edit { values ->
            check(
                values[APP_MODE] == AppMode.LOCAL.name &&
                    values[USER_ID] == expectedUserId &&
                    values[ROLE] == UserRole.TRACKER.name,
            ) { "Local session changed while restoring a backup" }
            values[DISPLAY_NAME] = snapshot.displayName.trim().take(120)
            values[PRIVACY_MODE] = snapshot.privacyMode
            values[USE_SYSTEM_THEME] = snapshot.useSystemTheme
            values[DARK_MODE] = snapshot.darkMode
            values[LOCAL_FERTILITY_ENABLED] = snapshot.localFertilityPredictionEnabled
            values.putOrRemove(LOCAL_TYPICAL_CYCLE_LENGTH, snapshot.localTypicalCycleLength)
            values.putOrRemove(LOCAL_TYPICAL_PERIOD_LENGTH, snapshot.localTypicalPeriodLength)
            values.putOrRemove(LOCAL_LUTEAL_LENGTH, snapshot.localLutealLength)
            values[LOCAL_PREDICTION_PAUSED] = snapshot.localPredictionPaused
            values[LOCAL_SPECIAL_MODE] = snapshot.localSpecialMode
            values.writeNotificationPreferencesForSession(expectedUserId, snapshot.notifications)
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

    /** A membership move changes the complete server data scope. */
    suspend fun clearLastSync() {
        context.lunaraDataStore.edit { it.remove(LAST_SYNC) }
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
        val REAUTHENTICATION_BASE_URL = stringPreferencesKey("session.reauthentication.base_url")
        val REAUTHENTICATION_USER_ID = stringPreferencesKey("session.reauthentication.user_id")
        val REAUTHENTICATION_ROLE = stringPreferencesKey("session.reauthentication.role")
        val PRIVACY_MODE = booleanPreferencesKey("privacy.flag_secure")
        val USE_SYSTEM_THEME = booleanPreferencesKey("appearance.system")
        val DARK_MODE = booleanPreferencesKey("appearance.dark")
        val LAST_SYNC = stringPreferencesKey("sync.last_success")
        val APP_MODE = stringPreferencesKey("app.mode")
        val LOCAL_USER_ID = stringPreferencesKey("local.user_id")
        val LOCAL_FERTILITY_ENABLED = booleanPreferencesKey("local.profile.fertility_enabled")
        val LOCAL_TYPICAL_CYCLE_LENGTH = intPreferencesKey("local.profile.typical_cycle_length")
        val LOCAL_TYPICAL_PERIOD_LENGTH = intPreferencesKey("local.profile.typical_period_length")
        val LOCAL_LUTEAL_LENGTH = intPreferencesKey("local.profile.luteal_length")
        val LOCAL_PREDICTION_PAUSED = booleanPreferencesKey("local.profile.prediction_paused")
        val LOCAL_SPECIAL_MODE = stringPreferencesKey("local.profile.special_mode")
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

private fun MutablePreferences.putOrRemove(key: Preferences.Key<Int>, value: Int?) {
    if (value == null) remove(key) else this[key] = value
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
