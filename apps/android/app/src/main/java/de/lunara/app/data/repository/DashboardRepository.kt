package de.lunara.app.data.repository

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

    suspend fun partnerDashboard(expectedUserId: String): PartnerDashboardDto = partnerRefreshMutex.withLock {
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
                .todayTip(endpoint(base, "api/v1/tips/today"))
            if (tipResponse.code() == 401 && connectionRepository.refresh()) {
                tipResponse = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                    .todayTip(endpoint(base, "api/v1/tips/today"))
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

    suspend fun trackerPrediction(expectedUserId: String): de.lunara.app.data.remote.dto.PredictionDto {
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
                notificationDataCache.saveTrackerPrediction(expectedUserId, prediction)
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
            notificationDataCache.saveTrackerPrediction(expectedUserId, prediction)
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
