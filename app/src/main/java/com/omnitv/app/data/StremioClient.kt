package com.omnitv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente del protocolo de addons de Stremio (catalog / meta / stream),
 * ahora con parser tolerante de manifest (resources string|object, catalogs con extra, etc.)
 * y soporte completo de extras (search, genre, skip).
 *
 * Addons base:
 * - Cinemeta  → https://v3-cinemeta.strem.io  (IMDb tt…)
 * - Nuvio     → https://catalog.nuvio.tv      (TMDB)
 *
 * Endpoints:
 * - `GET {base}/manifest.json`
 * - `GET {base}/catalog/{type}/{catalogId}[/{extra}.json] → {metas:[…]}`
 * - `GET {base}/meta/{type}/{id}.json → {meta:{…, videos:[…]}}`
 * - `GET {base}/stream/{type}/{id}.json → {streams:[…]}`
 */
object StremioClient {

    const val CINEMETA = "https://v3-cinemeta.strem.io"
    const val NUVIO = "https://catalog.nuvio.tv"
    const val CINEMETA_NAME = "Cinemeta"
    const val NUVIO_NAME = "Nuvio"

    fun baseFor(sourceName: String): String = when (sourceName) {
        NUVIO_NAME -> NUVIO
        else -> CINEMETA
    }

    /* ------------------------------- HTTP -------------------------------- */

    private fun httpJson(url: String): JSONObject? =
        httpJsonDirect(url) ?: Doh.retry(url)?.let { body ->
            runCatching { JSONObject(body) }.getOrNull()
        }

    private fun httpJsonDirect(url: String): JSONObject? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "OmniTV/1.0 (Android TV)")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return null
            JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /* ------------------------------ Catálogo ----------------------------- */

    /** Lee un catálogo. `extras` → `search=…&skip=20&genre=Action` */
    suspend fun fetchCatalog(
        base: String,
        type: String,
        catalogId: String,
        extras: Map<String, String> = emptyMap()
    ): List<JSONObject> = withContext(Dispatchers.IO) {
        val extraPath = if (extras.isEmpty()) "" else "/" + extras.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
        val json = httpJson("$base/catalog/$type/$catalogId$extraPath.json")
        json?.optJSONArray("metas").toObjectList()
    }

    /** Búsqueda (extra: search). Respeta extraSupported del manifest si se provee. */
    suspend fun fetchSearch(
        base: String,
        type: String,
        catalogId: String,
        query: String
    ): List<JSONObject> = fetchCatalog(base, type, catalogId, mapOf("search" to query))

    /** Ficha completa `meta/{type}/{id}` con reparto y episodios. */
    suspend fun fetchMeta(base: String, type: String, id: String): JSONObject? =
        withContext(Dispatchers.IO) {
            httpJson("$base/meta/$type/$id.json")?.optJSONObject("meta")
        }

    /** Streams `stream/{type}/{id}` — usado por StreamHub para addons custom. */
    suspend fun fetchStreams(base: String, type: String, id: String): List<JSONObject> = withContext(Dispatchers.IO) {
        val json = httpJson("$base/stream/$type/${encode(id)}.json")
            ?: httpJson("$base/stream/$type/${encode(id)}")
            ?: return@withContext emptyList()
        (json.optJSONArray("streams") ?: json.optJSONArray("results") ?: JSONArray()).toObjectList()
    }

    /** Manifest raw + parseado tolerante. */
    suspend fun fetchManifest(urlOrBase: String): JSONObject? = withContext(Dispatchers.IO) {
        val url = if (urlOrBase.endsWith(".json")) urlOrBase else "${urlOrBase.trimEnd('/')}/manifest.json"
        httpJson(url)
    }

    suspend fun fetchParsedManifest(urlOrBase: String): StremioManifest? = withContext(Dispatchers.IO) {
        val json = fetchManifest(urlOrBase) ?: return@withContext null
        runCatching { StremioManifestParser.parse(json) }.getOrNull()
    }

    /** Valida si el catálogo soporta los extras pedidos (search, genre, skip). */
    fun catalogSupports(manifest: StremioManifest, catalog: StremioCatalog, extras: Set<String>): Boolean =
        extras.all { catalog.supportsExtra(it) }

    /* ------------------------------ Mapeo -------------------------------- */

    fun parseVideo(meta: JSONObject, sourceName: String): Video {
        val id = meta.optString("id").ifEmpty { meta.optString("imdb_id") }
        val remoteType = meta.optString("type", "movie")
        val genres = (meta.optJSONArray("genres") ?: meta.optJSONArray("genre")).toStringList()
        val poster = meta.optString("poster", "")
        val background = meta.optString("background", "").ifEmpty { poster }
        val runtime = parseRuntime(meta.optString("runtime", ""))
        val year = meta.optString("year", "").ifEmpty { meta.optString("releaseInfo", "") }.take(4).toIntOrNull() ?: 0
        val rating = meta.optString("imdbRating", "").toDoubleOrNull()
            ?: meta.optJSONObject("app_extras")?.optJSONObject("ratings")?.optJSONObject("imdb")?.optString("rating")?.toDoubleOrNull()
            ?: meta.optDouble("imdbRating", 0.0).takeIf { it > 0 } ?: 0.0

        val type = when {
            remoteType == "movie" -> VideoType.MOVIE
            genres.any { it.contains("anim", ignoreCase = true) } -> VideoType.ANIME
            else -> VideoType.SERIES
        }

        val episodes = meta.optJSONArray("videos").toObjectList()
            .filter { it.optInt("season", 1) >= 1 }
            .map { v ->
                val season = v.optInt("season", 1)
                val number = v.optInt("episode", v.optInt("number", 1))
                Episode(
                    id = "s${season}e$number",
                    title = v.optString("name", "Episodio $number"),
                    number = number,
                    season = season,
                    durationMinutes = runtime,
                    description = v.optString("description", v.optString("overview", "")),
                    thumbnailUrl = v.optString("thumbnail", "").ifEmpty { poster },
                    videoUrl = "",
                    streamId = v.optString("id", ""),
                    aired = v.optString("released", v.optString("firstAired", "")).ifEmpty { null }
                )
            }

        return Video(
            id = id,
            title = meta.optString("name", id),
            description = meta.optString("description", meta.optString("overview", "")),
            backdropUrl = background,
            cardUrl = poster,
            year = year,
            durationMinutes = runtime,
            rating = rating,
            certification = meta.optString("certification", meta.optString("ageRating", "")),
            quality = "HD",
            genres = genres,
            videoUrl = "",
            cast = meta.optJSONArray("cast").toStringList(),
            language = meta.optString("country", "").ifEmpty { "—" },
            type = type,
            episodes = episodes,
            isRemote = true,
            source = sourceName,
            remoteType = remoteType
        )
    }

    /* ------------------------------ Utilidades --------------------------- */

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun parseRuntime(runtime: String): Int = runtime.filter { it.isDigit() }.toIntOrNull() ?: 0

    private fun JSONArray?.toObjectList(): List<JSONObject> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) optJSONObject(i)?.let { add(it) } }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
    }
}
