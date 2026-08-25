package de.lunara.app.data.preferences

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalIdentityPolicyTest {
    @Test
    fun validStoredIdentityStaysStableAcrossRestarts() {
        val stored = "d9428888-122b-4e26-91d2-3dd94e6b994b"
        var generated = 0

        assertEquals(stored, LocalIdentityPolicy.resolve(stored) { generated += 1; UUID.randomUUID() })
        assertEquals(0, generated)
    }

    @Test
    fun missingOrInvalidIdentityIsReplacedWithValidUuid() {
        val replacement = UUID.fromString("6ba7b810-9dad-41d1-80b4-00c04fd430c8")

        assertEquals(replacement.toString(), LocalIdentityPolicy.resolve(null) { replacement })
        assertEquals(replacement.toString(), LocalIdentityPolicy.resolve("not-a-uuid") { replacement })
    }
}
