package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.CreateCustomSymptomDto
import de.lunara.api.models.CustomSymptomResponseDto
import de.lunara.api.models.SymptomCatalogResponseDto
import de.lunara.api.models.UpdateCustomSymptomDto

interface SymptomsApi {
    /**
     * DELETE api/v1/symptoms/{id}
     *
     *
     * Responses:
     *  - 204: Custom symptom archived
     *
     * @param id
     * @return [Unit]
     */
    @DELETE("api/v1/symptoms/{id}")
    suspend fun symptomsArchive(@Path("id") id: kotlin.String): Response<Unit>

    /**
     * POST api/v1/symptoms
     *
     *
     * Responses:
     *  - 201:
     *
     * @param createCustomSymptomDto
     * @return [CustomSymptomResponseDto]
     */
    @POST("api/v1/symptoms")
    suspend fun symptomsCreate(@Body createCustomSymptomDto: CreateCustomSymptomDto): Response<CustomSymptomResponseDto>

    /**
     * GET api/v1/symptoms
     *
     *
     * Responses:
     *  - 200:
     *
     * @param includeArchived  (optional)
     * @return [SymptomCatalogResponseDto]
     */
    @GET("api/v1/symptoms")
    suspend fun symptomsList(@Query("includeArchived") includeArchived: kotlin.Boolean? = null): Response<SymptomCatalogResponseDto>

    /**
     * PATCH api/v1/symptoms/{id}
     *
     *
     * Responses:
     *  - 200:
     *
     * @param id
     * @param updateCustomSymptomDto
     * @return [CustomSymptomResponseDto]
     */
    @PATCH("api/v1/symptoms/{id}")
    suspend fun symptomsUpdate(@Path("id") id: kotlin.String, @Body updateCustomSymptomDto: UpdateCustomSymptomDto): Response<CustomSymptomResponseDto>

}
