package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

/**
 * Mini servidor web embebido (puerto 8080) que sirve el **panel de
 * vinculación** de Omni TV.
 *
 * Al escanear el QR de Ajustes, el móvil abre `http://{ip-del-tv}:8080/?code={código}`
 * y el panel se desbloquea solo (303 → `/` con cookie de sesión). Quien abra la
 * URL a mano verá el formulario para introducir el código. Desde el panel se puede:
 *
 * - **Enlazar su cuenta** (email),
 * - **Añadir addons** (URLs de `manifest.json` de Stremio: aparecen como
 *   filas nuevas del catálogo),
 * - **Añadir APIs** (nombre + clave, guardadas para integraciones).
 *
 * Seguridad: cualquiera en la misma red puede llegar a este puerto, así que
 * **sin el código de emparejamiento o una sesión vigente no se entrega ni se acepta nada** — la
 * página sin código solo muestra el campo para introducirlo (ver
 * [PairingRouter] y [PairingPortal]). El código viaja en el QR a propósito: es
 * la única credencial práctica de emparejamiento «de sofá»; el 303 al final
 * borra el código de la URL del navegador, el guardado está ligado a la IP de
 * origen, el guardado rechaza orígenes cruzados y el tamaño de las peticiones
 * está acotado. Rotar el código en Ajustes invalida todas las sesiones.
 */
object PairingServer {

    private const val PORT = 8080
    private const val MAX_CONCURRENT_REQUESTS = 8

    // Evita crear un hilo ilimitado por conexión desde cualquier equipo de la LAN.
    private val requestSlots = Semaphore(MAX_CONCURRENT_REQUESTS)
    private var serverSocket: ServerSocket? = null

    /** El servidor está escuchando. */
    val running = mutableStateOf(false)

    /** Error de apertura visible en Ajustes (no un «Iniciando…» eterno). */
    val lastError = mutableStateOf("")

    /** Última acción recibida desde el panel (se muestra en Ajustes). */
    val lastAction = mutableStateOf("")

    /** Reglas del panel (código, límite de intentos, guardado) sobre los ajustes reales. */
    private val router = PairingRouter(SettingsPanelStore, onEvent = { lastAction.value = it })

