package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El QR lleva el código (`/?code=…`) y el router responde 303 → `/` con una
 * sesión con cookie `HttpOnly` + `SameSite=Strict`, ligada a la IP y al código
 * vigente: el código no sobrevive en la URL del móvil.
 */
class PairingSessionTest {

    private companion object {
        const val CODE = "K7QW9Z"
        const val CLIENT = "192.168.1.50"
        const val OTHER = "192.168.1.66"
        const val ACCOUNT = "sofia@ejemplo.com"
    }

    private class FakeStore : PanelStore {
        override var pairingCode = CODE
        var savedAccount = ACCOUNT
        var writes = 0
        override fun data() = PanelData(
            pairingCode, savedAccount, emptyList(), emptyList(), emptyList(),
            "", "", emptyMap(), secretsEncrypted = true
        )
        override fun setAccount(email: String) { writes++; savedAccount = email }
        override fun setAddons(urls: List<String>) = Unit
        override fun setStreamAddons(urls: List<String>) = Unit
        override fun setNuvioPlugins(urls: List<String>) = Unit
        override fun setNuvioBridge(url: String) = Unit
        override fun setCloudstreamBridge(url: String) = Unit
        override fun setFfmpegBridge(url: String) = Unit
        override fun setApiKeys(keys: Map<String, String>) = Unit
    }

    private var clock = 1_000L
    private val store = FakeStore()
    private val sessions = SessionGuard(ttlMillis = 60_000L, now = { clock })
    private val router = PairingRouter(
        store,
        guard = AttemptGuard(now = { clock }),
        sessions = sessions,
        onEvent = {}
    )

    private fun get(target: String, client: String = CLIENT, headers: Map<String, String> = emptyMap()) =
        router.route(
            PanelRequest("GET", target.substringBefore('?'), parseForm(target.substringAfter('?', "")), headers, ""),
            client
        )

    private fun post(path: String, body: String, client: String = CLIENT, headers: Map<String, String> = emptyMap()) =
        router.route(PanelRequest("POST", path, emptyMap(), headers, body), client)

    private fun cookie(token: String) = mapOf("Cookie" to "omnitv_session=$token; otra=1")

    private fun issued(response: PanelResponse): String? =
        response.extraHeaders.firstOrNull { it.startsWith("Set-Cookie:", true) }

    private fun token(response: PanelResponse): String =
        issued(response)!!.substringAfter("omnitv_session=").substringBefore(';')

    @Test
    fun `el formulario bloqueado usa POST y no expone el codigo`() {
        val html = PairingPortal.html(null)
        assertTrue(html.contains("method=\"POST\""))
        assertTrue(html.contains("action=\"/unlock\""))
        assertTrue(html.contains("name=\"code\""))
        assertFalse(html.contains(CODE))
        assertFalse(html.contains("?code="))
    }

    @Test
    fun `unlock canjea el codigo por una cookie de sesion y un 303 limpio`() {
        val response = post("/unlock", "code=$CODE")
        assertEquals(303, response.status)
        // El navegador aterriza en `/`: el código no queda en la URL
        assertTrue(response.extraHeaders.contains("Location: /"))
        val header = issued(response)
        assertNotNull(header)
        assertTrue(header!!.contains("HttpOnly"))
        assertTrue(header.contains("SameSite=Strict"))
        assertTrue(header.contains("Path=/"))
        assertFalse(header.contains(CODE))
        assertFalse(response.body.contains(CODE))
        // La cookie abre la sesión completa en `/`
        val opened = get("/", headers = cookie(token(response)))
        assertEquals(200, opened.status)
        assertTrue(opened.body.contains("action=\"/save\""))
    }

    @Test
    fun `la cookie abre el panel sin reenviar el codigo`() {
        val first = post("/unlock", "code=$CODE")
        val session = token(first)
        val again = get("/", headers = cookie(session))
        assertEquals(200, again.status)
        assertTrue(again.body.contains(ACCOUNT))
        assertFalse(again.body.contains(CODE))
        // Cada respuesta autenticada renueva la sesión (TTL deslizante).
        assertTrue(issued(again)!!.contains("omnitv_session="))
    }

