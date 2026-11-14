package com.omnitv.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBridgeTest {

    private val original = StreamSource(
        label = "CDN",
        url = "https://cdn.example/video.mkv",
        headers = mapOf("Referer" to "https://addon.example/", "Cookie" to "no-enviar", "X-Test" to "ok")
    )

    @Test fun `normaliza solo puentes permitidos sin query`() {
        assertEquals("http://192.168.1.20:8790", MediaBridge.normalize(" http://192.168.1.20:8790/ "))
        assertNull(MediaBridge.normalize("http://public.example:8790"))
        assertNull(MediaBridge.normalize("https://bridge.example?token=x"))
        assertNull(MediaBridge.normalize(""))
    }

    @Test fun `la peticion conserva cabeceras seguras y el codigo de error`() {
        val json = JSONObject(MediaBridge.requestBody(original, "ERROR_CODE_DECODER_INIT_FAILED"))
        assertEquals(original.url, json.getString("url"))
        assertEquals("ERROR_CODE_DECODER_INIT_FAILED", json.getString("reason"))
        val headers = json.getJSONObject("headers")
        assertEquals("https://addon.example/", headers.getString("Referer"))
        assertEquals("ok", headers.getString("X-Test"))
        assertFalse(headers.has("Cookie"))
    }

    @Test fun `respuesta relativa del mismo origen se convierte en fuente hls`() {
        val json = JSONObject("""{"action":"remux","url":"/media/abc/playlist.m3u8","format":"hls"}""")
        val repaired = MediaBridge.parseResponse(json, original, "http://192.168.1.20:8790", mapOf("Authorization" to "Bearer token"))
        requireNotNull(repaired)
        assertEquals("http://192.168.1.20:8790/media/abc/playlist.m3u8", repaired.url)
        assertEquals(StreamFormat.HLS, repaired.format)
        assertEquals("CDN · puente", repaired.label)
        assertEquals(mapOf("Authorization" to "Bearer token"), repaired.headers)
    }

    @Test fun `rechaza respuestas directas de otro origen o fuera de media`() {
        val base = "https://bridge.example"
        assertNull(MediaBridge.parseResponse(JSONObject("""{"action":"direct"}"""), original, base))
        assertNull(MediaBridge.parseResponse(JSONObject("""{"action":"remux","url":"https://other.example/media/a.m3u8"}"""), original, base))
        assertNull(MediaBridge.parseResponse(JSONObject("""{"action":"remux","url":"/private/a.m3u8"}"""), original, base))
        assertNull(MediaBridge.parseResponse(JSONObject("""{"action":"remux","url":"http://public.example/media/a.m3u8"}"""), original, "http://192.168.1.20:8790"))
    }

    @Test fun `formato declarado prevalece sobre extension`() {
        val json = JSONObject("""{"action":"transcode","url":"/media/abc/playlist","format":"hls"}""")
        val repaired = MediaBridge.parseResponse(json, original, "https://bridge.example")
        assertTrue(repaired?.format == StreamFormat.HLS)
    }
}
