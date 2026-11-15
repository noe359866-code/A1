package com.omnitv.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JikanClientTest {

    @Test
    fun `normaliza una ficha de Jikan sin inventar streams`() {
        val json = JSONObject(
            """
            {"data":[{
              "mal_id":5114,
              "title":"Fullmetal Alchemist: Brotherhood",
              "title_english":"Fullmetal Alchemist: Brotherhood",
              "type":"TV",
              "episodes":64,
              "score":9.1,
              "rating":"R - 17+ (violence & profanity)",
              "duration":"24 min per ep",
              "year":2009,
              "synopsis":"Dos hermanos buscan la piedra filosofal.",
              "genres":[{"name":"Action"},{"name":"Fantasy"}],
              "themes":[{"name":"Military"}],
              "images":{"jpg":{"large_image_url":"https://cdn.example/poster.jpg"}},
              "trailer":{"images":{"maximum_image_url":"https://cdn.example/backdrop.jpg"}}
            }]}
            """.trimIndent()
        )

        val video = JikanClient.parseList(json).single()
        assertEquals("mal:5114", video.id)
        assertEquals("Fullmetal Alchemist: Brotherhood", video.title)
        assertEquals(VideoType.ANIME, video.type)
        assertEquals("series", video.remoteType)
        assertEquals(2009, video.year)
        assertEquals(24, video.durationMinutes)
        assertEquals(9.1, video.rating, 0.0)
        assertEquals("16+", video.certification)
        assertEquals(listOf("Action", "Fantasy", "Military"), video.genres)
        assertEquals("https://cdn.example/poster.jpg", video.cardUrl)
        assertEquals("https://cdn.example/backdrop.jpg", video.backdropUrl)
        assertEquals("", video.videoUrl)
        assertTrue(video.isRemote)
        assertEquals("Jikan", video.source)
        assertEquals("Jikan", video.badge)
    }

    @Test
    fun `descarta contenido adulto y una nota desconocida sigue sin nota`() {
        val json = JSONObject(
            """
            {"data":[
              {"mal_id":1,"title":"Safe","rating":"G - All Ages","score":0,
               "images":{"jpg":{"image_url":"https://cdn.example/safe.jpg"}}},
              {"mal_id":2,"title":"Adult","rating":"Rx - Hentai",
               "genres":[{"name":"Hentai"}],
               "images":{"jpg":{"image_url":"https://cdn.example/adult.jpg"}}}
            ]}
            """.trimIndent()
        )

        val videos = JikanClient.parseList(json)
        assertEquals(1, videos.size)
        assertEquals("mal:1", videos.single().id)
        assertEquals(0.0, videos.single().rating, 0.0)
        assertFalse(videos.single().hasRating)
        assertEquals("TP", videos.single().certification)
    }

    @Test
    fun `lee IMDb externo y prepara episodios con ese id de stream`() {
        val full = JSONObject(
            """
            {"data":{
              "mal_id":5114,
              "title":"Fullmetal Alchemist: Brotherhood",
              "external":[
                {"name":"Official Site","url":"https://example.com/fma"},
                {"name":"IMDb","url":"https://www.imdb.com/title/tt0421357/"}
              ]
            }}
            """.trimIndent()
        ).optJSONObject("data")!!
        assertEquals("tt0421357", JikanClient.imdbId(full))

        val episodesJson = JSONObject(
            """
            {"data":[
              {"mal_id":1,"title":"Uno","title_english":"First","aired":"2009-04-05T00:00:00+00:00"},
              {"mal_id":2,"title":"Segundo"}
            ],"pagination":{"has_next_page":false}}
            """.trimIndent()
        )
        val episodes = JikanClient.parseEpisodes(episodesJson, "tt0421357", 24, "https://cdn.example/poster.jpg")
        assertEquals(2, episodes.size)
        assertEquals("First", episodes[0].title)
        assertEquals("s1e1", episodes[0].id)
        assertEquals("tt0421357:1:1", episodes[0].streamId)
        assertEquals("Segundo", episodes[1].title)
        assertEquals("s1e2", episodes[1].id)
        assertEquals("tt0421357:1:2", episodes[1].streamId)
        assertEquals(24, episodes[0].durationMinutes)
        assertEquals("https://cdn.example/poster.jpg", episodes[0].thumbnailUrl)
        assertEquals("2009-04-05T00:00:00+00:00", episodes[0].aired)
    }

    @Test
    fun `las urls codifican la consulta y piden contenido seguro`() {
        val url = JikanClient.url(
            "/anime",
            listOf("q" to "attack on titan", "limit" to "20", "sfw" to "true")
        )
        assertTrue(url.startsWith("https://api.jikan.moe/v4/anime?"))
        assertTrue(url.contains("q=attack%20on%20titan"))
        assertTrue(url.contains("limit=20"))
        assertTrue(url.contains("sfw=true"))
    }
}
