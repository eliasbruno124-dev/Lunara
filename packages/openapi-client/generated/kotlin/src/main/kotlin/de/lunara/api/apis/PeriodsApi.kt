package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.CreatePeriodDto
import de.lunara.api.models.PeriodResponse
import de.lunara.api.models.UpdatePeriodDto

interface PeriodsApi {
    /**
     * POST api/v1/periods
     * 
     * 
     * Responses:
     *  - 201: Successful response
     *
     * @param createPeriodDto 
     * @return [PeriodResponse]
     */
    @POST("api/v1/periods")
    suspend fun periodsCreate(@Body createPeriodDto: CreatePeriodDto): Response<PeriodResponse>

    /**
     * GET api/v1/periods
     * 
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.List<PeriodResponse>]
     */
    @GET("api/v1/periods")
    suspend fun periodsList(): Response<kotlin.collections.List<PeriodResponse>>

    /**
     * DELETE api/v1/periods/{id}
     * 
     * 
     * Responses:
     *  - 204: 
     *
     * @param id 
     * @param version 
     * @return [Unit]
     */
    @DELETE("api/v1/periods/{id}")
    suspend fun periodsRemove(@Path("id") id: kotlin.String, @Query("version") version: java.math.BigDecimal): Response<Unit>

    /**
     * PATCH api/v1/periods/{id}
     * 
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @param id 
     * @param updatePeriodDto 
     * @return [PeriodResponse]
     */
    @PATCH("api/v1/periods/{id}")
    suspend fun periodsUpdate(@Path("id") id: kotlin.String, @Body updatePeriodDto: UpdatePeriodDto): Response<PeriodResponse>

}
