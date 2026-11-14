package com.omnitv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Modelo de un scraper de Nuvio (plugin) — corresponde a una entrada de
 * `scrapers[]` en el manifest All-in-One-Nuvio.
 *
 * Cada scraper es un `providers/*.js` que Nuvio ejecuta para sacar links
 * HTTP directos (mp4/mkv/m3u8/mpd) de una web concreta.
 *
 * Ejemplo: `{"id":"hianime","name":"HiAnime","supportedTypes":["movie","tv"],"formats":["m3u8"]}`
 */
data class NuvioScraper(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val supportedTypes: List<String>,
    val filename: String,
    val enabled: Boolean,
    val hasSettings: Boolean,
    val formats: List<String>,
    val logo: String?,
    val contentLanguage: List<String>,
    val limited: Boolean = false
) {
    val isAnime: Boolean get() = name.contains("anime", ignoreCase = true) || id.contains("anime")
    val isHindi: Boolean get() = "hi" in contentLanguage
    val isFrench: Boolean get() = "fr" in contentLanguage
}

/** Manifest de repositorio de plugins Nuvio (no confundir con addon Stremio). */
data class NuvioPluginManifest(
    val name: String,
    val version: String,
    val scrapers: List<NuvioScraper>,
    val raw: JSONObject
)

/**
 * Gestor de plugins Nuvio — fetchea repos tipo All-in-One, cachea y expone
 * contadores para Ajustes y StreamHub.
 *
 * El manifest Nuvio con `scrapers[]` no es installable como addon Stremio;
 * es un índice. Cada `filename: providers/xyz.js` se ejecuta dentro de Nuvio.
 * En Omni TV lo tratamos como catálogo de fuentes: si hay un bridge local
 * (`http://192.168.x:8787/scrape/{id}`) que ejecute los JS, lo usamos.
 */
object NuvioPluginManager {

    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 12_000

    /** Cache en memoria: repoUrl -> manifest. */
    private val cache = mutableMapOf<String, NuvioPluginManifest>()

    /** Total de scrapers de todos los repos instalados (para UI). */
    val totalScrapers: Int get() = cache.values.sumOf { it.scrapers.size }

    /** Todos los scrapers agregados (para búsqueda por idioma/tipo). */
    val allScrapers: List<NuvioScraper> get() = cache.values.flatMap { it.scrapers }

    suspend fun fetchRepo(url: String): NuvioPluginManifest? = withContext(Dispatchers.IO) {
        if (!NetPolicy.isAllowed(url)) return@withContext null
        cache[url]?.let { return@withContext it }
        val json = httpJson(url) ?: return@withContext null
        val manifest = parseManifest(json) ?: return@withContext null
        cache[url] = manifest
        manifest
    }

    /** Refresca todos los repos configurados (SettingsStore.nuvioPlugins). */
    suspend fun refreshAll(): List<NuvioPluginManifest> {
        val urls = SettingsStore.nuvioPlugins
        if (urls.isEmpty()) return emptyList()
        return urls.mapNotNull { fetchRepo(it) }
    }

    fun clearCache() { cache.clear() }

    fun parseManifest(json: JSONObject): NuvioPluginManifest? {
        if (!json.has("scrapers")) return null
        val scrapersArray = json.optJSONArray("scrapers") ?: return null
        val scrapers = buildList {
            for (i in 0 until scrapersArray.length()) {
                val o = scrapersArray.optJSONObject(i) ?: continue
                val id = o.optString("id").ifBlank { continue }
                add(
                    NuvioScraper(
                        id = id,
                        name = o.optString("name", id),
                        description = o.optString("description", ""),
                        version = o.optString("version", "1.0.0"),
                        author = o.optString("author", ""),
                        supportedTypes = o.optJSONArray("supportedTypes").toStringList().ifEmpty { listOf("movie", "tv") },
                        filename = o.optString("filename", "providers/$id.js"),
                        enabled = o.optBoolean("enabled", true),
                        hasSettings = o.optBoolean("hasSettings", false),
                        formats = o.optJSONArray("formats").toStringList().ifEmpty { listOf("mp4", "m3u8") },
                        logo = o.optString("logo").takeIf { it.isNotBlank() },
                        contentLanguage = o.optJSONArray("contentLanguage").toStringList(),
                        limited = o.optBoolean("limited", false)
                    )
                )
            }
        }
        if (scrapers.isEmpty()) return null
        return NuvioPluginManifest(
            name = json.optString("name", "Nuvio Plugins"),
            version = json.optString("version", "1.0.0"),
            scrapers = scrapers,
            raw = json
        )
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
    }

