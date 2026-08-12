package de.lunara.app.data.repository

import de.lunara.app.core.security.DatabaseSecrets
import de.lunara.app.core.security.SecretStore
import de.lunara.app.core.security.SecureCookieJar
import de.lunara.app.core.security.SecureWipeCoordinator
import de.lunara.app.data.local.LunaraDatabase
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.sync.SyncScheduler
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
) {
    suspend fun wipe() = withContext(Dispatchers.IO) {
        SecureWipeCoordinator.execute(
            localSteps = listOf(
                { syncScheduler.cancelImmediateAndWait() },
                { clearEncryptedDatabase() },
                { cookieJar.clear() },
                { rotateDatabaseKeyAndDestroyAllOldSecrets() },
                // Clear DataStore last so the UI cannot expose onboarding before the wipe finished.
                { preferences.clearAll() },
            ),
        )
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
