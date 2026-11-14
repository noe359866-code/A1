package com.omnitv.app.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioIntegrationTest {
    private fun manifest(resources: String = """["catalog","meta","stream"]""", catalogs: String = """[{"id":"top","type":"movie","extraSupported":["search","skip"]}]""") = JSONObject(
        """{"id":"test.addon","name":"Addon","version":"1.0.0","resources":$resources,"types":["movie","series"],"catalogs":$catalogs}"""
    )

    @Test
    fun `las restricciones se aplican por recurso tipo e id`() {
        val m = StremioManifestParser.parse(manifest("""[{"name":"stream","types":["movie"],"idPrefixes":["tt"]},{"name":"meta","types":["series"]}]"""))
        assertTrue(m.supports("stream", "movie", "tt123"))
        assertFalse(m.supports("stream", "series", "tt123:1:2"))
        assertFalse(m.supports("stream", "movie", "tmdb:123"))
        assertTrue(m.supports("meta", "series", "anilist:1"))
        assertFalse(m.supports("catalog", "movie", ""))
        assertFalse(m.isCatalogAddon())
    }

    @Test
    fun `un indice Nuvio no se instala como Stremio`() {
        val json = JSONObject("""{"name":"Nuvio","scrapers":[{"id":"a"}]}""")
        assertNotNull(StremioManifestParser.validate(json))
        assertNotNull(NuvioPluginManager.parseManifest(json))
    }

    @Test
    fun `extras obligatorios usan opciones y no se inventa search`() {
        val m = StremioManifestParser.parse(manifest(catalogs = """[{"id":"top","type":"movie","extra":[{"name":"genre","isRequired":true,"options":["Action"]}]}]"""))
        val c = m.catalogs.single()
        assertEquals(mapOf("genre" to "Action"), m.catalogExtras(c))
        assertNull(m.catalogExtras(c, mapOf("search" to "matrix")))
        assertFalse(c.supportsExtra("skip"))
        val requiredSearch = StremioManifestParser.parse(manifest(catalogs = """[{"id":"search","type":"series","extraRequired":["search"]}]""")).catalogs.single()
        assertNull(requiredSearch.normalizedExtras())
        assertEquals(mapOf("search" to "matrix"), requiredSearch.normalizedExtras(mapOf("search" to "matrix")))
    }

    @Test
    fun `la configuracion del manifest se conserva y los extras van en el path`() {
        val url = StremioUrl.normalize("stremio://addon.example/opciones/manifest.json")
        assertEquals("https://addon.example/opciones/manifest.json", url)
        assertEquals("https://addon.example/opciones", StremioUrl.base(url!!))
        assertEquals("https://addon.example/opciones", StremioUrl.normalize("https://addon.example/opciones"))
        assertEquals(
            "https://addon.example/catalog/movie/top/genre=Science%20Fiction&skip=20.json",
            StremioUrl.catalog("https://addon.example", "movie", "top", mapOf("skip" to "20", "genre" to "Science Fiction"))
        )
        assertEquals("https://addon.example/stream/series/tt1%3A1%3A2.json", StremioUrl.resource("https://addon.example", "stream", "series", "tt1:1:2"))
        assertNull(StremioUrl.normalize("http://public.example/manifest.json"))
        assertNull(StremioUrl.normalize("https://addon.example/manifest.json?token=secreto"))
    }

    @Test
    fun `descubrimiento combina manifests y proyectos sin duplicar ni inventar Nuvio`() = runBlocking {
        val m = StremioManifestParser.parse(manifest())
        val addons = StreamHub.configuredAddons(
            manifests = listOf(AddonStatus("https://addon.example/manifest.json", m)),
            projects = listOf("https://addon.example", "http://192.168.1.5:8787"),
            plugins = emptyList()
        )
        assertEquals(2, addons.size)
        assertTrue(addons.first() is ProjectStreamAddon)
        val withNuvio = StreamHub.configuredAddons(emptyList(), emptyList(), listOf("https://repo.example/manifest.json"))
        assertEquals("nuvio-plugins", withNuvio.single().id)
        assertTrue(StreamHub.configuredAddons(emptyList(), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `paginacion solo se ofrece cuando se pidio skip y hay resultados`() = runBlocking {
        // La decisión de continuidad se prueba sin red sobre el contrato parseado.
        val c = StremioManifestParser.parse(manifest()).catalogs.single()
        assertTrue(c.supportsExtra("skip"))
        assertEquals("https://a/catalog/movie/top/skip=20.json", StremioUrl.catalog("https://a", "movie", "top", mapOf("skip" to "20")))
        val page = CatalogPage(listOf(JSONObject("""{"id":"tt1"}""")), true)
        assertTrue(page.hasMore)
        assertFalse(CatalogPage(emptyList(), false).hasMore)
    }
}
