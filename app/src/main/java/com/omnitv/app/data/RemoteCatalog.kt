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

        // Addons añadidos por el usuario desde el panel web del QR
        val customRows = loadCustomAddons()

        rows.clear()
        rows.addAll(loadedRows.filter { it.items.isNotEmpty() } + customRows)

        gridVideos.clear()
        gridVideos.addAll(
            (loadedRows + customRows).flatMap { it.items }
                .filter { it.type == VideoType.MOVIE }
                .distinctBy { it.id }
        )

        featured.value = rows.firstOrNull()?.items?.firstOrNull()
        loading.value = false
        loaded.value = true
        loadedRevision = SettingsStore.revision
    }

    private var loadedRevision = -1

    /** Hay que recargar (primera vez o cambio de configuración vía panel). */
    fun needsReload(): Boolean = !loaded.value || loadedRevision != SettingsStore.revision

    /**
     * Catálogos de los addons Stremio añadidos por el usuario desde el
     * panel web del QR (`SettingsStore.customAddons`).
     */
    private suspend fun loadCustomAddons(): List<MediaRow> {
        val urls = SettingsStore.customAddons
        if (urls.isEmpty()) return emptyList()
        return coroutineScope {
            urls.map { manifestUrl ->
                async {
                    val base = manifestUrl
                        .substringBefore("/manifest.json")
                        .trimEnd('/')
                    val manifest = StremioClient.fetchManifest(manifestUrl) ?: return@async emptyList()
                    val addonName = manifest.optString("name", "Addon")
                    val catalogs = manifest.optJSONArray("catalogs").toCatalogList()
                    catalogs.take(2).mapNotNull { cat ->
                        val type = cat.optString("type", "movie")
                        val id = cat.optString("id")
                        if (id.isBlank()) return@mapNotNull null
                        val metas = StremioClient.fetchCatalog(base, type, id)
                        val videos = metas.take(20).map { StremioClient.parseVideo(it, addonName) }
                        videos.forEach { CatalogRepository.registerRemote(it) }
                        if (videos.isEmpty()) null
                        else MediaRow("$addonName · ${cat.optString("name", id)}", videos)
                    }
                }
            }.awaitAll().flatten()
        }
    }

    private fun org.json.JSONArray?.toCatalogList(): List<org.json.JSONObject> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) optJSONObject(i)?.let { add(it) }
        }
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
        val base = if (video.id.startsWith("tmdb:")) {
            StremioClient.NUVIO
        } else {
            StremioClient.baseFor(video.source)
        }
        // Tipo remoto: el declarado; si se desconoce o no responde, se prueban
        // ambos (movie ↔ series) — evita fichas vacías al reabrir desde
        // «Mi lista» / «Continuar viendo» tras reiniciar la app
        val candidates = video.remoteType?.let { listOf(it) } ?: listOf("movie", "series")
        var type = candidates.first()
        var meta: org.json.JSONObject? = null
        for (candidate in candidates) {
            meta = StremioClient.fetchMeta(base, candidate, video.id)
            if (meta != null) {
                type = candidate
                break
            }
        }
        val fullMeta = meta ?: return null
        var full = StremioClient.parseVideo(fullMeta, video.source)

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

    /** Caché de catálogos por plataforma (para «Solo en» y «Plataformas»). */
    private val providerCache = java.util.concurrent.ConcurrentHashMap<Int, List<Video>>()

    /** [fetchProvider] con caché por id de plataforma. */
    suspend fun fetchProviderCached(provider: StreamingProvider): List<Video> =
        providerCache[provider.id] ?: fetchProvider(provider).also { providerCache[provider.id] = it }

    /** Caché de catálogos por estudio (sweetfox.app). */
    private val studioCache = java.util.concurrent.ConcurrentHashMap<Int, List<Video>>()

    /** Catálogo real de un estudio («Por estudio»), con caché. */
    suspend fun fetchStudio(studio: Studio): List<Video> =
        studioCache[studio.id] ?: SweetFoxClient.fetchStudio(studio.id).also { list ->
            list.forEach { CatalogRepository.registerRemote(it) }
            studioCache[studio.id] = list
        }
}
