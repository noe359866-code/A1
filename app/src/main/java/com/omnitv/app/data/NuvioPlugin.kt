package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

/** Entrada de un índice Nuvio. El JS asociado NO se ejecuta en el televisor. */
data class NuvioScraper(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0.0",
    val author: String = "",
    val supportedTypes: List<String> = listOf("movie", "tv"),
    val filename: String = "providers/$id.js",
    val enabled: Boolean = true,
    val hasSettings: Boolean = false,
    val formats: List<String> = listOf("mp4", "m3u8"),
    val logo: String? = null,
    val contentLanguage: List<String> = emptyList(),
    val limited: Boolean = false
) {
    val formatsLabel: String get() = buildString {
        append(supportedTypes.joinToString("/"))
        if (formats.isNotEmpty()) append(" · ${formats.joinToString(", ")}")
    }

    fun supports(type: String): Boolean {
        val aliases = if (type == "series") setOf("series", "tv", "show") else setOf(type)
        return supportedTypes.any { it.lowercase() in aliases }
    }
}

data class NuvioPluginManifest(
    val name: String,
    val version: String,
    val scrapers: List<NuvioScraper>,
    val raw: JSONObject,
    val url: String = ""
)

data class NuvioRepoStatus(
    val url: String,
    val manifest: NuvioPluginManifest? = null,
    val error: String = ""
) {
    val label: String get() = manifest?.name ?: runCatching { java.net.URI(url).host }.getOrDefault("Nuvio")
    val summary: String get() = manifest?.let {
        "${it.scrapers.count { s -> s.enabled }} activos / ${it.scrapers.size} scrapers"
    } ?: error.ifBlank { "Pendiente de comprobar" }
}

/**
 * Gestiona índices `scrapers[]`, no los confunde con Stremio ni anuncia una
 * ejecución JS que no existe. La ejecución se delega al puente configurado.
 */
object NuvioPluginManager {
    const val TTL_MS = 5 * 60_000L
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Pair<Long, NuvioRepoStatus>>()
    val repositories = mutableStateOf<List<NuvioRepoStatus>>(emptyList())
    val refreshing = mutableStateOf(false)
    val lastError = mutableStateOf("")

    suspend fun refreshAll(force: Boolean = false): List<NuvioPluginManifest> =
        refresh(SettingsStore.nuvioPlugins, force).mapNotNull { it.manifest }

    suspend fun refresh(urls: List<String>, force: Boolean = false): List<NuvioRepoStatus> = mutex.withLock {
        refreshing.value = true
        try {
            val normalized = urls.map { it.trim() }.filter(NetPolicy::isAllowed).distinct()
            val now = System.currentTimeMillis()
            val results = coroutineScope {
                normalized.map { url ->
                    async {
                        val old = cache[url]
                        if (!force && old != null && now - old.first < TTL_MS && old.second.manifest != null) old.second
                        else {
                            val json = withContext(Dispatchers.IO) { AddonHttp.getJson(url) as? JSONObject }
                            val manifest = json?.let { parseManifest(it, url) }
                            val status = when {
                                json == null -> NuvioRepoStatus(url, old?.second?.manifest, "No se pudo descargar el índice JSON")
                                manifest == null -> NuvioRepoStatus(url, error = "No es un repositorio Nuvio válido (scrapers[])")
                                else -> NuvioRepoStatus(url, manifest)
                            }
                            cache[url] = now to status
                            status
                        }
                    }
                }.awaitAll()
            }
            cache.keys.retainAll(normalized.toSet())
            repositories.value = results
            lastError.value = results.firstOrNull { it.error.isNotEmpty() }?.let { "${it.label}: ${it.error}" }.orEmpty()
            results
        } finally {
            refreshing.value = false
        }
    }

    fun clearCache() {
        cache.clear()
        repositories.value = emptyList()
        lastError.value = ""
    }

