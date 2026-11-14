package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del teclado en pantalla del buscador.
 *
 * Es la parte que se puede verificar sin un televisor: el modelo es lógica pura
 * (qué escribe cada tecla, cómo borra, que ninguna tecla de control meta basura
 * en la consulta).
 */
class TvKeyboardTest {

    private fun type(vararg keys: String): String =
        keys.fold("") { acc, key -> TvKeypad.apply(acc, key) }

    @Test
    fun `las teclas de letras se anaden al final`() {
        assertEquals("A", type("A"))
        assertEquals("ABC", type("A", "B", "C"))
        assertEquals("HOLA", type("H", "O", "L", "A"))
    }

    @Test
    fun `borrar quita el ultimo caracter`() {
        assertEquals("HOL", TvKeypad.apply("HOLA", TvKeypad.BACKSPACE))
        assertEquals("", TvKeypad.apply("A", TvKeypad.BACKSPACE))
    }

    @Test
    fun `borrar con el campo vacio no rompe nada`() {
        // El usuario puede pulsar borrar antes de escribir: tiene que ser inocuo
        // y no lanzar excepción (era el caso fácil de dejar sin cubrir).
        assertEquals("", TvKeypad.apply("", TvKeypad.BACKSPACE))
    }

    @Test
    fun `el espacio solo se anade cuando aporta`() {
        // Sin texto no empieza con espacio y no se acumulan espacios: en un
        // mando es facilísimo pulsar «ESPACIO» dos veces sin querer.
        assertEquals("", TvKeypad.apply("", TvKeypad.SPACE))
        assertEquals("HOLA ", TvKeypad.apply("HOLA", TvKeypad.SPACE))
        assertEquals("HOLA ", TvKeypad.apply("HOLA ", TvKeypad.SPACE))
        assertEquals("HOLA M", type("H", "O", "L", "A", TvKeypad.SPACE, "M"))
    }

    @Test
    fun `listo no modifica el texto`() {
        assertEquals("ABC", TvKeypad.apply("ABC", TvKeypad.DONE))
    }

    @Test
    fun `respeta la longitud maxima`() {
        val text = "A".repeat(64)
        assertEquals(64, TvKeypad.apply(text, "B").length)
    }

    @Test
    fun `ignora los caracteres de control`() {
        // Un carácter de control no debe colarse en la consulta porque llegue
        // desde una tecla física del mando.
        assertEquals("A", TvKeypad.apply("A", "\n"))
    }

    @Test
    fun `la distribucion tiene las letras necesarias y la enie`() {
        val all = TvKeypad.rows.joinToString("")
        assertTrue("falta la Ñ", all.contains("Ñ"))
        assertTrue("falta la A", all.contains("A"))
        assertTrue("falta la Z", all.contains("Z"))
        assertEquals("no hay teclas repetidas", all.length, all.toSet().size)
        assertEquals("las letras van en mayúsculas", all, all.uppercase())
    }

    @Test
    fun `las teclas de accion no se confunden con caracteres`() {
        assertTrue(TvKeypad.isAction(TvKeypad.DONE))
        assertFalse(TvKeypad.isAction("A"))
        assertTrue(TvKeypad.isBackspace(TvKeypad.BACKSPACE))
        assertFalse(TvKeypad.isBackspace("A"))
    }

    @Test
    fun `la busqueda normaliza acentos y mayusculas`() {
        // El teclado no tiene tildes: «accion» tiene que encontrar «Acción».
        assertTrue(SearchText.matches("Acción", "accion"))
        assertTrue(SearchText.matches("Películas", "PELICULAS"))
        assertTrue(SearchText.matches("Ciencia ficción", "ficcion"))
        assertTrue(SearchText.matches("Animación", "anim"))
    }

    @Test
    fun `la busqueda no inventa coincidencias`() {
        assertFalse(SearchText.matches("Acción", "drama"))
        assertFalse(SearchText.matches("Sintel", "zzz"))
    }

    @Test
    fun `normalize colapsa espacios y recorta`() {
        assertEquals("ciencia ficcion", SearchText.normalize("  Ciencia   Ficción  "))
        assertEquals("", SearchText.normalize("   "))
    }

    @Test
    fun `la consulta vacia no filtra`() {
        // `matches` no decide qué hacer con la consulta vacía: eso es cosa de la
        // pantalla, que muestra el estado inicial.
        assertTrue(SearchText.matches("lo que sea", ""))
    }
}
