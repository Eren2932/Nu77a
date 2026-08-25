package club.nuva.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NUVA VISUAL LANGUAGE — "Aurora"
 * =============================================================================
 * Written once, referenced everywhere. No screen may hardcode a colour, a
 * radius or a font size: it takes them from here. That rule is what makes a
 * restyle a one-file change instead of a three-day hunt.
 *
 * The three decisions that make Nuva not-Material:
 *
 *  1. COLOUR. Dark mode is a cold near-black (#0B0E12) instead of Material's
 *     grey-purple, and light mode is warm paper (#F5F4F1) instead of blue-white.
 *     The accent is periwinkle violet, not the default blue: an accent nobody
 *     confuses with WhatsApp green or Telegram blue.
 *  2. SHAPE. Everything is soft and pill-shaped: 22dp cards, full-round
 *     buttons, and message bubbles with ONE sharp-ish corner (6dp) pointing at
 *     their author. That single asymmetric corner is the shape signature of the
 *     app and it costs nothing to draw.
 *  3. TYPE. Tight negative tracking on headlines, generous line height in body
 *     text, and monospace for anything the user might have to read aloud or
 *     copy (ids, recovery codes, server addresses).
 *
 * Fonts: system sans-serif for now, deliberately. A custom face (Inter or
 * Manrope) is a drop-in later — put the .ttf in app/src/main/res/font and
 * change `NuvaFontFamily` below, nothing else. Font binaries cannot be
 * generated as text, so this is honest rather than half-done.
 */

// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------

/** Extra roles Material 3 has no slot for. Reached through `NuvaTheme.palette`. */
@Immutable
data class NuvaPalette(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceSunken: Color,
    val hairline: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val mint: Color,
    val amber: Color,
    val coral: Color,
    /** Own messages. */
    val bubbleOut: Color,
    val bubbleOutText: Color,
    /** Messages from the other side. */
    val bubbleIn: Color,
    val bubbleInText: Color,
    /** Soft aurora wash used behind hero areas. Never behind body text. */
    val glow: Color,
)

private val DarkPalette = NuvaPalette(
    isDark = true,
    canvas = Color(0xFF0B0E12),
    surface = Color(0xFF141922),
    surfaceAlt = Color(0xFF1B2230),
    surfaceSunken = Color(0xFF090B0F),
    hairline = Color(0xFF2A3342),
    text = Color(0xFFECEFF4),
    textMuted = Color(0xFF8E9AAE),
    textFaint = Color(0xFF5D6879),
    accent = Color(0xFF8B7CFF),
    accentSoft = Color(0xFFB7ADFF),
    accentInk = Color(0xFF0B0E12),
    mint = Color(0xFF3FD9B3),
    amber = Color(0xFFFFB454),
    coral = Color(0xFFFF5C6E),
    bubbleOut = Color(0xFF6F5FE8),
    bubbleOutText = Color(0xFFF6F5FF),
    bubbleIn = Color(0xFF1B2230),
    bubbleInText = Color(0xFFECEFF4),
    glow = Color(0x338B7CFF),
)

private val LightPalette = NuvaPalette(
    isDark = false,
    canvas = Color(0xFFF5F4F1),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFECEBE7),
    surfaceSunken = Color(0xFFE7E5E0),
    hairline = Color(0xFFDCDAD4),
    text = Color(0xFF14181F),
    textMuted = Color(0xFF616B7C),
    textFaint = Color(0xFF8B93A1),
    accent = Color(0xFF5B49E0),
    accentSoft = Color(0xFF8B7CFF),
    accentInk = Color(0xFFFFFFFF),
    mint = Color(0xFF17A97F),
    amber = Color(0xFFB4741A),
    coral = Color(0xFFD93B4C),
    bubbleOut = Color(0xFF5B49E0),
    bubbleOutText = Color(0xFFFFFFFF),
    bubbleIn = Color(0xFFFFFFFF),
    bubbleInText = Color(0xFF14181F),
    glow = Color(0x225B49E0),
)

private fun darkScheme(p: NuvaPalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = p.accentInk,
    primaryContainer = p.accent,
    onPrimaryContainer = p.accentInk,
    inversePrimary = p.accentSoft,
    secondary = p.mint,
    onSecondary = p.accentInk,
    tertiary = p.amber,
    onTertiary = p.accentInk,
    background = p.canvas,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = p.surfaceAlt,
    onSurfaceVariant = p.textMuted,
    surfaceContainer = p.surfaceAlt,
    surfaceContainerHigh = p.surfaceAlt,
    surfaceContainerLow = p.surface,
    error = p.coral,
    onError = Color.White,
    errorContainer = p.coral.copy(alpha = 0.16f),
    onErrorContainer = p.coral,
    outline = p.hairline,
    outlineVariant = p.hairline,
    scrim = Color(0xCC05070A),
)

private fun lightScheme(p: NuvaPalette) = lightColorScheme(
    primary = p.accent,
    onPrimary = p.accentInk,
    primaryContainer = p.accent,
    onPrimaryContainer = p.accentInk,
    inversePrimary = p.accentSoft,
    secondary = p.mint,
    onSecondary = Color.White,
    tertiary = p.amber,
    onTertiary = Color.White,
    background = p.canvas,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = p.surfaceAlt,
    onSurfaceVariant = p.textMuted,
    surfaceContainer = p.surfaceAlt,
    surfaceContainerHigh = p.surfaceAlt,
    surfaceContainerLow = p.surface,
    error = p.coral,
    onError = Color.White,
    errorContainer = p.coral.copy(alpha = 0.12f),
    onErrorContainer = p.coral,
    outline = p.hairline,
    outlineVariant = p.hairline,
    scrim = Color(0x99000000),
)

// ---------------------------------------------------------------------------
// Type
// ---------------------------------------------------------------------------

/** Swap this for a bundled face when the .ttf lands. Single point of change. */
val NuvaFontFamily: FontFamily = FontFamily.SansSerif
val NuvaMonoFamily: FontFamily = FontFamily.Monospace

private val bodyLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun sans(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = NuvaFontFamily,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = bodyLineHeight,
)

private val NuvaTypography = Typography(
    displaySmall = sans(34, FontWeight.Bold, 40, -1.0),
    headlineLarge = sans(30, FontWeight.Bold, 36, -0.8),
    headlineMedium = sans(25, FontWeight.Bold, 31, -0.6),
    headlineSmall = sans(21, FontWeight.SemiBold, 27, -0.4),
    titleLarge = sans(19, FontWeight.SemiBold, 25, -0.2),
    titleMedium = sans(16, FontWeight.SemiBold, 22),
    titleSmall = sans(14, FontWeight.SemiBold, 19),
    bodyLarge = sans(16, FontWeight.Normal, 24),
    bodyMedium = sans(14, FontWeight.Normal, 21),
    bodySmall = sans(13, FontWeight.Normal, 19),
    labelLarge = sans(15, FontWeight.SemiBold, 20),
    labelMedium = sans(13, FontWeight.Medium, 17, 0.1),
    labelSmall = sans(11, FontWeight.Medium, 15, 0.5),
)

/** For ids, tokens, addresses and recovery codes. Never for prose. */
val NuvaMonoStyle: TextStyle = TextStyle(
    fontFamily = NuvaMonoFamily,
    fontSize = 13.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.2.sp,
)

// ---------------------------------------------------------------------------
// Shape and spacing
// ---------------------------------------------------------------------------

private val NuvaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
)

