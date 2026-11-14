package com.omnitv.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Resolución de nombres por **DNS sobre HTTPS** (DNS de Cloudflare, RFC 8484).
 *
 * Para qué: en Android TV hay routers, cajas de operador y redes de hotel
 * donde el DNS falla o filtra, y los addons (`v3-cinemeta.strem.io`,
 * `catalog.nuvio.tv`) se quedan cargando sin explicación. Con DoH la consulta
 * va **cifrada por https**, así que no depende de que el router la entienda ni
 * de que no la manipule.
 *
 * Cómo se usa: **solo como red de seguridad**. Cada cliente intenta primero su
 * conexión normal; si esa falla (típicamente `UnknownHostException`), [retry]
 * resuelve el nombre con Cloudflare y reintenta contra la IP.
 *
 * Seguridad:
 * - Solo `https://`. Un `http://` nunca sale por aquí: [NetPolicy] solo lo
 *   permite en la red local, donde un resolutor público no pinta nada.
 * - El certificado se sigue validando **contra el nombre original**, no contra
 *   la IP: apuntar a una IP conocida no permite que nadie se haga pasar por el
 *   servidor (ver [hostnameVerifier]).
 * - Si DoH falla o responde algo raro, se devuelve `null` y la app se queda
 *   como estaba (sin datos), nunca con datos de un tercero.
 */
object Doh {

    /** Endpoint JSON del DNS de Cloudflare (el mismo que usa 1.1.1.1). */
    const val CLOUDFLARE_URL = "https://cloudflare-dns.com/dns-query"

    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    /**
     * Interruptor de Ajustes. Si está apagado, [retry] no hace nada y la app
     * usa solo el DNS del sistema (como antes).
     */
    @Volatile
    var enabled: Boolean = true

    /** Un JSON de A/AAAA no llega a 2 KB; el tope protege la memoria del TV. */
    private const val MAX_BODY_BYTES = 64 * 1024

    /** Tope del cuerpo que se acepta al reintentar una descarga por DoH. */
    private const val MAX_RETRY_BYTES = 8 * 1024 * 1024

    private const val CACHE_ENTRIES = 64
    private const val MIN_TTL_MS = 30_000L
    private const val MAX_TTL_MS = 5 * 60_000L

    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 8_000

