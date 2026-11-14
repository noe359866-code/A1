package com.omnitv.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.omnitv.app.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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
private fun nowClock(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
fun PlayerScreen(video: Video, onBack: () -> Unit, onPlayNext: (Video) -> Unit = {}) {
    val context = LocalContext.current
    val sources = video.playbackSources
    val nextVideo = remember(video.id) { CatalogRepository.related(video.id).firstOrNull() }
    val trackSelector = remember { DefaultTrackSelector(context) }
    val player = remember {
        ExoPlayer.Builder(context).setTrackSelector(trackSelector).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(), true)
            setMediaItem(ExoMediaItem.Builder().setUri(sources[0].url).build())
            playWhenReady = SettingsStore.autoplay
            if (SettingsStore.mutedStart) volume = 0f
            setPlaybackSpeed(SettingsStore.playbackSpeed); prepare()
        }
    }
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
    var showNextPanel by remember { mutableStateOf<Video?>(null) }
    var nextCountdown by remember { mutableStateOf(0) }
    var sleepMinutes by remember { mutableStateOf(0) }
    var sleepDeadline by remember { mutableStateOf<Long?>(null) }
    var sleepLabel by remember { mutableStateOf("Temporizador") }
    val rootFocus = remember { FocusRequester() }; val playFocus = remember { FocusRequester() }

    fun applyTrack(type: Int, choice: TrackChoice?, disable: Boolean = false) {
        val b = trackSelector.buildUponParameters(); b.clearOverridesOfType(type)
        if (type == C.TRACK_TYPE_TEXT) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disable)
        if (choice != null) b.setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
        trackSelector.setParameters(b.build())
    }
    val switchServer: (Int) -> Unit = { index ->
        val pos = player.currentPosition; serverIndex = index
        player.setMediaItem(ExoMediaItem.Builder().setUri(sources[index].url).build(), pos)
        player.prepare(); player.playWhenReady = true; hasError = null
        toast = "Reproduciendo desde ${sources[index].label}"
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY && player.duration > 0) durationMs = player.duration
                if (playbackState == Player.STATE_ENDED) {
                    if (nextVideo != null && showNextPanel == null && hasError == null) showNextPanel = nextVideo
                    else if (nextVideo == null) toast = "Fin de la reproducción"
                }
            }
            override fun onTracksChanged(currentTracks: Tracks) {
                tracks = currentTracks
                if (!preferenceApplied && currentTracks.groups.isNotEmpty()) {
                    preferenceApplied = true
                    val target = when (SettingsStore.preferredQuality) { "4K" -> 2160; "1080p" -> 1080; "720p" -> 720; "480p" -> 480; else -> 0 }
                    if (target > 0) {
                        var best: TrackChoice? = null; var bestDiff = Int.MAX_VALUE
                        for (group in currentTracks.groups) { if (group.type != C.TRACK_TYPE_VIDEO) continue; for (i in 0 until group.length) { if (!group.isTrackSupported(i)) continue; val h = group.getTrackFormat(i).height; if (h <= 0) continue; val diff = abs(h - target); if (diff < bestDiff) { bestDiff = diff; best = TrackChoice(group.mediaTrackGroup, i, group.getTrackFormat(i)) } } }
                        best?.let { videoChoice = it; applyTrack(C.TRACK_TYPE_VIDEO, it) }
                    }
                    if (SettingsStore.subtitlesDefault) { for (group in currentTracks.groups) { if (group.type != C.TRACK_TYPE_TEXT) continue; for (i in 0 until group.length) { if (!group.isTrackSupported(i)) continue; subtitlesOn = true; textChoice = TrackChoice(group.mediaTrackGroup, i, group.getTrackFormat(i)); applyTrack(C.TRACK_TYPE_TEXT, textChoice, disable = false); break } } }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                val next = serverIndex + 1
                if (next < sources.size) { toast = "Error · cambiando a ${sources[next].label}…"; switchServer(next) } else hasError = error.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            val watched = if (player.duration > 0) player.currentPosition.toFloat() / player.duration else 0f
            if (watched > 0f && hasError == null) ProfileStore.setProgress(video.id, watched)
            player.release()
        }
    }
    LaunchedEffect(player) { while (true) { positionMs = player.currentPosition.coerceAtLeast(0); bufferedMs = player.bufferedPosition.coerceAtLeast(0); if (player.duration > 0) durationMs = player.duration; clock = nowClock(); delay(500) } }
    LaunchedEffect(controlsVisible, isPlaying, menu, showNextPanel) { if (controlsVisible && isPlaying && menu == MenuKind.NONE && showNextPanel == null) { delay(7000); controlsVisible = false; showInfo = false } }
    LaunchedEffect(toast) { if (toast != null) { delay(2800); toast = null } }
    LaunchedEffect(showNextPanel) { val next = showNextPanel; if (next != null && SettingsStore.autoNext) { nextCountdown = 10; while (nextCountdown > 0) { delay(1000); nextCountdown -= 1 }; if (nextCountdown == 0) onPlayNext(next) } else nextCountdown = 0 }
    LaunchedEffect(sleepDeadline) { val d = sleepDeadline; if (d == null) { sleepLabel = "Temporizador"; return@LaunchedEffect }; while (System.currentTimeMillis() < d) { val r = (d - System.currentTimeMillis()).coerceAtLeast(0) / 1000; sleepLabel = String.format(Locale.US, "⏱ %d:%02d", r / 60, r % 60); delay(1000) }; player.pause(); toast = "Temporizador finalizado · vídeo en pausa"; sleepMinutes = 0; sleepDeadline = null }
    LaunchedEffect(controlsVisible, menu) { when { !controlsVisible -> rootFocus.requestFocus(); menu == MenuKind.NONE -> playFocus.requestFocus() } }
    BackHandler { when { showNextPanel != null -> showNextPanel = null; menu != MenuKind.NONE -> menu = MenuKind.NONE; controlsVisible -> { controlsVisible = false; showInfo = false }; else -> onBack() } }

    val qualityOptions = remember(tracks, videoChoice) { buildList { add(PlayerOption("Automática", videoChoice == null) { videoChoice = null; applyTrack(C.TRACK_TYPE_VIDEO, null); menu = MenuKind.NONE }); for (g in tracks.groups) { if (g.type != C.TRACK_TYPE_VIDEO) continue; for (i in 0 until g.length) { if (!g.isTrackSupported(i)) continue; val f = g.getTrackFormat(i); val label = buildString { append(if (f.height > 0) "${f.height}p" else (f.label ?: "Pista ${i + 1}")); if (f.bitrate > 0) append(" · ${f.bitrate / 1000} kbps") }; add(PlayerOption(label, videoChoice == TrackChoice(g.mediaTrackGroup, i, f)) { videoChoice = TrackChoice(g.mediaTrackGroup, i, f); applyTrack(C.TRACK_TYPE_VIDEO, videoChoice); menu = MenuKind.NONE }) } } } }
    val audioOptions = remember(tracks, audioChoice) { buildList { add(PlayerOption("Automático", audioChoice == null) { audioChoice = null; applyTrack(C.TRACK_TYPE_AUDIO, null); menu = MenuKind.NONE }); for (g in tracks.groups) { if (g.type != C.TRACK_TYPE_AUDIO) continue; for (i in 0 until g.length) { if (!g.isTrackSupported(i)) continue; val f = g.getTrackFormat(i); val label = f.label ?: f.language ?: "Pista ${i + 1}"; add(PlayerOption(label, audioChoice == TrackChoice(g.mediaTrackGroup, i, f)) { audioChoice = TrackChoice(g.mediaTrackGroup, i, f); applyTrack(C.TRACK_TYPE_AUDIO, audioChoice); menu = MenuKind.NONE }) } } } }
    val subtitleOptions = remember(tracks, textChoice, subtitlesOn) { buildList { add(PlayerOption("Desactivados", !subtitlesOn) { subtitlesOn = false; textChoice = null; applyTrack(C.TRACK_TYPE_TEXT, null, true); menu = MenuKind.NONE }); for (g in tracks.groups) { if (g.type != C.TRACK_TYPE_TEXT) continue; for (i in 0 until g.length) { if (!g.isTrackSupported(i)) continue; val f = g.getTrackFormat(i); val label = f.label ?: f.language ?: "Subtítulos ${i + 1}"; add(PlayerOption(label, subtitlesOn && textChoice == TrackChoice(g.mediaTrackGroup, i, f)) { subtitlesOn = true; textChoice = TrackChoice(g.mediaTrackGroup, i, f); applyTrack(C.TRACK_TYPE_TEXT, textChoice, false); menu = MenuKind.NONE }) } } } }
    val serverOptions = sources.mapIndexed { idx, s -> PlayerOption("${s.label} · ${if (s.format == StreamFormat.HLS) "HLS" else "MP4"}", idx == serverIndex) { menu = MenuKind.NONE; if (idx != serverIndex) switchServer(idx) } }
    val aspectOptions = AspectMode.entries.map { m -> PlayerOption(m.label, aspectMode == m) { aspectMode = m; menu = MenuKind.NONE } }
    val speedOptions = SettingsStore.SPEED_PRESETS.map { preset -> PlayerOption(SettingsStore.speedLabel(preset), speed == preset) { speed = preset; player.setPlaybackSpeed(preset); SettingsStore.savePlaybackSpeed(preset); menu = MenuKind.NONE; toast = "Velocidad ${SettingsStore.speedLabel(preset)}" } }
    val sleepOptions = listOf(0, 15, 30, 60).map { mins -> PlayerOption(if (mins == 0) "Desactivado" else "$mins min", sleepMinutes == mins) { sleepMinutes = mins; sleepDeadline = if (mins == 0) null else System.currentTimeMillis() + mins * 60_000L; menu = MenuKind.NONE; toast = if (mins == 0) "Temporizador desactivado" else "Temporizador: $mins min" } }
    val qualityLabel = videoChoice?.let { c -> val h = c.format.height; if (h > 0) "${h}p" else "Manual" } ?: "Auto"

    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(rootFocus).focusable().onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> if (!controlsVisible) { controlsVisible = true; true } else false
            Key.DirectionLeft, Key.MediaRewind -> if (!controlsVisible) { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); controlsVisible = true; true } else false
            Key.DirectionRight, Key.MediaFastForward -> if (!controlsVisible) { player.seekTo(player.currentPosition + 10_000); controlsVisible = true; true } else false
            Key.DirectionUp -> if (!controlsVisible) { controlsVisible = true; true } else false
            else -> false
        }
    }) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false; setKeepContentOnPlayerReset(true); isFocusable = false; isFocusableInTouchMode = false } }, update = { it.player = player; it.resizeMode = aspectMode.resizeMode }, modifier = if (aspectMode.ratio > 0f) Modifier.fillMaxWidth().aspectRatio(aspectMode.ratio) else Modifier.fillMaxSize())

        AnimatedVisibility(!isPlaying && !isBuffering && hasError == null && controlsVisible && showNextPanel == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            CenterPlayButton(R.drawable.ic_play, onClick = { player.play() })
        }
        if (isBuffering && hasError == null) {
            GlassPanel(Modifier.align(Alignment.Center)) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LoadingSpinner(size = 22.dp); Spacer(Modifier.width(12.dp))
                    Column { Text("Cargando", style = AppType.bodyStrong.copy(color = Color.White, fontSize = 14.sp)); Text("Preparando la mejor calidad…", style = AppType.caption.copy(color = Color.White.copy(0.60f))) }
                }
            }
        }
        AnimatedVisibility(toast != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)) { InfoChip(toast ?: "") }
        if (hasError != null) {
            GlassPanel(Modifier.align(Alignment.Center).width(560.dp)) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(0.08f)).border(1.dp, Color.White.copy(0.08f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) { Text("!", style = AppType.display.copy(color = Color.White, fontSize = 28.sp)) }
                    Spacer(Modifier.height(14.dp)); Text("No se pudo reproducir", style = AppType.sectionTitle.copy(color = Color.White), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(6.dp)); Text(hasError ?: "Intenta con otro servidor o vuelve más tarde", style = AppType.body.copy(color = Color.White.copy(0.64f)), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(20.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton("Reintentar", R.drawable.ic_server, true, onClick = { hasError = null; player.setMediaItem(ExoMediaItem.Builder().setUri(sources[serverIndex].url).build(), positionMs); player.prepare(); player.playWhenReady = true })
                        PillButton("Volver", R.drawable.ic_back, false, onClick = onBack)
                    }
                }
            }
        }
        if (controlsVisible && hasError == null) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(0.30f), 0.22f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Transparent, 0.78f to Color.Black.copy(0.26f))))
        }
        AnimatedVisibility(controlsVisible && hasError == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().background(PlayerScrimTop).padding(horizontal = AppSpacing.screenMargin, vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TransportButton(R.drawable.ic_back, "Volver", size = 44.dp, onClick = onBack)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(video.title, style = AppType.bodyStrong.copy(color = Color.White, fontSize = 19.sp, lineHeight = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(10.dp))
                                InfoChip(video.quality.takeIf { it.isNotBlank() } ?: "HD"); Spacer(Modifier.width(6.dp))
                                InfoChip(if (video.streamFormat == StreamFormat.HLS) "HLS" else "MP4")
                                if (speed != 1f) { Spacer(Modifier.width(6.dp)); InfoChip(SettingsStore.speedLabel(speed)) }
                                if (sleepDeadline != null) { Spacer(Modifier.width(6.dp)); EpgChip(sleepLabel, Accent) }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                EpgChip("AHORA", LiveAccent); Spacer(Modifier.width(8.dp))
                                Text(video.title, style = AppType.caption.copy(color = Color.White.copy(0.90f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                nextVideo?.let {
                                    Spacer(Modifier.width(12.dp)); Box(Modifier.size(3.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(0.32f))); Spacer(Modifier.width(12.dp))
                                    EpgChip("A CONTINUACIÓN", Color.White.copy(0.38f)); Spacer(Modifier.width(8.dp))
                                    Text(it.title, style = AppType.caption.copy(color = Color.White.copy(0.58f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(clock, style = AppType.bodyStrong.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 17.sp, color = Color.White))
                            Spacer(Modifier.height(2.dp))
                            Text("EN VIVO", style = AppType.overline.copy(color = LiveAccent, fontSize = 10.sp), modifier = Modifier.background(LiveAccent.copy(0.14f), AppRadius.xs).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (showInfo) {
                    GlassPanel(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenMargin, vertical = 10.dp)) {
                        Row(Modifier.padding(18.dp)) {
                            AsyncImage(video.cardUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.width(200.dp).height(112.dp).clip(AppRadius.md))
                            Spacer(Modifier.width(18.dp))
                            Column {
                                Text(video.title, style = AppType.bodyStrong.copy(color = Color.White, fontSize = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(6.dp)); MetaLine(video)
                                Spacer(Modifier.height(8.dp)); Text(video.description, style = AppType.metadata.copy(color = TextSecondary, lineHeight = 18.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { InfoChip(sources[serverIndex].label); InfoChip(video.language.takeIf { it.isNotBlank() } ?: "—"); InfoChip(video.certification.takeIf { it.isNotBlank() } ?: "TP") }
                            }
                        }
                    }
                }
                showNextPanel?.let { next ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenMargin, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                        GlassPanel(Modifier.width(560.dp)) {
                            Column(Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        AsyncImage(next.cardUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.width(148.dp).height(84.dp).clip(AppRadius.md).background(Color.Black))
                                        if (SettingsStore.autoNext) {
                                            androidx.compose.foundation.Canvas(Modifier.matchParentSize().padding(6.dp)) {
                                                drawArc(Color.White.copy(0.20f), -90f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                                                val sweep = (nextCountdown / 10f) * 360f; drawArc(Accent, -90f, sweep, false, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) { EpgChip("A CONTINUACIÓN", LiveAccent); Spacer(Modifier.height(6.dp)); Text(next.title, style = AppType.bodyStrong.copy(color = Color.White, fontSize = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(4.dp)); Text(if (SettingsStore.autoNext) "Empieza en $nextCountdown s · Auto" else "Listo para reproducir", style = AppType.caption.copy(color = Color.White.copy(0.66f))) }
                                    if (SettingsStore.autoNext) Box(Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text("$nextCountdown", style = AppType.bodyStrong.copy(color = OnLight, fontSize = 16.sp)) }
                                }
                                Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PillButton("Reproducir ahora", R.drawable.ic_play, true, onClick = { showNextPanel = null; onPlayNext(next) })
                                    PillButton("Cancelar", R.drawable.ic_back, false, onClick = { showNextPanel = null })
                                }
                            }
                        }
                    }
                }
                if (menu != MenuKind.NONE) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenMargin, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
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
                Column(Modifier.fillMaxWidth().background(PlayerScrimBottom).padding(horizontal = AppSpacing.screenMargin, vertical = 20.dp)) {
                    PlayerTimeline(positionMs, durationMs, bufferedMs, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TransportButton(R.drawable.ic_rewind, "Retroceder 10s", onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) })
                        TransportButton(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play, if (isPlaying) "Pausar" else "Reproducir", size = 62.dp, primary = true, onClick = { if (isPlaying) player.pause() else player.play() }, modifier = Modifier.focusRequester(playFocus))
                        TransportButton(R.drawable.ic_forward, "Avanzar 10s", onClick = { player.seekTo(player.currentPosition + 10_000) })
                        if (nextVideo != null) TransportButton(R.drawable.ic_skip_next, "Siguiente", onClick = { showNextPanel = null; onPlayNext(nextVideo) })
                        Spacer(Modifier.weight(1f))
                        ControlPill(R.drawable.ic_server, sources[serverIndex].label, active = menu == MenuKind.SERVER, onClick = { menu = if (menu == MenuKind.SERVER) MenuKind.NONE else MenuKind.SERVER })
                        ControlPill(R.drawable.ic_quality, qualityLabel, active = menu == MenuKind.QUALITY, onClick = { menu = if (menu == MenuKind.QUALITY) MenuKind.NONE else MenuKind.QUALITY })
                        ControlPill(R.drawable.ic_audio, "Audio", active = menu == MenuKind.AUDIO, onClick = { menu = if (menu == MenuKind.AUDIO) MenuKind.NONE else MenuKind.AUDIO })
                        ControlPill(R.drawable.ic_subtitles, "Subtítulos", active = subtitlesOn || menu == MenuKind.SUBTITLES, onClick = { menu = if (menu == MenuKind.SUBTITLES) MenuKind.NONE else MenuKind.SUBTITLES })
                        ControlPill(R.drawable.ic_aspect, aspectMode.label, active = menu == MenuKind.ASPECT, onClick = { menu = if (menu == MenuKind.ASPECT) MenuKind.NONE else MenuKind.ASPECT })
                        ControlPill(R.drawable.ic_quality, SettingsStore.speedLabel(speed), active = menu == MenuKind.SPEED, onClick = { menu = if (menu == MenuKind.SPEED) MenuKind.NONE else MenuKind.SPEED })
                        ControlPill(R.drawable.ic_server, sleepLabel, active = sleepDeadline != null || menu == MenuKind.SLEEP, onClick = { menu = if (menu == MenuKind.SLEEP) MenuKind.NONE else MenuKind.SLEEP })
                        ControlPill(R.drawable.ic_info, "Info", active = showInfo, onClick = { showInfo = !showInfo })
                    }
                }
            }
        }
    }
}

private fun fontSize20() = AppType.body
private fun fontSize13() = AppType.metadata
