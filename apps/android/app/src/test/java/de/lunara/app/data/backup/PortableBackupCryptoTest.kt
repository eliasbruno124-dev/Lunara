package de.lunara.app.data.backup

import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PortableBackupCryptoTest {
    @Test
    fun passphraseLengthCountsUnicodeCodePoints() {
        assertFalse(PortableBackupPassphrasePolicy.isValid("🔒".repeat(11)))
        assertTrue(PortableBackupPassphrasePolicy.isValid("🔒".repeat(12)))
        assertTrue(PortableBackupPassphrasePolicy.isValid("🔒".repeat(128)))
        assertFalse(PortableBackupPassphrasePolicy.isValid("🔒".repeat(129)))
    }

    @Test
    fun decryptsWebCryptoCompatibleUnicodeVector() {
        val webEnvelope = """{"format":"lunara-portable-backup","version":1,"kdf":{"name":"PBKDF2-HMAC-SHA256","iterations":310000,"salt":"AAECAwQFBgcICQoLDA0ODw=="},"cipher":{"name":"AES-256-GCM","iv":"EBESExQVFhcYGRob"},"ciphertext":"xPAznaEyiz2TBpZXbvL9pQKKfE2guAEPYukqQgzWbxfm9H3C7lMC4+L7LO/MYK91GQZ6DPBj"}"""

        assertEquals(
            "{\"schemaVersion\":1,\"product\":\"Lunara\"}",
            PortableBackupCrypto.decrypt(
                webEnvelope.encodeToByteArray(),
                "Lunara 🔒 Backup sicher".toCharArray(),
            ).decodeToString(),
        )
    }

    @Test
    fun roundTripUsesPortablePassphraseAndRandomEnvelope() {
        val plaintext = "private cycle data".encodeToByteArray()
        val first = PortableBackupCrypto.encrypt(plaintext, "correct horse battery".toCharArray())
        val second = PortableBackupCrypto.encrypt(plaintext, "correct horse battery".toCharArray())

        assertFalse(first.contentEquals(second))
        val envelope = com.google.gson.JsonParser.parseString(first.decodeToString()).asJsonObject
        assertEquals("lunara-portable-backup", envelope["format"].asString)
        assertEquals(1, envelope["version"].asInt)
        assertEquals("PBKDF2-HMAC-SHA256", envelope["kdf"].asJsonObject["name"].asString)
        assertEquals(310_000, envelope["kdf"].asJsonObject["iterations"].asInt)
        assertEquals(16, java.util.Base64.getDecoder().decode(envelope["kdf"].asJsonObject["salt"].asString).size)
        assertEquals(12, java.util.Base64.getDecoder().decode(envelope["cipher"].asJsonObject["iv"].asString).size)
        assertArrayEquals(
            plaintext,
            PortableBackupCrypto.decrypt(first, "correct horse battery".toCharArray()),
        )
    }

    @Test
    fun wrongPassphraseFailsClosed() {
        val encrypted = PortableBackupCrypto.encrypt(
            "health data".encodeToByteArray(),
            "correct horse battery".toCharArray(),
        )

        try {
            PortableBackupCrypto.decrypt(encrypted, "incorrect passphrase".toCharArray())
            fail("Wrong passphrase must not return plaintext")
        } catch (_: IncorrectBackupPassphraseException) {
            // Expected authenticated-decryption failure.
        }
    }

    @Test
    fun tamperingWithCiphertextFailsClosed() {
        val encrypted = PortableBackupCrypto.encrypt(
            "health data".encodeToByteArray(),
            "correct horse battery".toCharArray(),
        )
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()

        try {
            PortableBackupCrypto.decrypt(encrypted, "correct horse battery".toCharArray())
            fail("Tampered backup must not return plaintext")
        } catch (failure: Exception) {
            assertTrue(
                failure is IncorrectBackupPassphraseException || failure is InvalidPortableBackupException,
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun weakExportPassphraseIsRejected() {
        PortableBackupCrypto.encrypt(byteArrayOf(1), "too-short".toCharArray())
    }

    @Test
    fun validatorAcceptsOneWellFormedLocalBackup() {
        PortableBackupValidator.validate(
            PortableBackupPayload(
                schemaVersion = 1,
                product = "Lunara",
                exportedAt = "2026-08-25T12:00:00Z",
                settings = PortableLocalSettings(displayName = "Dunja"),
                periods = listOf(
                    PortablePeriod(
                        id = "00000000-0000-4000-8000-000000000001",
                        startDate = "2026-08-01",
                        endDate = "2026-08-05",
                        createdAt = "2026-08-01T12:00:00Z",
                        updatedAt = "2026-08-05T12:00:00Z",
                    ),
                ),
                dailyLogs = listOf(
                    PortableDailyLog(
                        id = "00000000-0000-4000-8000-000000000002",
                        localDate = "2026-08-02",
                        painLevel = 4,
                        symptomsJson = "[\"Krämpfe\"]",
                        createdAt = "2026-08-02T12:00:00Z",
                        updatedAt = "2026-08-02T12:00:00Z",
                    ),
                ),
            ),
            today = LocalDate.parse("2026-08-25"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun validatorRejectsDuplicateDailyDates() {
        val log = PortableDailyLog(
            id = "00000000-0000-4000-8000-000000000002",
            localDate = "2026-08-02",
            createdAt = "2026-08-02T12:00:00Z",
            updatedAt = "2026-08-02T12:00:00Z",
        )
        PortableBackupValidator.validate(
            PortableBackupPayload(
                schemaVersion = 1,
                product = "Lunara",
                exportedAt = "2026-08-25T12:00:00Z",
                settings = PortableLocalSettings(displayName = "Dunja"),
                dailyLogs = listOf(log, log.copy(id = "00000000-0000-4000-8000-000000000003")),
            ),
            today = LocalDate.parse("2026-08-25"),
        )
    }
}
