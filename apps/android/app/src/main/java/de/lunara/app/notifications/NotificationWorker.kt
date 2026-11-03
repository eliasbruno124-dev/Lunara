package de.lunara.app.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.local.LunaraDatabase
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.repository.DashboardRepository
import de.lunara.app.data.sync.ProcessSyncGate
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val preferences: AppPreferences,
    private val dashboardRepository: DashboardRepository,
    private val database: LunaraDatabase,
    private val notificationManager: LunaraNotificationManager,
    private val deliveryStore: NotificationDeliveryStore,
    private val dataCache: NotificationDataCache,
    private val notificationScheduler: NotificationScheduler,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val state = preferences.state.first()
        val session = state.session ?: return Result.success()
        val settings = state.notifications
        if (!settings.master) return Result.success()
        if (!notificationManager.notificationsAllowed()) {
            return scheduleIfCurrent(session.userId, deferUntilQuietEnd = false)
        }
        if (NotificationPolicy.isQuietTime(LocalTime.now(), settings)) {
            return scheduleIfCurrent(session.userId, deferUntilQuietEnd = true)
        }

        return try {
            performNotificationCheck(session.userId, session.role, settings)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                scheduleIfCurrent(session.userId, deferUntilQuietEnd = false)
            }
        }
    }

    private suspend fun performNotificationCheck(
        userId: String,
        role: UserRole,
        settings: NotificationPreferences,
    ): Result {
        var prediction: PredictionDto? = null
        var partnerDashboard: PartnerDashboardDto? = null
        var remoteUnavailable = false
        when (role) {
            UserRole.TRACKER -> {
                if (settings.periodTomorrow || settings.fertileTomorrow) {
                    try {
                        prediction = dashboardRepository.trackerPrediction(userId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        remoteUnavailable = true
                    }
                }
            }
            UserRole.PARTNER -> {
                if (settings.periodTomorrow || settings.fertileTomorrow || settings.partnerTip) {
                    try {
                        partnerDashboard = dashboardRepository.partnerDashboard(userId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        remoteUnavailable = true
                        // A cached projection may predate a sharing revocation. Notifications are
                        // therefore strictly fail-closed whenever the partner endpoint is unavailable.
                        partnerDashboard = null
                    }
                }
            }
        }

        // Repository network work stays outside the process gate. Everything that reads or writes
        // local health state, publishes, commits delivery, or appends work is serialized with wipe.
        return ProcessSyncGate.serialized {
            val currentState = preferences.state.first()
            val currentSession = currentState.session
            if (currentSession?.userId != userId || currentSession.role != role) {
                return@serialized Result.success()
            }
            val currentSettings = currentState.notifications
            if (!currentSettings.master) return@serialized Result.success()
            if (!notificationManager.notificationsAllowed()) {
                return@serialized finishAndScheduleNext(currentSettings)
            }
            if (NotificationPolicy.isQuietTime(LocalTime.now(), currentSettings)) {
                return@serialized finishAfterQuietTime(currentSettings)
            }

            val today = LocalDate.now()
            val effectivePrediction = if (
                role == UserRole.TRACKER && prediction == null && remoteUnavailable
            ) {
                dataCache.trackerPrediction(userId)
            } else {
                prediction
            }
            val hasDailyLog = if (role == UserRole.TRACKER && currentSettings.dailyLogReminder) {
                database.dailyLogDao().findByDate(userId, today.toString()) != null
            } else {
                false
            }
            val pendingTrackerChanges = role == UserRole.TRACKER && currentState.appMode == AppMode.SERVER && (
                database.mutationDao().observeCount().first() > 0 ||
                    database.periodDao().observeResolvingCount().first() > 0 ||
                    database.dailyLogDao().observeResolvingCount().first() > 0
                )
            val candidates = NotificationPolicy.candidates(
                role = role,
                today = today,
                preferences = currentSettings,
                prediction = effectivePrediction,
                partnerDashboard = partnerDashboard,
                hasDailyLogToday = hasDailyLog,
                pendingTrackerChanges = pendingTrackerChanges,
            )
            var publicationFailed = false
            candidates.forEach { candidate ->
                val occurrenceKey = "${candidate.type.name}:${candidate.eventDate}"
                val reservation = deliveryStore.reserve(
                    type = candidate.type,
                    occurrenceKey = occurrenceKey,
                    localDate = today.toString(),
                    maxPerDay = currentSettings.maxPerDay,
                ) ?: return@forEach
                var published = false
                try {
                    published = notificationManager.publish(candidate, currentSettings.hideSensitiveContent)
                    if (!published) publicationFailed = true
                } finally {
                    deliveryStore.complete(reservation, published)
                }
            }
            if ((remoteUnavailable || publicationFailed) && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                finishAndScheduleNext(currentSettings)
            }
        }
    }

    private suspend fun scheduleIfCurrent(
        expectedUserId: String,
        deferUntilQuietEnd: Boolean,
    ): Result = ProcessSyncGate.serialized {
        val currentState = preferences.state.first()
        if (currentState.session?.userId != expectedUserId || !currentState.notifications.master) {
            return@serialized Result.success()
        }
        if (
            deferUntilQuietEnd &&
            NotificationPolicy.isQuietTime(LocalTime.now(), currentState.notifications)
        ) {
            finishAfterQuietTime(currentState.notifications)
        } else {
            finishAndScheduleNext(currentState.notifications)
        }
    }

    private fun finishAndScheduleNext(settings: NotificationPreferences): Result {
        notificationScheduler.scheduleNext(settings)
        return Result.success()
    }

    private fun finishAfterQuietTime(settings: NotificationPreferences): Result {
        notificationScheduler.scheduleAfterQuietTime(settings)
        return Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
