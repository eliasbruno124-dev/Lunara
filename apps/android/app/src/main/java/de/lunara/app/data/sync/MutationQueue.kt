package de.lunara.app.data.sync

import de.lunara.app.data.local.MutationEntity
import kotlin.math.min

object MutationQueuePolicy {
    const val MAX_ATTEMPTS = 8

    fun isTerminalAfterNextFailure(attemptCount: Int): Boolean =
        attemptCount + 1 >= MAX_ATTEMPTS

    fun nextDelayMillis(attempt: Int): Long {
        val bounded = attempt.coerceIn(0, MAX_ATTEMPTS)
        return min(6L * 60L * 60L * 1_000L, 15_000L * (1L shl bounded))
    }

    fun stableOrder(items: List<MutationEntity>): List<MutationEntity> =
        items.distinctBy { it.clientMutationId }.sortedWith(compareBy({ it.createdAt }, { it.id }))
}

internal object SyncDrainPolicy {
    const val MAX_MUTATIONS_PER_RUN = 100

    fun needsContinuation(completedMutations: Int, anotherMutationReady: Boolean): Boolean =
        anotherMutationReady && completedMutations >= MAX_MUTATIONS_PER_RUN
}

internal object SyncConflictPolicy {
    fun isTransientReservation(status: Int, responseDetails: String?): Boolean =
        status == 409 && responseDetails?.contains("already being processed", ignoreCase = true) == true
}

internal object SyncReplayPolicy {
    /** Finalization depends on completing the replay, not on the cursor used to start this run. */
    fun shouldFinalizeResolvingRows(hasMore: Boolean): Boolean = !hasMore
}

internal data class SyncEntityKey(val type: String, val id: String)

internal object SyncAcknowledgementPolicy {
    fun key(entityType: String, entityId: String): SyncEntityKey = SyncEntityKey(
        type = when (entityType) {
            "PeriodEvent" -> "PERIOD"
            "DailyLog" -> "DAILY_LOG"
            else -> entityType
        },
        id = entityId,
    )

    fun preserveOptimisticState(unresolvedFollowUpCount: Int): Boolean = unresolvedFollowUpCount > 0

    /** Remote echoes must not overwrite edits that still need an explicit local decision. */
    fun protectsLocalState(syncState: String?): Boolean =
        syncState == "PENDING" || syncState == "CONFLICT"

    fun completedVersion(operation: String, baseVersion: Long, responseVersion: Long?): Long = when (operation) {
        "DELETE" -> {
            require(baseVersion > 0L) { "A delete acknowledgement needs a positive base version" }
            // The DELETE endpoint performs one compare-and-increment operation and does not
            // return an entity body. Deriving this from its optimistic base is deterministic.
            baseVersion + 1L
        }
        else -> responseVersion ?: if (baseVersion == 0L) 1L else baseVersion + 1L
    }

    fun protectsRemoteChange(
        entityType: String,
        entityId: String,
        protectedEntities: Set<SyncEntityKey>,
    ): Boolean = key(entityType, entityId) in protectedEntities

    fun shouldApplyRemoteUpsert(localVersion: Long?, incomingVersion: Long): Boolean =
        localVersion == null || incomingVersion > localVersion

    fun shouldApplyRemoteDelete(localVersion: Long?, incomingVersion: Long?): Boolean =
        localVersion == null || incomingVersion == null || incomingVersion >= localVersion
}

internal enum class SyncAuthenticationAction { SKIP, REFRESH, SYNC }

internal object SyncAuthenticationPolicy {
    fun decide(hasSession: Boolean, accessToken: String?, refreshToken: String?): SyncAuthenticationAction = when {
        !hasSession -> SyncAuthenticationAction.SKIP
        !accessToken.isNullOrBlank() -> SyncAuthenticationAction.SYNC
        !refreshToken.isNullOrBlank() -> SyncAuthenticationAction.REFRESH
        else -> SyncAuthenticationAction.SKIP
    }
}
