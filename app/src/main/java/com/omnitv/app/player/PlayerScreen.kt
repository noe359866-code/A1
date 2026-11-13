package com.omnitv.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.material.Text
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.data.StreamFormat
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.LoadingSpinner
import com.omnitv.app.ui.components.MetaLine
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.SeekBar
import com.omnitv.app.ui.components.formatTime
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/* -------------------------------------------------------------------------- */
/*  Estados del reproductor                                                   */
/* -------------------------------------------------------------------------- */

private enum class MenuKind { NONE, SERVER, QUALITY, AUDIO, SUBTITLES, ASPECT, SPEED, SLEEP }

private enum class AspectMode(val label: String, val ratio: Float, val resizeMode: Int) {
    FIT("Ajustar", 0f, AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Llenar", 0f, AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", 0f, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    R16_9("16:9", 16f / 9f, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    R4_3("4:3", 4f / 3f, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    R21_9("21:9", 21f / 9f, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

private data class TrackChoice(val group: TrackGroup, val index: Int, val format: Format)

private fun nowClock(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

/**
 * Reproductor estilo Xuper TV sobre Media3 ExoPlayer.
 *
 * - **Autoplay**: los vídeos empiezan solos (ajustable en Ajustes).
 * - **Auto-next**: al terminar aparece «A continuación» con cuenta atrás
 *   de 10 s y botones *Reproducir ahora* / *Cancelar*.
 * - Franja EPG «AHORA / A CONTINUACIÓN», multi-servidor con conmutación
 *   automática, calidad/audio/subtítulos/velocidad, zoom y temporizador.
 * - Controles de vidrio para mando de TV y atajos ◀▶ (±10 s).
 */
@Composable
fun PlayerScreen(
    video: Video,
    onBack: () -> Unit,
    onPlayNext: (Video) -> Unit = {}
) {
    val context = LocalContext.current
    val sources = video.playbackSources
    val nextVideo = remember(video.id) { CatalogRepository.related(video.id).firstOrNull() }

    val trackSelector = remember { DefaultTrackSelector(context) }
    val player = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                setMediaItem(ExoMediaItem.Builder().setUri(sources[0].url).build())
                playWhenReady = SettingsStore.autoplay
                if (SettingsStore.mutedStart) volume = 0f
                setPlaybackSpeed(SettingsStore.playbackSpeed)
                prepare()
            }
    }

    // ---------------- Estado de la UI ----------------
    var isPlaying by remember { mutableStateOf(SettingsStore.autoplay) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedMs by remember { mutableStateOf(0L) }
    var clock by remember { mutableStateOf(nowClock()) }

    var serverIndex by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(MenuKind.NONE) }
    var showInfo by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    var tracks by remember { mutableStateOf(player.currentTracks) }
    var videoChoice by remember { mutableStateOf<TrackChoice?>(null) }
    var audioChoice by remember { mutableStateOf<TrackChoice?>(null) }
    var textChoice by remember { mutableStateOf<TrackChoice?>(null) }
    var subtitlesOn by remember { mutableStateOf(false) }
    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }
    var speed by remember { mutableStateOf(SettingsStore.playbackSpeed) }
    var preferenceApplied by remember { mutableStateOf(false) }

    // Auto-next: panel «A continuación» + cuenta atrás
    var showNextPanel by remember { mutableStateOf<Video?>(null) }
    var nextCountdown by remember { mutableStateOf(0) }

    // Temporizador de apagado
    var sleepMinutes by remember { mutableStateOf(0) }
    var sleepDeadline by remember { mutableStateOf<Long?>(null) }
    var sleepLabel by remember { mutableStateOf("Temporizador") }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    // ---------------- Acciones ----------------

    fun applyTrack(type: Int, choice: TrackChoice?, disable: Boolean = false) {
        val builder = trackSelector.buildUponParameters()
        builder.clearOverridesOfType(type)
        if (type == C.TRACK_TYPE_TEXT) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disable)
        }
        if (choice != null) {
            builder.setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
        }
        trackSelector.setParameters(builder.build())
    }

    val switchServer: (Int) -> Unit = { index ->
        val position = player.currentPosition
        serverIndex = index
        player.setMediaItem(ExoMediaItem.Builder().setUri(sources[index].url).build(), position)
        player.prepare()
        player.playWhenReady = true
        hasError = null
        toast = "Reproduciendo desde ${sources[index].label}"
    }

    // ---------------- Ciclo de vida del reproductor ----------------

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
                if (playbackState == Player.STATE_ENDED) {
                    if (nextVideo != null && showNextPanel == null && hasError == null) {
                        // Auto-next: muestra el panel «A continuación»
                        showNextPanel = nextVideo
                    } else if (nextVideo == null) {
                        toast = "Fin de la reproducción"
                    }
                }
            }

