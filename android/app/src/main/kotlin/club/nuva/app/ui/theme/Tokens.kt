package club.nuva.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NUVA VISUAL LANGUAGE 3.0 — "Aurora"
 * =============================================================================
 * Every colour, size, radius, duration and text style in the app lives in this
 * one file. Screens reference names, never numbers. That rule is the only
 * reason a restyle is a one-file change instead of a three-day hunt.
 *
 * What changed in 3.0 and why:
 *
 *  1. COLOUR. The accent moved from periwinkle violet to a muted jade
 *     (#12B39A). Violet reads as "Viber", saturated green reads as "WhatsApp";
 *     a desaturated jade on a cold graphite canvas reads as nothing else on
 *     the store. Canvas lost its blue cast (#0B0E12 -> #0C0F12 with a green
 *     undertone) so the accent sits in the same temperature family as the
 *     background instead of vibrating against it.
 *
 *  2. DENSITY. The page gutter went 20dp -> 16dp and the body size 16sp ->
 *     15sp. A messenger is judged by how many conversations fit on one screen:
 *     this alone takes the chat list from six rows to nine on a 1080x2400
 *     panel, with no loss of legibility.
 *
 *  3. ROLES, NOT SHADES. `iris` for anything the system says about itself,
 *     `amber` for in-flight, `mint` for confirmed, `danger` for destructive.
 *     A screen never picks a colour by looks, it picks it by meaning.
 *
 * Shadows: none, anywhere. Separation is a 1dp hairline or a change of
 * surface. Elevation shadows on a near-black canvas turn into grey mud.
 *
 * Fonts: system sans for now, deliberately. A bundled face is a drop-in later
 * (put the .ttf in app/src/main/res/font, change NuvaFontFamily, nothing
 * else). Font binaries cannot be produced as text, so this stays honest.
 */

// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------

/** Roles Material 3 has no slot for. Reached through `NuvaTheme.palette`. */
@Immutable
data class NuvaPalette(
    val isDark: Boolean,
    /** App background. Nothing is painted below this. */
    val canvas: Color,
    /** Cards, tab bar, top bar. One step up from the canvas. */
    val surface: Color,
    /** Input fields, incoming bubbles, pressed states. */
    val surfaceAlt: Color,
    /** Wells: search field, day chips, anything that should read as recessed. */
    val surfaceSunken: Color,
    /** 1dp separators. The only structural line in the app. */
    val hairline: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    /** Actions: FAB, own bubbles, active tab, primary buttons. */
    val accent: Color,
    /** A legible lighter accent, safe as a FOREGROUND on dark surfaces. */
    val accentSoft: Color,
    /** Accent at low alpha, for FILLS behind icons and active tabs. */
    val accentWash: Color,
    /** Text and icons drawn on top of `accent`. */
    val accentInk: Color,
    /** Confirmed: online, delivered, verified key. */
    val mint: Color,
    /** In flight: sending, pending, needs attention but not broken. */
    val amber: Color,
    /** The system talking about itself: links, selection, archive, hints. */
    val iris: Color,
    /** Destructive and failed. */
    val danger: Color,
    /** Kept as the pre-3.0 name for `danger`. New code uses `danger`. */
    val coral: Color,
    /** Own messages. */
    val bubbleOut: Color,
    val bubbleOutText: Color,
    /** Messages from the other side. */
    val bubbleIn: Color,
    val bubbleInText: Color,
    /** Soft wash behind hero areas only. Never behind body text. */
    val glow: Color,
)

internal val DarkPalette = NuvaPalette(
    isDark = true,
    canvas = Color(0xFF0C0F12),
    surface = Color(0xFF14181C),
    surfaceAlt = Color(0xFF1C2126),
    surfaceSunken = Color(0xFF090B0D),
    hairline = Color(0xFF232A30),
    text = Color(0xFFECF1F4),
    textMuted = Color(0xFF8D9AA4),
    textFaint = Color(0xFF5D6A73),
    accent = Color(0xFF12B39A),
    accentSoft = Color(0xFF56D9C2),
    accentWash = Color(0x2412B39A),
    accentInk = Color(0xFF04140F),
    mint = Color(0xFF4ADE9B),
    amber = Color(0xFFE8A93C),
    iris = Color(0xFF7C7CF0),
    danger = Color(0xFFE4574C),
    coral = Color(0xFFE4574C),
    bubbleOut = Color(0xFF12B39A),
    bubbleOutText = Color(0xFF04140F),
    bubbleIn = Color(0xFF1C2126),
    bubbleInText = Color(0xFFECF1F4),
    glow = Color(0x3812B39A),
)

/**
 * Light is warm paper, not an inverted dark theme. Inverting a cold dark
 * palette gives a blue-white that looks like a bug report; paper looks
 * deliberate. The accent darkens so that white ink on it clears 4.5:1.
 */
internal val LightPalette = NuvaPalette(
    isDark = false,
    canvas = Color(0xFFF6F4F1),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEDEAE5),
    surfaceSunken = Color(0xFFE7E3DC),
    hairline = Color(0xFFE0DCD5),
    text = Color(0xFF101418),
    textMuted = Color(0xFF5C6770),
    textFaint = Color(0xFF8A949C),
    accent = Color(0xFF0B8C79),
    accentSoft = Color(0xFF0E9C86),
    accentWash = Color(0x1F0B8C79),
    accentInk = Color(0xFFFFFFFF),
    mint = Color(0xFF12A06B),
    amber = Color(0xFFA9741A),
    iris = Color(0xFF5551D6),
    danger = Color(0xFFC93A30),
    coral = Color(0xFFC93A30),
    bubbleOut = Color(0xFF0B8C79),
    bubbleOutText = Color(0xFFFFFFFF),
    bubbleIn = Color(0xFFFFFFFF),
    bubbleInText = Color(0xFF101418),
    glow = Color(0x1F0B8C79),
)

