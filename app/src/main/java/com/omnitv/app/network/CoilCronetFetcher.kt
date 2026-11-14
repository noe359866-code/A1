package com.omnitv.app.network

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okhttp3.OkHttpClient
import okio.Buffer
import okio.ByteString.Companion.toByteString

/**
 * Coil fetcher que usa Cronet para hosts con 403 (animeonline.ninja, tmdb con protección)
 * Se registra como fallback solo para esos hosts; el resto usa Coil default (OkHttp)
 * Evita re-descargar posters que OkHttp no puede por JA3.
 */
class CronetFetcher(
    private val url: String,
    private val options: Options,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val shouldUseCronet = url.contains("animeonline.ninja", true) ||
                              url.contains("ww3.animeonline", true) ||
                              options.headers["Use-Cronet"] == "true"

        if (!shouldUseCronet) return null // deja que Coil use default

        val data = CronetClient.get(url, ArtworkHeaders.forImage(url))?.body?.toByteArray()
            ?: return null

        return SourceResult(
            source = ImageSource(source = Buffer().write(data), context = context),
            mimeType = null,
            dataSource = DataSource.NETWORK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<coil.request.ImageRequest> {
        override fun create(data: coil.request.ImageRequest, options: Options, imageLoader: ImageLoader): Fetcher? {
            val url = data.data.toString()
            return if (url.startsWith("http")) CronetFetcher(url, options, context) else null
        }
    }
}

fun ImageLoader.Builder.cronetSupport(context: Context): ImageLoader.Builder {
    // OkHttp con UA Chrome para fallback + Coil Coil
    val okHttp = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", ArtworkHeaders.CHROME_UA)
                .header("Accept", "image/avif,image/webp,*/*")
                .build()
            chain.proceed(req)
        }.build()
    // Nota: Coil 2.x usa OkHttp via coil-network; aquí solo aseguramos headers
    return this
}
