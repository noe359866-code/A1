package com.omnitv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de sweetfox.app — catálogo real por estudio («Browse by studio»).
 *
 * La página `https://sweetfox.app/company/{id}` se renderiza en el servidor
 * con las fichas de TMDB (poster `image.tmdb.org/t/p/w780/…`, enlace
 * `#title/{tipo}/{tmdbId}`, título, valoración y año). Se extraen con un
 * parser tolerante; si la web cambia, la app degrada sin romperse.
 *
 * Los ids TMDB (`tmdb:{id}`) se enriquecen luego con la meta de Nuvio
 * (reparto, géneros, episodios vía Cinemeta).
 */
object SweetFoxClient {

    private const val BASE = "https://sweetfox.app"

    /** Catálogo de un estudio (películas + series), sin duplicados. */
    suspend fun fetchStudio(studioId: Int): List<Video> = coroutineScope {
        val films = async { fetchPage("$BASE/company/$studioId") }
        val series = async { fetchPage("$BASE/company/$studioId?type=tv") }
        (films.await() + series.await()).distinctBy { it.id }
    }

    private suspend fun fetchPage(url: String): List<Video> = withContext(Dispatchers.IO) {
        val html = httpText(url) ?: return@withContext emptyList()
        parseCompanyPage(html)
    }

    /* ------------------------------ HTTP -------------------------------- */

    /**
     * Descarga [url]. Primero por el DNS normal; si el nombre no resuelve
     * (redes con DNS roto o filtrado), reintenta con el DNS de Cloudflare
     * ([Doh]) validando el certificado del nombre original.
     */
    private fun httpText(url: String): String? =
        httpTextDirect(url) ?: Doh.retry(url)

    private fun httpTextDirect(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "OmniTV/1.0 (Android TV)")
            connection.setRequestProperty("Accept", "text/html")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /* ------------------------------ Parser ------------------------------ */

    // href="…#title/movie/1465063" o href="/tv/84773"
    private val linkRegex =
        """href="[^"]*?(?:#title/)?(movie|tv|anime)/(\d+)""".toRegex()
    private val posterRegex =
        """image\.tmdb\.org/t/p/(?:w\d{3}|original)/([\w.\-]+\.jpg)""".toRegex()
    private val altRegex = """alt="([^"]{2,120})"""".toRegex()
    private val yearRegex = """\b((19|20)\d{2})\b""".toRegex()

    internal fun parseCompanyPage(html: String): List<Video> {
        val results = LinkedHashMap<String, Video>()
        val links = linkRegex.findAll(html).toList()
        for ((index, match) in links.withIndex()) {
            val kind = match.groupValues[1] // movie | tv | anime
            val tmdbId = match.groupValues[2]
            if (tmdbId in results) continue

            val from = (match.range.first - 150).coerceAtLeast(0)
            val to = (match.range.last + 700).coerceAtMost(html.length)
            val window = html.substring(from, to)

            val posterPath = posterRegex.find(window)?.groupValues?.get(1) ?: continue
            val title = altRegex.find(window)?.groupValues?.get(1) ?: "Sin título"
            // Valoración: solo texto visible de ESTA ficha (desde su enlace hasta el de
            // la siguiente). Hay títulos sin nota (estrenos futuros): quedan en 0.0
            // y la interfaz no muestra valoración, en vez de heredar la del vecino.
            val cardEnd = links.drop(index + 1)
                .firstOrNull { it.groupValues[2] != tmdbId }?.range?.first
                ?: html.length
            val card = html.substring(match.range.first, minOf(cardEnd, to))
            val rating = ScrapeText.rating(card, title)
            val year = yearRegex.find(window)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val type = when (kind) {
                "movie" -> VideoType.MOVIE
                "anime" -> VideoType.ANIME
                else -> VideoType.SERIES
            }

            results[tmdbId] = Video(
                id = "tmdb:$tmdbId",
                title = title,
                description = "", // se completa con la meta de Nuvio al abrir la ficha
                backdropUrl = "https://image.tmdb.org/t/p/w1280/$posterPath",
                cardUrl = "https://image.tmdb.org/t/p/w500/$posterPath",
                year = year,
                durationMinutes = 0,
                rating = rating,
                certification = "",
                quality = "HD",
                genres = emptyList(),
                videoUrl = "", // los catálogos no traen streams → los resuelve StreamHub
                type = type,
                isRemote = true,
                source = "SweetFox",
                remoteType = if (kind == "movie") "movie" else "series"
            )
        }
        return results.values.toList()
    }
}