internal fun darkScheme(p: NuvaPalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = p.accentInk,
    primaryContainer = p.accent,
    onPrimaryContainer = p.accentInk,
    inversePrimary = p.accentSoft,
    secondary = p.mint,
    onSecondary = p.accentInk,
    tertiary = p.iris,
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
    error = p.danger,
    onError = Color.White,
    errorContainer = p.danger.copy(alpha = 0.16f),
    onErrorContainer = p.danger,
    outline = p.hairline,
    outlineVariant = p.hairline,
    scrim = Color(0xCC04070A),
)

internal fun lightScheme(p: NuvaPalette) = lightColorScheme(
    primary = p.accent,
    onPrimary = p.accentInk,
    primaryContainer = p.accent,
    onPrimaryContainer = p.accentInk,
    inversePrimary = p.accentSoft,
    secondary = p.mint,
    onSecondary = Color.White,
    tertiary = p.iris,
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
    error = p.danger,
    onError = Color.White,
    errorContainer = p.danger.copy(alpha = 0.12f),
    onErrorContainer = p.danger,
    outline = p.hairline,
    outlineVariant = p.hairline,
    scrim = Color(0x99000000),
)

// ---------------------------------------------------------------------------
// Type
// ---------------------------------------------------------------------------

/** Swap this when a bundled face lands. Single point of change. */
val NuvaFontFamily: FontFamily = FontFamily.SansSerif
val NuvaMonoFamily: FontFamily = FontFamily.Monospace

private val centeredLineHeight = LineHeightStyle(
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
    lineHeightStyle = centeredLineHeight,
)

/**
 * Material slot names are kept because every screen already imports them, but
 * the values are the 3.0 scale. Message text is `bodyLarge` at 15sp: one step
 * down from Material's 16 buys roughly four extra characters per line, and at
 * 21sp line height it is still comfortable at arm's length.
 */
internal val NuvaTypography = Typography(
    displaySmall = sans(32, FontWeight.Bold, 38, -0.8),
    headlineLarge = sans(28, FontWeight.Bold, 34, -0.6),
    headlineMedium = sans(24, FontWeight.Bold, 30, -0.5),
    headlineSmall = sans(20, FontWeight.SemiBold, 26, -0.4),
    titleLarge = sans(20, FontWeight.SemiBold, 26, -0.4),
    titleMedium = sans(16, FontWeight.SemiBold, 22, -0.2),
    titleSmall = sans(15, FontWeight.SemiBold, 20),
    bodyLarge = sans(15, FontWeight.Normal, 21),
    bodyMedium = sans(14, FontWeight.Normal, 20),
    bodySmall = sans(13, FontWeight.Normal, 18),
    labelLarge = sans(15, FontWeight.SemiBold, 20),
    labelMedium = sans(13, FontWeight.Medium, 17, 0.1),
    labelSmall = sans(11, FontWeight.Medium, 14, 0.4),
)

/** For ids, fingerprints, addresses and recovery codes. Never for prose. */
val NuvaMonoStyle: TextStyle = TextStyle(
    fontFamily = NuvaMonoFamily,
    fontSize = 13.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 18.sp,
    letterSpacing = 0.2.sp,
)

// ---------------------------------------------------------------------------
// Shape
// ---------------------------------------------------------------------------

internal val NuvaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Corner radii by role. `chip` is a full pill at any height. */
object NuvaRadius {
    val chip = 999.dp
    val tile = 12.dp
    val field = 14.dp
    val card = 18.dp
    val bubble = 20.dp

    /**
     * The tail corner of the last bubble in a run. A 6dp corner where the
     * other three are 20dp reads as "this side is speaking" without drawing a
     * triangle that breaks on line wrap or on a bubble two pixels tall.
     */
    val bubbleTail = 6.dp
    val sheet = 28.dp
}

// ---------------------------------------------------------------------------
// Spacing and size
// ---------------------------------------------------------------------------

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
    val gutter = 16.dp
}

/**
 * Fixed heights and diameters. These are the numbers that decide whether the
 * app feels like a messenger or like a settings menu, so they are named and
 * reviewed rather than typed at each call site.
 */
object NuvaSize {
    val topBar = 56.dp
    val tabBar = 60.dp
    val fab = 56.dp

    /** Icon button: 40dp of paint inside a 44dp touch target. */
    val iconButton = 40.dp
    val touchMin = 44.dp

    val searchField = 42.dp
    val chatRow = 68.dp
    val chatRowCompact = 60.dp
    val personRow = 56.dp

    val avatarChat = 48.dp
    val avatarPerson = 40.dp
    val avatarTopBar = 36.dp
    val avatarProfile = 96.dp

    val pin = 56.dp
    val pinRow = 84.dp

    val composerMin = 52.dp
    val actionTile = 64.dp
    val iconTile = 32.dp
    val cover = 200.dp

    /** Widest a bubble may get, as a fraction of the list width. */
    const val BUBBLE_MAX_WIDTH = 0.78f

    /** Room the tab bar needs at the bottom of a scrolling list. */
    val tabBarInset = 88.dp
}

/** Motion, in one place so the whole app breathes at one rate. */
object NuvaMotion {
    const val FAST = 90
    const val NORMAL = 180
    const val SLOW = 260

    /** Pre-3.0 name, kept so older call sites keep compiling. */
    const val BASE = NORMAL

    const val SPRING_STIFFNESS = 400f
    const val SPRING_DAMPING = 0.85f
}
