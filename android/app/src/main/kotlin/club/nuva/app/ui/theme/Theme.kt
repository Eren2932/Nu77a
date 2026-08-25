package club.nuva.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Nuva palette: deep, calm, high contrast on text. Blue accent, no gradients.
val NuvaBlue = Color(0xFF5B8CFF)
val NuvaBlueDark = Color(0xFF3A6BE0)
val NuvaInk = Color(0xFF0E1116)
val NuvaSurfaceDark = Color(0xFF161B22)
val NuvaSurfaceDarker = Color(0xFF1F2630)
val NuvaTextDark = Color(0xFFE8ECF2)
val NuvaMuted = Color(0xFF8A94A6)
val NuvaDanger = Color(0xFFE5484D)
val NuvaSuccess = Color(0xFF3DD68C)

private val DarkColors = darkColorScheme(
    primary = NuvaBlue,
    onPrimary = Color.White,
    primaryContainer = NuvaBlueDark,
    onPrimaryContainer = Color.White,
    secondary = NuvaMuted,
    background = NuvaInk,
    onBackground = NuvaTextDark,
    surface = NuvaSurfaceDark,
    onSurface = NuvaTextDark,
    surfaceVariant = NuvaSurfaceDarker,
    onSurfaceVariant = NuvaMuted,
    error = NuvaDanger,
    onError = Color.White,
    outline = Color(0xFF303845),
)

private val LightColors = lightColorScheme(
    primary = NuvaBlueDark,
    onPrimary = Color.White,
    secondary = Color(0xFF5A6478),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF12161C),
    surface = Color.White,
    onSurface = Color(0xFF12161C),
    surfaceVariant = Color(0xFFE9ECF2),
    onSurfaceVariant = Color(0xFF5A6478),
    error = NuvaDanger,
    onError = Color.White,
    outline = Color(0xFFD3D8E0),
)

private val NuvaTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun NuvaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NuvaTypography,
        content = content,
    )
}