    fun parseManifest(json: JSONObject, url: String = ""): NuvioPluginManifest? {
        val array = json.optJSONArray("scrapers") ?: return null
        val scrapers = buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                // El id acabará en una ruta HTTP del puente: nunca una ruta arbitraria.
                if (!id.matches(Regex("[A-Za-z0-9_-]+"))) continue
                add(
                    NuvioScraper(
                        id = id,
                        name = obj.optString("name", id),
                        description = obj.optString("description"),
                        version = obj.optString("version", "1.0.0"),
                        author = obj.optString("author"),
                        supportedTypes = obj.optJSONArray("supportedTypes").toStringList().ifEmpty { listOf("movie", "tv") },
                        filename = obj.optString("filename", "providers/$id.js"),
                        enabled = obj.optBoolean("enabled", true),
                        hasSettings = obj.optBoolean("hasSettings", false),
                        formats = obj.optJSONArray("formats").toStringList().ifEmpty { listOf("mp4", "m3u8") },
                        logo = obj.optString("logo").takeIf { it.isNotBlank() },
                        contentLanguage = obj.optJSONArray("contentLanguage").toStringList(),
                        limited = obj.optBoolean("limited", false)
                    )
                )
            }
        }.distinctBy { it.id }
        if (scrapers.isEmpty()) return null
        return NuvioPluginManifest(json.optString("name", "Nuvio Plugins"), json.optString("version", "1.0.0"), scrapers, json, url)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
    }
}

/** Petición de ejecución al puente; `tv` y `series` representan lo mismo. */
internal object NuvioBridgeRequest {
    fun body(
        repo: NuvioPluginManifest,
        scraper: NuvioScraper,
        type: String,
        videoId: String,
        video: Video? = null
    ): String {
        val parts = videoId.split(':')
        val tmdb = videoId.startsWith("tmdb:")
        val tmdbSubtyped = tmdb && parts.getOrNull(1)?.let { !it.all(Char::isDigit) } == true
        val tmdbValue = when {
            tmdbSubtyped -> parts.getOrNull(2)?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            tmdb -> parts.getOrNull(1)?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            else -> null
        }
        val anime = AnimeIds.isAnime(videoId)
        val animeEpisode = AnimeIds.episodeParts(videoId)
        val combinedTarget = video?.let { SkipTarget.from(it) }
        val season = when {
            anime -> animeEpisode?.first
            tmdbSubtyped -> parts.getOrNull(3)?.toIntOrNull()
            tmdb -> parts.getOrNull(2)?.toIntOrNull()
            else -> parts.getOrNull(1)?.toIntOrNull()
        } ?: combinedTarget?.season
        val episode = when {
            anime -> animeEpisode?.second
            tmdbSubtyped -> parts.getOrNull(4)?.toIntOrNull()
            tmdb -> parts.getOrNull(3)?.toIntOrNull()
            else -> parts.getOrNull(2)?.toIntOrNull()
        } ?: combinedTarget?.episode
        val imdbValue = parts.first().takeIf { it.startsWith("tt") } ?: combinedTarget?.imdbId
        val extra = JSONObject().apply {
            put("tmdbId", tmdbValue ?: combinedTarget?.tmdbId)
            put("imdbId", imdbValue)
            put("season", season)
            put("episode", episode)
        }
        return JSONObject().apply {
            put("type", if (type == "series") "tv" else type)
            put("id", videoId)
            put("repoUrl", repo.url)
            put("filename", scraper.filename)
            put("scraper", scraper.id)
            put("extra", extra)
        }.toString()
    }
}

/**
 * Puente opcional: POST /scrape/{id}. Sin URL no hay scrapers activos.
 * Se envían repositorio y archivo para distinguir ids repetidos entre repos.
 * El puente debe ejecutar el JS y devolver streams/results con URL HTTP directa.
 */
object NuvioScraperResolver {
    val lastStatus = mutableStateOf("")
    private val limiter = Semaphore(4)

