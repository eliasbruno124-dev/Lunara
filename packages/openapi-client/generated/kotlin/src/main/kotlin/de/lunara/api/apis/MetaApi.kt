package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.MetaResponse

interface MetaApi {
    /**
     * GET api/v1/meta
     * Server identity, compatibility and setup state
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @return [MetaResponse]
     */
    @GET("api/v1/meta")
    suspend fun metaGetMeta(): Response<MetaResponse>

}
