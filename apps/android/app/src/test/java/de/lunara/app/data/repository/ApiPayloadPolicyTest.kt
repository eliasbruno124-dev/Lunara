package de.lunara.app.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import de.lunara.app.data.remote.dto.AcceptInvitationRequest
import de.lunara.app.data.remote.dto.CreateInvitationRequest
import de.lunara.app.data.remote.dto.InvitationDto
import de.lunara.app.data.remote.dto.OidcLoginRequest
import de.lunara.app.data.remote.dto.RegisterRequest
import de.lunara.app.data.remote.dto.SetupRequest
import de.lunara.app.data.remote.dto.SyncMutationDto
import de.lunara.app.data.remote.dto.SyncRequest
import de.lunara.app.data.remote.dto.UpdateSharingRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiPayloadPolicyTest {
    @Test
    fun `invitation acceptance payload requires explicit confirmation`() {
        val payload = Gson().toJson(AcceptInvitationRequest("ABCDEFGH2345"))

        assertEquals("{\"invitationCode\":\"ABCDEFGH2345\",\"confirm\":true}", payload)
    }

    @Test
    fun `requests a non-expiring one-time invitation explicitly`() {
        val payload = Gson().toJson(CreateInvitationRequest())

        assertTrue(payload.contains("\"neverExpires\":true"))
        assertFalse(payload.contains("expiresInMinutes"))
    }

    @Test
    fun `registration payload has no household or bootstrap field`() {
        val payload = Gson().toJson(
            RegisterRequest("tracker", "Tracker", "a-secure-password", "DE", "Phone"),
        )

        assertFalse(payload.contains("household", ignoreCase = true))
        assertFalse(payload.contains("bootstrap", ignoreCase = true))
    }

    @Test
    fun `oidc invitation carries only external identity and relationship proof`() {
        val payload = Gson().toJson(
            OidcLoginRequest(
                accessToken = "external-access-token",
                deviceName = "Phone",
                invitationCode = "ABCDEFGH2345",
                preferredLanguage = "DE",
            ),
        )

        assertTrue(payload.contains("\"invitationCode\":\"ABCDEFGH2345\""))
        assertTrue(payload.contains("\"preferredLanguage\":\"DE\""))
        assertFalse(payload.contains("createIfMissing"))
        assertFalse(payload.contains("username", ignoreCase = true))
        assertFalse(payload.contains("password", ignoreCase = true))
        assertFalse(payload.contains("displayName", ignoreCase = true))
    }

    @Test
    fun `legacy oidc login omits unsupported enrollment fields`() {
        val payload = Gson().toJson(
            OidcLoginRequest("external-access-token", "Phone"),
        )

        assertFalse(payload.contains("invitationCode"))
        assertFalse(payload.contains("preferredLanguage"))
        assertFalse(payload.contains("createIfMissing"))
    }

    @Test
    fun `primary authentik login explicitly opts into tracker provisioning`() {
        val payload = Gson().toJson(
            OidcLoginRequest(
                accessToken = "external-access-token",
                deviceName = "Phone",
                preferredLanguage = "DE",
                createIfMissing = true,
            ),
        )

        assertTrue(payload.contains("\"createIfMissing\":true"))
        assertFalse(payload.contains("invitationCode"))
    }

    @Test
    fun `legacy setup payload uses a fixed neutral internal name`() {
        val payload = Gson().toJson(
            SetupRequest("b".repeat(32), "tracker", "Tracker", "a-secure-password", "DE", "Phone"),
        )

        assertTrue(payload.contains("\"householdName\":\"Lunara\""))
    }

    @Test
    fun `invitation accepts a missing expiry`() {
        val invitation = InvitationDto("id", "ABCDEFGH2345", "PARTNER", expiresAt = null)

        assertNull(invitation.expiresAt)
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

    @Test
    fun `daily log full record payload preserves explicit nulls`() {
        val payload = DailyLogPayloadPolicy.serialize(
            Gson(),
            linkedMapOf(
                "id" to "log-id",
                "localDate" to "2026-08-13",
                "flowLevel" to null,
                "mood" to null,
                "customSymptoms" to emptyList<String>(),
                "version" to 2L,
            ),
        )

        assertTrue(payload.contains("\"flowLevel\":null"))
        assertTrue(payload.contains("\"mood\":null"))
        assertTrue(payload.contains("\"customSymptoms\":[]"))
        assertTrue(payload.contains("\"version\":2"))
    }

    @Test
    fun `daily log explicit null survives the actual sync wire model`() {
        val storedPayload = DailyLogPayloadPolicy.serialize(
            Gson(),
            linkedMapOf("id" to "log-id", "flowLevel" to null, "version" to 2L),
        )
        val request = SyncRequest(
            cursor = "5",
            mutations = listOf(
                SyncMutationDto(
                    clientMutationId = "mutation-id",
                    entityType = "DAILY_LOG",
                    operation = "UPSERT",
                    payload = JsonParser.parseString(storedPayload).asJsonObject,
                ),
            ),
        )

        val wireJson = Gson().toJson(request)

        assertTrue(wireJson.contains("\"flowLevel\":null"))
    }

    @Test
    fun `default converter still omits nullable sharing fields`() {
        val payload = Gson().toJson(UpdateSharingRequest(sharePrediction = false))

        assertEquals("{\"sharePrediction\":false}", payload)
    }
}
