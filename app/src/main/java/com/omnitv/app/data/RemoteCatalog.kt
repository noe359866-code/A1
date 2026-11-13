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
     * Ficha completa de un título remoto (episodios, reparto):
     * - ids `tt…` → meta de Cinemeta/Nuvio;
     * - ids `tmdb:…` (cinejoy.pk) → meta de Nuvio, que resuelve el canónico.
     * Los episodios (`videos[]`) se completan desde Cinemeta cuando existen.
     * El caché conserva tanto el id canónico como el id de navegación.
     */
    suspend fun fetchDetail(video: Video): Video? {
        val type = video.remoteType ?: return null
        val base = if (video.id.startsWith("tmdb:")) {
            StremioClient.NUVIO
        } else {
            StremioClient.baseFor(video.source)
        }
        val meta = StremioClient.fetchMeta(base, type, video.id) ?: return null
        var full = StremioClient.parseVideo(meta, video.source)

        // Episodios reales desde Cinemeta (su meta trae videos[] por temporada)
        if (full.episodes.isEmpty() && full.id.startsWith("tt")) {
            StremioClient.fetchMeta(StremioClient.CINEMETA, type, full.id)?.let { cinemetaMeta ->
                val enriched = StremioClient.parseVideo(cinemetaMeta, full.source)
                if (enriched.episodes.isNotEmpty()) {
                    full = full.copy(
                        episodes = enriched.episodes,
                        cast = full.cast.ifEmpty { enriched.cast }
                    )
                }
            }
        }

        CatalogRepository.registerRemote(full)
        val stable = full.copy(id = video.id) // conservar el id con el que se navegó
        CatalogRepository.registerRemote(stable)
        return stable
    }

    /**
     * Catálogo de una plataforma («Plataformas»): títulos reales de
     * cinejoy.pk + catálogos Nuvio de la plataforma (Netflix, Disney+,
     * Prime Video) cuando existen.
     */
    suspend fun fetchProvider(provider: StreamingProvider): List<Video> {
        val scraped = CinejoyClient.fetchProvider(provider.id)
        val nuvio = coroutineScope {
            listOfNotNull(provider.nuvioMoviesCatalog, provider.nuvioSeriesCatalog)
                .map { catalog ->
                    async {
                        val type = if ("series" in catalog) "series" else "movie"
                        StremioClient.fetchCatalog(StremioClient.NUVIO, type, catalog)
                            .map { StremioClient.parseVideo(it, StremioClient.NUVIO_NAME) }
                    }
                }
                .awaitAll()
                .flatten()
        }
        return (scraped + nuvio)
            .distinctBy { it.id }
            .also { list -> list.forEach { CatalogRepository.registerRemote(it) } }
    }
}
