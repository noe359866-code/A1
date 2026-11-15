package com.omnitv.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimePlaybackTest {

    private fun animeParent() = Video(
        id = "mal:5114",
        title = "Fullmetal",
        description = "",
        backdropUrl = "",
        cardUrl = "https://cdn.example/poster.jpg",
        year = 2009,
        durationMinutes = 24,
        rating = 0.0,
        certification = "16+",
        quality = "HD",
        genres = emptyList(),
        videoUrl = "",
        type = VideoType.ANIME,
        isRemote = true,
        source = "Jikan",
        remoteType = "series"
    )

    @Test
    fun `el id de un titulo de anime se resuelve como serie`() {
        assertEquals("series" to "mal:5114", StreamHub.stremioId(animeParent()))
        assertEquals("anime", AnimeIds.alternateType("series"))
    }

    @Test
    fun `Cloudstream recibe el titulo y el episodio correcto con id mal`() {
        val parent = animeParent()
        val episode = CatalogRepository.episodeVideo(
            parent,
            Episode(
                id = "s1e2",
                title = "Dos",
                number = 2,
                durationMinutes = 24,
                description = "",
                thumbnailUrl = "",
                videoUrl = "",
                season = 1,
                streamId = "mal:5114:1:2"
            )
        )
        val request = JSONObject(CloudstreamBridge.request(episode))
        assertEquals("Fullmetal", request.optString("title"))
        assertEquals("series", request.optString("type"))
        assertEquals("mal:5114:1:2", request.optString("id"))
        assertEquals(2009, request.optInt("year"))
        assertEquals(1, request.optInt("season"))
        assertEquals(2, request.optInt("episode"))
        assertFalse(request.has("imdbId"))
    }

    @Test
    fun `el puente Nuvio no confunde el id de Kitsu con temporada`() {
        val repo = NuvioPluginManifest(
            name = "Repo",
            version = "1",
            scrapers = emptyList(),
            raw = JSONObject(),
            url = "https://repo.example/manifest.json"
        )
        val scraper = NuvioScraper(id = "scraper", name = "Scraper")
        val body = JSONObject(
            NuvioBridgeRequest.body(repo, scraper, "series", "kitsu:7442:1:2")
        )
        val extra = body.optJSONObject("extra")!!
        assertEquals("tv", body.optString("type"))
        assertEquals("kitsu:7442:1:2", body.optString("id"))
        assertEquals(1, extra.optInt("season"))
        assertEquals(2, extra.optInt("episode"))
        assertFalse(extra.has("imdbId"))
        assertFalse(extra.has("tmdbId"))
        assertTrue(extra.optInt("season") != 7442)
    }
}
