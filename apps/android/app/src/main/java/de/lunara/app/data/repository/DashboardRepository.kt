package de.lunara.app.data.repository

import de.lunara.app.data.preferences.AppPreferences
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

@Singleton
class DashboardRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val connectionRepository: ConnectionRepository,
    private val database: LunaraDatabase,
    private val gson: Gson,
    private val notificationDataCache: NotificationDataCache,
) {
    suspend fun partnerDashboard(expectedUserId: String): PartnerDashboardDto {
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .partnerDashboard(endpoint(base, "api/v1/partner/dashboard"))
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .partnerDashboard(endpoint(base, "api/v1/partner/dashboard"))
        }
        val dashboard = response.requireBody()
        var tipResponse = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .todayTip(endpoint(base, "api/v1/tips/today"))
        if (tipResponse.code() == 401 && connectionRepository.refresh()) {
            tipResponse = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .todayTip(endpoint(base, "api/v1/tips/today"))
        }
        val completeDashboard = dashboard.copy(tip = tipResponse.requireBody().tip)
        ProcessSyncGate.serialized {
            if (preferences.state.first().session?.userId != expectedUserId) throw SessionChangedException()
            database.partnerDashboardDao().save(
                PartnerDashboardEntity(
                    payloadJson = gson.toJson(completeDashboard),
                    updatedAt = Instant.now().toString(),
                ),
            )
        }
        return completeDashboard
    }

    suspend fun cachedPartnerDashboard(): PartnerDashboardDto? = database.partnerDashboardDao().current()?.let {
        runCatching { gson.fromJson(it.payloadJson, PartnerDashboardDto::class.java) }.getOrNull()
    }

    fun observeCachedPartnerDashboard(): Flow<PartnerDashboardDto?> =
        database.partnerDashboardDao().observeCurrent().map { cached ->
            cached?.let { runCatching { gson.fromJson(it.payloadJson, PartnerDashboardDto::class.java) }.getOrNull() }
        }

    suspend fun trackerPrediction(expectedUserId: String): de.lunara.app.data.remote.dto.PredictionDto {
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
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
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .cycleProfile(endpoint(base, "api/v1/cycle/profile"))
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .cycleProfile(endpoint(base, "api/v1/cycle/profile"))
        }
        return response.requireBody()
    }

    suspend fun setFertilityPredictionEnabled(enabled: Boolean): CycleProfileDto {
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
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
