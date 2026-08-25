package club.nuva.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which Nuva server this installation talks to.
 *
 * Nuva is not a single service: anyone can run the server, and the client must
 * be able to point at any of them. That is the whole difference between "trust
 * us" and "run it yourself", so the address is user data - not a build
 * constant baked into the APK.
 *
 * Stored in ordinary SharedPreferences, NOT in the encrypted store: a server
 * address is not a secret, and keeping it outside the encrypted blob means a
 * broken OEM keystore can never make the app forget where its server is.
 */
class ServerStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _baseUrl = MutableStateFlow(prefs.getString(KEY_BASE_URL, null))

    /** Null until the user has picked a server. */
    val baseUrl: StateFlow<String?> = _baseUrl.asStateFlow()

    fun current(): String? = _baseUrl.value

    fun save(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
        _baseUrl.value = url
    }

    fun clear() {
        prefs.edit().remove(KEY_BASE_URL).apply()
        _baseUrl.value = null
    }

    private companion object {
        const val PREFS_NAME = "nuva_server"
        const val KEY_BASE_URL = "base_url"
    }
}
