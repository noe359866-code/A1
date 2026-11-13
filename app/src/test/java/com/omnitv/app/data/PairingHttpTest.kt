package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class PairingHttpTest {

    private fun read(raw: String, maxHeader: Int = 16 * 1024, maxBody: Int = 64 * 1024) =
        readPanelRequest(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)), maxHeader, maxBody)

    // ------------------------- Lectura de la petición -------------------------

    @Test
    fun `lee un GET con query`() {
        val request = read("GET /?code=K7QW9Z&x=a%20b HTTP/1.1\r\nHost: 192.168.1.10:8080\r\nAccept: */*\r\n\r\n")!!

        assertEquals("GET", request.method)
        assertEquals("/", request.path)
        assertEquals(mapOf("code" to "K7QW9Z", "x" to "a b"), request.query)
        assertEquals("192.168.1.10:8080", request.headers["host"])
        assertEquals("", request.body)
    }

    @Test
    fun `lee el cuerpo de un POST que llega junto a la cabecera`() {
        // Regresión: cabecera y cuerpo en el mismo paquete. Con un lector con búfer
        // para la cabecera, el cuerpo se perdía y el guardado se quedaba colgado.
        val body = "code=K7QW9Z&account=sofia%40ejemplo.com&apis=TMDB%3Dabc"
        val request = read(
            "POST /save HTTP/1.1\r\n" +
                "Host: tv\r\n" +
                "Content-Type: application/x-www-form-urlencoded\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "\r\n" +
                body
        )!!

        assertEquals("POST", request.method)
        assertEquals("/save", request.path)
        assertEquals(body, request.body)
        assertEquals("sofia@ejemplo.com", parseForm(request.body)["account"])
    }

    @Test
    fun `el cuerpo conserva los caracteres UTF-8`() {
        val body = "account=Sof%C3%ADa&nota=ñandú"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val request = read("POST /save HTTP/1.1\r\nContent-Length: ${bytes.size}\r\n\r\n$body")!!

        assertEquals(body, request.body)
        assertEquals("Sofía", parseForm(request.body)["account"])
    }

    @Test
    fun `acepta saltos de linea sin retorno de carro`() {
        val request = read("GET /status HTTP/1.1\nHost: tv\n\n")!!
        assertEquals("/status", request.path)
        assertEquals("tv", request.headers["host"])
    }

    @Test
    fun `no lee mas alla de Content-Length`() {
        val request = read("POST /save HTTP/1.1\r\nContent-Length: 4\r\n\r\ncode=SOBRA")!!
        assertEquals("code", request.body)
    }

    @Test
    fun `devuelve null si no es HTTP o la conexion se corta`() {
        assertNull(read(""))
        assertNull(read("GET / HTTP/1.1\r\nHost: tv")) // la conexión se corta antes del final
        assertNull(read("basura\r\n\r\n"))
        assertNull(read("POST /save HTTP/1.1\r\nContent-Length: muchos\r\n\r\n"))
        assertNull(read("POST /save HTTP/1.1\r\nContent-Length: -5\r\n\r\n"))
    }

    @Test
    fun `rechaza un cuerpo mayor que el limite sin reservar memoria`() {
        try {
            read("POST /save HTTP/1.1\r\nContent-Length: 2000000000\r\n\r\n")
            fail("debería rechazar el cuerpo")
        } catch (expected: RequestTooLargeException) {
            // correcto
        }
    }

    @Test
    fun `rechaza una cabecera interminable`() {
        try {
            read("GET /" + "a".repeat(5000) + " HTTP/1.1\r\n\r\n", maxHeader = 1024)
            fail("debería rechazar la cabecera")
        } catch (expected: RequestTooLargeException) {
            // correcto
        }
    }

    // ------------------------- Formularios -------------------------

    @Test
    fun `parseForm decodifica mas y porcentajes`() {
        assertEquals(
            mapOf("a" to "uno dos", "b" to "x=y&z", "c" to ""),
            parseForm("a=uno+dos&b=x%3Dy%26z&c=")
        )
    }

    @Test
    fun `parseForm ignora campos mal formados sin romperse`() {
        assertEquals(mapOf("ok" to "1"), parseForm("roto=%ZZ&ok=1&sinvalor&=sinclave"))
        assertTrue(parseForm("").isEmpty())
    }

    // ------------------------- Límite de intentos -------------------------

    @Test
    fun `AttemptGuard bloquea al llegar al maximo y libera al caducar`() {
        var clock = 1_000L
        val guard = AttemptGuard(maxFailures = 3, lockMillis = 10_000L, now = { clock })

        repeat(2) { guard.recordFailure("a") }
        assertFalse(guard.isLocked("a"))
        guard.recordFailure("a")
        assertTrue(guard.isLocked("a"))
        assertFalse(guard.isLocked("b"))

        clock += 9_999L
        assertTrue(guard.isLocked("a"))
        clock += 1L
        assertFalse(guard.isLocked("a"))
    }

    @Test
    fun `AttemptGuard olvida los fallos tras un acierto o una pausa larga`() {
        var clock = 0L
        val guard = AttemptGuard(maxFailures = 3, lockMillis = 10_000L, now = { clock })

        repeat(2) { guard.recordFailure("a") }
        guard.recordSuccess("a")
        repeat(2) { guard.recordFailure("a") }
        assertFalse(guard.isLocked("a"))

        clock += 10_001L // los dos fallos anteriores ya no cuentan
        repeat(2) { guard.recordFailure("a") }
        assertFalse(guard.isLocked("a"))
        guard.recordFailure("a")
        assertTrue(guard.isLocked("a"))
    }

    @Test
    fun `AttemptGuard mantiene acotada su memoria`() {
        var clock = 0L
        val guard = AttemptGuard(maxFailures = 3, lockMillis = 10_000L, now = { clock })

        repeat(5_000) { guard.recordFailure("cliente-$it") }
        clock += 20_000L
        // Tras la limpieza sigue funcionando para clientes nuevos
        repeat(3) { guard.recordFailure("nuevo") }
        assertTrue(guard.isLocked("nuevo"))
    }
}
