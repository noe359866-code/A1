package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URL

/**
 * Enlace de reproducción entregado por un [StreamAddon].
 */
data class StreamLink(
    val label: String,
    val url: String,
    val quality: String = "",
    val format: StreamFormat = StreamFormat.MP4,
    val headers: Map<String, String> = emptyMap()
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
    baseUrl: String,
    override val name: String = "Proyecto externo",
    private val manifest: StremioManifest? = null
) : StreamAddon {
    private val base = if (manifest != null) StremioUrl.base(baseUrl) else baseUrl.trimEnd('/')
    override val id = "project:$base"

    override suspend fun resolve(type: String, videoId: String): List<StreamLink> = withContext(Dispatchers.IO) {
        if (manifest?.supports("stream", type, videoId) == false) return@withContext emptyList()
        val standard = StremioUrl.resource(base, "stream", type, videoId)
        // La alternativa sin .json solo aplica a proyectos simples, no a un
        // manifest Stremio que ya declara su contrato.
        val json = AddonHttp.getJson(standard)
            ?: if (manifest == null) AddonHttp.getJson(standard.removeSuffix(".json")) else null
        parseStreams(json)
    }

    internal fun parseStreams(json: Any?): List<StreamLink> = StreamResponse.parse(json, name)
}

/**
 * «Omni Streams» — el addon interno de la app. Su única función es
 * **entregar streams de otros proyectos**: consulta en paralelo todos los
 * proyectos configurados (StreamFlix Reborn y afines vía
 * [ProjectStreamAddon], addons Stremio de streams, etc.), normaliza los
 * resultados y los pasa al reproductor multi-servidor. Si nadie entrega
 * enlaces, la app muestra un error sin sustituir el título.
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
        resolving.value = true
        try {
            val addons = configuredAddons()
            if (addons.isEmpty()) {
                lastStatus.value = if (SettingsStore.nuvioPlugins.isNotEmpty()) NuvioScraperResolver.lastStatus.value
                    .ifBlank { "Configura el puente JS en Ajustes → Plugins Nuvio" }
                else "Sin addons de streams configurados"
                return emptyList()
            }
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
                            format = link.format,
                            headers = link.headers
                        )
                    }
                }
                .flatten()
                .distinctBy { it.url }
                .take(24)

            lastStatus.value = if (sources.isEmpty()) {
                if (addons.any { it.id == "nuvio-plugins" }) NuvioScraperResolver.lastStatus.value
                    .ifBlank { "Ningún addon entregó enlaces para este título" }
                else "Ningún addon entregó enlaces para este título"
            } else {
                "${sources.size} enlaces de ${sources.map { it.label.substringBefore(" · ") }.distinct().size} addons"
            }
            return sources
        } finally {
            resolving.value = false
        }
    }

    /** Descubre streams tanto en addons Stremio instalados como en proyectos simples. */
    internal suspend fun configuredAddons(
        manifests: List<AddonStatus> = AddonRegistry.refresh(),
        projects: List<String> = SettingsStore.streamAddons,
        plugins: List<String> = SettingsStore.nuvioPlugins
    ): List<StreamAddon> {
        val addons = mutableListOf<StreamAddon>()
        val bases = mutableSetOf<String>()
        manifests.filter { it.manifest?.isStreamAddon() == true }.forEach { status ->
            val base = StremioUrl.base(status.url)
            if (bases.add(base)) addons += ProjectStreamAddon(base, status.label, status.manifest)
        }
        projects.forEach { url ->
            val base = StremioUrl.base(url).trimEnd('/')
            if (NetPolicy.isAllowed(base) && bases.add(base)) addons += ProjectStreamAddon(base, hostLabel(base))
        }
        if (plugins.isNotEmpty()) addons += NuvioStreamAddon()
        return addons
    }

    private fun hostLabel(url: String): String = runCatching {
        URL(url).host.removePrefix("www.")
    }.getOrDefault("Proyecto externo")
}
