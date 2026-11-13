package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los dos parsers de HTML son la pieza más frágil de la app (dependen de una
 * web ajena que puede cambiar sin aviso). Estos tests fijan lo que sí es
 * nuestro: la forma de la tarjeta que sabemos leer y, sobre todo, que **un
 * dato que no está no se inventa**.
 */
class ParserTest {

    /** Una tarjeta de cinejoy.pk con la forma real de /provider/{id}. */
    private fun cinejoyCard(
        tmdbId: Long,
        slug: String,
        title: String,
        year: Int,
        rating: String?,
        poster: String = "$tmdbId.jpg"
    ) = """
        <a href="/movie/$tmdbId-$slug" class="group block">
          <div class="aspect-[2/3] overflow-hidden">
            <img src="https://image.tmdb.org/t/p/w500/$poster" alt="$title" loading="lazy"
                 class="w-full h-full object-cover transition duration-300">
          </div>
          <div class="mt-2 flex items-center justify-between gap-2">
            <h3 class="truncate text-sm">$title</h3>
            <span class="text-xs text-zinc-400">$year ${rating ?: ""}</span>
          </div>
        </a>
    """.trimIndent()

    private fun cinejoyPage(vararg cards: String) =
        "<html><body><div class=\"grid gap-4\">${cards.joinToString("\n")}</div></body></html>"

    /** Una tarjeta de sweetfox.app con la forma real de /company/{id}. */
    private fun sweetfoxCard(
        tmdbId: Long,
        kind: String,
        title: String,
        year: Int,
        rating: String?,
        poster: String = "$tmdbId.jpg"
    ) = """
        <a href="#title/$kind/$tmdbId" class="group">
          <div class="relative aspect-[2/3]">
            <img src="https://image.tmdb.org/t/p/w780/$poster" alt="$title" loading="lazy"
                 class="h-full w-full rounded-lg object-cover">
            <span class="absolute left-1 top-1 rounded bg-black/70 px-1 text-[10px]">4K</span>
          </div>
          <div class="mt-1 flex items-center gap-1">
            <span class="font-semibold">${rating ?: ""}</span><span class="opacity-60">Movie</span><span class="opacity-60">$year</span>
          </div>
        </a>
    """.trimIndent()

    // ------------------------------ Cinejoy ------------------------------

    @Test
    fun `cinejoy lee titulo, ano, poster y valoracion de cada tarjeta`() {
        val videos = CinejoyClient.parseProviderPage(
            cinejoyPage(
                cinejoyCard(940721, "godzilla-minus-one-2023", "Godzilla Minus One", 2023, "7.1"),
                cinejoyCard(1570488, "a-dog-s-perfect-christmas-2026", "A Dog's Perfect Christmas", 2026, "6.3")
            ),
            8
        )

        assertEquals(2, videos.size)
        val first = videos[0]
        assertEquals("tmdb:940721", first.id)
        assertEquals("Godzilla Minus One", first.title)
        assertEquals(2023, first.year)
        assertEquals(7.1, first.rating, 0.0)
        assertTrue(first.hasRating)
        assertEquals("https://image.tmdb.org/t/p/w500/940721.jpg", first.cardUrl)
        assertEquals(VideoType.MOVIE, first.type)
        assertTrue(first.isRemote)
        assertEquals(6.3, videos[1].rating, 0.0)
    }

    @Test
    fun `cinejoy no hereda la valoracion de la tarjeta vecina`() {
        // La del medio no tiene nota (la web solo pone el año): su nota es 0,
        // no la «9.1» de la anterior ni la «8.2» de la siguiente.
        val videos = CinejoyClient.parseProviderPage(
            cinejoyPage(
                cinejoyCard(1, "primera-2024", "Primera", 2024, "9.1"),
                cinejoyCard(2, "sin-estrenar-2027", "Sin Estrenar", 2027, null),
                cinejoyCard(3, "tercera-2022", "Tercera", 2022, "8.2")
            ),
            8
        )

        assertEquals(3, videos.size)
        assertEquals(9.1, videos[0].rating, 0.0)
        assertEquals(0.0, videos[1].rating, 0.0)
        assertFalse("no debe tener nota", videos[1].hasRating)
        assertEquals(8.2, videos[2].rating, 0.0)
    }

