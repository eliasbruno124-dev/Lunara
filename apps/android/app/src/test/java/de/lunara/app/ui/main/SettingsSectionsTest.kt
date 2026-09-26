package de.lunara.app.ui.main

import de.lunara.app.core.model.UserRole
import org.junit.Assert.*
import org.junit.Test

class SettingsSectionsTest {
    @Test fun trackerCanReachEveryExistingSettingsArea() {
        assertEquals(SettingsSection.entries, availableSettingsSections(UserRole.TRACKER, false))
    }
    @Test fun partnerCannotSeeTrackerOnlyAreas() {
        val sections = availableSettingsSections(UserRole.PARTNER, false)
        assertTrue(SettingsSection.PARTNER in sections)
        assertTrue(SettingsSection.ACCOUNT in sections)
        assertFalse(SettingsSection.SHARING in sections)
        assertFalse(SettingsSection.CARE in sections)
        assertFalse(SettingsSection.CYCLE in sections)
    }
    @Test fun soloKeepsBackupSettingsButOmitsServerRelationshipControls() {
        val sections = availableSettingsSections(UserRole.TRACKER, true)
        assertTrue(SettingsSection.ACCOUNT in sections)
        assertTrue(SettingsSection.CYCLE in sections)
        assertFalse(SettingsSection.PARTNER in sections)
        assertFalse(SettingsSection.SHARING in sections)
        assertFalse(SettingsSection.CARE in sections)
    }
}
