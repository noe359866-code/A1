package com.omnitv.app.plugins

import com.omnitv.app.data.AddonHttp
import com.omnitv.app.data.NetPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Extractores de embeds a enlaces directos. Todos usan [AddonHttp] para que
 * las redirecciones, el límite de cuerpo y [NetPolicy] sean los mismos que en
 * el resto de la app; ya no hay `Jsoup.connect` propio ni descargas sin tope.
 */
private val BROWSER_HEADERS = mapOf(
    "User-Agent" to "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36",
    "Accept" to "text/html,application/xhtml+xml,*/*;q=0.8"
)

private val M3U8_REGEX = Regex("""https?://[^"'\s]+\.m3u8[^"'\s]*""")
private val MP4_REGEX = Regex("""https?://[^"'\s]+\.mp4[^"'\s]*""")

/** Mixdrop — `https://mixdrop.co/e/{id}` → eval packed → m3u8/mp4. */
object MixdropExtractor : Extractor {
    override val name = "Mixdrop"
    override val domains = listOf("mixdrop.co", "mixdrop.to", "mixdrop.ag", "mixdroop.bz", "mixdrop.ps")

    override suspend fun extract(url: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val html = AddonHttp.getText(url, BROWSER_HEADERS) ?: return@withContext null
        val packed = Regex("""eval\(function\(p,a,c.*?\.split\('\|'\)\)""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.value
        val unpacked = packed?.let { Unpacker.unpack(it) } ?: html
        val direct = sequenceOf(
            M3U8_REGEX.find(unpacked)?.value,
            Regex("""MDCore\.wurl\s*=\s*["'](https?://[^"']+)["']""").find(html)?.groupValues?.get(1),
            MP4_REGEX.find(unpacked)?.value
        ).firstOrNull { !it.isNullOrBlank() } ?: return@withContext null
        if (!NetPolicy.isAllowed(direct)) return@withContext null
        ExtractedStream(
            url = direct,
            quality = parseQuality(direct),
            headers = mapOf("Referer" to "https://mixdrop.co/")
        )
    }
}

object StreamtapeExtractor : Extractor {
    override val name = "Streamtape"
    override val domains = listOf("streamtape.com", "streamtape.to", "strtape.cloud", "shavetape.cash")

    override suspend fun extract(url: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val html = AddonHttp.getText(url, BROWSER_HEADERS) ?: return@withContext null
        val inner = Regex("""(?:https?:)?//[^"'\s]+get_video\?[^"'\s]+""").find(html)?.value?.let {
            if (it.startsWith("//")) "https:$it" else it
        } ?: Regex("""get_video\?id=[^"'\s]+""").find(html)?.value?.let { "https://streamtape.com/$it" }
            ?: return@withContext null
        // AddonHttp sigue las redirecciones validadas y devuelve la URL final.
        val result = AddonHttp.getTextWithMeta(inner, BROWSER_HEADERS)
        val final = result.finalUrl.takeIf { it.isNotBlank() } ?: inner
        if (!NetPolicy.isAllowed(final)) return@withContext null
        ExtractedStream(url = final, quality = "720p")
    }
}

object DoodExtractor : Extractor {
    override val name = "DoodStream"
    override val domains = listOf("dood.watch", "doodstream.com", "dood.la", "dood.pm", "dood.ws")

    override suspend fun extract(url: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val html = AddonHttp.getText(url, BROWSER_HEADERS) ?: return@withContext null
        val token = Regex("""fetch\('/pass_md5/([^']+)'""").find(html)?.groupValues?.get(1)
            ?: Regex("""\$\.get\('/pass_md5/([^']+)'""").find(html)?.groupValues?.get(1)
            ?: return@withContext null
        // El token va en el path: solo caracteres seguros, sin `../` ni query.
        if (!token.matches(Regex("[A-Za-z0-9_-]+"))) return@withContext null
        val pass = AddonHttp.getText("https://dood.watch/pass_md5/$token")?.trim() ?: return@withContext null
        val direct = if (pass.startsWith("http", true)) pass else "https://dood.watch$pass"
        if (!NetPolicy.isAllowed(direct)) return@withContext null
        ExtractedStream(url = direct, headers = mapOf("Referer" to "https://dood.watch/"))
    }
}

object VoeExtractor : Extractor {
    override val name = "Voe"
    override val domains = listOf("voe.sx", "voeunblock.com", "voe-unblock.com")

    override suspend fun extract(url: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val html = AddonHttp.getText(url, BROWSER_HEADERS)
            ?.replace("\\u0026", "&")
            ?.replace("\\/", "/")
            ?: return@withContext null
        val m3u8 = M3U8_REGEX.find(html)?.value ?: return@withContext null
        if (!NetPolicy.isAllowed(m3u8)) return@withContext null
        ExtractedStream(url = m3u8, quality = parseQuality(m3u8))
    }
}

object TurbovioExtractor : Extractor {
    override val name = "Turbovio"
    override val domains = listOf("turbovio.com", "turbovid")

    override suspend fun extract(url: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val api = "https://turbovio.com/api/resolve?url=${URLEncoder.encode(url, "UTF-8")}"
        val json = AddonHttp.getText(api)?.replace("\\/", "/") ?: return@withContext null
        val m3u8 = M3U8_REGEX.find(json)?.value ?: return@withContext null
        if (!NetPolicy.isAllowed(m3u8)) return@withContext null
        ExtractedStream(url = m3u8, quality = "1080p")
    }
}

object RpmvidExtractor : Extractor {
    override val name = "Rpmvid"
    override val domains = listOf("rpmvid.com", "rmpvid", "rpsky")

    override suspend fun extract(url: String): ExtractedStream? =
        TurbovioExtractor.extract(url) ?: withContext(Dispatchers.IO) {
            val html = AddonHttp.getText(url, BROWSER_HEADERS) ?: return@withContext null
            val m3u8 = Regex("""file:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""")
                .find(html)?.groupValues?.get(1) ?: return@withContext null
            if (!NetPolicy.isAllowed(m3u8)) return@withContext null
            ExtractedStream(url = m3u8, quality = "720p")
        }
}

// Minimal JS unpacker stub (eval p.a.c.k.e.r) — suficiente para mixdrop.
private object Unpacker {
    fun unpack(packed: String): String = try {
        val dictMatch = Regex("""\}\('([^']*)',\d+,\d+,'([^']*)'\.split""").find(packed) ?: return packed
        val payload = dictMatch.groupValues[1]
        val dict = dictMatch.groupValues[2].split("|")
        var out = payload
        dict.forEachIndexed { i, w -> if (w.isNotEmpty()) out = out.replace("\\b$i\\b".toRegex(), w) }
        out
    } catch (_: Exception) {
        packed
    }
}
