package com.omnitv.app.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/** Mixdrop — `https://mixdrop.co/e/{id}` → eval packed → m3u8/mp4. Streamflix usa `Rpmvid→Turbovio` fallback similar. */
object MixdropExtractor : Extractor {
    override val name = "Mixdrop"
    override val domains = listOf("mixdrop.co","mixdrop.to","mixdrop.ag","mixdroop.bz","mixdrop.ps")

    override suspend fun extract(url: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val doc = runCatching { Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(8000).get() }.getOrNull() ?: return@withContext null
        val html = doc.html()
        // eval(function(p,a,c,k,e,d)...) unpacker
        val packed = Regex("""eval\(function\(p,a,c.*?\.split\('\|'\)\)\)""", RegexOption.DOT_MATCHES_ALL).find(html)?.value
        val unpacked = packed?.let { Unpacker.unpack(it) } ?: html
        val m3u8 = Regex("""https?://[^"']+\.m3u8[^"']*""").find(unpacked)?.value
            ?: Regex("""MDCore\.wurl\s*=\s*"(https?://[^"]+)"""").find(html)?.groupValues?.get(1)
        val mp4 = Regex("""https?://[^"']+\.mp4[^"']*""").find(unpacked)?.value
        val direct = m3u8 ?: mp4 ?: return@withContext null
        ExtractedStream(url = direct, quality = parseQuality(direct), headers = mapOf("Referer" to "https://mixdrop.co/"))
    }
}

object StreamtapeExtractor : Extractor {
    override val name = "Streamtape"
    override val domains = listOf("streamtape.com","streamtape.to","strtape.cloud","shavetape.cash")
    override suspend fun extract(url: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val id = Regex("""/(?:e|v)/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1) ?: return@withContext null
        // Streamtape API-like: https://streamtape.com/get_video?id={id}&expires=&ip=&sig=
        // Simplificado: busca robotlink en html
        val doc = runCatching { Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(8000).get() }.getOrNull() ?: return@withContext null
        val robotlink = doc.select("div#robotlink").text()
            .takeIf { it.isNotBlank() }?.let { "https:" + it.substring(4) } // //...
        val inner = robotlink ?: Regex("""get_video\?id=[^"']+""").find(doc.html())?.value?.let { "https://streamtape.com/$it" } ?: return@withContext null
        // fetch final Location
        val conn = runCatching { org.jsoup.helper.HttpConnection.connect(inner).userAgent("Mozilla/5.0").followRedirects(true).timeout(8000).execute() }.getOrNull()
        val final = conn?.url()?.toString() ?: inner
        ExtractedStream(url = final, quality = "720p")
    }
}

object DoodExtractor : Extractor {
    override val name = "DoodStream"
    override val domains = listOf("dood.watch","doodstream.com","dood.la","dood.pm","dood.ws")
    override suspend fun extract(url: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val doc = runCatching { Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(8000).get() }.getOrNull() ?: return@withContext null
        val script = doc.select("script").joinToString("\n") { it.data() }
        // token pass_md5
        val token = Regex("""fetch\('/pass_md5/([^']+)'""").find(script)?.groupValues?.get(1)
            ?: Regex("""\$.get\('/pass_md5/([^']+)'""").find(script)?.groupValues?.get(1) ?: return@withContext null
        val pass = runCatching { Jsoup.connect("https://dood.watch/pass_md5/$token").ignoreContentType(true).execute().body() }.getOrNull() ?: return@withContext null
        // pass is m3u8 path
        val direct = if (pass.startsWith("http")) pass else "https://dood.watch$pass"
        ExtractedStream(url = direct, quality = "HD")
    }
}

object VoeExtractor : Extractor {
    override val name = "Voe"
    override val domains = listOf("voe.sx","voeunblock.com","voe-unblock.com")
    override suspend fun extract(url: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val doc = runCatching { Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(8000).get() }.getOrNull() ?: return@withContext null
        val html = doc.html()
        val m3u8 = Regex("""hls":"(https?://[^"]+\.m3u8[^"]*)"""").find(html)?.groupValues?.get(1)
            ?.replace("\\u0026","&")?.replace("\\/","/") ?: Regex("""source[^}]*"(https?://[^"]+\.m3u8[^"]*)"""").find(html)?.groupValues?.get(1) ?: return@withContext null
        ExtractedStream(url = m3u8, quality = parseQuality(m3u8))
    }
}

object TurbovioExtractor : Extractor {
    override val name = "Turbovio"
    override val domains = listOf("turbovio.com","turbovio.","turbovid")
    override suspend fun extract(url: String): ExtractedStream? = withContext(Dispatchers.IO) {
        // Nuevo en Streamflix e1e18879 — delega a API pública resolve
        val api = "https://turbovio.com/api/resolve?url=${java.net.URLEncoder.encode(url,"UTF-8")}"
        val json = runCatching { Jsoup.connect(api).ignoreContentType(true).timeout(8000).execute().body() }.getOrNull() ?: return@withContext null
        val m3u8 = Regex(""""url"\s*:\s*"(https?://[^"]+\.m3u8[^"]*)"""").find(json)?.groupValues?.get(1)?.replace("\\/","/") ?: return@withContext null
        ExtractedStream(url = m3u8, quality = "1080p")
    }
}

object RpmvidExtractor : Extractor {
    override val name = "Rpmvid"
    override val domains = listOf("rpmvid.com","rmpvid","rpsky")
    override suspend fun extract(url: String): ExtractedStream? {
        // Usa Turbovio API con fallback legacy
        return TurbovioExtractor.extract(url) ?: withContext(Dispatchers.IO) {
            val doc = runCatching { Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(8000).get() }.getOrNull() ?: return@withContext null
            val m3u8 = Regex("""file:\s*"(https?://[^"]+\.m3u8[^"]*)"""").find(doc.html())?.groupValues?.get(1) ?: return@withContext null
            ExtractedStream(url = m3u8, quality = "720p")
        }
    }
}

// Minimal JS unpacker stub (eval p.a.c.k.e.r) — suficiente para mixdrop
private object Unpacker {
    fun unpack(packed: String): String = try {
        // Simplificado: extrae array |...| y reemplaza índices
        val dictMatch = Regex("""\}\('([^']*)',\d+,\d+,'([^']*)'\.split""").find(packed) ?: return packed
        val payload = dictMatch.groupValues[1]; val dict = dictMatch.groupValues[2].split("|")
        var out = payload
        dict.forEachIndexed { i, w -> if (w.isNotEmpty()) out = out.replace("\\b$i\\b".toRegex(), w) }
        out
    } catch (_: Exception) { packed }
}
