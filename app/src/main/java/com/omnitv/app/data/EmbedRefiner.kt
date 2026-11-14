package com.omnitv.app.data

import com.omnitv.app.plugins.ExtractedStream
import com.omnitv.app.plugins.ExtractorRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

/**
 * Conecta los extractores Kotlin (Mixdrop, Streamtape, Dood, Voe…) con el
 * flujo real de reproducción. Un addon puede entregar la página de un embed
 * en lugar del MP4/HLS; aquí se resuelve a enlace directo y se propagan sus
 * cabeceras y subtítulos hasta `StreamSource` (y de ahí a Media3).
 *
 * Nada de esto sustituye a un addon que ya entregó un enlace directo: si no
 * hay extractor para el host, la fuente sigue intacta. Cada resolución va con
 * timeout y en paralelo, y el resultado se valida con [NetPolicy].
 */
internal object EmbedRefiner {
    private const val TIMEOUT_MS = 8_000L
    private const val MAX_SUBTITLES = 8

    suspend fun refineAll(sources: List<StreamSource>): List<StreamSource> = coroutineScope {
        sources.map { source ->
            async { withTimeoutOrNull(TIMEOUT_MS) { refine(source) } ?: source }
        }.awaitAll()
    }

    /** [resolve] es inyectable para poder probarlo sin red. */
    internal suspend fun refine(
        source: StreamSource,
        resolve: suspend (String) -> ExtractedStream? = { ExtractorRegistry.resolve(it) }
    ): StreamSource {
        // findFor ya compara el host real; un dominio «parecido» no entra.
        ExtractorRegistry.findFor(source.url) ?: return source
        val extracted = runCatching { resolve(source.url) }.getOrNull() ?: return source
        val direct = extracted.url.trim()
        if (direct.isBlank() || direct == source.url || !NetPolicy.isAllowed(direct)) return source

        val inferred = StreamResponse.format(direct)
        val format = if (inferred == StreamFormat.MP4 && source.format != StreamFormat.MP4) source.format else inferred
        val headers = StreamResponse.sanitizeHeaders(source.headers + extracted.headers)
        val subtitles = (source.subtitles + extracted.subtitles)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && NetPolicy.isAllowed(it) && sameHost(direct, it) }
            .distinct()
            .take(MAX_SUBTITLES)
            .toList()

        return source.copy(url = direct, format = format, headers = headers, subtitles = subtitles)
    }

    /**
     * Las cabeceras del stream (Referer, Authorization…) se aplican también a
     * las peticiones de subtítulos. Para no filtrarlas a otro origen, solo se
     * aceptan subtítulos servidos por el mismo host del enlace directo.
     */
    private fun sameHost(a: String, b: String): Boolean = runCatching {
        URI(a).host?.lowercase() == URI(b).host?.lowercase()
    }.getOrDefault(false)
}
