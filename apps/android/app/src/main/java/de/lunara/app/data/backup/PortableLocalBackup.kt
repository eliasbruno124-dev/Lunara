package de.lunara.app.data.backup

import de.lunara.app.core.model.PeriodEndPolicy

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lunara.app.data.local.DailyLogEntity
import de.lunara.app.data.local.LunaraDatabase
import de.lunara.app.data.local.PortableImportJournalEntity
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.preferences.LocalSettingsSnapshot
import de.lunara.app.notifications.NotificationPreferences
import de.lunara.app.data.sync.ProcessSyncGate
import de.lunara.app.notifications.NotificationDataCache
import de.lunara.app.notifications.NotificationDeliveryStore
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object PortableBackupPassphrasePolicy {
    const val MIN_CODE_POINTS = 12
    const val MAX_CODE_POINTS = 128
    const val MAX_UTF16_CODE_UNITS = MAX_CODE_POINTS * 2

    fun isValid(passphrase: CharSequence): Boolean {
        var index = 0
        var count = 0
        while (index < passphrase.length) {
            val current = passphrase[index]
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= passphrase.length || !Character.isLowSurrogate(passphrase[index + 1])) return false
                index += 2
            } else {
                if (Character.isLowSurrogate(current)) return false
                index += 1
            }
            count += 1
            if (count > MAX_CODE_POINTS) return false
        }
        return count >= MIN_CODE_POINTS
    }

    fun isValid(passphrase: CharArray): Boolean {
        var index = 0
        var count = 0
        while (index < passphrase.size) {
            val current = passphrase[index]
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= passphrase.size || !Character.isLowSurrogate(passphrase[index + 1])) return false
                index += 2
            } else {
                if (Character.isLowSurrogate(current)) return false
                index += 1
            }
            count += 1
            if (count > MAX_CODE_POINTS) return false
        }
        return count >= MIN_CODE_POINTS
    }
}

class InvalidPortableBackupException(cause: Throwable? = null) : Exception("Invalid Lunara backup", cause)
class IncorrectBackupPassphraseException : Exception("The backup passphrase is incorrect")

/** Portable JSON envelope shared by Android and the Lunara PWA. */
object PortableBackupCrypto {
    private const val FORMAT = "lunara-portable-backup"
    private const val FORMAT_VERSION = 1
    private const val KDF_NAME = "PBKDF2-HMAC-SHA256"
    private const val CIPHER_NAME = "AES-256-GCM"
    private const val ITERATIONS = 310_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    private val aad = "$FORMAT:v$FORMAT_VERSION".toByteArray(StandardCharsets.UTF_8)
    private val envelopeGson = Gson()
    private val base64Encoder = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()

