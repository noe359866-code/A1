package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Mini servidor web embebido (puerto 8080) que sirve el **panel de
 * vinculación** de Omni TV.
 *
 * Al escanear el QR de Ajustes, el móvil abre `http://{ip-del-tv}:8080/`
 * (con el código de emparejamiento) y el usuario puede:
 *
 * - **Enlazar su cuenta** (email),
 * - **Añadir addons** (URLs de `manifest.json` de Stremio: aparecen como
 *   filas nuevas del catálogo),
 * - **Añadir APIs** (nombre + clave, guardadas para integraciones).
 *
 * Todo se valida con el código de emparejamiento y se guarda en
 * [SettingsStore]; la app lo aplica en caliente.
 */
object PairingServer {

    private const val PORT = 8080
    private var serverSocket: ServerSocket? = null

    /** El servidor está escuchando. */
    val running = mutableStateOf(false)

    /** Última acción recibida desde el panel (se muestra en Ajustes). */
    val lastAction = mutableStateOf("")

    fun start() {
        if (serverSocket != null) return
        thread(name = "omni-tv-pairing", isDaemon = true) {
            runCatching {
                val ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT))
                }
                serverSocket = ss
                running.value = true
                while (true) {
                    val socket = ss.accept()
                    thread(name = "omni-tv-pairing-req", isDaemon = true) {
                        runCatching { handle(socket) }
                    }
                }
            }.onFailure {
                running.value = false
            }
        }
    }

    /** IP local del televisor en la LAN. */
    fun localIpv4(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()?.hostAddress ?: "0.0.0.0"
    }.getOrDefault("0.0.0.0")

    /** URL del panel (se muestra debajo del QR, como el ejemplo del usuario). */
    fun panelUrl(): String = "http://${localIpv4()}:$PORT/"

    /** URL de emparejamiento con código (la que codifica el QR). */
    fun pairingUrl(): String = "${panelUrl()}?code=${SettingsStore.pairingCode}"

    /* ------------------------------------------------------------------ */
    /*  Servidor HTTP mínimo                                              */
    /* ------------------------------------------------------------------ */

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 10_000
            val input = s.getInputStream()
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))

            val requestLine = reader.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }

            val method = requestLine.substringBefore(' ').uppercase()
            val target = requestLine.substringAfter(' ').substringBefore(' ')
            val path = target.substringBefore('?')
            val query = parseForm(target.substringAfter('?', ""))

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyBytes = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(bodyBytes, read, contentLength - read)
                if (n < 0) break
                read += n
            }
            val body = String(bodyBytes, 0, read, Charsets.UTF_8)

            when {
                method == "GET" && path == "/" || path == "/index.html" -> {
                    val code = query["code"].orEmpty()
                    val ok = code.equals(SettingsStore.pairingCode, ignoreCase = true)
                    if (ok) lastAction.value = "Panel web abierto desde ${s.inetAddress.hostAddress ?: "el móvil"}"
                    respond(s, "200 OK", portalHtml(codeOk = ok))
                }
                method == "GET" && path == "/status" -> {
                    respond(
                        s, "200 OK",
                        """{"account":${json(SettingsStore.linkedAccount)},"addons":${SettingsStore.customAddons.size},"streams":${SettingsStore.streamAddons.size},"apis":${SettingsStore.apiKeys.size}}""",
                        contentType = "application/json; charset=utf-8"
                    )
                }
                method == "POST" && path == "/save" -> {
                    val form = parseForm(body)
                    val code = form["code"].orEmpty()
                    if (!code.equals(SettingsStore.pairingCode, ignoreCase = true)) {
                        respond(s, "403 Forbidden", portalHtml(codeOk = false, message = "❌ Código de emparejamiento incorrecto"))
                        return
                    }
                    form["account"]?.trim()?.takeIf { it.isNotBlank() }?.let { SettingsStore.setLinkedAccount(it) }
                    form["addons"]?.let { text ->
                        SettingsStore.setCustomAddons(text.lines().map { it.trim() }.filter { it.startsWith("http") })
                    }
                    form["streamAddons"]?.let { text ->
                        SettingsStore.setStreamAddons(text.lines().map { it.trim() }.filter { it.startsWith("http") })
                    }
                    form["apis"]?.let { text ->
                        val keys = text.lines()
                            .map { it.trim() }
                            .filter { '=' in it }
                            .associate {
                                val (k, v) = it.split('=', limit = 2)
                                k.trim().uppercase() to v.trim()
                            }
                        SettingsStore.setApiKeys(keys)
                    }
                    lastAction.value = "✅ Configuración guardada desde el panel web (addons: ${SettingsStore.customAddons.size}, streams: ${SettingsStore.streamAddons.size}, APIs: ${SettingsStore.apiKeys.size})"
                    respond(
                        s, "200 OK",
                        portalHtml(
                            codeOk = true,
                            message = "✅ Guardado. La cuenta, los addons y las APIs ya están en el televisor."
                        )
                    )
                }
                else -> respond(s, "404 Not Found", portalHtml(codeOk = false, message = "Página no encontrada"))
            }
        }
    }

    private fun respond(
        socket: Socket,
        status: String,
        body: String,
        contentType: String = "text/html; charset=utf-8"
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("\r\n")
        }
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Utilidades                                                        */
    /* ------------------------------------------------------------------ */

    private fun parseForm(text: String): Map<String, String> =
        text.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1).replace('+', ' '), "UTF-8")
                key to value
            }
        }.toMap()

    private fun json(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun esc(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /* ------------------------------------------------------------------ */
    /*  Portal web (móvil)                                                */
    /* ------------------------------------------------------------------ */

    private fun portalHtml(codeOk: Boolean, message: String = ""): String {
        val codeField = if (codeOk) {
            """<input type="hidden" name="code" value="${esc(SettingsStore.pairingCode)}">""" +
                """<p class="ok">Código verificado ✓</p>"""
        } else {
            """<label>Código de emparejamiento (se muestra en la TV)
               <input name="code" placeholder="Ej: ${esc(SettingsStore.pairingCode)}" required></label>"""
        }
        val addonsValue = esc(SettingsStore.customAddons.joinToString("\n"))
        val streamsValue = esc(SettingsStore.streamAddons.joinToString("\n"))
        val apisValue = esc(SettingsStore.apiKeys.entries.joinToString("\n") { "${it.key}=${it.value}" })

        return """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Omni TV · Vinculación</title>
<style>
  body { margin:0; font-family:system-ui,sans-serif; background:#0B0B0F; color:#F5F5F7; }
  .wrap { max-width:640px; margin:0 auto; padding:24px 18px 60px; }
  h1 { font-size:22px; margin:0 0 4px; } h1 span { color:#0A84FF; }
  .sub { color:#8E8E93; font-size:13px; margin:0 0 22px; }
  .card { background:#16161C; border:1px solid rgba(255,255,255,.08); border-radius:14px; padding:18px; margin:14px 0; }
  .card h2 { font-size:15px; margin:0 0 12px; }
  label { display:block; font-size:12px; color:#8E8E93; margin:10px 0 4px; }
  input, textarea { width:100%; box-sizing:border-box; background:#0F0F14; color:#F5F5F7;
    border:1px solid rgba(255,255,255,.14); border-radius:10px; padding:12px; font-size:15px; }
  textarea { min-height:96px; font-family:ui-monospace,monospace; font-size:13px; }
  button { width:100%; margin-top:18px; background:#0A84FF; color:#fff; border:0; border-radius:12px;
    padding:15px; font-size:16px; font-weight:700; }
  .ok { color:#30D158; font-size:13px; } .msg { background:#1F1F26; border-radius:10px; padding:12px; font-size:14px; margin:12px 0; }
  .hint { color:#6E6E73; font-size:11px; margin-top:6px; }
</style>
</head>
<body>
<div class="wrap">
  <h1>Omni <span>TV</span> · Panel de vinculación</h1>
  <p class="sub">Conectado con tu televisor · ${esc(SettingsStore.pairingCode)}</p>
  ${if (message.isNotEmpty()) """<div class="msg">${message}</div>""" else ""}
  <form method="POST" action="/save">
    $codeField
    <div class="card">
      <h2>👤 Enlazar cuenta</h2>
      <label>Email de tu cuenta Omni TV
        <input name="account" type="email" placeholder="tu@email.com" value="${esc(SettingsStore.linkedAccount)}">
      </label>
    </div>
    <div class="card">
      <h2>🧩 Añadir addons</h2>
      <label>URLs de manifest.json (una por línea)
        <textarea name="addons" placeholder="https://v3-cinemeta.strem.io/manifest.json">$addonsValue</textarea>
      </label>
      <p class="hint">Cualquier addon de Stremio compatible (catálogo/meta). Aparecerá como filas nuevas en el Inicio.</p>
    </div>
    <div class="card">
      <h2>🎬 Añadir addons de streams</h2>
      <label>Proyectos que entregan enlaces de reproducción (una URL por línea)
        <textarea name="streamAddons" placeholder="https://tu-proyecto.ejemplo">$streamsValue</textarea>
      </label>
      <p class="hint">El addon interno «Omni Streams» les pide los streams con el protocolo
        <code>stream/{tipo}/{id}</code> (Stremio). Compatible con proyectos tipo
        StreamFlix Reborn, Torrentio, Comet… que expongan su API de enlaces.</p>
    </div>
    <div class="card">
      <h2>🔑 Añadir APIs</h2>
      <label>Una por línea con el formato NOMBRE=clave
        <textarea name="apis" placeholder="TMDB=tu_api_key&#10;TRAKT=tu_api_key">$apisValue</textarea>
      </label>
      <p class="hint">Se guardan cifradas localmente en el televisor para integraciones.</p>
    </div>
    <button type="submit">Guardar en el televisor</button>
  </form>
</div>
</body>
</html>
        """.trimIndent()
    }
}
