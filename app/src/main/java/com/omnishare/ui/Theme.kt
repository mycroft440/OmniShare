package com.omnishare.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Premium Neon Color Palette ──
val CyanNeon = Color(0xFF00E5FF)
val CyanDark = Color(0xFF00B8D4)
val CyanLight = Color(0xFF84FFFF)
val PurpleDeep = Color(0xFF7C4DFF)
val PurpleLight = Color(0xFFB388FF)
val OrangeAccent = Color(0xFFFF6D00)
val GreenSuccess = Color(0xFF00E676)
val RedError = Color(0xFFFF1744)

val DarkBackground = Color(0xFF0A0E1A)
val DarkSurface = Color(0xFF141B2D)
val DarkSurfaceVariant = Color(0xFF1C2438)
val DarkCardBorder = Color(0xFF2A3350)

private val DarkColorScheme = darkColorScheme(
    primary = CyanNeon,
    onPrimary = Color(0xFF003544),
    primaryContainer = Color(0xFF004D61),
    onPrimaryContainer = CyanLight,
    secondary = PurpleDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E1A6E),
    onSecondaryContainer = PurpleLight,
    tertiary = OrangeAccent,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF4A2800),
    onTertiaryContainer = Color(0xFFFFBD80),
    background = DarkBackground,
    onBackground = Color(0xFFE2E2F0),
    surface = DarkSurface,
    onSurface = Color(0xFFE2E2F0),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA0A8C4),
    outline = DarkCardBorder,
    outlineVariant = Color(0xFF1E2640),
    error = RedError,
    onError = Color.White,
    errorContainer = Color(0xFF5C0011),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006C84),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDE9FF),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF5C49D2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7DEFF),
    onSecondaryContainer = Color(0xFF1A0063),
    tertiary = Color(0xFFBF4E00),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FE),
    onBackground = Color(0xFF1A1C2A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C2A),
    surfaceVariant = Color(0xFFEEF0FA),
    onSurfaceVariant = Color(0xFF44475E),
    outline = Color(0xFFD0D3E5),
    outlineVariant = Color(0xFFE8EAF6),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val OmniTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W700, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W700, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W600, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W600, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W600, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W600, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W500, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

@Composable
fun OmniShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OmniTypography,
        content = content
    )
}
