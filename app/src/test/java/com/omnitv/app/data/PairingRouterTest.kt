package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingRouterTest {

    private companion object {
        const val CODE = "K7QW9Z"
        const val ACCOUNT = "sofia@ejemplo.com"
        const val ADDON = "https://addon.ejemplo/manifest.json"
        const val STREAM_ADDON = "http://192.168.1.77:8787"
        const val NUVIO_REPO = "https://plugins.ejemplo/manifest.json"
        const val NUVIO_BRIDGE = "http://192.168.1.77:8788"
        const val CS_BRIDGE = "http://192.168.1.77:8789"
        const val API_KEY = "s3cr3t-tmdb-key"
    }

    /** Ajustes en memoria que registran si alguien los tocó. */
    private class FakeStore : PanelStore {
        override var pairingCode = CODE
        var savedAccount = ACCOUNT
        var savedAddons = listOf(ADDON)
        var savedStreams = listOf(STREAM_ADDON)
        var savedPlugins = listOf(NUVIO_REPO)
        var savedBridge = NUVIO_BRIDGE
        var savedCloudstream = CS_BRIDGE
        var savedApiKeys = mapOf("TMDB" to API_KEY)
        var reads = 0
        var writes = 0

        override fun data(): PanelData {
            reads++
            return PanelData(pairingCode, savedAccount, savedAddons, savedStreams, savedPlugins, savedBridge, savedCloudstream, savedApiKeys, secretsEncrypted = true)
        }

        override fun setAccount(email: String) { writes++; savedAccount = email }
        override fun setAddons(urls: List<String>) { writes++; savedAddons = urls }
        override fun setStreamAddons(urls: List<String>) { writes++; savedStreams = urls }
        override fun setNuvioPlugins(urls: List<String>) { writes++; savedPlugins = urls }
        override fun setNuvioBridge(url: String) { writes++; savedBridge = url }
        override fun setCloudstreamBridge(url: String) { writes++; savedCloudstream = url }
        override fun setApiKeys(keys: Map<String, String>) { writes++; savedApiKeys = keys }
    }

    private var clock = 0L
    private val store = FakeStore()
    private val events = mutableListOf<String>()
    private val router = PairingRouter(
        store,
        AttemptGuard(maxFailures = 5, lockMillis = 60_000L, now = { clock }),
        onEvent = { events += it }
    )

    private fun get(target: String, client: String = "192.168.1.50") = router.route(
        PanelRequest("GET", target.substringBefore('?'), parseForm(target.substringAfter('?', "")), emptyMap(), ""),
        client
    )

    private fun post(path: String, body: String, client: String = "192.168.1.50") =
        router.route(PanelRequest("POST", path, emptyMap(), emptyMap(), body), client)

    /** La respuesta no revela nada del televisor: ni el código ni los datos guardados. */
    private fun assertNoSecrets(response: PanelResponse) {
        for (secret in listOf(CODE, ACCOUNT, ADDON, STREAM_ADDON, NUVIO_REPO, NUVIO_BRIDGE, CS_BRIDGE, API_KEY, "192.168.1.77")) {
            assertFalse("la respuesta filtra «$secret»", response.body.contains(secret, ignoreCase = true))
        }
        assertEquals("se leyeron los ajustes sin código válido", 0, store.reads)
    }

    // ------------------------- Sin código -------------------------

    @Test
    fun `la pagina sin codigo solo pide el codigo`() {
        val response = get("/")
        assertEquals(200, response.status)
        assertTrue(response.body.contains("name=\"code\""))
        assertNoSecrets(response)
    }

    @Test
    fun `index html sin codigo tampoco entrega datos`() = assertNoSecrets(get("/index.html"))

    @Test
    fun `status exige el codigo`() {
        val response = get("/status")
        assertEquals(403, response.status)
        assertNoSecrets(response)
    }

    @Test
    fun `una ruta desconocida responde 404 sin datos`() {
        val response = get("/admin")
        assertEquals(404, response.status)
        assertNoSecrets(response)
    }

    @Test
    fun `un metodo no previsto sobre el panel no entrega datos`() {
        val response = post("/", "code=$CODE")
        assertEquals(404, response.status)
        assertNoSecrets(response)
    }

    // ------------------------- Código incorrecto -------------------------

    @Test
    fun `un codigo incorrecto recibe 403 sin datos`() {
        val response = get("/?code=AAAAAA")
        assertEquals(403, response.status)
        assertNoSecrets(response)
        assertTrue(events.last().contains("incorrecto"))
    }

    @Test
    fun `guardar con codigo incorrecto no cambia nada`() {
        val response = post("/save", "code=AAAAAA&account=intruso%40mal.com&apis=TMDB%3Drobada&addons=https%3A%2F%2Fmal.com")
        assertEquals(403, response.status)
        assertNoSecrets(response)
        assertEquals(0, store.writes)
        assertEquals(ACCOUNT, store.savedAccount)
        assertEquals(mapOf("TMDB" to API_KEY), store.savedApiKeys)
    }

    @Test
    fun `guardar sin codigo no cambia nada`() {
        val response = post("/save", "account=intruso%40mal.com")
        assertEquals(403, response.status)
        assertEquals(0, store.writes)
    }

    @Test
    fun `un codigo sin inicializar no abre el panel`() {
        store.pairingCode = "------"
        assertEquals(403, get("/?code=------").status)
        store.pairingCode = ""
        assertEquals(403, get("/?code=x").status)
        assertEquals(0, store.reads)
    }

    // ------------------------- Código correcto -------------------------

    @Test
    fun `el codigo correcto abre el panel con los datos`() {
        val response = get("/?code=$CODE")
        assertEquals(200, response.status)
        for (expected in listOf(ACCOUNT, ADDON, STREAM_ADDON, NUVIO_REPO, NUVIO_BRIDGE, CS_BRIDGE, "TMDB=$API_KEY")) {
            assertTrue("falta «$expected» en el panel", response.body.contains(expected))
        }
        assertTrue(response.body.contains("action=\"/save\""))
    }

    @Test
    fun `el codigo vale en minusculas y con espacios alrededor`() {
        assertEquals(200, get("/?code=${CODE.lowercase()}").status)
        assertEquals(200, get("/?code=+$CODE+").status)
    }

    @Test
    fun `status con codigo devuelve el resumen`() {
        val response = get("/status?code=$CODE")
        assertEquals(200, response.status)
        assertEquals(
            """{"account":"$ACCOUNT","addons":1,"streams":1,"plugins":1,"cloudstream":1,"apis":1}""",
            response.body
        )
    }

    @Test
    fun `guardar con codigo actualiza los ajustes`() {
        val body = "code=$CODE" +
            "&account=marco%40ejemplo.com" +
            "&addons=https%3A%2F%2Fa.ejemplo%2Fmanifest.json%0D%0Ahttps%3A%2F%2Fb.ejemplo%2Fmanifest.json" +
            "&streamAddons=http%3A%2F%2F10.0.0.5%3A8787" +
            "&nuvioPlugins=https%3A%2F%2Fplugins.ejemplo%2Fmanifest.json" +
            "&nuvioBridge=http%3A%2F%2F10.0.0.5%3A8788" +
            "&cloudstreamBridge=http%3A%2F%2F10.0.0.5%3A8789" +
            "&apis=tmdb%3Dabc123%0D%0ATRAKT+%3D+xyz%3D%3D%0D%0Alinea+sin+igual"
        val response = post("/save", body)

        assertEquals(200, response.status)
        assertEquals("marco@ejemplo.com", store.savedAccount)
        assertEquals(listOf("https://a.ejemplo/manifest.json", "https://b.ejemplo/manifest.json"), store.savedAddons)
        assertEquals(listOf("http://10.0.0.5:8787"), store.savedStreams)
        assertEquals(listOf("https://plugins.ejemplo/manifest.json"), store.savedPlugins)
        assertEquals("http://10.0.0.5:8788", store.savedBridge)
        assertEquals("http://10.0.0.5:8789", store.savedCloudstream)
        assertEquals(mapOf("TMDB" to "abc123", "TRAKT" to "xyz=="), store.savedApiKeys)
        assertTrue(response.body.contains("Guardado"))
        assertFalse(response.body.contains("descartaron"))
    }

    @Test
    fun `las URL http publicas se descartan y se avisa`() {
        val body = "code=$CODE" +
            "&streamAddons=http%3A%2F%2Fpublico.ejemplo%0Ahttp%3A%2F%2F192.168.1.30%3A8787%0Ahttps%3A%2F%2Fseguro.ejemplo%0Abasura"
        val response = post("/save", body)

        assertEquals(200, response.status)
        assertEquals(listOf("http://192.168.1.30:8787", "https://seguro.ejemplo"), store.savedStreams)
        assertTrue(response.body.contains("Se descartaron 2 URL"))
    }

    @Test
    fun `stremio se normaliza y el puente puede borrarse sin borrar repos`() {
        val body = "code=$CODE&addons=stremio%3A%2F%2Faddon.ejemplo%2Fconfig%2Fmanifest.json&nuvioBridge="
        assertEquals(200, post("/save", body).status)
        assertEquals(listOf("https://addon.ejemplo/config/manifest.json"), store.savedAddons)
        assertEquals("", store.savedBridge)
        assertEquals(listOf(NUVIO_REPO), store.savedPlugins)
    }

    @Test
    fun `puente publico sin cifrar o con query se rechaza`() {
        val body = "code=$CODE&nuvioBridge=http%3A%2F%2Fpublico.ejemplo%3A8788"
        assertTrue(post("/save", body).body.contains("Se descartaron 1 URL"))
        assertEquals(NUVIO_BRIDGE, store.savedBridge)
        val query = "code=$CODE&nuvioBridge=https%3A%2F%2Fbridge.ejemplo%3Ftoken%3Dx"
        assertTrue(post("/save", query).body.contains("Se descartaron 1 URL"))
        assertEquals(NUVIO_BRIDGE, store.savedBridge)
    }

    // ------------------------- Límite de intentos -------------------------

    @Test
    fun `cinco fallos bloquean al cliente aunque luego acierte`() {
        repeat(5) { assertEquals(403, get("/?code=MAL$it").status) }

        val locked = get("/?code=$CODE")
        assertEquals(429, locked.status)
        assertNoSecrets(locked)
        assertEquals(429, post("/save", "code=$CODE&account=x%40y.z").status)
        assertEquals(0, store.writes)
    }

    @Test
    fun `el bloqueo es por cliente y caduca`() {
        repeat(5) { get("/?code=MAL$it", client = "192.168.1.66") }
        assertEquals(429, get("/?code=$CODE", client = "192.168.1.66").status)

        // Otro dispositivo no se ve afectado
        assertEquals(200, get("/?code=$CODE", client = "192.168.1.50").status)

        // Pasado el minuto, el bloqueado puede volver a intentarlo
        clock += 60_001L
        assertEquals(200, get("/?code=$CODE", client = "192.168.1.66").status)
    }

    @Test
    fun `abrir la pagina sin codigo no cuenta como intento`() {
        repeat(20) { assertEquals(200, get("/").status) }
        assertEquals(200, get("/?code=$CODE").status)
    }

    // ------------------------- HTML -------------------------

    @Test
    fun `los valores guardados se escapan en el HTML`() {
        store.savedAccount = "\"><script>alert(1)</script>"
        store.savedAddons = listOf("https://a.ejemplo/</textarea><script>x()</script>")
        val body = get("/?code=$CODE").body

        assertFalse(body.contains("<script>"))
        assertTrue(body.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
    }

    @Test
    fun `el status escapa comillas y barras en la cuenta`() {
        val account = "a\"b\\c"     // a"b\c
        val escaped = "a\\\"b\\\\c" // a\"b\\c, tal como debe ir en JSON
        store.savedAccount = account

        val body = get("/status?code=$CODE").body
        assertTrue(body, body.contains(""""account":"$escaped""""))
        assertFalse(body, body.contains(""""account":"$account""""))
    }

    @Test
    fun `el aviso de cifrado refleja el estado real`() {
        val encrypted = PairingPortal.html(PanelData(CODE, "", emptyList(), emptyList(), emptyList(), "", "", emptyMap(), secretsEncrypted = true))
        val plain = PairingPortal.html(PanelData(CODE, "", emptyList(), emptyList(), emptyList(), "", "", emptyMap(), secretsEncrypted = false))

        assertTrue(encrypted.contains("cifradas"))
        assertFalse(encrypted.contains("sin cifrar"))
        assertTrue(plain.contains("sin cifrar"))
    }
}
