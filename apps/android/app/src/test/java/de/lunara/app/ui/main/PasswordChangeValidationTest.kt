package de.lunara.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordChangeValidationTest {
    @Test
    fun `valid password change passes`() {
        assertNull(validatePasswordChange("old-password-2026", "new-password-2026", "new-password-2026"))
    }

    @Test
    fun `current password is required`() {
        assertEquals(
            PasswordChangeValidation.CURRENT_REQUIRED,
            validatePasswordChange("", "new-password-2026", "new-password-2026"),
        )
    }

    @Test
    fun `new password follows server length`() {
        assertEquals(
            PasswordChangeValidation.NEW_LENGTH,
            validatePasswordChange("old-password-2026", "short", "short"),
        )
    }

    @Test
    fun `new password must differ`() {
        assertEquals(
            PasswordChangeValidation.SAME_PASSWORD,
            validatePasswordChange("same-password-2026", "same-password-2026", "same-password-2026"),
        )
    }

    @Test
    fun `confirmation must match`() {
        assertEquals(
            PasswordChangeValidation.CONFIRMATION_MISMATCH,
            validatePasswordChange("old-password-2026", "new-password-2026", "different-password"),
        )
    }
}
