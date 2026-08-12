package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json


interface HealthApi {
    /**
     * GET health
     * Liveness check
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @GET("health")
    suspend fun healthHealth(): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

    /**
     * GET ready
     * Readiness check including PostgreSQL
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @GET("ready")
    suspend fun healthReady(): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

}
