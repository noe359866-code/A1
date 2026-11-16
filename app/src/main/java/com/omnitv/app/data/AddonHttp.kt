package com.omnitv.app.data

import com.omnitv.app.BuildConfig
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * HTTP para addons, puentes y scraping de catálogos. Cada redirección se
 * valida con [NetPolicy], el cuerpo está limitado y se admite un objeto o
 * array en la raíz. Un POST no se reenvía automáticamente a otro destino.
 *
 * Es el **único** cliente HTTP de la app: así el límite de 4 MB, la política
 * de red y el fallback DNS (DoH) no dependen de que cada scraper recuerde
 * aplicarlos.
 */
internal object AddonHttp {
    const val MAX_BODY = 4 * 1024 * 1024
    private const val MAX_REDIRECTS = 5

    fun getJson(url: String, headers: Map<String, String> = emptyMap()): Any? {
        val result = request("GET", url, null, headers)
        if (result.value != null || !result.dnsFailure) return result.value
        // DoH solo sustituye una resolución DNS fallida, no un HTTP 404/500.
        val fallback = Doh.retry(url)?.takeIf { it.length <= MAX_BODY } ?: return null
        return parseJson(fallback)
    }

    /** GET con estado HTTP: quien llama puede distinguir 429 de «sin datos». */
    fun getJsonWithStatus(
        url: String,
        headers: Map<String, String> = emptyMap(),
        readTimeoutMs: Int = 10_000
    ): Result {
        val result = request("GET", url, null, headers, readTimeoutMs)
        if (result.value != null || !result.dnsFailure) return result
        val fallback = Doh.retry(url)?.takeIf { it.length <= MAX_BODY } ?: return result
        val value = parseJson(fallback)
        return result.copy(value = value, dnsFailure = false, status = if (value != null) 200 else result.status, raw = fallback)
    }

    fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        readTimeoutMs: Int = 10_000
    ): Any? = request("POST", url, body, headers, readTimeoutMs).value

    /** Cuerpo de texto (HTML, manifiesto…), sin parsear como JSON. */
    fun getText(url: String, headers: Map<String, String> = emptyMap()): String? =
        getTextWithMeta(url, headers).text

    /** Como [getText], pero conserva la URL final tras redirecciones válidas. */
    fun getTextWithMeta(url: String, headers: Map<String, String> = emptyMap()): TextResult {
        if (!NetPolicy.isAllowed(url)) return TextResult(null, url)
        val withAccept = if (headers.keys.any { it.equals("Accept", true) }) headers else headers + ("Accept" to "text/html")
        val result = request("GET", url, null, withAccept)
        if (result.raw != null || !result.dnsFailure) {
            return TextResult(result.raw, result.finalUrl.ifBlank { url }, result.status)
        }
        val fallback = Doh.retry(url)?.takeIf { it.length <= MAX_BODY }
            ?: return TextResult(null, url, result.status)
        return TextResult(fallback, url, 200)
    }

    internal data class Result(
        val value: Any?,
        val dnsFailure: Boolean = false,
        val status: Int = 0,
        val raw: String? = null,
        val finalUrl: String = ""
    )

    internal data class TextResult(val text: String?, val finalUrl: String, val status: Int = 0)

    internal fun request(
        method: String,
        initialUrl: String,
        body: String?,
        headers: Map<String, String> = emptyMap(),
        readTimeoutMs: Int = 10_000
    ): Result {
        if (!NetPolicy.isAllowed(initialUrl)) return Result(null)
        var current = initialUrl
        repeat(MAX_REDIRECTS + 1) {
            val connection = runCatching {
                (URL(current).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 6_000
                    readTimeout = readTimeoutMs.coerceIn(1_000, 120_000)
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "OmniTV/${BuildConfig.VERSION_NAME} (Android TV)")
                    setRequestProperty("Accept", "application/json")
                    val sameOrigin = runCatching {
                        val a = URI(current); val b = URI(initialUrl)
                        a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) &&
                            (if (a.port >= 0) a.port else if (a.scheme.equals("https", true)) 443 else 80) ==
                            (if (b.port >= 0) b.port else if (b.scheme.equals("https", true)) 443 else 80)
                    }.getOrDefault(false)
                    headers.forEach { (name, value) ->
                        val credential = name.equals("Authorization", true) || name.equals("X-API-Key", true) || name.equals("X-Bridge-Token", true)
                        // Las credenciales no viajan a otro host tras una redirección.
                        if (value.isNotBlank() && (!credential || sameOrigin)) setRequestProperty(name, value)
                    }
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                }
            }.getOrNull() ?: return Result(null)
            try {
                if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code in 300..399) {
                    if (method != "GET") return Result(null, status = code)
                    val location = connection.getHeaderField("Location") ?: return Result(null, status = code)
                    val next = runCatching { URI(current).resolve(location).toString() }.getOrNull() ?: return Result(null, status = code)
                    if (!NetPolicy.isAllowed(next)) return Result(null, status = code)
                    current = next
                    return@repeat
                }
                if (code !in 200..299) return Result(null, status = code)
                val text = readLimited(connection) ?: return Result(null, status = code)
                return Result(parseJson(text), status = code, raw = text, finalUrl = current)
            } catch (_: java.net.UnknownHostException) {
                return Result(null, dnsFailure = true)
            } catch (_: Exception) {
                return Result(null)
            } finally {
                connection.disconnect()
            }
        }
        return Result(null)
    }

    internal fun parseJson(text: String): Any? = runCatching {
        if (text.length > MAX_BODY) null else JSONTokener(text).nextValue().takeIf { it is org.json.JSONObject || it is org.json.JSONArray }
    }.getOrNull()

    private fun readLimited(connection: HttpURLConnection): String? {
        // contentLengthLong requiere API 24; el proyecto conserva minSdk 22.
        val declared = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
        if (declared > MAX_BODY) return null
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > MAX_BODY) return null
                output.write(buffer, 0, read)
            }
            output.toString("UTF-8")
        }
    }
}
