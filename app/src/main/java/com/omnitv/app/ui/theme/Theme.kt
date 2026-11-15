package com.omnitv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Tema de Omni TV: modo oscuro inspirado en tvOS y diseñado para Android TV (10 pies).
 */
@Composable
fun OmniTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = Background,
            onBackground = TextPrimary,
            surface = Surface,
            onSurface = TextPrimary,
            surfaceVariant = SurfaceElevated,
            onSurfaceVariant = TextSecondary
        ),
        typography = Typography(
            displayLarge = AppType.hero,
            headlineLarge = AppType.screenTitle,
            headlineMedium = AppType.sectionTitle,
            titleLarge = AppType.cardHeading,
            titleMedium = AppType.cardTitle,
            bodyLarge = AppType.body,
            bodyMedium = AppType.metadata,
            labelLarge = AppType.button,
            labelMedium = AppType.chip,
            labelSmall = AppType.chip
        ),
        shapes = Shapes(
            extraSmall = AppRadius.xs,
            small = AppRadius.sm,
            medium = AppRadius.md,
            large = AppRadius.lg,
            extraLarge = AppRadius.pill
        ),
        content = content
    )
}
