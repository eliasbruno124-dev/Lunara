package de.lunara.app.data.repository

import de.lunara.app.data.local.MutationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PeriodMutationPolicyTest {
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
