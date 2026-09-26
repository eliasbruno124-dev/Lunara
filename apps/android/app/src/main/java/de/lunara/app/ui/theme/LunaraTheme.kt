package de.lunara.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.lunara.app.core.branding.BrandTokens

private val LightColors = lightColorScheme(
    primary = BrandTokens.Petrol,
    onPrimary = BrandTokens.Cream,
    primaryContainer = BrandTokens.Sage,
    onPrimaryContainer = BrandTokens.Petrol,
    secondary = Color(0xFF8D4738),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E1D7),
    onSecondaryContainer = Color(0xFF653124),
    tertiary = BrandTokens.Teal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6EBE1),
    onTertiaryContainer = Color(0xFF2F493F),
    background = BrandTokens.Cream,
    onBackground = BrandTokens.Petrol,
    surface = Color(0xFFFCFAF5),
    onSurface = BrandTokens.Petrol,
    surfaceVariant = Color(0xFFEAE8DE),
    onSurfaceVariant = Color(0xFF54615E),
    surfaceTint = BrandTokens.Petrol,
    surfaceDim = Color(0xFFDEDCD3),
    surfaceBright = Color(0xFFFCFAF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2EFE7),
    surfaceContainer = Color(0xFFEDEAE2),
    surfaceContainerHigh = Color(0xFFE7E5DC),
    surfaceContainerHighest = Color(0xFFE2E0D7),
    outline = Color(0xFF77817B),
    outlineVariant = Color(0xFFD1D6CC),
    inverseSurface = BrandTokens.Petrol,
    inverseOnSurface = BrandTokens.Cream,
    inversePrimary = Color(0xFFB5D4C8),
    error = Color(0xFFA53232),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF762020),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5D4C8),
    onPrimary = Color(0xFF173A34),
    primaryContainer = Color(0xFF2A4541),
    onPrimaryContainer = Color(0xFFDAE8DF),
    secondary = Color(0xFFF0AF99),
    onSecondary = Color(0xFF53291F),
    secondaryContainer = Color(0xFF533C33),
    onSecondaryContainer = Color(0xFFF7DED2),
    tertiary = Color(0xFFC4D1B9),
    onTertiary = Color(0xFF2D4028),
    tertiaryContainer = Color(0xFF344339),
    onTertiaryContainer = Color(0xFFDCE7D4),
    background = BrandTokens.Charcoal,
    onBackground = Color(0xFFF0EFE5),
    surface = BrandTokens.DarkSurface,
    onSurface = Color(0xFFF0EFE5),
    surfaceVariant = Color(0xFF2D3B3B),
    onSurfaceVariant = Color(0xFFBCC9C1),
    surfaceTint = Color(0xFFB5D4C8),
    surfaceDim = BrandTokens.Charcoal,
    surfaceBright = Color(0xFF354344),
    surfaceContainerLowest = Color(0xFF0C1718),
    surfaceContainerLow = Color(0xFF162325),
    surfaceContainer = BrandTokens.DarkSurface,
    surfaceContainerHigh = Color(0xFF223233),
    surfaceContainerHighest = Color(0xFF2D3B3B),
    outline = Color(0xFF86958E),
    outlineVariant = Color(0xFF3F504B),
    inverseSurface = BrandTokens.Cream,
    inverseOnSurface = BrandTokens.Petrol,
    inversePrimary = BrandTokens.Petrol,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF73322E),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val LunaraTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 56.sp, lineHeight = 62.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 46.sp, lineHeight = 52.sp, letterSpacing = (-0.6).sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
)

private val LunaraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LunaraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LunaraTypography,
        shapes = LunaraShapes,
        content = content,
    )
}