    // Caché acotada (inspirada en SkyStream, lib/features/skip y nuvio_result_cache):
    // volver a un episodio ya resuelto no vuelve a lanzar todos los scrapers.
    // La clave incluye la revisión de ajustes, así que cambiar repos, puente o
    // scrapers desactivados invalida todo; TTL corto porque las URLs de los
    // plugins suelen caducar.
    private const val RESULT_TTL_MS = 3 * 60_000L
    private const val RESULT_CACHE_MAX = 24
    private val resultCache = object : LinkedHashMap<String, Pair<Long, List<StreamLink>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<StreamLink>>>?): Boolean = size > RESULT_CACHE_MAX
    }

    /** Vacía la caché de resultados (Ajustes la usan al comprobar). */
    fun clearResultCache() = synchronized(resultCache) { resultCache.clear() }

    suspend fun resolve(type: String, videoId: String, video: Video? = null, forceRefresh: Boolean = false): List<StreamLink> {
        val base = SettingsStore.nuvioBridge.trim().trimEnd('/')
        if (base.isEmpty()) {
            lastStatus.value = "Plugins Nuvio guardados: configura el puente JS para ejecutarlos"
            return emptyList()
        }
        if (!NetPolicy.isAllowed(base)) {
            lastStatus.value = "URL del puente Nuvio no permitida"
            return emptyList()
        }
        val repos = NuvioPluginManager.refreshAll().filter { it.url.isNotEmpty() }
        val disabled = SettingsStore.disabledNuvioScrapers
        val jobs = repos.flatMap { repo ->
            repo.scrapers.filter { it.enabled && it.id !in disabled && it.supports(type) }.map { repo to it }
        }
        if (jobs.isEmpty()) {
            val anySupported = repos.any { repo -> repo.scrapers.any { it.enabled && it.supports(type) } }
            lastStatus.value = if (anySupported) "Todos los scrapers Nuvio para $type están desactivados en Ajustes"
            else "Nuvio no tiene scrapers activos para $type"
            return emptyList()
        }
        val cacheKey = "$type|$videoId|${SettingsStore.revision}"
        if (!forceRefresh) {
            val cached = synchronized(resultCache) { resultCache[cacheKey] }
            if (cached != null && System.currentTimeMillis() - cached.first < RESULT_TTL_MS) {
                lastStatus.value = "${cached.second.size} enlaces del puente Nuvio (caché)"
                return cached.second
            }
        }
        val links = coroutineScope {
            jobs.map { (repo, scraper) ->
                async {
                    limiter.withPermit {
                        withTimeoutOrNull(8_000L) {
                            val body = NuvioBridgeRequest.body(repo, scraper, type, videoId, video)
                            val json = withContext(Dispatchers.IO) {
                                AddonHttp.postJson("$base/scrape/${StremioUrl.encodeSegment(scraper.id)}", body)
                            }
                            StreamResponse.parse(json, scraper.name).map {
                                it.copy(label = "${scraper.name} · ${it.label}".take(100))
                            }
                        }.orEmpty()
                    }
                }
            }.awaitAll().flatten()
        }.distinctBy { it.url }
        // Un vacío puede ser un fallo transitorio del puente: no se cachea,
        // así el siguiente intento vuelve a probar de verdad.
        if (links.isNotEmpty()) synchronized(resultCache) { resultCache[cacheKey] = System.currentTimeMillis() to links }
        lastStatus.value = if (links.isEmpty()) "El puente Nuvio no entregó enlaces para este título"
        else "${links.size} enlaces del puente Nuvio"
        return links
    }

    /** Prueba solo conectividad/protocolo, no afirma haber ejecutado ningún scraper. */
    suspend fun checkBridge(): String {
        val base = SettingsStore.nuvioBridge.trim().trimEnd('/')
        if (base.isEmpty()) return "Puente JS no configurado"
        if (!NetPolicy.isAllowed(base)) return "URL del puente no permitida"
        val json = withContext(Dispatchers.IO) { AddonHttp.getJson("$base/health") as? JSONObject }
        return if (json != null && json.optBoolean("ok", false)) {
            val count = json.optInt("scrapers", -1)
            if (count >= 0) "Puente conectado · $count scraper(s) · ejecución JS externa"
            else "Puente conectado · ejecución JS externa"
        } else {
            "El puente no respondió correctamente a /health"
        }
    }
}

/** Adaptador Nuvio para el mismo resolvedor multi-fuente que los addons Stremio. */
internal class NuvioStreamAddon : StreamAddon {
    override val id = "nuvio-plugins"
    override val name = "Plugins Nuvio"
    override suspend fun resolve(video: Video): List<StreamLink> {
        val (type, videoId) = StreamHub.stremioId(video) ?: return emptyList()
        return NuvioScraperResolver.resolve(type, videoId, video)
    }
}
