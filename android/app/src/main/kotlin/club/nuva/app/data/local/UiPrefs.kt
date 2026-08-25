package club.nuva.app.data.local

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Appearance preferences.
 *
 * Plain SharedPreferences on purpose, same reasoning as ServerStore: none of
 * this is secret, and a broken keystore on a bad ROM must never be able to
 * make the app forget how it should look. Read synchronously at construction
 * so the very first frame is already painted in the right theme — a theme flash
 * on launch is the cheapest way to look unfinished.
 */
class UiPrefs(context: Context) {

    enum class ThemeMode { System, Dark, Light }

    @Immutable
    data class State(
        val themeMode: ThemeMode = ThemeMode.System,
        val compactChats: Boolean = false,
        val sendOnEnter: Boolean = false,
    )

    private val prefs = context.getSharedPreferences("nuva_ui", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        State(
            themeMode = runCatching {
                ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.System.name)!!)
            }.getOrDefault(ThemeMode.System),
            compactChats = prefs.getBoolean(KEY_COMPACT, false),
            sendOnEnter = prefs.getBoolean(KEY_SEND_ON_ENTER, false),
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun setCompactChats(value: Boolean) {
        prefs.edit().putBoolean(KEY_COMPACT, value).apply()
        _state.update { it.copy(compactChats = value) }
    }

    fun setSendOnEnter(value: Boolean) {
        prefs.edit().putBoolean(KEY_SEND_ON_ENTER, value).apply()
        _state.update { it.copy(sendOnEnter = value) }
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_COMPACT = "compact_chats"
        const val KEY_SEND_ON_ENTER = "send_on_enter"
    }
}
