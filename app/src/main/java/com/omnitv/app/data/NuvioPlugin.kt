package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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

    val totalScrapers: Int get() = repositories.value.sumOf { it.manifest?.scrapers?.size ?: 0 }
    val allScrapers: List<NuvioScraper> get() = repositories.value.flatMap { it.manifest?.scrapers.orEmpty() }

    suspend fun fetchRepo(url: String, force: Boolean = false): NuvioPluginManifest? =
        refresh(listOf(url), force).firstOrNull()?.manifest

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
    fun body(repo: NuvioPluginManifest, scraper: NuvioScraper, type: String, videoId: String): String {
        val parts = videoId.split(':')
        val tmdb = videoId.startsWith("tmdb:")
        val canonical = if (tmdb) "tmdb:${parts.getOrNull(1).orEmpty()}" else parts.first()
        val season = parts.getOrNull(if (tmdb) 2 else 1)?.toIntOrNull()
        val episode = parts.getOrNull(if (tmdb) 3 else 2)?.toIntOrNull()
        val extra = JSONObject().apply {
            put("tmdbId", canonical.takeIf { it.startsWith("tmdb:") }?.removePrefix("tmdb:"))
            put("imdbId", canonical.takeIf { it.startsWith("tt") })
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
    private val limiter = Semaphore(6)

    suspend fun resolve(type: String, videoId: String): List<StreamLink> {
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
        val jobs = repos.flatMap { repo -> repo.scrapers.filter { it.enabled && it.supports(type) }.map { repo to it } }
        if (jobs.isEmpty()) {
            lastStatus.value = "Nuvio no tiene scrapers activos para $type"
            return emptyList()
        }
        val links = coroutineScope {
            jobs.map { (repo, scraper) ->
                async {
                    limiter.withPermit {
                        val body = NuvioBridgeRequest.body(repo, scraper, type, videoId)
                        val json = withContext(Dispatchers.IO) {
                            AddonHttp.postJson("$base/scrape/${StremioUrl.encodeSegment(scraper.id)}", body)
                        }
                        StreamResponse.parse(json, scraper.name).map { it.copy(label = "${scraper.name} · ${it.label}".take(100)) }
                    }
                }
            }.awaitAll().flatten()
        }.distinctBy { it.url }
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
        return if (json != null && json.optBoolean("ok", false)) "Puente conectado · ejecución JS externa"
        else "El puente no respondió correctamente a /health"
    }
}

/** Adaptador Nuvio para el mismo resolvedor multi-fuente que los addons Stremio. */
internal class NuvioStreamAddon : StreamAddon {
    override val id = "nuvio-plugins"
    override val name = "Plugins Nuvio"
    override suspend fun resolve(type: String, videoId: String): List<StreamLink> = NuvioScraperResolver.resolve(type, videoId)
}
