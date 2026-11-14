package com.omnitv.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHubTest {
    private val addon = ProjectStreamAddon("https://addon.example", "Addon")

    @Test
    fun `acepta la forma simple con array en la raiz`() {
        val json = JSONArray("""[{"label":"Principal","url":"https://cdn.example/a.m3u8?token=1","quality":"720p"}]""")
        val link = addon.parseStreams(json).single()
        assertEquals("Principal", link.label)
        assertEquals(StreamFormat.HLS, link.format)
        assertEquals("720p", link.quality)
    }

    @Test
    fun `lee la forma Stremio y no acepta HTTP publico ni credenciales`() {
        val json = JSONObject("""{"streams":[
            {"name":"LAN","url":"http://192.168.1.8:8787/v.mp4"},
            {"name":"Malo","url":"http://public.example/v.mp4"},
            {"name":"Credenciales","url":"https://user:pass@cdn.example/v.mp4"},
            {"name":"Torrent","infoHash":"abc"}
        ]}""")
        val link = addon.parseStreams(json).single()
        assertEquals("LAN", link.label)
        assertEquals(StreamFormat.MP4, link.format)
    }

    @Test
    fun `una respuesta vacia o inesperada no inventa fuentes`() {
        assertTrue(addon.parseStreams(null).isEmpty())
        assertTrue(addon.parseStreams(JSONObject()).isEmpty())
        assertTrue(addon.parseStreams(JSONArray()).isEmpty())
    }

    @Test
    fun `el episodio remoto usa su id Stremio y tipo series`() {
        val episode = Video(
            id = "tt0944947~s1e2", title = "Episodio", description = "",
            backdropUrl = "", cardUrl = "", year = 0, durationMinutes = 0,
            rating = 0.0, certification = "", quality = "", genres = emptyList(),
            videoUrl = "", type = VideoType.SERIES, remoteType = "series",
            streamId = "tt0944947:1:2"
        )
        assertEquals("series" to "tt0944947:1:2", StreamHub.stremioId(episode))
    }
}
