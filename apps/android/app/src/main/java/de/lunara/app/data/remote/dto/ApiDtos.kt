package de.lunara.app.data.remote.dto

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

data class MetaDto(
    val name: String,
    val version: String,
    val apiVersion: String,
    val setupRequired: Boolean,
    val registrationEnabled: Boolean = false,
    val serverTime: String,
    val gatewayHeaderName: String? = null,
    val gatewayMode: String? = null,
    val authenticationMethods: List<String> = listOf("PASSWORD"),
    /** Present on servers that explicitly support Authentik-primary enrollment. */
    val primaryAuthenticationMethod: String? = null,
    val oidc: OidcMetaDto? = null,
    val refreshIdempotencySupported: Boolean = false,
)

data class HealthDto(val status: String)

data class OidcMetaDto(
    val providerName: String,
    val issuer: String,
    /** Public OAuth client identifier. This is intentionally not a secret. */
    val clientId: String? = null,
    /** Accepted access-token audience. Older servers used this as the client identifier too. */
    val audience: String? = null,
    val scopes: List<String> = listOf("openid"),
    val discoveryUrl: String? = null,
    val enrollment: OidcEnrollmentDto? = null,
)

data class OidcEnrollmentDto(
    val registration: Boolean = false,
    val invitation: Boolean = false,
    val localPasswordRequired: Boolean = true,
    val createIfMissingRequired: Boolean = false,
)

data class OidcLoginRequest(
    val accessToken: String,
    val deviceName: String,
    /**
     * Optional single-use relationship proof. On an Authentik-primary server an
     * unknown external identity becomes a PARTNER only when this code is
     * redeemed in the same verified OIDC operation.
     */
    val invitationCode: String? = null,
    /** Used only while provisioning a previously unknown external identity. */
    val preferredLanguage: String? = null,
    /**
     * Explicit opt-in for creating a TRACKER projection when this external
     * identity is not known yet. Keeping the wire default false prevents an
     * older invitation flow from silently starting a separate relationship.
     */
    val createIfMissing: Boolean? = null,
)

data class OidcLinkRequest(
    val accessToken: String,
    val currentPassword: String,
)

data class AcceptInvitationRequest(
    val invitationCode: String,
    val confirm: Boolean = true,
)

data class OidcStatusDto(
    val enabled: Boolean,
    val providerName: String? = null,
    val linked: Boolean = false,
    val linkedAt: String? = null,
    val lastUsedAt: String? = null,
    val passwordLoginEnabled: Boolean = true,
)

data class LoginRequest(
    val username: String,
    val password: String,
    val deviceName: String,
)

data class SetupRequest(
    val bootstrapToken: String,
    val username: String,
    val displayName: String,
    val password: String,
    val preferredLanguage: String,
    val deviceName: String,
    // Kept only for compatibility with older v1 servers. This internal value is
    // deliberately neutral and is never requested or shown in the app.
    val householdName: String = "Lunara",
)

data class RegisterRequest(
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

data class RefreshRequest(
    val refreshToken: String,
    /** Required by the server only when this Lunara session was created through OIDC. */
    val oidcAccessToken: String? = null,
    /** Stable across retries of the same rotation; omitted for older servers. */
    val refreshRequestId: String? = null,
)

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
    /**
     * A tree payload plus the field adapter retains explicit JsonNull values on the actual
     * Retrofit wire. Daily-log edits can therefore clear an optional value without globally
     * enabling serializeNulls for unrelated PATCH requests.
     */
    @field:JsonAdapter(ExplicitNullJsonObjectAdapter::class)
    val payload: JsonObject,
)

/** Enables explicit nulls only while Gson writes the mutation's nested payload object. */
class ExplicitNullJsonObjectAdapter : TypeAdapter<JsonObject>() {
    private val delegate = Gson().getAdapter(JsonObject::class.java)

    override fun write(writer: JsonWriter, value: JsonObject?) {
        val previousSerializeNulls = writer.serializeNulls
        writer.serializeNulls = true
        try {
            delegate.write(writer, value)
        } finally {
            writer.serializeNulls = previousSerializeNulls
        }
    }