/** One spacing scale. Screens use these names, not raw numbers. */
object NuvaSpace {
    val hair = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val huge = 40.dp

    /** Horizontal page gutter. Every screen uses exactly this. */
    val gutter = 20.dp
}

/** Motion durations, in one place so the whole app breathes at one rate. */
object NuvaMotion {
    const val FAST = 140
    const val NORMAL = 220
    const val SLOW = 380
}

// ---------------------------------------------------------------------------
// Theme entry point
// ---------------------------------------------------------------------------

val LocalNuvaPalette: ProvidableCompositionLocal<NuvaPalette> =
    staticCompositionLocalOf { DarkPalette }

/** `NuvaTheme.palette.mint` at any call site inside the theme. */
object NuvaTheme {
    val palette: NuvaPalette
        @Composable get() = LocalNuvaPalette.current
}

/**
 * The theme choice, resolved from the user's preference.
 *
 * Deliberately the DEFAULT argument of NuvaTheme rather than something
 * MainActivity has to pass: that way the theme setting works without touching
 * the activity, and a forgotten wiring step cannot silently ignore it.
 * Falls back to the system setting when the container is not up (previews).
 */
@Composable
fun nuvaDarkThemeDefault(): Boolean {
    val systemDark = isSystemInDarkTheme()
    if (!club.nuva.app.di.ServiceLocator.isInitialized) return systemDark
    val prefs = androidx.lifecycle.compose.collectAsStateWithLifecycle(
        club.nuva.app.di.ServiceLocator.uiPrefs.state,
    ).value
    return when (prefs.themeMode) {
        club.nuva.app.data.local.UiPrefs.ThemeMode.Dark -> true
        club.nuva.app.data.local.UiPrefs.ThemeMode.Light -> false
        club.nuva.app.data.local.UiPrefs.ThemeMode.System -> systemDark
    }
}

@Composable
fun NuvaTheme(
    darkTheme: Boolean = nuvaDarkThemeDefault(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(LocalNuvaPalette provides palette) {
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

@Deprecated("Use NuvaTheme.palette.coral")
val NuvaDanger: Color = DarkPalette.coral

@Deprecated("Use NuvaTheme.palette.mint")
val NuvaSuccess: Color = DarkPalette.mint
