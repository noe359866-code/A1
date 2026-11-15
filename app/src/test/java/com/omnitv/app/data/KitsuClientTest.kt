package com.omnitv.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsuClientTest {

    @Test
    fun `normaliza JSON API con generos incluidos`() {
        val json = JSONObject(
            """
            {"data":[{
              "id":"1","type":"anime",
              "attributes":{
                "canonicalTitle":"Cowboy Bebop",
                "titles":{"en":"Cowboy Bebop"},
                "synopsis":"Cazadores de recompensas en el espacio.",
                "startDate":"1998-04-03",
                "averageRating":"82.28",
                "episodeLength":25,
                "subtype":"TV",
                "ageRating":"R",
                "nsfw":false,
                "posterImage":{"large":"https://media.kitsu.app/poster.jpg"},
                "coverImage":{"large":"https://media.kitsu.app/cover.jpg"}
              },
              "relationships":{"genres":{"data":[
                {"type":"genres","id":"1"},{"type":"genres","id":"2"}
              ]}}
            }],
            "included":[
              {"id":"1","type":"genres","attributes":{"name":"Action"}},
              {"id":"2","type":"genres","attributes":{"name":"Space"}}
            ]}
            """.trimIndent()
        )

        val video = KitsuClient.parseList(json).single()
        assertEquals("kitsu:1", video.id)
        assertEquals("Cowboy Bebop", video.title)
        assertEquals(VideoType.ANIME, video.type)
        assertEquals("series", video.remoteType)
        assertEquals(1998, video.year)
        assertEquals(25, video.durationMinutes)
        assertEquals(8.228, video.rating, 0.0001)
        assertEquals("16+", video.certification)
        assertEquals(listOf("Action", "Space"), video.genres)
        assertEquals("https://media.kitsu.app/poster.jpg", video.cardUrl)
        assertEquals("https://media.kitsu.app/cover.jpg", video.backdropUrl)
        assertEquals("", video.videoUrl)
        assertEquals("Kitsu", video.source)
        assertEquals("Kitsu", video.badge)
    }

    @Test
    fun `descarta R18 y nsfw aunque la API no los filtre`() {
        val json = JSONObject(
            """
            {"data":[
              {"id":"2","attributes":{"canonicalTitle":"Safe","ageRating":"G","nsfw":false,
               "averageRating":"","posterImage":{"large":"https://media.kitsu.app/safe.jpg"}}},
              {"id":"3","attributes":{"canonicalTitle":"Adult","ageRating":"R18","nsfw":true,
               "averageRating":"90.0","posterImage":{"large":"https://media.kitsu.app/adult.jpg"}}}
            ]}
            """.trimIndent()
        )
        val videos = KitsuClient.parseList(json)
        assertEquals(1, videos.size)
        assertEquals("kitsu:2", videos.single().id)
        assertEquals("TP", videos.single().certification)
        assertEquals(0.0, videos.single().rating, 0.0)
        assertFalse(videos.single().hasRating)
    }

    @Test
    fun `lee una ficha individual y prefiere el titulo en ingles`() {
        val json = JSONObject(
            """
            {"data":{
              "id":"46474",
              "attributes":{
                "canonicalTitle":"Sousou no Frieren",
                "titles":{"en":"Frieren: Beyond Journey's End"},
                "subtype":"TV",
                "ageRating":"PG",
                "nsfw":false,
                "averageRating":"88.86",
                "startDate":"2023-09-29",
                "posterImage":{"large":"https://media.kitsu.app/frieren.jpg"}
              }
            }}
            """.trimIndent()
        )
        val video = KitsuClient.parseSingle(json)!!
        assertEquals("kitsu:46474", video.id)
        assertEquals("Frieren: Beyond Journey's End", video.title)
        assertEquals("7+", video.certification)
        assertEquals("series", video.remoteType)
    }

    @Test
    fun `lee episodios reales con temporada numero y miniatura`() {
        val json = JSONObject(
            """
            {"data":[
              {"id":"10","attributes":{"canonicalTitle":"To You Two Thousand Years Later",
                "seasonNumber":1,"number":1,"relativeNumber":1,"airdate":"2013-04-06","length":24,
                "synopsis":"Primero","thumbnail":{"original":"https://media.kitsu.app/ep1.jpg"}}},
              {"id":"11","attributes":{"canonicalTitle":"Second",
                "seasonNumber":1,"number":2,"relativeNumber":2}}
            ],"meta":{"count":25},"links":{"next":"https://kitsu.app/api/edge/anime/7442/episodes?page%5Boffset%5D=2"}}
            """.trimIndent()
        )
        val episodes = KitsuClient.parseEpisodes(json, "kitsu:7442", "https://media.kitsu.app/poster.jpg")
        assertEquals(2, episodes.size)
        assertEquals("s1e1", episodes[0].id)
        assertEquals(1, episodes[0].season)
        assertEquals("kitsu:7442:1:1", episodes[0].streamId)
        assertEquals(24, episodes[0].durationMinutes)
        assertEquals("https://media.kitsu.app/ep1.jpg", episodes[0].thumbnailUrl)
        assertEquals("2013-04-06", episodes[0].aired)
        assertEquals("s1e2", episodes[1].id)
        assertEquals("kitsu:7442:1:2", episodes[1].streamId)
        assertEquals("https://media.kitsu.app/poster.jpg", episodes[1].thumbnailUrl)
    }

    @Test
    fun `las urls codifican corchetes de JSON API`() {
        val url = KitsuClient.url(
            "/anime",
            listOf("filter[text]" to "frieren", "page[limit]" to "20")
        )
        assertTrue(url.startsWith("https://kitsu.app/api/edge/anime?"))
        assertTrue(url.contains("filter%5Btext%5D=frieren"))
        assertTrue(url.contains("page%5Blimit%5D=20"))
    }
}
