package com.omnitv.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cabeceras y subtítulos que llegan de addons/extractores hasta Media3. */
class StreamResponseTest {

    @Test
    fun `parseHeaders filtra claves prohibidas y saltos de linea`() {
        val json = JSONObject(
            """{"Referer":"https://x.example/","Host":"evil","Cookie":"a=b","Bad\nKey":"x","X":"line\rbreak"}"""
        )
        assertEquals(mapOf("Referer" to "https://x.example/"), StreamResponse.parseHeaders(json))
    }

    @Test
    fun `sanitizeHeaders aplica el mismo filtro a maps de extractores`() {
        val raw = mapOf(
            "Referer" to "https://mixdrop.co/",
            "Host" to "evil.example",
            "Authorization" to "Bearer no-a-otro-host",
            "Bad\nKey" to "x"
        )
        assertEquals(
            mapOf("Referer" to "https://mixdrop.co/", "Authorization" to "Bearer no-a-otro-host"),
            StreamResponse.sanitizeHeaders(raw)
        )
    }

    @Test
    fun `parseSubtitles acepta cadenas y objetos y descarta HTTP publico`() {
        val array = JSONArray(
            """["https://cdn.example/a.vtt",{"url":"https://cdn.example/b.vtt","lang":"es"},{"url":"http://public.example/c.vtt"},"https://cdn.example/a.vtt"]"""
        )
        assertEquals(
            listOf("https://cdn.example/a.vtt", "https://cdn.example/b.vtt"),
            StreamResponse.parseSubtitles(array)
        )
    }

    @Test
    fun `parse conserva los subtitulos del addon en el StreamLink`() {
        val json = JSONObject(
            """{"streams":[{"name":"A","url":"https://cdn.example/v.m3u8","subtitles":[{"url":"https://cdn.example/a.vtt"}]}]}"""
        )
        val link = StreamResponse.parse(json, "Fallback").single()
        assertEquals(listOf("https://cdn.example/a.vtt"), link.subtitles)
        assertEquals(StreamFormat.HLS, link.format)
    }
}
