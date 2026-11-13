package com.omnitv.app.data

import java.util.Locale

/** Formato del flujo de vídeo que reproduce Media3. */
enum class StreamFormat { MP4, HLS }

/** Tipo de contenido, como la clasificación de Netflix. */
enum class VideoType(val label: String) {
    MOVIE("Película"),
    SERIES("Serie"),
    ANIME("Anime")
}

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

/** Episodio de una serie o anime. */
data class Episode(
    val id: String,
    val title: String,
    val number: Int,
    val durationMinutes: Int,
    val description: String,
    val thumbnailUrl: String,
    val videoUrl: String
) {
    val durationLabel: String get() = "$durationMinutes min"
}

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
    val language: String = "Español (España)",
    /** Película, serie o anime. */
    val type: VideoType = VideoType.MOVIE,
    /** Episodios (series y anime). */
    val episodes: List<Episode> = emptyList()
) {
    val durationLabel: String
        get() = if (durationMinutes >= 60) {
            "${durationMinutes / 60} h ${durationMinutes % 60} min"
        } else {
            "$durationMinutes min"
        }

    val ratingLabel: String
        get() = String.format(Locale.US, "%.1f", rating).replace('.', ',')

    /** Porcentaje de afinidad estilo Netflix («98 % para ti»). */
    val matchPercent: Int
        get() = (55 + rating * 5).toInt().coerceAtMost(99)

    /** Etiqueta de temporadas/episodios para series y anime. */
    val episodesLabel: String?
        get() = if (episodes.isEmpty()) null else "1 temporada · ${episodes.size} episodios"

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
