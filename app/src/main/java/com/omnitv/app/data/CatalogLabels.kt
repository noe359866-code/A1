package com.omnitv.app.data

/**
 * Texto editorial de las filas del catálogo, independiente de su procedencia.
 * No mezclar aquí el nombre del addon, la URL ni el tipo técnico de Stremio:
 * esos datos se conservan en [Video.source] y [RemoteCatalog] para resolver
 * las fichas y la reproducción, no para presentar el contenido.
 */
internal object CatalogLabels {
    fun title(catalog: StremioCatalog): String = catalog.name.trim().ifBlank {
        // Un catálogo sin nombre no debe exponer su id ni quedarse en blanco.
        when (catalog.type) {
            "movie" -> "Películas"
            "series" -> "Series"
            "channel" -> "Canales"
            "tv" -> "Televisión"
            else -> "Catálogo"
        }
    }
}
