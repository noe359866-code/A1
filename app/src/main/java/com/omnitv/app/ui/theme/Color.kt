package com.omnitv.app.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta inspirada en tvOS y el modo oscuro de Apple:
// fondo casi negro, superficies translúcidas y un único acento azul.
val Background = Color(0xFF0B0B0F)
val Surface = Color(0xFF16161C)
val SurfaceElevated = Color(0xFF1F1F26)
val Accent = Color(0xFF0A84FF)        // systemBlue dark
val AccentGreen = Color(0xFF30D158)   // systemGreen dark
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E93) // systemGray de iOS
val TextTertiary = Color(0xFF48484A)  // systemGray3
val FocusBorder = Color(0xFFFFFFFF)
val GlassFill = Color(0x28787880)     // fill terciario translúcido
val GlassFillStrong = Color(0x4D787880)
val Danger = Color(0xFFFF453A)        // systemRed dark (acciones destructivas)

/* -------------------------------------------------------------------------- */
/*  Tokens semánticos                                                         */
/* -------------------------------------------------------------------------- */

/**
 * Texto **atenuado pero legible** (avisos, pies, notas al margen).
 *
 * `TextTertiary` no sirve para texto: sobre `Background` da 2,5:1, por debajo
 * del mínimo de 4,5:1 de las pautas de accesibilidad, y en un televisor con el
 * contraste bajo del salón desaparece. Este gris es `systemGray2` (≈ 6:1 sobre
 * el fondo) y sí se lee. `TextTertiary` se queda para adornos no textuales
 * (puntos separadores, pista del interruptor apagado).
 */
val TextMuted = Color(0xFFAEAEB2)     // systemGray2 dark

/** Verde de disponibilidad y estados correctos (alias explícito de [AccentGreen]). */
val Success = AccentGreen

/** Acento en vivo: el rojo de «AHORA» de la guía del reproductor. */
val LiveAccent = Color(0xFFE50914)

/** Texto sobre un fondo claro (botón blanco enfocado). Casi negro, no negro puro. */
val OnLight = Color(0xFF0E0E13)

/** Superficie casi blanca de los botones primarios en reposo. */
val SurfaceBright = Color(0xFFF2F2F7)

/** Fondo de tarjeta dentro de las pantallas de ajustes (un paso sobre [Surface]). */
val SurfaceCard = Color(0xFF15151B)
