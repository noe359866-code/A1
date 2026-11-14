package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

/** Lo que Media3 declaró de una pista en el momento del fallo. */
data class TrackFacts(
    val mime: String?,
    val codecs: String?,
    val supported: Boolean
)

/** Último fallo de reproducción, sin URLs ni cabeceras del stream. */
data class CodecReport(
    val errorCode: String,
    val sourceLabel: String,
    val video: TrackFacts?,
    val audio: TrackFacts?,
    val advice: String,
    val bridgeRecommended: Boolean
) {
    fun toJson(): String = JSONObject().apply {
        put("error", errorCode)
        put("source", sourceLabel.take(80))
        put("videoMime", video?.mime)
        put("videoCodecs", video?.codecs)
        put("videoSupported", video?.supported)
        put("audioMime", audio?.mime)
        put("audioCodecs", audio?.codecs)
        put("audioSupported", audio?.supported)
        put("advice", advice)
        put("bridge", bridgeRecommended)
    }.toString()

    companion object {
        fun fromJson(text: String): CodecReport? = runCatching {
            val obj = JSONObject(text)
            fun track(prefix: String): TrackFacts? {
                val mime = obj.optString("$prefixMime").takeIf { it.isNotBlank() && it != "null" }
                val codecs = obj.optString("$prefixCodecs").takeIf { it.isNotBlank() && it != "null" }
                if (mime == null && codecs == null && !obj.has("$prefixSupported")) return null
                return TrackFacts(mime, codecs, obj.optBoolean("$prefixSupported", true))
            }
            val error = obj.optString("error").takeIf { it.isNotBlank() } ?: return null
            val video = track("video")
            val audio = track("audio")
            CodecReport(
                errorCode = error,
                sourceLabel = obj.optString("source"),
                video = video,
                audio = audio,
                advice = obj.optString("advice").ifBlank { PlaybackDiagnostics.adviceFor(error, video, audio) },
                bridgeRecommended = obj.optBoolean("bridge", PlaybackDiagnostics.recommendBridge(error, video, audio))
            )
        }.getOrNull()
    }
}

/**
 * Diagnóstico barato antes de meter un motor de códecs en el APK.
 *
 * No afirma que un MIME concreto «no tenga licencia»: registra el código de
 * Media3, los MIME/codecs observados y si Media3 marcó la pista como no
 * soportada. Con eso decide si tiene sentido pedir ayuda al puente externo.
 */
object PlaybackDiagnostics {

    val last = mutableStateOf<CodecReport?>(null)

    fun current(): CodecReport? {
        last.value?.let { return it }
        return PlaybackDiagnosticsJson.parse(SettingsStore.lastPlaybackDiagnostic).also { last.value = it }
    }

    private fun isNetwork(errorCode: String): Boolean =
        errorCode.startsWith("ERROR_CODE_IO") || errorCode.contains("NETWORK") || errorCode.contains("HTTP_STATUS")

    fun recommendBridge(errorCode: String, video: TrackFacts?, audio: TrackFacts?): Boolean {
        if (isNetwork(errorCode)) return false
        val decoder = errorCode.contains("DECODER") || errorCode.contains("DECODING") || errorCode.contains("AUDIO_TRACK")
        val container = errorCode.contains("PARSING_CONTAINER")
        return decoder || container || video?.supported == false || audio?.supported == false
    }

    fun adviceFor(errorCode: String, video: TrackFacts?, audio: TrackFacts?): String = when {
        isNetwork(errorCode) -> "Parece un fallo de red o del servidor, no de códecs"
        audio?.supported == false -> "Media3 marcó el audio ${audio.mime ?: audio.codecs ?: "observado"} como no compatible; el puente puede convertirlo a AAC"
        video?.supported == false -> "Media3 marcó el vídeo ${video.mime ?: video.codecs ?: "observado"} como no compatible; el puente puede recodificarlo si su operador activa esa opción"
        errorCode.contains("DECODER") || errorCode.contains("DECODING") || errorCode.contains("AUDIO_TRACK") ->
            "Fallo de decodificación; el puente puede convertir el audio a AAC. Si el vídeo también falla, necesita recodificación en el puente"
        errorCode.contains("PARSING_CONTAINER") ->
            "El contenedor no se pudo leer; el puente puede remuxearlo a HLS sin recodificar el vídeo"
        video == null && audio == null -> "Media3 no declaró pistas; puede ser el contenedor, el códec o la red"
        else -> "Prueba otro servidor; si se repite, revisa el diagnóstico antes de convertir el stream"
    }

    fun record(errorCode: String, sourceLabel: String, video: TrackFacts?, audio: TrackFacts?): CodecReport {
        val report = CodecReport(
            errorCode = errorCode,
            sourceLabel = sourceLabel,
            video = video,
            audio = audio,
            advice = adviceFor(errorCode, video, audio),
            bridgeRecommended = recommendBridge(errorCode, video, audio)
        )
        last.value = report
        SettingsStore.saveLastPlaybackDiagnostic(report.toJson())
        return report
    }

    fun summary(): String = current()?.let {
        "${it.errorCode} · vídeo ${it.video?.mime ?: it.video?.codecs ?: "?"} · audio ${it.audio?.mime ?: it.audio?.codecs ?: "?"}"
    } ?: "Sin errores registrados"
}

/** Separado para poder probar el parseo sin tocar el estado observable. */
internal object PlaybackDiagnosticsJson {
    fun parse(text: String): CodecReport? = if (text.isBlank()) null else CodecReport.fromJson(text)
}
