package com.omnitv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Cliente de Jikan v4 (API no oficial de MyAnimeList).
 *
 * Endpoints usados: `/top/anime`, `/seasons/now`, `/anime?q=…`,
 * `/anime/{id}/full` y `/anime/{id}/episodes`. Jikan limita a 3 peticiones
 * por segundo y 60 por minuto: este cliente separa sus propias llamadas al
 * menos 350 ms y usa el [AddonHttp] común (4 MB, redirects y DoH).
 *
 * **No aporta streams**; solo metadatos. Las fichas se identifican como
 * `mal:{id}` para que Stremio/addons de anime puedan reconocerlas.
 */
internal object JikanClient {
    private const val BASE = "https://api.jikan.moe/v4"
    private const val MIN_GAP_MS = 350L

    private val throttleMutex = Mutex()
    private var lastRequestAt = 0L

    internal fun url(path: String, params: List<Pair<String, String>> = emptyList()): String {
        val query = params.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }
        return "$BASE$path${if (query.isBlank()) "" else "?$query"}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private suspend fun throttle() = throttleMutex.withLock {
        val wait = MIN_GAP_MS - (System.currentTimeMillis() - lastRequestAt)
        if (wait > 0) delay(wait)
        lastRequestAt = System.currentTimeMillis()
    }

    private suspend fun request(path: String, params: List<Pair<String, String>> = emptyList()): AddonHttp.Result {
        throttle()
        // Jikan es un servicio comunitario: si no responde, no se bloquea el
        // catálogo de Kitsu ni el buscador general durante el read timeout.
        return withContext(Dispatchers.IO) {
            AddonHttp.getJsonWithStatus(url(path, params), readTimeoutMs = 5_000)
        }
    }

    suspend fun top(page: Int = 1): AnimePage {
        val result = request(
            "/top/anime",
            listOf("page" to page.coerceAtLeast(1).toString(), "limit" to "20", "sfw" to "true")
        )
        return AnimePage(parseList(result.value).take(20), result.status)
    }

    suspend fun seasonNow(page: Int = 1): AnimePage {
        val result = request(
            "/seasons/now",
            listOf("page" to page.coerceAtLeast(1).toString(), "limit" to "20", "sfw" to "true")
        )
        return AnimePage(parseList(result.value).take(20), result.status)
    }

    suspend fun search(query: String): AnimePage {
        val result = request(
            "/anime",
            listOf("q" to query.trim(), "limit" to "20", "sfw" to "true", "page" to "1")
        )
        return AnimePage(parseList(result.value).take(20), result.status)
    }

    /** Ficha completa: incluye `external[]`, de donde se puede leer un id IMDb. */
    suspend fun full(malId: String): JSONObject? {
        if (!malId.all { it in '0'..'9' }) return null
        val result = request("/anime/$malId/full")
        return (result.value as? JSONObject)?.optJSONObject("data")
    }

    suspend fun episodes(
        malId: String,
        page: Int,
        streamPrefix: String,
        duration: Int,
        poster: String
    ): AnimeEpisodePage {
        if (!malId.all { it in '0'..'9' }) return AnimeEpisodePage()
        val result = request("/anime/$malId/episodes", listOf("page" to page.coerceAtLeast(1).toString()))
        val root = result.value as? JSONObject
        val hasMore = root?.optJSONObject("pagination")?.optBoolean("has_next_page", false) ?: false
        return AnimeEpisodePage(parseEpisodes(root, streamPrefix, duration, poster), hasMore, result.status)
    }

    /* ------------------------------ Parsers ------------------------------ */

    internal fun parseList(json: Any?): List<Video> {
        val array = when (json) {
            is JSONObject -> json.optJSONArray("data")
            is JSONArray -> json
            else -> null
        } ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { parseAnime(it) }?.let(::add)
            }
        }.distinctBy { it.id }
    }

    internal fun parseAnime(obj: JSONObject): Video? {
        val malId = obj.optInt("mal_id", 0)
        if (malId <= 0) return null

        val ratingText = obj.optString("rating", "")
        val genres = namedList(obj, "genres")
        val explicit = namedList(obj, "explicit_genres")
        if (AnimeAgeRating.isAdultJikan(ratingText, genres + explicit)) return null

        val id = "${AnimeIds.JIKAN}$malId"
        val images = obj.optJSONObject("images")
        val jpg = images?.optJSONObject("jpg")
        val webp = images?.optJSONObject("webp")
        val poster = image(jpg, "large_image_url", "image_url")
            .ifBlank { image(webp, "large_image_url", "image_url") }
        val trailer = obj.optJSONObject("trailer")?.optJSONObject("images")
            ?.let { image(it, "maximum_image_url", "large_image_url", "image_url") }.orEmpty()
        val title = obj.optString("title_english", "").ifBlank {
            obj.optString("title", "").ifBlank { obj.optString("title_japanese", id) }
        }
        val typeRaw = obj.optString("type", "")
        val remoteType = if (typeRaw.equals("Movie", ignoreCase = true)) "movie" else "series"
        val allGenres = (genres + namedList(obj, "themes") + namedList(obj, "demographics")).distinct()

        return Video(
            id = id,
            title = title,
            description = obj.optString("synopsis", ""),
            backdropUrl = trailer.ifBlank { poster },
            cardUrl = poster,
            year = year(obj),
            durationMinutes = duration(obj.optString("duration", "")),
            rating = obj.optDouble("score", 0.0).coerceIn(0.0, 10.0),
            certification = AnimeAgeRating.jikan(ratingText),
            quality = "HD",
            genres = allGenres,
            videoUrl = "", // ni Jikan ni Kitsu entregan enlaces de vídeo
            badge = AnimeIds.SOURCE_JIKAN,
            language = "—",
            type = VideoType.ANIME,
            episodes = emptyList(),
            isRemote = true,
            source = AnimeIds.SOURCE_JIKAN,
            remoteType = remoteType
        )
    }

    internal fun imdbId(full: JSONObject): String? {
        val external = full.optJSONArray("external") ?: return null
        for (i in 0 until external.length()) {
            val url = external.optJSONObject(i)?.optString("url", "").orEmpty()
            AnimeIds.imdbFromUrl(url)?.let { return it }
        }
        return null
    }

    internal fun parseEpisodes(
        json: Any?,
        streamPrefix: String,
        duration: Int,
        poster: String
    ): List<Episode> {
        val array = when (json) {
            is JSONObject -> json.optJSONArray("data")
            is JSONArray -> json
            else -> null
        } ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val number = item.optInt("mal_id", i + 1).takeIf { it > 0 } ?: continue
                val title = item.optString("title_english", "").ifBlank {
                    item.optString("title", "").ifBlank { "Episodio $number" }
                }
                add(
                    Episode(
                        id = "s1e$number",
                        title = title,
                        number = number,
                        durationMinutes = duration,
                        description = "",
                        thumbnailUrl = poster,
                        videoUrl = "",
                        season = 1,
                        streamId = AnimeIds.episodeStreamId(streamPrefix, null, 1, number),
                        aired = item.optString("aired", "").takeIf { it.isNotBlank() }
                    )
                )
            }
        }.distinctBy { it.number }.sortedBy { it.number }
    }

    private fun namedList(obj: JSONObject, key: String): List<String> {
        val array = obj.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                val name = item?.optString("name", "")?.takeIf { it.isNotBlank() }
                    ?: array.optString(i, "").takeIf { it.isNotBlank() && it != "null" }
                if (name != null) add(name)
            }
        }
    }

    /** Solo se aceptan URLs válidas según la política de red de la app. */
    private fun image(obj: JSONObject?, vararg keys: String): String {
        if (obj == null) return ""
        for (key in keys) {
            val value = obj.optString(key, "")
            if (value.isNotBlank() && NetPolicy.isAllowed(value)) return value
        }
        return ""
    }

    private fun year(obj: JSONObject): Int {
        val declared = obj.optInt("year", 0)
        if (declared in 1800..2100) return declared
        return obj.optJSONObject("aired")?.optString("from", "")?.take(4)?.toIntOrNull() ?: 0
    }

    private fun duration(raw: String): Int =
        Regex("\\d+").find(raw)?.value?.toIntOrNull()?.coerceIn(1, 300) ?: 0
}
