package com.omnitv.app.data

import java.text.Normalizer

/**
 * Normalización del texto de búsqueda.
 *
 * El teclado en pantalla de la tele no tiene teclas de acentos (QWERTY en
 * mayúsculas, para escribir con el mando sin pelearse), así que quien busca
 * «accion» tiene que encontrar «Acción» y quien busca «PELICULAS» tiene que
 * encontrar «Películas». Aquí se quitan los diacríticos y se pasa todo a
 * minúsculas antes de comparar.
 *
 * Es lógica pura (sin Android) y se prueba con tests de JVM.
 */
object SearchText {

    /**
     * Texto comparable: minúsculas, sin acentos y con los espacios colapsados.
     *
     * `"  Acción  "` → `"accion"`. Se usa en la consulta y en cada campo del
     * catálogo, para que la comparación sea simétrica.
     */
    fun normalize(text: String): String {
        val sinAcentos = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return sinAcentos.lowercase().trim().replace(Regex("\\s+"), " ")
    }

    /**
     * `true` si [haystack] contiene a [query] ignorando acentos y mayúsculas.
     *
     * Con [query] vacía devuelve `true`: la pantalla decide qué hacer con la
     * consulta vacía, aquí no se inventa un resultado.
     */
    fun matches(haystack: String, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return normalize(haystack).contains(q)
    }
}
