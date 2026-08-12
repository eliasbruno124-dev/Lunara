package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.PartnerDashboardResponse

interface PartnerApi {
    /**
     * GET api/v1/partner/dashboard
     * 
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @return [PartnerDashboardResponse]
     */
    @GET("api/v1/partner/dashboard")
    suspend fun partnerDashboard(): Response<PartnerDashboardResponse>

}