    fun encrypt(plaintext: ByteArray, passphrase: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(PortableBackupPassphrasePolicy.isValid(passphrase))
        require(plaintext.size <= MAX_PLAINTEXT_BYTES)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt, ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(plaintext)
            try {
                envelopeGson.toJson(
                    CipherEnvelope(
                        format = FORMAT,
                        version = FORMAT_VERSION,
                        kdf = KdfEnvelope(
                            name = KDF_NAME,
                            iterations = ITERATIONS,
                            salt = base64Encoder.encodeToString(salt),
                        ),
                        cipher = CipherDetails(
                            name = CIPHER_NAME,
                            iv = base64Encoder.encodeToString(nonce),
                        ),
                        ciphertext = base64Encoder.encodeToString(ciphertext),
                    ),
                ).toByteArray(StandardCharsets.UTF_8).also {
                    require(it.size <= MAX_FILE_BYTES)
                }
            } finally {
                ciphertext.fill(0)
            }
        } finally {
            key.encoded.fill(0)
            salt.fill(0)
            nonce.fill(0)
            passphrase.fill('\u0000')
        }
    }

    fun decrypt(file: ByteArray, passphrase: CharArray): ByteArray {
        require(PortableBackupPassphrasePolicy.isValid(passphrase))
        if (file.size !in 1..MAX_FILE_BYTES) throw InvalidPortableBackupException()
        var derivedKey: SecretKeySpec? = null
        return try {
            val envelope = envelopeGson.fromJson(
                String(file, StandardCharsets.UTF_8),
                CipherEnvelope::class.java,
            ) ?: throw InvalidPortableBackupException()
            if (
                envelope.format != FORMAT || envelope.version != FORMAT_VERSION ||
                envelope.kdf.name != KDF_NAME || envelope.kdf.iterations != ITERATIONS ||
                envelope.cipher.name != CIPHER_NAME
            ) {
                throw InvalidPortableBackupException()
            }
            val salt = base64Decoder.decode(envelope.kdf.salt)
            val nonce = base64Decoder.decode(envelope.cipher.iv)
            val ciphertext = base64Decoder.decode(envelope.ciphertext)
            if (salt.size != SALT_BYTES || nonce.size != NONCE_BYTES || ciphertext.size < GCM_TAG_BITS / 8) {
                throw InvalidPortableBackupException()
            }
            derivedKey = deriveKey(passphrase, salt, ITERATIONS)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, derivedKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(aad)
            try {
                cipher.doFinal(ciphertext)
            } catch (_: AEADBadTagException) {
                throw IncorrectBackupPassphraseException()
            } finally {
                salt.fill(0)
                nonce.fill(0)
                ciphertext.fill(0)
            }
        } catch (known: IncorrectBackupPassphraseException) {
            throw known
        } catch (known: InvalidPortableBackupException) {
            throw known
        } catch (failure: Throwable) {
            throw InvalidPortableBackupException(failure)
        } finally {
            derivedKey?.encoded?.fill(0)
            passphrase.fill('\u0000')
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private const val MAX_PLAINTEXT_BYTES = 12 * 1024 * 1024

    private data class CipherEnvelope(
        val format: String = "",
        val version: Int = 0,
        val kdf: KdfEnvelope = KdfEnvelope(),
        val cipher: CipherDetails = CipherDetails(),
        val ciphertext: String = "",
    )

    private data class KdfEnvelope(
        val name: String = "",
        val iterations: Int = 0,
        val salt: String = "",
    )

    private data class CipherDetails(
        val name: String = "",
        val iv: String = "",
    )
}

internal data class PortableBackupPayload(
    val schemaVersion: Int = 0,
    val product: String = "",
    val exportedAt: String = "",
    val profile: PortableLocalProfile = PortableLocalProfile(),
    val settings: PortableLocalSettings = PortableLocalSettings(),
    val periods: List<PortablePeriod> = emptyList(),
    val dailyLogs: List<PortableDailyLog> = emptyList(),
)

internal data class PortableLocalProfile(
    val fertilityPredictionEnabled: Boolean = false,
    val typicalCycleLength: Int? = null,
    val typicalPeriodLength: Int? = null,
    val lutealLength: Int? = null,
    val predictionPaused: Boolean = false,
    val specialMode: String = "NONE",
)

internal data class PortableLocalSettings(
    val displayName: String = "Lunara",
    val privacyMode: Boolean = true,
    val useSystemTheme: Boolean = true,
    val darkMode: Boolean = false,
    val notifications: NotificationPreferences = NotificationPreferences(),
)

internal data class PortablePeriod(
    val id: String = "",
    val startDate: String = "",
    val endDate: String? = null,
    val source: String = "MANUAL",
    val createdAt: String = "",
    val updatedAt: String = "",
    val version: Long = 0,
)

internal data class PortableDailyLog(
    val id: String = "",
    val localDate: String = "",
    val flowLevel: String? = null,
    val painLevel: Int? = null,
    val mood: String? = null,
    val energy: Int? = null,
    val sleepQuality: Int? = null,
    val bodyTemperature: Double? = null,
    val symptomsJson: String = "[]",
    val encryptedNote: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val version: Long = 0,
)

internal object PortableBackupValidator {
    private const val SCHEMA_VERSION = 1
    private const val MAX_PERIODS = 5_000
    private const val MAX_DAILY_LOGS = 50_000
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT)
    private val isoPattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun validate(payload: PortableBackupPayload, today: LocalDate) {
        require(payload.schemaVersion == SCHEMA_VERSION)
        require(payload.product == "Lunara")
        Instant.parse(payload.exportedAt)
        require(payload.periods.size <= MAX_PERIODS && payload.dailyLogs.size <= MAX_DAILY_LOGS)
        require(payload.periods.map(PortablePeriod::id).distinct().size == payload.periods.size)
        require(payload.dailyLogs.map(PortableDailyLog::id).distinct().size == payload.dailyLogs.size)
        require(payload.dailyLogs.map(PortableDailyLog::localDate).distinct().size == payload.dailyLogs.size)

        val periodRanges = payload.periods.map { period ->
            UUID.fromString(period.id)
            require(period.source.length <= 32)
            require(period.version >= 0)
            Instant.parse(period.createdAt)
            Instant.parse(period.updatedAt)
            val start = strictDate(period.startDate)
            val end = period.endDate?.let(::strictDate)
            require(!start.isAfter(today) && end?.isAfter(today) != true)
            require(end?.isBefore(start) != true)
            Triple(start, PeriodEndPolicy.overlapEnd(start, end), period.id)
        }.sortedBy { it.first }
        periodRanges.zipWithNext().forEach { (left, right) ->
            require(left.second.isBefore(right.first)) { "Overlapping period ranges" }
        }

        payload.dailyLogs.forEach { log ->
            UUID.fromString(log.id)
            require(log.version >= 0)
            Instant.parse(log.createdAt)
            Instant.parse(log.updatedAt)
            require(!strictDate(log.localDate).isAfter(today))
            require(log.flowLevel == null || log.flowLevel.length <= 32)
            require(log.painLevel == null || log.painLevel in 0..10)
            require(log.mood == null || log.mood.length <= 64)
            require(log.energy == null || log.energy in 0..10)
            require(log.sleepQuality == null || log.sleepQuality in 0..10)
            require(log.bodyTemperature == null || log.bodyTemperature.isFinite() && log.bodyTemperature in 25.0..45.0)
            require(log.encryptedNote == null || log.encryptedNote.length <= 20_000)
            require(log.symptomsJson.length <= 20_000)
            val symptoms = JsonParser.parseString(log.symptomsJson)
            require(symptoms.isJsonArray)
            require(symptoms.asJsonArray.size() <= 30)
            require(symptoms.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.length <= 80 })
        }

        validateSettings(payload.settings, payload.profile)
    }

    private fun validateSettings(settings: PortableLocalSettings, profile: PortableLocalProfile) {
        require(settings.displayName.trim().length in 1..120)
        require(profile.typicalCycleLength == null || profile.typicalCycleLength in 15..90)
        require(profile.typicalPeriodLength == null || profile.typicalPeriodLength in 1..14)
        require(profile.lutealLength == null || profile.lutealLength in 8..20)
        require(profile.specialMode in setOf(
            "NONE", "HORMONAL_CONTRACEPTION", "PREGNANCY", "POSTPARTUM",
            "MENOPAUSE_TRANSITION", "PAUSED",
        ))
        with(settings.notifications) {
            require(reminderHour in 0..23 && reminderMinute in 0..59)
            require(quietStartHour in 0..23 && quietStartMinute in 0..59)
            require(quietEndHour in 0..23 && quietEndMinute in 0..59)
            require(maxPerDay in 1..3)
        }
    }

    private fun strictDate(raw: String): LocalDate {
        require(isoPattern.matches(raw))
        return LocalDate.parse(raw, isoFormatter)
    }
}

