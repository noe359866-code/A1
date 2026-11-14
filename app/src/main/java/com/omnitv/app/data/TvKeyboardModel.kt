package com.omnitv.app.data

/**
 * Modelo del teclado en pantalla de Omni TV.
 *
 * Es lógica pura (sin Compose ni Android) para poder probarla con tests de JVM:
 * qué teclas hay, qué escribe cada una y cómo se comportan el borrado y las
 * teclas especiales.
 *
 * Diseño: distribución **QWERTY en mayúsculas**, sin teclas de acentos. Un
 * teclado de TV se maneja con cuatro flechas y OK, así que cuantas menos teclas
 * y menos filas, menos pulsaciones para escribir. Los acentos se resuelven con
 * la búsqueda normalizando el texto (ver `SearchText`).
 */
object TvKeypad {

    /** Filas de letras, en orden de lectura. */
    val rows: List<String> = listOf(
        "1234567890",
        "QWERTYUIOP",
        "ASDFGHJKLÑ",
        "ZXCVBNM"
    )

    /** Tecla de borrar un carácter. */
    const val BACKSPACE = "⌫"

    /** Tecla de espacio. */
    const val SPACE = "ESPACIO"

    /** Tecla que cierra el teclado sin cambiar el texto. */
    const val DONE = "LISTO"

    /**
     * Aplica una tecla a [text] y devuelve el texto resultante.
     *
     * Es una función pura: el mismo estado + la misma tecla dan siempre el mismo
     * texto, así que se puede probar sin componer nada.
     *
     * - [BACKSPACE] quita el último carácter (o no hace nada si está vacío).
     * - [SPACE], [DONE] y las letras de control no modifican el texto.
     * - Cualquier otro carácter se añade al final. Se ignoran los caracteres de
     *   control y se colapsan los espacios repetidos (en la tele, escribir dos
     *   espacios seguidos por error es lo más habitual).
     */
    fun apply(text: String, key: String, maxLength: Int = 64): String = when (key) {
        BACKSPACE -> if (text.isEmpty()) text else text.dropLast(1)
        SPACE -> if (text.isEmpty() || text.endsWith(" ")) text else (text + " ").take(maxLength)
        DONE -> text
        else -> when {
            key.length != 1 -> text
            key[0].isISOControl() -> text
            text.length >= maxLength -> text
            else -> text + key
        }
    }

    /** Fila inferior del teclado, en el mismo orden que su interfaz. */
    val actions: List<String> = listOf(SPACE, BACKSPACE, DONE)

    data class Position(val row: Int, val column: Int)
    enum class Direction { UP, DOWN, LEFT, RIGHT }

    /**
     * Navegación explícita del D-pad. Los bordes se mantienen dentro del
     * teclado: Compose no debe saltar a las pestañas ni a los resultados
     * que quedan detrás. Las filas cortas conservan la posición relativa.
     */
    fun move(current: Position, direction: Direction): Position {
        val lengths = rows.map { it.length } + listOf(actions.size)
        val row = current.row.coerceIn(0, lengths.lastIndex)
        val column = current.column.coerceIn(0, lengths[row] - 1)
        return when (direction) {
            Direction.LEFT -> Position(row, (column - 1).coerceAtLeast(0))
            Direction.RIGHT -> Position(row, (column + 1).coerceAtMost(lengths[row] - 1))
            Direction.UP, Direction.DOWN -> {
                val nextRow = (row + if (direction == Direction.UP) -1 else 1)
                    .coerceIn(0, lengths.lastIndex)
                val nextColumn = if (nextRow == row) column else
                    column * (lengths[nextRow] - 1) / (lengths[row] - 1)
                Position(nextRow, nextColumn)
            }
        }
    }

    /** Teclas que cierran o confirman, no escriben. */
    fun isAction(key: String): Boolean = key == DONE

    /** `true` si la tecla borra en vez de escribir. */
    fun isBackspace(key: String): Boolean = key == BACKSPACE
}
