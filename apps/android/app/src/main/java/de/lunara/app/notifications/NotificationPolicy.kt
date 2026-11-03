package de.lunara.app.notifications

import de.lunara.app.core.model.UserRole
import de.lunara.app.data.prediction.FertilityWindowPolicy
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PredictionDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class NotificationPreferences(
    val master: Boolean = false,
    val periodTomorrow: Boolean = true,
    val fertileTomorrow: Boolean = false,
    val dailyLogReminder: Boolean = false,
    val partnerTip: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val quietStartHour: Int = 22,
    val quietStartMinute: Int = 0,
    val quietEndHour: Int = 8,
    val quietEndMinute: Int = 0,
    val hideSensitiveContent: Boolean = true,
    val maxPerDay: Int = 2,
) {
    init {
        require(reminderHour in 0..23) { "reminderHour must be between 0 and 23" }
        require(reminderMinute in 0..59) { "reminderMinute must be between 0 and 59" }
        require(quietStartHour in 0..23) { "quietStartHour must be between 0 and 23" }
        require(quietStartMinute in 0..59) { "quietStartMinute must be between 0 and 59" }
        require(quietEndHour in 0..23) { "quietEndHour must be between 0 and 23" }
        require(quietEndMinute in 0..59) { "quietEndMinute must be between 0 and 59" }
        require(maxPerDay in 1..3) { "maxPerDay must be between 1 and 3" }
    }
}

enum class NotificationType {
    PERIOD_WINDOW_STARTS_TOMORROW,
    FERTILE_WINDOW_STARTS_TOMORROW,
    DAILY_LOG_REMINDER,
    PARTNER_TIP,
}

/**
 * Stable localization keys for a later Android renderer. Estimate-related keys deliberately avoid
 * claiming that a period or fertile day is certain.
 */
enum class NotificationContent {
    PRIVATE_REMINDER,
    ESTIMATED_PERIOD_WINDOW_STARTS_TOMORROW,
    ROUGH_ESTIMATED_FERTILE_WINDOW_STARTS_TOMORROW,
    DAILY_LOG_REMINDER,
    PARTNER_TIP_AVAILABLE,
}

data class NotificationCandidate(
    val type: NotificationType,
    val content: NotificationContent,
    val eventDate: LocalDate,
    val sensitive: Boolean = true,
)

object NotificationPolicy {
    /**
     * Returns today's candidates in deterministic privacy-preserving priority order. A partner can
     * only use the server-filtered partner dashboard; passing a tracker's prediction cannot grant
     * the partner any additional information.
     */
    fun candidates(
        role: UserRole,
        today: LocalDate,
        preferences: NotificationPreferences,
        prediction: PredictionDto? = null,
        partnerDashboard: PartnerDashboardDto? = null,
        hasDailyLogToday: Boolean = false,
        pendingTrackerChanges: Boolean = false,
    ): List<NotificationCandidate> {
        if (!preferences.master) return emptyList()

        val tomorrow = today.plusDays(1)
        val dashboard = partnerDashboard?.takeUnless { it.sharingPaused }
        val visiblePrediction = when (role) {
            UserRole.TRACKER -> prediction
            UserRole.PARTNER -> dashboard?.prediction
        }
        val result = mutableListOf<NotificationCandidate>()

        if (
            preferences.periodTomorrow &&
            validRangeStartsOn(
                visiblePrediction,
                visiblePrediction?.predictedStartFrom,
                visiblePrediction?.predictedStartTo,
                tomorrow,
            )
        ) {
            result += candidate(
                NotificationType.PERIOD_WINDOW_STARTS_TOMORROW,
                NotificationContent.ESTIMATED_PERIOD_WINDOW_STARTS_TOMORROW,
                tomorrow,
            )
        }

        if (
            preferences.fertileTomorrow &&
            (role != UserRole.PARTNER || dashboard?.calendarAccess?.fertileWindow != false) &&
            FertilityWindowPolicy.current(visiblePrediction, today, role == UserRole.TRACKER && pendingTrackerChanges)?.from == tomorrow
        ) {
            result += candidate(
                NotificationType.FERTILE_WINDOW_STARTS_TOMORROW,
                NotificationContent.ROUGH_ESTIMATED_FERTILE_WINDOW_STARTS_TOMORROW,
                tomorrow,
            )
        }

        if (preferences.dailyLogReminder && role == UserRole.TRACKER && !hasDailyLogToday) {
            result += candidate(
                NotificationType.DAILY_LOG_REMINDER,
                NotificationContent.DAILY_LOG_REMINDER,
                today,
            )
        }

        if (preferences.partnerTip && role == UserRole.PARTNER && dashboard?.tip != null) {
            result += candidate(
                NotificationType.PARTNER_TIP,
                NotificationContent.PARTNER_TIP_AVAILABLE,
                today,
            )
        }

        return result.take(preferences.maxPerDay)
    }

    /** Quiet start is inclusive and quiet end is exclusive. Equal bounds disable quiet time. */
    fun isQuietTime(time: LocalTime, preferences: NotificationPreferences): Boolean {
        val start = preferences.quietStart()
        val end = preferences.quietEnd()
        return when {
            start == end -> false
            start < end -> time >= start && time < end
            else -> time >= start || time < end
        }
    }

    /**
     * Finds the next configured daily execution instant and moves occurrences out of quiet time.
     * The previous calendar day's occurrence is considered because an overnight quiet interval can
     * defer it into the current morning.
     */
    fun nextExecutionAt(
        now: LocalDateTime,
        preferences: NotificationPreferences,
    ): LocalDateTime? {
        if (!preferences.master || !preferences.hasEnabledCategory()) return null

        val reminderTime = LocalTime.of(preferences.reminderHour, preferences.reminderMinute)
        return (-1L..2L)
            .asSequence()
            .map { dayOffset -> LocalDateTime.of(now.toLocalDate().plusDays(dayOffset), reminderTime) }
            .map { scheduled -> moveOutOfQuietTime(scheduled, preferences) }
            .filter { scheduled -> !scheduled.isBefore(now) }
            .minOrNull()
    }

    private fun candidate(
        type: NotificationType,
        visibleContent: NotificationContent,
        eventDate: LocalDate,
    ) = NotificationCandidate(
        type = type,
        content = visibleContent,
        eventDate = eventDate,
    )

    private fun validRangeStartsOn(
        prediction: PredictionDto?,
        rawFrom: String?,
        rawTo: String?,
        expectedStart: LocalDate,
    ): Boolean {
        if (prediction?.available != true) return false
        val from = rawFrom?.let(::parseDate) ?: return false
        val to = rawTo?.let(::parseDate) ?: return false
        return from == expectedStart && !to.isBefore(from)
    }

    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()

    private fun moveOutOfQuietTime(
        scheduled: LocalDateTime,
        preferences: NotificationPreferences,
    ): LocalDateTime {
        if (!isQuietTime(scheduled.toLocalTime(), preferences)) return scheduled
        val start = preferences.quietStart()
        val end = preferences.quietEnd()
        val endDate = if (start > end && scheduled.toLocalTime() >= start) {
            scheduled.toLocalDate().plusDays(1)
        } else {
            scheduled.toLocalDate()
        }
        return LocalDateTime.of(endDate, end)
    }

    private fun NotificationPreferences.quietStart() = LocalTime.of(quietStartHour, quietStartMinute)

    private fun NotificationPreferences.quietEnd() = LocalTime.of(quietEndHour, quietEndMinute)

    private fun NotificationPreferences.hasEnabledCategory() =
        periodTomorrow || fertileTomorrow || dailyLogReminder || partnerTip
}
