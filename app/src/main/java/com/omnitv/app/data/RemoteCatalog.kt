package com.omnitv.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object RemoteCatalog {

    val rows = mutableStateListOf<MediaRow>()
    val gridVideos = mutableStateListOf<Video>()
    val featured = mutableStateOf<Video?>(null)
    val loading = mutableStateOf(true)
    val loaded = mutableStateOf(false)

    private data class RowSpec(val title: String, val base: String, val sourceName: String, val type: String, val catalogId: String)

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
        val customRows = loadCustomAddons()
        // Nuvio plugins no aportan catálogo (solo streams), pero se refresca cache de scrapers
        runCatching { NuvioPluginManager.refreshAll() }

        rows.clear()
        rows.addAll(loadedRows.filter { it.items.isNotEmpty() } + customRows)
        gridVideos.clear()
        gridVideos.addAll((loadedRows + customRows).flatMap { it.items }.filter { it.type == VideoType.MOVIE }.distinctBy { it.id })
        featured.value = rows.firstOrNull()?.items?.firstOrNull()
        loading.value = false
        loaded.value = true
        loadedRevision = SettingsStore.revision
    }

    private var loadedRevision = -1
    fun needsReload(): Boolean = !loaded.value || loadedRevision != SettingsStore.revision

    /** Catálogos de addons Stremio personalizados — soporta manifest flexible. */
    private suspend fun loadCustomAddons(): List<MediaRow> {
        val urls = SettingsStore.customAddons
        if (urls.isEmpty()) return emptyList()
        return coroutineScope {
            urls.map { manifestUrl ->
                async {
                    val base = manifestUrl.substringBefore("/manifest.json").trimEnd('/')
                    val raw = StremioClient.fetchManifest(manifestUrl) ?: return@async emptyList()
                    val manifest = runCatching { StremioManifestParser.parse(raw) }.getOrNull()
                    if (manifest != null) {
                        // Usar parser robusto: respeta extraRequired y tipos
                        val addonName = manifest.name
                        manifest.catalogs.filter { it.type in setOf("movie","series") }.take(3).mapNotNull { cat ->
                            if (!manifest.supportsType(cat.type)) return@mapNotNull null
                            val metas = StremioClient.fetchCatalog(base, cat.type, cat.id)
                            val videos = metas.take(20).map { StremioClient.parseVideo(it, addonName) }
                            videos.forEach { CatalogRepository.registerRemote(it) }
                            if (videos.isEmpty()) null else MediaRow("$addonName · ${cat.name}", videos)
                        }
                    } else {
                        // Fallback legacy
                        val addonName = raw.optString("name", "Addon")
                        val catalogs = raw.optJSONArray("catalogs").toCatalogList()
                        catalogs.take(2).mapNotNull { cat ->
                            val type = cat.optString("type", "movie"); val id = cat.optString("id")
                            if (id.isBlank()) return@mapNotNull null
                            val metas = StremioClient.fetchCatalog(base, type, id)
                            val videos = metas.take(20).map { StremioClient.parseVideo(it, addonName) }
                            videos.forEach { CatalogRepository.registerRemote(it) }
                            if (videos.isEmpty()) null else MediaRow("$addonName · ${cat.optString("name", id)}", videos)
                        }
                    }
                }
            }.awaitAll().flatten()
        }
    }

    private fun org.json.JSONArray?.toCatalogList(): List<org.json.JSONObject> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) optJSONObject(i)?.let { add(it) } }
    }

    /** Búsqueda remota — ahora también en addons custom con `search` extra. */
    suspend fun search(query: String): List<Video> {
        if (query.isBlank()) return emptyList()
        val baseResults = coroutineScope {
            listOf(
                async { StremioClient.fetchSearch(StremioClient.CINEMETA, "movie", "top", query) },
                async { StremioClient.fetchSearch(StremioClient.CINEMETA, "series", "top", query) },
                async { StremioClient.fetchSearch(StremioClient.NUVIO, "movie", "popular-movies", query) }
            ).awaitAll().flatten()
        }
        val customResults = coroutineScope {
            SettingsStore.customAddons.map { url ->
                async {
                    val base = url.substringBefore("/manifest.json").trimEnd('/')
                    val raw = StremioClient.fetchManifest(url) ?: return@async emptyList<org.json.JSONObject>()
                    val manifest = runCatching { StremioManifestParser.parse(raw) }.getOrNull() ?: return@async emptyList()
                    val searchable = manifest.catalogs.filter { it.supportsExtra("search") }
                    if (searchable.isEmpty()) return@async emptyList()
                    val cat = searchable.first()
                    StremioClient.fetchSearch(base, cat.type, cat.id, query)
                }
            }.awaitAll().flatten()
        }
        return (baseResults + customResults)
            .map { StremioClient.parseVideo(it, if (it.optString("id").startsWith("tt")) StremioClient.CINEMETA_NAME else StremioClient.NUVIO_NAME) }
            .distinctBy { it.id }
            .also { list -> list.forEach { CatalogRepository.registerRemote(it) } }
    }

    suspend fun fetchDetail(video: Video): Video? {
        val base = if (video.id.startsWith("tmdb:")) StremioClient.NUVIO else StremioClient.baseFor(video.source)
        val candidates = video.remoteType?.let { listOf(it) } ?: listOf("movie", "series")
        var type = candidates.first()
        var meta: org.json.JSONObject? = null
        for (candidate in candidates) {
            meta = StremioClient.fetchMeta(base, candidate, video.id)
            if (meta != null) { type = candidate; break }
        }
        val fullMeta = meta ?: return null
        var full = StremioClient.parseVideo(fullMeta, video.source)
        if (full.episodes.isEmpty() && full.id.startsWith("tt")) {
            StremioClient.fetchMeta(StremioClient.CINEMETA, type, full.id)?.let { cinemetaMeta ->
                val enriched = StremioClient.parseVideo(cinemetaMeta, full.source)
                if (enriched.episodes.isNotEmpty()) full = full.copy(episodes = enriched.episodes, cast = full.cast.ifEmpty { enriched.cast })
            }
        }
        CatalogRepository.registerRemote(full)
        val stable = full.copy(id = video.id)
        CatalogRepository.registerRemote(stable)
        return stable
    }

    suspend fun fetchProvider(provider: StreamingProvider): List<Video> {
        val scraped = CinejoyClient.fetchProvider(provider.id)
        val nuvio = coroutineScope {
            listOfNotNull(provider.nuvioMoviesCatalog, provider.nuvioSeriesCatalog)
                .map { catalog ->
                    async {
                        val type = if ("series" in catalog) "series" else "movie"
                        StremioClient.fetchCatalog(StremioClient.NUVIO, type, catalog).map { StremioClient.parseVideo(it, StremioClient.NUVIO_NAME) }
                    }
                }.awaitAll().flatten()
        }
        return (scraped + nuvio).distinctBy { it.id }.also { list -> list.forEach { CatalogRepository.registerRemote(it) } }
    }

    private val providerCache = java.util.concurrent.ConcurrentHashMap<Int, List<Video>>()
    suspend fun fetchProviderCached(provider: StreamingProvider): List<Video> =
        providerCache[provider.id] ?: fetchProvider(provider).also { providerCache[provider.id] = it }

    private val studioCache = java.util.concurrent.ConcurrentHashMap<Int, List<Video>>()
    suspend fun fetchStudio(studio: Studio): List<Video> =
        studioCache[studio.id] ?: SweetFoxClient.fetchStudio(studio.id).also { list ->
            list.forEach { CatalogRepository.registerRemote(it) }
            studioCache[studio.id] = list
        }
}
