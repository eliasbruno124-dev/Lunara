package de.lunara.app.data.repository

import de.lunara.app.data.remote.dto.CycleProfileDto
import org.junit.Assert.*
import org.junit.Test

class CycleSettingsTest {
    @Test fun emptyInputMeansAutomatic() {
        assertNull(CycleSettings.parseLength("  ", 15..90).getOrThrow())
    }

    @Test fun lengthsOutsideTheServerLimitsAreRejected() {
        assertTrue(CycleSettings.parseLength("14", 15..90).isFailure)
        assertTrue(CycleSettings.parseLength("91", 15..90).isFailure)
        assertTrue(CycleSettings.parseLength("abc", 1..14).isFailure)
        assertEquals(28, CycleSettings.parseLength("28", 15..90).getOrThrow())
    }

    @Test fun unknownLifePhasesFallBackToRegular() {
        val settings = CycleSettings.of(CycleProfileDto(specialMode = "SOMETHING_NEW", typicalCycleLengthOptional = 30, predictionPaused = true))
        assertEquals("NONE", settings.specialMode)
        assertEquals(30, settings.typicalCycleLength)
        assertTrue(settings.predictionPaused)
    }

    @Test fun lifePhasesAndPauseSwitchPredictionsOff() {
        val regular = CycleSettings("NONE", null, null, false)
        assertFalse(regular.switchesPredictionsOff())
        assertTrue(regular.copy(predictionPaused = true).switchesPredictionsOff())
        assertTrue(regular.copy(specialMode = "PREGNANCY").switchesPredictionsOff())
    }
}
