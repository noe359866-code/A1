package com.omnitv.app.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Cabeceras y estado HTTP del cliente común, con un servidor real en loopback. */
class AddonHttpHeadersTest {
    private lateinit var server: ServerSocket
    private lateinit var worker: Thread
    private var port = 0
    private val lastAuth = AtomicReference<String>("")
    private val lastPath = AtomicReference<String>("")

    @Before fun start() {
        server = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        port = server.localPort
        worker = thread(name = "addon-headers-fixture") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    runCatching {
                        val input = it.getInputStream().bufferedReader()
                        val request = input.readLine().orEmpty()
                        lastPath.set(request.split(' ').getOrNull(1).orEmpty())
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Authorization:", true)) lastAuth.set(line.substringAfter(':').trim())
                        }
                        val body = """{"segments":{"intro":{"start_ms":1000,"end_ms":40000}}}"""
                        val status = if (lastPath.get().startsWith("/limited")) "429 Too Many Requests" else "200 OK"
                        val payload = if (status.startsWith("429")) """{"error":"rate limit"}""" else body
                        val bytes = payload.toByteArray()
                        val headers = "HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                        it.getOutputStream().apply { write(headers.toByteArray()); write(bytes); flush() }
                    }
                }
            }
        }
    }

    @After fun stop() { server.close(); worker.join(2000) }

    @Test fun `las cabeceras opcionales llegan al servidor`() {
        val json = AddonHttp.getJson("http://127.0.0.1:$port/media", mapOf("Authorization" to "Bearer clave")) as JSONObject
        assertEquals("Bearer clave", lastAuth.get())
        assertTrue(json.has("segments"))
    }

    @Test fun `sin clave la lectura funciona igual`() {
        lastAuth.set("")
        val json = AddonHttp.getJson("http://127.0.0.1:$port/media") as JSONObject
        assertEquals("", lastAuth.get())
        assertTrue(json.has("segments"))
    }

    @Test fun `el estado 429 se distingue de una respuesta vacia`() {
        val limited = AddonHttp.getJsonWithStatus("http://127.0.0.1:$port/limited")
        assertEquals(429, limited.status)
        assertNull(limited.value)
        assertEquals(200, AddonHttp.getJsonWithStatus("http://127.0.0.1:$port/media").status)
    }
}
