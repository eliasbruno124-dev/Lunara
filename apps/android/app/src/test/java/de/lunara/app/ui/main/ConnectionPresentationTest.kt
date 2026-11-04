package de.lunara.app.ui.main

import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.remote.dto.PredictionDto
import org.junit.Assert.*
import org.junit.Test

class ConnectionPresentationTest {
    @Test fun `badge reports mode or actionable state but never asserts online from an old sync`() {
        assertEquals(SyncBadge.LOCAL, ConnectionPresentation.badge(AppMode.LOCAL, true, 2, 3))
        assertEquals(SyncBadge.AUTHENTICATION, ConnectionPresentation.badge(AppMode.SERVER, true, 2, 3))
        assertEquals(SyncBadge.CONFLICT, ConnectionPresentation.badge(AppMode.SERVER, false, 2, 3))
        assertEquals(SyncBadge.PENDING, ConnectionPresentation.badge(AppMode.SERVER, false, 0, 3))
        assertEquals(SyncBadge.SERVER, ConnectionPresentation.badge(AppMode.SERVER, false, 0, 0))
    }

    @Test fun `window is inclusive and refers to possible starts not bleeding end`() {
        val prediction = PredictionDto(predictedStartFrom = "2026-09-09", predictedStartTo = "2026-09-16", predictedEndTo = "2026-09-21", confidence = "LOW", basedOnCycleCount = 2)
        assertEquals(8, ConnectionPresentation.predictionWindowDays(prediction))
        assertNull(ConnectionPresentation.predictionWindowDays(prediction.copy(available = false)))
        assertNull(ConnectionPresentation.predictionWindowDays(prediction.copy(predictedStartTo = "2026-09-08")))
        assertNull(ConnectionPresentation.predictionWindowDays(prediction.copy(predictedStartTo = "broken")))
    }

    @Test fun `PayPal link has one recipient without payment amount tracking or credentials`() {
        assertEquals("https://paypal.me/eliasbruno123", DonationLinkPolicy.safeUrl("https://paypal.me/eliasbruno123"))
        listOf("", "http://paypal.me/name", "https://paypal.me.evil.example/name", "https://name@paypal.me/name", "https://paypal.me/name/100", "https://paypal.me/name?email=private", "https://paypal.me/name#data", "javascript:alert(1)").forEach {
            assertNull(it, DonationLinkPolicy.safeUrl(it))
        }
    }

    @Test fun `unknown server version is not substituted with app version`() {
        assertEquals("—", ConnectionPresentation.version(" "))
        assertEquals("1.1.5", ConnectionPresentation.version("1.1.5"))
    }
}
