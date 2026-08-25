package club.nuva.app.data.repository

import android.os.Build
import club.nuva.app.data.local.SessionStore
import club.nuva.app.data.remote.ApiException
import club.nuva.app.data.remote.NuvaApi
import club.nuva.app.data.remote.UserDto
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the sign-in lifecycle. The UI never touches NuvaApi or SessionStore for
 * auth, so there is exactly one code path that can create or destroy a session.
 */
class AuthRepository(
    private val api: NuvaApi,
    private val sessionStore: SessionStore,
) {
    val session: StateFlow<SessionStore.Session?> = sessionStore.session

    val isSignedIn: Boolean get() = sessionStore.session.value != null

    /** Returns the one-time recovery code that must be shown to the user. */
    suspend fun register(username: String, displayName: String, password: String): String {
        val response = api.register(
            username = username.trim(),
            displayName = displayName.trim().ifEmpty { username.trim() },
            password = password,
            deviceName = deviceName(),
        )
        persist(response.user, response.accessToken, response.refreshToken)
        return response.recoveryCode.orEmpty()
    }

    suspend fun login(username: String, password: String) {
        val response = api.login(
            username = username.trim(),
            password = password,
            deviceName = deviceName(),
        )
        persist(response.user, response.accessToken, response.refreshToken)
    }

    /**
     * Signs out locally no matter what the server says. A network failure must
     * never leave the user stuck inside an account they asked to leave.
     */
    suspend fun logout() {
        sessionStore.currentRefreshToken()?.let { api.logout(it) }
        sessionStore.clear()
    }

    /**
     * Verifies the stored session against the server and refreshes the cached
     * profile. Returns null when the session is gone for good.
     */
    suspend fun refreshProfile(): UserDto? {
        if (!isSignedIn) return null
        return try {
            val user = api.me()
            sessionStore.session.value?.let { current ->
                sessionStore.save(
                    current.copy(username = user.username, displayName = user.displayName),
                )
            }
            user
        } catch (e: ApiException) {
            // Only a definitive rejection clears the session; being offline
            // keeps the user signed in with cached data.
            if (e.isAuthProblem) sessionStore.clear()
            null
        }
    }

    private fun persist(user: UserDto, accessToken: String, refreshToken: String) {
        sessionStore.save(
            SessionStore.Session(
                userId = user.id,
                username = user.username,
                displayName = user.displayName,
                accessToken = accessToken,
                refreshToken = refreshToken,
            ),
        )
    }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
}
