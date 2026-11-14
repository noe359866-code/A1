package com.omnitv.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La ficha de un addon de Stremio puede no traer valoración, año o duración.
 * Antes se rellenaban con 7.0 / 0 y se mostraban como si fueran datos reales;
 * ahora quedan a 0 y la interfaz los oculta ([Video.hasRating]).
 */
class StremioClientTest {

    private fun meta(vararg pairs: Pair<String, Any>) = JSONObject(mapOf(*pairs))

    private fun seriesMeta() = meta(
        "id" to "tt0944947",
        "type" to "series",
        "name" to "Juego de tronos",
        "poster" to "https://images.metahub.space/poster/medium/tt0944947/img",
        "year" to "2011",
        "genres" to JSONArray(listOf("Drama", "Fantasía")),
        "videos" to JSONArray(
            listOf(
                meta("id" to "tt0944947:1:1", "season" to 1, "episode" to 1, "name" to "Invernalia"),
                meta("id" to "tt0944947:1:2", "season" to 1, "episode" to 2, "name" to "El Camino Real")
            )
        )
    )

    @Test
    fun `sin valoracion el titulo queda sin nota`() {
        val video = StremioClient.parseVideo(meta("id" to "tt1", "name" to "Sin nota", "year" to "2024"), "Cinemeta")

        assertEquals(0.0, video.rating, 0.0)
        assertFalse(video.hasRating)
        assertEquals("tt1", video.id)
        assertEquals(2024, video.year)
    }

    @Test
    fun `lee la valoracion de imdbRating y de app_extras`() {
        val direct = StremioClient.parseVideo(meta("id" to "tt2", "imdbRating" to "8.9"), "Cinemeta")
        assertEquals(8.9, direct.rating, 0.0)
        assertTrue(direct.hasRating)

        val extras = meta(
            "id" to "tt3",
            "app_extras" to meta("ratings" to meta("imdb" to meta("rating" to "7.6")))
        )
        assertEquals(7.6, StremioClient.parseVideo(extras, "Nuvio").rating, 0.0)
    }

    @Test
    fun `una valoracion basura no se convierte en numero`() {
        val video = StremioClient.parseVideo(meta("id" to "tt4", "imdbRating" to "N/A"), "Cinemeta")
        assertEquals(0.0, video.rating, 0.0)
    }

    @Test
    fun `parsea una serie con episodios`() {
        val video = StremioClient.parseVideo(seriesMeta(), "Cinemeta")

        assertEquals(VideoType.SERIES, video.type)
        assertEquals(2011, video.year)
        assertEquals(0.0, video.rating, 0.0)
        assertEquals(2, video.episodes.size)
        assertEquals("Invernalia", video.episodes[0].title)
        assertEquals(1, video.episodes[0].season)
        assertEquals("tt0944947:1:2", video.episodes[1].streamId)
        assertEquals("1 temporada · 2 episodios", video.episodesLabel)
    }

    @Test
    fun `sin duracion ni ano no se inventan valores`() {
        val video = StremioClient.parseVideo(meta("id" to "tt5", "name" to "Sin nada"), "Nuvio")

        assertEquals(0, video.year)
        assertEquals(0, video.durationMinutes)
        assertEquals("0 min", video.durationLabel)
        assertEquals(0.0, video.rating, 0.0)
    }
}
