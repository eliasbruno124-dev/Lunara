package de.lunara.app.data.repository

import de.lunara.app.data.remote.dto.RelationshipDto

/** Connection to a person is independent of network reachability and health-data sharing. */
data class RelationshipState(
    val known: Boolean = false,
    val connected: Boolean = false,
    val peerName: String? = null,
    val checkedAt: String? = null,
    val refreshing: Boolean = false,
    val unavailable: Boolean = false,
)

internal data class RelationshipSnapshot(
    val baseUrl: String,
    val userId: String,
    val state: RelationshipState,
)

internal object RelationshipPolicy {
    fun fromServer(response: RelationshipDto, ownUserId: String, checkedAt: String): RelationshipState {
        require(response.members.any { it.userId == ownUserId && !it.disabled })
        val peer = response.members.firstOrNull { it.userId.isNotBlank() && it.userId != ownUserId && !it.disabled }
        return RelationshipState(known = true, connected = peer != null,
            peerName = peer?.displayName?.takeIf(String::isNotBlank), checkedAt = checkedAt)
    }

    fun cached(snapshot: RelationshipSnapshot?, baseUrl: String, userId: String): RelationshipState? =
        snapshot?.takeIf { it.baseUrl == baseUrl && it.userId == userId && it.state.known }
            ?.state?.copy(unavailable = true, refreshing = false)

    fun failed(previous: RelationshipState): RelationshipState = previous.copy(refreshing = false, unavailable = true)
}
