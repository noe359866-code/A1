package com.omnitv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Cliente de Kitsu (JSON:API, host actual `kitsu.app`).
 *
 * Endpoints usados: `/trending/anime`, `/anime` (búsqueda y mejor valoradas),
 * `/anime/{id}` y `/anime/{id}/episodes`. Se pide un fieldset reducido con
 * `include=genres` para no descargar relaciones innecesarias; el endpoint de
 * tendencias ignora el fieldset, así que el parser también acepta la forma
 * completa. Las fichas se identifican como `kitsu:{id}`.
 *
 * **No aporta streams**; solo metadatos. Se descartan `nsfw` y `R18` en el
 * cliente porque Kitsu no permite filtrarlos en todas sus rutas.
 */
internal object KitsuClient {
    private const val BASE = "https://kitsu.app/api/edge"
    private const val ANIME_FIELDS =
        "canonicalTitle,synopsis,posterImage,coverImage,startDate,averageRating," +
            "episodeCount,episodeLength,subtype,ageRating,nsfw,titles,genres"
    private val HEADERS = mapOf("Accept" to "application/vnd.api+json")

    internal fun url(path: String, params: List<Pair<String, String>> = emptyList()): String {
        val query = params.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }
        return "$BASE$path${if (query.isBlank()) "" else "?$query"}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private suspend fun request(path: String, params: List<Pair<String, String>> = emptyList()): AddonHttp.Result =
        withContext(Dispatchers.IO) { AddonHttp.getJsonWithStatus(url(path, params), HEADERS) }

    private fun listParams(extra: List<Pair<String, String>> = emptyList()): List<Pair<String, String>> =
        listOf(
            "include" to "genres",
            "fields[anime]" to ANIME_FIELDS,
            "fields[genres]" to "name",
            "page[limit]" to "20"
        ) + extra

    suspend fun trending(): AnimePage {
        val result = request("/trending/anime", listParams())
        return AnimePage(parseList(result.value).take(20), result.status)
    }

    suspend fun topRated(offset: Int = 0): AnimePage {
        val result = request(
            "/anime",
            listParams(
                listOf(
                    "sort" to "-averageRating",
                    "filter[ageRating]" to "G,PG,R",
                    "page[offset]" to offset.coerceAtLeast(0).toString()
                )
            )
        )
        return AnimePage(parseList(result.value).take(20), result.status)
    }

    suspend fun search(query: String): AnimePage {
        val result = request(
            "/anime",
            listParams(
                listOf(
                    "filter[text]" to query.trim(),
                    "filter[ageRating]" to "G,PG,R",
                    "page[offset]" to "0"
                )
            )
        )
        return AnimePage(parseList(result.value).take(20), result.status)
    }

    suspend fun anime(kitsuId: String): JSONObject? {
        if (!kitsuId.all { it in '0'..'9' }) return null
        val result = request(
            "/anime/$kitsuId",
            listOf(
                "include" to "genres",
                "fields[anime]" to ANIME_FIELDS,
                "fields[genres]" to "name"
            )
        )
        return result.value as? JSONObject
    }

    suspend fun episodes(
        kitsuId: String,
        offset: Int,
        streamPrefix: String,
        poster: String
    ): AnimeEpisodePage {
        if (!kitsuId.all { it in '0'..'9' }) return AnimeEpisodePage()
        val result = request(
            "/anime/$kitsuId/episodes",
            listOf(
                "page[limit]" to "20",
                "page[offset]" to offset.coerceAtLeast(0).toString(),
                "sort" to "number"
            )
        )
        val root = result.value as? JSONObject
        val parsed = parseEpisodes(root, streamPrefix, poster)
        val next = root?.optJSONObject("links")?.optString("next", "").orEmpty().isNotBlank()
        val count = root?.optJSONObject("meta")?.optInt("count", 0) ?: 0
        val hasMore = parsed.isNotEmpty() && (next || count > offset + parsed.size)
        return AnimeEpisodePage(parsed, hasMore, result.status)
    }

    /* ------------------------------ Parsers ------------------------------ */

