package com.omnitv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de cinejoy.pk — «Browse by Provider» con catálogo real por
 * plataforma de streaming.
 *
 * La página `https://cinejoy.pk/provider/{id}` se sirve en el servidor con
 * las fichas de TMDB (poster, enlace `/movie/{tmdbId}-slug`, título, año y
 * valoración). Se extraen con un parser tolerante; si la página cambia de
 * formato, la app degrada sin romperse (lista vacía + estado informativo).
 *
 * Los ids TMDB resultantes (`tmdb:{id}`) se enriquecen luego con la meta de
 * Nuvio (`meta/{type}/tmdb:{id}`), que incluye reparto, géneros y trailers.
 */
object CinejoyClient {

    private const val BASE = "https://cinejoy.pk"

    /** Películas destacadas de una plataforma en cinejoy.pk. */
    suspend fun fetchProvider(providerId: Int): List<Video> = withContext(Dispatchers.IO) {
        val html = httpText("$BASE/provider/$providerId") ?: return@withContext emptyList()
        parseProviderPage(html, providerId)
    }

    /* ------------------------------ HTTP -------------------------------- */

    private fun httpText(url: String): String? = runCatching {
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

    private val linkRegex =
        """href="(?:https?://cinejoy\.pk)?/(movie|tv|series)/(\d+)[^"]*"""".toRegex()
    private val posterRegex =
        """image\.tmdb\.org/t/p/(?:w\d{3}|original)/([\w.\-]+\.jpg)""".toRegex()
    private val altRegex = """alt="([^"]{2,120})"""".toRegex()
    private val yearInSlugRegex = """-(19|20)\d{2}$""".toRegex()

    internal fun parseProviderPage(html: String, providerId: Int): List<Video> {
        val results = LinkedHashMap<String, Video>()
        val links = linkRegex.findAll(html).toList()
        for ((index, match) in links.withIndex()) {
            val kind = match.groupValues[1] // movie | tv | series
            val tmdbId = match.groupValues[2]
            if (tmdbId in results) continue

            // Ventana de contexto alrededor del enlace (poster, alt, año, rating)
            val from = (match.range.first - 120).coerceAtLeast(0)
            val to = (match.range.last + 800).coerceAtMost(html.length)
            val window = html.substring(from, to)

            val posterPath = posterRegex.find(window)?.groupValues?.get(1) ?: continue
            val href = match.value
            val slugPath = href.substringAfterLast('/').substringBefore('"')
            val title = altRegex.find(window)?.groupValues?.get(1)
                ?: titleFromSlug(slugPath)
            val year = yearInSlugRegex.find(slugPath)?.value
                ?.trim('-')?.toIntOrNull() ?: 0
            // Valoración: solo texto visible de ESTA ficha (desde su enlace hasta el de
            // la siguiente). Si no aparece queda en 0.0 y la interfaz no muestra nota.
            val cardEnd = links.drop(index + 1)
                .firstOrNull { it.groupValues[2] != tmdbId }?.range?.first
                ?: html.length
            val card = html.substring(match.range.first, minOf(cardEnd, to))
            val rating = ScrapeText.rating(card, title)

            val isMovie = kind == "movie"
            val type = when {
                providerId == 283 -> VideoType.ANIME // Crunchyroll
                isMovie -> VideoType.MOVIE
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
                videoUrl = "", // los catálogos no traen streams → demo en el reproductor
                type = type,
                isRemote = true,
                source = "Cinejoy",
                remoteType = if (isMovie) "movie" else "series"
            )
        }
        return results.values.toList()
    }

    /** «1570488-a-dog-s-perfect-christmas-2026» → «A Dog S Perfect Christmas». */
    private fun titleFromSlug(slugPath: String): String {
        val slug = slugPath
            .substringAfter('-')
            .replace(yearInSlugRegex, "")
        return slug.split('-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
            .ifBlank { "Sin título" }
    }
}
