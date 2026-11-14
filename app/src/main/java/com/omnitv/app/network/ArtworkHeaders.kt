package com.omnitv.app.network

/**
 * Cabeceras tipo navegador para los hosts de imágenes que dan 403 con el
 * fingerprint de OkHttp. Se mantienen solo las que consume [CronetClient] a
 * través de [CronetFetcher]; el resolver WebView y sus heurísticas de
 * challenge se eliminaron (no estaban cableados y habilitaban JS en la TV).
 */
object ArtworkHeaders {

    const val CHROME_UA =
        "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.115 Safari/537.36"

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

    private fun refererFor(url: String): String = when {
        "tmdb.org" in url || "themoviedb" in url -> "https://www.themoviedb.org/"
        "animeonline.ninja" in url -> "https://ww3.animeonline.ninja/"
        "nuvio" in url || "cinejoy" in url -> "https://nuvio.tv/"
        else -> "https://www.google.com/"
    }
}