    @Synchronized
    fun start() {
        if (serverSocket != null) return
        lastError.value = ""
        val host = localIpv4()
        if (host.isEmpty()) {
            lastError.value = "Conecta el televisor a tu red local para usar la vinculación."
            return
        }
        val ss = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(host, PORT))
            }
        } catch (_: java.io.IOException) {
            running.value = false
            lastError.value = "No se pudo abrir el puerto 8080. Vuelve a abrir la app."
            return
        }
        serverSocket = ss
        running.value = true
        thread(name = "omni-tv-pairing", isDaemon = true) {
            try {
                while (!ss.isClosed) {
                    val socket = ss.accept()
                    if (!requestSlots.tryAcquire()) {
                        // El panel es un servicio local pequeño: rechaza exceso de
                        // conexiones en vez de acumular sockets/hilos sin límite.
                        runCatching { socket.close() }
                        continue
                    }
                    thread(name = "omni-tv-pairing-req", isDaemon = true) {
                        try {
                            runCatching { handle(socket) }
                        } finally {
                            runCatching { socket.close() }
                            requestSlots.release()
                        }
                    }
                }
            } catch (_: java.io.IOException) {
                // accept también falla al cerrar el socket desde onStop: es normal.
            } finally {
                runCatching { ss.close() }
                synchronized(this@PairingServer) {
                    if (serverSocket === ss) {
                        serverSocket = null
                        running.value = false
                    }
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        running.value = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    /** IP local del televisor en la LAN. */
    fun localIpv4(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .sortedByDescending { it.isSiteLocalAddress }
            .firstOrNull()?.hostAddress ?: ""
    }.getOrDefault("")

    /** URL del panel (se muestra debajo del QR, como el ejemplo del usuario). */
    fun panelUrl(): String = localIpv4().takeIf { it.isNotEmpty() }?.let { "http://$it:$PORT/" }.orEmpty()

    /**
     * URL que codifica el QR: `http://{ip}:8080/?code={código}`.
     *
     * Escaneo = conexión directa: [PairingRouter] responde 303 a `/` con la
     * cookie de sesión y el navegador se queda en una URL limpia (el código
     * no queda en el historial). El flujo manual sigue disponible: la página
     * que pide el código a mano se sirve a quien abra la URL sin `?code=`.
     *
     * El QR es, de hecho, la única forma de emparejar "a distancia de sofá":
     * quien puede fotografiarlo ya está delante del televisor. Rotar el código
     * en Ajustes invalida todas las sesiones abiertas al instante.
     */
    fun pairingUrl(): String {
        val base = panelUrl()
        if (base.isEmpty()) return ""
        val code = SettingsStore.pairingCode
        return if (code.isBlank() || code == "------") base else "$base?code=$code"
    }

    /** Los ajustes reales del televisor, vistos desde el panel. */
    private object SettingsPanelStore : PanelStore {
        override val pairingCode: String get() = SettingsStore.pairingCode

        override fun data() = PanelData(
            code = SettingsStore.pairingCode,
            account = SettingsStore.linkedAccount,
            addons = SettingsStore.customAddons,
            streamAddons = SettingsStore.streamAddons,
            nuvioPlugins = SettingsStore.nuvioPlugins,
            nuvioBridge = SettingsStore.nuvioBridge,
            cloudstreamBridge = SettingsStore.cloudstreamBridge,
            apiKeys = SettingsStore.apiKeys,
            secretsEncrypted = SettingsStore.secretsEncrypted,
            ffmpegBridge = SettingsStore.ffmpegBridge
        )

        override fun setAccount(email: String) {
            SettingsStore.saveLinkedAccount(email)
        }

        override fun setAddons(urls: List<String>) = SettingsStore.saveCustomAddons(urls)

        override fun setStreamAddons(urls: List<String>) = SettingsStore.saveStreamAddons(urls)

        override fun setNuvioPlugins(urls: List<String>) = SettingsStore.saveNuvioPlugins(urls)
        override fun setNuvioBridge(url: String) = SettingsStore.saveNuvioBridge(url)
        override fun setCloudstreamBridge(url: String) = SettingsStore.saveCloudstreamBridge(url)
        override fun setFfmpegBridge(url: String) = SettingsStore.saveFfmpegBridge(url)
        override fun setApiKeys(keys: Map<String, String>) = SettingsStore.saveApiKeys(keys)
    }

    /* ------------------------------------------------------------------ */
    /*  Servidor HTTP mínimo                                              */
    /* ------------------------------------------------------------------ */

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 10_000
            val client = s.inetAddress?.hostAddress ?: "desconocido"
            val response = try {
                val request = readPanelRequest(BufferedInputStream(s.getInputStream()))
                if (request == null) {
                    PanelResponse(400, PairingPortal.html(null, "Petición no válida", isError = true))
                } else {
                    router.route(request, client)
                }
            } catch (e: RequestTooLargeException) {
                PanelResponse(413, PairingPortal.html(null, "La petición es demasiado grande", isError = true))
            }
            respond(s, response)
        }
    }

    private fun respond(socket: Socket, response: PanelResponse) {
        val bytes = response.body.toByteArray(Charsets.UTF_8)
        val header = panelResponseHeaders(response, bytes.size)
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }
}

/**
 * Cabeceras HTTP del panel, como función pura para poder probarlas en la JVM.
 * [extraHeaders] se escribe tal cual (p. ej. `Set-Cookie`); el llamador es
 * responsable de que no contengan saltos de línea ni datos del televisor.
 */
internal fun panelResponseHeaders(response: PanelResponse, bodyBytes: Int): String {
    val reason = when (response.status) {
        200 -> "OK"
        303 -> "See Other"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        413 -> "Payload Too Large"
        429 -> "Too Many Requests"
        else -> "Error"
    }
    return buildString {
        append("HTTP/1.1 ${response.status} $reason\r\n")
        append("Content-Type: ${response.contentType}\r\n")
        append("Content-Length: $bodyBytes\r\n")
        append("Connection: close\r\n")
        append("Cache-Control: no-store\r\n")
        append("Referrer-Policy: no-referrer\r\n")
        append("X-Content-Type-Options: nosniff\r\n")
        append("X-Frame-Options: DENY\r\n")
        append("Cross-Origin-Resource-Policy: same-origin\r\n")
        append("Cross-Origin-Opener-Policy: same-origin\r\n")
        append("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'\r\n")
        response.extraHeaders.forEach { append(it).append("\r\n") }
        append("\r\n")
    }
}
