package de.lunara.app.data.sync

import de.lunara.app.data.local.MutationEntity
import kotlin.math.min

object MutationQueuePolicy {
    const val MAX_ATTEMPTS = 8

    fun nextDelayMillis(attempt: Int): Long {
        val bounded = attempt.coerceIn(0, MAX_ATTEMPTS)
        return min(6L * 60L * 60L * 1_000L, 15_000L * (1L shl bounded))
    }

    fun stableOrder(items: List<MutationEntity>): List<MutationEntity> =
        items.distinctBy { it.clientMutationId }.sortedWith(compareBy({ it.createdAt }, { it.id }))
}
