package de.lunara.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.sync.SyncScheduler
import de.lunara.app.notifications.NotificationScheduleEmissionPolicy
import de.lunara.app.notifications.NotificationScheduler
import de.lunara.app.notifications.NotificationScheduleUpdate
import de.lunara.app.ui.LunaraApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var preferences: AppPreferences
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var notificationScheduler: NotificationScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        observePrivacyMode()
        observeForegroundSync()
        observeNotificationScheduling()
        setContent { LunaraApp() }
    }

    private fun observeForegroundSync() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val state = preferences.state.first()
                    if (state.session != null && state.appMode == AppMode.SERVER) {
                        syncScheduler.schedulePeriodic()
                        syncScheduler.requestImmediate()
                    }
                    // WorkManager remains responsible in the background. While the app is open,
                    // this bounded pulse makes changes from the other device visible promptly.
                    delay(60_000)
                }
            }
        }
    }

    private fun observePrivacyMode() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.state.map { it.privacyMode }.distinctUntilChanged().collect { enabled ->
                    if (enabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    private fun observeNotificationScheduling() {
        lifecycleScope.launch {
            val emissionPolicy = NotificationScheduleEmissionPolicy()
            preferences.state
                .map { state -> state.session?.userId to state.notifications }
                .distinctUntilChanged()
                .collect { (userId, notificationPreferences) ->
                    when (emissionPolicy.next()) {
                        NotificationScheduleUpdate.ENSURE ->
                            notificationScheduler.ensureScheduled(userId != null, notificationPreferences)
                        NotificationScheduleUpdate.REPLACE ->
                            notificationScheduler.apply(userId != null, notificationPreferences)
                    }
                }
        }
    }
}
