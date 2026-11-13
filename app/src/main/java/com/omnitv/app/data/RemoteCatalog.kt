package com.omnitv.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Estado observable del catálogo real (Cinemeta + Nuvio).
 *
 * Carga filas para la portada, un listado completo para la rejilla de
 * Películas y búsqueda remota. Todo el contenido registrado queda también
 * en el caché de [CatalogRepository] para resolver `find()` por id.
 */
object RemoteCatalog {

    /** Filas remotas de la portada (en orden de aparición). */
    val rows = mutableStateListOf<MediaRow>()

    /** Catálogo completo (rejilla de Películas), sin duplicados. */
    val gridVideos = mutableStateListOf<Video>()

    /** Título destacado para el héroe. */
    val featured = mutableStateOf<Video?>(null)

    /** Se está cargando el catálogo. */
    val loading = mutableStateOf(true)

    /** Ya se intentó cargar alguna vez (para no repetir). */
    val loaded = mutableStateOf(false)

    /** Selección curada: nombre visible → addon, tipo y catálogo. */
    private data class RowSpec(
        val title: String,
        val base: String,
        val sourceName: String,
        val type: String,
        val catalogId: String
    )

    private val homeRows = listOf(
        RowSpec("Películas populares", StremioClient.CINEMETA, StremioClient.CINEMETA_NAME, "movie", "top"),
        RowSpec("Series populares", StremioClient.CINEMETA, StremioClient.CINEMETA_NAME, "series", "top"),
        RowSpec("Tendencias hoy", StremioClient.NUVIO, StremioClient.NUVIO_NAME, "movie", "trending-movies"),
        RowSpec("Mejor valoradas", StremioClient.CINEMETA, StremioClient.CINEMETA_NAME, "movie", "imdbRating"),
        RowSpec("Series destacadas", StremioClient.CINEMETA, StremioClient.CINEMETA_NAME, "series", "imdbRating"),
        RowSpec("En Netflix", StremioClient.NUVIO, StremioClient.NUVIO_NAME, "movie", "netflix-movies"),
        RowSpec("Series Disney+", StremioClient.NUVIO, StremioClient.NUVIO_NAME, "series", "disney-plus-series"),
        RowSpec("Documentales", StremioClient.NUVIO, StremioClient.NUVIO_NAME, "movie", "documentaries"),
        RowSpec("En familia", StremioClient.NUVIO, StremioClient.NUVIO_NAME, "movie", "family-movies")
    )

    /** Carga (o recarga) el catálogo remoto. Idempotente y en paralelo. */
    suspend fun refresh() {
        loading.value = true
        val loadedRows = coroutineScope {
            homeRows.map { spec ->
                async {
                    val metas = StremioClient.fetchCatalog(spec.base, spec.type, spec.catalogId)
                    val videos = metas.map { StremioClient.parseVideo(it, spec.sourceName) }
                    videos.forEach { CatalogRepository.registerRemote(it) }
                    MediaRow(spec.title, videos)
                }
            }.awaitAll()
        }

        rows.clear()
        rows.addAll(loadedRows.filter { it.items.isNotEmpty() })

        gridVideos.clear()
        gridVideos.addAll(
            loadedRows.flatMap { it.items }
                .filter { it.type == VideoType.MOVIE }
                .distinctBy { it.id }
        )

        featured.value = rows.firstOrNull()?.items?.firstOrNull()
        loading.value = false
        loaded.value = true
    }

    /** Búsqueda remota en Cinemeta (películas y series) + Nuvio. */
    suspend fun search(query: String): List<Video> {
        if (query.isBlank()) return emptyList()
        val results = coroutineScope {
            listOf(
                async { StremioClient.fetchSearch(StremioClient.CINEMETA, "movie", "top", query) },
                async { StremioClient.fetchSearch(StremioClient.CINEMETA, "series", "top", query) },
                async { StremioClient.fetchSearch(StremioClient.NUVIO, "movie", "popular-movies", query) }
            ).awaitAll().flatten()
        }
        return results
            .map { StremioClient.parseVideo(it, if (it.optString("id").startsWith("tt")) StremioClient.CINEMETA_NAME else StremioClient.NUVIO_NAME) }
            .distinctBy { it.id }
            .also { list -> list.forEach { CatalogRepository.registerRemote(it) } }
    }

    /**
     * Ficha completa de un título remoto (episodios, reparto) desde Cinemeta
     * o Nuvio según el origen; actualiza el caché.
     */
    suspend fun fetchDetail(video: Video): Video? {
        val base = StremioClient.baseFor(video.source)
        val type = video.remoteType ?: return null
        val meta = StremioClient.fetchMeta(base, type, video.id) ?: return null
        val full = StremioClient.parseVideo(meta, video.source)
        CatalogRepository.registerRemote(full)
        return full
    }
}
