package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Orden de fuentes por calidad; a igualdad, se conserva el orden anterior. */
class StreamRankerTest {

    private fun source(label: String, quality: String = "", url: String = "https://cdn.example/${label.hashCode()}.mp4") =
        StreamSource(label = label, url = url, quality = quality)

    @Test
    fun `la mejor resolucion queda primera`() {
        val sorted = StreamRanker.sort(
            listOf(
                source("SD", "480p"),
                source("FHD", "1080p"),
                source("UHD", "2160p")
            ),
            preferred = "Auto"
        )
        assertEquals(listOf("UHD", "FHD", "SD"), sorted.map { it.label })
    }

    @Test
    fun `la preferencia concreta encabeza la lista sin romper el resto del orden`() {
        val sorted = StreamRanker.sort(
            listOf(
                source("UHD", "2160p"),
                source("FHD", "1080p"),
                source("HD720", "720p")
            ),
            preferred = "720p"
        )
        assertEquals("HD720", sorted.first().label)
        // El resto conserva su orden relativo por calidad
        assertEquals(listOf("UHD", "FHD"), sorted.drop(1).map { it.label })
    }

    @Test
    fun `auto no altera la estabilidad a igualdad de puntuacion`() {
        val sorted = StreamRanker.sort(
            listOf(source("A", "1080p"), source("B", "1080p"), source("C", "")),
            preferred = "Auto"
        )
        assertEquals(listOf("A", "B", "C"), sorted.map { it.label })
    }

    @Test
    fun `la calidad se detecta tambien en la etiqueta cuando falta el campo`() {
        assertTrue(StreamRanker.scoreText("Exclusivo Servidor 4K") >= 40)
        assertEquals(30, StreamRanker.scoreText("BluRay 1080p"))
        assertEquals(15, StreamRanker.scoreText("Mega")) // desconocida, ni primera ni última
    }

    @Test
    fun `una lista vacia o desconocida no explota`() {
        assertEquals(emptyList<StreamSource>(), StreamRanker.sort(emptyList(), "Auto"))
        val sorted = StreamRanker.sort(listOf(source("X", ""), source("Y", "")), "480p")
        assertEquals(2, sorted.size)
    }
}