    internal fun parseList(json: Any?): List<Video> {
        val root = json as? JSONObject ?: return emptyList()
        val array = root.optJSONArray("data") ?: return emptyList()
        val names = genreNames(root)
        return buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { parseResource(it, names) }?.let(::add)
            }
        }.distinctBy { it.id }
    }

    internal fun parseSingle(json: Any?): Video? {
        val root = json as? JSONObject ?: return null
        val resource = root.optJSONObject("data") ?: return null
        return parseResource(resource, genreNames(root))
    }

    internal fun parseResource(resource: JSONObject, genreNames: Map<String, String> = emptyMap()): Video? {
        val rawId = resource.optString("id", "")
        if (rawId.isBlank() || !rawId.all { it in '0'..'9' }) return null
        val attributes = resource.optJSONObject("attributes") ?: return null
        val ageRating = attributes.optString("ageRating", "")
        if (AnimeAgeRating.isAdultKitsu(ageRating, attributes.optBoolean("nsfw", false))) return null

        val id = "${AnimeIds.KITSU}$rawId"
        val titles = attributes.optJSONObject("titles")
        val title = titles?.optString("en_us", "")?.takeIf { it.isNotBlank() }
            ?: titles?.optString("en", "")?.takeIf { it.isNotBlank() }
            ?: attributes.optString("canonicalTitle", "").ifBlank { attributes.optString("slug", id) }
        val poster = image(attributes.optJSONObject("posterImage"), "large", "medium", "original", "small")
        val cover = image(attributes.optJSONObject("coverImage"), "large", "original", "small")
        val subtype = attributes.optString("subtype", "")
        val remoteType = if (subtype.equals("movie", ignoreCase = true)) "movie" else "series"
        val genres = genreIds(resource).mapNotNull { genreNames[it] }.distinct()
        val score = attributes.optString("averageRating", "").toDoubleOrNull() ?: 0.0

        return Video(
            id = id,
            title = title,
            description = attributes.optString("synopsis", attributes.optString("description", "")),
            backdropUrl = cover.ifBlank { poster },
            cardUrl = poster,
            year = attributes.optString("startDate", "").take(4).toIntOrNull() ?: 0,
            durationMinutes = attributes.optInt("episodeLength", 0).coerceIn(0, 300),
            rating = (score / 10.0).coerceIn(0.0, 10.0),
            certification = AnimeAgeRating.kitsu(ageRating),
            quality = "HD",
            genres = genres,
            videoUrl = "", // ni Kitsu ni Jikan entregan enlaces de vídeo
            badge = AnimeIds.SOURCE_KITSU,
            language = "—",
            type = VideoType.ANIME,
            episodes = emptyList(),
            isRemote = true,
            source = AnimeIds.SOURCE_KITSU,
            remoteType = remoteType
        )
    }

    internal fun parseEpisodes(json: Any?, streamPrefix: String, poster: String): List<Episode> {
        val array = when (json) {
            is JSONObject -> json.optJSONArray("data")
            is JSONArray -> json
            else -> null
        } ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val resource = array.optJSONObject(i) ?: continue
                val attributes = resource.optJSONObject("attributes") ?: continue
                val season = attributes.optInt("seasonNumber", 1).takeIf { it > 0 } ?: 1
                val number = attributes.optInt("number", attributes.optInt("relativeNumber", i + 1))
                    .takeIf { it > 0 } ?: continue
                val title = attributes.optString("canonicalTitle", "").ifBlank { "Episodio $number" }
                val thumbnail = image(attributes.optJSONObject("thumbnail"), "original").ifBlank { poster }
                add(
                    Episode(
                        id = "s${season}e$number",
                        title = title,
                        number = number,
                        durationMinutes = attributes.optInt("length", 0).coerceIn(0, 300),
                        description = attributes.optString("synopsis", ""),
                        thumbnailUrl = thumbnail,
                        videoUrl = "",
                        season = season,
                        streamId = AnimeIds.episodeStreamId(streamPrefix, null, season, number),
                        aired = attributes.optString("airdate", "").takeIf { it.isNotBlank() }
                    )
                )
            }
        }.distinctBy { "${it.season}:${it.number}" }
            .sortedWith(compareBy<Episode> { it.season }.thenBy { it.number })
    }

    private fun genreNames(root: JSONObject): Map<String, String> {
        val included = root.optJSONArray("included") ?: return emptyMap()
        val names = LinkedHashMap<String, String>()
        for (i in 0 until included.length()) {
            val item = included.optJSONObject(i) ?: continue
            if (!item.optString("type", "").equals("genres", ignoreCase = true)) continue
            val name = item.optJSONObject("attributes")?.optString("name", "").orEmpty()
            if (name.isNotBlank()) names[item.optString("id", "")] = name
        }
        return names
    }

    private fun genreIds(resource: JSONObject): List<String> {
        val data = resource.optJSONObject("relationships")
            ?.optJSONObject("genres")?.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val id = data.optJSONObject(i)?.optString("id", "").orEmpty()
                if (id.isNotBlank()) add(id)
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
}
