package de.lunara.app.data.remote

import de.lunara.app.data.remote.dto.AuthResponseDto
import de.lunara.app.data.remote.dto.CheckInDto
import de.lunara.app.data.remote.dto.ChangePasswordRequest
import de.lunara.app.data.remote.dto.CreateCheckInRequest
import de.lunara.app.data.remote.dto.CreateInvitationRequest
import de.lunara.app.data.remote.dto.InvitationDto
import de.lunara.app.data.remote.dto.JoinRequest
import de.lunara.app.data.remote.dto.LoginRequest
import de.lunara.app.data.remote.dto.LogoutRequest
import de.lunara.app.data.remote.dto.MetaDto
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PeriodDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.remote.dto.RefreshRequest
import de.lunara.app.data.remote.dto.SetupRequest
import de.lunara.app.data.remote.dto.SyncRequest
import de.lunara.app.data.remote.dto.SyncResponseDto
import de.lunara.app.data.remote.dto.SyncPullDto
import de.lunara.app.data.remote.dto.TodayTipDto
import de.lunara.app.data.remote.dto.SharingSettingsDto
import de.lunara.app.data.remote.dto.UpdateSharingRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Query
import retrofit2.http.POST
import retrofit2.http.Url

interface LunaraApi {
    @GET suspend fun meta(@Url url: String): Response<MetaDto>
    @POST suspend fun setup(@Url url: String, @Body request: SetupRequest): Response<AuthResponseDto>
    @POST suspend fun login(@Url url: String, @Body request: LoginRequest): Response<AuthResponseDto>
    @POST suspend fun join(@Url url: String, @Body request: JoinRequest): Response<AuthResponseDto>
    @POST suspend fun refresh(@Url url: String, @Body request: RefreshRequest): Response<AuthResponseDto>
    @POST suspend fun logout(@Url url: String, @Body request: LogoutRequest): Response<Unit>
    @POST suspend fun changePassword(@Url url: String, @Body request: ChangePasswordRequest): Response<Unit>
    @POST suspend fun sync(@Url url: String, @Body request: SyncRequest): Response<SyncResponseDto>
    @GET suspend fun pullSync(@Url url: String, @Query("cursor") cursor: String): Response<SyncPullDto>
    @GET suspend fun partnerDashboard(@Url url: String): Response<PartnerDashboardDto>
    @GET suspend fun periods(@Url url: String): Response<List<PeriodDto>>
    @GET suspend fun prediction(@Url url: String): Response<PredictionDto>
    @GET suspend fun todayTip(@Url url: String): Response<TodayTipDto>
    @POST suspend fun createCheckIn(@Url url: String, @Body request: CreateCheckInRequest): Response<CheckInDto>
    @POST suspend fun createInvitation(@Url url: String, @Body request: CreateInvitationRequest): Response<InvitationDto>
    @GET suspend fun sharing(@Url url: String): Response<SharingSettingsDto>
    @PATCH suspend fun updateSharing(@Url url: String, @Body request: UpdateSharingRequest): Response<SharingSettingsDto>
}
