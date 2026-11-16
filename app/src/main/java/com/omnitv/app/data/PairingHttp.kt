package com.omnitv.app.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder

/*
 * Piezas sin estado del panel de vinculación: lectura de la petición HTTP,
 * formularios y límite de intentos. Sin dependencias de Android, para que
 * se puedan probar con tests unitarios.
 */

/** Petición HTTP ya interpretada. */
internal class PanelRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: String
)

/** Respuesta del panel. [extraHeaders] permite, por ejemplo, `Set-Cookie`. */
internal class PanelResponse(
    val status: Int,
    val body: String,
    val contentType: String = "text/html; charset=utf-8",
    val extraHeaders: List<String> = emptyList()
)

/** La cabecera o el cuerpo superan el tamaño permitido (→ 413). */
internal class RequestTooLargeException : RuntimeException()

/**
 * Lee una petición HTTP/1.1 de [input]. Devuelve `null` si el cliente cerró
 * o envió algo que no es HTTP.
 *
 * Cabecera y cuerpo se leen **del mismo flujo, byte a byte**: un lector con
 * búfer propio se tragaría el cuerpo junto a la cabecera y el `POST` se
 * quedaría esperando datos que ya llegaron. Los tamaños están acotados para
 * que nadie en la red pueda agotar la memoria del televisor.
 */
internal fun readPanelRequest(
    input: InputStream,
    maxHeaderBytes: Int = 16 * 1024,
    maxBodyBytes: Int = 64 * 1024
): PanelRequest? {
    val head = ByteArrayOutputStream()
    var lineBreaks = 0
    while (lineBreaks < 2) {
        val b = input.read()
        if (b < 0) return null
        head.write(b)
        if (head.size() > maxHeaderBytes) throw RequestTooLargeException()
        when (b) {
            '\n'.code -> lineBreaks++
            '\r'.code -> Unit
            else -> lineBreaks = 0
        }
    }

    val lines = head.toString("ISO-8859-1").split('\n').map { it.trimEnd('\r') }
    val requestLine = lines.first().split(' ').filter { it.isNotEmpty() }
    if (requestLine.size < 2) return null
    val method = requestLine[0].uppercase()
    val target = requestLine[1]

    val headers = HashMap<String, String>()
    for (line in lines.drop(1)) {
        val idx = line.indexOf(':')
        if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
    }

    val declared = headers["content-length"]
    val contentLength = if (declared == null) 0 else declared.toIntOrNull() ?: return null
    if (contentLength < 0) return null
    if (contentLength > maxBodyBytes) throw RequestTooLargeException()

    val bodyBytes = ByteArray(contentLength)
    var read = 0
    while (read < contentLength) {
        val n = input.read(bodyBytes, read, contentLength - read)
        if (n < 0) break
        read += n
    }

    return PanelRequest(
        method = method,
        path = target.substringBefore('?'),
        query = parseForm(target.substringAfter('?', "")),
        headers = headers,
        body = String(bodyBytes, 0, read, Charsets.UTF_8)
    )
}

/** Decodifica `a=1&b=2` (query o cuerpo `application/x-www-form-urlencoded`). */
internal fun parseForm(text: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for (pair in text.split('&')) {
        val idx = pair.indexOf('=')
        if (idx <= 0) continue
        // Un «%» mal formado descarta solo ese campo, no la petición entera
        runCatching {
            val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
            val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            result[key] = value
        }
    }
    return result
}

/**
 * Freno a la fuerza bruta del código de emparejamiento: tras [maxFailures]
 * intentos fallidos seguidos, ese cliente queda bloqueado [lockMillis].
 */
internal class AttemptGuard(
    private val maxFailures: Int = 5,
    private val lockMillis: Long = 60_000L,
    private val now: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private class Entry(var failures: Int, var lastFailure: Long, var lockedUntil: Long)

    private val entries = HashMap<String, Entry>()

    /** El cliente está bloqueado ahora mismo. */
    @Synchronized
    fun isLocked(client: String): Boolean {
        val entry = entries[client] ?: return false
        return now() < entry.lockedUntil
    }

    /** Apunta un intento fallido; bloquea al cliente si agota los permitidos. */
    @Synchronized
    fun recordFailure(client: String) {
        val time = now()
        if (entries.size >= MAX_CLIENTS) {
            // Olvida a quien ya cumplió su bloqueo y lleva un rato sin fallar
            entries.values.removeAll { time >= it.lockedUntil && time - it.lastFailure > lockMillis }
            // Si aun así no cabe, no se apuntan clientes nuevos (memoria acotada)
            if (entries.size >= MAX_CLIENTS && client !in entries) return
        }
        val entry = entries.getOrPut(client) { Entry(0, time, 0L) }
        if (time - entry.lastFailure > lockMillis) entry.failures = 0
        entry.failures++
        entry.lastFailure = time
        if (entry.failures >= maxFailures) {
            entry.failures = 0
            entry.lockedUntil = time + lockMillis
        }
    }

    /** Un acierto borra el historial de fallos del cliente. */
    @Synchronized
    fun recordSuccess(client: String) {
        entries.remove(client)
    }

    private companion object {
        const val MAX_CLIENTS = 512
    }
}

/**
 * Sesiones temporales del panel de vinculación.
 *
 * El QR lleva el código (`/?code=…`); el móvil aterriza en una sesión con
 * cookie `HttpOnly` ligada a su IP y al código vigente, y un 303 a `/` borra
 * el código de la URL. Si el código se regenera en Ajustes, la huella cambia y
 * **todas** las sesiones abiertas dejan de valer sin necesidad de avisos.
 */
internal class SessionGuard(
    private val ttlMillis: Long = 10 * 60_000L,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val random: java.security.SecureRandom = java.security.SecureRandom()
) {
    private class Entry(val client: String, val fingerprint: String, val expiresAt: Long)

    private val sessions = HashMap<String, Entry>()
    private val hex = "0123456789abcdef".toCharArray()

    @Synchronized
    fun issue(client: String, code: String): String {
        prune()
        if (sessions.size >= MAX_SESSIONS) {
            // Memoria acotada: si una LAN muy ruidosa abre demasiadas
            // sesiones, se cierran todas en lugar de crecer sin límite.
            sessions.clear()
        }
        val token = ByteArray(24).also { random.nextBytes(it) }.toHex()
        sessions[token] = Entry(client, fingerprint(code), now() + ttlMillis)
        return token
    }

    @Synchronized
    fun valid(token: String?, client: String, code: String): Boolean {
        if (token.isNullOrBlank()) return false
        val entry = sessions[token] ?: return false
        if (now() >= entry.expiresAt) {
            sessions.remove(token)
            return false
        }
        val expected = fingerprint(code).toByteArray(Charsets.US_ASCII)
        return entry.client == client && java.security.MessageDigest.isEqual(
            entry.fingerprint.toByteArray(Charsets.US_ASCII), expected
        )
    }

    @Synchronized
    fun clear() {
        sessions.clear()
    }

    private fun prune() {
        val time = now()
        sessions.entries.removeAll { time >= it.value.expiresAt }
    }

    private fun fingerprint(code: String): String = java.security.MessageDigest
        .getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8)).toHex()

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (b in this@toHex) {
            val v = b.toInt() and 0xff
            append(hex[v ushr 4]); append(hex[v and 0x0f])
        }
    }

    private companion object {
        const val MAX_SESSIONS = 512
    }
}
