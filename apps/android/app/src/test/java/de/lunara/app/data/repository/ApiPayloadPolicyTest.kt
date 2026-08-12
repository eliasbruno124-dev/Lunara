package de.lunara.app.data.repository

import com.google.gson.Gson
import de.lunara.app.data.remote.dto.CreateInvitationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ApiPayloadPolicyTest {
    @Test
    fun `uses server invitation lifetime policy`() {
        val payload = Gson().toJson(CreateInvitationRequest())

        assertFalse(payload.contains("expiresInMinutes"))
    }

    @Test
    fun `daily log text matches server limits`() {
        assertNull(ApiPayloadPolicy.sanitizeMood("   "))
        assertEquals(64, ApiPayloadPolicy.sanitizeMood("m".repeat(100))?.length)

        val symptoms = ApiPayloadPolicy.sanitizeSymptoms(
            listOf("  cramps  ", "x".repeat(100), "cramps") + List(35) { "symptom-$it" },
        )
        assertEquals(30, symptoms.size)
        assertEquals("cramps", symptoms.first())
        assertEquals(80, symptoms[1].length)
    }
}
