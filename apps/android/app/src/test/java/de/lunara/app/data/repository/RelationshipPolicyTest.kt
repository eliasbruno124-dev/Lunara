package de.lunara.app.data.repository

import de.lunara.app.data.remote.dto.RelationshipDto
import de.lunara.app.data.remote.dto.RelationshipMemberDto
import org.junit.Assert.*
import org.junit.Test

class RelationshipPolicyTest {
    private val own = RelationshipMemberDto("PARTNER", userId = "own", displayName = "Me")
    private val peer = RelationshipMemberDto("TRACKER", userId = "peer", displayName = "Partner")

    @Test fun `partner role sees tracker and never counts self as a connection`() {
        val linked = RelationshipPolicy.fromServer(RelationshipDto(listOf(own, peer)), "own", "now")
        assertTrue(linked.known)
        assertTrue(linked.connected)
        assertEquals("Partner", linked.peerName)
        assertFalse(RelationshipPolicy.fromServer(RelationshipDto(listOf(own)), "own", "now").connected)
        assertFalse(RelationshipPolicy.fromServer(RelationshipDto(listOf(own, peer.copy(disabled = true))), "own", "now").connected)
    }

    @Test fun `network failure never invents a disconnection`() {
        val known = RelationshipPolicy.fromServer(RelationshipDto(listOf(own, peer)), "own", "now")
        val failed = RelationshipPolicy.failed(known)
        assertTrue(failed.connected)
        assertTrue(failed.unavailable)
        assertFalse(RelationshipPolicy.failed(RelationshipState()).known)
    }

    @Test fun `encrypted cached relationship is isolated by server and account`() {
        val snapshot = RelationshipSnapshot("https://a.example/", "own", RelationshipState(known = true, connected = true))
        assertNotNull(RelationshipPolicy.cached(snapshot, "https://a.example/", "own"))
        assertNull(RelationshipPolicy.cached(snapshot, "https://b.example/", "own"))
        assertNull(RelationshipPolicy.cached(snapshot, "https://a.example/", "another"))
        assertTrue(RelationshipPolicy.cached(snapshot, "https://a.example/", "own")!!.unavailable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unexpected household response is rejected`() {
        RelationshipPolicy.fromServer(RelationshipDto(listOf(peer)), "own", "now")
    }
}