            override fun onTracksChanged(currentTracks: Tracks) {
                tracks = currentTracks
                // Aplica la calidad preferida y los subtítulos por defecto
                if (!preferenceApplied && currentTracks.groups.isNotEmpty()) {
                    preferenceApplied = true
                    val target = when (SettingsStore.preferredQuality) {
                        "4K" -> 2160
                        "1080p" -> 1080
                        "720p" -> 720
                        "480p" -> 480
                        else -> 0
                    }
                    if (target > 0) {
                        var best: TrackChoice? = null
                        var bestDiff = Int.MAX_VALUE
                        for (group in currentTracks.groups) {
                            if (group.type != C.TRACK_TYPE_VIDEO) continue
                            for (i in 0 until group.length) {
                                if (!group.isTrackSupported(i)) continue
                                val height = group.getTrackFormat(i).height
                                if (height <= 0) continue
                                val diff = abs(height - target)
                                if (diff < bestDiff) {
                                    bestDiff = diff
                                    best = TrackChoice(group.mediaTrackGroup, i, group.getTrackFormat(i))
                                }
                            }
                        }
                        best?.let {
                            videoChoice = it
                            applyTrack(C.TRACK_TYPE_VIDEO, it)
                        }
                    }
                    if (SettingsStore.subtitlesDefault) {
                        run {
                            for (group in currentTracks.groups) {
                                if (group.type != C.TRACK_TYPE_TEXT) continue
                                for (i in 0 until group.length) {
                                    if (!group.isTrackSupported(i)) continue
                                    subtitlesOn = true
                                    textChoice = TrackChoice(group.mediaTrackGroup, i, group.getTrackFormat(i))
                                    applyTrack(C.TRACK_TYPE_TEXT, textChoice, disable = false)
                                    return@run
                                }
                            }
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Multi-servidor: si falla, se conmuta automáticamente al siguiente
                val next = serverIndex + 1
                if (next < sources.size) {
                    toast = "Error · cambiando a ${sources[next].label}…"
                    switchServer(next)
                } else {
                    hasError = error.errorCodeName
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // Guarda el progreso en el perfil actual (para "Continuar viendo")
            val watched = if (player.duration > 0) {
                player.currentPosition.toFloat() / player.duration
            } else {
                0f
            }
            if (watched > 0f && hasError == null) {
                ProfileStore.setProgress(video.id, watched)
            }
            player.release()
        }
    }

    // Sondeo de posición + reloj
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            bufferedMs = player.bufferedPosition.coerceAtLeast(0)
            if (player.duration > 0) durationMs = player.duration
            clock = nowClock()
            delay(500)
        }
    }

    // Ocultar controles a los 7 s reproduciendo (sin menús ni panel siguiente)
    LaunchedEffect(controlsVisible, isPlaying, menu, showNextPanel) {
        if (controlsVisible && isPlaying && menu == MenuKind.NONE && showNextPanel == null) {
            delay(7000)
            controlsVisible = false
            showInfo = false
        }
    }

    // Aviso temporal (cambios de servidor, velocidad, temporizador…)
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2800)
            toast = null
        }
    }

    // Cuenta atrás del auto-next (10 s)
    LaunchedEffect(showNextPanel) {
        val next = showNextPanel
        if (next != null && SettingsStore.autoNext) {
            nextCountdown = 10
            while (nextCountdown > 0) {
                delay(1000)
                nextCountdown -= 1
            }
            if (nextCountdown == 0) onPlayNext(next)
        } else {
            nextCountdown = 0
        }
    }

    // Temporizador de apagado
    LaunchedEffect(sleepDeadline) {
        val deadline = sleepDeadline
        if (deadline == null) {
            sleepLabel = "Temporizador"
            return@LaunchedEffect
        }
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(0) / 1000
            sleepLabel = String.format(Locale.US, "⏱ %d:%02d", remaining / 60, remaining % 60)
            delay(1000)
        }
        player.pause()
        toast = "Temporizador finalizado · vídeo en pausa"
        sleepMinutes = 0
        sleepDeadline = null
    }

    // Gestión de foco: al mostrar controles → botón de play; al ocultar → raíz
    LaunchedEffect(controlsVisible, menu) {
        when {
            !controlsVisible -> rootFocus.requestFocus()
            menu == MenuKind.NONE -> playFocus.requestFocus()
        }
    }

    BackHandler {
        when {
            showNextPanel != null -> showNextPanel = null
            menu != MenuKind.NONE -> menu = MenuKind.NONE
            controlsVisible -> {
                controlsVisible = false
                showInfo = false
            }
            else -> onBack()
        }
    }

    // ---------------- Opciones de pistas y ajustes ----------------

    val qualityOptions = remember(tracks, videoChoice) {
        buildList {
            add(PlayerOption("Automática", videoChoice == null) {
                videoChoice = null
                applyTrack(C.TRACK_TYPE_VIDEO, null)
                menu = MenuKind.NONE
            })
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_VIDEO) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    val label = buildString {
                        append(if (format.height > 0) "${format.height}p" else (format.label ?: "Pista ${i + 1}"))
                        if (format.bitrate > 0) append(" · ${format.bitrate / 1000} kbps")
                    }
                    add(PlayerOption(label, videoChoice == TrackChoice(group.mediaTrackGroup, i, format)) {
                        videoChoice = TrackChoice(group.mediaTrackGroup, i, format)
                        applyTrack(C.TRACK_TYPE_VIDEO, videoChoice)
                        menu = MenuKind.NONE
                    })
                }
            }
        }
    }

    val audioOptions = remember(tracks, audioChoice) {
        buildList {
            add(PlayerOption("Automático", audioChoice == null) {
                audioChoice = null
                applyTrack(C.TRACK_TYPE_AUDIO, null)
                menu = MenuKind.NONE
            })
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    val label = format.label ?: format.language ?: "Pista ${i + 1}"
                    add(PlayerOption(label, audioChoice == TrackChoice(group.mediaTrackGroup, i, format)) {
                        audioChoice = TrackChoice(group.mediaTrackGroup, i, format)
                        applyTrack(C.TRACK_TYPE_AUDIO, audioChoice)
                        menu = MenuKind.NONE
                    })
                }
            }
        }
    }

    val subtitleOptions = remember(tracks, textChoice, subtitlesOn) {
        buildList {
            add(PlayerOption("Desactivados", !subtitlesOn) {
                subtitlesOn = false
                textChoice = null
                applyTrack(C.TRACK_TYPE_TEXT, null, disable = true)
                menu = MenuKind.NONE
            })
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_TEXT) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    val label = format.label ?: format.language ?: "Subtítulos ${i + 1}"
                    add(PlayerOption(label, subtitlesOn && textChoice == TrackChoice(group.mediaTrackGroup, i, format)) {
                        subtitlesOn = true
                        textChoice = TrackChoice(group.mediaTrackGroup, i, format)
                        applyTrack(C.TRACK_TYPE_TEXT, textChoice, disable = false)
                        menu = MenuKind.NONE
                    })
                }
            }
        }
    }

    val serverOptions = sources.mapIndexed { index, source ->
        PlayerOption(
            label = "${source.label} · ${if (source.format == StreamFormat.HLS) "HLS" else "MP4"}",
            selected = index == serverIndex
        ) {
            menu = MenuKind.NONE
            if (index != serverIndex) switchServer(index)
        }
    }

    val aspectOptions = AspectMode.entries.map { mode ->
        PlayerOption(mode.label, aspectMode == mode) {
            aspectMode = mode
            menu = MenuKind.NONE
        }
    }

    val speedOptions = SettingsStore.SPEED_PRESETS.map { preset ->
        PlayerOption(SettingsStore.speedLabel(preset), speed == preset) {
            speed = preset
            player.setPlaybackSpeed(preset)
            SettingsStore.savePlaybackSpeed(preset)
            menu = MenuKind.NONE
            toast = "Velocidad ${SettingsStore.speedLabel(preset)}"
        }
    }

    val sleepOptions = listOf(0, 15, 30, 60).map { minutes ->
        PlayerOption(
            label = if (minutes == 0) "Desactivado" else "$minutes min",
            selected = sleepMinutes == minutes
        ) {
            sleepMinutes = minutes
            sleepDeadline = if (minutes == 0) null else System.currentTimeMillis() + minutes * 60_000L
            menu = MenuKind.NONE
            toast = if (minutes == 0) "Temporizador desactivado" else "Temporizador: $minutes min"
        }
    }

    val qualityLabel = videoChoice?.let { choice ->
        val height = choice.format.height
        if (height > 0) "${height}p" else "Manual"
    } ?: "Auto"

    // ---------------- Interfaz ----------------

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        if (!controlsVisible) {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                            controlsVisible = true
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        if (!controlsVisible) {
                            player.seekTo(player.currentPosition + 10_000)
                            controlsVisible = true
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            true
                        } else {
                            false
                        }
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
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = aspectMode.resizeMode
            },
            // Las relaciones fijas (16:9, 4:3, 21:9) recortan con aspectRatio;
            // los modos Ajustar/Llenar/Zoom dejan la relación natural del vídeo.
            modifier = if (aspectMode.ratio > 0f) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectMode.ratio)
            } else {
                Modifier.fillMaxSize()
            }
        )

        // Botón central grande al pausar (como Xuper TV)
        AnimatedVisibility(
            visible = !isPlaying && !isBuffering && hasError == null && controlsVisible && showNextPanel == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CenterPlayButton(
                iconRes = R.drawable.ic_play,
                onClick = { player.play() }
            )
        }

        // Cargando…
        if (isBuffering && hasError == null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoadingSpinner()
                Spacer(Modifier.height(14.dp))
                Text("Cargando…", style = fontSize15(), color = Color(0xFF8E8E93))
            }
        }

        // Aviso flotante
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            InfoChip(text = toast ?: "")
        }

        // Error de reproducción
        if (hasError != null) {
            GlassPanel(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(520.dp)
            ) {
                Column(
                    Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No se pudo reproducir el vídeo", style = fontSize20(), color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(hasError ?: "", style = fontSize13(), color = Color(0xFF8E8E93))
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PillButton(
                            text = "Reintentar",
                            iconRes = R.drawable.ic_server,
                            primary = true,
                            onClick = {
                                hasError = null
                                player.setMediaItem(
                                    ExoMediaItem.Builder().setUri(sources[serverIndex].url).build(),
                                    positionMs
                                )
                                player.prepare()
                                player.playWhenReady = true
                            }
                        )
                        PillButton(
                            text = "Volver",
                            iconRes = R.drawable.ic_back,
                            primary = false,
                            onClick = onBack
                        )
                    }
                }
            }
        }

        // ---------------- Superposición de controles ----------------
        AnimatedVisibility(
            visible = controlsVisible && hasError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                // Cabecera: volver, título, franja EPG y reloj
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.78f),
                                1f to Color.Transparent
                            )
                        )
                        .padding(horizontal = 44.dp, vertical = 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = "Volver",
                        size = 48.dp,
                        onClick = onBack
                    )
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                video.title,
                                style = fontSize22(),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(12.dp))
                            InfoChip(video.quality)
                            Spacer(Modifier.width(8.dp))
                            InfoChip(if (video.streamFormat == StreamFormat.HLS) "HLS" else "MP4")
                            if (speed != 1f) {
                                Spacer(Modifier.width(8.dp))
                                InfoChip(SettingsStore.speedLabel(speed))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // Franja EPG estilo Xuper TV: qué se emite ahora y después
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EpgChip("AHORA", Color(0xFFE50914))
                            Spacer(Modifier.width(8.dp))
                            Text(video.title, style = fontSize13(), color = Color(0xFFE5E5EA))
                            nextVideo?.let {
                                Spacer(Modifier.width(16.dp))
                                EpgChip("A CONTINUACIÓN", Color(0xFF636366))
                                Spacer(Modifier.width(8.dp))
                                Text(it.title, style = fontSize13(), color = Color(0xFF8E8E93))
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(clock, style = fontSize20(), color = Color.White)
                }

                Spacer(Modifier.weight(1f))

                // Panel de información del título
                if (showInfo) {
                    GlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 44.dp, vertical = 10.dp)
                    ) {
                        Row(Modifier.padding(20.dp)) {
                            AsyncImage(
                                model = video.cardUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(112.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(20.dp))
                            Column {
                                Text(video.title, style = fontSize20(), color = Color.White)
                                Spacer(Modifier.height(6.dp))
                                MetaLine(video)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    video.description,
                                    style = fontSize13(),
                                    color = Color(0xFF8E8E93),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InfoChip(sources[serverIndex].label)
                                    InfoChip(video.language)
                                    InfoChip(video.certification)
                                }
                            }
                        }
                    }
                }

                // Panel «A continuación» (auto-next)
                showNextPanel?.let { next ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 44.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        GlassPanel(Modifier.width(540.dp)) {
                            Column(Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = next.cardUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(132.dp)
                                            .height(74.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        EpgChip("A CONTINUACIÓN", Color(0xFFE50914))
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            next.title,
                                            style = fontSize17(),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            if (SettingsStore.autoNext) "Empieza en $nextCountdown s"
                                            else "Listo para reproducir",
                                            style = fontSize13(),
                                            color = Color(0xFF8E8E93)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PillButton(
                                        text = "Reproducir ahora",
                                        iconRes = R.drawable.ic_play,
                                        primary = true,
                                        onClick = {
                                            showNextPanel = null
                                            onPlayNext(next)
                                        }
                                    )
                                    PillButton(
                                        text = "Cancelar",
                                        iconRes = R.drawable.ic_back,
                                        primary = false,
                                        onClick = { showNextPanel = null }
                                    )
                                }
                            }
                        }
                    }
                }

                // Menú de opciones activo (derecha)
                if (menu != MenuKind.NONE) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 44.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        when (menu) {
                            MenuKind.SERVER -> OptionMenu("Servidor", serverOptions)
                            MenuKind.QUALITY -> OptionMenu("Calidad", qualityOptions)
                            MenuKind.AUDIO -> OptionMenu("Audio", audioOptions)
                            MenuKind.SUBTITLES -> OptionMenu("Subtítulos", subtitleOptions)
                            MenuKind.ASPECT -> OptionMenu("Relación de aspecto", aspectOptions)
                            MenuKind.SPEED -> OptionMenu("Velocidad", speedOptions)
                            MenuKind.SLEEP -> OptionMenu("Temporizador", sleepOptions)
                            MenuKind.NONE -> {}
                        }
                    }
                }

                // Barra inferior de controles
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.88f)
                            )
                        )
                        .padding(horizontal = 44.dp, vertical = 26.dp)
                ) {
                    // Tiempos + barra de progreso
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(positionMs), style = fontSize13(), color = Color(0xFF8E8E93))
                        Spacer(Modifier.width(16.dp))
                        SeekBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            bufferedMs = bufferedMs,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            if (durationMs > 0) "-${formatTime(durationMs - positionMs)}" else "--:--",
                            style = fontSize13(),
                            color = Color(0xFF8E8E93)
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // Transporte + ajustes del reproductor
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TransportButton(
                            iconRes = R.drawable.ic_rewind,
                            contentDescription = "Retroceder 10 segundos",
                            onClick = {
                                player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                            }
                        )
                        TransportButton(
                            iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            size = 64.dp,
                            primary = true,
                            onClick = { if (isPlaying) player.pause() else player.play() },
                            modifier = Modifier.focusRequester(playFocus)
                        )
                        TransportButton(
                            iconRes = R.drawable.ic_forward,
                            contentDescription = "Avanzar 10 segundos",
                            onClick = { player.seekTo(player.currentPosition + 10_000) }
                        )
                        if (nextVideo != null) {
                            TransportButton(
                                iconRes = R.drawable.ic_skip_next,
                                contentDescription = "Siguiente título",
                                onClick = {
                                    showNextPanel = null
                                    onPlayNext(nextVideo)
                                }
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        ControlPill(
                            iconRes = R.drawable.ic_server,
                            label = sources[serverIndex].label,
                            active = menu == MenuKind.SERVER,
                            onClick = { menu = if (menu == MenuKind.SERVER) MenuKind.NONE else MenuKind.SERVER }
                        )
                        ControlPill(
                            iconRes = R.drawable.ic_quality,
                            label = qualityLabel,
                            active = menu == MenuKind.QUALITY,
                            onClick = { menu = if (menu == MenuKind.QUALITY) MenuKind.NONE else MenuKind.QUALITY }
                        )
                        ControlPill(
                            iconRes = R.drawable.ic_audio,
                            label = "Audio",
                            active = menu == MenuKind.AUDIO,
                            onClick = { menu = if (menu == MenuKind.AUDIO) MenuKind.NONE else MenuKind.AUDIO }
                        )
                        ControlPill(
                            iconRes = R.drawable.ic_subtitles,
                            label = "Subtítulos",
                            active = subtitlesOn || menu == MenuKind.SUBTITLES,
                            onClick = { menu = if (menu == MenuKind.SUBTITLES) MenuKind.NONE else MenuKind.SUBTITLES }
                        )
                        ControlPill(
                            iconRes = R.drawable.ic_aspect,
                            label = aspectMode.label,
                            active = menu == MenuKind.ASPECT,
                            onClick = { menu = if (menu == MenuKind.ASPECT) MenuKind.NONE else MenuKind.ASPECT }
                        )
                        ControlPill(
                            iconRes = R.drawable.ic_quality,
                            label = SettingsStore.speedLabel(speed),
                            active = menu == MenuKind.SPEED,
                            onClick = { menu = if (menu == MenuKind.SPEED) MenuKind.NONE else MenuKind.SPEED }
                        )
                        ControlPill(
                            iconRes = R.drawable.ic_server,
                            label = sleepLabel,
                            active = sleepDeadline != null || menu == MenuKind.SLEEP,
                            onClick = { menu = if (menu == MenuKind.SLEEP) MenuKind.NONE else MenuKind.SLEEP }
                        )
                        ControlPill(
                            iconRes = R.drawable.ic_info,
                            label = "Info",
                            active = showInfo,
                            onClick = { showInfo = !showInfo }
                        )
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Estilos tipográficos locales del reproductor                              */
/* -------------------------------------------------------------------------- */

private fun fontSize22() = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp
)

private fun fontSize20() = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp
)

private fun fontSize17() = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp
)

private fun fontSize15() = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp
)

private fun fontSize13() = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp
)
