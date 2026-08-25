package club.nuva.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models. These mirror the Go structs in server/internal/api one to one.
 * Rule: if you change a field here, change it there in the same commit.
 */

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val bio: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

@Serializable
data class AuthResponseDto(
    val user: UserDto,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("recovery_code") val recoveryCode: String? = null,
)

@Serializable
data class RegisterRequestDto(
    val username: String,
    @SerialName("display_name") val displayName: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
)

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
)

@Serializable
data class RefreshRequestDto(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class UpdateProfileRequestDto(
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class MetaDto(
    @SerialName("api_version") val apiVersion: String,
    @SerialName("online_users") val onlineUsers: Int = 0,
    @SerialName("max_upload_bytes") val maxUploadBytes: Long = 0,
)

@Serializable
data class ApiErrorDto(
    val error: Body,
) {
    @Serializable
    data class Body(val code: String = "unknown", val message: String = "")
}

// ---------------------------------------------------------------------------
// Media and reactions (4.0)
// ---------------------------------------------------------------------------

/** Reply from POST /v1/media. `url` is relative; prepend the server base. */
@Serializable
data class MediaDto(
    val id: String,
    val url: String,
    val kind: String,
    val mime: String,
    val bytes: Long,
    @SerialName("duration_ms") val durationMs: Int = 0,
    val waveform: List<Int> = emptyList(),
    /** True when the server already had these exact bytes. */
    val reused: Boolean = false,
)

/** An attachment as embedded inside a message. */
@Serializable
data class AttachmentDto(
    val id: String,
    val kind: String,
    val mime: String,
    val bytes: Long,
    @SerialName("duration_ms") val durationMs: Int = 0,
    val waveform: List<Int> = emptyList(),
)

/**
 * One collapsed reaction pill. `mine` is resolved per recipient by the server,
 * so the client never has to scan the user list to know whether to highlight.
 */
@Serializable
data class ReactionTallyDto(
    val emoji: String,
    val count: Int,
    val mine: Boolean = false,
    val users: List<String> = emptyList(),
)

/** Payload of a `reaction_relay` frame. Always the full tally, never a delta. */
@Serializable
data class ReactionRelayDto(
    @SerialName("message_id") val messageId: String,
    @SerialName("conversation_id") val conversationId: String,
    val reactions: List<ReactionTallyDto> = emptyList(),
)

/** Payload of `send_voice`. The audio is uploaded over HTTP first. */
@Serializable
data class SendVoiceDto(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("attachment_id") val attachmentId: String,
)

/** Payload of `reaction_add` and `reaction_remove`. */
@Serializable
data class ReactionActionDto(
    @SerialName("message_id") val messageId: String,
    val emoji: String,
)
