package de.lunara.app.notifications

import de.lunara.app.core.model.UserRole
import de.lunara.app.data.remote.dto.FertilityEstimateDto
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PartnerCalendarAccessDto
import de.lunara.app.data.remote.dto.PartnerTipDto
import de.lunara.app.data.remote.dto.PredictionDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {
    private val today = LocalDate.of(2026, 8, 14)

    @Test fun `fertility notifications reject legacy widths and preserve independently shared safe window`() {
        val start = today.plusDays(1)
        val settings = NotificationPreferences(master = true, periodTomorrow = false, fertileTomorrow = true)
        val bounded = completePrediction(start).copy(fertileWindowTo = start.plusDays(18).toString())
        assertEquals(1, NotificationPolicy.candidates(UserRole.TRACKER, today, settings, prediction = bounded).size)
        for (width in listOf(20L, 33L)) {
            val legacy = bounded.copy(fertileWindowTo = start.plusDays(width - 1).toString())
            assertTrue(NotificationPolicy.candidates(UserRole.TRACKER, today, settings, prediction = legacy).isEmpty())
            assertTrue(NotificationPolicy.candidates(UserRole.PARTNER, today, settings, partnerDashboard = PartnerDashboardDto(prediction = legacy)).isEmpty())
        }
        assertTrue(NotificationPolicy.candidates(UserRole.TRACKER, today, settings, prediction = bounded, pendingTrackerChanges = true).isEmpty())
        val independent = PartnerDashboardDto(prediction = bounded.copy(available = false, reason = "NOT_SHARED"), calendarAccess = PartnerCalendarAccessDto(false, true))
        assertEquals(1, NotificationPolicy.candidates(UserRole.PARTNER, today, settings, partnerDashboard = independent, pendingTrackerChanges = true).size)
        assertTrue(NotificationPolicy.candidates(UserRole.PARTNER, today, settings, partnerDashboard = independent.copy(sharingPaused = true)).isEmpty())
        assertTrue(NotificationPolicy.candidates(UserRole.PARTNER, today, settings, partnerDashboard = independent.copy(calendarAccess = PartnerCalendarAccessDto(false, false))).isEmpty())
        assertTrue(NotificationPolicy.candidates(UserRole.TRACKER, today, settings, prediction = bounded.copy(fertilityEstimate = bounded.fertilityEstimate?.copy(reason = "UNRELIABLE_DATA"))).isEmpty())
    }

    @Test
    fun `defaults require explicit opt in and avoid intrusive categories`() {
        val preferences = NotificationPreferences()

        assertFalse(preferences.master)
        assertTrue(preferences.periodTomorrow)
        assertFalse(preferences.fertileTomorrow)
        assertFalse(preferences.dailyLogReminder)
        assertFalse(preferences.partnerTip)
        assertEquals(9, preferences.reminderHour)
        assertEquals(0, preferences.reminderMinute)
        assertEquals(22, preferences.quietStartHour)
        assertEquals(8, preferences.quietEndHour)
        assertTrue(preferences.hideSensitiveContent)
        assertEquals(2, preferences.maxPerDay)
    }

    @Test
    fun `tracker candidates are deterministically prioritized and capped`() {
        val candidates = NotificationPolicy.candidates(
            role = UserRole.TRACKER,
            today = today,
            preferences = allCategories(maxPerDay = 2),
            prediction = completePrediction(today.plusDays(1)),
            partnerDashboard = PartnerDashboardDto(tip = tip()),
            hasDailyLogToday = false,
        )

        assertEquals(
            listOf(
                NotificationType.PERIOD_WINDOW_STARTS_TOMORROW,
                NotificationType.FERTILE_WINDOW_STARTS_TOMORROW,
            ),
            candidates.map { it.type },
        )
        assertEquals(
            listOf(
                NotificationContent.ESTIMATED_PERIOD_WINDOW_STARTS_TOMORROW,
                NotificationContent.ROUGH_ESTIMATED_FERTILE_WINDOW_STARTS_TOMORROW,
            ),
            candidates.map { it.content },
        )
    }

    @Test
    fun `partner uses only projected unpaused dashboard and receives partner tip last`() {
        val rawTrackerPrediction = completePrediction(today.plusDays(1))
        val preferences = allCategories(maxPerDay = 3, hideSensitiveContent = false)

        val withoutProjection = NotificationPolicy.candidates(
            role = UserRole.PARTNER,
            today = today,
            preferences = preferences,
            prediction = rawTrackerPrediction,
            partnerDashboard = PartnerDashboardDto(tip = tip()),
        )
        assertEquals(listOf(NotificationType.PARTNER_TIP), withoutProjection.map { it.type })

        val projected = NotificationPolicy.candidates(
            role = UserRole.PARTNER,
            today = today,
            preferences = preferences,
            prediction = rawTrackerPrediction,
            partnerDashboard = PartnerDashboardDto(
                prediction = completePrediction(today.plusDays(1)),
                tip = tip(),
            ),
        )
        assertEquals(
            listOf(
                NotificationType.PERIOD_WINDOW_STARTS_TOMORROW,
                NotificationType.FERTILE_WINDOW_STARTS_TOMORROW,
                NotificationType.PARTNER_TIP,
            ),
            projected.map { it.type },
        )
        assertEquals(
            listOf(
                NotificationContent.ESTIMATED_PERIOD_WINDOW_STARTS_TOMORROW,
                NotificationContent.ROUGH_ESTIMATED_FERTILE_WINDOW_STARTS_TOMORROW,
                NotificationContent.PARTNER_TIP_AVAILABLE,
            ),
            projected.map { it.content },
        )

        val paused = NotificationPolicy.candidates(
            role = UserRole.PARTNER,
            today = today,
            preferences = preferences,
            partnerDashboard = PartnerDashboardDto(
                sharingPaused = true,
                prediction = completePrediction(today.plusDays(1)),
                tip = tip(),
            ),
        )
        assertTrue(paused.isEmpty())
    }

    @Test
    fun `partner without a fresh server projection receives no health or tip notification`() {
        assertTrue(
            NotificationPolicy.candidates(
                role = UserRole.PARTNER,
                today = today,
                preferences = allCategories(),
                prediction = completePrediction(today.plusDays(1)),
                partnerDashboard = null,
            ).isEmpty(),
        )
    }

    @Test
    fun `master and individual category switches fail closed`() {
        val prediction = completePrediction(today.plusDays(1))
        assertTrue(
            NotificationPolicy.candidates(
                UserRole.TRACKER,
                today,
                allCategories().copy(master = false),
                prediction,
            ).isEmpty(),
        )
        assertEquals(
            listOf(NotificationType.DAILY_LOG_REMINDER),
            NotificationPolicy.candidates(
                UserRole.TRACKER,
                today,
                allCategories().copy(periodTomorrow = false, fertileTomorrow = false),
                prediction,
            ).map { it.type },
        )
    }

    @Test
    fun `period estimate fires only when a complete valid range begins tomorrow`() {
        val preferences = NotificationPreferences(master = true, hideSensitiveContent = false)

        assertEquals(
            listOf(NotificationType.PERIOD_WINDOW_STARTS_TOMORROW),
            NotificationPolicy.candidates(
                UserRole.TRACKER,
                today,
                preferences,
                periodPrediction(today.plusDays(1)),
            ).map { it.type },
        )
        listOf(
            periodPrediction(today),
            periodPrediction(today.plusDays(2)),
            periodPrediction(today.plusDays(1)).copy(predictedStartTo = null),
            periodPrediction(today.plusDays(1)).copy(predictedStartTo = today.toString()),
            periodPrediction(today.plusDays(1)).copy(predictedStartFrom = "not-a-date"),
            periodPrediction(today.plusDays(1)).copy(available = false),
        ).forEach { prediction ->
            assertTrue(
                NotificationPolicy.candidates(
                    UserRole.TRACKER,
                    today,
                    preferences,
                    prediction,
                ).isEmpty(),
            )
        }
    }

    @Test
    fun `fertility candidate requires every safety flag and tomorrow boundary`() {
        val preferences = NotificationPreferences(
            master = true,
            periodTomorrow = false,
            fertileTomorrow = true,
            hideSensitiveContent = false,
        )
        val complete = completePrediction(today.plusDays(1))
        assertEquals(
            NotificationContent.ROUGH_ESTIMATED_FERTILE_WINDOW_STARTS_TOMORROW,
            NotificationPolicy.candidates(UserRole.TRACKER, today, preferences, complete).single().content,
        )

        val safety = complete.fertilityEstimate!!
        listOf(
            complete.copy(fertilityEstimate = null),
            complete.copy(fertilityEstimate = safety.copy(enabled = false)),
            complete.copy(fertilityEstimate = safety.copy(available = false)),
            complete.copy(fertilityEstimate = safety.copy(estimated = false)),
            complete.copy(fertilityEstimate = safety.copy(notContraception = false)),
            complete.copy(fertilityEstimate = safety.copy(doesNotConfirmOvulation = false)),
            complete.copy(fertileWindowFrom = today.toString()),
            complete.copy(fertileWindowFrom = today.plusDays(2).toString()),
            complete.copy(fertileWindowTo = null),
            complete.copy(fertileWindowTo = today.toString()),
        ).forEach { prediction ->
            assertTrue(
                NotificationPolicy.candidates(UserRole.TRACKER, today, preferences, prediction).isEmpty(),
            )
        }
    }

    @Test
    fun `daily logging is tracker only and stops after a log exists`() {
        val preferences = NotificationPreferences(
            master = true,
            periodTomorrow = false,
            dailyLogReminder = true,
        )

        assertEquals(
            listOf(NotificationType.DAILY_LOG_REMINDER),
            NotificationPolicy.candidates(UserRole.TRACKER, today, preferences).map { it.type },
        )
        assertTrue(
            NotificationPolicy.candidates(
                UserRole.TRACKER,
                today,
                preferences,
                hasDailyLogToday = true,
            ).isEmpty(),
        )
        assertTrue(NotificationPolicy.candidates(UserRole.PARTNER, today, preferences).isEmpty())
    }

    @Test
    fun `overnight quiet time includes start and excludes end`() {
        val preferences = NotificationPreferences(master = true)

        assertFalse(NotificationPolicy.isQuietTime(LocalTime.of(21, 59), preferences))
        assertTrue(NotificationPolicy.isQuietTime(LocalTime.of(22, 0), preferences))
        assertTrue(NotificationPolicy.isQuietTime(LocalTime.of(0, 0), preferences))
        assertTrue(NotificationPolicy.isQuietTime(LocalTime.of(7, 59), preferences))
        assertFalse(NotificationPolicy.isQuietTime(LocalTime.of(8, 0), preferences))
    }

    @Test
    fun `same-day quiet time and equal disabled bounds are deterministic`() {
        val daytime = NotificationPreferences(
            master = true,
            quietStartHour = 13,
            quietEndHour = 15,
        )
        assertFalse(NotificationPolicy.isQuietTime(LocalTime.of(12, 59), daytime))
        assertTrue(NotificationPolicy.isQuietTime(LocalTime.of(13, 0), daytime))
        assertFalse(NotificationPolicy.isQuietTime(LocalTime.of(15, 0), daytime))

        val disabled = daytime.copy(quietStartHour = 0, quietEndHour = 0)
        assertFalse(NotificationPolicy.isQuietTime(LocalTime.MIDNIGHT, disabled))
        assertFalse(NotificationPolicy.isQuietTime(LocalTime.NOON, disabled))
    }

    @Test
    fun `next execution respects overnight quiet time including deferred previous day`() {
        val lateReminder = NotificationPreferences(
            master = true,
            reminderHour = 23,
            reminderMinute = 30,
        )

        assertEquals(
            LocalDateTime.of(2026, 8, 15, 8, 0),
            NotificationPolicy.nextExecutionAt(
                LocalDateTime.of(2026, 8, 14, 21, 0),
                lateReminder,
            ),
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 15, 8, 0),
            NotificationPolicy.nextExecutionAt(
                LocalDateTime.of(2026, 8, 15, 1, 0),
                lateReminder,
            ),
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 16, 8, 0),
            NotificationPolicy.nextExecutionAt(
                LocalDateTime.of(2026, 8, 15, 8, 1),
                lateReminder,
            ),
        )
    }

    @Test
    fun `next execution returns normal reminder and none when globally disabled`() {
        val preferences = NotificationPreferences(master = true, reminderHour = 19, reminderMinute = 15)
        assertEquals(
            LocalDateTime.of(2026, 8, 14, 19, 15),
            NotificationPolicy.nextExecutionAt(LocalDateTime.of(2026, 8, 14, 10, 0), preferences),
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 15, 19, 15),
            NotificationPolicy.nextExecutionAt(LocalDateTime.of(2026, 8, 14, 19, 16), preferences),
        )
        assertEquals(null, NotificationPolicy.nextExecutionAt(LocalDateTime.now(), preferences.copy(master = false)))
        assertEquals(
            null,
            NotificationPolicy.nextExecutionAt(
                LocalDateTime.now(),
                preferences.copy(
                    periodTomorrow = false,
                    fertileTomorrow = false,
                    dailyLogReminder = false,
                    partnerTip = false,
                ),
            ),
        )
    }

    @Test
    fun `invalid times and daily maximum are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { NotificationPreferences(reminderHour = 24) }
        assertThrows(IllegalArgumentException::class.java) { NotificationPreferences(quietEndMinute = 60) }
        assertThrows(IllegalArgumentException::class.java) { NotificationPreferences(maxPerDay = 0) }
        assertThrows(IllegalArgumentException::class.java) { NotificationPreferences(maxPerDay = 4) }
    }

    private fun allCategories(
        maxPerDay: Int = 3,
        hideSensitiveContent: Boolean = true,
    ) = NotificationPreferences(
        master = true,
        periodTomorrow = true,
        fertileTomorrow = true,
        dailyLogReminder = true,
        partnerTip = true,
        hideSensitiveContent = hideSensitiveContent,
        maxPerDay = maxPerDay,
    )

    private fun periodPrediction(start: LocalDate) = PredictionDto(
        predictedStartFrom = start.toString(),
        predictedStartTo = start.plusDays(2).toString(),
        confidence = "LOW",
    )

    private fun completePrediction(start: LocalDate) = periodPrediction(start).copy(
        fertileWindowFrom = start.toString(),
        fertileWindowTo = start.plusDays(5).toString(),
        fertilityEstimate = FertilityEstimateDto(
            enabled = true,
            available = true,
            estimated = true,
            reason = "AVAILABLE",
            basedOnCycleCount = 6,
            notContraception = true,
            doesNotConfirmOvulation = true,
        ),
    )

    private fun tip() = PartnerTipDto(
        id = "tip-id",
        title = "Nachfragen",
        body = "Frag zuerst, was hilfreich wäre.",
        callToAction = "Kurz nachfragen",
        category = "COMMUNICATION",
    )
}
