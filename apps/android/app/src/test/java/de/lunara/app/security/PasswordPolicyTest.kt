package de.lunara.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {
    @Test
    fun `new password boundaries count unicode code points`() {
        assertFalse(isValidNewAccountPassword(LOCK.repeat(7)))
        assertTrue(isValidNewAccountPassword(LOCK.repeat(8)))
        assertTrue(isValidNewAccountPassword(LOCK.repeat(128)))
        assertFalse(isValidNewAccountPassword(LOCK.repeat(129)))
        assertFalse(isValidNewAccountPassword(HEART_WITH_VARIATION_SELECTOR.repeat(3)))
        assertTrue(isValidNewAccountPassword(HEART_WITH_VARIATION_SELECTOR.repeat(4)))
        assertFalse(isValidNewAccountPassword(HEART_WITH_VARIATION_SELECTOR.repeat(65)))
    }

    @Test
    fun `existing password compatibility still enforces the code point maximum`() {
        assertTrue(isValidExistingPassword(LOCK))
        assertTrue(isValidExistingPassword(LOCK.repeat(128)))
        assertFalse(isValidExistingPassword(LOCK.repeat(129)))
    }

    @Test
    fun `input limit preserves the supplementary character at the boundary`() {
        val input = "a".repeat(127) + LOCK + "z"
        val limited = limitAccountPasswordInput(input)

        assertEquals(128, limited.codePointCount(0, limited.length))
        assertTrue(limited.endsWith(LOCK))
        assertTrue(Character.isSurrogatePair(limited[limited.lastIndex - 1], limited.last()))
    }

    @Test
    fun `input below the maximum remains unchanged`() {
        val input = "a".repeat(126) + LOCK
        assertEquals(input, limitAccountPasswordInput(input))
    }

    private companion object {
        const val LOCK = "🔒"
        const val HEART_WITH_VARIATION_SELECTOR = "❤️"
    }
}
