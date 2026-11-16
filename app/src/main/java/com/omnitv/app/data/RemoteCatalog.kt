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
    /** «Sin resultados» distinto de «no se pudo consultar el catálogo». */
    val searchError = mutableStateOf("")

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
                            .filter { it.id.isNotBlank() }
                            .distinctBy { it.id }
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
            if (rows.isEmpty() && gridVideos.isEmpty()) {
                lastError.value = "No se pudo cargar el catálogo. Revisa la conexión y tus addons en Ajustes."
            }
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
                    // El nombre y la URL del addon siguen identificando la página,
                    // pero no se añaden al título que ve el usuario.
                    val title = CatalogLabels.title(catalog)
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
                            lastError.value = "No se pudieron cargar más títulos. Inténtalo de nuevo."
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
        searchError.value = ""
        if (query.isBlank()) return emptyList()
        val results = coroutineScope {
            val builtin = listOf(
                async { StremioClient.CINEMETA_NAME to StremioClient.fetchSearchPage(StremioClient.CINEMETA, "movie", "top", query) },
                async { StremioClient.CINEMETA_NAME to StremioClient.fetchSearchPage(StremioClient.CINEMETA, "series", "top", query) },
                async { StremioClient.NUVIO_NAME to StremioClient.fetchSearchPage(StremioClient.NUVIO, "movie", "popular-movies", query) }
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
            val anime = async { AnimeCatalog.search(query) }
            val pages = builtin.awaitAll()
            val customResults = custom.awaitAll().flatten()
            val animeResults = anime.await()
            if (pages.all { it.second.status !in 200..299 } && customResults.isEmpty() && animeResults.isEmpty()) {
                searchError.value = "No se pudo consultar el catálogo remoto. Revisa la conexión e inténtalo de nuevo."
            }
            val fromBuiltin = pages.flatMap { (name, page) ->
                page.metas.map { StremioClient.parseVideo(it, name) }
            }
            fromBuiltin + customResults + animeResults
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
        // Jikan/Kitsu no son addons Stremio: delegan en su cliente propio.
        if (AnimeIds.isAnime(video.id)) return AnimeCatalog.fetchDetail(video)
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
        val resolvedImdb = full.id.takeIf { it.startsWith("tt") }
        val stable = full.copy(
            id = video.id, // conservar el id con el que se navegó
            title = full.title.ifBlank { video.title },
            description = full.description.ifBlank { video.description },
            backdropUrl = full.backdropUrl.ifBlank { video.backdropUrl },
            cardUrl = full.cardUrl.ifBlank { video.cardUrl },
            streamId = full.streamId ?: resolvedImdb?.takeIf { it != video.id } ?: video.streamId
        )
        CatalogRepository.registerRemote(stable)
        return stable
    }

    /**
     * Catálogo de una plataforma («Plataformas»):
     * - Cinejoy (`cinejoy.pk/provider/{id}`),
     * - Nuvio (catálogos explícitos + descubiertos dinámicamente en su manifest),
     * - addons Stremio instalados cuyos catálogos correspondan a la plataforma,
     * - Crunchyroll (`id == 283`) respaldado por [AnimeCatalog],
     * - TMDB `/discover/{movie|tv}?with_watch_providers={id}` si el usuario
     *   configuró clave TMDB en Ajustes.
     * Todo se consulta en paralelo con timeouts individuales para evitar bloqueos.
     */
    suspend fun fetchProvider(provider: StreamingProvider): List<Video> = coroutineScope {
        val cinejoyTask = async {
            withTimeoutOrNull(12_000L) {
                runCatching { CinejoyClient.fetchProvider(provider.id) }.getOrDefault(emptyList())
            }.orEmpty()
        }

        val nuvioSpecs = linkedSetOf<Pair<String, String>>().apply {
            provider.nuvioMoviesCatalog?.let { add("movie" to it) }
            provider.nuvioSeriesCatalog?.let { add("series" to it) }
        }

        val dynamicAddonTask = async {
            val nuvioManifest = runCatching { getNuvioManifest() }.getOrNull()
            val fromNuvioManifest = if (nuvioManifest == null) {
                emptyList()
            } else {
                nuvioManifest.catalogs
                    .filter { catalogMatchesProvider(it, provider) }
                    .mapNotNull { cat ->
                        val extras = nuvioManifest.catalogExtras(cat) ?: return@mapNotNull null
                        CatalogCandidate(StremioClient.NUVIO, StremioClient.NUVIO_NAME, null, nuvioManifest, cat, extras)
                    }
            }

            val installed = runCatching { AddonRegistry.refresh() }.getOrDefault(emptyList())
            val fromInstalled = installed.filter { it.manifest != null }.flatMap { status ->
                val manifest = status.manifest!!
                manifest.catalogs
                    .filter { catalogMatchesProvider(it, provider) && manifest.supports("catalog", it.type, "") }
                    .mapNotNull { cat ->
                        val extras = manifest.catalogExtras(cat) ?: return@mapNotNull null
                        CatalogCandidate(StremioUrl.base(status.url), manifest.name, status.url, manifest, cat, extras)
                    }
            }

            (fromNuvioManifest + fromInstalled)
                .filterNot { it.base == StremioClient.NUVIO && (it.cat.type to it.cat.id) in nuvioSpecs }
                .distinctBy { "${it.base}|${it.cat.type}|${it.cat.id}" }
                .take(4)
                .map { cand ->
                    async {
                        withTimeoutOrNull(10_000L) {
                            runCatching {
                                StremioClient.fetchCatalog(cand.base, cand.cat.type, cand.cat.id, cand.extras)
                                    .map { meta ->
                                        val metaUrl = cand.addonUrl?.takeIf {
                                            cand.manifest?.supports("meta", cand.cat.type, meta.optString("id")) == true
                                        }
                                        StremioClient.parseVideo(meta, cand.sourceName, metaUrl, cand.cat.type)
                                    }
                            }.getOrDefault(emptyList())
                        }.orEmpty()
                    }
                }
                .awaitAll()
                .flatten()
        }

        val nuvioKnownTask = nuvioSpecs.map { (type, catalogId) ->
            async {
                withTimeoutOrNull(10_000L) {
                    runCatching {
                        StremioClient.fetchCatalog(StremioClient.NUVIO, type, catalogId)
                            .map { StremioClient.parseVideo(it, StremioClient.NUVIO_NAME, fallbackType = type) }
                    }.getOrDefault(emptyList())
                }.orEmpty()
            }
        }

        val animeTask = async {
            if (provider.id == 283) {
                withTimeoutOrNull(12_000L) {
                    runCatching {
                        if (AnimeCatalog.needsReload()) AnimeCatalog.refresh()
                        AnimeCatalog.catalogFor(null, "Todos")
                    }.getOrDefault(emptyList())
                }.orEmpty()
            } else emptyList()
        }

        val tmdbTask = async {
            val apiKey = tmdbApiKey() ?: return@async emptyList()
            withTimeoutOrNull(10_000L) {
                runCatching { fetchTmdbProviderCatalog(provider.id, apiKey) }.getOrDefault(emptyList())
            }.orEmpty()
        }

        val combined = buildList {
            addAll(cinejoyTask.await())
            addAll(nuvioKnownTask.awaitAll().flatten())
            addAll(dynamicAddonTask.await())
            addAll(animeTask.await())
            addAll(tmdbTask.await())
        }

        combined
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }
            .sortedByDescending { if (it.cardUrl.isNotBlank() || it.backdropUrl.isNotBlank()) 1 else 0 }
            .also { list -> list.forEach { CatalogRepository.registerRemote(it) } }
    }

    private data class CatalogCandidate(
        val base: String,
        val sourceName: String,
        val addonUrl: String?,
        val manifest: StremioManifest?,
        val cat: StremioCatalog,
        val extras: Map<String, String>
    )

    private val manifestMutex = Mutex()
    @Volatile
    private var nuvioManifestCache: StremioManifest? = null

    private suspend fun getNuvioManifest(): StremioManifest? = manifestMutex.withLock {
        nuvioManifestCache ?: withTimeoutOrNull(8_000L) {
            runCatching {
                StremioClient.fetchManifest(StremioClient.NUVIO)?.let { StremioManifestParser.parse(it) }
            }.getOrNull()
        }?.also { nuvioManifestCache = it }
    }

    private fun catalogMatchesProvider(catalog: StremioCatalog, provider: StreamingProvider): Boolean {
        val tokens = listOf(catalog.id, catalog.name)
            .flatMap { it.lowercase().split('-', '_', ' ', '.', '/', '+') }
            .filter { it.isNotBlank() }
        val aliasGroups = (provider.aliases + provider.name.lowercase())
            .map { alias -> alias.lowercase().split('-', '_', ' ', '.', '/', '+').filter { it.isNotBlank() } }
            .filter { it.isNotEmpty() }
        return aliasGroups.any { group ->
            group.size <= tokens.size && (0..(tokens.size - group.size)).any { start ->
                tokens.subList(start, start + group.size) == group
            }
        }
    }

    private fun tmdbApiKey(): String? = SettingsStore.apiKeys.entries
        .firstOrNull { (k, v) ->
            v.isNotBlank() && (k.equals("tmdb", true) || k.equals("tmdb_api_key", true))
        }?.value?.trim()

    private suspend fun fetchTmdbProviderCatalog(providerId: Int, apiKey: String): List<Video> = coroutineScope {
        val encodedKey = java.net.URLEncoder.encode(apiKey, "UTF-8")
        val endpoints = listOf("movie" to VideoType.MOVIE, "tv" to VideoType.SERIES)
        endpoints.map { (kind, videoType) ->
            async(kotlinx.coroutines.Dispatchers.IO) {
                val url = "https://api.themoviedb.org/3/discover/$kind?api_key=$encodedKey" +
                    "&with_watch_providers=$providerId&watch_region=ES&language=es-ES&sort_by=popularity.desc"
                val obj = AddonHttp.getJson(url) as? org.json.JSONObject ?: return@async emptyList()
                val arr = obj.optJSONArray("results") ?: return@async emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val tmdbId = item.optLong("id", 0L)
                        if (tmdbId <= 0L) continue
                        val poster = item.optString("poster_path", "").trim()
                        val backdrop = item.optString("backdrop_path", "").trim().ifBlank { poster }
                        val title = item.optString("title", "").ifBlank { item.optString("name", "") }
                        if (title.isBlank()) continue
                        val date = item.optString("release_date", "").ifBlank { item.optString("first_air_date", "") }
                        val year = date.take(4).toIntOrNull() ?: 0
                        val rating = item.optDouble("vote_average", 0.0).let { if (it.isNaN()) 0.0 else it }
                        add(
                            Video(
                                id = "tmdb:$tmdbId",
                                title = title,
                                description = item.optString("overview", ""),
                                backdropUrl = if (backdrop.isNotBlank()) "https://image.tmdb.org/t/p/w1280$backdrop" else "",
                                cardUrl = if (poster.isNotBlank()) "https://image.tmdb.org/t/p/w500$poster" else "",
                                year = year,
                                durationMinutes = 0,
                                rating = rating,
                                certification = "",
                                quality = "HD",
                                genres = emptyList(),
                                videoUrl = "",
                                type = if (providerId == 283) VideoType.ANIME else videoType,
                                isRemote = true,
                                source = StremioClient.NUVIO_NAME,
                                remoteType = if (kind == "movie") "movie" else "series"
                            )
                        )
                    }
                }
            }
        }.awaitAll().flatten()
    }

    /* ---------------- Caché de plataformas y estudios ------------------ */

    private const val CACHE_TTL_MS = 15 * 60 * 1000L
    private data class CacheEntry(val videos: List<Video>, val at: Long)

    private val providerCache = java.util.concurrent.ConcurrentHashMap<Int, CacheEntry>()
    private val studioCache = java.util.concurrent.ConcurrentHashMap<Int, CacheEntry>()
    private val providerLocks = java.util.concurrent.ConcurrentHashMap<Int, Mutex>()
    private val studioLocks = java.util.concurrent.ConcurrentHashMap<Int, Mutex>()

    @Volatile
    private var cacheRevision = -1

    /** Un cambio de addons/ajustes invalida todo; si no, la entrada caduca a los 15 min. */
    private fun syncCaches(revision: Int) {
        if (cacheRevision != revision) {
            providerCache.clear()
            studioCache.clear()
            cacheRevision = revision
        }
    }

    private fun java.util.concurrent.ConcurrentHashMap<Int, CacheEntry>.fresh(id: Int): List<Video>? {
        val entry = get(id) ?: return null
        return if (System.currentTimeMillis() - entry.at < CACHE_TTL_MS) entry.videos else {
            remove(id)
            null
        }
    }

    /** `getOrPut` es de Kotlin, no `computeIfAbsent` (API 24) para conservar minSdk 22. */
    private fun mutexFor(map: java.util.concurrent.ConcurrentHashMap<Int, Mutex>, id: Int): Mutex =
        synchronized(map) { map.getOrPut(id) { Mutex() } }

    /** [fetchProvider] con caché con TTL, un solo fetch por id y sin guardar errores vacíos. */
    suspend fun fetchProviderCached(provider: StreamingProvider, force: Boolean = false): List<Video> =
        mutexFor(providerLocks, provider.id).withLock {
            val revision = SettingsStore.revision
            syncCaches(revision)
            if (!force) {
                providerCache.fresh(provider.id)?.let { return@withLock it }
            } else {
                providerCache.remove(provider.id)
            }
            fetchProvider(provider).also { list ->
                if (list.isNotEmpty() && cacheRevision == revision) {
                    providerCache[provider.id] = CacheEntry(list, System.currentTimeMillis())
                }
            }
        }

    /** Catálogo real de un estudio («Por estudio»), con la misma política de caché. */
    suspend fun fetchStudio(studio: Studio, force: Boolean = false): List<Video> =
        mutexFor(studioLocks, studio.id).withLock {
            val revision = SettingsStore.revision
            syncCaches(revision)
            if (!force) {
                studioCache.fresh(studio.id)?.let { return@withLock it }
            } else {
                studioCache.remove(studio.id)
            }
            withTimeoutOrNull(14_000L) {
                runCatching { SweetFoxClient.fetchStudio(studio.id) }.getOrDefault(emptyList())
            }.orEmpty().also { list ->
                list.forEach { CatalogRepository.registerRemote(it) }
                if (list.isNotEmpty() && cacheRevision == revision) {
                    studioCache[studio.id] = CacheEntry(list, System.currentTimeMillis())
                }
            }
        }

    /** Fuerza la recarga de filas, plataformas y estudios al volver al catálogo. */
    fun invalidateCache() {
        providerCache.clear()
        studioCache.clear()
        nuvioManifestCache = null
        loadedRevision = -1
        loaded.value = false
    }
}
