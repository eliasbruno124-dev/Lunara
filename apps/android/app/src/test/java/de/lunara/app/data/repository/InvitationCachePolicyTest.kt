package de.lunara.app.data.repository

import de.lunara.app.data.remote.dto.InvitationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvitationCachePolicyTest {
    @Test
    fun `restores only the matching active invitation code`() {
        val remote = InvitationDto("active", assignedRole = "PARTNER", expiresAt = null)
        val cached = InvitationDto("active", code = "ABCDEFGH2345", assignedRole = "PARTNER")

        assertEquals("ABCDEFGH2345", InvitationCachePolicy.merge(remote, cached)?.code)
        assertNull(
            InvitationCachePolicy.merge(
                remote,
                cached.copy(id = "revoked"),
            )?.code,
        )
    }

    @Test
    fun `server absence invalidates the cached bearer code`() {
        val cached = InvitationDto("used", code = "ABCDEFGH2345", assignedRole = "PARTNER")

        assertNull(InvitationCachePolicy.merge(null, cached))
    }
}
