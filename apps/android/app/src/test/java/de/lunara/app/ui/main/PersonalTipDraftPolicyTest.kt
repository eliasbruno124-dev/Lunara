package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.CreatePersonalTipRequest
import org.junit.Assert.*
import org.junit.Test

class PersonalTipDraftPolicyTest {
    @Test fun `personal words are preserved in both language fields rather than generated`() {
        val request = CreatePersonalTipRequest("id", "Mein Wunsch", "Das entspannt mich.", "Bitte bring mir meinen Tee.")
        assertEquals(request.titleDe, request.titleEn)
        assertEquals(request.bodyDe, request.bodyEn)
        assertEquals(request.callToActionDe, request.callToActionEn)
        assertTrue(PersonalTipDraftPolicy.valid(request.titleDe, request.bodyDe, request.callToActionDe))
    }
    @Test fun `invalid lengths and controls cannot be submitted`() {
        assertFalse(PersonalTipDraftPolicy.valid("", "Text", "Aktion"))
        assertFalse(PersonalTipDraftPolicy.valid(" ", "Text", "Aktion"))
        assertFalse(PersonalTipDraftPolicy.valid("Titel", "Text", "x".repeat(241)))
        assertFalse(PersonalTipDraftPolicy.valid("Titel", "Text\nZeile", "Aktion"))
        assertTrue(PersonalTipDraftPolicy.valid("x".repeat(160), "x".repeat(600), "x".repeat(240)))
    }
}
