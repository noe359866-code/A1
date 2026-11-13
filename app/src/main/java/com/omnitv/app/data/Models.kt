package com.omnitv.app.data

import java.util.Locale

/** Formato del flujo de vídeo que reproduce Media3. */
enum class StreamFormat { MP4, HLS }

/**
 * Fuente alternativa del mismo contenido (multi-servidor, como Xuper TV):
 * cada título puede tener varios enlaces y el reproductor conmuta entre
 * ellos si el activo falla.
 */
data class StreamSource(
    val label: String,
    val url: String,
    val format: StreamFormat = StreamFormat.MP4
)

/**
 * Modelo de dominio de un título del catálogo de Omni TV.
 */
data class Video(
    val id: String,
    val title: String,
    val description: String,
    val backdropUrl: String,
    val cardUrl: String,
    val year: Int,
    val durationMinutes: Int,
    val rating: Double,
    val certification: String,
    val quality: String,
    val genres: List<String>,
    val videoUrl: String,
    val streamFormat: StreamFormat = StreamFormat.MP4,
    /** Enlaces alternativos (multi-servidor); vacío → se deriva de [videoUrl]. */
    val sources: List<StreamSource> = emptyList(),
    val badge: String? = null,
    /** Progreso de reproducción de 0f a 1f para la fila "Continuar viendo". */
    val progress: Float = 0f,
    val cast: List<String> = emptyList(),
    val language: String = "Español (España)"
) {
    val durationLabel: String
        get() = if (durationMinutes >= 60) {
            "${durationMinutes / 60} h ${durationMinutes % 60} min"
        } else {
            "$durationMinutes min"
        }

    val ratingLabel: String
        get() = String.format(Locale.US, "%.1f", rating).replace('.', ',')

    /** Fuentes de reproducción (al menos «Servidor 1»). */
    val playbackSources: List<StreamSource>
        get() = if (sources.isEmpty()) {
            listOf(StreamSource("Servidor 1", videoUrl, streamFormat))
        } else {
            sources
        }
}

/** Fila horizontal de la pantalla de inicio. */
data class MediaRow(
    val title: String,
    val items: List<Video>
)