    private class Entry(val addresses: List<InetAddress>, val expiresAt: Long)

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > CACHE_ENTRIES
    }

    /**
     * Resuelve [host] con [endpoint] (Cloudflare por defecto) y devuelve sus
     * direcciones, vacío si no hay ninguna. Sin caché: ver [cached].
     *
     * [fetch] es inyectable para poder probarlo sin red.
     */
    fun resolve(
        host: String,
        endpoint: String = CLOUDFLARE_URL,
        fetch: (String) -> String? = ::httpGetText
    ): List<InetAddress> = resolveWithTtl(host, endpoint, fetch).first

    /** Como [resolve], pero además devuelve el TTL (segundos) que anunció el DNS. */
    internal fun resolveWithTtl(
        host: String,
        endpoint: String = CLOUDFLARE_URL,
        fetch: (String) -> String? = ::httpGetText
    ): Pair<List<InetAddress>, Long> {
        val name = normalizeHost(host) ?: return emptyList<InetAddress>() to 0L
        val found = LinkedHashMap<String, InetAddress>()
        var ttl = Long.MAX_VALUE
        var anyAnswer = false
        for (type in intArrayOf(TYPE_A, TYPE_AAAA)) {
            val body = runCatching { fetch(queryUrl(name, type, endpoint)) }.getOrNull() ?: continue
            val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
            // Un error de DNS (Status != 0) descarta la respuesta entera
            if (json.optInt("Status", -1) != 0) continue
            val answers = parseAnswers(json)
            if (answers.isEmpty()) continue
            anyAnswer = true
            ttl = minOf(ttl, recordTtl(json))
            for (address in answers) {
                val key = address.hostAddress ?: continue
                if (!found.containsKey(key)) found[key] = address
            }
        }
        if (!anyAnswer) return emptyList<InetAddress>() to 0L
        return found.values.toList() to ttl.coerceAtLeast(0L)
    }

    /** Como [resolve], con caché en memoria y respetando el TTL de la respuesta. */
    fun cached(
        host: String,
        endpoint: String = CLOUDFLARE_URL,
        now: () -> Long = System::currentTimeMillis,
        fetch: (String) -> String? = ::httpGetText
    ): List<InetAddress> {
        val name = normalizeHost(host) ?: return emptyList()
        val time = now()
        synchronized(cache) {
            cache[name]?.let { if (time < it.expiresAt) return it.addresses }
        }
        val (addresses, ttlSeconds) = resolveWithTtl(name, endpoint, fetch)
        if (addresses.isNotEmpty()) {
            val ttl = (ttlSeconds * 1000L).coerceIn(MIN_TTL_MS, MAX_TTL_MS)
            synchronized(cache) { cache[name] = Entry(addresses, time + ttl) }
        }
        return addresses
    }

    /** Vacía la caché (tests y cambio de red). */
    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    /**
     * Reintenta descargar [url] cuando la conexión normal falló: resuelve el
     * nombre por DoH y conecta a la IP **validando el certificado del nombre
     * original**. Devuelve el cuerpo, o `null` si no hay forma segura.
     */
    fun retry(
        url: String,
        enabled: Boolean = this.enabled,
        fetch: (String) -> String? = ::httpGetText
    ): String? {
        if (!enabled) return null
        val target = runCatching { URL(url) }.getOrNull() ?: return null
        if (target.protocol?.lowercase() != "https") return null
        val host = normalizeHost(target.host) ?: return null
        if (parseLiteral(host) != null) return null // si ya era una IP, no es cosa del DNS

        // Resolución fresca (sin caché): este camino solo se pisa tras un fallo
        if (!NetPolicy.isAllowed(url)) return null
        val addresses = resolve(host, fetch = fetch).take(3)
        for (address in addresses) {
            val body = runCatching { fetchWithHostOverride(target, host, address) }.getOrNull()
            if (body != null) return body
        }
        return null
    }

    /**
     * Verificación del certificado en el reintento por IP: se valida el
     * certificado presentado **contra el nombre original**, con el verificador
     * del sistema. Nunca se acepta un certificado sin comprobar.
     */
    internal fun hostnameVerifier(host: String) = javax.net.ssl.HostnameVerifier { _, session ->
        HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)
    }

    /** URL de la consulta DoH (`?name=…&type=1`), con el nombre escapado. */
    internal fun queryUrl(host: String, type: Int, endpoint: String = CLOUDFLARE_URL): String =
        "${endpoint.trimEnd('/')}?name=${escape(host)}&type=$type"

    /**
     * Direcciones de una respuesta DoH. Solo acepta `Status == 0` (sin error)
     * y registros `A`/`AAAA`: los CNAME y el texto libre se ignoran.
     */
    internal fun parseAnswers(json: JSONObject): List<InetAddress> {
        if (json.optInt("Status", -1) != 0) return emptyList()
        val answers = json.optJSONArray("Answer") ?: return emptyList()
        val result = LinkedHashMap<String, InetAddress>()
        for (i in 0 until minOf(answers.length(), 32)) {
            val record = answers.optJSONObject(i) ?: continue
            if (record.optInt("type", -1) != TYPE_A && record.optInt("type", -1) != TYPE_AAAA) continue
            val address = parseLiteral(record.optString("data", "")) ?: continue
            val key = address.hostAddress ?: continue
            if (!result.containsKey(key)) result[key] = address
        }
        return result.values.toList()
    }

    /**
     * TTL (segundos) del primer registro de la respuesta. El TTL va en cada
     * `Answer`, no en la raíz del JSON; si falta, 0 (se aplicará el mínimo).
     */
    internal fun recordTtl(json: JSONObject): Long {
        val answers = json.optJSONArray("Answer") ?: return 0L
        for (i in 0 until answers.length()) {
            val record = answers.optJSONObject(i) ?: continue
            return record.optInt("TTL", 0).toLong()
        }
        return 0L
    }

    /** IP literal (v4 o v6, con o sin corchetes) o `null` si es un nombre. */
    internal fun parseLiteral(text: String): InetAddress? {
        val value = text.trim().removePrefix("[").removeSuffix("]")
        if (value.isEmpty() || value.length > 45) return null
        if (value.any { it.isWhitespace() || it == '/' || it == '%' || it == '@' }) return null
        val isV4 = value.all { it == '.' || it in '0'..'9' }
        val isV6 = ':' in value && value.all {
            it == ':' || it == '.' || it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
        }
        if (!isV4 && !isV6) return null
        return runCatching { InetAddress.getByName(value) }.getOrNull()
            ?.takeIf { it is Inet4Address || it is Inet6Address }
    }

    /**
     * Nombre de host válido: sin esquema, puerto, espacios ni credenciales.
     * Devuelve `null` si no es un nombre al que tenga sentido preguntar por
     * DoH (por ejemplo, una IP literal: se conecta tal cual).
     */
    private fun normalizeHost(host: String): String? {
        val name = host.trim().trimEnd('.')
        if (name.isEmpty() || name.length > 253) return null
        if (name.any { it.isWhitespace() || it == '/' || it == '@' || it == '\\' }) return null
        if (':' in name) return null // IPv6 literal o nombre con puerto: no es un nombre
        if (name.all { it == '.' || it in '0'..'9' }) return null // IPv4 literal
        return name
    }

    /** Escapa el nombre para la query (letras, dígitos, `-`, `.`, `_`). */
    private fun escape(value: String): String = buildString {
        for (c in value) {
            when {
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' -> append(c)
                else -> append('%').append(String.format(java.util.Locale.US, "%02X", c.code))
            }
        }
    }

    /**
     * GET con `Accept: application/dns-json` al endpoint de Cloudflare.
     * Es el formato JSON documentado de 1.1.1.1 (mismo cuerpo que la consulta
     * binaria de RFC 8484, sin tener que construir el paquete DNS a mano).
     */
    private fun httpGetText(url: String): String? {
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/dns-json")
            connection.setRequestProperty("User-Agent", "OmniTV/1.0 (Android TV)")
            if (connection.responseCode !in 200..299) return null
            return readLimited(connection.inputStream, MAX_BODY_BYTES)
        } finally {
            connection.disconnect()
        }
    }

    /** Conexión a la IP resuelta con el `Host` del nombre original y su certificado. */
    private fun fetchWithHostOverride(target: URL, host: String, address: InetAddress): String? {
        val literal = address.hostAddress ?: return null
        val urlWithIp = URL(
            target.protocol,
            if (address is Inet6Address) "[$literal]" else literal,
            if (target.port > 0) target.port else target.defaultPort,
            target.file
        )
        val connection = urlWithIp.openConnection() as HttpsURLConnection
        try {
            // El nombre original viaja en Host y es el que valida el certificado
            connection.setRequestProperty("Host", host)
            connection.hostnameVerifier = hostnameVerifier(host)
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "OmniTV/1.0 (Android TV)")
            if (connection.responseCode !in 200..299) return null
            return readLimited(connection.inputStream, MAX_RETRY_BYTES)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(stream: java.io.InputStream, max: Int): String? = runCatching {
        stream.use { input ->
            val buffer = ByteArray(16 * 1024)
            val output = java.io.ByteArrayOutputStream()
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                output.write(buffer, 0, n)
                if (output.size() > max) return null
            }
            output.toString("UTF-8")
        }
    }.getOrNull()
}
