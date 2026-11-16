package com.omnitv.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapeo Kitsu → MyAnimeList para que AniSkip funcione con fichas `kitsu:`.
 * La resolución en red (`resolveMalId`) se cubre solo en dispositivo/CI.
 */
class SkipKitsuMappingTest {

    private fun episode(id: String) = Video(
        id = id.substringBefore(':') + "~e", title = "Anime · Uno", description = "", backdropUrl = "", cardUrl = "",
        year = 2024, durationMinutes = 24, rating = 0.0, certification = "", quality = "", genres = emptyList(),
        videoUrl = "", type = VideoType.ANIME, remoteType = "series", streamId = id
    )

    @Test
    fun `ids kitsu conservan temporada y episodio y cachean por kitsu`() {
        val target = SkipTarget.from(episode("kitsu:123:1:2"))!!
        assertEquals("123", target.kitsuId)
        assertNull(target.malId)
        assertTrue(target.isEpisode)
        assertEquals(1, target.season)
        assertEquals(2, target.episode)
        assertTrue(target.cacheKey.startsWith("kitsu:123:"))
    }

    @Test
    fun `combinado kitsu mas sfx de episodio conserva el id kitsu`() {
        val combined = episode("kitsu:42:2:8").copy(id = "kitsu:42~s2e8")
        val target = SkipTarget.from(combined)!!
        assertEquals("42", target.kitsuId)
        assertEquals(2, target.season)
        assertEquals(8, target.episode)
    }

    @Test
    fun `AniSkip devuelve null sin malId y la traduccion ocurre en query`() {
        val kitsuOnly = SkipTarget(null, null, 1, 2, kitsuId = "123")
        assertNull(SkipSegmentService.buildRequest(SkipSegmentParser.SOURCE_ANISKIP, kitsuOnly, true, 1440))
        // Con mal ya resuelto, la petición usa el malId (comportamiento verificado aparte)
        val resolved = SkipSegmentService.buildRequest(
            SkipSegmentParser.SOURCE_ANISKIP,
            kitsuOnly.copy(malId = "5114"), true, 1440
        )!!
        assertTrue(resolved.first.startsWith("https://api.aniskip.com/v2/skip-times/5114/2?"))
    }

    @Test
    fun `el cacheKey difiere entre mal y kitsu a igualdad de numeros`() {
        val mal = SkipTarget(null, null, 1, 2, malId = "123")
        val kitsu = SkipTarget(null, null, 1, 2, kitsuId = "123")
        assertTrue(mal.cacheKey != kitsu.cacheKey)
    }

    @Test
    fun `parseKitsuMalMapping coge el externalId myanimelist aunque haya varios mappings`() {
        val json = JSONObject("""{
            "data": [
                {"attributes": {"externalSite": "anidb", "externalId": "99999", "itemType": "anime", "itemId": "123"}},
                {"attributes": {"externalSite": "myanimelist/anime", "externalId": "5114", "itemType": "anime", "itemId": "123"}}
            ]
        }""")
        assertEquals("5114", SkipSegmentService.parseKitsuMalMapping(json))
    }

    @Test
    fun `parseKitsuMalMapping falla cerrado ante datos raros`() {
        assertNull(SkipSegmentService.parseKitsuMalMapping(JSONObject("""{}""")))
        assertNull(SkipSegmentService.parseKitsuMalMapping(JSONObject("""{"data": []}""")))
        // externalSite que no es MAL y externalId no numérico: ambos se descartan
        val wrongSite = JSONObject("""{"data": [
            {"attributes": {"externalSite": "anilist", "externalId": "12345", "itemType": "anime", "itemId": "7"}}
        ]}""")
        assertNull(SkipSegmentService.parseKitsuMalMapping(wrongSite))
        val notNumeric = JSONObject("""{"data": [
            {"attributes": {"externalSite": "myanimelist/anime", "externalId": "abc", "itemType": "anime", "itemId": "7"}}
        ]}""")
        assertNull(SkipSegmentService.parseKitsuMalMapping(notNumeric))
    }
}
