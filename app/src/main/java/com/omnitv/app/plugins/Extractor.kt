package com.omnitv.app.plugins

import com.omnitv.app.data.NetPolicy
import java.net.URI

/**
 * Resultado de resolver un embed a un enlace directo que Media3 puede leer.
 * Antes vivía en el contrato `Provider` heredado; ahora es el único modelo
 * que usan los extractores y el adaptador de [com.omnitv.app.data.StreamHub].
 */
data class ExtractedStream(
    val url: String,
    val quality: String = "HD",
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<String> = emptyList()
) {
    val isHls: Boolean get() = url.contains(".m3u8", true)
    val isMp4: Boolean get() = url.endsWith(".mp4", true)
}

/**
 * Extractor — resuelve un embed (mixdrop, streamtape, dood…) a URL directo.
 * La comparación se hace sobre el **host real**, no sobre la cadena completa:
 * `https://mixdrop.co.attacker.example/e/1` no debe engañar al registro.
 */
interface Extractor {
    val name: String
    val domains: List<String>

    fun canHandle(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase()?.trimEnd('.') }.getOrNull() ?: return false
        if (host.isEmpty()) return false
        return domains.any { domain ->
            val d = domain.lowercase().trimEnd('.')
            host == d || host.endsWith(".$d")
        }
    }

    suspend fun extract(url: String): ExtractedStream?
}

/** Registro global de extractores — orden = prioridad (el específico primero). */
object ExtractorRegistry {
    private const val CACHE_SIZE = 128

    private val extractors = mutableListOf<Extractor>()
    private val cache = object : LinkedHashMap<String, ExtractedStream>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExtractedStream>?) = size > CACHE_SIZE
    }

    @Synchronized
    fun register(e: Extractor) {
        if (extractors.none { it.name == e.name }) extractors.add(e)
    }

    @Synchronized
    fun unregister(name: String) {
        extractors.removeAll { it.name == name }
    }

    @Synchronized
    fun all(): List<Extractor> = extractors.toList()

    @Synchronized
    fun findFor(url: String): Extractor? = extractors.firstOrNull { it.canHandle(url) }

    @Synchronized
    fun clearCache() {
        cache.clear()
    }

    /**
     * Intenta el extractor del host y, si falla, el resto de extractores que
     * acepten el dominio. Cachea solo resultados válidos según [NetPolicy].
     * [extract] es inyectable para probar el registro sin red.
     */
    suspend fun resolve(
        url: String,
        extract: suspend (Extractor, String) -> ExtractedStream? = { e, u -> e.extract(u) }
    ): ExtractedStream? {
        cached(url)?.let { return it }
        val specific = findFor(url) ?: return null
        val candidates = buildList {
            add(specific)
            addAll(all().filter { it !== specific && it.canHandle(url) })
        }
        for (e in candidates) {
            val result = runCatching { extract(e, url) }.getOrNull() ?: continue
            if (result.url.isNotBlank() && NetPolicy.isAllowed(result.url)) {
                remember(url, result)
                return result
            }
        }
        return null
    }

    @Synchronized
    private fun cached(url: String): ExtractedStream? = cache[url]

    @Synchronized
    private fun remember(url: String, stream: ExtractedStream) {
        cache[url] = stream
    }

    init {
        register(MixdropExtractor)
        register(StreamtapeExtractor)
        register(DoodExtractor)
        register(VoeExtractor)
        register(TurbovioExtractor)
        register(RpmvidExtractor)
    }
}

// Helpers comunes
internal fun parseQuality(label: String): String =
    listOf("2160p", "4K", "1080p", "1080", "720p", "720", "480p", "480", "360p")
        .firstOrNull { it.contains(label, true) || label.contains(it, true) } ?: "HD"
