package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.PredictionResponse
import de.lunara.api.models.UpdateCycleProfileDto

interface CycleApi {
    /**
     * GET api/v1/predictions
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @return [PredictionResponse]
     */
    @GET("api/v1/predictions")
    suspend fun predictionGetPrediction(): Response<PredictionResponse>

    /**
     * GET api/v1/cycle/profile
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @GET("api/v1/cycle/profile")
    suspend fun predictionGetProfile(): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

    /**
     * PATCH api/v1/cycle/profile
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @param updateCycleProfileDto
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @PATCH("api/v1/cycle/profile")
    suspend fun predictionUpdateProfile(@Body updateCycleProfileDto: UpdateCycleProfileDto): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

}
