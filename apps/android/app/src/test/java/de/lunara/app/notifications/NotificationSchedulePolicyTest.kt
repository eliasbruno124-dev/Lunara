package de.lunara.app.notifications

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationSchedulePolicyTest {
    @Test
    fun activityCollectorEnsuresFirstEmissionAndReplacesOnlyRealChanges() {
        val collector = NotificationScheduleEmissionPolicy()

        assertEquals(NotificationScheduleUpdate.ENSURE, collector.next())
        assertEquals(NotificationScheduleUpdate.REPLACE, collector.next())
        assertEquals(NotificationScheduleUpdate.REPLACE, collector.next())
        assertEquals(NotificationScheduleUpdate.ENSURE, NotificationScheduleEmissionPolicy().next())
    }

    @Test
    fun schedulesTheNextStrictlyFutureLocalOccurrence() {
        val zone = ZoneId.of("Europe/Berlin")
        val preferences = enabled(reminderHour = 9, reminderMinute = 15)

        assertEquals(
            ZonedDateTime.of(2026, 8, 14, 9, 15, 0, 0, zone),
            NotificationSchedulePolicy.nextRun(
                ZonedDateTime.of(2026, 8, 14, 8, 0, 0, 0, zone),
                preferences,
            ),
        )
        assertEquals(
            ZonedDateTime.of(2026, 8, 15, 9, 15, 0, 0, zone),
            NotificationSchedulePolicy.nextRun(
                ZonedDateTime.of(2026, 8, 14, 9, 15, 0, 0, zone),
                preferences,
            ),
        )
    }

    @Test
    fun reminderInsideOvernightQuietTimeMovesToQuietEnd() {
        val zone = ZoneId.of("Europe/Berlin")
        val preferences = enabled(reminderHour = 23, reminderMinute = 30).copy(
            quietStartHour = 22,
            quietEndHour = 8,
        )

        assertEquals(
            ZonedDateTime.of(2026, 8, 15, 8, 0, 0, 0, zone),
            NotificationSchedulePolicy.nextRun(
                ZonedDateTime.of(2026, 8, 14, 20, 0, 0, 0, zone),
                preferences,
            ),
        )
    }

    @Test
    fun dozeDelayedRunInsideQuietTimeIsDeferredToQuietEndNotNextReminder() {
        val zone = ZoneId.of("Europe/Berlin")
        val preferences = enabled(reminderHour = 21, reminderMinute = 0).copy(
            quietStartHour = 22,
            quietEndHour = 8,
        )
        val delayedStart = ZonedDateTime.of(2026, 8, 14, 22, 10, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 8, 15, 8, 0, 0, 0, zone),
            NotificationSchedulePolicy.afterQuietTime(delayedStart, preferences),
        )
        assertEquals(
            ZonedDateTime.of(2026, 8, 15, 21, 0, 0, 0, zone),
            NotificationSchedulePolicy.nextRun(delayedStart, preferences),
        )
    }

    @Test
    fun springGapPreservesRequestedMinutesAtFirstValidOffset() {
        val zone = ZoneId.of("Europe/Berlin")
        val preferences = enabled(reminderHour = 2, reminderMinute = 30)

        val target = NotificationSchedulePolicy.nextRun(
            ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, zone),
            preferences,
        )

        assertEquals(LocalDateTime.of(2026, 3, 29, 3, 30), target?.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(2), target?.offset)
    }

    @Test
    fun autumnOverlapSelectsTheStillFutureSecondOffset() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.ofLocal(
            LocalDateTime.of(2026, 10, 25, 2, 15),
            zone,
            ZoneOffset.ofHours(1),
        )

        val target = NotificationSchedulePolicy.nextRun(now, enabled(reminderHour = 2, reminderMinute = 30))

        assertEquals(LocalDateTime.of(2026, 10, 25, 2, 30), target?.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(1), target?.offset)
    }

    @Test
    fun disabledOrEmptyPreferencesProduceNoWork() {
        val now = ZonedDateTime.parse("2026-08-14T08:00:00+02:00[Europe/Berlin]")
        assertNull(NotificationSchedulePolicy.nextRun(now, NotificationPreferences()))
        assertNull(
            NotificationSchedulePolicy.nextRun(
                now,
                NotificationPreferences(
                    master = true,
                    periodTomorrow = false,
                    fertileTomorrow = false,
                    dailyLogReminder = false,
                    partnerTip = false,
                ),
            ),
        )
    }

    private fun enabled(reminderHour: Int, reminderMinute: Int) = NotificationPreferences(
        master = true,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        quietStartHour = 0,
        quietStartMinute = 0,
        quietEndHour = 0,
        quietEndMinute = 0,
    )
}
