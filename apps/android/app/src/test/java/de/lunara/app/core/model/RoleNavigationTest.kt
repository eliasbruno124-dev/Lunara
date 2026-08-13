package de.lunara.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleNavigationTest {
    @Test
    fun `tracker receives history and period write capability`() {
        val destinations = RoleNavigation.destinations(UserRole.TRACKER)
        assertTrue(MainDestination.HISTORY in destinations)
        assertFalse(MainDestination.SUPPORT in destinations)
        assertTrue(RoleNavigation.canCreatePeriod(UserRole.TRACKER))
        assertTrue(RoleNavigation.canManagePeriods(UserRole.TRACKER))
    }

    @Test
    fun `partner receives support and no period write capability`() {
        val destinations = RoleNavigation.destinations(UserRole.PARTNER)
        assertEquals(
            listOf(MainDestination.TODAY, MainDestination.CALENDAR, MainDestination.SUPPORT, MainDestination.SETTINGS),
            destinations,
        )
        assertFalse(RoleNavigation.canCreatePeriod(UserRole.PARTNER))
        assertFalse(RoleNavigation.canManagePeriods(UserRole.PARTNER))
    }
}
