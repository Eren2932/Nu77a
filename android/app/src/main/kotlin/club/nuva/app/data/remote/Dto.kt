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
