package de.lunara.app.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionUiIsolationTest {
    @Test
    fun `session data is visible only to its current owner`() {
        assertTrue(SessionUiIsolation.canExpose("current-user", "current-user"))
        assertFalse(SessionUiIsolation.canExpose("new-user", "old-user"))
        assertFalse(SessionUiIsolation.canExpose(null, "old-user"))
        assertFalse(SessionUiIsolation.canExpose("current-user", null))
    }
}
