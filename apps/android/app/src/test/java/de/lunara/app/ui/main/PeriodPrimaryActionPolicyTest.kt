package de.lunara.app.ui.main

import de.lunara.app.data.local.PeriodEntity
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodPrimaryActionPolicyTest {
    private val today = LocalDate.parse("2026-08-16")

    @Test fun `day five offers end and day six offers start without mutating raw end`() {
        val raw = period("unconfirmed", "2026-11-01")
        assertEquals(PeriodPrimaryActionType.END, PeriodPrimaryActionPolicy.resolve(listOf(raw), LocalDate.parse("2026-11-05")).type)
        assertEquals(PeriodPrimaryActionType.START, PeriodPrimaryActionPolicy.resolve(listOf(raw), LocalDate.parse("2026-11-06")).type)
        assertNull(raw.endDate)
    }

    @Test fun `profile calendar day controls the fifth day boundary instead of device midnight`() {
        val instant = Instant.parse("2026-11-05T23:30:00Z")
        val device = ZoneId.of("America/Los_Angeles")
        val row = period("profile", "2026-11-01")
        val profileDay = PredictionFreshnessPolicy.referenceDate(instant, "Europe/Berlin", device)
        val deviceDay = PredictionFreshnessPolicy.referenceDate(instant, null, device)
        assertEquals(PeriodPrimaryActionType.START, PeriodPrimaryActionPolicy.resolve(listOf(row), profileDay).type)
        assertEquals(PeriodPrimaryActionType.END, PeriodPrimaryActionPolicy.resolve(listOf(row), deviceDay).type)
    }

    @Test
    fun `offers start when no ongoing or today-completed period exists`() {
        val action = PeriodPrimaryActionPolicy.resolve(
            listOf(period("older", "2026-07-10", endDate = "2026-07-14")),
            today,
        )

        assertEquals(PeriodPrimaryActionType.START, action.type)
        assertTrue(action.enabled)
        assertNull(action.periodId)
        assertNull(action.startDate)
    }

    @Test
    fun `offers end for the newest ongoing period`() {
        val action = PeriodPrimaryActionPolicy.resolve(
            listOf(
                period("old-open", "2026-06-01"),
                period("current", "2026-08-14"),
                period("closed", "2026-08-01", endDate = "2026-08-05"),
            ),
            today,
        )

        assertEquals(PeriodPrimaryActionType.END, action.type)
        assertTrue(action.enabled)
        assertEquals("current", action.periodId)
        assertEquals(LocalDate.parse("2026-08-14"), action.startDate)
    }

    @Test
    fun `ongoing conflicted or resolving period keeps end action disabled`() {
        listOf("CONFLICT", "RESOLVING").forEach { syncState ->
            val action = PeriodPrimaryActionPolicy.resolve(
                listOf(period("blocked", "2026-08-14", syncState = syncState)),
                today,
            )

            assertEquals(PeriodPrimaryActionType.END, action.type)
            assertFalse(action.enabled)
            assertEquals("blocked", action.periodId)
        }
    }

    @Test
    fun `reports done after period was ended today and cannot immediately start again`() {
        val action = PeriodPrimaryActionPolicy.resolve(
            listOf(period("completed", "2026-08-12", endDate = today.toString())),
            today,
        )

        assertEquals(PeriodPrimaryActionType.DONE, action.type)
        assertFalse(action.enabled)
        assertEquals("completed", action.periodId)
        assertEquals(LocalDate.parse("2026-08-12"), action.startDate)
    }

    @Test
    fun `ongoing period takes priority over a separate period completed today`() {
        val action = PeriodPrimaryActionPolicy.resolve(
            listOf(
                period("completed", "2026-08-01", endDate = today.toString()),
                period("open", "2026-08-15"),
            ),
            today,
        )

        assertEquals(PeriodPrimaryActionType.END, action.type)
        assertEquals("open", action.periodId)
    }

    @Test
    fun `ignores deleted malformed and future records`() {
        val action = PeriodPrimaryActionPolicy.resolve(
            listOf(
                period("deleted", "2026-08-10", deletedAt = "2026-08-15T00:00:00Z"),
                period("malformed", "not-a-date"),
                period("future", "2026-08-17"),
            ),
            today,
        )

        assertEquals(PeriodPrimaryActionType.START, action.type)
        assertTrue(action.enabled)
    }

    private fun period(
        id: String,
        startDate: String,
        endDate: String? = null,
        deletedAt: String? = null,
        syncState: String = "SYNCED",
    ) = PeriodEntity(
        id = id,
        userId = "tracker",
        startDate = startDate,
        endDate = endDate,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        deletedAt = deletedAt,
        syncState = syncState,
    )
}
