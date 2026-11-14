package com.omnitv.app.network

/**
 * Port de Streamflix ArtworkRequestHeaders.kt + ArtworkLoader fix
 * Provee headers tipo navegador para imágenes y páginas que dan 403 con OkHttp
 * Chrome 120 JA3 + Referer correcto evita bloqueos de Cloudflare / Hotlink protection
 */
object ArtworkHeaders {

    const val CHROME_UA = "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.115 Safari/537.36"

    fun forImage(url: String, referer: String? = null): Map<String, String> = buildMap {
        put("User-Agent", CHROME_UA)
        put("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        put("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
        put("Referer", referer ?: refererFor(url))
        put("Sec-Fetch-Mode", "no-cors")
        put("Sec-Fetch-Site", if (referer != null) "same-site" else "cross-site")
    }

    fun forPage(url: String, baseUrl: String? = null): Map<String, String> = buildMap {
        put("User-Agent", CHROME_UA)
        put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        put("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
        put("Referer", baseUrl ?: refererFor(url))
        put("Cache-Control", "no-cache")
        put("Pragma", "no-cache")
    }

    fun forApi(url: String): Map<String, String> = mapOf(
        "User-Agent" to CHROME_UA,
        "Accept" to "application/json, text/plain, */*",
        "Referer" to refererFor(url)
    )

    private fun refererFor(url: String): String = when {
        "tmdb.org" in url || "themoviedb" in url -> "https://www.themoviedb.org/"
        "animeonline.ninja" in url -> "https://ww3.animeonline.ninja/"
        "image.tmdb.org" in url -> "https://www.themoviedb.org/"
        "nuvio" in url || "cinejoy" in url -> "https://nuvio.tv/"
        else -> "https://www.google.com/"
    }

    /** Detecta página de challenge/403 para decidir retry con Cronet/WebView */
    fun requiresClearance(html: String): Boolean =
        html.contains("cf-challenge-run", true) || html.contains("cf-turnstile", true) ||
        html.contains("Attention Required", true) || html.contains("Just a moment", true) ||
        html.contains("DDoS protection by Cloudflare", true)

    fun hasUsableContent(html: String, url: String): Boolean =
        html.length > 500 && !requiresClearance(html) && (
            html.contains("tmdb", true) || html.contains("<img", true) || html.contains("<video", true) || url.contains("tmdb.org")
        )
}
