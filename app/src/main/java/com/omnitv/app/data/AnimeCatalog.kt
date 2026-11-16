package com.omnitv.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Catálogo **solo anime** alimentado por Jikan y Kitsu.
 *
 * - Filas curadas y búsqueda combinada de las dos APIs.
 * - Caché de 15 min para búsquedas/fichas, invalidada por
 *   [SettingsStore.revision].
 * - Un proveedor caído no vacía el otro: se informa con [lastError].
 * - Todo título se registra en [CatalogRepository] para ficha, búsqueda y
 *   navegación. Los episodios se completan al abrir la ficha.
 *
 * Jikan/Kitsu son metadatos: la reproducción depende de los addons/bridges
 * configurados. Cuando Jikan aporta un id IMDb se usa como `streamId`.
 */
object AnimeCatalog {

    val SOURCES = listOf("Todos", AnimeIds.SOURCE_JIKAN, AnimeIds.SOURCE_KITSU)

    /** Filas curadas, en orden de aparición. */
    val rows = mutableStateListOf<MediaRow>()

    /** Catálogo completo, sin duplicados, para la rejilla «Todos los títulos». */
    val videos = mutableStateListOf<Video>()

    val loading = mutableStateOf(false)
    val loaded = mutableStateOf(false)
    val loadingMore = mutableStateOf(false)
    val canLoadMore = mutableStateOf(false)
    val lastError = mutableStateOf("")

    private const val TTL_MS = 15 * 60 * 1000L
    private val refreshMutex = Mutex()
    private val pageMutex = Mutex()
    private val searchMutex = Mutex()
    private val detailMutex = Mutex()

    private var loadedRevision = -1
    private var nextJikanPage = 2
    private var nextKitsuOffset = 20

    private data class CacheEntry<T>(val value: T, val at: Long)
    private val searchCache = ConcurrentHashMap<String, CacheEntry<List<Video>>>()
    private val detailCache = ConcurrentHashMap<String, CacheEntry<Video>>()

    @Volatile
    private var cacheRevision = -1

    private data class Section(
        val key: String,
        val title: String,
        val provider: String,
        val page: AnimePage
    )

    fun needsReload(): Boolean = !loaded.value || loadedRevision != SettingsStore.revision

    /** Carga inicial o recarga tras un cambio de ajustes/addons. */
    suspend fun refresh(force: Boolean = false) = refreshMutex.withLock {
        if (!force && !needsReload()) return@withLock
        val revision = SettingsStore.revision
        loading.value = true
        lastError.value = ""
        try {
            val sections = coroutineScope {
                listOf(
                    async {
                        Section(
                            "jikan-top", "Mejor valorados · Jikan", AnimeIds.SOURCE_JIKAN,
                            withTimeoutOrNull(8_000L) { JikanClient.top(1) } ?: AnimePage()
                        )
                    },
                    async {
                        Section(
                            "jikan-now", "En emisión · Jikan", AnimeIds.SOURCE_JIKAN,
                            withTimeoutOrNull(8_000L) { JikanClient.seasonNow(1) } ?: AnimePage()
                        )
                    },
                    async {
                        Section(
                            "kitsu-trending", "Tendencias · Kitsu", AnimeIds.SOURCE_KITSU,
                            withTimeoutOrNull(10_000L) { KitsuClient.trending() } ?: AnimePage()
                        )
                    },
                    async {
                        Section(
                            "kitsu-top", "Mejor valoradas · Kitsu", AnimeIds.SOURCE_KITSU,
                            withTimeoutOrNull(10_000L) { KitsuClient.topRated(0) } ?: AnimePage()
                        )
                    }
                ).awaitAll()
            }
            val all = sections.flatMap { it.page.videos }.distinctBy { it.id }
            all.forEach { CatalogRepository.registerRemote(it) }
            if (revision == SettingsStore.revision) {
                rows.clear()
                rows.addAll(
                    sections.filter { it.page.videos.isNotEmpty() }
                        .map {
                            MediaRow(
                                it.title,
                                it.page.videos.filter { v -> v.id.isNotBlank() }.distinctBy { v -> v.id },
                                it.key
                            )
                        }
                )
                videos.clear()
                videos.addAll(all)
                loaded.value = true
                loadedRevision = revision
                nextJikanPage = 2
                nextKitsuOffset = 20
                val jikanPages = sections.filter { it.provider == AnimeIds.SOURCE_JIKAN }.map { it.page }
                val kitsuPages = sections.filter { it.provider == AnimeIds.SOURCE_KITSU }.map { it.page }
                val jikanOk = jikanPages.any { it.ok }
                val kitsuOk = kitsuPages.any { it.ok }
                canLoadMore.value = jikanOk || kitsuOk
                lastError.value = when {
                    jikanOk && kitsuOk -> ""
                    !jikanOk && !kitsuOk ->
                        "No se pudo cargar el catálogo de anime (Jikan: ${statusLabel(jikanPages)}; Kitsu: ${statusLabel(kitsuPages)}). Revisa la conexión."
                    !jikanOk -> "Jikan no respondió (${statusLabel(jikanPages)}); se muestran los títulos de Kitsu."
                    else -> "Kitsu no respondió (${statusLabel(kitsuPages)}); se muestran los títulos de Jikan."
                }
            }
        } finally {
            loading.value = false
        }
    }