    private fun httpJson(url: String): JSONObject? {
        val direct = httpJsonDirect(url)
        if (direct != null) return direct
        val byDoh = Doh.retry(url) ?: return null
        return runCatching { JSONObject(byDoh) }.getOrNull()
    }

    private fun httpJsonDirect(url: String): JSONObject? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = CONNECT_TIMEOUT
            c.readTimeout = READ_TIMEOUT
            c.setRequestProperty("User-Agent", "OmniTV/1.0 (Android TV)")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) return null
            JSONObject(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }.getOrNull()

    /** URLs de repositorios conocidos que el usuario puede añadir con 1 tap. */
    val knownRepos: List<Pair<String, String>> = listOf(
        "https://raw.githubusercontent.com/D3adlyRocket/All-in-One-Nuvio/refs/heads/main/manifest.json" to "All-in-One-Nuvio (68 scrapers)",
        "https://raw.githubusercontent.com/yoruix/nuvio-providers/refs/heads/main/manifest.json" to "Yoru Providers",
        "https://raw.githubusercontent.com/NuvioPlugin/All-in-One-Nuvio/refs/heads/main/manifest.json" to "D3adlyRocket Mirror"
    )
}

/**
 * Resolver de streams vía scrapers Nuvio — delega a un bridge local que sí
 * ejecuta los `providers/*.js`.
 *
 * El bridge esperado (opcional) expone: `POST http://192.168.x:8787/scrape/{scraperId}` con body
 * `{"type":"movie","id":"tt123","extra":{"tmdbId":"123","title":"..."}}` y
 * responde `{"streams":[{"url":"https://...","quality":"1080p","name":"HiAnime"}]}`.
 * Si no hay bridge, devuelve vacío y StreamHub sigue con Stremio.
 */
object NuvioScraperResolver {

    /** Bridge configurado — si es null, los scrapers no resuelven (solo se listan). */
    var bridgeBase: String? = null

    suspend fun resolveScraper(scraperId: String, type: String, videoId: String): List<StreamLink> {
        val base = bridgeBase?.trimEnd('/') ?: return emptyList()
        if (!NetPolicy.isAllowed(base)) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("$base/scrape/$scraperId")
                val body = org.json.JSONObject().apply {
                    put("type", type)
                    put("id", videoId)
                }.toString()
                val c = url.openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 6_000
                    c.readTimeout = 10_000
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.setRequestProperty("User-Agent", "OmniTV/1.0 (Android TV)")
                    c.outputStream.bufferedWriter().use { it.write(body) }
                    if (c.responseCode !in 200..299) return@runCatching emptyList<StreamLink>()
                    val json = JSONObject(c.inputStream.bufferedReader().readText())
                    parseStreams(json)
                } finally { c.disconnect() }
            }.getOrDefault(emptyList())
        }
    }

    private fun parseStreams(json: JSONObject): List<StreamLink> {
        val arr = json.optJSONArray("streams") ?: json.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url").ifEmpty { o.optString("link") }
                if (url.isBlank() || !NetPolicy.isAllowed(url)) continue
                add(
                    StreamLink(
                        label = o.optString("name", o.optString("title", o.optString("provider", scraperLabel(o)))),
                        url = url,
                        quality = o.optString("quality", o.optString("resolution", "")),
                        format = if (url.contains(".m3u8")) StreamFormat.HLS else StreamFormat.MP4
                    )
                )
            }
        }
    }

    private fun scraperLabel(o: JSONObject): String = o.optString("scraper", "Nuvio")
}
