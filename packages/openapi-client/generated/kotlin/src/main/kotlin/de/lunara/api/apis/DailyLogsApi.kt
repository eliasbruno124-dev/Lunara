package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.CreateDailyLogDto
import de.lunara.api.models.DailyLogResponse
import de.lunara.api.models.UpdateDailyLogDto

interface DailyLogsApi {
    /**
     * POST api/v1/daily-logs
     *
     *
     * Responses:
     *  - 201: Successful response
     *
     * @param createDailyLogDto
     * @return [DailyLogResponse]
     */
    @POST("api/v1/daily-logs")
    suspend fun dailyLogsCreate(@Body createDailyLogDto: CreateDailyLogDto): Response<DailyLogResponse>

    /**
     * GET api/v1/daily-logs
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @param from  (optional)
     * @param to  (optional)
     * @return [kotlin.collections.List<DailyLogResponse>]
     */
    @GET("api/v1/daily-logs")
    suspend fun dailyLogsList(@Query("from") from: java.time.LocalDate? = null, @Query("to") to: java.time.LocalDate? = null): Response<kotlin.collections.List<DailyLogResponse>>

    /**
     * DELETE api/v1/daily-logs/{id}
     *
     *
     * Responses:
     *  - 204:
     *
     * @param id
     * @param version
     * @return [Unit]
     */
    @DELETE("api/v1/daily-logs/{id}")
    suspend fun dailyLogsRemove(@Path("id") id: kotlin.String, @Query("version") version: java.math.BigDecimal): Response<Unit>

    /**
     * PATCH api/v1/daily-logs/{id}
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @param id
     * @param updateDailyLogDto
     * @return [DailyLogResponse]
     */
    @PATCH("api/v1/daily-logs/{id}")
    suspend fun dailyLogsUpdate(@Path("id") id: kotlin.String, @Body updateDailyLogDto: UpdateDailyLogDto): Response<DailyLogResponse>

}
