package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI

/**
 * Puente multimedia opcional: FFmpeg/ffprobe se ejecutan en otro equipo.
 *
 * El APK no incluye FFmpeg ni decodificadores por software. Cuando Media3
 * informa de un fallo de códec/contenedor, este servicio puede remuxear el
 * origen a HLS y convertir el audio a AAC; el vídeo se copia salvo que el
 * operador active la recodificación de vídeo en el puente.
 *
 * Contrato: `GET /health` y `POST /prepare {url, headers, reason}`.
 * Respuesta útil: `{action:"remux"|"transcode", url:"/media/…", format:"hls"}`.
 */
object MediaBridge {

    /** Nombre de la clave opcional del puente en Ajustes → APIs. */
    const val TOKEN_KEY = "FFMPEG_BRIDGE"

    val status = mutableStateOf("Puente multimedia no configurado")

    fun normalize(url: String): String? {
        val value = url.trim().trimEnd('/')
        if (value.isEmpty() || !NetPolicy.isAllowed(value)) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        return value
    }

    /** El token viaja como Bearer; AddonHttp no lo reenvía a otro host. */
    private fun authHeaders(): Map<String, String> =
        SettingsStore.apiKeys[TOKEN_KEY]?.takeIf { it.isNotBlank() }
            ?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()

    suspend fun check(base: String = SettingsStore.ffmpegBridge): String {
        val normalized = normalize(base)
        if (normalized == null) {
            return if (base.isBlank()) "Puente multimedia no configurado" else "URL del puente no permitida"
        }
        val json = withContext(Dispatchers.IO) {
            AddonHttp.getJson("$normalized/health", authHeaders()) as? JSONObject
        }
        return when {
            json == null -> "El puente multimedia no respondió a /health"
            !json.optBoolean("ok", false) -> json.optString("error").ifBlank { "FFmpeg/ffprobe no están disponibles en el puente" }
            else -> "Puente multimedia conectado · FFmpeg ${json.optString("ffmpeg", "?")}"
        }.also { status.value = it }
    }

    /**
     * Pide una versión reproducible del stream. Devuelve null si no hay puente,
     * si el servicio responde `direct` o si no pudo convertirlo.
     *
     * La espera está acotada por el timeout de lectura de AddonHttp; el trabajo
     * pesado ocurre en el equipo del puente, no en el televisor.
     */
    suspend fun repair(source: StreamSource, reason: String): StreamSource? {
        val base = normalize(SettingsStore.ffmpegBridge)
        if (base == null) {
            status.value = "Puente multimedia no configurado"
            return null
        }
        val json = withContext(Dispatchers.IO) {
            AddonHttp.postJson(
                url = "$base/prepare",
                body = requestBody(source, reason),
                headers = authHeaders(),
                readTimeoutMs = 55_000
            )
        }
        if (json == null) {
            status.value = "El puente no respondió o rechazó la petición · revisa URL, token y orígenes permitidos"
            return null
        }
        val repaired = parseResponse(json, source, base, authHeaders())
        status.value = when {
            repaired != null -> "Stream preparado por el puente (${(json as JSONObject).optString("action")})"
            json is JSONObject && json.optString("action") == "direct" ->
                "El puente indica que el origen ya es compatible; el fallo puede ser del dispositivo"
            else -> (json as? JSONObject)?.optString("error")?.takeIf { it.isNotBlank() }?.take(200)
                ?: "El puente no pudo preparar este stream"
        }
        return repaired
    }

    internal fun requestBody(source: StreamSource, reason: String): String = JSONObject().apply {
        put("url", source.url)
        put("headers", JSONObject(StreamResponse.parseHeaders(JSONObject(source.headers))))
        put("reason", reason.take(200))
    }.toString()

    internal fun parseResponse(
        json: Any?,
        original: StreamSource,
        base: String,
        auth: Map<String, String> = emptyMap()
    ): StreamSource? {
        val obj = json as? JSONObject ?: return null
        val action = obj.optString("action")
        if (action != "remux" && action != "transcode") return null
        val rawUrl = obj.optString("url").takeIf { it.isNotBlank() } ?: return null
        val url = runCatching { URI(base).resolve(rawUrl).toString() }.getOrNull() ?: return null
        if (!NetPolicy.isAllowed(url) || !sameOrigin(base, url)) return null
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        if (!path.startsWith("/media/")) return null
        val format = when (obj.optString("format").lowercase()) {
            "hls", "m3u8" -> StreamFormat.HLS
            "dash", "mpd" -> StreamFormat.DASH
            else -> StreamResponse.format(url)
        }
        return StreamSource(
            label = "${original.label} · puente",
            url = url,
            format = format,
            headers = auth
        )
    }

    private fun sameOrigin(a: String, b: String): Boolean = runCatching {
        val x = URI(a)
        val y = URI(b)
        x.scheme.equals(y.scheme, true) && x.host.equals(y.host, true) &&
            effectivePort(x) == effectivePort(y)
    }.getOrDefault(false)

    private fun effectivePort(uri: URI): Int =
        if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
}
