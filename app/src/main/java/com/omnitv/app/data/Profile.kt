package com.omnitv.app.data

import androidx.compose.ui.graphics.Color

/**
 * Perfil de espectador al estilo Netflix: nombre, avatar de color,
 * modo infantil, progreso de "Continuar viendo" y "Mi lista".
 */
data class Profile(
    val id: String,
    val name: String,
    val colorIndex: Int = 0,
    val isKids: Boolean = false,
    /** videoId → progreso (0f..1f) para la fila "Continuar viendo". */
    val progress: Map<String, Float> = emptyMap(),
    /** Ids de la lista personal "Mi lista". */
    val myList: Set<String> = emptySet()
) {
    /** Inicial del nombre para el avatar. */
    val initial: String
        get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

/** Paleta de avatares estilo Netflix (un color por perfil). */
val AvatarPalette: List<Color> = listOf(
    Color(0xFFE50914), // rojo Netflix
    Color(0xFFFF9500), // naranja
    Color(0xFFFFD60A), // amarillo
    Color(0xFF30D158), // verde
    Color(0xFF00C2A8), // turquesa
    Color(0xFF0A84FF), // azul
    Color(0xFF7C5CFF), // violeta
    Color(0xFFFF375F)  // rosa
)

fun avatarColor(index: Int): Color {
    val size = AvatarPalette.size
    return AvatarPalette[((index % size) + size) % size]
}
