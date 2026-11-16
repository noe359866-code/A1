package com.omnitv.app.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.omnitv.app.BuildConfig
import com.omnitv.app.data.NetPolicy
import com.omnitv.app.data.StreamFormat
import com.omnitv.app.data.StreamSource
import java.net.URI

/**
 * Cada servidor tiene sus propias cabeceras. Se crean al cambiar de fuente,
 * no como propiedades globales que puedan viajar al siguiente CDN.
 * Media3 elige el extractor progresivo/HLS/DASH por el MIME explícito.
 *
 * Los subtítulos externos se registran en el `MediaItem`; solo se aceptan del
 * mismo host para no reenviar a otro origen las cabeceras privadas del stream.
 */
@OptIn(UnstableApi::class)
internal fun streamMediaSource(context: Context, source: StreamSource): MediaSource {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent("OmniTV/${BuildConfig.VERSION_NAME} (Android TV)")
        .setDefaultRequestProperties(source.headers)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)
        .setAllowCrossProtocolRedirects(false)
    val item = MediaItem.Builder().setUri(source.url).apply {
        when (source.format) {
            StreamFormat.HLS -> setMimeType(MimeTypes.APPLICATION_M3U8)
            StreamFormat.DASH -> setMimeType(MimeTypes.APPLICATION_MPD)
            StreamFormat.MP4 -> Unit // Uri y Content-Type permiten detectar el contenedor.
        }
        val subtitles = source.subtitles
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && NetPolicy.isAllowed(it) && sameHost(source.url, it) }
            .distinct()
            .take(8)
            .map { url ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                    .setMimeType(subtitleMime(url))
                    .build()
            }
            .toList()
        if (subtitles.isNotEmpty()) setSubtitleConfigurations(subtitles)
    }.build()
    return DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)).createMediaSource(item)
}

private fun subtitleMime(url: String): String {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".ttml") || path.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.TEXT_VTT
    }
}

private fun sameHost(a: String, b: String): Boolean = runCatching {
    URI(a).host?.lowercase() == URI(b).host?.lowercase()
}.getOrDefault(false)
