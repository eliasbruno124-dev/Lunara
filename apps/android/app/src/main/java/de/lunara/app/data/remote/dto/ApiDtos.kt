package de.lunara.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MetaDto(
    val name: String,
    val version: String,
    val apiVersion: String,
    val setupRequired: Boolean,
    val registrationEnabled: Boolean = false,
    val serverTime: String,
    val gatewayHeaderName: String? = null,
)

data class LoginRequest(
    val username: String,
    val password: String,
    val deviceName: String,
)

data class SetupRequest(
    val bootstrapToken: String,
    val householdName: String,
    val username: String,
    val displayName: String,
    val password: String,
    val preferredLanguage: String,
    val deviceName: String,
)

data class JoinRequest(
    val invitationCode: String,
    val username: String,
    val displayName: String,
    val password: String,
    val preferredLanguage: String,
    val deviceName: String,
)

data class RefreshRequest(val refreshToken: String)

data class LogoutRequest(val refreshToken: String)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

data class MembershipDto(
    val id: String? = null,
    val role: String,
    val customRoleLabel: String? = null,
)

data class UserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val preferredLanguage: String? = null,
    val role: String,
    val householdId: String? = null,
    val membership: MembershipDto? = null,
)

data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long? = null,
    val sessionId: String? = null,
    val user: UserDto,
)

data class CreatePeriodRequest(
    val id: String,
    val startDate: String,
    val endDate: String? = null,
    val source: String = "MANUAL",
    val clientMutationId: String,
)

data class PeriodDto(
    val id: String,
    val userId: String,
    val startDate: String,
    val endDate: String? = null,
    val source: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val version: Long,
)

data class SyncMutationDto(
    val clientMutationId: String,
    val entityType: String,
    val operation: String,
    val payload: Map<String, Any?>,
)

data class SyncRequest(
    val cursor: String? = null,
    val mutations: List<SyncMutationDto>,
)

data class SyncChangeDto(
    val cursor: String? = null,
    val entityType: String,
    val entityId: String? = null,
    val operation: String,
    val entityVersion: Long? = null,
    val occurredAt: String? = null,
    @SerializedName(value = "entity", alternate = ["data"])
    val entity: Map<String, Any?>? = null,
)

data class SyncResultDto(
    val clientMutationId: String,
    val entityType: String? = null,
    val id: String? = null,
    val deleted: Boolean = false,
    val entity: Map<String, Any?>? = null,
)

data class SyncResponseDto(
    val cursor: String,
    val hasMore: Boolean = false,
    val results: List<SyncResultDto> = emptyList(),
    val changes: List<SyncChangeDto> = emptyList(),
    val partnerDashboard: PartnerDashboardDto? = null,
)

data class SyncPullDto(
    val cursor: String,
    val hasMore: Boolean = false,
    val changes: List<SyncChangeDto> = emptyList(),
    val partnerDashboard: PartnerDashboardDto? = null,
)

data class PredictionDto(
    val available: Boolean = true,
    val predictedStartFrom: String? = null,
    val predictedStartTo: String? = null,
    val confidence: String,
    val basedOnCycleCount: Int = 0,
    val explanation: Map<String, Any?>? = null,
    val reason: String? = null,
    val typicalCycleLength: Int? = null,
    val typicalPeriodLength: Int? = null,
    val variationDays: Int? = null,
    val statistics: Map<String, Any?>? = null,
)

data class PartnerTipDto(
    val id: String,
    val title: String,
    val body: String,
    val callToAction: String,
    val category: String,
)

data class PartnerDashboardDto(
    val sharingPaused: Boolean = false,
    val prediction: PredictionDto? = null,
    val phase: String? = null,
    val periodDates: List<Map<String, Any?>> = emptyList(),
    val todayLog: Map<String, Any?>? = null,
    val tip: PartnerTipDto? = null,
)

data class TodayTipDto(val tip: PartnerTipDto? = null, val date: String? = null)

data class CreateCheckInRequest(val prompt: String)
data class CheckInDto(
    val id: String,
    val prompt: String,
    val status: String,
    val response: String? = null,
    val expiresAt: String,
)

data class CreateInvitationRequest(val expiresInMinutes: Int? = null)
data class InvitationDto(
    val id: String,
    val code: String,
    val assignedRole: String,
    val expiresAt: String,
)

data class SharingSettingsDto(
    val sharePrediction: Boolean = true,
    val sharePeriodDates: Boolean = false,
    val sharePhase: Boolean = false,
    val shareFlow: Boolean = false,
    val sharePain: Boolean = false,
    val shareMood: Boolean = false,
    val shareEnergy: Boolean = false,
    val shareSleep: Boolean = false,
    val shareSymptoms: Boolean = false,
    val shareNotes: Boolean = false,
    val shareFertileWindow: Boolean = false,
    val allowSymptomBasedTips: Boolean = false,
    val tipsEnabled: Boolean = true,
    val checkInsEnabled: Boolean = true,
    val sharingPaused: Boolean = false,
    val privateDate: String? = null,
)

data class UpdateSharingRequest(
    val sharePrediction: Boolean? = null,
    val sharePeriodDates: Boolean? = null,
    val sharePhase: Boolean? = null,
    val shareFlow: Boolean? = null,
    val sharePain: Boolean? = null,
    val shareMood: Boolean? = null,
    val shareEnergy: Boolean? = null,
    val shareSleep: Boolean? = null,
    val shareSymptoms: Boolean? = null,
    val shareNotes: Boolean? = null,
    val shareFertileWindow: Boolean? = null,
    val allowSymptomBasedTips: Boolean? = null,
    val tipsEnabled: Boolean? = null,
    val checkInsEnabled: Boolean? = null,
    val sharingPaused: Boolean? = null,
    val privateDate: String? = null,
)
