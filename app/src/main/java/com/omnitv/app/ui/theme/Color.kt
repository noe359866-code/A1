package com.omnitv.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ────────────────────────────────────────────────────────────────────────────
//  Omni TV · Sistema de color — inspirado en tvOS / Material You dark
//  Un fondo casi negro, superficies estratificadas y un único acento azul.
//  Todo el contraste verifica WCAG AA (≥4.5:1) a 3m de distancia.
// ────────────────────────────────────────────────────────────────────────────

val Background = Color(0xFF0B0B0F)
val BackgroundSoft = Color(0xFF111116)
val Surface = Color(0xFF16161C)
val SurfaceElevated = Color(0xFF1E1E26)
val SurfaceHighlight = Color(0xFF252530)
val SurfaceCard = Color(0xFF15151B) // tarjeta en Ajustes

val Accent = Color(0xFF0A84FF)          // systemBlue dark
val AccentHover = Color(0xFF409CFF)
val AccentPressed = Color(0xFF0066CC)
val AccentGreen = Color(0xFF30D158)     // systemGreen dark
val AccentAmber = Color(0xFFFF9F0A)
val Danger = Color(0xFFFF453A)         // systemRed dark

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E93)  // systemGray
val TextTertiary = Color(0xFF48484A)   // systemGray3 — solo adornos, nunca texto
val TextMuted = Color(0xFFAEAEB2)      // systemGray2 dark — texto legible 6:1

val FocusBorder = Color.White
val GlassFill = Color(0x28787880)
val GlassFillStrong = Color(0x4D787880)
val GlassBorder = Color(0x14FFFFFF)

val Success = AccentGreen
val LiveAccent = Color(0xFFE50914)
val OnLight = Color(0xFF0E0E13)
val SurfaceBright = Color(0xFFF2F2F7)
val Scrim = Color(0x99000000)

// ────────────────────────────────────────────────────────────────────────────
//  Gradientes de marca — usados en héroe, velos y banners
// ────────────────────────────────────────────────────────────────────────────
val GradientHero: Brush
    get() = Brush.verticalGradient(
        0.00f to Background.copy(alpha = 0.70f),
        0.35f to Background.copy(alpha = 0.10f),
        0.70f to Background.copy(alpha = 0.85f),
        1.00f to Background
    )
val GradientHeroHorizontal: Brush
    get() = Brush.horizontalGradient(
        0.00f to Background.copy(alpha = 0.92f),
        0.45f to Background.copy(alpha = 0.55f),
        0.75f to Color.Transparent
    )
val GradientTopBar: Brush
    get() = Brush.verticalGradient(
        0.00f to Background.copy(alpha = 0.92f),
        1.00f to Color.Transparent
    )
val GradientAccent: Brush
    get() = Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)))
