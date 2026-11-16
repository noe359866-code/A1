@file:kotlin.OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ConfirmKeyUpGuard
import com.omnitv.app.data.MediaBridge
import com.omnitv.app.data.PlaybackDiagnostics
import com.omnitv.app.data.PlayerScrubRates
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.data.SkipKind
import com.omnitv.app.data.SkipSegment
import com.omnitv.app.data.SkipSegmentService
import com.omnitv.app.data.SkipTimeline
import com.omnitv.app.data.StreamFormat
import com.omnitv.app.data.TrackFacts
import com.omnitv.app.data.TraktService
import com.omnitv.app.data.TvKeyCodes
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.LoadingSpinner
import com.omnitv.app.ui.components.MetaLine
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.SeekBar
import com.omnitv.app.ui.components.consumeStrayConfirmKeyUp
import com.omnitv.app.ui.components.formatTime
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.LiveAccent
import com.omnitv.app.ui.theme.LiveAccentText
import com.omnitv.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/* -------------------------------------------------------------------------- */
/*  Estados del reproductor                                                   */
/* -------------------------------------------------------------------------- */

/** Umbral mínimo de reproducción para el `scrobble/start` de Trakt: por debajo,
 * un «siguiente»/salir accidental dejaría un marcado espurio. El STOP (fin o
 * salida) conserva `pos` real; Trakt decide "visto" con ≥80 %. */
private const val SCROBBLE_START_MIN_MS = 10_000L

private enum class MenuKind { NONE, SERVER, QUALITY, AUDIO, SUBTITLES, ASPECT, SPEED, SLEEP }

