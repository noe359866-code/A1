package com.omnitv.app.data

import com.omnitv.app.R

/** Número de avatares disponibles (caras ilustradas estilo Netflix). */
const val AVATAR_COUNT = 8

/**
 * Perfil de espectador al estilo Netflix: nombre, avatar ilustrado,
 * modo infantil, progreso de "Continuar viendo" y "Mi lista".
 */
data class Profile(
    val id: String,
    val name: String,
    /** Índice del avatar (0..[AVATAR_COUNT]-1), como el selector de Netflix. */
    val avatarIndex: Int = 0,
    val isKids: Boolean = false,
    /** videoId → progreso (0f..1f) para la fila "Continuar viendo". */
    val progress: Map<String, Float> = emptyMap(),
    /** Ids de la lista personal "Mi lista". */
    val myList: Set<String> = emptySet()
)

/**
 * Recurso vectorial del avatar: caras ilustradas originales inspiradas en
 * los avatares clásicos de Netflix (sonrisa, gran sonrisa, ojos cerrados,
 * lengua, pícara, sorprendida, guiño y sonrisa con dientes).
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
        else -> R.drawable.avatar_face_8
    }
}
