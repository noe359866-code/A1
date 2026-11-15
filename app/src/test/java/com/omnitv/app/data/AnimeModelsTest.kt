package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeModelsTest {

    @Test
    fun `los ids nativos identifican proveedor y episodio`() {
        assertTrue(AnimeIds.isAnime("mal:5114"))
        assertTrue(AnimeIds.isAnime("kitsu:7442:1:2"))
        assertFalse(AnimeIds.isAnime("tt0944947:1:2"))
        assertEquals("Jikan", AnimeIds.source("mal:5114"))
        assertEquals("Kitsu", AnimeIds.source("kitsu:7442"))
        assertEquals("5114", AnimeIds.providerId("mal:5114:1:2"))
        assertEquals("7442", AnimeIds.providerId("kitsu:7442"))
        assertNull(AnimeIds.providerId("tt123:1:2"))
        assertEquals(1 to 2, AnimeIds.episodeParts("kitsu:7442:1:2"))
        assertEquals(1 to 2, AnimeIds.episodeParts("mal:5114:1:2~s1e2".substringBefore('~')))
        assertNull(AnimeIds.episodeParts("mal:5114"))
        assertNull(AnimeIds.episodeParts("tt0944947:1:2"))
    }

    @Test
    fun `el tipo alternativo solo alterna series y anime`() {
        assertEquals("anime", AnimeIds.alternateType("series"))
        assertEquals("series", AnimeIds.alternateType("anime"))
        assertNull(AnimeIds.alternateType("movie"))
        assertEquals("tt0421357:1:2", AnimeIds.episodeStreamId("mal:5114", "tt0421357", 1, 2))
        assertEquals("kitsu:7442:1:2", AnimeIds.episodeStreamId("kitsu:7442", null, 1, 2))
        assertEquals("tt0421357", AnimeIds.imdbFromUrl("https://www.imdb.com/title/tt0421357/"))
    }

    @Test
    fun `las clasificaciones se traducen sin inventar una edad`() {
        assertEquals("TP", AnimeAgeRating.jikan("G - All Ages"))
        assertEquals("7+", AnimeAgeRating.jikan("PG - Children"))
        assertEquals("12+", AnimeAgeRating.jikan("PG-13 - Teens 13 or older"))
        assertEquals("16+", AnimeAgeRating.jikan("R - 17+ (violence & profanity)"))
        assertEquals("18+", AnimeAgeRating.jikan("R+ - Mild Nudity"))
        assertEquals("18+", AnimeAgeRating.jikan("Rx - Hentai"))
        assertEquals("", AnimeAgeRating.jikan(""))
        assertEquals("TP", AnimeAgeRating.kitsu("G"))
        assertEquals("7+", AnimeAgeRating.kitsu("PG"))
        assertEquals("16+", AnimeAgeRating.kitsu("R"))
        assertEquals("18+", AnimeAgeRating.kitsu("R18"))
        assertEquals("", AnimeAgeRating.kitsu("desconocida"))
    }

    @Test
    fun `el filtro adulto no depende solo de la consulta sfw`() {
        assertTrue(AnimeAgeRating.isAdultJikan("Rx - Hentai", emptyList()))
        assertTrue(AnimeAgeRating.isAdultJikan("R - 17+", listOf("Hentai")))
        assertFalse(AnimeAgeRating.isAdultJikan("PG-13", listOf("Action", "Fantasy")))
        assertTrue(AnimeAgeRating.isAdultKitsu("PG", nsfw = true))
        assertTrue(AnimeAgeRating.isAdultKitsu("R18", nsfw = false))
        assertFalse(AnimeAgeRating.isAdultKitsu("R", nsfw = false))
    }

    @Test
    fun `un perfil infantil solo ve anime TP o 7 mas`() {
        fun video(certification: String) = Video(
            id = "kitsu:1", title = "Anime", description = "", backdropUrl = "", cardUrl = "",
            year = 2024, durationMinutes = 24, rating = 0.0, certification = certification,
            quality = "HD", genres = emptyList(), videoUrl = "", type = VideoType.ANIME
        )
        val kids = Profile(id = "kids", name = "Peques", isKids = true)
        assertTrue(AnimeAgeRating.visible(video("TP"), kids))
        assertTrue(AnimeAgeRating.visible(video("7+"), kids))
        assertFalse(AnimeAgeRating.visible(video("12+"), kids))
        assertFalse(AnimeAgeRating.visible(video(""), kids))
        assertTrue(AnimeAgeRating.visible(video("16+"), null))
        assertFalse(
            AnimeAgeRating.visible(
                video("TP").copy(type = VideoType.MOVIE),
                null
            )
        )
    }
}
