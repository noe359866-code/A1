package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** El D-pad no puede escapar del teclado hacia la barra o los resultados. */
class TvKeyboardNavigationTest {
    @Test
    fun `los cuatro bordes mantienen el foco dentro del teclado`() {
        assertEquals(TvKeypad.Position(0, 0), TvKeypad.move(TvKeypad.Position(0, 0), TvKeypad.Direction.UP))
        assertEquals(TvKeypad.Position(0, 0), TvKeypad.move(TvKeypad.Position(0, 0), TvKeypad.Direction.LEFT))
        val lastRow = TvKeypad.rows.lastIndex
        val lastColumn = TvKeypad.rows.last().lastIndex
        assertEquals(TvKeypad.Position(lastRow, lastColumn),
            TvKeypad.move(TvKeypad.Position(lastRow, lastColumn), TvKeypad.Direction.RIGHT))
        val lastAction = TvKeypad.actions.lastIndex
        assertEquals(TvKeypad.Position(TvKeypad.rows.size, lastAction),
            TvKeypad.move(TvKeypad.Position(TvKeypad.rows.size, lastAction), TvKeypad.Direction.DOWN))
    }

    @Test
    fun `bajar desde la ultima letra llega a listo y volver sube al teclado`() {
        val last = TvKeypad.Position(TvKeypad.rows.lastIndex, TvKeypad.rows.last().lastIndex)
        val done = TvKeypad.move(last, TvKeypad.Direction.DOWN)
        assertEquals(TvKeypad.rows.size, done.row)
        assertEquals(TvKeypad.actions.lastIndex, done.column)
        assertEquals(last, TvKeypad.move(done, TvKeypad.Direction.UP))
    }

    @Test
    fun `las teclas de accion ejecutan su funcion sin escribir su etiqueta`() {
        assertEquals("A B", TvKeypad.apply("A", TvKeypad.SPACE).let { TvKeypad.apply(it, "B") })
        assertEquals("A", TvKeypad.apply("AB", TvKeypad.BACKSPACE))
        assertEquals("A", TvKeypad.apply("A", TvKeypad.DONE))
    }

    @Test
    fun `una posicion guardada fuera de rango se normaliza`() {
        val moved = TvKeypad.move(TvKeypad.Position(99, 99), TvKeypad.Direction.LEFT)
        assertEquals(TvKeypad.rows.size, moved.row)
        assertEquals(TvKeypad.actions.lastIndex - 1, moved.column)
    }
}
