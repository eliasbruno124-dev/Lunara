package de.lunara.app.data.repository

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.remote.ApiClientFactory
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import de.lunara.app.data.local.LunaraDatabase
import de.lunara.app.data.local.PartnerDashboardEntity
import com.google.gson.Gson
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import de.lunara.app.data.sync.ProcessSyncGate
import de.lunara.app.data.remote.dto.CycleProfileDto
import de.lunara.app.data.remote.dto.UpdateCycleProfileRequest
import de.lunara.app.notifications.NotificationDataCache
import de.lunara.app.data.prediction.LocalPredictionEngine
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DashboardRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val connectionRepository: ConnectionRepository,
    private val database: LunaraDatabase,
    private val gson: Gson,
    private val notificationDataCache: NotificationDataCache,
    private val credentialGate: SessionCredentialGate,
) {
    private val partnerRefreshMutex = Mutex()

    suspend fun partnerDashboard(expectedUserId: String, tipVariant: Int = 0): PartnerDashboardDto = partnerRefreshMutex.withLock {
        val generation = credentialGate.snapshot()
        val initial = preferences.state.first()
        val base = initial.baseUrl ?: throw ApiException(400)
        if (!PartnerRefreshPolicy.canCommit(expectedUserId, base, initial)) throw SessionChangedException()
        var requestToken = secrets.accessToken
        val dashboard = try {
            var response = apiFactory.create(base, secrets.gatewayCredentials, requestToken)
                .partnerDashboard(endpoint(base, "api/v1/partner/dashboard"))
            if (response.code() == 401 && connectionRepository.refresh()) {
                requestToken = secrets.accessToken
                response = apiFactory.create(base, secrets.gatewayCredentials, requestToken)
                    .partnerDashboard(endpoint(base, "api/v1/partner/dashboard"))
            }
            PartnerRefreshPolicy.permitted(response.requireBody())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // A response captured before a successful recovery must not flag the renewed session.
            if (credentialGate.snapshot() == generation && secrets.accessToken == requestToken) {
                connectionRepository.recordAuthenticationFailure(failure, base, expectedUserId, requestToken)
            }
            throw failure
        }
        val updatedAt = Instant.now().toString()
        suspend fun saveCurrent(payload: PartnerDashboardDto) = ProcessSyncGate.serialized {
            val committed = credentialGate.commitIfCurrent(generation) {
                val current = preferences.state.first()
                if (!PartnerRefreshPolicy.canCommit(expectedUserId, base, current)) return@commitIfCurrent false
                database.partnerDashboardDao().save(
                    PartnerDashboardEntity(payloadJson = gson.toJson(payload), updatedAt = updatedAt),
                )
                true
            }
            if (!committed) throw SessionChangedException()
        }
        // Commit permitted health data immediately, even if the optional tip service is unavailable.
        saveCurrent(dashboard)
        val completeDashboard = PartnerRefreshPolicy.withOptionalTip(dashboard) {
            var tipResponse = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .todayTip(endpoint(base, "api/v1/tips/today?variant=${tipVariant.coerceIn(0, 100)}"))
            if (tipResponse.code() == 401 && connectionRepository.refresh()) {
                tipResponse = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                    .todayTip(endpoint(base, "api/v1/tips/today?variant=${tipVariant.coerceIn(0, 100)}"))
            }
            tipResponse.requireBody().tip
        }
        saveCurrent(completeDashboard)
        completeDashboard
    }

    suspend fun cachedPartnerDashboard(): PartnerDashboardDto? = database.partnerDashboardDao().current()?.let {
        runCatching { gson.fromJson(it.payloadJson, PartnerDashboardDto::class.java) }.getOrNull()
    }

    fun observeCachedPartnerDashboard(): Flow<PartnerDashboardDto?> =
        database.partnerDashboardDao().observeCurrent().map { cached ->
            cached?.let { runCatching { gson.fromJson(it.payloadJson, PartnerDashboardDto::class.java) }.getOrNull() }
        }

    fun observePartnerDashboardSnapshot(): Flow<PartnerDashboardSnapshot?> =
        database.partnerDashboardDao().observeCurrent().map { cached ->
            cached?.let {
                runCatching {
                    PartnerDashboardSnapshot(
                        PartnerRefreshPolicy.permitted(gson.fromJson(it.payloadJson, PartnerDashboardDto::class.java)),
                        it.updatedAt,
                    )
                }.getOrNull()
            }
        }

    /** Bumped by every cycle-settings change; older prediction requests are then outdated. */
    private val cycleSettingsGeneration = java.util.concurrent.atomic.AtomicLong(0)

    /** True if a prediction requested at [generation] may still be shown and cached. */
    fun isCurrentPredictionGeneration(generation: Long): Boolean = cycleSettingsGeneration.get() == generation

    fun predictionGeneration(): Long = cycleSettingsGeneration.get()

    suspend fun trackerPrediction(expectedUserId: String): de.lunara.app.data.remote.dto.PredictionDto =
        // If the cycle settings change while a request runs, ask again. An estimate from before
        // the change is never returned or cached; after repeated changes this throws
        // PredictionOutdatedException, the screen keeps its value and reminders retry later.
        fetchWithStableGeneration(cycleSettingsGeneration::get) { generation ->
            trackerPredictionOnce(expectedUserId, generation)
        }

    private suspend fun trackerPredictionOnce(
        expectedUserId: String,
        generation: Long,
    ): de.lunara.app.data.remote.dto.PredictionDto {
        val preferenceState = preferences.state.first()
        if (preferenceState.appMode == AppMode.LOCAL) {
            if (preferenceState.session?.userId != expectedUserId) throw SessionChangedException()
            val prediction = LocalPredictionEngine.calculate(
                periods = database.periodDao().activeForUser(expectedUserId),
                preferences = preferenceState,
                referenceDate = LocalDate.now(ZoneId.systemDefault()),
            )
            ProcessSyncGate.serialized {
                val current = preferences.state.first()
                if (current.session?.userId != expectedUserId || current.appMode != AppMode.LOCAL) {
                    throw SessionChangedException()
                }
                if (generation == cycleSettingsGeneration.get()) notificationDataCache.saveTrackerPrediction(expectedUserId, prediction)
            }
            return prediction
        }
        val base = preferenceState.baseUrl ?: throw ApiException(400)
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .prediction(endpoint(base, "api/v1/predictions"))
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .prediction(endpoint(base, "api/v1/predictions"))
        }
        val prediction = response.requireBody()
        ProcessSyncGate.serialized {
            if (preferences.state.first().session?.userId != expectedUserId) throw SessionChangedException()
            // A request that started before a settings change must not cache a stale estimate.
            if (generation == cycleSettingsGeneration.get()) notificationDataCache.saveTrackerPrediction(expectedUserId, prediction)
        }
        return prediction
    }

    suspend fun cycleProfile(): CycleProfileDto {
        val state = preferences.state.first()
        if (state.appMode == AppMode.LOCAL) {
            val session = state.session ?: throw SessionChangedException()
            return CycleProfileDto(
                id = "local-profile",
                userId = session.userId,
                typicalCycleLengthOptional = state.localTypicalCycleLength,
                typicalPeriodLengthOptional = state.localTypicalPeriodLength,
                lutealLengthOptional = state.localLutealLength,
                fertilityPredictionEnabled = state.localFertilityPredictionEnabled,
                predictionPaused = state.localPredictionPaused,
                specialMode = state.localSpecialMode,
                timezone = ZoneId.systemDefault().id,
            )
        }
        val base = state.baseUrl ?: throw ApiException(400)
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .cycleProfile(endpoint(base, "api/v1/cycle/profile"))
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .cycleProfile(endpoint(base, "api/v1/cycle/profile"))
        }
        return response.requireBody()
    }

    suspend fun setFertilityPredictionEnabled(enabled: Boolean): CycleProfileDto {
        val state = preferences.state.first()
        if (state.appMode == AppMode.LOCAL) {
            val session = state.session ?: throw SessionChangedException()
            if (session.role != de.lunara.app.core.model.UserRole.TRACKER) throw ApiException(403)
            preferences.setLocalFertilityPredictionEnabled(session.userId, enabled)
            return cycleProfile()
        }
        val base = state.baseUrl ?: throw ApiException(400)
        val request = UpdateCycleProfileRequest(fertilityPredictionEnabled = enabled)
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .updateCycleProfile(endpoint(base, "api/v1/cycle/profile"), request)
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .updateCycleProfile(endpoint(base, "api/v1/cycle/profile"), request)
        }
        return response.requireBody()
    }

    suspend fun updateCycleSettings(settings: CycleSettings): CycleProfileDto {
        cycleSettingsGeneration.incrementAndGet()
        val state = preferences.state.first()
        if (state.appMode == AppMode.LOCAL) {
            val session = state.session ?: throw SessionChangedException()
            if (session.role != de.lunara.app.core.model.UserRole.TRACKER) throw ApiException(403)
            preferences.setLocalCycleSettings(
                session.userId,
                settings.specialMode,
                settings.typicalCycleLength,
                settings.typicalPeriodLength,
                settings.predictionPaused,
            )
            cycleSettingsGeneration.incrementAndGet()
            if (settings.switchesPredictionsOff()) notificationDataCache.clear()
            return cycleProfile()
        }
        val base = state.baseUrl ?: throw ApiException(400)
        val json = org.json.JSONObject()
            .put("specialMode", settings.specialMode)
            .put("typicalCycleLengthOptional", settings.typicalCycleLength ?: org.json.JSONObject.NULL)
            .put("typicalPeriodLengthOptional", settings.typicalPeriodLength ?: org.json.JSONObject.NULL)
            .put("predictionPaused", settings.predictionPaused)
            .toString()
        fun body() = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .updateCycleSettings(endpoint(base, "api/v1/cycle/profile"), body())
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .updateCycleSettings(endpoint(base, "api/v1/cycle/profile"), body())
        }
        val saved = response.requireBody()
        cycleSettingsGeneration.incrementAndGet()
        // Reminders must not keep using an estimate that was just switched off.
        if (CycleSettings.of(saved).switchesPredictionsOff()) notificationDataCache.clear()
        return saved
    }

    suspend fun sendCheckIn(prompt: String): de.lunara.app.data.remote.dto.CheckInDto {
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
        val request = de.lunara.app.data.remote.dto.CreateCheckInRequest(prompt.take(240))
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .createCheckIn(endpoint(base, "api/v1/check-ins"), request)
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .createCheckIn(endpoint(base, "api/v1/check-ins"), request)
        }
        return response.requireBody()
    }
}

