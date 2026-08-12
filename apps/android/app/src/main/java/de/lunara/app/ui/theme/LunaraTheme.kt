package de.lunara.app.ui.theme

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
import de.lunara.app.core.branding.BrandTokens

private val LightColors = lightColorScheme(
    primary = BrandTokens.Aubergine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2D9F1),
    onPrimaryContainer = Color(0xFF321534),
    secondary = BrandTokens.Coral,
    onSecondary = Color.White,
    tertiary = BrandTokens.Teal,
    onTertiary = Color.White,
    background = BrandTokens.Cream,
    onBackground = Color(0xFF211B20),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF211B20),
    surfaceVariant = Color(0xFFF1E8ED),
    onSurfaceVariant = Color(0xFF51464E),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE5B8E3),
    onPrimary = Color(0xFF432145),
    primaryContainer = Color(0xFF5B375D),
    onPrimaryContainer = Color(0xFFFFD7FA),
    secondary = Color(0xFFFFB4A8),
    onSecondary = Color(0xFF5E160F),
    tertiary = Color(0xFF9FD3CD),
    onTertiary = Color(0xFF003734),
    background = BrandTokens.Charcoal,
    onBackground = Color(0xFFEAE0E7),
    surface = BrandTokens.DarkSurface,
    onSurface = Color(0xFFEAE0E7),
    surfaceVariant = Color(0xFF4A4248),
    onSurfaceVariant = Color(0xFFD4C5CD),
    error = Color(0xFFFFB4AB),
)

private val LunaraTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun LunaraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LunaraTypography,
        content = content,
    )
}
