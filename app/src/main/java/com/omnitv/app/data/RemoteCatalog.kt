package com.omnitv.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

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

    private val refreshMutex = Mutex()
    private val pageMutex = Mutex()
    private var catalogPages = emptyList<CatalogPageState>()
    val loadingMore = mutableStateOf(false)
    val canLoadMore = mutableStateOf(false)
    val lastError = mutableStateOf("")

    /** Página de un catálogo instalado; el offset avanza con el botón del mando. */
    private data class CatalogPageState(
        val key: String,
        val title: String,
        val base: String,
        val source: String,
        val manifestUrl: String,
        val catalog: StremioCatalog,
        val extras: Map<String, String>,
        val skip: Int,
        val hasMore: Boolean
    )

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

    /** Carga (o recarga) el catálogo remoto. Un addon caído no bloquea los demás. */
    suspend fun refresh() = refreshMutex.withLock {
        val revision = SettingsStore.revision
        loading.value = true
        lastError.value = ""
        try {
            val loadedRows = coroutineScope {
                homeRows.map { spec ->
                    async {
                        val metas = runCatching { StremioClient.fetchCatalog(spec.base, spec.type, spec.catalogId) }.getOrDefault(emptyList())
                        val videos = metas.map { StremioClient.parseVideo(it, spec.sourceName) }
                        videos.forEach { CatalogRepository.registerRemote(it) }
                        MediaRow(spec.title, videos)
                    }
                }.awaitAll()
            }
            val installed = AddonRegistry.refresh(SettingsStore.customAddons)
            val custom = loadInstalledCatalogs(installed)
            rows.clear()
            val named = loadedRows.filter { it.items.isNotEmpty() }.map { MediaRow(it.title, it.items, it.title) } +
                custom.map { MediaRow(it.title, it.videos, it.key) }
            rows.addAll(named.distinctBy { it.key })
            gridVideos.clear()
            gridVideos.addAll((loadedRows.flatMap { it.items } + custom.flatMap { it.videos })
                .filter { it.type == VideoType.MOVIE }.distinctBy { it.id })
            featured.value = rows.firstOrNull()?.items?.firstOrNull()
            loadedRevision = revision
            loaded.value = true
        } finally {
            loading.value = false
        }
    }

    private var loadedRevision = -1

    /** Hay que recargar (primera vez o cambio de configuración vía panel). */
    fun needsReload(): Boolean = !loaded.value || loadedRevision != SettingsStore.revision

    private data class LoadedCatalog(val key: String, val title: String, val videos: List<Video>, val state: CatalogPageState)

    private suspend fun loadInstalledCatalogs(installed: List<AddonStatus>): List<LoadedCatalog> = coroutineScope {
        installed.filter { it.manifest != null }.flatMap { status ->
            val manifest = status.manifest!!
            val url = status.url
            manifest.catalogs.filter { manifest.supports("catalog", it.type, "") }.map { catalog ->
                async {
                    val requested = if (catalog.supportsExtra("skip")) mapOf("skip" to "0") else emptyMap()
                    val extras = manifest.catalogExtras(catalog, requested) ?: return@async null
                    val key = "$url|${catalog.type}|${catalog.id}"
                    val title = "${manifest.name} · ${catalog.name} (${catalog.type})"
                    val base = StremioUrl.base(url)
                    val page = fetchPage(base, catalog, extras) ?: return@async null
                    val videos = page.metas.map { StremioClient.parseVideo(it, manifest.name, url.takeIf { _ -> manifest.supports("meta", catalog.type, it.optString("id")) }, catalog.type) }
                        .filter { it.id.isNotBlank() }.distinctBy { it.id }
                    videos.forEach { CatalogRepository.registerRemote(it) }
                    LoadedCatalog(key, title, videos, CatalogPageState(key, title, base, manifest.name, url, catalog, extras,
                        page.metas.size, page.hasMore && catalog.supportsExtra("skip")))
                }
            }
        }.awaitAll().filterNotNull().also { loaded ->
            catalogPages = loaded.map { it.state }
            canLoadMore.value = catalogPages.any { it.hasMore }
        }
    }

    private suspend fun fetchPage(base: String, catalog: StremioCatalog, extras: Map<String, String>): CatalogPage? =
        withTimeoutOrNull(12_000L) {
            runCatching { StremioClient.fetchCatalogPage(base, catalog.type, catalog.id, extras) }.getOrNull()
        }

    /** Siguiente página de cada catálogo que declara skip; botón visible en Películas. */
    suspend fun loadMore() = pageMutex.withLock {
        if (loadingMore.value || !canLoadMore.value) return@withLock
        loadingMore.value = true
        val revision = SettingsStore.revision
        try {
            val installed = AddonRegistry.refresh()
            val next = coroutineScope {
                catalogPages.map { page ->
                    async {
                        if (!page.hasMore) return@async page
                        val extras = page.extras + ("skip" to page.skip.toString())
                        val result = fetchPage(page.base, page.catalog, extras)
                        if (result == null) {
                            lastError.value = "No se pudo cargar más de ${page.title}"
                            return@async page.copy(hasMore = false)
                        }
                        val manifest = installed.firstOrNull { it.url == page.manifestUrl }?.manifest
                        val videos = result.metas.map {
                            StremioClient.parseVideo(it, page.source, page.manifestUrl.takeIf { _ ->
                                manifest?.supports("meta", page.catalog.type, it.optString("id")) == true
                            }, page.catalog.type)
                        }.filter { it.id.isNotBlank() }.distinctBy { it.id }
                        videos.forEach { CatalogRepository.registerRemote(it) }
                        if (revision == SettingsStore.revision) {
                            val index = rows.indexOfFirst { it.key == page.key }
                            if (index >= 0) rows[index] = rows[index].copy(items = (rows[index].items + videos).distinctBy { it.id })
                            if (page.catalog.type == "movie") {
                                val all = (gridVideos + videos).distinctBy { it.id }
                                gridVideos.clear(); gridVideos.addAll(all)
                            }
                        }
                        page.copy(skip = page.skip + result.metas.size, hasMore = result.hasMore)
                    }
                }.awaitAll()
            }
            if (revision == SettingsStore.revision) {
                catalogPages = next
                canLoadMore.value = next.any { it.hasMore }
            }
        } finally { loadingMore.value = false }
    }

    /** Cinemeta/Nuvio + solo los catálogos instalados que declaran search. */
    suspend fun search(query: String): List<Video> {
        if (query.isBlank()) return emptyList()
        val results = coroutineScope {
            val builtin = listOf(
                async { StremioClient.fetchSearch(StremioClient.CINEMETA, "movie", "top", query).map { StremioClient.parseVideo(it, StremioClient.CINEMETA_NAME) } },
                async { StremioClient.fetchSearch(StremioClient.CINEMETA, "series", "top", query).map { StremioClient.parseVideo(it, StremioClient.CINEMETA_NAME) } },
                async { StremioClient.fetchSearch(StremioClient.NUVIO, "movie", "popular-movies", query).map { StremioClient.parseVideo(it, StremioClient.NUVIO_NAME) } }
            )
            val custom = AddonRegistry.refresh().filter { it.manifest != null }.flatMap { status ->
                val manifest = status.manifest!!
                val url = status.url
                manifest.catalogs.filter { it.supportsExtra("search") && manifest.supports("catalog", it.type, "") }.map { catalog ->
                    async {
                        val extras = manifest.catalogExtras(catalog, mapOf("search" to query)) ?: return@async emptyList()
                        val metas = withTimeoutOrNull(10_000L) {
                            runCatching { StremioClient.fetchCatalog(StremioUrl.base(url), catalog.type, catalog.id, extras) }.getOrDefault(emptyList())
                        }.orEmpty()
                        metas.map { StremioClient.parseVideo(it, manifest.name, url.takeIf { _ -> manifest.supports("meta", catalog.type, it.optString("id")) }, catalog.type) }
                    }
                }
            }
            (builtin + custom).awaitAll().flatten()
        }
        return results.filter { it.id.isNotBlank() }.distinctBy { it.id }.also { list -> list.forEach { CatalogRepository.registerRemote(it) } }
    }

    /**
     * Ficha completa de un título remoto (episodios, reparto):
     * - ids `tt…` → meta de Cinemeta/Nuvio;
     * - ids `tmdb:…` (cinejoy.pk) → meta de Nuvio, que resuelve el canónico.
     * Los episodios (`videos[]`) se completan desde Cinemeta cuando existen.
     * El caché conserva tanto el id canónico como el id de navegación.
     */
    suspend fun fetchDetail(video: Video): Video? {
        val installed = AddonRegistry.refresh()
        val types = video.remoteType?.let { listOf(it) } ?: listOf("movie", "series")
        var type = types.first()
        var meta: org.json.JSONObject? = null
        var metaUrl = video.metaAddonUrl
        for (candidateType in types) {
            // Primero el addon de origen; después los que declaran meta para
            // ese tipo/prefijo. Cinemeta/Nuvio solo reciben ids que les competen.
            val compatible = installed.filter { it.manifest?.supports("meta", candidateType, video.id) == true }
                .sortedByDescending { it.url == video.metaAddonUrl }
            val sources = compatible.map { StremioUrl.base(it.url) to it.url as String? }.toMutableList()
            if (video.id.startsWith("tt") || video.id.startsWith("tmdb:")) {
                val builtin = if (video.id.startsWith("tmdb:")) StremioClient.NUVIO else StremioClient.baseFor(video.source)
                sources.add(minOf(1, sources.size), builtin to null)
            }
            for ((base, url) in sources.distinctBy { it.first }) {
                val result = StremioClient.fetchMeta(base, candidateType, video.id) ?: continue
                if (!result.has("type")) result.put("type", candidateType)
                meta = result; type = candidateType; metaUrl = url
                break
            }
            if (meta != null) break
        }
        val fullMeta = meta ?: return null
        var full = StremioClient.parseVideo(fullMeta, video.source, metaUrl)

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
