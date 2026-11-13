package com.omnitv.app.data

/**
 * Utilidades de lectura de HTML para los parsers de catálogo
 * ([CinejoyClient], [SweetFoxClient]).
 *
 * Regla de la casa: **un dato que no se encuentra no se inventa**. La
 * valoración desconocida es `0.0` ([Video.hasRating] = `false`) y la
 * interfaz la oculta.
 */
internal object ScrapeText {

    private val blockRegex =
        Regex("""<(script|style)\b.*?</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tagRegex = Regex("""<[^>]*>""")

    // Un decimal «7.4» suelto: no forma parte de un número mayor («17.45»,
    // «1.2.3»), ni de un porcentaje, ni va pegado a letras («2.5K», «v1.0»)
    private val ratingRegex = Regex("""(?<![\w.,])(\d{1,2}\.\d)(?![\w.,%])""")

    /**
     * Texto visible de un fragmento de HTML: sin etiquetas ni sus atributos
     * (rutas SVG, estilos, URLs…), que es donde aparecen decimales que no
     * son valoraciones. Tolera que el fragmento empiece o acabe a mitad de
     * una etiqueta.
     */
    fun visibleText(fragment: String): String {
        var text = fragment
        // Empieza dentro de una etiqueta («href="…">Texto»): se descarta el resto de ella
        val firstClose = text.indexOf('>')
        val firstOpen = text.indexOf('<')
        if (firstClose >= 0 && (firstOpen < 0 || firstClose < firstOpen)) {
            text = text.substring(firstClose + 1)
        }
        text = blockRegex.replace(text, " ")
        text = tagRegex.replace(text, " ")
        // Acaba dentro de una etiqueta («…<img src="»): se descarta el trozo
        val lastOpen = text.lastIndexOf('<')
        if (lastOpen >= 0) text = text.substring(0, lastOpen)
        return text
    }

    /**
     * Valoración (0–10) que aparece como texto visible en la ficha
     * [cardHtml], o `0.0` si no hay ninguna. [title] se excluye de la
     * búsqueda para que un título como «Evangelion: 1.0» no pase por nota.
     */
    fun rating(cardHtml: String, title: String = ""): Double {
        var text = visibleText(cardHtml)
        if (title.isNotBlank()) text = text.replace(title, " ")
        for (match in ratingRegex.findAll(text)) {
            val value = match.groupValues[1].toDoubleOrNull() ?: continue
            if (value > 0.0 && value <= 10.0) return value
        }
        return 0.0
    }
}
