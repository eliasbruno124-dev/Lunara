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
    fun `only an open period can be ended by the primary action`() {
        assertTrue(PeriodMutationPolicy.canEnd(period("open", "2026-08-10", null)))
        assertEquals(
            false,
            PeriodMutationPolicy.canEnd(period("closed", "2026-08-10", "2026-08-12")),
        )
    }

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
    fun `period create carries a known historical end date in the single create payload`() {
        val payload = JsonParser.parseString(
            PeriodPayloadPolicy.create(
                Gson(),
                "period-1",
                LocalDate.parse("2026-07-10"),
                LocalDate.parse("2026-07-14"),
            ),
        ).asJsonObject

        assertEquals("period-1", payload.get("id").asString)
        assertEquals("2026-07-10", payload.get("startDate").asString)
        assertEquals("2026-07-14", payload.get("endDate").asString)
        assertEquals("MANUAL", payload.get("source").asString)
        assertEquals(false, payload.has("version"))
    }

    @Test
    fun `open period create explicitly retains a null end date`() {
        val payload = JsonParser.parseString(
            PeriodPayloadPolicy.create(
                Gson(),
                "period-open",
                LocalDate.parse("2026-08-13"),
                null,
            ),
        ).asJsonObject

        assertTrue(payload.has("endDate"))
        assertTrue(payload.get("endDate").isJsonNull)
    }

    @Test
    fun `ending an unattempted create can replace it with one complete create payload`() {
        val create = mutation(operation = "UPSERT", baseVersion = 0)
        val replaceable = PeriodMutationPolicy.replaceableTail(listOf(create))
        val completedPayload = JsonParser.parseString(
            PeriodPayloadPolicy.create(
                Gson(),
                "period-open",
                LocalDate.parse("2026-08-10"),
                LocalDate.parse("2026-08-13"),
            ),
        ).asJsonObject

        assertEquals(create, replaceable)
        assertEquals("2026-08-13", completedPayload.get("endDate").asString)
        assertEquals(false, completedPayload.has("version"))
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

    @Test
    fun `period overlap policy accepts adjacent closed ranges but rejects shared boundary days`() {
        val existing = period("existing", "2026-07-10", "2026-07-14")
        val today = LocalDate.parse("2026-08-16")

        assertEquals(
            false,
            PeriodOverlapPolicy.hasOverlap(
                listOf(existing),
                LocalDate.parse("2026-07-15"),
                LocalDate.parse("2026-07-18"),
                today,
            ),
        )
        assertTrue(
            PeriodOverlapPolicy.hasOverlap(
                listOf(existing),
                LocalDate.parse("2026-07-14"),
                LocalDate.parse("2026-07-18"),
                today,
            ),
        )
    }

    @Test
    fun `open periods overlap through today and block a second open range`() {
        val existing = period("open", "2026-08-10", null)
        val today = LocalDate.parse("2026-08-16")

        assertTrue(
            PeriodOverlapPolicy.hasOverlap(
                listOf(existing),
                LocalDate.parse("2026-08-12"),
                null,
                today,
            ),
        )
        assertEquals(
            false,
            PeriodOverlapPolicy.hasOverlap(
                listOf(existing),
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-05"),
                today,
            ),
        )
    }

    @Test
    fun `period overlap policy excludes the edited record and ignores invalid or deleted data`() {
        val current = period("current", "2026-08-10", null)
        val malformed = period("malformed", "not-a-date", null)
        val deleted = period("deleted", "2026-08-12", null).copy(deletedAt = "2026-08-13T00:00:00Z")
        val today = LocalDate.parse("2026-08-16")

        assertEquals(
            false,
            PeriodOverlapPolicy.hasOverlap(
                listOf(current, malformed, deleted),
                LocalDate.parse("2026-08-10"),
                null,
                today,
                excludingId = "current",
            ),
        )
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

    private fun period(id: String, startDate: String, endDate: String?) = PeriodEntity(
        id = id,
        userId = "tracker",
        startDate = startDate,
        endDate = endDate,
        createdAt = "",
        updatedAt = "",
        syncState = "SYNCED",
    )
}