    @Test
    fun `cinejoy no toma por nota los decimales de los atributos`() {
        val card = """
            <a href="/movie/77-algo-2025" class="group" style="--tw-scale:0.85">
              <img src="https://image.tmdb.org/t/p/w500/77.jpg" alt="Algo" width="500.0">
              <svg viewBox="0 0 24 24" stroke-width="1.5"><path d="M12 2.5 15.1 8.6Z"/></svg>
              <h3>Algo</h3><span>2025</span>
            </a>
        """.trimIndent()

        val video = CinejoyClient.parseProviderPage(cinejoyPage(card), 8).single()
        assertEquals(0.0, video.rating, 0.0)
    }

    @Test
    fun `cinejoy descarta enlaces repetidos y no rompe con HTML vacio`() {
        val card = cinejoyCard(5, "repetida-2020", "Repetida", 2020, "7.5")
        assertEquals(1, CinejoyClient.parseProviderPage(cinejoyPage(card, card), 8).size)
        assertTrue(CinejoyClient.parseProviderPage("", 8).isEmpty())
        assertTrue(CinejoyClient.parseProviderPage("<html><body>Vacío</body></html>", 8).isEmpty())
    }

    @Test
    fun `cinejoy deduce el titulo del slug si falta el alt`() {
        val card = """
            <a href="/movie/1570488-a-dog-s-perfect-christmas-2026">
              <img src="https://image.tmdb.org/t/p/w500/1570488.jpg">
            </a>
        """.trimIndent()

        val video = CinejoyClient.parseProviderPage(cinejoyPage(card), 8).single()
        assertEquals("A Dog S Perfect Christmas", video.title)
        assertEquals(2026, video.year)
        assertEquals(0.0, video.rating, 0.0)
    }

    // ------------------------------ SweetFox ------------------------------

    @Test
    fun `sweetfox lee la tarjeta con nota`() {
        val videos = SweetFoxClient.parseCompanyPage(
            sweetfoxCard(420, "movie", "Shang-Chi", 2021, "8.5") +
                sweetfoxCard(84773, "tv", "Una serie", 2024, "7.9")
        )

        assertEquals(2, videos.size)
        assertEquals("Shang-Chi", videos[0].title)
        assertEquals(8.5, videos[0].rating, 0.0)
        assertEquals(2021, videos[0].year)
        assertEquals(VideoType.MOVIE, videos[0].type)
        assertEquals(VideoType.SERIES, videos[1].type)
        assertEquals(7.9, videos[1].rating, 0.0)
    }

    @Test
    fun `sweetfox deja sin nota los estrenos sin valoracion`() {
        // Caso real de /company/420: «Avengers: Doomsday» aparece como
        // «Movie2026», sin nota. Antes se le ponía un 7.0 inventado.
        val videos = SweetFoxClient.parseCompanyPage(
            sweetfoxCard(1476455, "movie", "Avengers: Doomsday", 2026, null) +
                sweetfoxCard(1003596, "movie", "Avengers: Secret Wars", 2027, null) +
                sweetfoxCard(10, "movie", "Otra con nota", 2023, "8.1")
        )

        assertEquals(3, videos.size)
        videos.take(2).forEach {
            assertEquals(it.title, 0.0, it.rating, 0.0)
            assertFalse(it.title, it.hasRating)
        }
        assertEquals(8.1, videos[2].rating, 0.0)
    }

    @Test
    fun `sweetfox no se queda con la nota de la tarjeta anterior`() {
        val videos = SweetFoxClient.parseCompanyPage(
            sweetfoxCard(1, "movie", "Con Nota", 2020, "9.4") +
                sweetfoxCard(2, "movie", "Sin Nota", 2027, null)
        )

        assertEquals(0.0, videos[1].rating, 0.0)
    }

    @Test
    fun `sweetfox descarta enlaces repetidos y HTML vacio`() {
        val card = sweetfoxCard(9, "movie", "Repetida", 2019, "6.0")
        assertEquals(1, SweetFoxClient.parseCompanyPage(card + card).size)
        assertTrue(SweetFoxClient.parseCompanyPage("").isEmpty())
    }
}
