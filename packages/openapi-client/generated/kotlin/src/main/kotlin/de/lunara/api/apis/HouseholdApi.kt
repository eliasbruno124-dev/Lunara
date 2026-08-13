package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.AuthResponse
import de.lunara.api.models.CreateInvitationDto
import de.lunara.api.models.InvitationResponse
import de.lunara.api.models.InvitationSummaryResponse
import de.lunara.api.models.JoinHouseholdDto

interface HouseholdApi {
    /**
     * POST api/v1/household/invitations
     *
     *
     * Responses:
     *  - 201: Successful response
     *
     * @param createInvitationDto
     * @return [InvitationResponse]
     */
    @POST("api/v1/household/invitations")
    suspend fun householdCreateInvitation(@Body createInvitationDto: CreateInvitationDto): Response<InvitationResponse>

    /**
     * GET api/v1/household
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @GET("api/v1/household")
    suspend fun householdGet(): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

    /**
     * POST api/v1/household/join
     *
     *
     * Responses:
     *  - 201: Successful response
     *
     * @param joinHouseholdDto
     * @return [AuthResponse]
     */
    @POST("api/v1/household/join")
    suspend fun householdJoin(@Body joinHouseholdDto: JoinHouseholdDto): Response<AuthResponse>

    /**
     * GET api/v1/household/invitations
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.List<InvitationSummaryResponse>]
     */
    @GET("api/v1/household/invitations")
    suspend fun householdListInvitations(): Response<kotlin.collections.List<InvitationSummaryResponse>>

    /**
     * DELETE api/v1/household/invitations/{id}
     *
     *
     * Responses:
     *  - 204:
     *
     * @param id
     * @return [Unit]
     */
    @DELETE("api/v1/household/invitations/{id}")
    suspend fun householdRevokeInvitation(@Path("id") id: kotlin.String): Response<Unit>

}
