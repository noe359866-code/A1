/*
 * OmniStreamServer.kt — puente mínimo para entregar streams a Omni TV
 * desde una app Android tipo StreamFlix (providers/extractors).
 *
 * COPIA ESTE ARCHIVO en tu proyecto (cambia el `package` de arriba) y
 * engancha tu resolver en el constructor — ver el bloque «WIRE AQUÍ» de
 * `main()` o la sección «Cableado» de README.md.
 *
 * Implementa el protocolo de docs/STREAM-ADDON-PROTOCOL.md:
 *
 *     GET /stream/{tipo}/{id}.json     ← forma estándar (Stremio)
 *     GET /stream/{tipo}/{id}          ← forma simple (alternativa)
 *
 * Sin dependencias: solo JDK/Android (ServerSocket). JSON manual.
 * Ejecuta `main()` en la JVM para probar con curl antes de meterlo en la app.
 */
package com.tuproyecto.omni

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.Executors

/** Enlace de reproducción que tu proyecto entrega a Omni TV. */
data class OmniStream(
    val label: String,
    val url: String,
    val quality: String? = null,
    val title: String? = null
)

class OmniStreamServer(
    private val port: Int = 8787,
    /** PUNTO DE ENGANCHE: (tipo, id) → lista de enlaces de TU proyecto. */
    private val resolver: (kind: String, id: String) -> List<OmniStream>
) {
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r ->
        Thread(r, "omni-stream-bridge").apply { isDaemon = true }
    }

    @Volatile
    var running = false
        private set

    fun start() {
        if (running) return
        server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port)) // 0.0.0.0 → accesible desde la LAN
        }
        running = true
        pool.execute {
            while (running) {
                val socket = try {
                    server!!.accept()
                } catch (_: Exception) {
                    break
                }
                pool.execute { handle(socket) }
            }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
    }

    // ---------------------- HTTP mínimo ----------------------------------

    private fun handle(socket: java.net.Socket) {
        socket.use { s ->
            try {
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                while (true) {
                    val h = reader.readLine() ?: break
                    if (h.isEmpty()) break // fin de cabeceras
                }
                val parts = requestLine.split(" ")
                if (parts.size < 2 || parts[0] != "GET") {
                    respond(s.getOutputStream(), 405, """{"err":"solo GET"}""")
                    return
                }
                val path = URLDecoder.decode(parts[1].substringBefore('?'), "UTF-8")
                when {
                    path == "/health" ->
                        respond(s.getOutputStream(), 200, """{"ok":true,"name":"omni-stream-server"}""")
                    path == "/" || path == "/index.html" ->
                        respond(s.getOutputStream(), 200, INDEX, "text/html; charset=utf-8")
                    path.startsWith("/stream/") -> handleStream(s.getOutputStream(), path.removePrefix("/stream/"))
                    else -> respond(s.getOutputStream(), 404, """{"err":"ruta no encontrada"}""")
                }
            } catch (_: Exception) {
                // conexión rota: no tumbar el servidor
            }
        }
    }

    /**
     * `http://` solo hacia la red local: IP privada literal (10/8, 172.16/12,
     * 192.168/16, 169.254/16, 127/8) o `localhost`. Sin resolver DNS, para que
     * un nombre público no pueda apuntar a la LAN.
     */
    private fun isLocalHttp(url: String): Boolean {
        val host = runCatching { java.net.URI(url.trim()).host }.getOrNull()?.lowercase() ?: return false
        if (host == "localhost" || host.endsWith(".local")) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return false
            if (part.length > 1 && part[0] == '0') return false
            part.toInt().also { if (it > 255) return false }
        }
        val (a, b) = octets
        return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
    }

    private fun handleStream(out: OutputStream, rest: String) {
        val clean = if (rest.endsWith(".json")) rest.removeSuffix(".json") else rest
        val slash = clean.indexOf('/')
        if (slash <= 0 || slash == clean.length - 1) {
            respond(out, 400, """{"err":"formato: /stream/{tipo}/{id}"}""")
            return
        }
        val kind = clean.substring(0, slash).lowercase()
        val id = clean.substring(slash + 1)
        val kindOk = kind == "movie" || kind == "series" || kind == "tv" || kind == "serie"
        val idOk = id.isNotEmpty() && id.all { it.isLetterOrDigit() || it in ":-_." }
        if (!kindOk || !idOk) {
            respond(out, 400, """{"err":"tipo o id inválidos"}""")
            return
        }
        val normalizedKind = if (kind == "tv" || kind == "serie") "series" else kind
        val streams = try {
            resolver(normalizedKind, id)
                // Misma regla que la app: https siempre, http solo en la red local
                // (el puente se sirve en http://<ip-lan>:8787 y lo que resuelve
                // hacia fuera debe ir cifrado o quedarse en la LAN).
                .filter { it.url.startsWith("https://") || (it.url.startsWith("http://") && isLocalHttp(it.url)) }
                .distinctBy { it.url }
                .take(24)
        } catch (_: Exception) {
            emptyList()
        }
        // SUPERFORMA (name+label+title+url+quality): la parsean igual el
        // cliente estándar y el simple de Omni TV.
        val items = streams.joinToString(",") { s ->
            val label = json(s.label.ifBlank { "Servidor" })
            val title = json(s.title ?: s.quality ?: "")
            val quality = json(s.quality ?: inferQuality(s.label + " " + s.url))
            """{"name":$label,"label":$label,"title":$title,"url":${json(s.url)},"quality":$quality}"""
        }
        respond(out, 200, """{"streams":[$items],"cacheMaxAge":60}""")
    }

    private fun respond(out: OutputStream, code: Int, body: String, ctype: String = "application/json; charset=utf-8") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "200 OK"; 400 -> "400 Bad Request"; 404 -> "404 Not Found"
            405 -> "405 Method Not Allowed"; else -> "500 Internal Server Error"
        }
        val head = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: $ctype\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun json(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\")
            '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    private fun inferQuality(text: String): String {
        val t = text.lowercase()
        return when {
            "2160" in t -> "2160p"; "1440" in t -> "1440p"; "1080" in t -> "1080p"
            "720" in t -> "720p"; "480" in t -> "480p"; "360" in t -> "360p"
            else -> "SD"
        }
    }

    companion object {
        private val INDEX = """<!doctype html><html lang="es"><meta charset="utf-8">
<title>omni-stream-server</title><body style="font-family:system-ui;background:#0B0B0F;color:#eee;padding:2rem">
<h1 style="color:#0A84FF">🌉 omni-stream-server</h1>
<p>Puente de streams para Omni TV. Estado: <a style="color:#0A84FF" href="/health">/health</a></p>
<pre>GET /stream/movie/tt1375666.json
GET /stream/series/tt0944947:1:1.json</pre></body></html>"""
    }
}

// ------------------------------------------------------------------------
// WIRE AQUÍ — ejemplo ejecutable en JVM (prueba con curl antes de la app).
// En tu app Android, instancia OmniStreamServer desde tu Application/Activity
// con el lambda que llame a tus providers/extractors.
// ------------------------------------------------------------------------
fun main() {
    val server = OmniStreamServer(port = 8787) { kind, id ->
        // ⬇⬇ SUSTITUYE por tus resolvers (ver README.md: cableado CloudStream) ⬇⬇
        println("resolve($kind, $id)")
        listOf(
            OmniStream(
                label = "Demo · Big Buck Bunny",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                quality = "720p",
                title = "$kind:$id · sustituye el resolver por tus extractors"
            ),
            OmniStream(
                label = "Demo · Sintel",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                quality = "720p"
            )
        )
    }
    server.start()
    println("🌉 omni-stream-server en http://0.0.0.0:8787 — Ctrl+C para parar")
    Thread.sleep(Long.MAX_VALUE)
}