    /** Página siguiente de Jikan *top* y Kitsu *mejor valoradas*. */
    suspend fun loadMore() = pageMutex.withLock {
        if (loadingMore.value || !canLoadMore.value) return@withLock
        loadingMore.value = true
        val revision = SettingsStore.revision
        try {
            val jikan = withTimeoutOrNull(8_000L) { JikanClient.top(nextJikanPage) } ?: AnimePage()
            val kitsu = withTimeoutOrNull(10_000L) { KitsuClient.topRated(nextKitsuOffset) } ?: AnimePage()
            val known = videos.mapTo(HashSet<String>()) { it.id }
            val fresh = (jikan.videos + kitsu.videos).distinctBy { it.id }.filter { it.id !in known }
            fresh.forEach { CatalogRepository.registerRemote(it) }
            if (revision == SettingsStore.revision) {
                appendToRow("jikan-top", jikan.videos)
                appendToRow("kitsu-top", kitsu.videos)
                videos.addAll(fresh)
                nextJikanPage++
                nextKitsuOffset += 20
                canLoadMore.value = (jikan.videos.isNotEmpty() || kitsu.videos.isNotEmpty()) &&
                    nextJikanPage <= 4 && nextKitsuOffset <= 80
                if (!jikan.ok && !kitsu.ok) {
                    lastError.value = "No se pudieron cargar más títulos de anime. Inténtalo de nuevo."
                }
            }
        } finally {
            loadingMore.value = false
        }
    }

    /** Búsqueda combinada, con caché por consulta normalizada. */
    suspend fun search(query: String): List<Video> = searchMutex.withLock {
        val key = SearchText.normalize(query)
        if (key.isEmpty()) return@withLock emptyList()
        val revision = SettingsStore.revision
        syncCaches(revision)
        searchCache.fresh(key)?.let { return@withLock it }

        val (jikan, kitsu) = coroutineScope {
            val a = async { withTimeoutOrNull(5_000L) { JikanClient.search(query) } ?: AnimePage() }
            val b = async { withTimeoutOrNull(8_000L) { KitsuClient.search(query) } ?: AnimePage() }
            a.await() to b.await()
        }
        val results = (jikan.videos + kitsu.videos).distinctBy { it.id }
        results.forEach { CatalogRepository.registerRemote(it) }
        // Un fallo total de red no se cachea: el reintento debe poder recuperarse.
        val confirmed = jikan.status in 200..299 || kitsu.status in 200..299
        if (cacheRevision == revision && (results.isNotEmpty() || confirmed)) {
            searchCache[key] = CacheEntry(results, System.currentTimeMillis())
        }
        return@withLock results
    }

    /**
     * Ficha enriquecida con episodios reales. Jikan usa `/full` para leer un
     * id IMDb externo y `/episodes`; Kitsu usa `/anime/{id}/episodes`.
     */
    suspend fun fetchDetail(video: Video): Video? = detailMutex.withLock {
        val providerId = AnimeIds.providerId(video.id) ?: return@withLock null
        val revision = SettingsStore.revision
        syncCaches(revision)
        detailCache.fresh(video.id)?.let { return@withLock it }

        val full = if (video.id.startsWith(AnimeIds.KITSU)) {
            fetchKitsuDetail(video, providerId)
        } else {
            fetchJikanDetail(video, providerId)
        }
        if (full != null) {
            CatalogRepository.registerRemote(full)
            if (cacheRevision == revision) {
                detailCache[video.id] = CacheEntry(full, System.currentTimeMillis())
            }
        }
        return@withLock full
    }

