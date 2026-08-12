package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.SyncRequestDto
import de.lunara.api.models.SyncResponse

interface SyncApi {
    /**
     * GET api/v1/sync
     * 
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @param cursor  (optional)
     * @return [SyncResponse]
     */
    @GET("api/v1/sync")
    suspend fun syncPull(@Query("cursor") cursor: kotlin.String? = null): Response<SyncResponse>

    /**
     * POST api/v1/sync
     * 
     * 
     * Responses:
     *  - 201: Successful response
     *
     * @param syncRequestDto 
     * @return [SyncResponse]
     */
    @POST("api/v1/sync")
    suspend fun syncPush(@Body syncRequestDto: SyncRequestDto): Response<SyncResponse>

}
