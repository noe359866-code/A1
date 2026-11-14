package com.omnitv.app.data

import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * HTTP JSON para addons y puentes. Cada redirección se valida con [NetPolicy],
 * el cuerpo está limitado y se admite un objeto o array en la raíz.
 * Un POST no se reenvía automáticamente a otro destino.
 */
internal object AddonHttp {
    const val MAX_BODY = 4 * 1024 * 1024
    private const val MAX_REDIRECTS = 5

    fun getJson(url: String): Any? {
        val result = request("GET", url, null)
        if (result.value != null || !result.dnsFailure) return result.value
        // DoH solo sustituye una resolución DNS fallida, no un HTTP 404/500.
        val fallback = Doh.retry(url) ?: return null
        return parseJson(fallback)
    }

    fun postJson(url: String, body: String): Any? = request("POST", url, body).value

    internal data class Result(val value: Any?, val dnsFailure: Boolean = false)

    internal fun request(method: String, initialUrl: String, body: String?): Result {
        if (!NetPolicy.isAllowed(initialUrl)) return Result(null)
        var current = initialUrl
        repeat(MAX_REDIRECTS + 1) {
            val connection = runCatching {
                (URL(current).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 6_000
                    readTimeout = 10_000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "OmniTV/1.2 (Android TV)")
                    setRequestProperty("Accept", "application/json")
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
                    if (method != "GET") return Result(null)
                    val location = connection.getHeaderField("Location") ?: return Result(null)
                    val next = runCatching { URI(current).resolve(location).toString() }.getOrNull() ?: return Result(null)
                    if (!NetPolicy.isAllowed(next)) return Result(null)
                    current = next
                    return@repeat
                }
                if (code !in 200..299) return Result(null)
                return Result(parseJson(readLimited(connection) ?: return Result(null)))
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
