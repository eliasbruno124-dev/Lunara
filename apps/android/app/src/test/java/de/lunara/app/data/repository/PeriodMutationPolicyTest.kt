package de.lunara.app.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import de.lunara.app.data.local.MutationEntity
import de.lunara.app.data.local.PeriodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PeriodMutationPolicyTest {
    @Test
    fun `an end-only edit is treated as a real period change`() {
        val current = PeriodEntity(
            id = "period",
            userId = "tracker",
            startDate = "2026-08-10",
            endDate = null,
            createdAt = "",
            updatedAt = "",
        )

        assertTrue(
            PeriodMutationPolicy.datesChanged(
                current,
                LocalDate.parse("2026-08-10"),
                LocalDate.parse("2026-08-12"),
            ),
        )
        assertEquals(
            false,
            PeriodMutationPolicy.datesChanged(current, LocalDate.parse("2026-08-10"), null),
        )
    }

    @Test
    fun `fresh unsent period mutation is replaceable before its first attempt`() {
        val create = mutation(operation = "UPSERT", baseVersion = 0)

        assertEquals(create, PeriodMutationPolicy.replaceableTail(listOf(create)))
    }

    @Test
    fun `fresh create predicts version one for a dependent delete`() {
        val create = mutation(operation = "UPSERT", baseVersion = 0, attemptCount = 1)

        assertEquals(1L, PeriodMutationPolicy.predictedServerVersion(0, listOf(create)))
    }

    @Test
    fun `attempted create is never discarded locally`() {
        val attempted = mutation(operation = "UPSERT", baseVersion = 0, attemptCount = 1)

        assertNull(PeriodMutationPolicy.replaceableTail(listOf(attempted)))
        assertEquals(1L, PeriodMutationPolicy.predictedServerVersion(0, listOf(attempted)))
    }

    @Test
    fun `dependent edits use the version produced by preceding mutations`() {
        val pendingUpdate = mutation(operation = "UPSERT", baseVersion = 4, attemptCount = 1)
        val pendingDelete = mutation(operation = "DELETE", baseVersion = 5)

        assertEquals(6L, PeriodMutationPolicy.predictedServerVersion(4, listOf(pendingUpdate, pendingDelete)))
    }

    @Test
    fun `only the final unattempted mutation can be replaced`() {
        val inFlightDependency = mutation(operation = "UPSERT", baseVersion = 2, state = "IN_FLIGHT")
        val unattemptedTail = mutation(id = "tail", operation = "UPSERT", baseVersion = 3)

        assertEquals(unattemptedTail, PeriodMutationPolicy.replaceableTail(listOf(inFlightDependency, unattemptedTail)))
    }

    @Test
    fun `conflicted and resolving records remain read only until explicit resolution completes`() {
        assertEquals(true, SyncEditPolicy.isBlocked("CONFLICT"))
        assertEquals(true, SyncEditPolicy.isBlocked("RESOLVING"))
        assertEquals(false, SyncEditPolicy.isBlocked("PENDING"))
        assertEquals(false, SyncEditPolicy.isBlocked("SYNCED"))
    }

    @Test
    fun `fixed width mutation timestamps sort chronologically across nanoseconds and seconds`() {
        val millisecond = MutationTimestampPolicy.format(Instant.parse("2026-08-13T10:00:00.123Z"))
        val nextNanosecond = MutationTimestampPolicy.format(Instant.parse("2026-08-13T10:00:00.123000001Z"))
        val finalNanosecond = MutationTimestampPolicy.format(Instant.parse("2026-08-13T10:00:00.999999999Z"))
        val nextSecond = MutationTimestampPolicy.format(Instant.parse("2026-08-13T10:00:01Z"))

        assertEquals("2026-08-13T10:00:00.123000000Z", millisecond)
        assertEquals("2026-08-13T10:00:00.123000001Z", nextNanosecond)
        assertEquals("2026-08-13T10:00:00.999999999Z", finalNanosecond)
        assertEquals("2026-08-13T10:00:01.000000000Z", nextSecond)
        assertEquals(
            listOf(millisecond, nextNanosecond, finalNanosecond, nextSecond),
            listOf(nextSecond, nextNanosecond, finalNanosecond, millisecond).sorted(),
        )
    }

    @Test
    fun `period patch preserves the optional end date and sends explicit null when removed`() {
        val withEnd = JsonParser.parseString(
            PeriodPayloadPolicy.update(
                Gson(),
                "period-1",
                LocalDate.parse("2026-08-10"),
                LocalDate.parse("2026-08-13"),
                4,
            ),
        ).asJsonObject
        val withoutEnd = JsonParser.parseString(
            PeriodPayloadPolicy.update(
                Gson(),
                "period-1",
                LocalDate.parse("2026-08-10"),
                null,
                5,
            ),
        ).asJsonObject

        assertEquals("2026-08-13", withEnd.get("endDate").asString)
        assertEquals(4, withEnd.get("version").asInt)
        assertTrue(withoutEnd.has("endDate"))
        assertTrue(withoutEnd.get("endDate").isJsonNull)
    }

    @Test
    fun `repository date policy rejects future and reversed periods`() {
        val today = LocalDate.parse("2026-08-13")

        PeriodDatePolicy.requireValid(LocalDate.parse("2026-08-10"), null, today)
        PeriodDatePolicy.requireValid(
            LocalDate.parse("2026-08-10"),
            LocalDate.parse("2026-08-13"),
            today,
        )
        assertThrows(PeriodDateInFutureException::class.java) {
            PeriodDatePolicy.requireValid(
                LocalDate.parse("2026-08-10"),
                LocalDate.parse("2026-08-14"),
                today,
            )
        }
        assertThrows(InvalidPeriodRangeException::class.java) {
            PeriodDatePolicy.requireValid(
                LocalDate.parse("2026-08-10"),
                LocalDate.parse("2026-08-09"),
                today,
            )
        }
    }

    private fun mutation(
        id: String = "mutation",
        operation: String,
        baseVersion: Long,
        state: String = "PENDING",
        attemptCount: Int = 0,
    ) = MutationEntity(
        id = id,
        clientMutationId = "client-$id",
        entityType = "PERIOD",
        entityId = "period",
        operation = operation,
        payloadJson = "{}",
        baseVersion = baseVersion,
        createdAt = "2026-08-13T00:00:00Z",
        attemptCount = attemptCount,
        nextAttemptAt = "2026-08-13T00:00:00Z",
        state = state,
    )
}
