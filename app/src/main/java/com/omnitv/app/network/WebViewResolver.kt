package com.omnitv.app.network

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * WebView fallback para Cloudflare Turnstile / cf_clearance
 * Cuando Cronet y OkHttp dan 403/challenge, se carga la URL en WebView oculto (TV: 1x1),
 * se espera a que hasUsableSiteContent==true y se extrae html + cookies.
 * Igual que Streamflix AnimeOnlineNinjaProvider.getDocument() con WebViewResolver.
 */
class WebViewResolver(private val context: Context) {

    data class Result(val finalUrl: String, val html: String, val cookies: String)

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun getResult(
        url: String,
        headers: Map<String, String> = ArtworkHeaders.forPage(url),
        timeoutMs: Long = 15000,
        completion: (String, String, String) -> Boolean = { _, html, _ -> ArtworkHeaders.hasUsableContent(html, url) }
    ): Result? = suspendCancellableCoroutine { cont ->
        var finished = false
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = ArtworkHeaders.CHROME_UA
        }

        val start = System.currentTimeMillis()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlRequest(view: WebView, request: WebResourceRequest): Boolean = false

            override fun onPageFinished(view: WebView, urlFinished: String) {
                view.evaluateJavascript("(function(){return document.documentElement.outerHTML})()") { htmlRaw ->
                    val html = htmlRaw?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""
                    val cookies = CookieManager.getInstance().getCookie(urlFinished) ?: ""
                    val challenge = ArtworkHeaders.requiresClearance(html) || urlFinished.contains("/cdn-cgi/", true)
                    val usable = completion(urlFinished, html, cookies) || ArtworkHeaders.hasUsableContent(html, urlFinished)

                    if (!challenge && usable && !finished) {
                        finished = true
                        CronetClient.promoteClearanceCookies(urlFinished)
                        cont.resume(Result(urlFinished, html, cookies))
                    } else if (System.currentTimeMillis() - start > timeoutMs && !finished) {
                        finished = true
                        cont.resume(null)
                    }
                    // si sigue challenge, deja que WebView siga reintentando (Turnstile auto)
                }
            }
        }

        // Poll por si el challenge se resuelve sin onPageFinished extra
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!finished) {
                webView.evaluateJavascript("document.documentElement.outerHTML") { htmlRaw ->
                    val html = htmlRaw?.take(2000) ?: ""
                    if (html.length > 100 && !finished) {
                        finished = true
                        cont.resume(null)
                    }
                }
            }
        }, timeoutMs)

        cont.invokeOnCancellation { webView.destroy() }

        headers["Referer"]?.let { webView.loadUrl(url, mapOf("Referer" to it)) } ?: webView.loadUrl(url)
    }
}
