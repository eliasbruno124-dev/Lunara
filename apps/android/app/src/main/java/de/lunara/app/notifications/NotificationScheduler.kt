package de.lunara.app.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.guava.await

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    /** Keeps an already delayed one-time reminder intact on Activity creation/recreation. */
    fun ensureScheduled(hasSession: Boolean, preferences: NotificationPreferences) {
        configure(hasSession, preferences, ExistingWorkPolicy.KEEP)
    }

    /** Replaces the schedule only after an actual session or notification preference change. */
    fun apply(hasSession: Boolean, preferences: NotificationPreferences) {
        configure(hasSession, preferences, ExistingWorkPolicy.REPLACE)
    }

    private fun configure(
        hasSession: Boolean,
        preferences: NotificationPreferences,
        policy: ExistingWorkPolicy,
    ) {
        if (!hasSession || !preferences.master) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        if (!enqueueNext(preferences, policy)) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    /** Appends one next local occurrence after the currently running one-time worker. */
    fun scheduleNext(preferences: NotificationPreferences) {
        if (!preferences.master) return
        enqueueNext(preferences, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    /** Preserves a delayed daily check by moving it to the configured end of quiet time. */
    fun scheduleAfterQuietTime(preferences: NotificationPreferences) {
        if (!preferences.master) return
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val target = NotificationSchedulePolicy.afterQuietTime(now, preferences)
        if (target == null) {
            enqueueNext(preferences, ExistingWorkPolicy.APPEND_OR_REPLACE)
            return
        }
        enqueue(target, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueueNext(preferences: NotificationPreferences, policy: ExistingWorkPolicy): Boolean {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val target = NotificationSchedulePolicy.nextRun(now, preferences) ?: return false
        enqueue(target, policy, now)
        return true
    }

    private fun enqueue(
        target: ZonedDateTime,
        policy: ExistingWorkPolicy,
        now: ZonedDateTime = ZonedDateTime.now(target.zone),
    ) {
        val initialDelay = Duration.between(now.toInstant(), target.toInstant()).coerceAtLeast(Duration.ZERO)
        val work = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(initialDelay)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            WORK_NAME,
            policy,
            work,
        )
    }

    suspend fun cancelAllAndWait() {
        workManager.cancelUniqueWork(WORK_NAME).result.await()
        workManager.cancelAllWorkByTag(WORK_TAG).result.await()
    }

    private companion object {
        const val WORK_NAME = "lunara-local-notifications-v1"
        const val WORK_TAG = "lunara-local-notifications"
    }
}

internal enum class NotificationScheduleUpdate {
    ENSURE,
    REPLACE,
}

/** State is scoped to one Activity collector; recreating it must ensure rather than replace. */
internal class NotificationScheduleEmissionPolicy {
    private var firstEmission = true

    fun next(): NotificationScheduleUpdate = if (firstEmission) {
        firstEmission = false
        NotificationScheduleUpdate.ENSURE
    } else {
        NotificationScheduleUpdate.REPLACE
    }
}

/** Pure wall-clock calculation kept separate from WorkManager for DST and timezone tests. */
object NotificationSchedulePolicy {
    fun nextRun(now: ZonedDateTime, preferences: NotificationPreferences): ZonedDateTime? {
        var cursor = now.toLocalDateTime()
        repeat(4) {
            val localTarget = NotificationPolicy.nextExecutionAt(cursor, preferences) ?: return null
            val future = resolve(localTarget, now.zone)
                .firstOrNull { candidate -> candidate.toInstant().isAfter(now.toInstant()) }
            if (future != null) return future
            cursor = localTarget.plusNanos(1)
        }
        return null
    }

    fun afterQuietTime(
        now: ZonedDateTime,
        preferences: NotificationPreferences,
    ): ZonedDateTime? {
        if (!preferences.master || !NotificationPolicy.isQuietTime(now.toLocalTime(), preferences)) return null
        val start = LocalTime.of(preferences.quietStartHour, preferences.quietStartMinute)
        val end = LocalTime.of(preferences.quietEndHour, preferences.quietEndMinute)
        if (start == end) return null
        val endDate = when {
            start > end && now.toLocalTime() >= start -> now.toLocalDate().plusDays(1)
            else -> now.toLocalDate()
        }
        val localTarget = LocalDateTime.of(endDate, end)
        return resolve(localTarget, now.zone)
            .firstOrNull { candidate -> candidate.toInstant().isAfter(now.toInstant()) }
    }

    private fun resolve(localTarget: java.time.LocalDateTime, zone: ZoneId): List<ZonedDateTime> {
        val offsets = zone.rules.getValidOffsets(localTarget)
        if (offsets.isEmpty()) return listOf(localTarget.atZone(zone))
        return offsets
            .map { offset -> ZonedDateTime.ofLocal(localTarget, zone, offset) }
            .sortedBy(ZonedDateTime::toInstant)
    }
}
