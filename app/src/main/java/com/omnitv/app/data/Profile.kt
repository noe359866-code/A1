package com.omnitv.app.data

import com.omnitv.app.R

/** Número de avatares disponibles (caras ilustradas estilo Netflix/tvOS). */
const val AVATAR_COUNT = 12

/**
 * Perfil de espectador al estilo Netflix: nombre, avatar ilustrado,
 * modo infantil, reproducción automática, progreso de "Continuar viendo" y "Mi lista".
 */
data class Profile(
    val id: String,
    val name: String,
    /** Índice del avatar (0..[AVATAR_COUNT]-1), como el selector de Netflix. */
    val avatarIndex: Int = 0,
    val isKids: Boolean = false,
    /** Reproducción automática del siguiente episodio por perfil */
    val autoPlayNext: Boolean = true,
    /** videoId → progreso (0f..1f) para la fila "Continuar viendo". */
    val progress: Map<String, Float> = emptyMap(),
    /** Ids de la lista personal "Mi lista". */
    val myList: Set<String> = emptySet()
)

/**
 * Recurso vectorial del avatar: caras ilustradas originales inspiradas en
 * los avatares clásicos de Netflix y tvOS para Android TV.
 */
fun avatarRes(index: Int): Int {
    val i = ((index % AVATAR_COUNT) + AVATAR_COUNT) % AVATAR_COUNT
    return when (i) {
        0 -> R.drawable.avatar_face_1
        1 -> R.drawable.avatar_face_2
        2 -> R.drawable.avatar_face_3
        3 -> R.drawable.avatar_face_4
        4 -> R.drawable.avatar_face_5
        5 -> R.drawable.avatar_face_6
        6 -> R.drawable.avatar_face_7
        7 -> R.drawable.avatar_face_8
        8 -> R.drawable.avatar_face_9
        9 -> R.drawable.avatar_face_10
        10 -> R.drawable.avatar_face_11
        else -> R.drawable.avatar_face_12
    }
}

/** Título descriptivo del estilo de avatar */
fun avatarLabel(index: Int): String {
    val i = ((index % AVATAR_COUNT) + AVATAR_COUNT) % AVATAR_COUNT
    return when (i) {
        0 -> "Sonrisa clásica"
        1 -> "Gran sonrisa"
        2 -> "Alegre"
        3 -> "Guiño"
        4 -> "Travieso"
        5 -> "Sorpresa"
        6 -> "Pícaro"
        7 -> "Dientes"
        8 -> "Robot"
        9 -> "Astronauta"
        10 -> "Gatito"
        else -> "Gafas de sol"
    }
}
