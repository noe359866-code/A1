package com.omnitv.app.plugins

import com.omnitv.app.data.Video
import com.omnitv.app.plugins.extractors.*

/**
 * Registro de providers locales (scraping) + bridge a Stremio/Nuvio.
 * Streamflix tiene 38 providers; aquí plantilla extensible.
 */
object ProviderRegistry {
    private val providers = mutableListOf<Provider>()

    fun register(p: Provider) { if (providers.none { it.id == p.id }) providers.add(p) }
    fun all(): List<Provider> = providers.toList()
    fun find(id: String): Provider? = providers.firstOrNull { it.id == id }

    suspend fun searchAll(query: String): List<Video> {
        if (providers.isEmpty()) return emptyList()
        return providers.flatMap { runCatching { it.search(query) }.getOrDefault(emptyList()) }
    }

    init {
        // Ejemplo: provider template (CB01-like Italian) — demo sin dominio real
        register(TemplateProvider("cb01","CB01","it","https://cb01.example"))
        // Añadir más: register(AnimeUnityProvider) etc. cuando se tenga scraping real
    }
}

/** Template provider — muestra estructura Streamflix para portar cualquiera. */
class TemplateProvider(
    override val id: String,
    override val name: String,
    override val lang: String,
    override val baseUrl: String
) : Provider {
    override suspend fun search(query: String): List<Video> = emptyList() // TODO: Jsoup + baseUrl/search?q=
    override suspend fun getCatalog(type: String, genre: String?, page: Int, skip: Int): List<Video> = emptyList()
    override suspend fun getDetail(videoId: String): Video? = null
    override suspend fun getServers(videoId: String, episodeId: String?): List<Server> {
        // return listOf(Server("1","Mixdrop","https://mixdrop.co/e/xxx", needsResolver=false), Server("2","SafeGo","https://safego..."))
        return emptyList()
    }
}

/** Helper: resuelve un Server embed a stream directo vía ExtractorRegistry + Resolvers. */
suspend fun Server.resolveToStream(
    numericCaptcha: suspend (com.omnitv.app.plugins.resolvers.SafeGoResolver.CaptchaChallenge) -> String? = { null },
    gridCaptcha: suspend (com.omnitv.app.plugins.resolvers.UprotResolver.GridChallenge) -> List<Int>? = { null }
): ExtractedStream? {
    // SafeGo/Uprot primero (resolver captcha)
    if (com.omnitv.app.plugins.resolvers.SafeGoResolver.isSafeGo(url)) {
        val resolved = com.omnitv.app.plugins.resolvers.SafeGoResolver.resolve(url, numericCaptcha) ?: return null
        return ExtractorRegistry.resolve(resolved)
    }
    if (com.omnitv.app.plugins.resolvers.UprotResolver.isUprot(url)) {
        val resolved = com.omnitv.app.plugins.resolvers.UprotResolver.resolve(url, gridCaptcha) ?: return null
        return ExtractorRegistry.resolve(resolved)
    }
    // Direct extractor
    return ExtractorRegistry.resolve(url)
}