/** Life phase and known lengths a tracker can set; null lengths mean "calculate from entries". */
data class CycleSettings(
    val specialMode: String,
    val typicalCycleLength: Int?,
    val typicalPeriodLength: Int?,
    val predictionPaused: Boolean,
) {
    /** Pregnancy, the pill, a pause … all mean: show no regular period estimate. */
    fun switchesPredictionsOff(): Boolean = predictionPaused || specialMode != "NONE"

    companion object {
        val MODES = listOf("NONE", "HORMONAL_CONTRACEPTION", "PREGNANCY", "POSTPARTUM", "MENOPAUSE_TRANSITION", "PAUSED")

        fun of(profile: CycleProfileDto) = CycleSettings(
            specialMode = profile.specialMode.takeIf { it in MODES } ?: "NONE",
            typicalCycleLength = profile.typicalCycleLengthOptional,
            typicalPeriodLength = profile.typicalPeriodLengthOptional,
            predictionPaused = profile.predictionPaused,
        )

        /** Accepts empty input as "automatic" and rejects values outside the server's limits. */
        fun parseLength(text: String, range: IntRange): Result<Int?> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return Result.success(null)
            val value = trimmed.toIntOrNull() ?: return Result.failure(IllegalArgumentException())
            return if (value in range) Result.success(value) else Result.failure(IllegalArgumentException())
        }
    }
}
