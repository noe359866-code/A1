package com.omnitv.app.plugins

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Registro de extractores: matching por host, caché y política de red. */
class ExtractorRegistryTest {

    private val fake = object : Extractor {
        override val name = "FakeTest"
        override val domains = listOf("fake.example")
        override suspend fun extract(url: String): ExtractedStream = ExtractedStream("https://cdn.example/v.mp4")
    }

    @Before
    fun setUp() {
        ExtractorRegistry.clearCache()
        ExtractorRegistry.register(fake)
    }

    @After
    fun tearDown() {
        ExtractorRegistry.unregister(fake.name)
        ExtractorRegistry.clearCache()
    }

    @Test
    fun `canHandle compara el host real y no la URL completa`() {
        assertTrue(MixdropExtractor.canHandle("https://mixdrop.co/e/1"))
        assertTrue(MixdropExtractor.canHandle("https://e.mixdrop.co/e/1"))
        assertFalse(MixdropExtractor.canHandle("https://mixdrop.co.attacker.example/e/1"))
        assertFalse(MixdropExtractor.canHandle("https://example.com/?next=https://mixdrop.co/e/1"))
        assertFalse(MixdropExtractor.canHandle("no-es-una-url"))
    }

    @Test
    fun `findFor elige el extractor del dominio`() {
        assertEquals("FakeTest", ExtractorRegistry.findFor("https://fake.example/e/1")?.name)
        assertNull(ExtractorRegistry.findFor("https://fake.example.attacker.test/e/1"))
    }

    @Test
    fun `resolve cachea el exito y no repite la red`() = runBlocking {
        val calls = AtomicInteger(0)
        val extract: suspend (Extractor, String) -> ExtractedStream? = { _, _ ->
            calls.incrementAndGet()
            ExtractedStream("https://cdn.example/v.mp4")
        }
        val first = ExtractorRegistry.resolve("https://fake.example/e/1", extract)
        val second = ExtractorRegistry.resolve("https://fake.example/e/1", extract)
        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(1, calls.get())
    }

    @Test
    fun `resolve descarta un enlace que no cumple NetPolicy`() = runBlocking {
        val result = ExtractorRegistry.resolve("https://fake.example/e/2") { _, _ ->
            ExtractedStream("http://public.example/v.mp4")
        }
        assertNull(result)
        // No se cachea: un intento posterior con un enlace válido sí resuelve.
        val valid = ExtractorRegistry.resolve("https://fake.example/e/2") { _, _ ->
            ExtractedStream("https://cdn.example/v.mp4")
        }
        assertNotNull(valid)
    }
}
