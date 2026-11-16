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
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<String> = emptyList()
)

/**
 * Un addon de streams: su ÚNICA misión es **entregar enlaces de
 * reproducción** (stream/{type}/{id}) de proyectos externos.
 */
interface StreamAddon {
    val id: String
    val name: String

    /**
     * Recibe el título completo para que los puentes que buscan por nombre
     * (Cloudstream) no pierdan título, año ni episodio.
     */
    suspend fun resolve(video: Video): List<StreamLink>
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

    override suspend fun resolve(video: Video): List<StreamLink> {
        val (type, videoId) = StreamHub.stremioId(video) ?: return emptyList()
        var links = resolve(type, videoId)
        val alternate = AnimeIds.alternateType(type)
        if (links.isEmpty() && alternate != null &&
            (AnimeIds.isAnime(videoId) || (video.type == VideoType.ANIME && manifest?.supports("stream", alternate, videoId) == true))
        ) {
            // Algunos addons de anime anuncian `type: anime` en lugar de `series`.
            links = resolve(alternate, videoId)
        }
        if (links.isEmpty() && AnimeIds.isAnime(video.id)) {
            val target = SkipTarget.from(video)
            val nativeBase = video.id.substringBefore('~')
            val nativeId = if (target?.isEpisode == true) {
                "$nativeBase:${target.season}:${target.episode}"
            } else {
                nativeBase
            }
            if (nativeId != videoId) {
                if (manifest == null || manifest.supports("stream", type, nativeId)) {
                    links = resolve(type, nativeId)
                }
                if (links.isEmpty() && alternate != null &&
                    (manifest == null || manifest.supports("stream", alternate, nativeId))
                ) {
                    links = resolve(alternate, nativeId)
                }
            }
        }
        return links
    }

    suspend fun resolve(type: String, videoId: String): List<StreamLink> = withContext(Dispatchers.IO) {
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
     * Misma URL entregada por varios addons: conserva la variante con mejor
     * calidad detectada. Etiqueta, cabeceras y subtítulos se toman de la
     * PRIMERA aparición (la etiqueta puede llevar nombre de addon útil); el
     * `quality` resulta el MÁXIMO visto. Así el ranking no puntúa a ciegas por
     * una primera aparición pobre, y las cabeceras necesarias para reproducir
     * nunca se descartan.
     */
    internal fun dedupeByUrlBestQuality(sources: List<StreamSource>): List<StreamSource> {
        val merged = LinkedHashMap<String, StreamSource>()
        for (source in sources) {
            val existing = merged[source.url]
            if (existing == null) {
                merged[source.url] = source
                continue
            }
            val better = StreamRanker.scoreText(source.quality.ifBlank { source.label }) >
                StreamRanker.scoreText(existing.quality.ifBlank { existing.label })
            if (better && source.quality.isNotBlank()) {
                merged[source.url] = existing.copy(quality = source.quality)
            }
        }
        return merged.values.toList()
    }

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
                        val timeoutMs = if (addon.id == "cloudstream-bridge" || addon.id == "nuvio-plugins") 25_000L else 12_000L
                        withTimeoutOrNull(timeoutMs) {
                            runCatching { addon.resolve(video) }.getOrDefault(emptyList())
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
                            headers = link.headers,
                            subtitles = link.subtitles,
                            quality = link.quality
                        )
                    }
                }
                .flatten()
                .let(::dedupeByUrlBestQuality)
            // La mejor calidad primero; con «Calidad preferida» concreta, esa
            // resolución encabeza la lista (ver StreamRanker).
            val ranked = StreamRanker.sort(sources, SettingsStore.preferredQuality).take(24)


            lastStatus.value = if (ranked.isEmpty()) {
                when {
                    addons.any { it.id == "cloudstream-bridge" } -> CloudstreamBridge.status.value
                    addons.any { it.id == "nuvio-plugins" } -> NuvioScraperResolver.lastStatus.value
                        .ifBlank { "Ningún addon entregó enlaces para este título" }
                    else -> "Ningún addon entregó enlaces para este título"
                }
            } else {
                "${ranked.size} enlaces de ${ranked.map { it.label.substringBefore(" · ") }.distinct().size} addons"
            }
            return ranked
        } finally {
            resolving.value = false
        }
    }

    /** Descubre streams tanto en addons Stremio instalados como en proyectos simples. */
    internal suspend fun configuredAddons(
        manifests: List<AddonStatus>? = null,
        projects: List<String> = SettingsStore.streamAddons,
        plugins: List<String> = SettingsStore.nuvioPlugins,
        cloudstreamBridge: String = SettingsStore.cloudstreamBridge
    ): List<StreamAddon> {
        val addons = mutableListOf<StreamAddon>()
        val bases = mutableSetOf<String>()
        (manifests ?: AddonRegistry.refresh()).filter { it.manifest?.isStreamAddon() == true }.forEach { status ->
            val base = StremioUrl.base(status.url)
            if (bases.add(base)) addons += ProjectStreamAddon(base, status.label, status.manifest)
        }
        projects.forEach { url ->
            val base = StremioUrl.base(url).trimEnd('/')
            if (NetPolicy.isAllowed(base) && bases.add(base)) addons += ProjectStreamAddon(base, hostLabel(base))
        }
        if (plugins.isNotEmpty()) addons += NuvioStreamAddon()
        if (CloudstreamBridge.normalize(cloudstreamBridge) != null) addons += CloudstreamStreamAddon()
        return addons
    }

    private fun hostLabel(url: String): String = runCatching {
        URL(url).host.removePrefix("www.")
    }.getOrDefault("Proyecto externo")
}