// Media3 UI está fijado a 1.4.1; asumimos explícitamente su API marcada.
@OptIn(UnstableApi::class)
private enum class AspectMode(val label: String, val ratio: Float, val resizeMode: Int) {
    FIT("Ajustar", 0f, AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Llenar", 0f, AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", 0f, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    R16_9("16:9", 16f / 9f, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    R4_3("4:3", 4f / 3f, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    R21_9("21:9", 21f / 9f, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

private data class TrackChoice(val group: TrackGroup, val index: Int, val format: Format)

/** MIME/codecs que Media3 declaró para la pista seleccionada (o la primera). */
private fun trackFacts(tracks: Tracks, type: Int): TrackFacts? {
    var fallback: TrackFacts? = null
    for (group in tracks.groups) {
        if (group.type != type) continue
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val facts = TrackFacts(format.sampleMimeType, format.codecs, group.isTrackSupported(i))
            if (group.isTrackSelected(i)) return facts
            if (fallback == null) fallback = facts
        }
    }
    return fallback
}

private fun nowClock(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

/**
 * Reproductor para TV inspirado en tvOS sobre Media3 ExoPlayer.
 *
 * - **Autoplay**: los vídeos empiezan solos (ajustable en Ajustes).
 * - **Auto-next**: al terminar aparece «A continuación» con cuenta atrás
 *   de 10 s y botones *Reproducir ahora* / *Cancelar*.
 * - Franja EPG «AHORA / A CONTINUACIÓN», multi-servidor con conmutación
 *   automática, calidad/audio/subtítulos/velocidad, zoom y temporizador.
 * - Controles de vidrio para mando de TV, barra de progreso enfocable y
 *   avance/retroceso acelerado al mantener ◀ / ▶ (±10s → ±20s → ±30s → ±60s).
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    video: Video,
    onBack: () -> Unit,
    onPlayNext: (Video) -> Unit = {}
) {
    // Cada título tiene su propio reproductor, trabajos asíncronos y estado de UI.
    key(video.id) { PlayerScreenContent(video, onBack, onPlayNext) }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreenContent(video: Video, onBack: () -> Unit, onPlayNext: (Video) -> Unit) {
    val context = LocalContext.current
    var sources by remember(video.id) { mutableStateOf(video.playbackSources) }
    val nextVideo = remember(video.id) { CatalogRepository.nextEpisode(video.id) }
    if (sources.isEmpty()) {
        val emptyBackFocus = remember { FocusRequester() }
        RequestTvFocus(emptyBackFocus, key = video.id)
        BackHandler(onBack = onBack)
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Este título no tiene una fuente reproducible", style = AppType.body, color = Color.White)
                Spacer(Modifier.height(18.dp))
                PillButton("Volver", primary = false, onClick = onBack, modifier = Modifier.focusRequester(emptyBackFocus))
            }
        }
        return
    }

    val trackSelector = remember(video.id) {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !SettingsStore.subtitlesDefault).build())
        }
    }
    val player = remember(video.id) {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                setMediaSource(streamMediaSource(context, sources[0]))
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
    var previousControlsVisible by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedMs by remember { mutableStateOf(0L) }
    var clock by remember { mutableStateOf(nowClock()) }

    var serverIndex by remember { mutableStateOf(0) }
    var bridgeBusy by remember { mutableStateOf(false) }
    var bridgeMessage by remember { mutableStateOf<String?>(null) }
    val bridgeAttempted = remember(video.id) { mutableSetOf<String>() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(MenuKind.NONE) }
    var lastOpenedMenu by remember { mutableStateOf(MenuKind.NONE) }
    var previousMenu by remember { mutableStateOf(MenuKind.NONE) }
    var previousHasError by remember { mutableStateOf<String?>(null) }
    var previousShowNext by remember { mutableStateOf<Video?>(null) }
    var previousShowSkip by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var seekPreview by remember { mutableStateOf<String?>(null) }

    var tracks by remember { mutableStateOf(player.currentTracks) }
    var videoChoice by remember { mutableStateOf<TrackChoice?>(null) }
    var audioChoice by remember { mutableStateOf<TrackChoice?>(null) }
    var textChoice by remember { mutableStateOf<TrackChoice?>(null) }
    var subtitlesOn by remember { mutableStateOf(false) }
    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }
    var speed by remember { mutableStateOf(SettingsStore.playbackSpeed) }
    var preferenceApplied by remember { mutableStateOf(false) }
    var resumeApplied by remember { mutableStateOf(false) }

    // Auto-next: panel «A continuación» + cuenta atrás
    var showNextPanel by remember { mutableStateOf<Video?>(null) }
    var nextCountdown by remember { mutableStateOf(0) }

    // Temporizador de apagado
    var sleepMinutes by remember { mutableStateOf(0) }
    var sleepDeadline by remember { mutableStateOf<Long?>(null) }
    var sleepLabel by remember { mutableStateOf("Temporizador") }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val cancelNextFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val seekBarFocus = remember { FocusRequester() }

    val serverPillFocus = remember { FocusRequester() }
    val qualityPillFocus = remember { FocusRequester() }
    val audioPillFocus = remember { FocusRequester() }
    val subtitlesPillFocus = remember { FocusRequester() }
    val aspectPillFocus = remember { FocusRequester() }
    val speedPillFocus = remember { FocusRequester() }
    val sleepPillFocus = remember { FocusRequester() }

    val rootConfirmGuard = remember { ConfirmKeyUpGuard(initialSuppressUntilDown = false) }

    // ---------------- Saltar intro / resumen / créditos ----------------
    var skipTimeline by remember(video.id) { mutableStateOf(SkipTimeline.EMPTY) }
    // Trakt: un scrobble START por reproducción; STOP al salir o terminar.
    var traktScrobbleStarted by remember(video.id) { mutableStateOf(false) }
    val autoSkipped = remember(video.id) { mutableSetOf<String>() }
    val durationSeconds = (durationMs / 1000L).toInt()
    val activeSkip: SkipSegment? = remember(skipTimeline, positionMs) { skipTimeline.activeAt(positionMs) }
    var dismissedSkipKey by remember(video.id) { mutableStateOf<String?>(null) }
    var skipButtonFocused by remember { mutableStateOf(false) }
    val visibleSkip = activeSkip?.takeIf { "${it.kind}:${it.startMs}" != dismissedSkipKey }
    val showSkipOverlay = visibleSkip != null && hasError == null && showNextPanel == null && menu == MenuKind.NONE && !showInfo

    fun skipSegment(segment: SkipSegment, fromAuto: Boolean = false) {
        val total = player.duration.takeIf { it > 0 } ?: durationMs
        val isTailCredits = segment.kind == SkipKind.CREDITS &&
            skipTimeline.isTailSegment(segment, total) &&
            nextVideo != null
        if (isTailCredits) {
            val next = nextVideo!!
            if (!fromAuto && '~' in video.id) {
                showNextPanel = null
                onPlayNext(next)
            } else {
                showNextPanel = next
                menu = MenuKind.NONE
                showInfo = false
                controlsVisible = true
                toast = "Saltado: ${segment.kind.label}"
            }
            return
        }
        val target = segment.targetMs(total)
        if (target > player.currentPosition) {
            player.seekTo(target)
            positionMs = target
            toast = "Saltado: ${segment.kind.label}"
        }
    }

    fun seekBy(deltaMs: Long, showPreview: Boolean) {
        val total = player.duration.takeIf { it > 0 } ?: durationMs
        val target = PlayerScrubRates.computeSeekTarget(player.currentPosition, total, deltaMs)
        player.seekTo(target)
        positionMs = target
        if (showPreview) {
            val sec = kotlin.math.abs(deltaMs / 1000L)
            val arrow = if (deltaMs < 0) "⏪ -$sec s" else "⏩ +$sec s"
            seekPreview = if (total > 0) "$arrow · ${formatTime(target)} / ${formatTime(total)}" else arrow
        }
    }

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

    val failedServers = remember(video.id) { mutableSetOf<Int>() }
    val switchServer: (Int) -> Unit = { index ->
        if (index in sources.indices) {
            val position = player.currentPosition.coerceAtLeast(0)
            val shouldPlay = player.playWhenReady
            serverIndex = index
            videoChoice = null; audioChoice = null; textChoice = null; subtitlesOn = false
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !SettingsStore.subtitlesDefault)
                    .build()
            )
            player.setMediaSource(streamMediaSource(context, sources[index]), position)
            preferenceApplied = false
            player.prepare()
            player.playWhenReady = shouldPlay
            hasError = null
            bridgeMessage = null
            toast = "Reproduciendo desde ${sources[index].label}"
        }
    }

    // Algunas fuentes publican la duración después de STATE_READY.
    fun applyResumeIfReady() {
        val duration = player.duration
        if (resumeApplied || player.playbackState != Player.STATE_READY || duration <= 0) return
        resumeApplied = true
        val fraction = ProfileStore.currentProfile?.progress?.get(video.id)
        if (fraction != null && fraction > 0.02f && fraction < 0.94f) {
            player.seekTo((duration * fraction).toLong())
        }
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
                    applyResumeIfReady()
                }
                if (playbackState == Player.STATE_ENDED && traktScrobbleStarted) {
                    traktScrobbleStarted = false
                    val target = video
                    val dur = player.duration.coerceAtLeast(0L)
                    scope.launch(Dispatchers.IO) { TraktService.scrobble(TraktService.ScrobbleEvent.STOP, target, dur, dur) }
                }
                if (playbackState == Player.STATE_ENDED) {
                    if (nextVideo != null && showNextPanel == null && hasError == null) {
                        showNextPanel = nextVideo
                        menu = MenuKind.NONE
                        showInfo = false
                        controlsVisible = true
                    } else if (nextVideo == null) {
                        toast = "Fin de la reproducción"
                    }
                }
            }

            override fun onTracksChanged(currentTracks: Tracks) {
                tracks = currentTracks
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
                val source = sources[serverIndex]
                val report = PlaybackDiagnostics.record(
                    errorCode = error.errorCodeName,
                    sourceLabel = source.label,
                    video = trackFacts(tracks, C.TRACK_TYPE_VIDEO),
                    audio = trackFacts(tracks, C.TRACK_TYPE_AUDIO)
                )
                failedServers.add(serverIndex)
                val next = (serverIndex + 1 until sources.size).firstOrNull { it !in failedServers }
                    ?: sources.indices.firstOrNull { it !in failedServers }
                if (next != null) {
                    toast = "Fuente no disponible · probando ${sources[next].label}…"
                    switchServer(next)
                } else {
                    hasError = error.errorCodeName
                    controlsVisible = true
                    if (report.bridgeRecommended && SettingsStore.ffmpegBridge.isNotBlank() &&
                        !source.label.endsWith(" · puente") && bridgeAttempted.add(source.url)
                    ) {
                        bridgeBusy = true
                        bridgeMessage = "Preparando una versión compatible en el puente…"
                        scope.launch {
                            try {
                                val repaired = MediaBridge.repair(source, report.errorCode)
                                if (repaired != null && sources.none { it.url == repaired.url }) {
                                    bridgeAttempted.add(repaired.url)
                                    sources = sources + repaired
                                    switchServer(sources.lastIndex)
                                } else {
                                    bridgeMessage = MediaBridge.status.value
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                bridgeMessage = "El puente multimedia no pudo completar la conversión"
                            } finally {
                                bridgeBusy = false
                            }
                        }
                    }
                }
            }
        }
        var playerReleased = false
        fun saveProgressSafe() {
            if (playerReleased) return
            val dur = player.duration
            if (dur > 0) {
                ProfileStore.setProgress(video.id, player.currentPosition.toFloat() / dur)
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgressSafe()
            if (traktScrobbleStarted) {
                val target = video
                val pos = player.currentPosition
                val dur = player.duration.coerceAtLeast(0L)
                // El scope de Compose ya no está activo aquí: llamada directa
                // bloqueante pero acotada por el timeout de red de AddonHttp.
                Thread {
                    TraktService.scrobble(TraktService.ScrobbleEvent.STOP, target, pos, dur)
                }.start()
            }
            playerReleased = true
            player.removeListener(listener)
            player.playWhenReady = false
            player.stop()
            player.clearVideoSurface()
            player.release()
        }
    }

    var disposedLifecycle by remember(player) { mutableStateOf(false) }
    fun saveProgress() {
        if (disposedLifecycle) return
        val dur = player.duration
        if (dur > 0) {
            ProfileStore.setProgress(video.id, player.currentPosition.toFloat() / dur)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                saveProgress()
                if (traktScrobbleStarted) {
                    val target = video
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0L)
                    scope.launch(Dispatchers.IO) { TraktService.scrobble(TraktService.ScrobbleEvent.PAUSE, target, pos, dur) }
                }
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            disposedLifecycle = true
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Sondeo de posición + reloj
    LaunchedEffect(player, video.id) {
        var ticks = 0
        while (true) {
            applyResumeIfReady()
            positionMs = player.currentPosition.coerceAtLeast(0)
            bufferedMs = player.bufferedPosition.coerceAtLeast(0)
            if (player.duration > 0) durationMs = player.duration
            clock = nowClock()
            if (player.isPlaying && !traktScrobbleStarted && player.currentPosition >= SCROBBLE_START_MIN_MS) {
                traktScrobbleStarted = true
                val target = video
                val pos = player.currentPosition
                val dur = player.duration.coerceAtLeast(0L)
                scope.launch(Dispatchers.IO) { TraktService.scrobble(TraktService.ScrobbleEvent.START, target, pos, dur) }
            }
            if (++ticks % 20 == 0 && player.isPlaying) saveProgress()
            delay(500)
        }
    }

    // Ocultar controles a los 7 s de inactividad mientras reproduce
    LaunchedEffect(controlsVisible, isPlaying, menu, showNextPanel, showInfo, interactionTick) {
        if (controlsVisible && isPlaying && menu == MenuKind.NONE && showNextPanel == null && !showInfo) {
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

    LaunchedEffect(seekPreview) {
        if (seekPreview != null) {
            delay(1600)
            seekPreview = null
        }
    }

    // Cuenta atrás del auto-next (10 s)
    val autoNextEnabled = SettingsStore.autoNext && (ProfileStore.currentProfile?.autoPlayNext != false)
    LaunchedEffect(showNextPanel, autoNextEnabled) {
        val next = showNextPanel
        if (next != null && autoNextEnabled) {
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

    LaunchedEffect(video.id, durationSeconds, SettingsStore.revision) {
        if (!SettingsStore.skipSegmentsEnabled) {
            skipTimeline = SkipTimeline.EMPTY
            return@LaunchedEffect
        }
        var waited = 0
        while (durationSeconds == 0 && player.duration <= 0 && waited < 12_000) {
            delay(1_000)
            waited += 1_000
        }
        val knownMs = if (durationSeconds > 0) durationSeconds * 1000L else player.duration.coerceAtLeast(0)
        skipTimeline = try {
            SkipSegmentService.fetch(video, knownMs)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SkipTimeline.EMPTY
        }
    }

    LaunchedEffect(activeSkip) {
        if (activeSkip == null) dismissedSkipKey = null
    }

    LaunchedEffect(visibleSkip) {
        if (visibleSkip == null) skipButtonFocused = false
    }

    LaunchedEffect(activeSkip, SettingsStore.autoSkipKinds, durationMs) {
        val segment = activeSkip ?: return@LaunchedEffect
        if (segment.kind.name !in SettingsStore.autoSkipKinds) return@LaunchedEffect
        if (segment.uncertain) return@LaunchedEffect
        val key = "${segment.kind}:${segment.startMs}"
        if (autoSkipped.add(key)) skipSegment(segment, fromAuto = true)
    }

    LaunchedEffect(sleepDeadline) {
        val deadline = sleepDeadline
        if (deadline == null) {
            sleepLabel = "Temporizador"
            return@LaunchedEffect
        }
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val remaining = (deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0) / 1000
            sleepLabel = String.format(Locale.US, "⏱ %d:%02d", remaining / 60, remaining % 60)
            delay(1000)
        }
        player.pause()
        toast = "Temporizador finalizado · vídeo en pausa"
        sleepMinutes = 0
        sleepDeadline = null
    }

    if (menu != MenuKind.NONE) {
        lastOpenedMenu = menu
    }

    fun pillRequesterFor(kind: MenuKind): FocusRequester? = when (kind) {
        MenuKind.SERVER -> serverPillFocus
        MenuKind.QUALITY -> qualityPillFocus
        MenuKind.AUDIO -> audioPillFocus
        MenuKind.SUBTITLES -> subtitlesPillFocus
        MenuKind.ASPECT -> aspectPillFocus
        MenuKind.SPEED -> speedPillFocus
        MenuKind.SLEEP -> sleepPillFocus
        MenuKind.NONE -> null
    }

    // Gestión de foco: respeta menús abiertos y restaura el foco a la píldora que abrió el menú
    LaunchedEffect(controlsVisible, menu, hasError, showNextPanel, showSkipOverlay) {
        val closedMenu = previousMenu != MenuKind.NONE && menu == MenuKind.NONE
        val controlsJustShown = !previousControlsVisible && controlsVisible
        val clearedOverlay =
            (previousHasError != null && hasError == null) ||
                (previousShowNext != null && showNextPanel == null)
        val skipDisappeared = previousShowSkip && !showSkipOverlay && skipButtonFocused
        previousMenu = menu
        previousControlsVisible = controlsVisible
        previousHasError = hasError
        previousShowNext = showNextPanel
        previousShowSkip = showSkipOverlay
        for (attempt in 0 until 5) {
            withFrameNanos { }
            val target = when {
                hasError != null -> retryFocus
                showNextPanel != null -> cancelNextFocus
                !controlsVisible && showSkipOverlay -> skipFocus
                !controlsVisible -> rootFocus
                menu != MenuKind.NONE -> null
                closedMenu -> pillRequesterFor(lastOpenedMenu) ?: playFocus
                controlsJustShown || clearedOverlay || skipDisappeared -> playFocus
                else -> null
            }
            if (target == null) return@LaunchedEffect
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    fun handleBackAction() {
        when {
            showNextPanel != null -> showNextPanel = null
            menu != MenuKind.NONE -> menu = MenuKind.NONE
            showInfo -> showInfo = false
            controlsVisible -> controlsVisible = false
            visibleSkip != null -> dismissedSkipKey = "${visibleSkip.kind}:${visibleSkip.startMs}"
            else -> onBack()
        }
    }

    BackHandler { handleBackAction() }

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
            label = "${source.label} · ${when (source.format) { StreamFormat.HLS -> "HLS"; StreamFormat.DASH -> "DASH"; StreamFormat.MP4 -> "MP4" }}",
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
            sleepDeadline = if (minutes == 0) null else android.os.SystemClock.elapsedRealtime() + minutes * 60_000L
            menu = MenuKind.NONE
            toast = if (minutes == 0) "Temporizador desactivado" else "Temporizador: $minutes min"
        }
    }

    val qualityLabel = videoChoice?.let { choice ->
        val height = choice.format.height
        if (height > 0) "${height}p" else "Manual"
    } ?: "Auto"

    // ---------------- Interfaz ----------------

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusProperties {
                canFocus = !controlsVisible && hasError == null && showNextPanel == null
            }
            .consumeStrayConfirmKeyUp(rootConfirmGuard)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (controlsVisible) interactionTick++

                val native = event.nativeKeyEvent
                val code = native.keyCode
                if (hasError != null) {
                    return@onPreviewKeyEvent if (code == TvKeyCodes.KEYCODE_ESCAPE) {
                        handleBackAction()
                        true
                    } else {
                        false
                    }
                }
                when {
                    code == TvKeyCodes.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.playWhenReady) player.pause() else player.play()
                        true
                    }
                    code == TvKeyCodes.KEYCODE_MEDIA_PLAY -> {
                        player.play()
                        true
                    }
                    code == TvKeyCodes.KEYCODE_MEDIA_PAUSE || code == TvKeyCodes.KEYCODE_MEDIA_STOP -> {
                        player.pause()
                        true
                    }
                    TvKeyCodes.isRewindKey(code) -> {
                        seekBy(-PlayerScrubRates.seekStepMs(native.repeatCount), showPreview = !controlsVisible)
                        true
                    }
                    TvKeyCodes.isFastForwardKey(code) -> {
                        seekBy(PlayerScrubRates.seekStepMs(native.repeatCount), showPreview = !controlsVisible)
                        true
                    }
                    code == TvKeyCodes.KEYCODE_MEDIA_NEXT -> {
                        when {
                            nextVideo != null -> onPlayNext(nextVideo)
                            visibleSkip != null -> skipSegment(visibleSkip)
                        }
                        true
                    }
                    code == TvKeyCodes.KEYCODE_MEDIA_PREVIOUS -> {
                        player.seekTo(0)
                        positionMs = 0L
                        true
                    }
                    code == TvKeyCodes.KEYCODE_CAPTIONS -> {
                        controlsVisible = true
                        showInfo = false
                        menu = if (menu == MenuKind.SUBTITLES) MenuKind.NONE else MenuKind.SUBTITLES
                        true
                    }
                    code == TvKeyCodes.KEYCODE_MEDIA_AUDIO_TRACK -> {
                        controlsVisible = true
                        showInfo = false
                        menu = if (menu == MenuKind.AUDIO) MenuKind.NONE else MenuKind.AUDIO
                        true
                    }
                    code == TvKeyCodes.KEYCODE_INFO -> {
                        controlsVisible = true
                        menu = MenuKind.NONE
                        showInfo = !showInfo
                        true
                    }
                    code == TvKeyCodes.KEYCODE_SPACE -> {
                        if (player.playWhenReady) player.pause() else player.play()
                        true
                    }
                    code == TvKeyCodes.KEYCODE_ESCAPE -> {
                        handleBackAction()
                        true
                    }
                    TvKeyCodes.isConfirm(code) -> {
                        if (!controlsVisible) {
                            if (skipButtonFocused && visibleSkip != null) {
                                false // deja que OK pulse el botón «Saltar»
                            } else {
                                rootConfirmGuard.armSuppressNextKeyUp()
                                controlsVisible = true
                                true
                            }
                        } else {
                            false
                        }
                    }
                    TvKeyCodes.isLeft(code) -> {
                        if (!controlsVisible) {
                            seekBy(-PlayerScrubRates.seekStepMs(native.repeatCount), showPreview = true)
                            true
                        } else {
                            false
                        }
                    }
                    TvKeyCodes.isRight(code) -> {
                        if (!controlsVisible) {
                            seekBy(PlayerScrubRates.seekStepMs(native.repeatCount), showPreview = true)
                            true
                        } else {
                            false
                        }
                    }
                    TvKeyCodes.isUp(code) || TvKeyCodes.isDown(code) -> {
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
            .focusable()
    ) {
        val containerRatio = if (maxHeight > 0.dp) maxWidth.value / maxHeight.value else (16f / 9f)
        val videoModifier = when {
            aspectMode.ratio <= 0f -> Modifier.fillMaxSize()
            aspectMode.ratio < containerRatio ->
                Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(aspectMode.ratio)
            else ->
                Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(aspectMode.ratio)
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isClickable = false
                    isLongClickable = false
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = aspectMode.resizeMode
                view.keepScreenOn = (isPlaying || isBuffering) && hasError == null
            },
            modifier = videoModifier
        )

        // Indicador central al pausar
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
                Text("Cargando…", style = AppType.metadata, color = TextSecondary)
            }
        }

        // Indicador compacto de salto rápido (con controles ocultos)
        AnimatedVisibility(
            visible = seekPreview != null && !controlsVisible && hasError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            GlassPanel(cornerRadius = 999.dp) {
                Text(
                    text = seekPreview.orEmpty(),
                    style = AppType.controlLabel,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
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
                    Text("No se pudo reproducir el vídeo", style = AppType.body, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(hasError ?: "", style = AppType.metadata, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        PlaybackDiagnostics.current()?.advice ?: "Revisa el diagnóstico en Ajustes",
                        style = AppType.metadata, color = TextSecondary
                    )
                    if (bridgeBusy || bridgeMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (bridgeBusy) "Convirtiendo en el puente externo…" else bridgeMessage.orEmpty(),
                            style = AppType.metadata, color = LiveAccentText
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(
                        Modifier.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PillButton(
                            text = if (bridgeBusy) "Convirtiendo…" else "Reintentar",
                            iconRes = R.drawable.ic_server,
                            primary = true,
                            modifier = Modifier.focusRequester(retryFocus),
                            onClick = {
                                if (!bridgeBusy) {
                                    failedServers.clear()
                                    switchServer(serverIndex)
                                }
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

        // Botón de salto: esquina inferior derecha con controles ocultos, o sobre la barra cuando están abiertos
        AnimatedVisibility(
            visible = showSkipOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AppSpacing.screenMargin, bottom = if (controlsVisible) 248.dp else 52.dp)
        ) {
            visibleSkip?.let { segment ->
                val isTailNext = segment.kind == SkipKind.CREDITS &&
                    skipTimeline.isTailSegment(segment, durationMs) &&
                    nextVideo != null &&
                    '~' in video.id
                val skipLabel = when {
                    isTailNext -> "Siguiente episodio"
                    segment.uncertain -> "Saltar ${segment.kind.label} (?)"
                    else -> "Saltar ${segment.kind.label}"
                }
                ControlPill(
                    iconRes = R.drawable.ic_skip_next,
                    label = skipLabel,
                    onClick = { skipSegment(segment) },
                    onFocusChanged = { skipButtonFocused = it },
                    maxWidth = 260.dp,
                    modifier = Modifier
                        .focusRequester(skipFocus)
                        .then(
                            if (controlsVisible) Modifier.focusProperties { down = seekBarFocus }
                            else Modifier
                        ),
                    active = true
                )
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
                // Cabecera: volver, título, franja informativa y reloj
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.78f),
                                1f to Color.Transparent
                            )
                        )
                        .padding(horizontal = AppSpacing.screenMargin, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = "Volver",
                        size = 48.dp,
                        onClick = onBack
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            video.title,
                            style = AppType.sectionTitle,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isPlaying) "Reproduciendo" else if (isBuffering) "Cargando…" else "En pausa",
                            style = AppType.metadata,
                            color = Color.White.copy(alpha = 0.68f)
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    Text(clock, style = AppType.body, color = Color.White)
                }

                Spacer(Modifier.weight(1f))

                // Panel de información del título
                if (showInfo) {
                    GlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.screenMargin, vertical = 8.dp)
                    ) {
                        Row(Modifier.padding(20.dp)) {
                            AsyncImage(
                                model = video.backdropUrl.ifBlank { video.cardUrl },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(112.dp)
                                    .clip(AppRadius.sm)
                            )
                            Spacer(Modifier.width(20.dp))
                            Column {
                                Text(video.title, style = AppType.body, color = Color.White)
                                Spacer(Modifier.height(6.dp))
                                MetaLine(video)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    video.description,
                                    style = AppType.metadata,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InfoChip(sources[serverIndex].label)
                                    InfoChip(video.quality)
                                    InfoChip(when (sources[serverIndex].format) {
                                        StreamFormat.HLS -> "HLS"
                                        StreamFormat.DASH -> "DASH"
                                        StreamFormat.MP4 -> "MP4"
                                    })
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
                            .padding(horizontal = AppSpacing.screenMargin, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        GlassPanel(Modifier.width(540.dp)) {
                            Column(Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = next.backdropUrl.ifBlank { next.cardUrl },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(132.dp)
                                            .height(74.dp)
                                            .clip(AppRadius.sm)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        EpgChip("A CONTINUACIÓN", LiveAccent)
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            next.title,
                                            style = AppType.body,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            if (autoNextEnabled) "Empieza en $nextCountdown s"
                                            else "Listo para reproducir",
                                            style = AppType.metadata,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    Modifier.focusGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
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
                                        modifier = Modifier.focusRequester(cancelNextFocus),
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
                            .padding(horizontal = AppSpacing.screenMargin, vertical = 8.dp),
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

                // Controles inferiores (barra de progreso enfocable + transporte + ajustes)
                if (showNextPanel == null) Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.88f)
                            )
                        )
                        .padding(start = AppSpacing.screenMargin, end = AppSpacing.screenMargin, top = 30.dp, bottom = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(positionMs), style = AppType.playerTime, color = TextSecondary)
                        Spacer(Modifier.width(16.dp))
                        SeekBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            bufferedMs = bufferedMs,
                            skipSegments = skipTimeline.segments,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(seekBarFocus)
                                .then(
                                    if (showSkipOverlay) Modifier.focusProperties { up = skipFocus }
                                    else Modifier
                                ),
                            onSeekBy = { delta, _ ->
                                seekBy(delta, showPreview = false)
                                interactionTick++
                            },
                            onConfirm = {
                                if (player.playWhenReady) player.pause() else player.play()
                            }
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            if (durationMs > 0) "-${formatTime((durationMs - positionMs).coerceAtLeast(0L))}" else "--:--",
                            style = AppType.playerTime,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TransportButton(
                            iconRes = R.drawable.ic_rewind,
                            contentDescription = "Retroceder 10 segundos",
                            onClick = { seekBy(-10_000L, showPreview = false) }
                        )
                        TransportButton(
                            iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            size = 64.dp,
                            primary = true,
                            onClick = { if (player.playWhenReady) player.pause() else player.play() },
                            modifier = Modifier.focusRequester(playFocus)
                        )
                        TransportButton(
                            iconRes = R.drawable.ic_forward,
                            contentDescription = "Avanzar 10 segundos",
                            onClick = { seekBy(10_000L, showPreview = false) }
                        )
                        if (nextVideo != null) {
                            TransportButton(
                                iconRes = R.drawable.ic_skip_next,
                                contentDescription = "Siguiente título",
                                onClick = { showNextPanel = null; onPlayNext(nextVideo) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .focusGroup()
                            .focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            ControlPill(
                                iconRes = R.drawable.ic_server,
                                label = sources[serverIndex].label,
                                active = menu == MenuKind.SERVER,
                                modifier = Modifier.focusRequester(serverPillFocus),
                                onClick = { showInfo = false; menu = if (menu == MenuKind.SERVER) MenuKind.NONE else MenuKind.SERVER }
                            )
                        }
                        item {
                            ControlPill(
                                iconRes = R.drawable.ic_quality,
                                label = qualityLabel,
                                active = menu == MenuKind.QUALITY,
                                modifier = Modifier.focusRequester(qualityPillFocus),
                                onClick = { showInfo = false; menu = if (menu == MenuKind.QUALITY) MenuKind.NONE else MenuKind.QUALITY }
                            )
                        }
                        item {
                            ControlPill(
                                iconRes = R.drawable.ic_audio,
                                label = "Audio",
                                active = menu == MenuKind.AUDIO,
                                modifier = Modifier.focusRequester(audioPillFocus),
                                onClick = { showInfo = false; menu = if (menu == MenuKind.AUDIO) MenuKind.NONE else MenuKind.AUDIO }
                            )
                        }
                        item {
                            ControlPill(
                                iconRes = R.drawable.ic_subtitles,
                                label = "Subtítulos",
                                active = subtitlesOn || menu == MenuKind.SUBTITLES,
                                modifier = Modifier.focusRequester(subtitlesPillFocus),
                                onClick = { showInfo = false; menu = if (menu == MenuKind.SUBTITLES) MenuKind.NONE else MenuKind.SUBTITLES }
                            )
                        }
                        item {
                            ControlPill(
                                iconRes = R.drawable.ic_aspect,
                                label = aspectMode.label,
                                active = menu == MenuKind.ASPECT,
                                modifier = Modifier.focusRequester(aspectPillFocus),
                                onClick = { showInfo = false; menu = if (menu == MenuKind.ASPECT) MenuKind.NONE else MenuKind.ASPECT }
                            )
                        }
                        item {
                            ControlPill(
                                iconRes = R.drawable.ic_quality,
                                label = SettingsStore.speedLabel(speed),
                                active = menu == MenuKind.SPEED,
                                modifier = Modifier.focusRequester(speedPillFocus),
                                onClick = { showInfo = false; menu = if (menu == MenuKind.SPEED) MenuKind.NONE else MenuKind.SPEED }
                            )
                        }
                        item {
                            ControlPill(
                                iconRes = R.drawable.ic_server,
                                label = sleepLabel,
                                active = sleepDeadline != null || menu == MenuKind.SLEEP,
                                modifier = Modifier.focusRequester(sleepPillFocus),
                                onClick = { showInfo = false; menu = if (menu == MenuKind.SLEEP) MenuKind.NONE else MenuKind.SLEEP }
                            )
                        }
                        item {
                            ControlPill(
                                iconRes = R.drawable.ic_info,
                                label = "Info",
                                active = showInfo,
                                onClick = { menu = MenuKind.NONE; showInfo = !showInfo }
                            )
                        }
                        val introEstimateMs = skipTimeline.introEstimateMs
                        if (visibleSkip == null && introEstimateMs != null && positionMs < 360_000L) {
                            item {
                                val estSec = (introEstimateMs / 1000L).coerceAtLeast(5L)
                                ControlPill(
                                    iconRes = R.drawable.ic_skip_next,
                                    label = "Intro ~$estSec s",
                                    onClick = {
                                        val total = player.duration.takeIf { it > 0 } ?: durationMs
                                        val target = (player.currentPosition + introEstimateMs)
                                            .let { if (total > 0) minOf(it, total) else it }
                                        player.seekTo(target)
                                        positionMs = target
                                        toast = "Avance de intro estimado (+$estSec s)"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