    override fun read(reader: JsonReader): JsonObject? = delegate.read(reader)
}

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
    val predictedEndFrom: String? = null,
    val predictedEndTo: String? = null,
    val fertileWindowFrom: String? = null,
    val fertileWindowTo: String? = null,
    val fertilityEstimate: FertilityEstimateDto? = null,
    val confidence: String,
    val basedOnCycleCount: Int = 0,
    val explanation: Map<String, Any?>? = null,
    val reason: String? = null,
    val typicalCycleLength: Int? = null,
    val typicalPeriodLength: Int? = null,
    val variationDays: Int? = null,
    val statistics: Map<String, Any?>? = null,
    val calculatedAt: String? = null,
    val algorithmVersion: String? = null,
)

data class FertilityEstimateDto(
    val enabled: Boolean = false,
    val available: Boolean = false,
    val estimated: Boolean = false,
    val reason: String? = null,
    val basedOnCycleCount: Int = 0,
    val notContraception: Boolean = false,
    val doesNotConfirmOvulation: Boolean = false,
)

data class CycleProfileDto(
    val id: String? = null,
    val userId: String? = null,
    val typicalCycleLengthOptional: Int? = null,
    val typicalPeriodLengthOptional: Int? = null,
    val lutealLengthOptional: Int? = null,
    val fertilityPredictionEnabled: Boolean = false,
    val predictionPaused: Boolean = false,
    val specialMode: String = "NONE",
    val timezone: String? = null,
)

data class UpdateCycleProfileRequest(
    val fertilityPredictionEnabled: Boolean? = null,
)

data class PartnerTipDto(
    val id: String,
    val title: String,
    val body: String,
    val callToAction: String,
    val category: String,
    val applicablePhase: String? = null,
    val selectionReason: String? = null,
    val source: TipSourceDto? = null,
)

data class TipSourceDto(val title: String, val url: String)

data class PersonalTipDto(val id: String, val titleDe: String, val bodyDe: String, val callToActionDe: String)
data class CreatePersonalTipRequest(
    val id: String,
    val titleDe: String,
    val bodyDe: String,
    val callToActionDe: String,
    val titleEn: String = titleDe,
    val bodyEn: String = bodyDe,
    val callToActionEn: String = callToActionDe,
)

data class PartnerDashboardDto(
    val sharingPaused: Boolean = false,
    val prediction: PredictionDto? = null,
    val phase: String? = null,
    val periodDates: List<Map<String, Any?>> = emptyList(),
    val todayLog: Map<String, Any?>? = null,
    val tip: PartnerTipDto? = null,
    /** Effective permissions, not record availability. Absent on older servers. */
    val calendarAccess: PartnerCalendarAccessDto? = null,
)

data class PartnerCalendarAccessDto(
    val periodDates: Boolean? = null,
    val fertileWindow: Boolean? = null,
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

data class CreateInvitationRequest(
    val neverExpires: Boolean = true,
)
data class InvitationDto(
    val id: String,
    val code: String? = null,
    val assignedRole: String,
    val expiresAt: String? = null,
    val createdAt: String? = null,
    /** Present only when a code is created; never returned by invitation overview after restart. */
    val acceptanceUrl: String? = null,
)

data class RelationshipMemberDto(
    val role: String,
    val disabled: Boolean = false,
    val userId: String = "",
    val displayName: String = "",
)

data class RelationshipDto(
    val members: List<RelationshipMemberDto> = emptyList(),
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

data class CarePreferencesDto(
    val likesWarmth: Boolean = false,
    val likesCuddling: Boolean = false,
    val likesSpace: Boolean = false,
    val likesSnacks: Boolean = false,
    val likesConversation: Boolean = false,
    val likesHouseholdHelp: Boolean = false,
    val likesReminders: Boolean = false,
    val dislikesUnsolicitedAdvice: Boolean = true,
    val customHelpfulActions: List<String> = emptyList(),
    val customUnhelpfulActions: List<String> = emptyList(),
)

data class UpdateCarePreferencesRequest(
    val likesWarmth: Boolean? = null,
    val likesCuddling: Boolean? = null,
    val likesSpace: Boolean? = null,
    val likesSnacks: Boolean? = null,
    val likesConversation: Boolean? = null,
    val likesHouseholdHelp: Boolean? = null,
    val likesReminders: Boolean? = null,
    val dislikesUnsolicitedAdvice: Boolean? = null,
)