@Singleton
class PortableLocalBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LunaraDatabase,
    private val preferences: AppPreferences,
    private val gson: Gson,
    private val notificationDataCache: NotificationDataCache,
    private val notificationDeliveryStore: NotificationDeliveryStore,
) {
    private var clock: Clock = Clock.systemUTC()

    suspend fun exportTo(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        try {
            require(PortableBackupPassphrasePolicy.isValid(passphrase))
            resumePendingImport()
            val state = preferences.state.first()
            val session = state.session
            require(state.appMode == AppMode.LOCAL && session != null)
            val payload = ProcessSyncGate.serialized {
                val current = preferences.state.first()
                require(current.appMode == AppMode.LOCAL && current.session?.userId == session.userId)
                PortableBackupPayload(
                    schemaVersion = 1,
                    product = "Lunara",
                    exportedAt = clock.instant().toString(),
                    profile = current.toPortableProfile(),
                    settings = current.toPortableSettings(),
                    periods = database.periodDao().activeForUser(session.userId).map { it.toPortable() },
                    dailyLogs = database.dailyLogDao().activeForUser(session.userId).map { it.toPortable() },
                )
            }
            val plaintext = gson.toJson(payload).toByteArray(StandardCharsets.UTF_8)
            val encrypted = try {
                PortableBackupCrypto.encrypt(plaintext, passphrase)
            } finally {
                plaintext.fill(0)
            }
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output -> output.write(encrypted) }
                    ?: throw InvalidPortableBackupException()
            } finally {
                encrypted.fill(0)
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    suspend fun importFrom(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        try {
            require(PortableBackupPassphrasePolicy.isValid(passphrase))
            val encrypted = context.contentResolver.openInputStream(uri)?.use(::readBounded)
                ?: throw InvalidPortableBackupException()
            val plaintext = try {
                PortableBackupCrypto.decrypt(encrypted, passphrase)
            } finally {
                encrypted.fill(0)
            }
            val payload = try {
                ByteArrayInputStream(plaintext).use { input ->
                    InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                        gson.fromJson(reader, PortableBackupPayload::class.java)
                            ?: throw InvalidPortableBackupException()
                    }
                }
            } catch (failure: Throwable) {
                if (failure is InvalidPortableBackupException) throw failure
                throw InvalidPortableBackupException(failure)
            } finally {
                plaintext.fill(0)
            }
            try {
                PortableBackupValidator.validate(payload, LocalDate.now())
            } catch (failure: Throwable) {
                throw InvalidPortableBackupException(failure)
            }

            ProcessSyncGate.serialized {
                val current = preferences.state.first()
                val session = current.session
                require(current.appMode == AppMode.LOCAL && session != null)
                val settingsSnapshot = payload.toLocalSettingsSnapshot()
                database.withTransaction {
                    database.periodDao().clear()
                    database.dailyLogDao().clear()
                    database.mutationDao().clear()
                    database.syncMetadataDao().clear()
                    database.partnerDashboardDao().clear()
                    database.portableImportJournalDao().clear()
                    database.periodDao().upsertAll(payload.periods.map { it.toEntity(session.userId) })
                    database.dailyLogDao().upsertAll(payload.dailyLogs.map { it.toEntity(session.userId) })
                    database.portableImportJournalDao().save(
                        PortableImportJournalEntity(
                            userId = session.userId,
                            settingsJson = gson.toJson(settingsSnapshot),
                            createdAt = clock.instant().toString(),
                        ),
                    )
                }
                finishPendingImport()
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /** Idempotently completes an import interrupted after the Room commit. */
    suspend fun resumePendingImport() = withContext(Dispatchers.IO) {
        ProcessSyncGate.serialized { finishPendingImport() }
    }

    private suspend fun finishPendingImport() {
        val journal = database.portableImportJournalDao().pending() ?: return
        val current = preferences.state.first()
        check(
            current.appMode == AppMode.LOCAL &&
                current.session?.userId == journal.userId &&
                current.session.role == de.lunara.app.core.model.UserRole.TRACKER,
        ) { "Local session changed while completing a backup import" }
        val snapshot = runCatching {
            gson.fromJson(journal.settingsJson, LocalSettingsSnapshot::class.java)
        }.getOrNull() ?: throw InvalidPortableBackupException()
        validateSettingsSnapshot(snapshot)
        preferences.restoreLocalSettings(journal.userId, snapshot)
        notificationDataCache.clear()
        notificationDeliveryStore.clear()
        database.portableImportJournalDao().complete(journal.id)
    }

    private fun validateSettingsSnapshot(snapshot: LocalSettingsSnapshot) {
        require(snapshot.displayName.length <= 120)
        require(snapshot.localTypicalCycleLength == null || snapshot.localTypicalCycleLength in 15..90)
        require(snapshot.localTypicalPeriodLength == null || snapshot.localTypicalPeriodLength in 1..14)
        require(snapshot.localLutealLength == null || snapshot.localLutealLength in 8..20)
        require(
            snapshot.localSpecialMode in setOf(
                "NONE", "HORMONAL_CONTRACEPTION", "PREGNANCY", "POSTPARTUM",
                "MENOPAUSE_TRANSITION", "PAUSED",
            ),
        )
        with(snapshot.notifications) {
            require(reminderHour in 0..23 && reminderMinute in 0..59)
            require(quietStartHour in 0..23 && quietStartMinute in 0..59)
            require(quietEndHour in 0..23 && quietEndMinute in 0..59)
            require(maxPerDay in 1..3)
        }
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        return try {
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > PortableBackupCrypto.MAX_FILE_BYTES) throw InvalidPortableBackupException()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            buffer.fill(0)
        }
    }

    private fun PeriodEntity.toPortable() = PortablePeriod(
        id, startDate, endDate, source, createdAt, updatedAt, version,
    )

    private fun DailyLogEntity.toPortable() = PortableDailyLog(
        id, localDate, flowLevel, painLevel, mood, energy, sleepQuality, bodyTemperature,
        symptomsJson, encryptedNote, createdAt, updatedAt, version,
    )

    private fun PortablePeriod.toEntity(userId: String) = PeriodEntity(
        id, userId, startDate, endDate, source, createdAt, updatedAt,
        deletedAt = null, version = version, syncState = "LOCAL",
    )

    private fun PortableDailyLog.toEntity(userId: String) = DailyLogEntity(
        id, userId, localDate, flowLevel, painLevel, mood, energy, sleepQuality, bodyTemperature,
        symptomsJson, encryptedNote, createdAt, updatedAt, deletedAt = null, version = version,
        syncState = "LOCAL",
    )

    private fun de.lunara.app.data.preferences.AppPreferencesState.toPortableProfile() = PortableLocalProfile(
        fertilityPredictionEnabled = localFertilityPredictionEnabled,
        typicalCycleLength = localTypicalCycleLength,
        typicalPeriodLength = localTypicalPeriodLength,
        lutealLength = localLutealLength,
        predictionPaused = localPredictionPaused,
        specialMode = localSpecialMode,
    )

    private fun de.lunara.app.data.preferences.AppPreferencesState.toPortableSettings() = PortableLocalSettings(
        displayName = session?.displayName.orEmpty().ifBlank { "Lunara" },
        privacyMode = privacyMode,
        useSystemTheme = useSystemTheme,
        darkMode = darkMode,
        notifications = notifications,
    )

    private fun PortableBackupPayload.toLocalSettingsSnapshot() = LocalSettingsSnapshot(
        displayName = settings.displayName,
        privacyMode = settings.privacyMode,
        useSystemTheme = settings.useSystemTheme,
        darkMode = settings.darkMode,
        notifications = settings.notifications,
        localFertilityPredictionEnabled = profile.fertilityPredictionEnabled,
        localTypicalCycleLength = profile.typicalCycleLength,
        localTypicalPeriodLength = profile.typicalPeriodLength,
        localLutealLength = profile.lutealLength,
        localPredictionPaused = profile.predictionPaused,
        localSpecialMode = profile.specialMode,
    )
}
