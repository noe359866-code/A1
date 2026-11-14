package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class StreamLink(val label: String, val url: String, val quality: String = "", val format: StreamFormat = StreamFormat.MP4)

interface StreamAddon {
    val id: String
    val name: String
    suspend fun resolve(type: String, videoId: String): List<StreamLink>
}

/**
 * Addon Stremio que expone `stream/{type}/{id}.json` — formato estándar
 * (Torrentio, Comet, MediaFusion, AIOStreams, Easynews, Nuvio Streams…).
 */
class ProjectStreamAddon(
    private val baseUrl: String,
    override val name: String = "Proyecto externo"
) : StreamAddon {
    override val id: String = "project:$baseUrl"
    override suspend fun resolve(type: String, videoId: String): List<StreamLink> = withContext(Dispatchers.IO) {
        val idPath = URLEncoder.encode(videoId, "UTF-8")
        val base = baseUrl.trimEnd('/')
        val json = httpJson("$base/stream/$type/$idPath.json")
            ?: httpJson("$base/stream/$type/$idPath")
            ?: StremioClient.fetchStreams(base, type, videoId).let { arr ->
                if (arr.isEmpty()) return@withContext emptyList()
                JSONObject().apply { put("streams", org.json.JSONArray(arr)) }
            }.let { return@withContext parseStreams(it) }
        parseStreams(json)
    }

    private fun parseStreams(json: JSONObject): List<StreamLink> {
        val array = json.optJSONArray("streams") ?: json.optJSONArray("results") ?: json.optJSONArray("videos") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = item.optString("url").ifEmpty { item.optString("externalUrl") }.ifEmpty { item.optString("href") }
                if (url.isBlank() || !NetPolicy.isAllowed(url)) continue
                val rawName = item.optString("name", item.optString("label", name))
                val title = item.optString("title", item.optString("description", ""))
                val quality = item.optString("quality", "").ifEmpty {
                    listOf("2160p","2160","4K","1080p","1080","720p","720","480p","480").firstOrNull { it in "$rawName $title" } ?: ""
                }
                val behavior = item.optJSONObject("behaviorHints")
                val bingeGroup = behavior?.optString("bingeGroup") ?: ""
                val label = listOfNotNull(rawName.takeIf { it.isNotBlank() }, title.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }, bingeGroup.takeIf { it.isNotBlank() })
                    .distinct().joinToString(" · ").take(80)
                val fmt = when {
                    url.contains(".m3u8") || title.contains("m3u8", true) -> StreamFormat.HLS
                    url.contains(".mpd") -> StreamFormat.MP4 // DASH → MP4 fallback (ExoPlayer soporta dash si se añade extension)
                    else -> StreamFormat.MP4
                }
                add(StreamLink(label = label.ifBlank { name }, url = url, quality = quality, format = fmt))
            }
        }
    }

    private fun httpJson(url: String): JSONObject? = httpJsonDirect(url) ?: Doh.retry(url)?.let { JSONObject(it) }
    private fun httpJsonDirect(url: String): JSONObject? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 6_000; c.readTimeout = 8_000
            c.setRequestProperty("User-Agent", "OmniTV/1.0 (Android TV)")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) return null
            JSONObject(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }.getOrNull()
}

/** Bridge Nuvio scrapers → StreamAddon (via NuvioScraperResolver). */
class NuvioBridgeAddon(
    private val scraperId: String,
    private val scraperName: String
) : StreamAddon {
    override val id: String = "nuvio:$scraperId"
    override val name: String = scraperName
    override suspend fun resolve(type: String, videoId: String): List<StreamLink> =
        NuvioScraperResolver.resolveScraper(scraperId, type, videoId)
}

object StreamHub {
    val resolving = mutableStateOf(false)
    val lastStatus = mutableStateOf("")

    fun stremioId(video: Video): Pair<String, String>? {
        val raw = video.streamId ?: video.id
        if (video.streamId == null && '~' in raw) return null
        val type = when {
            video.remoteType == "series" -> "series"
            video.remoteType == "movie" -> "movie"
            video.type == VideoType.SERIES || video.type == VideoType.ANIME -> "series"
            else -> "movie"
        }
        return type to raw
    }

    suspend fun resolve(video: Video): List<StreamSource> {
        val (type, id) = stremioId(video) ?: return emptyList()
        val addons = configuredAddons()
        if (addons.isEmpty()) {
            lastStatus.value = "Sin fuentes configuradas — añade un addon de streams o un plugin Nuvio en Ajustes → Vincular"
            return emptyList()
        }
        resolving.value = true
        try {
            val perAddon = coroutineScope {
                addons.map { addon ->
                    async {
                        withTimeoutOrNull(12_000L) { runCatching { addon.resolve(type, id) }.getOrDefault(emptyList()) }.orEmpty()
                    }
                }.awaitAll()
            }
            val sources = perAddon.mapIndexed { index, links ->
                val addon = addons[index]
                links.map { link ->
                    StreamSource(label = listOf(addon.name, link.label, link.quality).filter { it.isNotBlank() }.distinct().joinToString(" · "), url = link.url, format = link.format)
                }
            }.flatten().distinctBy { it.url }.take(32)
            lastStatus.value = if (sources.isEmpty()) "Ningún origen entregó enlaces para este título" else "${sources.size} enlaces de ${sources.map { it.label.substringBefore(" · ") }.distinct().size} fuentes"
            return sources
        } finally { resolving.value = false }
    }

    private fun configuredAddons(): List<StreamAddon> {
        val stremio = SettingsStore.streamAddons.map { url -> ProjectStreamAddon(baseUrl = url, name = hostLabel(url)) }
        // customAddons que también provean stream (ej: Torrentio con catalog+stream)
        val fromCustom = SettingsStore.customAddons.mapNotNull { url ->
            val base = url.substringBefore("/manifest.json").trimEnd('/')
            if (base == url) null else ProjectStreamAddon(baseUrl = base, name = hostLabel(base) + " (cat.)")
        }
        // scrapers Nuvio si hay bridge — cada scraper habilitado como addon virtual
        val nuvio = if (NuvioScraperResolver.bridgeBase != null) {
            NuvioPluginManager.allScrapers.filter { it.enabled }.map { NuvioBridgeAddon(it.id, it.name) }
        } else emptyList()
        return stremio + fromCustom + nuvio
    }

    private fun hostLabel(url: String): String = runCatching { URL(url).host.removePrefix("www.") }.getOrDefault("Proyecto externo")
}
