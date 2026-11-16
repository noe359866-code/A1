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
val TextTertiary = Color(0xFF48484A)  // systemGray3 (adornos no textuales)
/**
 * Relleno translúcido plano (alpha 16 %) para superficies sobre contenido —
 * tabs, chips y botones secundarios. **No es un material «vidrio»**: no hay
 * blur ni vibrancy, solo un alpha sobre el fondo. Si se necesita acabado
 * «vidrio» real hace falta blur; hasta entonces léase como «translucent fill».
 */
val GlassFill = Color(0x28787880)     // fill terciario translúcido (16 %)
val GlassFillStrong = Color(0x3D787880) // fill fuerte (24 %): texto secundario ≥4,5:1 (WCAG AA)
val Danger = Color(0xFFFF453A)        // systemRed dark (acciones destructivas)
val RatingGold = Color(0xFFFFD60A)    // estrella de valoración (estilo Cinejoy/GreyBox/NovaHD)
val MedalBronze = Color(0xFFE08E45)   // medalla de bronce «Top 10» (oro/plata/bronce)

/**
 * Fondo sólido de badges con texto blanco («HOY», «KIDS»). El acento de sistema
 * (#0A84FF) con blanco da 3,65:1 (bajo WCAG AA para 15 sp); este tono más
 * profundo de la misma familia llega a 4,78:1 sin cambiar de identidad.
 */
val BadgeAccent = Color(0xFF0070E0)

/* -------------------------------------------------------------------------- */
/*  Tokens semánticos                                                         */
/* -------------------------------------------------------------------------- */

/**
 * Texto **atenuado pero legible** (avisos, pies, notas al margen).
 *
 * `TextTertiary` no sirve para texto: sobre `Background` da 2,15:1, por debajo
 * del mínimo de 4,5:1 de las pautas de accesibilidad. Este gris es `systemGray2`
 * (8,88:1 sobre el fondo).
 */
val TextMuted = Color(0xFFAEAEB2)     // systemGray2 dark

/** Acento en vivo: el rojo de «AHORA» de la guía del reproductor (fondo de chip con texto blanco, 4,8:1). */
val LiveAccent = Color(0xFFE50914)

/** Rojo vivo legible como texto sobre superficies oscuras (7,2:1); para avisos y estado en vivo. */
val LiveAccentText = Color(0xFFFF6E64)

/** Placa clara tras logos de estudio/plataforma (los logos son oscuros). */
val LogoPlate = Color(0xFFF3F5FA)

/** Naranja de aviso del sistema (equivalente a systemOrange dark). */
val WarnOrange = Color(0xFFFF9F0A)

/** Texto sobre un fondo claro (botón blanco enfocado). Casi negro, no negro puro. */
val OnLight = Color(0xFF0E0E13)

/** Superficie casi blanca de los botones primarios en reposo. */
val SurfaceBright = Color(0xFFF2F2F7)

/** Fondo de tarjeta dentro de las pantallas de ajustes (un paso sobre [Surface]). */
val SurfaceCard = Color(0xFF15151B)

/** Scrim oscuro del reproductor (paneles y overlays sobre el vídeo). */
val GlassDark = Color(0xE60E0E13)

/** Borde translúcido de los paneles del reproductor. */
val GlassBorder = Color(0x33FFFFFF)
