package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json


interface RealtimeApi {
    /**
     * GET api/v1/events
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @GET("api/v1/events")
    suspend fun realtimeStream(): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

}
