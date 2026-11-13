package com.omnitv.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.omnitv.app.R
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.CircleIconButton
import com.omnitv.app.ui.components.LoadingSpinner
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.SeekBar
import com.omnitv.app.ui.components.formatTime
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.tv.material.Text
import kotlinx.coroutines.delay

/**
 * Reproductor a pantalla completa con Media3 ExoPlayer.
 *
 * Controles con el mando (D-pad):
 *  - Centro/Enter: mostrar controles · reproducir/pausar
 *  - Izquierda / Derecha: retroceder / avanzar 10 s
 *  - Arriba / Abajo: mostrar controles
 *  - Atrás: salir
 */
@Composable
fun PlayerScreen(video: Video, onBack: () -> Unit) {
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            setMediaItem(ExoMediaItem.Builder().setUri(video.videoUrl).build())
            playWhenReady = true
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedMs by remember { mutableStateOf(0L) }

    val focusRequester = remember { FocusRequester() }

    // Estado del reproductor → UI
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY && player.duration > 0) {
                    durationMs = player.duration
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                hasError = error.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Sondeo de posición (basta con 2 Hz para la barra de progreso)
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            bufferedMs = player.bufferedPosition.coerceAtLeast(0)
            if (player.duration > 0) durationMs = player.duration
            delay(500)
        }
    }

    // Ocultar controles a los 5 s mientras se reproduce
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(5000)
            controlsVisible = false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler(onBack = onBack)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        controlsVisible = true
                        if (isPlaying) player.pause() else player.play()
                        true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        controlsVisible = true
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        controlsVisible = true
                        player.seekTo(player.currentPosition + 10_000)
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        controlsVisible = true
                        true
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && hasError == null) {
            LoadingSpinner(Modifier.align(Alignment.Center))
        }

        hasError?.let { code ->
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No se pudo reproducir el vídeo", style = AppType.sectionTitle, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(code, style = AppType.metadata, color = TextSecondary)
                Spacer(Modifier.height(24.dp))
                PillButton(text = "Volver", iconRes = R.drawable.ic_back, primary = true, onClick = onBack)
            }
        }

        if (controlsVisible && hasError == null) {
            ControlsOverlay(
                video = video,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                onPlayPause = { if (isPlaying) player.pause() else player.play() },
                onBack = onBack
            )
        }
    }
}

@Composable
private fun ControlsOverlay(
    video: Video,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onPlayPause: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // Barra superior: volver + título
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Transparent
                    )
                )
                .padding(horizontal = 44.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                iconRes = R.drawable.ic_back,
                contentDescription = "Volver",
                size = 48.dp,
                onClick = onBack
            )
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    video.title,
                    style = AppType.sectionTitle,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${video.quality} · ${video.genres.joinToString(" · ")}",
                    style = AppType.metadata,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Barra inferior: progreso + controles
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f)
                    )
                )
                .padding(horizontal = 44.dp, vertical = 30.dp)
        ) {
            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatTime(positionMs), style = AppType.playerTime, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Text(
                    if (durationMs > 0) "-${formatTime(durationMs - positionMs)}" else "--:--",
                    style = AppType.playerTime,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(
                    iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    size = 60.dp,
                    background = Color.White.copy(alpha = 0.92f),
                    iconTint = Background,
                    onClick = onPlayPause
                )
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(video.title, style = AppType.cardTitle, color = TextPrimary)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Centro: reproducir/pausar · ◀▶: ±10 s",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(video.streamFormat.name, style = AppType.metadata, color = TextSecondary)
            }
        }
    }
}
