package de.lunara.app.data.remote

import de.lunara.app.data.remote.dto.AuthResponseDto
import de.lunara.app.data.remote.dto.AcceptInvitationRequest
import de.lunara.app.data.remote.dto.CheckInDto
import de.lunara.app.data.remote.dto.ChangePasswordRequest
import de.lunara.app.data.remote.dto.CarePreferencesDto
import de.lunara.app.data.remote.dto.CycleProfileDto
import de.lunara.app.data.remote.dto.CreateCheckInRequest
import de.lunara.app.data.remote.dto.CreateInvitationRequest
import de.lunara.app.data.remote.dto.InvitationDto
import de.lunara.app.data.remote.dto.JoinRequest
import de.lunara.app.data.remote.dto.LoginRequest
import de.lunara.app.data.remote.dto.LogoutRequest
import de.lunara.app.data.remote.dto.MetaDto
import de.lunara.app.data.remote.dto.HealthDto
import de.lunara.app.data.remote.dto.OidcLinkRequest
import de.lunara.app.data.remote.dto.OidcLoginRequest
import de.lunara.app.data.remote.dto.OidcStatusDto
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PeriodDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.remote.dto.RefreshRequest
import de.lunara.app.data.remote.dto.RegisterRequest
import de.lunara.app.data.remote.dto.RelationshipDto
import de.lunara.app.data.remote.dto.SetupRequest
import de.lunara.app.data.remote.dto.SyncRequest
import de.lunara.app.data.remote.dto.SyncResponseDto
import de.lunara.app.data.remote.dto.SyncPullDto
import de.lunara.app.data.remote.dto.TodayTipDto
import de.lunara.app.data.remote.dto.SharingSettingsDto
import de.lunara.app.data.remote.dto.UpdateSharingRequest
import de.lunara.app.data.remote.dto.UpdateCycleProfileRequest
import de.lunara.app.data.remote.dto.UpdateCarePreferencesRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Query
import retrofit2.http.POST
import retrofit2.http.Url

interface LunaraApi {
    @GET suspend fun meta(@Url url: String): Response<MetaDto>
    @GET suspend fun health(@Url url: String): Response<HealthDto>
    @POST suspend fun setup(@Url url: String, @Body request: SetupRequest): Response<AuthResponseDto>
    @POST suspend fun registerTracker(@Url url: String, @Body request: RegisterRequest): Response<AuthResponseDto>
    @POST suspend fun login(@Url url: String, @Body request: LoginRequest): Response<AuthResponseDto>
    @POST suspend fun oidcLogin(@Url url: String, @Body request: OidcLoginRequest): Response<AuthResponseDto>
    @POST suspend fun linkOidc(@Url url: String, @Body request: OidcLinkRequest): Response<OidcStatusDto>
    @GET suspend fun oidcStatus(@Url url: String): Response<OidcStatusDto>
    @POST suspend fun join(@Url url: String, @Body request: JoinRequest): Response<AuthResponseDto>
    @POST suspend fun acceptInvitation(@Url url: String, @Body request: AcceptInvitationRequest): Response<Unit>
    @GET suspend fun me(@Url url: String): Response<de.lunara.app.data.remote.dto.UserDto>
    @POST suspend fun refresh(@Url url: String, @Body request: RefreshRequest): Response<AuthResponseDto>
    @POST suspend fun logout(@Url url: String, @Body request: LogoutRequest): Response<Unit>
    @POST suspend fun changePassword(@Url url: String, @Body request: ChangePasswordRequest): Response<Unit>
    @POST suspend fun sync(@Url url: String, @Body request: SyncRequest): Response<SyncResponseDto>
    @GET suspend fun pullSync(@Url url: String, @Query("cursor") cursor: String): Response<SyncPullDto>
    @GET suspend fun partnerDashboard(@Url url: String): Response<PartnerDashboardDto>
    @GET suspend fun periods(@Url url: String): Response<List<PeriodDto>>
    @GET suspend fun prediction(@Url url: String): Response<PredictionDto>
    @GET suspend fun cycleProfile(@Url url: String): Response<CycleProfileDto>
    @PATCH suspend fun updateCycleProfile(@Url url: String, @Body request: UpdateCycleProfileRequest): Response<CycleProfileDto>
    /** Raw JSON body, because Gson drops nulls and clearing a typical length needs an explicit null. */
    @PATCH suspend fun updateCycleSettings(@Url url: String, @Body body: okhttp3.RequestBody): Response<CycleProfileDto>
    @GET suspend fun todayTip(@Url url: String): Response<TodayTipDto>
    @GET suspend fun personalTips(@Url url: String): Response<List<de.lunara.app.data.remote.dto.PersonalTipDto>>
    @POST suspend fun createPersonalTip(@Url url: String, @Body request: de.lunara.app.data.remote.dto.CreatePersonalTipRequest): Response<de.lunara.app.data.remote.dto.PersonalTipDto>
    @DELETE suspend fun disablePersonalTip(@Url url: String): Response<Unit>
    @POST suspend fun createCheckIn(@Url url: String, @Body request: CreateCheckInRequest): Response<CheckInDto>
    @POST suspend fun createInvitation(@Url url: String, @Body request: CreateInvitationRequest): Response<InvitationDto>
    @GET suspend fun openInvitations(@Url url: String): Response<List<InvitationDto>>
    @GET suspend fun relationship(@Url url: String): Response<RelationshipDto>
    @DELETE suspend fun revokeInvitation(@Url url: String): Response<Unit>
    @GET suspend fun sharing(@Url url: String): Response<SharingSettingsDto>
    @PATCH suspend fun updateSharing(@Url url: String, @Body request: UpdateSharingRequest): Response<SharingSettingsDto>
    @GET suspend fun carePreferences(@Url url: String): Response<CarePreferencesDto>
    @PATCH suspend fun updateCarePreferences(
        @Url url: String,
        @Body request: UpdateCarePreferencesRequest,
    ): Response<CarePreferencesDto>
}
