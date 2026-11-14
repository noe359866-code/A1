package com.omnitv.app.network

import android.content.Context
import android.webkit.CookieManager
import com.omnitv.app.data.NetPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Cronet para los hosts de imágenes que bloquean el JA3 de OkHttp
 * (animeonline.ninja y similares). Devuelve **bytes**, no un `String` UTF-8:
 * un JPEG re-codificado como texto llega corrupto al decodificador de Coil.
 *
 * Seguridad: solo URLs permitidas por [NetPolicy], redirecciones al mismo
 * host, cuerpo acotado, executor con límite de hilos y cancelación propagada.
 */
object CronetClient {

    private const val MAX_BODY_BYTES = 8 * 1024 * 1024

    @Volatile
    private var engine: CronetEngine? = null

    // Antes era newCachedThreadPool(): sin tope, podía crear un hilo por póster.
    private val executor = Executors.newFixedThreadPool(4)

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
        val bodyBytes: ByteArray,
        val headers: Map<String, List<String>>,
        val finalUrl: String
    ) {
        /** Vista de texto para páginas HTML; las imágenes usan [bodyBytes]. */
        val body: String get() = bodyBytes.toString(Charsets.UTF_8)
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = ArtworkHeaders.forPage(url),
        useCache: Boolean = true
    ): CronetResponse? = withContext(Dispatchers.IO) {
        val eng = engine ?: return@withContext null
        if (!NetPolicy.isAllowed(url)) return@withContext null
        val cookieHeader = CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
        val allHeaders = headers.toMutableMap().apply {
            if (cookieHeader != null) put("Cookie", cookieHeader)
        }
        suspendCancellableCoroutine<CronetResponse?> { cont ->
            val done = AtomicBoolean(false)
            fun finish(result: CronetResponse?) {
                if (done.compareAndSet(false, true) && cont.isActive) cont.resume(result)
            }

            val callback = object : UrlRequest.Callback() {
                val buffer = ByteBuffer.allocateDirect(32 * 1024)
                val chunks = mutableListOf<ByteArray>()
                var total = 0
                var status = 0
                var respHeaders: Map<String, List<String>> = emptyMap()

                override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                    // Cronet reenvía las cabeceras al seguir el redirect: no
                    // se permite salir del host ni caer en HTTP público.
                    val sameHost = runCatching {
                        URI(url).host?.equals(URI(newLocationUrl).host, true) == true
                    }.getOrDefault(false)
                    if (!NetPolicy.isAllowed(newLocationUrl) || !sameHost) {
                        request.cancel()
                        finish(null)
                        return
                    }
                    request.followRedirect()
                }

                override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                    status = info.httpStatusCode
                    respHeaders = info.allHeaders
                    info.allHeaders["set-cookie"]?.forEach { CookieManager.getInstance().setCookie(info.url, it) }
                    request.read(buffer)
                }

                override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                    byteBuffer.flip()
                    val bytes = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(bytes)
                    total += bytes.size
                    if (total > MAX_BODY_BYTES) {
                        request.cancel()
                        finish(null)
                        return
                    }
                    chunks.add(bytes)
                    byteBuffer.clear()
                    request.read(buffer)
                }

                override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                    val out = ByteArrayOutputStream()
                    chunks.forEach { out.write(it, 0, it.size) }
                    finish(CronetResponse(status, out.toByteArray(), respHeaders, info.url))
                }

                override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
                    finish(null)
                }
            }

            val request = eng.newUrlRequestBuilder(url, callback, executor).apply {
                setHttpMethod("GET")
                allHeaders.forEach { (k, v) -> addHeader(k, v) }
                if (!useCache) addHeader("Cache-Control", "no-cache")
            }.build()
            cont.invokeOnCancellation { runCatching { request.cancel() } }
            request.start()
        }
    }
}
