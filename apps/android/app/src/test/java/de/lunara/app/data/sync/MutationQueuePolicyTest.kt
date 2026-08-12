package de.lunara.app.data.sync

import de.lunara.app.data.local.MutationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
