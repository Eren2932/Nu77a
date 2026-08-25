package club.nuva.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent session storage.
 *
 * Tokens are kept in EncryptedSharedPreferences, backed by a key held in the
 * Android Keystore, so a stolen backup or a rooted file dump does not hand over
 * the account. Backup is disabled for this file in data_extraction_rules.xml.
 *
 * Everything here is deliberately synchronous and tiny: reading a token must
 * never be slower than the first network call that needs it.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)

    private val _session = MutableStateFlow(readSession())

    /** Current session, or null when the user is signed out. */
    val session: StateFlow<Session?> = _session.asStateFlow()

    data class Session(
        val userId: String,
        val username: String,
        val displayName: String,
        val accessToken: String,
        val refreshToken: String,
    )

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .apply()
        _session.value = session
    }

    /** Called after a token refresh; keeps the identity, swaps the tokens. */
    fun updateTokens(accessToken: String, refreshToken: String) {
        val current = _session.value ?: return
        save(current.copy(accessToken = accessToken, refreshToken = refreshToken))
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    fun currentAccessToken(): String? = _session.value?.accessToken

    fun currentRefreshToken(): String? = _session.value?.refreshToken

    private fun readSession(): Session? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return Session(
            userId = userId,
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            displayName = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty(),
            accessToken = access,
            refreshToken = refresh,
        )
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (t: Throwable) {
            // Happens on a handful of broken OEM keystores. Losing the encrypted
            // blob is recoverable (the user signs in again); crashing on launch
            // is not.
            Log.e(TAG, "encrypted prefs unavailable, falling back to plain prefs", t)
            context.deleteSharedPreferences(PREFS_NAME)
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private companion object {
        const val TAG = "SessionStore"
        const val PREFS_NAME = "nuva_session"
        const val FALLBACK_PREFS_NAME = "nuva_session_plain"
        const val MASTER_KEY_ALIAS = "nuva_master_key"

        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
