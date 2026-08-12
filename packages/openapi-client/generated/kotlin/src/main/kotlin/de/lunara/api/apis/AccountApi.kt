package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.UpdateMeDto

interface AccountApi {
    /**
     * GET api/v1/me
     * 
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @GET("api/v1/me")
    suspend fun usersGet(): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

    /**
     * PATCH api/v1/me
     * 
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @param updateMeDto 
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @PATCH("api/v1/me")
    suspend fun usersUpdate(@Body updateMeDto: UpdateMeDto): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

}
