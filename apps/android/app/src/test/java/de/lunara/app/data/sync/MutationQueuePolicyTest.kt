package de.lunara.app.data.sync

import de.lunara.app.data.local.MutationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class MutationQueuePolicyTest {
    @Test
    fun `deduplicates by client mutation id and preserves stable creation order`() {
        val later = mutation("b", "2026-08-12T12:01:00Z", "2")
        val first = mutation("a", "2026-08-12T12:00:00Z", "1")
        val duplicate = mutation("a", "2026-08-12T12:02:00Z", "3")

        val result = MutationQueuePolicy.stableOrder(listOf(later, first, duplicate))

        assertEquals(listOf("a", "b"), result.map { it.clientMutationId })
    }

    @Test
    fun `backoff grows and is capped`() {
        assertEquals(15_000L, MutationQueuePolicy.nextDelayMillis(0))
        assertTrue(MutationQueuePolicy.nextDelayMillis(3) > MutationQueuePolicy.nextDelayMillis(2))
        assertEquals(3_840_000L, MutationQueuePolicy.nextDelayMillis(20))
    }

    @Test
    fun `eighth failed mutation attempt becomes terminal`() {
        assertTrue(!MutationQueuePolicy.isTerminalAfterNextFailure(6))
        assertTrue(MutationQueuePolicy.isTerminalAfterNextFailure(7))
        assertTrue(MutationQueuePolicy.isTerminalAfterNextFailure(20))
    }

    @Test
    fun `sync authentication never permits an unauthenticated request`() {
        assertEquals(SyncAuthenticationAction.SKIP, SyncAuthenticationPolicy.decide(false, "access", "refresh"))
        assertEquals(SyncAuthenticationAction.SKIP, SyncAuthenticationPolicy.decide(true, null, null))
        assertEquals(SyncAuthenticationAction.REFRESH, SyncAuthenticationPolicy.decide(true, null, "refresh"))
        assertEquals(SyncAuthenticationAction.SYNC, SyncAuthenticationPolicy.decide(true, "access", null))
    }

    @Test
    fun `dependent period operations remain in deterministic order`() {
        val create = mutation("create", "2026-08-13T10:00:00.000000000Z", "a")
        val delete = mutation("delete", "2026-08-13T10:00:00.000000001Z", "b")

        assertEquals(listOf("create", "delete"), MutationQueuePolicy.stableOrder(listOf(delete, create)).map { it.clientMutationId })
    }

    @Test
    fun `successful queue drain continues inline and schedules only at the fair cap`() {
        assertFalse(SyncDrainPolicy.needsContinuation(99, anotherMutationReady = true))
        assertTrue(SyncDrainPolicy.needsContinuation(100, anotherMutationReady = true))
        assertFalse(SyncDrainPolicy.needsContinuation(100, anotherMutationReady = false))
    }

    @Test
    fun `only an in-progress idempotency reservation is a transient 409`() {
        assertTrue(
            SyncConflictPolicy.isTransientReservation(
                409,
                "{\"message\":\"This mutation is already being processed; retry shortly\"}",
            ),
        )
        assertFalse(SyncConflictPolicy.isTransientReservation(409, "Daily log changed on another device"))
        assertFalse(SyncConflictPolicy.isTransientReservation(500, "already being processed"))
    }

    @Test
    fun `resumed full replay finalizes resolving rows on its last page`() {
        assertFalse(SyncReplayPolicy.shouldFinalizeResolvingRows(hasMore = true))
        assertTrue(SyncReplayPolicy.shouldFinalizeResolvingRows(hasMore = false))
    }

    @Test
    fun `acknowledged create preserves a waiting delete and protects its remote echo`() {
        val key = SyncAcknowledgementPolicy.key("PERIOD", "period-1")

        assertTrue(SyncAcknowledgementPolicy.preserveOptimisticState(1))
        assertTrue(
            SyncAcknowledgementPolicy.protectsRemoteChange(
                "PeriodEvent",
                "period-1",
                setOf(key),
            ),
        )
        assertFalse(
            SyncAcknowledgementPolicy.protectsRemoteChange(
                "PeriodEvent",
                "period-2",
                setOf(key),
            ),
        )
    }

    @Test
    fun `pending and conflicted rows stay protected until explicitly resolved`() {
        assertTrue(SyncAcknowledgementPolicy.protectsLocalState("PENDING"))
        assertTrue(SyncAcknowledgementPolicy.protectsLocalState("CONFLICT"))
        assertFalse(SyncAcknowledgementPolicy.protectsLocalState("SYNCED"))
        assertFalse(SyncAcknowledgementPolicy.protectsLocalState("RESOLVING"))
        assertFalse(SyncAcknowledgementPolicy.protectsLocalState(null))
    }

    @Test
    fun `delete acknowledgement advances exactly one server version`() {
        assertEquals(8L, SyncAcknowledgementPolicy.completedVersion("DELETE", 7, null))
        assertEquals(8L, SyncAcknowledgementPolicy.completedVersion("DELETE", 7, 9))
        assertThrows(IllegalArgumentException::class.java) {
            SyncAcknowledgementPolicy.completedVersion("DELETE", 0, null)
        }
    }

    @Test
    fun `stale create echo cannot resurrect an acknowledged delete`() {
        assertFalse(SyncAcknowledgementPolicy.shouldApplyRemoteUpsert(localVersion = 2, incomingVersion = 1))
        assertFalse(SyncAcknowledgementPolicy.shouldApplyRemoteUpsert(localVersion = 2, incomingVersion = 2))
        assertTrue(SyncAcknowledgementPolicy.shouldApplyRemoteDelete(localVersion = 2, incomingVersion = 2))
    }

    private fun mutation(clientId: String, created: String, id: String) = MutationEntity(
        id = id,
        clientMutationId = clientId,
        entityType = "PERIOD_EVENT",
        entityId = id,
        operation = "CREATE",
        payloadJson = "{}",
        baseVersion = 0,
        createdAt = created,
        nextAttemptAt = created,
    )
}
