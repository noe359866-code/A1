package com.omnitv.app.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/** HTTP real en loopback: sin mocks del cliente ni acceso a Internet. */
class AddonHttpTest {
    private lateinit var server: HttpServer
    private var port = 0
    private val redirected = AtomicInteger()

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port
        server.createContext("/array") { exchange ->
            val body = """[{"id":"tt1"}]""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:$port/array")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/public") { exchange ->
            exchange.responseHeaders.add("Location", "http://public.example/array")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/post") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:$port/target")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/target") { exchange ->
            redirected.incrementAndGet(); exchange.sendResponseHeaders(200, 2); exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        server.createContext("/large") { exchange ->
            exchange.sendResponseHeaders(200, (AddonHttp.MAX_BODY + 1).toLong()); exchange.close()
        }
        server.createContext("/catalog") { exchange ->
            assertEquals("/catalog/movie/top/genre=Action&skip=20.json", exchange.requestURI.path)
            val body = """{"metas":[{"id":"tt1"},{"id":"tt2"}]}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @After fun stop() { server.stop(0) }
    private fun url(path: String) = "http://127.0.0.1:$port$path"

    @Test fun `acepta array raiz y redireccion local`() {
        assertTrue(AddonHttp.getJson(url("/array")) is JSONArray)
        assertTrue(AddonHttp.getJson(url("/redirect")) is JSONArray)
    }

    @Test fun `redireccion a HTTP publico se bloquea`() {
        assertNull(AddonHttp.getJson(url("/public")))
    }

    @Test fun `POST no reenvia el cuerpo a otra ruta`() {
        assertNull(AddonHttp.postJson(url("/post"), "{}"))
        assertEquals(0, redirected.get())
    }

    @Test fun `cuerpo declarado por encima del limite se rechaza`() {
        assertNull(AddonHttp.getJson(url("/large")))
    }

    @Test fun `catalogo pagina con extras en el path y skip real`() = runBlocking {
        val page = StremioClient.fetchCatalogPage("http://127.0.0.1:$port", "movie", "top", mapOf("skip" to "20", "genre" to "Action"))
        assertEquals(2, page.metas.size)
        assertTrue(page.hasMore)
        assertEquals("tt1", page.metas.first().getString("id"))
    }

    @Test fun `addon Stremio respeta tipo e id antes de pedir stream`() = runBlocking {
        val manifest = StremioManifestParser.parse(JSONObject("""{"id":"a","name":"A","resources":[{"name":"stream","types":["movie"],"idPrefixes":["tt"]}],"types":["movie"]}"""))
        val addon = ProjectStreamAddon("http://127.0.0.1:$port", "A", manifest)
        assertTrue(addon.resolve("series", "tt1:1:2").isEmpty())
        assertTrue(addon.resolve("movie", "tmdb:1").isEmpty())
    }
}
