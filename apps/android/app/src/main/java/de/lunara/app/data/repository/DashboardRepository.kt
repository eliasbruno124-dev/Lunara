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

@Singleton
class DashboardRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val connectionRepository: ConnectionRepository,
    private val database: LunaraDatabase,
    private val gson: Gson,
) {
    suspend fun partnerDashboard(): PartnerDashboardDto {
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
        return dashboard.copy(tip = tipResponse.body()?.tip).also {
            database.partnerDashboardDao().save(PartnerDashboardEntity(payloadJson = gson.toJson(it), updatedAt = Instant.now().toString()))
        }
    }

    suspend fun cachedPartnerDashboard(): PartnerDashboardDto? = database.partnerDashboardDao().current()?.let {
        runCatching { gson.fromJson(it.payloadJson, PartnerDashboardDto::class.java) }.getOrNull()
    }

    suspend fun trackerPrediction(): de.lunara.app.data.remote.dto.PredictionDto {
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .prediction(endpoint(base, "api/v1/predictions"))
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .prediction(endpoint(base, "api/v1/predictions"))
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
