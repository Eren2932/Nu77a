package club.nuva.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import club.nuva.app.data.local.UiPrefs
import club.nuva.app.di.ServiceLocator

/**
 * Theme entry point. Values live in Tokens.kt; this file only wires them into
 * Compose and resolves dark vs light. Keeping the wiring separate from the
 * values means a palette change can never accidentally alter behaviour.
 */

val LocalNuvaPalette: ProvidableCompositionLocal<NuvaPalette> =
    staticCompositionLocalOf { DarkPalette }

/** `NuvaTheme.palette.accent` at any call site inside the theme. */
object NuvaTheme {
    val palette: NuvaPalette
        @Composable get() = LocalNuvaPalette.current
}

/**
 * The theme choice, resolved from the user's preference.
 *
 * Deliberately the DEFAULT argument of NuvaTheme rather than something
 * MainActivity has to pass: the theme setting then works without touching the
 * activity, and a forgotten wiring step cannot silently ignore it. Falls back
 * to the system setting when the container is not up, which is the case in
 * @Preview and in unit tests.
 */
@Composable
fun nuvaDarkThemeDefault(): Boolean {
    val systemDark = isSystemInDarkTheme()
    if (!ServiceLocator.isInitialized) return systemDark
    val prefs = ServiceLocator.uiPrefs.state.collectAsStateWithLifecycle().value
    return when (prefs.themeMode) {
        UiPrefs.ThemeMode.Dark -> true
        UiPrefs.ThemeMode.Light -> false
        UiPrefs.ThemeMode.System -> systemDark
    }
}

@Composable
fun NuvaTheme(
    darkTheme: Boolean = nuvaDarkThemeDefault(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalNuvaPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme(palette) else lightScheme(palette),
            typography = NuvaTypography,
            shapes = NuvaShapes,
            content = content,
        )
    }
}

// ---------------------------------------------------------------------------
// Legacy top-level colours
// ---------------------------------------------------------------------------
// Kept as aliases on purpose: sprint-0 files imported these names directly.
// Deleting them would break compilation in files nobody remembers editing.
// New code must use NuvaTheme.palette instead.

@Deprecated("Use NuvaTheme.palette.accent", ReplaceWith("NuvaTheme.palette.accent"))
val NuvaBlue: Color = DarkPalette.accent

@Deprecated("Use NuvaTheme.palette.accent")
val NuvaBlueDark: Color = LightPalette.accent

@Deprecated("Use NuvaTheme.palette.canvas")
val NuvaInk: Color = DarkPalette.canvas

@Deprecated("Use NuvaTheme.palette.surface")
val NuvaSurfaceDark: Color = DarkPalette.surface

@Deprecated("Use NuvaTheme.palette.surfaceAlt")
val NuvaSurfaceDarker: Color = DarkPalette.surfaceAlt

@Deprecated("Use NuvaTheme.palette.text")
val NuvaTextDark: Color = DarkPalette.text

@Deprecated("Use NuvaTheme.palette.textMuted")
val NuvaMuted: Color = DarkPalette.textMuted

@Deprecated("Use NuvaTheme.palette.danger")
val NuvaDanger: Color = DarkPalette.danger

@Deprecated("Use NuvaTheme.palette.mint")
val NuvaSuccess: Color = DarkPalette.mint
