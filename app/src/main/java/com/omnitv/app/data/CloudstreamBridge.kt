package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Extensiones Cloudstream ejecutadas por un servicio externo de confianza.
 * El TV no carga DEX/.cs3 ni ejecuta código de repositorios dentro de la app.
 *
 * Contrato: GET /health y POST /resolve con el título completo y sus ids.
 * El servicio debe buscar/cargar/enlazar con sus extensiones y devolver URLs
 * HTTP directas. No se envían claves de API del televisor al puente.
 */
object CloudstreamBridge {
    val status = mutableStateOf("Puente Cloudstream no configurado")

    fun normalize(url: String): String? {
        val value = url.trim().trimEnd('/')
        if (value.isEmpty() || !NetPolicy.isAllowed(value)) return null
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        return value
    }

    suspend fun check(base: String = SettingsStore.cloudstreamBridge): String {
        val normalized = normalize(base)
        if (normalized == null) return if (base.isBlank()) "Puente Cloudstream no configurado" else "URL del puente no permitida"
        val json = withContext(Dispatchers.IO) { AddonHttp.getJson("$normalized/health") as? JSONObject }
        return when {
            json == null -> "El puente Cloudstream no respondió a /health"
            !json.optBoolean("ok", false) -> "El puente Cloudstream no está listo"
            else -> {
                val count = json.optJSONArray("extensions")?.length() ?: json.optInt("extensions", 0)
                "Puente Cloudstream conectado · $count extension(es) informadas"
            }
        }.also { status.value = it }
    }

    suspend fun resolve(video: Video): List<StreamLink> {
        val base = normalize(SettingsStore.cloudstreamBridge)
        if (base == null) {
            status.value = "Puente Cloudstream no configurado"
            return emptyList()
        }
        val body = request(video)
        val json = withTimeoutOrNull(25_000L) {
            withContext(Dispatchers.IO) { AddonHttp.postJson("$base/resolve", body) }
        }
        if (json == null) {
            status.value = "Cloudstream no respondió a tiempo"
            return emptyList()
        }
        val links = StreamResponse.parse(json, "Cloudstream")
        status.value = if (links.isEmpty()) {
            val obj = json as? JSONObject
            obj?.optString("error")?.takeIf { it.isNotBlank() } ?: "Cloudstream no entregó enlaces HTTP para este título"
        } else "${links.size} enlaces de Cloudstream"
        return links
    }

    internal fun request(video: Video): String {
        val (type, id) = StreamHub.stremioId(video) ?: ("movie" to video.id)
        val parts = id.split(':')
        val tmdb = id.startsWith("tmdb:")
        val season = parts.getOrNull(if (tmdb) 2 else 1)?.toIntOrNull()
        val episode = parts.getOrNull(if (tmdb) 3 else 2)?.toIntOrNull()
        return JSONObject().apply {
            put("title", if ('~' in video.id) video.title.substringBefore(" · ") else video.title)
            put("type", type)
            put("id", id)
            put("year", video.year.takeIf { it > 0 })
            put("imdbId", id.substringBefore(':').takeIf { it.startsWith("tt") })
            put("tmdbId", if (tmdb) parts.getOrNull(1) else null)
            put("season", season)
            put("episode", episode)
        }.toString()
    }
}

internal class CloudstreamStreamAddon : StreamAddon {
    override val id = "cloudstream-bridge"
    override val name = "Cloudstream"
    override suspend fun resolve(video: Video): List<StreamLink> = CloudstreamBridge.resolve(video)
}
