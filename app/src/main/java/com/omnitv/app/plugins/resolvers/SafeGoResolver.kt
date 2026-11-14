package com.omnitv.app.plugins.resolvers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup

/**
 * SafeGo numeric captcha resolver — inspirado en Streamflix `e1e18879`:
 * - numeric captcha dialog (cache, mutex, numpad TV support, deletion)
 * - session cookies reuse, captcha only when required
 * Adaptado a TV: D-pad 0-9 + OK + Backspace, sin WebView.
 */
object SafeGoResolver {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String,String>() // url → resolved url
    private var sessionCookies: Map<String,String> = emptyMap()

    data class CaptchaChallenge(val imageUrl: String, val session: String, val digits: Int = 4)

    suspend fun isSafeGo(url: String): Boolean = "safego" in url || "clicka" in url

    suspend fun resolve(url: String, captchaProvider: suspend (CaptchaChallenge) -> String?): String? = mutex.withLock {
        cache[url]?.let { return it }
        // Intento no-captcha primero (ya removido en SafeGo actual, pero fallback)
        val doc = runCatching { Jsoup.connect(url).cookies(sessionCookies).userAgent("Mozilla/5.0").timeout(10_000).get() }.getOrNull() ?: return null
        sessionCookies = doc.connection().response().cookies()
        // Buscar link directo sin captcha
        doc.select("a[href]").firstOrNull { it.attr("href").contains("no-captcha", true) }?.attr("href")?.let { direct ->
            cache[url] = direct; return direct
        }
        // Captcha numérico
        val img = doc.select("img.captcha, img[src*=captcha]").firstOrNull()?.attr("abs:src") ?: return null
        val challenge = CaptchaChallenge(imageUrl = img, session = url)
        val code = captchaProvider(challenge) ?: return null // TV dialog cancel
        // POST code
        val resp = runCatching {
            Jsoup.connect(url).cookies(sessionCookies).data("captcha", code).userAgent("Mozilla/5.0").timeout(10_000).post()
        }.getOrNull() ?: return null
        val resolved = resp.select("a[href]").firstOrNull { it.attr("href").startsWith("http") }?.attr("href")
        if (resolved != null) cache[url] = resolved
        resolved
    }

    fun clearCache() { cache.clear(); sessionCookies = emptyMap() }
}

/**
 * Uprot UPCaptcha — image grid 3x3 multi-round, rate-limit, blockedId filter
 * TV: D-pad navigable grid, selectable tiles, multi-round handling
 */
object UprotResolver {
    private val mutex = Mutex()
    private var cache = mutableMapOf<String,String>()

    data class GridChallenge(val images: List<String>, val prompt: String, val round: Int)

    suspend fun isUprot(url: String): Boolean = "uprot" in url

    suspend fun resolve(url: String, gridProvider: suspend (GridChallenge) -> List<Int>?): String? = mutex.withLock {
        cache[url]?.let { return it }
        // Fetch challenge page
        val doc = runCatching { Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(10_000).get() }.getOrNull() ?: return null
        val prompt = doc.select(".captcha-prompt, h3").text().ifBlank { "Select images" }
        val imgs = doc.select("img.captcha-grid img").map { it.attr("abs:src") }.take(9)
        if (imgs.isEmpty()) return null
        var round = 0
        var currentImgs = imgs
        while (round < 3) {
            val selected = gridProvider(GridChallenge(currentImgs, prompt, round)) ?: return null // cancel
            // POST selection
            val resp = runCatching {
                Jsoup.connect(url).data("selected", selected.joinToString(",")).userAgent("Mozilla/5.0").timeout(10_000).post()
            }.getOrNull() ?: return null
            if (resp.select("a.stream-link").isNotEmpty()) {
                val direct = resp.select("a.stream-link").first()!!.attr("href")
                cache[url] = direct; return direct
            }
            // next round?
            currentImgs = resp.select("img.captcha-grid img").map { it.attr("abs:src") }
            if (currentImgs.isEmpty()) break
            round++
        }
        return null
    }
}

/** WebView popunder/ad blocker list — TV+Mobile alineado con Streamflix. */
object WebViewBlocker {
    val adHosts = setOf(
        "popads.net","popcash.net","adcash.com","propellerads.com","doubleclick.net",
        "googlesyndication.com","adsterra.com","onclickads.net","exosrv.com"
    )
    fun shouldBlock(url: String): Boolean = adHosts.any { url.contains(it, true) }
    fun jsBlockScript(): String = """
        (function(){ 
          window.open = function(){ return null; };
          Notification.requestPermission = function(){ return Promise.resolve('denied'); };
          document.querySelectorAll('[id*="overlay"],[class*="popunder"]').forEach(e=>e.remove());
        })();
    """.trimIndent()
}
