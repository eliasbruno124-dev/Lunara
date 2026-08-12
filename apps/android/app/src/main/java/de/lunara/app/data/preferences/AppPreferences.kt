package de.lunara.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lunara.app.core.model.AppSession
import de.lunara.app.core.model.UserRole
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

    suspend fun clearAccount() {
        context.lunaraDataStore.edit { values ->
            listOf(USER_ID, USERNAME, DISPLAY_NAME, ROLE, BASE_URL, SERVER_NAME, SERVER_VERSION, LAST_SYNC)
                .forEach(values::remove)
        }
    }

    suspend fun clearAll() {
        context.lunaraDataStore.edit { it.clear() }
    }

    private companion object {
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
    }
}
