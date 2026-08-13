package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.UpdateSharingDto

interface SharingApi {
    /**
     * GET api/v1/sharing
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @GET("api/v1/sharing")
    suspend fun sharingGet(): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

    /**
     * PATCH api/v1/sharing
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @param updateSharingDto
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @PATCH("api/v1/sharing")
    suspend fun sharingUpdate(@Body updateSharingDto: UpdateSharingDto): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

}
