package com.omnitv.app.plugins

import com.omnitv.app.data.Video

/**
 * Contrato Provider inspirado en Streamflix Reborn — adaptado a Omni TV / Leanback.
 * Un provider puede ser scraping local (Jsoup) o delegar a addon Stremio/Nuvio.
 * Contrato heredado; la reproducción actual usa StreamHub y sus adaptadores.
 */
interface Provider {
    val id: String // ej: "cb01", "animeunity"
    val name: String
    val lang: String // "it", "es", "en", "multi"
    val baseUrl: String
    val supportsMovies: Boolean get() = true
    val supportsSeries: Boolean get() = true
    val supportsSearch: Boolean get() = true

    /** Búsqueda por título — usará Stremio `search` o Jsoup. */
    suspend fun search(query: String): List<Video>

    /** Catálogo por tipo/género/página — para Home/Descubre. */
    suspend fun getCatalog(type: String, genre: String? = null, page: Int = 1, skip: Int = 0): List<Video>

    /** Detalle (cast, episodios) por id del provider (tmdb/tt/slug). */
    suspend fun getDetail(videoId: String): Video?

    /** Servidores/embeds para un vídeo o episodio. */
    suspend fun getServers(videoId: String, episodeId: String? = null): List<Server>
}

data class Server(
    val id: String,
    val name: String, // "Mixdrop", "Streamtape", "Voe"
    val url: String, // embed url
    val quality: String = "",
    val type: String = "m3u8/mp4",
    val needsResolver: Boolean = false, // SafeGo/Uprot
    val headers: Map<String, String> = emptyMap()
)

data class ExtractedStream(
    val url: String, // mp4/m3u8 directo
    val quality: String = "HD",
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<String> = emptyList()
) {
    val isHls: Boolean get() = url.contains(".m3u8")
    val isMp4: Boolean get() = url.endsWith(".mp4", true)
}
