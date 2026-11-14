package com.omnitv.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioIntegrationTest {
    private val repoJson = JSONObject("""{
        "name":"Repo","version":"2.0.0","scrapers":[
            {"id":"anime","name":"Anime","supportedTypes":["movie","tv"],"filename":"providers/anime.js","enabled":true,"contentLanguage":["ja"],"formats":["m3u8"]},
            {"id":"off","enabled":false},
            {"id":"../escape","enabled":true},
            {"id":"anime","name":"Duplicado"}
        ]}""")

    @Test
    fun `indice Nuvio conserva metadatos y descarta ids peligrosos o duplicados`() {
        val repo = NuvioPluginManager.parseManifest(repoJson, "https://repo.example/manifest.json")!!
        assertEquals(2, repo.scrapers.size)
        assertEquals("providers/anime.js", repo.scrapers.first().filename)
        assertTrue(repo.scrapers.first().supports("series"))
        assertFalse(repo.scrapers.first().supports("channel"))
        assertFalse(repo.scrapers[1].enabled)
        assertNull(NuvioPluginManager.parseManifest(JSONObject("""{"resources":["stream"]}""")))
    }

    @Test
    fun `puente recibe tipo tv episodio e identificadores separados`() {
        val repo = NuvioPluginManager.parseManifest(repoJson, "https://repo.example/manifest.json")!!
        val body = JSONObject(NuvioBridgeRequest.body(repo, repo.scrapers.first(), "series", "tmdb:123:2:3"))
        assertEquals("tv", body.getString("type"))
        assertEquals("tmdb:123:2:3", body.getString("id"))
        assertEquals("https://repo.example/manifest.json", body.getString("repoUrl"))
        assertEquals("providers/anime.js", body.getString("filename"))
        val extra = body.getJSONObject("extra")
        assertEquals("123", extra.getString("tmdbId"))
        assertEquals(2, extra.getInt("season"))
        assertEquals(3, extra.getInt("episode"))
        assertTrue(extra.isNull("imdbId"))
        val imdb = JSONObject(NuvioBridgeRequest.body(repo, repo.scrapers.first(), "movie", "tt123")).getJSONObject("extra")
        assertEquals("tt123", imdb.getString("imdbId"))
        assertTrue(imdb.isNull("tmdbId"))
    }

    @Test
    fun `respuesta del puente admite results y cabeceras directas o Stremio`() {
        val json = JSONObject("""{"results":[
            {"name":"Anime","url":"https://cdn.example/a.m3u8","quality":"1080p","headers":{"Referer":"https://source.example/","Cookie":"no","Host":"evil"}},
            {"url":"https://cdn.example/b.mpd","behaviorHints":{"proxyHeaders":{"request":{"User-Agent":"TV"}}}},
            {"url":"http://public.example/a.mp4"},
            {"infoHash":"abc"}
        ]}""")
        val links = StreamResponse.parse(json, "Nuvio")
        assertEquals(2, links.size)
        assertEquals(StreamFormat.HLS, links[0].format)
        assertEquals(mapOf("Referer" to "https://source.example/"), links[0].headers)
        assertEquals(StreamFormat.DASH, links[1].format)
        assertEquals("TV", links[1].headers["User-Agent"])
    }

    @Test
    fun `notWebReady y enlaces no HTTP no llegan al reproductor`() {
        val json = JSONObject("""{"streams":[{"url":"https://a/v.mp4","behaviorHints":{"notWebReady":true}},{"url":"magnet:?xt=abc"},{"url":"https://user:pass@a/v.mp4"}]}""")
        assertTrue(StreamResponse.parse(json, "Nuvio").isEmpty())
        assertNotNull(StreamResponse.parse(JSONObject("""{"streams":[{"url":"https://a/v.mp4"}]}"""), "Nuvio").single())
    }
}
