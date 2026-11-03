package de.lunara.app.core.locale

import org.junit.Assert.*
import org.junit.Test

class AppLanguageTest {
    @Test fun newInstallationsStartInEnglish() {
        assertEquals("en", AppLanguage.initialLanguage(explicitChoice = null, existingInstallation = false))
    }

    @Test fun existingInstallationsKeepFollowingTheDevice() {
        assertNull(AppLanguage.initialLanguage(explicitChoice = null, existingInstallation = true))
    }

    @Test fun anExplicitEarlierChoiceIsKept() {
        assertEquals("de", AppLanguage.initialLanguage(explicitChoice = "de", existingInstallation = true))
        assertEquals("de", AppLanguage.initialLanguage(explicitChoice = "de", existingInstallation = false))
    }

    @Test fun unsupportedFrameworkLanguagesFallBackToTheDefaultPolicy() {
        assertEquals("en", AppLanguage.initialLanguage(explicitChoice = "fr", existingInstallation = false))
        assertNull(AppLanguage.initialLanguage(explicitChoice = "fr", existingInstallation = true))
        assertFalse(AppLanguage.isSupported("fr"))
    }
}