    private suspend fun fetchJikanDetail(video: Video, malId: String): Video? {
        val meta = withTimeoutOrNull(6_000L) { JikanClient.full(malId) } ?: return null
        val base = JikanClient.parseAnime(meta) ?: return null
        val imdb = JikanClient.imdbId(meta)
        val prefix = imdb ?: base.id
        val first = withTimeoutOrNull(6_000L) {
            JikanClient.episodes(malId, 1, prefix, base.durationMinutes, base.cardUrl)
        } ?: AnimeEpisodePage()
        val second = if (first.hasMore) {
            withTimeoutOrNull(6_000L) {
                JikanClient.episodes(malId, 2, prefix, base.durationMinutes, base.cardUrl)
            } ?: AnimeEpisodePage()
        } else {
            AnimeEpisodePage()
        }
        val episodes = (first.episodes + second.episodes).distinctBy { it.number }.sortedBy { it.number }
        return base.copy(
            id = video.id,
            title = base.title.ifBlank { video.title },
            description = base.description.ifBlank { video.description },
            backdropUrl = base.backdropUrl.ifBlank { video.backdropUrl },
            cardUrl = base.cardUrl.ifBlank { video.cardUrl },
            episodes = episodes,
            streamId = imdb
        )
    }

    private suspend fun fetchKitsuDetail(video: Video, kitsuId: String): Video? {
        val meta = withTimeoutOrNull(8_000L) { KitsuClient.anime(kitsuId) } ?: return null
        val base = KitsuClient.parseSingle(meta) ?: return null
        val first = withTimeoutOrNull(8_000L) {
            KitsuClient.episodes(kitsuId, 0, base.id, base.cardUrl)
        } ?: AnimeEpisodePage()
        val second = if (first.hasMore) {
            withTimeoutOrNull(8_000L) {
                KitsuClient.episodes(kitsuId, 20, base.id, base.cardUrl)
            } ?: AnimeEpisodePage()
        } else {
            AnimeEpisodePage()
        }
        val episodes = (first.episodes + second.episodes)
            .distinctBy { "${it.season}:${it.number}" }
            .sortedWith(compareBy<Episode> { it.season }.thenBy { it.number })
        return base.copy(
            id = video.id,
            title = base.title.ifBlank { video.title },
            description = base.description.ifBlank { video.description },
            backdropUrl = base.backdropUrl.ifBlank { video.backdropUrl },
            cardUrl = base.cardUrl.ifBlank { video.cardUrl },
            episodes = episodes
        )
    }

    /** Filas visibles para el perfil y el filtro de fuente elegido. */
    fun rowsFor(profile: Profile?, source: String): List<MediaRow> = rows.map { row ->
        row.copy(items = row.items.filter { matches(it, profile, source) })
    }.filter { it.items.isNotEmpty() }

    /** Rejilla completa, con el mismo filtro que las filas. */
    fun catalogFor(profile: Profile?, source: String): List<Video> =
        videos.filter { matches(it, profile, source) }.distinctBy { it.id }

    fun visible(video: Video, profile: Profile?): Boolean = AnimeAgeRating.visible(video, profile)

    private fun matches(video: Video, profile: Profile?, source: String): Boolean =
        visible(video, profile) && (source == "Todos" || video.source == source)

    private fun appendToRow(key: String, items: List<Video>) {
        val clean = items.filter { it.id.isNotBlank() }.distinctBy { it.id }
        if (clean.isEmpty()) return
        val index = rows.indexOfFirst { it.key == key }
        if (index >= 0) {
            rows[index] = rows[index].copy(items = (rows[index].items + clean).distinctBy { it.id })
        } else {
            rows.add(MediaRow("Más anime · ${AnimeIds.source(clean.first().id).orEmpty()}", clean, key))
        }
    }

    private fun syncCaches(revision: Int) {
        if (cacheRevision != revision) {
            searchCache.clear()
            detailCache.clear()
            cacheRevision = revision
        }
    }

    private fun <T> ConcurrentHashMap<String, CacheEntry<T>>.fresh(key: String): T? {
        val entry = get(key) ?: return null
        return if (System.currentTimeMillis() - entry.at < TTL_MS) entry.value else {
            remove(key)
            null
        }
    }

    private fun statusLabel(pages: List<AnimePage>): String = when {
        pages.any { it.status == 429 } -> "límite de peticiones (429)"
        pages.all { it.status == 0 } -> "sin respuesta"
        pages.all { it.status in 200..299 } -> "sin títulos"
        else -> pages.map { it.status }.filter { it > 0 }.distinct().joinToString("/")
    }

    /** Limpia las cachés de anime y fuerza recarga al abrir la pestaña Anime. */
    fun invalidateCache() {
        searchCache.clear()
        detailCache.clear()
        loadedRevision = -1
        loaded.value = false
    }
}
