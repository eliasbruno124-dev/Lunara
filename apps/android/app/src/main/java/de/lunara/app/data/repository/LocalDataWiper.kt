package de.lunara.app.data.repository

import de.lunara.app.core.security.DatabaseSecrets
import de.lunara.app.core.security.SecretStore
import de.lunara.app.core.security.SecureCookieJar
import de.lunara.app.core.security.SecureWipeCoordinator
import de.lunara.app.data.local.LunaraDatabase
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.ApiClientFactory
import de.lunara.app.data.sync.SyncScheduler
import de.lunara.app.data.sync.ProcessSyncGate
import de.lunara.app.notifications.LunaraNotificationManager
import de.lunara.app.notifications.NotificationDataCache
import de.lunara.app.notifications.NotificationDeliveryStore
import de.lunara.app.notifications.NotificationScheduler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LocalDataWiper @Inject constructor(
    private val database: LunaraDatabase,
    private val preferences: AppPreferences,
    private val cookieJar: SecureCookieJar,
    private val secretStore: SecretStore,
    private val syncScheduler: SyncScheduler,
    private val apiFactory: ApiClientFactory,
    private val credentialGate: SessionCredentialGate,
    private val sessionSecrets: SessionSecrets,
    private val notificationScheduler: NotificationScheduler,
    private val notificationManager: LunaraNotificationManager,
    private val notificationDataCache: NotificationDataCache,
    private val notificationDeliveryStore: NotificationDeliveryStore,
) {
    suspend fun wipe() = withContext(Dispatchers.IO) {
        credentialGate.invalidate()
        apiFactory.cancelAll()
        // Request cancellation before waiting for an active worker to leave the process-wide
        // gate. The second cancellation is tracked as a secure-wipe step if the first failed.
        runCatching { syncScheduler.cancelAllAndWait() }
        runCatching { notificationScheduler.cancelAllAndWait() }
        ProcessSyncGate.serialized {
            credentialGate.exclusive {
                SecureWipeCoordinator.execute(
                    localSteps = listOf(
                        { syncScheduler.cancelAllAndWait() },
                        { notificationScheduler.cancelAllAndWait() },
                        { notificationManager.cancelAll() },
                        { notificationDataCache.clear() },
                        { notificationDeliveryStore.clear() },
                        { clearEncryptedDatabase() },
                        { cookieJar.clear() },
                        // Tokens and gateway credentials must be removed even if SQLCipher
                        // cannot rekey the already-cleared database afterwards.
                        { sessionSecrets.clearAccountSecrets() },
                        { rotateDatabaseKeyAndDestroyAllOldSecrets() },
                        // Clear DataStore last so the UI cannot expose onboarding before the wipe finished.
                        { preferences.clearAll() },
                        // Remove a trigger that may have been enqueued while the local wipe ran.
                        { syncScheduler.cancelAllAndWait() },
                        { notificationScheduler.cancelAllAndWait() },
                    ),
                )
            }
        }
    }

    private fun clearEncryptedDatabase() {
        // Room 2.8 clears every declared entity table, checkpoints the WAL and vacuums the file.
        database.clearAllTables()
    }

    private fun rotateDatabaseKeyAndDestroyAllOldSecrets() {
        val replacement = ByteArray(32).also(SecureRandom()::nextBytes)
        try {
            val sqlCipher = database.openHelper.writableDatabase as? SQLiteDatabase
                ?: error("Encrypted database implementation is unavailable")
            val rekeyInput = replacement.copyOf()
            try {
                sqlCipher.changePassword(rekeyInput)
            } finally {
                rekeyInput.fill(0)
            }

            // This removes tokens, gateway credentials, encrypted cookie storage, the previous
            // database passphrase and the old Android Keystore alias. A fresh alias then wraps
            // only the replacement key for the already-empty database.
            secretStore.clear()
            secretStore.putBytes(DatabaseSecrets.PASSPHRASE_KEY, replacement.copyOf())
        } finally {
            replacement.fill(0)
        }
    }
}
