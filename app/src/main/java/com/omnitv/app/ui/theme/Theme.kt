package com.omnitv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AppColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003A75),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = AccentGreen,
    onSecondary = Background,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = Danger,
    onError = Color.White,
    outline = GlassBorder,
    outlineVariant = Color.White.copy(alpha = 0.06f),
    scrim = Scrim,
    inverseSurface = SurfaceBright,
    inverseOnSurface = OnLight
)

private val AppTypography = Typography(
    displayLarge = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 54.sp, 60.sp, (-1).sp),
    headlineLarge = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 40.sp, 46.sp, (-0.6).sp),
    titleLarge = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 27.sp, 33.sp),
    titleMedium = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 18.sp, 23.sp),
    bodyLarge = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 21.sp, 29.sp),
    labelLarge = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 21.sp),
    labelSmall = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 15.sp, letterSpacing = 1.sp)
)

/**
 * Tema Omni TV — oscuro puro, acento único, sin sorpresas.
 * Material 3 solo como base; los componentes con foco son propios
 * para control total del anillo, escala y sombra a 3m.
 */
@Composable
fun OmniTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}
