package com.omnitv.app.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** HTTP real en loopback, sin librerías JDK internas ocultas por el classpath Android. */
class AddonHttpTest {
    private lateinit var server: ServerSocket
    private lateinit var worker: Thread
    private var port = 0
    private val redirected = AtomicInteger()

    @Before fun start() {
        server = ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        port = server.localPort
        worker = thread(name = "addon-http-fixture") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    runCatching {
                        val input = it.getInputStream().bufferedReader()
                        val request = input.readLine().orEmpty()
                        while (true) { val line = input.readLine(); if (line.isNullOrEmpty()) break }
                        val path = request.split(' ').getOrNull(1).orEmpty()
                        val response = when (path) {
                            "/array" -> "200 OK" to """[{"id":"tt1"}]"""
                            "/redirect" -> "302 Found" to ""
                            "/public" -> "302 Found" to ""
                            "/post" -> "302 Found" to ""
                            "/target" -> { redirected.incrementAndGet(); "200 OK" to "{}" }
                            "/large" -> "200 OK" to ""
                            "/catalog/movie/top/genre=Action&skip=20.json" -> "200 OK" to """{"metas":[{"id":"tt1"},{"id":"tt2"}]}"""
                            else -> "404 Not Found" to "{}"
                        }
                        val body = response.second.toByteArray()
                        val headers = buildString {
                            append("HTTP/1.1 ${response.first}\r\nContent-Type: application/json\r\n")
                            when (path) {
                                "/redirect", "/post" -> append("Location: http://127.0.0.1:$port/${if (path == "/post") "target" else "array"}\r\n")
                                "/public" -> append("Location: http://public.example/array\r\n")
                            }
                            append("Content-Length: ${if (path == "/large") AddonHttp.MAX_BODY + 1 else body.size}\r\nConnection: close\r\n\r\n")
                        }
                        it.getOutputStream().apply { write(headers.toByteArray()); write(body); flush() }
                    }
                }
            }
        }
    }

    @After fun stop() { server.close(); worker.join(2000) }
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
