package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.DeleteAccountDto
import de.lunara.api.models.ExportDto
import de.lunara.api.models.ImportDto

interface DataManagementApi {
    /**
     * DELETE api/v1/account
     * 
     * 
     * Responses:
     *  - 204: 
     *
     * @param deleteAccountDto 
     * @return [Unit]
     */
    @DELETE("api/v1/account")
    suspend fun dataManagementDeleteAccount(@Body deleteAccountDto: DeleteAccountDto): Response<Unit>

    /**
     * POST api/v1/export
     * 
     * 
     * Responses:
     *  - 201: Successful response
     *
     * @param exportDto 
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @POST("api/v1/export")
    suspend fun dataManagementExport(@Body exportDto: ExportDto): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

    /**
     * POST api/v1/import
     * 
     * 
     * Responses:
     *  - 201: Successful response
     *
     * @param importDto 
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @POST("api/v1/import")
    suspend fun dataManagementImport(@Body importDto: ImportDto): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

}
