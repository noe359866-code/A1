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
 * HTTP directas. Del televisor solo viaja el token del puente
 * (`CLOUDSTREAM_BRIDGE` en Ajustes → APIs), y únicamente hacia su mismo host:
 * las claves de API del usuario nunca se reenvían.
 */
object CloudstreamBridge {

    /**
     * Nombre de la clave opcional del puente en Ajustes → APIs. El puente exige
     * token cuando escucha fuera de loopback (ver tools/cloudstream-bridge).
     */
    const val TOKEN_KEY = "CLOUDSTREAM_BRIDGE"

    val status = mutableStateOf("Puente Cloudstream no configurado")
    val extensions = mutableStateOf<List<String>>(emptyList())

    /** El token viaja como Bearer; AddonHttp no lo reenvía a otro host. */
    private fun authHeaders(): Map<String, String> =
        SettingsStore.apiKeys[TOKEN_KEY]?.takeIf { it.isNotBlank() }
            ?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()

    fun normalize(url: String): String? {
        val value = url.trim().trimEnd('/')
        if (value.isEmpty() || !NetPolicy.isAllowed(value)) return null
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        return value
    }

    suspend fun check(base: String = SettingsStore.cloudstreamBridge): String {
        val normalized = normalize(base)
        if (normalized == null) {
            extensions.value = emptyList()
            return (if (base.isBlank()) "Puente Cloudstream no configurado" else "URL del puente no permitida")
                .also { status.value = it }
        }
        val json = withContext(Dispatchers.IO) {
            AddonHttp.getJson("$normalized/health", authHeaders()) as? JSONObject
        }
        return when {
            json == null -> {
                extensions.value = emptyList()
                "El puente Cloudstream no respondió a /health"
            }
            !json.optBoolean("ok", false) -> {
                extensions.value = emptyList()
                "El puente Cloudstream no está listo"
            }
            else -> {
                val arr = json.optJSONArray("extensions")
                val list = buildList {
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }
                extensions.value = list
                val count = if (arr != null) list.size else json.optInt("extensions", 0)
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
            withContext(Dispatchers.IO) {
                AddonHttp.postJson("$base/resolve", body, headers = authHeaders())
            }
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
        val anime = AnimeIds.isAnime(id)
        val animeEpisode = AnimeIds.episodeParts(id)
        val target = SkipTarget.from(video)
        val season = when {
            anime -> animeEpisode?.first
            tmdb -> parts.getOrNull(2)?.toIntOrNull()
            else -> parts.getOrNull(1)?.toIntOrNull()
        } ?: target?.season
        val episode = when {
            anime -> animeEpisode?.second
            tmdb -> parts.getOrNull(3)?.toIntOrNull()
            else -> parts.getOrNull(2)?.toIntOrNull()
        } ?: target?.episode
        val imdbId = id.substringBefore(':').takeIf { it.startsWith("tt") } ?: target?.imdbId
        val tmdbId = (if (tmdb) parts.getOrNull(1) else null) ?: target?.tmdbId
        return JSONObject().apply {
            put("title", if ('~' in video.id) video.title.substringBefore(" · ") else video.title)
            put("type", type)
            put("id", id)
            put("year", video.year.takeIf { it > 0 })
            put("imdbId", imdbId)
            put("tmdbId", tmdbId)
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
