package com.omnitv.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Resultado de una petición de reproducción; el estado global de StreamHub no participa en la ruta. */
data class PlaybackResolution(val sources: List<StreamSource>, val message: String)

/** Una única resolución por pantalla; cambiar de título cancela la petición anterior desde Compose. */
object PlaybackResolver {
    private val mutex = Mutex()

    suspend fun resolve(video: Video): PlaybackResolution = mutex.withLock {
        val direct = video.playbackSources
        val resolved = StreamHub.resolve(video)
        val combined = (resolved + direct)
            .filter { NetPolicy.isAllowed(it.url) }
            .distinctBy { it.url }
        // Los embeds se convierten aquí en enlaces directos, con sus headers y
        // subtítulos, antes de entregárselos al reproductor multi-servidor.
        val sources = EmbedRefiner.refineAll(combined)
            .filter { NetPolicy.isAllowed(it.url) }
            .distinctBy { it.url }
            .take(24)
        PlaybackResolution(
            sources,
            if (sources.isNotEmpty()) "${sources.size} fuentes disponibles"
            else StreamHub.lastStatus.value.ifBlank { "No hay fuentes disponibles. Revisa tus addons y puentes en Ajustes." }
        )
    }
}
