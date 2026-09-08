package de.lunara.app.data.repository

import de.lunara.app.core.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationScopeResetPolicyTest {
    @Test
    fun `tracker to partner acceptance resets the old server scope`() {
        assertTrue(requiresInvitationScopeReset(UserRole.TRACKER, UserRole.PARTNER))
    }

    @Test
    fun `unchanged partner refresh does not reset its current scope`() {
        assertFalse(requiresInvitationScopeReset(UserRole.PARTNER, UserRole.PARTNER))
    }

    @Test
    fun `any queued local mutation blocks invitation acceptance`() {
        assertFalse(blocksInvitationAcceptance(0))
        assertTrue(blocksInvitationAcceptance(1))
        assertTrue(blocksInvitationAcceptance(4))
    }
}
