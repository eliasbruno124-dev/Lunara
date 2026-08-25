package de.lunara.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.lunara.app.notifications.LunaraNotificationManager
import de.lunara.app.data.backup.PortableLocalBackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LunaraApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationManager: LunaraNotificationManager
    @Inject lateinit var portableLocalBackupRepository: PortableLocalBackupRepository
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationManager.createChannels()
        applicationScope.launch {
            runCatching { portableLocalBackupRepository.resumePendingImport() }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()
}
