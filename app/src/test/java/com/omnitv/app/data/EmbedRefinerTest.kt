package com.omnitv.app.data

import com.omnitv.app.plugins.ExtractedStream
import com.omnitv.app.plugins.ExtractorRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/** El adaptador que conecta los extractores con StreamSource, sin red. */
class EmbedRefinerTest {

    @Before
    fun reset() {
        ExtractorRegistry.clearCache()
    }

    @Test
    fun `un enlace directo no se toca`() = runBlocking {
        val source = StreamSource("CDN", "https://cdn.example/v.mp4")
        var called = false
        val result = EmbedRefiner.refine(source) { called = true; ExtractedStream("https://otro.example/x.m3u8") }
        assertFalse("no debe invocar al extractor", called)
        assertEquals(source, result)
    }

    @Test
    fun `un embed se resuelve y propaga headers y subtitulos`() = runBlocking {
        val source = StreamSource("Mixdrop", "https://mixdrop.co/e/abc")
        val result = EmbedRefiner.refine(source) {
            ExtractedStream(
                url = "https://cdn.example/a.m3u8?token=1",
                quality = "720p",
                headers = mapOf(
                    "Referer" to "https://mixdrop.co/",
                    "Host" to "evil.example",
                    "Bad\nHeader" to "x",
                    "Authorization\r" to "y"
                ),
                subtitles = listOf(
                    "https://cdn.example/a.vtt",
                    "https://cdn.example/a.vtt",
                    "https://otro.example/b.vtt",
                    "http://public.example/c.vtt"
                )
            )
        }
        assertEquals("https://cdn.example/a.m3u8?token=1", result.url)
        assertEquals(StreamFormat.HLS, result.format)
        assertEquals(mapOf("Referer" to "https://mixdrop.co/"), result.headers)
        assertEquals(listOf("https://cdn.example/a.vtt"), result.subtitles)
        assertEquals("Mixdrop", result.label)
    }

    @Test
    fun `un extractor no puede devolver HTTP publico`() = runBlocking {
        val source = StreamSource("Mixdrop", "https://mixdrop.co/e/abc")
        val result = EmbedRefiner.refine(source) { ExtractedStream("http://public.example/x.m3u8") }
        assertEquals(source, result)
    }

    @Test
    fun `un dominio parecido no activa ningun extractor`() = runBlocking {
        val source = StreamSource("Falso", "https://mixdrop.co.attacker.example/e/abc")
        var called = false
        val result = EmbedRefiner.refine(source) { called = true; ExtractedStream("https://cdn.example/x.m3u8") }
        assertFalse(called)
        assertEquals(source, result)
    }

    @Test
    fun `refineAll conserva el orden y el numero de fuentes`() = runBlocking {
        val sources = listOf(
            StreamSource("A", "https://cdn.example/a.mp4"),
            StreamSource("B", "https://cdn.example/b.mp4")
        )
        val result = EmbedRefiner.refineAll(sources)
        assertEquals(2, result.size)
        assertEquals("A", result[0].label)
        assertEquals("B", result[1].label)
    }
}
