package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapeTextTest {

    private fun rating(html: String, title: String = "") = ScrapeText.rating(html, title)

    @Test
    fun `encuentra la valoracion que aparece como texto`() {
        assertEquals(7.4, rating("""<span class="r">7.4</span><span>2024</span>"""), 0.0)
        assertEquals(8.5, rating("""<div>★ 8.5</div>"""), 0.0)
        assertEquals(10.0, rating("""<b>10.0</b>"""), 0.0)
        assertEquals(6.3, rating("""href="/movie/1-x-2026" class="c"><span>2026</span> <span>6.3</span>"""), 0.0)
    }

    @Test
    fun `sin valoracion visible devuelve cero en vez de inventarla`() {
        assertEquals(0.0, rating("""<h3>Sin nota</h3><span>Movie</span><span>2027</span>"""), 0.0)
        assertEquals(0.0, rating(""), 0.0)
        assertEquals(0.0, rating("""<span>0.0</span>"""), 0.0)
    }

    @Test
    fun `no confunde los decimales de los atributos con una valoracion`() {
        val html = """<img src="/p/w500/a.jpg" style="opacity:0.5;transform:scale(1.2)">""" +
            """<svg viewBox="0 0 24 24" stroke-width="1.5"><path d="M12 2.5 15.1 8.6 22 9.3 17 14.1Z"/></svg>""" +
            """<h3 data-score="9.9">Título</h3><span>2026</span>"""
        assertEquals(0.0, rating(html), 0.0)
        assertEquals(7.1, rating("$html<span>7.1</span>"), 0.0)
    }

    @Test
    fun `ignora estilos y scripts incrustados`() {
        val html = """<style>.c{opacity: 0.8 }</style><script>var v = 1.5 ;</script><span>6.9</span>"""
        assertEquals(6.9, rating(html), 0.0)
    }

    @Test
    fun `no toma por valoracion otros numeros`() {
        for (text in listOf("17.45", "1.2.3", "95.5%", "2.5K", "v1.0", "12.5", "3,5", "1.0.", "x7.4")) {
            assertEquals("«$text» no es una valoración", 0.0, rating("<span>$text</span>"), 0.0)
        }
    }

    @Test
    fun `un decimal dentro del titulo no cuenta`() {
        val html = """<h3>Evangelion: 1.0 You Are (Not) Alone</h3><span>2007</span><span>7.7</span>"""
        assertEquals(7.7, rating(html, title = "Evangelion: 1.0 You Are (Not) Alone"), 0.0)
        assertEquals(0.0, rating("<h3>Evangelion: 1.0</h3>", title = "Evangelion: 1.0"), 0.0)
    }

    @Test
    fun `tolera fragmentos cortados a mitad de etiqueta`() {
        // Empieza dentro de <a …> y acaba dentro de <img …>
        val fragment = """href="/movie/5-x" data-v="3.3">Título <em>8.1</em><img src="/t/p/w500/9.9"""
        assertEquals(8.1, rating(fragment), 0.0)

        val text = ScrapeText.visibleText(fragment)
        assertTrue(text.contains("Título"))
        assertFalse(text.contains("3.3"))
        assertFalse(text.contains("9.9"))
    }

    @Test
    fun `un titulo sin valoracion no tiene nota ni afinidad que mostrar`() {
        val base = Video(
            id = "tmdb:1", title = "T", description = "", backdropUrl = "", cardUrl = "",
            year = 0, durationMinutes = 0, rating = 0.0, certification = "", quality = "HD",
            genres = emptyList(), videoUrl = ""
        )
        assertFalse(base.hasRating)
        assertTrue(base.copy(rating = 7.4).hasRating)
        assertEquals("7,4", base.copy(rating = 7.4).ratingLabel)
    }
}
