package com.omnitv.app.plugins

/**
 * Extractor — resuelve un embed (mixdrop, streamtape, dood…) a URL directo.
 * Inspirado en `app/src/main/java/com/streamflixreborn/streamflix/extractors/[host]`
 * Cada host tiene 1 archivo; aquí unificamos interfaz.
 */
interface Extractor {
    val name: String
    val domains: List<String> // ["mixdrop.co", "mixdrop.to"]

    fun canHandle(url: String): Boolean =
        domains.any { url.contains(it, ignoreCase = true) }

    suspend fun extract(url: String): ExtractedStream?
}

/** Registro global de extractores — orden = prioridad (directFirst). */
object ExtractorRegistry {
    private val extractors = mutableListOf<Extractor>()

    fun register(e: Extractor) { if (extractors.none { it.name == e.name }) extractors.add(e) }

    fun all(): List<Extractor> = extractors.toList()

    fun findFor(url: String): Extractor? = extractors.firstOrNull { it.canHandle(url) }

    /** Intenta todos hasta que uno resuelva; cachea éxito. */
    suspend fun resolve(url: String): ExtractedStream? {
        findFor(url)?.let { specific ->
            runCatching { specific.extract(url) }.getOrNull()?.let { return it }
        }
        // fallback: probar extractores genéricos (Voe, Filemoon) que aceptan varios dominios
        for (e in extractors) {
            if (e == findFor(url)) continue
            if (e.canHandle(url)) {
                runCatching { e.extract(url) }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    init {
        // Registro por defecto — ver extractors/
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
    listOf("2160p","4K","1080p","1080","720p","720","480p","480","360p").firstOrNull { it.contains(label, true) || label.contains(it, true) } ?: "HD"
