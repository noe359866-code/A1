package com.omnitv.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** El título visible describe el contenido, no la instalación que lo sirve. */
class CatalogLabelsTest {
    private fun catalog(name: String, type: String = "movie") = StremioCatalog(type, "top", name)

    @Test
    fun `conserva el titulo editorial sin el tipo tecnico`() {
        assertEquals("Estrenos de la semana", CatalogLabels.title(catalog("Estrenos de la semana")))
    }

    @Test
    fun `limpia los espacios sobrantes del nombre`() {
        assertEquals("Tendencias", CatalogLabels.title(catalog("  Tendencias\t\n")))
    }

    @Test
    fun `un catalogo sin nombre tiene una alternativa legible`() {
        val expected = mapOf(
            "movie" to "Películas",
            "series" to "Series",
            "channel" to "Canales",
            "tv" to "Televisión",
            "otro" to "Catálogo"
        )
        expected.forEach { (type, title) ->
            assertEquals(title, CatalogLabels.title(catalog("", type)))
            assertEquals(title, CatalogLabels.title(catalog(" \t\n", type)))
        }
    }

    @Test
    fun `el nombre del manifest no se mezcla con el titulo de la fila`() {
        val json = """
            {
              "id":"proveedor.privado", "name":"Nombre del proveedor", "version":"1.0.0",
              "resources":["catalog"], "types":["movie"],
              "catalogs":[{"id":"top","type":"movie","name":"Películas populares"}]
            }
        """.trimIndent()
        val manifest = StremioManifestParser.parse(JSONObject(json))
        val title = CatalogLabels.title(manifest.catalogs.single())
        assertEquals("Películas populares", title)
        assertFalse(title.contains(manifest.name))
        assertFalse(title.contains(manifest.id))
        assertFalse(title.contains("movie"))
    }

    @Test
    fun `las plataformas y estudios siguen siendo categorias editoriales`() {
        // Netflix o Marvel pueden describir una colección; no son el nombre
        // del addon que la sirve y no deben desaparecer del catálogo.
        listOf("En Netflix", "Series Disney+", "Marvel").forEach { name ->
            assertEquals(name, CatalogLabels.title(catalog(name)))
        }
    }
}
