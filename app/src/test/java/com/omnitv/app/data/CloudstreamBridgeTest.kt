package com.omnitv.app.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudstreamBridgeTest {
    private fun video() = Video(
        id = "tt1~s1e2", title = "Serie · Episodio", description = "", backdropUrl = "", cardUrl = "",
        year = 2024, durationMinutes = 0, rating = 0.0, certification = "", quality = "", genres = emptyList(),
        videoUrl = "", type = VideoType.SERIES, remoteType = "series", streamId = "tt1:1:2"
    )

    @Test fun `solo se aceptan puentes permitidos sin query`() {
        assertEquals("http://192.168.1.9:8789", CloudstreamBridge.normalize("http://192.168.1.9:8789/"))
        assertNull(CloudstreamBridge.normalize("http://public.example:8789"))
        assertNull(CloudstreamBridge.normalize("https://bridge.example?token=x"))
    }

    @Test fun `la peticion conserva titulo tipo ids y episodio`() {
        val json = JSONObject(CloudstreamBridge.request(video()))
        assertEquals("Serie", json.getString("title"))
        assertEquals("series", json.getString("type"))
        assertEquals("tt1", json.getString("imdbId"))
        assertEquals(1, json.getInt("season"))
        assertEquals(2, json.getInt("episode"))
    }

    @Test fun `sin puente no se inventan fuentes`() = runBlocking {
        assertTrue(CloudstreamBridge.resolve(video()).isEmpty())
    }

    @Test fun `StreamHub incorpora el adaptador Cloudstream solo cuando esta configurado`() = runBlocking {
        val addons = StreamHub.configuredAddons(emptyList(), emptyList(), emptyList(), "http://192.168.1.9:8789")
        assertEquals("cloudstream-bridge", addons.single().id)
        assertTrue(StreamHub.configuredAddons(emptyList(), emptyList(), emptyList(), "").isEmpty())
    }
}
