package com.omnitv.app.network

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.omnitv.app.data.NetPolicy
import okio.Buffer
import java.net.URI

/**
 * Fetcher de Coil que usa Cronet solo para los hosts con bloqueo 403. Si no
 * aplica devuelve `null` y Coil continúa con su `HttpUriFetcher` (OkHttp).
 *
 * Se registra en el `ImageLoader` singleton desde `MainActivity`; antes la
 * extensión `cronetSupport` construía un OkHttpClient que nadie usaba y el
 * fetcher quedaba sin instalar. Además se conservan los bytes originales.
 */
class CronetFetcher(
    private val url: String,
    private val options: Options,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (!needsCronet()) return null
        val response = CronetClient.get(url, ArtworkHeaders.forImage(url)) ?: return null
        if (response.statusCode !in 200..299 || response.bodyBytes.isEmpty()) return null
        return SourceResult(
            source = ImageSource(source = Buffer().write(response.bodyBytes), context = context),
            mimeType = null,
            dataSource = DataSource.NETWORK
        )
    }

    private fun needsCronet(): Boolean {
        val host = runCatching { URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
        val blockedHost = host == "animeonline.ninja" || host.endsWith(".animeonline.ninja")
        return blockedHost || options.headers["Use-Cronet"] == "true"
    }

    /** `data` ya viene mapeado por Coil (String/Uri/HttpUrl), no es el ImageRequest. */
    class Factory(private val context: Context) : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            val url = data.toString()
            if (!url.startsWith("http", true) || !NetPolicy.isAllowed(url)) return null
            return CronetFetcher(url, options, context)
        }
    }
}

/** Instala el fetcher antes de los componentes por defecto de Coil. */
fun ImageLoader.Builder.cronetSupport(context: Context): ImageLoader.Builder =
    components { add(CronetFetcher.Factory(context), Any::class.java) }