    @Test
    fun `la cookie sirve para status y para guardar`() {
        val session = token(post("/unlock", "code=$CODE"))
        val status = get("/status", headers = cookie(session))
        assertEquals(200, status.status)
        assertTrue(status.body.contains(ACCOUNT))

        val saved = post("/save", "account=marco%40ejemplo.com", headers = cookie(session))
        assertEquals(200, saved.status)
        assertEquals("marco@ejemplo.com", store.savedAccount)
        assertEquals(1, store.writes)
    }

    @Test
    fun `sin codigo ni cookie no se entrega ni se escribe nada`() {
        val locked = get("/")
        assertEquals(200, locked.status)
        assertFalse(locked.body.contains(ACCOUNT))
        val denied = post("/save", "account=intruso%40mal.com")
        assertEquals(403, denied.status)
        assertEquals(0, store.writes)
        assertEquals(ACCOUNT, store.savedAccount)
    }

    @Test
    fun `un codigo incorrecto no crea sesion`() {
        val denied = post("/unlock", "code=AAAAAA")
        assertEquals(403, denied.status)
        assertEquals(null, issued(denied))
        assertFalse(denied.body.contains(ACCOUNT))
    }

    @Test
    fun `la cookie esta ligada a la IP del cliente`() {
        val session = token(post("/unlock", "code=$CODE"))
        assertEquals(403, post("/save", "account=x%40y.z", client = OTHER, headers = cookie(session)).status)
        assertEquals(0, store.writes)
        assertEquals(200, post("/save", "account=x%40y.z", headers = cookie(session)).status)
    }

    @Test
    fun `la sesion caduca`() {
        val session = token(post("/unlock", "code=$CODE"))
        clock += 60_001L
        assertEquals(403, get("/status", headers = cookie(session)).status)
        assertEquals(403, post("/save", "account=x%40y.z", headers = cookie(session)).status)
        assertEquals(0, store.writes)
    }

    @Test
    fun `regenerar el codigo invalida todas las sesiones`() {
        val session = token(post("/unlock", "code=$CODE"))
        assertEquals(200, get("/status", headers = cookie(session)).status)
        store.pairingCode = "NUEVO9"
        assertEquals(403, get("/status", headers = cookie(session)).status)
        assertEquals(403, post("/save", "account=x%40y.z", headers = cookie(session)).status)
        assertEquals(0, store.writes)
        // Con el código nuevo se puede volver a desbloquear.
        assertEquals(303, post("/unlock", "code=NUEVO9").status)
    }

    @Test
    fun `unlock rechaza peticiones de otro origen`() {
        val response = post(
            "/unlock",
            "code=$CODE",
            headers = mapOf("Host" to "192.168.1.10:8080", "Origin" to "http://mal.example")
        )
        assertEquals(403, response.status)
        assertEquals(null, issued(response))
    }

    @Test
    fun `el panel desbloqueado no reexpone el codigo en el HTML`() {
        // Antes el formulario llevaba un <input hidden name="code">; con la
        // sesión ya no hace falta y el secreto no queda en el DOM del móvil.
        val html = PairingPortal.html(store.data())
        assertFalse(html.contains(CODE))
        assertFalse(html.contains("name=\"code\""))
        assertTrue(html.contains("action=\"/save\""))
    }

    @Test
    fun `el guard acota el numero de sesiones vivas`() {
        val many = SessionGuard(ttlMillis = 60_000L, now = { clock })
        val first = many.issue(CLIENT, CODE)
        repeat(511) { many.issue(CLIENT, CODE) }
        assertTrue(many.valid(first, CLIENT, CODE))
        many.issue(CLIENT, CODE) // supera el máximo: se cierran todas
        assertFalse(many.valid(first, CLIENT, CODE))
    }

    @Test
    fun `el servidor escribe Set-Cookie y no filtra el codigo`() {
        val response = post("/unlock", "code=$CODE")
        val headers = panelResponseHeaders(response, response.body.toByteArray(Charsets.UTF_8).size)
        assertTrue(headers.startsWith("HTTP/1.1 303 See Other\r\n"))
        assertTrue(headers.contains("Location: /"))
        assertTrue(headers.contains(issued(response)!!))
        assertTrue(headers.contains("HttpOnly"))
        assertTrue(headers.contains("SameSite=Strict"))
        assertTrue(headers.endsWith("\r\n\r\n"))
        assertFalse(headers.contains(CODE))
    }
}
