package com.omnitv.app.network

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Solución 403 de Streamflix: AnimeOnlineNinjaCronetClient
 * Usa Cronet (Chromium) en lugar de OkHttp → envía TLS fingerprint de Chrome (JA3)
 * Muchos providers bloquean OkHttp por su JA3; Cronet pasa como navegador real.
 * + Cache de clearance cookies (cf_clearance) de WebView cuando hay challenge.
 */
object CronetClient {

    @Volatile private var engine: CronetEngine? = null
    private val executor: Executor = Executors.newCachedThreadPool()

    @Synchronized
    fun init(context: Context) {
        if (engine != null) return
        val storage = java.io.File(context.applicationContext.cacheDir, "cronet").apply { mkdirs() }
        engine = CronetEngine.Builder(context.applicationContext)
            .setStoragePath(storage.absolutePath)
            .enableHttp2(true)
            .enableBrotli(true)
            .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, (10 * 1024 * 1024).toLong())
            .enableQuic(true)
            .build()
    }

    fun isAvailable(): Boolean = engine != null

    data class CronetResponse(
        val statusCode: Int,
        val body: String,
        val headers: Map<String, List<String>>,
        val finalUrl: String
    )

    suspend fun get(
        url: String,
        headers: Map<String, String> = ArtworkHeaders.forPage(url),
        useCache: Boolean = true
    ): CronetResponse? = withContext(Dispatchers.IO) {
        val eng = engine ?: return@withContext null
        // Inyecta clearance cookies si existen
        val cookieHeader = CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
        val allHeaders = headers.toMutableMap().apply {
            if (cookieHeader != null) put("Cookie", cookieHeader)
        }
        try {
            suspendCoroutine<CronetResponse> { cont ->
                val callback = object : UrlRequest.Callback() {
                    val buffer = ByteBuffer.allocateDirect(1024 * 64)
                    val data = mutableListOf<ByteArray>()
                    var responseInfo: UrlResponseInfo? = null
                    var status = 0
                    var respHeaders: Map<String, List<String>> = emptyMap()

                    override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                        request.followRedirect()
                    }
                    override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                        status = info.httpStatusCode
                        respHeaders = info.allHeaders
                        // guarda Set-Cookie → CookieManager (para cf_clearance)
                        info.allHeaders["set-cookie"]?.forEach { CookieManager.getInstance().setCookie(info.url, it) }
                        request.read(buffer)
                    }
                    override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                        byteBuffer.flip()
                        val bytes = ByteArray(byteBuffer.remaining())
                        byteBuffer.get(bytes)
                        data.add(bytes)
                        byteBuffer.clear()
                        request.read(buffer)
                    }
                    override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                        val body = data.fold(ByteArray(0)) { acc, b -> acc + b }.toString(Charsets.UTF_8)
                        cont.resume(CronetResponse(status, body, respHeaders, info.url))
                    }
                    override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
                        cont.resumeWithException(error)
                    }
                }
                val request = eng.newUrlRequestBuilder(url, callback, executor).apply {
                    setHttpMethod("GET")
                    allHeaders.forEach { (k, v) -> addHeader(k, v) }
                    if (!useCache) addHeader("Cache-Control", "no-cache")
                }.build()
                request.start()
            }
        } catch (_: Exception) { null }
    }

    /** Fetch con fallback: Cronet → OkHttp con headers navegador → WebView (si 403/challenge) */
    suspend fun fetchWithFallback(url: String, baseUrl: String? = null, headers: Map<String, String>? = null): String? {
        val h = headers ?: ArtworkHeaders.forPage(url, baseUrl)

        // 1) Cronet (JA3 Chrome)
        get(url, h)?.let { resp ->
            if (resp.statusCode in 200..299 && ArtworkHeaders.hasUsableContent(resp.body, url)) {
                return resp.body
            }
            if (resp.statusCode == 403 || ArtworkHeaders.requiresClearance(resp.body)) {
                // deja pasar a WebView para cf_clearance
            } else if (resp.statusCode in 200..299) return resp.body
        }

        // 2) OkHttp con Chrome UA (rápido, sin Cronet)
        val okhttpResult = tryOkHttp(url, h)
        if (okhttpResult != null && okhttpResult.length > 500) {
            if (!ArtworkHeaders.requiresClearance(okhttpResult)) return okhttpResult
        }

        // 3) WebView se maneja desde Provider (AnimeOnlineNinjaProvider pattern)
        return null
    }

    private suspend fun tryOkHttp(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try {
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().readText() else null
            } finally { conn.disconnect() }
        }.getOrNull()
    }

    fun persistCookies() {
        // Las cookies de clearance pertenecen a su dominio. Nunca se copian a
        // otro proveedor ni a un CDN de imágenes aparentemente relacionado.
        CookieManager.getInstance().flush()
    }
}
