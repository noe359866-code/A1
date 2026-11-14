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

    /** Teclas que cierran o confirman, no escriben. */
    fun isAction(key: String): Boolean = key == DONE

    /** `true` si la tecla borra en vez de escribir. */
    fun isBackspace(key: String): Boolean = key == BACKSPACE
}
