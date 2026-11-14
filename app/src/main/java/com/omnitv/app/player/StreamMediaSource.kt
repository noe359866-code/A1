package com.omnitv.app.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.omnitv.app.data.StreamFormat
import com.omnitv.app.data.StreamSource

/**
 * Cada servidor tiene sus propias cabeceras. Se crean al cambiar de fuente,
 * no como propiedades globales que puedan viajar al siguiente CDN.
 * Media3 elige el extractor progresivo/HLS/DASH por el MIME explícito.
 */
@OptIn(UnstableApi::class)
internal fun streamMediaSource(context: Context, source: StreamSource): MediaSource {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent("OmniTV/1.2 (Android TV)")
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
    }.build()
    return DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)).createMediaSource(item)
}
