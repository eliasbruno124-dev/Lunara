package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.CheckInResponse
import de.lunara.api.models.CreateCheckInDto
import de.lunara.api.models.RespondCheckInDto

interface CheckInsApi {
    /**
     * POST api/v1/check-ins
     *
     *
     * Responses:
     *  - 201: Successful response
     *
     * @param createCheckInDto
     * @return [CheckInResponse]
     */
    @POST("api/v1/check-ins")
    suspend fun checkInsCreate(@Body createCheckInDto: CreateCheckInDto): Response<CheckInResponse>

    /**
     * GET api/v1/check-ins
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.List<CheckInResponse>]
     */
    @GET("api/v1/check-ins")
    suspend fun checkInsList(): Response<kotlin.collections.List<CheckInResponse>>

    /**
     * POST api/v1/check-ins/{id}/respond
     *
     *
     * Responses:
     *  - 201: Successful response
     *
     * @param id
     * @param respondCheckInDto
     * @return [CheckInResponse]
     */
    @POST("api/v1/check-ins/{id}/respond")
    suspend fun checkInsRespond(@Path("id") id: kotlin.String, @Body respondCheckInDto: RespondCheckInDto): Response<CheckInResponse>

}
