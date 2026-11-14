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
    val videoUrl: String,
    val season: Int = 1,
    /** Id del vídeo en Stremio (`tt123:1:2`) para addons de streams. */
    val streamId: String? = null,
    val aired: String? = null
) {
    val durationLabel: String get() = if (durationMinutes > 0) "$durationMinutes min" else ""
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
    val episodes: List<Episode> = emptyList(),
    /** Contenido remoto (Cinemeta / Nuvio / addons) — requiere resolución de streams. */
    val isRemote: Boolean = false,
    /** Nombre del addon de origen ("Cinemeta" / "Nuvio"). */
    val source: String = "",
    /** Tipo Stremio remoto ("movie"/"series") para pedir la ficha completa. */
    val remoteType: String? = null,
    /** Id Stremio del stream (`tt123:1:2` / `tmdb:123:1:2` en episodios). */
    val streamId: String? = null
) {
    val durationLabel: String
        get() = if (durationMinutes >= 60) {
            "${durationMinutes / 60} h ${durationMinutes % 60} min"
        } else {
            "$durationMinutes min"
        }

    /**
     * Hay una valoración real. `0.0` significa «desconocida» (el origen no
     * la trae): la interfaz no debe mostrar ni nota ni afinidad.
     */
    val hasRating: Boolean get() = rating > 0.0

    val ratingLabel: String
        get() = String.format(Locale.US, "%.1f", rating).replace('.', ',')

    /**
     * Porcentaje de afinidad estilo Netflix («98 % para ti»), derivado de la
     * valoración. Solo tiene sentido si [hasRating].
     */
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
