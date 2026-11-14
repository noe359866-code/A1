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

/**
 * Enlace de reproducción entregado por un [StreamAddon].
 */
data class StreamLink(
    val label: String,
    val url: String,
    val quality: String = "",
    val format: StreamFormat = StreamFormat.MP4
)

/**
 * Un addon de streams: su única misión es entregar enlaces de reproducción
 * (stream/{type}/{id}) de proyectos externos.
 */
interface StreamAddon {
    val id: String
    val name: String

    /**
     * @param type "movie" o "series" (convención Stremio).
     * @param videoId id Stremio del vídeo: `tt1234567`, `tmdb:123` o
     *   `tt1234567:1:2` / `tmdb:123:1:2` para episodios.
     */
    suspend fun resolve(type: String, videoId: String): List<StreamLink>
}

/**
 * Addon que habla con proyectos externos vía HTTP:
 *  1. Protocolo Stremio: `GET {base}/stream/{type}/{id}.json`
 *  2. Forma simple: `GET {base}/stream/{type}/{id}`
 */
class ProjectStreamAddon(
    private val baseUrl: String,
    override val name: String = "Proyecto externo"
) : StreamAddon {

    override val id: String = "project:$baseUrl"

    override suspend fun resolve(type: String, videoId: String): List<StreamLink> =
        withContext(Dispatchers.IO) {
            val idPath = URLEncoder.encode(videoId, "UTF-8")
            val base = baseUrl.trimEnd('/')
            val json = httpJson("$base/stream/$type/$idPath.json")
                ?: httpJson("$base/stream/$type/$idPath")
                ?: return@withContext emptyList()
            parseStreams(json)
        }

    private fun parseStreams(json: JSONObject): List<StreamLink> {
        val array = json.optJSONArray("streams") ?: json.optJSONArray("videos") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = item.optString("url").ifEmpty { item.optString("externalUrl") }
                if (url.isBlank() || !NetPolicy.isAllowed(url)) continue
                val rawName = item.optString("name", item.optString("label", name))
                val title = item.optString("title", "")
                val quality = item.optString("quality", "").ifEmpty {
                    listOf("2160p", "1080p", "720p", "480p").firstOrNull { it in "$rawName $title" } ?: ""
                }
                val label = listOf(rawName, title.lineSequence().firstOrNull().orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .take(60)
                add(
                    StreamLink(
                        label = label.ifBlank { name },
                        url = url,
                        quality = quality,
                        format = if (url.contains(".m3u8")) StreamFormat.HLS else StreamFormat.MP4
                    )
                )
            }
        }
    }

    private fun httpJson(url: String): JSONObject? =
        httpJsonDirect(url) ?: Doh.retry(url)?.let { body ->
            runCatching { JSONObject(body) }.getOrNull()
        }

    private fun httpJsonDirect(url: String): JSONObject? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "OmniTV/1.0 (Android TV)")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return null
            JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/**
 * StreamHub — orquestador de streams profesional.
 *
 * Consulta en paralelo todos los addons configurados por el usuario,
 * normaliza los resultados y los entrega al reproductor multi-servidor.
 * Si ningún addon entrega enlaces, retorna lista vacía y la UI muestra
 * un mensaje profesional (sin contenido falso).
 */
object StreamHub {

    val resolving = mutableStateOf(false)
    val lastStatus = mutableStateOf("")

    fun stremioId(video: Video): Pair<String, String>? {
        val raw = video.streamId ?: video.id
        if (video.streamId == null && '~' in raw) {
            // Episodio local sin streamId: no se puede resolver remotamente
            return null
        }
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
            lastStatus.value = "Sin addons de streams configurados — añade uno en Ajustes"
            return emptyList()
        }

        resolving.value = true
        try {
            val perAddon = coroutineScope {
                addons.map { addon ->
                    async {
                        withTimeoutOrNull(10_000L) {
                            runCatching { addon.resolve(type, id) }.getOrDefault(emptyList())
                        }.orEmpty()
                    }
                }.awaitAll()
            }

            val sources = perAddon
                .mapIndexed { index, links ->
                    val addon = addons[index]
                    links.map { link ->
                        StreamSource(
                            label = listOf(addon.name, link.label, link.quality)
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString(" · "),
                            url = link.url,
                            format = link.format
                        )
                    }
                }
                .flatten()
                .distinctBy { it.url }
                .take(24)

            lastStatus.value = if (sources.isEmpty()) {
                "Ningún addon entregó enlaces para este título"
            } else {
                "${sources.size} enlaces de ${sources.map { it.label.substringBefore(" · ") }.distinct().size} fuentes"
            }
            return sources
        } finally {
            resolving.value = false
        }
    }

    private fun configuredAddons(): List<StreamAddon> =
        SettingsStore.streamAddons.map { url ->
            ProjectStreamAddon(baseUrl = url, name = hostLabel(url))
        }

    private fun hostLabel(url: String): String = runCatching {
        URL(url).host.removePrefix("www.")
    }.getOrDefault("Proyecto externo")
}
