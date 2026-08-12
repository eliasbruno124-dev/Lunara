package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.UpdateCarePreferencesDto

interface CarePreferencesApi {
    /**
     * GET api/v1/care-preferences
     * 
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @GET("api/v1/care-preferences")
    suspend fun careGet(): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

    /**
     * PATCH api/v1/care-preferences
     * 
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @param updateCarePreferencesDto 
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @PATCH("api/v1/care-preferences")
    suspend fun careUpdate(@Body updateCarePreferencesDto: UpdateCarePreferencesDto): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

}
