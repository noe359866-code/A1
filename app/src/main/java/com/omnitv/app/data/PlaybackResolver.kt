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
        val sources = (resolved + direct).filter { NetPolicy.isAllowed(it.url) }.distinctBy { it.url }
        PlaybackResolution(
            sources,
            if (sources.isNotEmpty()) "${sources.size} fuentes disponibles"
            else StreamHub.lastStatus.value.ifBlank { "No hay fuentes disponibles. Revisa tus addons y puentes en Ajustes." }
        )
    }
}
