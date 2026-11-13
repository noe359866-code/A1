package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
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
 * Un addon de streams: su ÚNICA misión es **entregar enlaces de
 * reproducción** (stream/{type}/{id}) de proyectos externos.
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
 * Addon que habla con proyectos externos (StreamFlix Reborn y afines):
 * cualquier proyecto puede entregar streams a Omni TV exponiendo un
 * endpoint HTTP con una de las dos formas soportadas (ver
 * `docs/STREAM-ADDON-PROTOCOL.md`):
 *
 * 1. **Protocolo Stremio**: `GET {base}/stream/{type}/{id}.json`
 *    → `{ "streams": [ { "name", "title", "url" | "infoHash" | "ytId" } ] }`
 * 2. **Forma simple**: `GET {base}/stream/{type}/{id}`
 *    → `{ "streams": [ { "name" | "label", "url", "quality" } ] }` o un
 *    array directo con el mismo contenido.
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
            // Forma Stremio primero; si no responde, forma simple
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
                // Sin url (infoHash/torrent) no hay soporte; y los enlaces deben cumplir la
                // política de red: https, o http solo dentro de la red local
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

    /**
     * Descarga [url] como JSON. Primero por el DNS normal; si el nombre no
     * resuelve, reintenta con el DNS de Cloudflare ([Doh]).
     */
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

/** Addon interno de demostración: los streams públicos de prueba. */
object DemoStreamAddon : StreamAddon {
    override val id = "demo"
    override val name = "Demo Omni TV"

    private val demo = listOf(
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
    )

    override suspend fun resolve(type: String, videoId: String): List<StreamLink> {
        val pick = demo[(videoId.hashCode().mod(demo.size))]
        return listOf(StreamLink(label = "Demo · Servidor 1", url = pick))
    }
}

/**
 * «Omni Streams» — el addon interno de la app. Su única función es
 * **entregar streams de otros proyectos**: consulta en paralelo todos los
 * proyectos configurados (StreamFlix Reborn y afines vía
 * [ProjectStreamAddon], addons Stremio de streams, etc.), normaliza los
 * resultados y los pasa al reproductor multi-servidor. Si nadie entrega
 * enlaces, el reproductor cae al flujo demo etiquetado.
 */
object StreamHub {

    /** Se está resolviendo la lista de enlaces. */
    val resolving = mutableStateOf(false)

    /** Estado de la última resolución (para la UI). */
    val lastStatus = mutableStateOf("")

    /**
     * Id Stremio del vídeo (tipo, id). Los episodios usan su `streamId`
     * (`tt123:1:2` / `tmdb:123:1:2`); los episodios locales (demo) no se
     * resuelven remotamente.
     */
    fun stremioId(video: Video): Pair<String, String>? {
        val raw = video.streamId ?: video.id
        if (video.streamId == null && '~' in raw) return null // episodio local demo
        val type = when {
            video.remoteType == "series" -> "series"
            video.remoteType == "movie" -> "movie"
            video.type == VideoType.SERIES || video.type == VideoType.ANIME -> "series"
            else -> "movie"
        }
        return type to raw
    }

    /**
     * Resuelve los enlaces de un título consultando todos los addons
     * configurados (en paralelo y con tiempo límite). Devuelve los
     * [StreamSource] listos para el reproductor (multi-servidor).
     */
    suspend fun resolve(video: Video): List<StreamSource> {
        val (type, id) = stremioId(video) ?: return emptyList()
        val addons = configuredAddons()
        if (addons.isEmpty()) {
            lastStatus.value = "Sin addons de streams configurados"
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
                "${sources.size} enlaces de ${sources.map { it.label.substringBefore(" · ") }.distinct().size} addons"
            }
            return sources
        } finally {
            resolving.value = false
        }
    }

    /** Addons activos: los proyectos configurados por el usuario. */
    private fun configuredAddons(): List<StreamAddon> =
        SettingsStore.streamAddons.map { url ->
            ProjectStreamAddon(
                baseUrl = url,
                name = hostLabel(url)
            )
        }

    private fun hostLabel(url: String): String = runCatching {
        URL(url).host.removePrefix("www.")
    }.getOrDefault("Proyecto externo")
}
