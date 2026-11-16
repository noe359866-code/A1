@file:kotlin.OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.screens

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.Episode
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.Video
import com.omnitv.app.data.VideoType
import com.omnitv.app.ui.components.CircleIconButton
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.ProvideHorizontalTvBringIntoView
import com.omnitv.app.ui.components.TvChip
import com.omnitv.app.ui.components.dpadRepeatThrottle
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AccentGreen
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.RatingGold
import com.omnitv.app.ui.theme.SurfaceCard
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import com.omnitv.app.ui.theme.TextTertiary
import java.util.Calendar
import java.util.Locale

/**
 * Ficha de detalle estilo Netflix, transparente:
 *
 * - El **tráiler se reproduce de fondo** en bucle y silenciado (como los
 *   previews de Netflix), con botón para activar el sonido.
 * - Mantiene temporada seleccionada y foco del episodio/elemento al volver
 *   desde el reproductor.
 */
@OptIn(UnstableApi::class)
@Composable
fun DetailScreen(
    video: Video,
    onBack: () -> Unit,
    onPlay: (Video) -> Unit,
    onOpenDetail: (Video) -> Unit
) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var full by remember(video.id) { mutableStateOf(video) }
    LaunchedEffect(video.id) {
        if (video.isRemote && video.episodes.isEmpty()) {
            RemoteCatalog.fetchDetail(video)?.let { full = it }
        }
    }

    val related = remember(full.id, full.episodes.size) { CatalogRepository.related(full.id) }
    var muted by remember { mutableStateOf(true) }
    var selectedSeason by rememberSaveable(video.id) { mutableStateOf(1) }

    val playFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    var lastFocusedKey by rememberSaveable(video.id) { mutableStateOf<String?>(null) }
    var initialFocusDone by rememberSaveable(video.id) { mutableStateOf(false) }
    val pendingRestoreKey = remember { lastFocusedKey }
    var mountRestoreDone by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(video.id, full.episodes.size) {
        if (mountRestoreDone) return@LaunchedEffect
        if (pendingRestoreKey != null && pendingRestoreKey != "play") {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { restoreFocus.requestFocus() }.isSuccess) {
                    mountRestoreDone = true
                    return@LaunchedEffect
                }
            }
            if (runCatching { playFocus.requestFocus() }.isSuccess) {
                initialFocusDone = true
                mountRestoreDone = true
            }
        } else if (!initialFocusDone || pendingRestoreKey == "play") {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { playFocus.requestFocus() }.isSuccess) {
                    initialFocusDone = true
                    mountRestoreDone = true
                    return@LaunchedEffect
                }
            }
        }
    }

    val playTarget = remember(full.id, full.episodes.size) {
        val firstEpisode = full.episodes.firstOrNull()
        if (firstEpisode != null) CatalogRepository.episodeVideo(full, firstEpisode) else full
    }

    // ---------------- Tráiler de fondo (Media3, en bucle, silenciado) ----------------
    // Liberar en ON_STOP para no mantener un decodificador MediaCodec hardware
    // ocupado en segundo plano mientras PlayerScreen reproduce otro vídeo.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScreenStarted by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isScreenStarted = true
                Lifecycle.Event.ON_STOP -> isScreenStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        isScreenStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val trailerPlayer = remember(full.videoUrl, isScreenStarted) {
        if (full.videoUrl.isEmpty() || !isScreenStarted) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    false
                )
                setMediaItem(ExoMediaItem.Builder().setUri(full.videoUrl).build())
                volume = if (muted) 0f else 1f
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                prepare()
            }
        }
    }
    DisposableEffect(trailerPlayer) {
        onDispose {
            trailerPlayer?.apply {
                playWhenReady = false
                stop()
                clearMediaItems()
                clearVideoSurface()
                release()
            }
        }
    }
    DisposableEffect(lifecycleOwner, trailerPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> trailerPlayer?.pause()
                Lifecycle.Event.ON_RESUME -> trailerPlayer?.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .dpadRepeatThrottle()
    ) {
        if (trailerPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = trailerPlayer
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        isFocusable = false
                        isFocusableInTouchMode = false
                        isClickable = false
                        isLongClickable = false
                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                },
                update = { it.player = trailerPlayer },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = full.backdropUrl.ifEmpty { full.cardUrl },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Background.copy(alpha = 0.45f),
                        0.30f to Background.copy(alpha = 0.15f),
                        0.62f to Background.copy(alpha = 0.88f),
                        0.85f to Background
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Background.copy(alpha = 0.85f),
                        0.42f to Background.copy(alpha = 0.25f),
                        0.75f to Color.Transparent
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(screenHeight * 0.30f))
            Column(
                Modifier
                    .padding(horizontal = AppSpacing.screenMargin)
                    .widthIn(max = 720.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TypeChip(full.type)
                    full.badge?.let { AccentChip(it) }
                    if (full.genres.isNotEmpty()) {
                        Text(
                            full.genres.take(3).joinToString(" · "),
                            style = AppType.metadata,
                            color = TextPrimary.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    full.title,
                    style = AppType.hero,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var hasBefore = false
                    if (full.hasRating) {
                        Text("★", style = AppType.metadata, color = RatingGold)
                        Spacer(Modifier.width(4.dp))
                        Text(full.ratingLabel, style = AppType.controlLabel, color = TextPrimary)
                        Dot()
                        Text(
                            "${full.matchPercent} % para ti",
                            style = AppType.controlLabel,
                            color = AccentGreen
                        )
                        hasBefore = true
                    }
                    val finishTime = if (full.episodes.isEmpty()) estimatedFinishLabel(full.durationMinutes) else null
                    val metaParts = listOfNotNull(
                        full.year.takeIf { it > 0 }?.toString(),
                        full.episodesLabel ?: full.durationLabel.takeIf { full.durationMinutes > 0 },
                        finishTime,
                        full.quality.takeIf { it.isNotBlank() },
                        full.certification.takeIf { it.isNotBlank() }
                    )
                    metaParts.forEach { part ->
                        if (hasBefore) Dot()
                        Text(part, style = AppType.metadata, color = TextSecondary)
                        hasBefore = true
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    full.description,
                    style = AppType.body,
                    color = TextPrimary.copy(alpha = 0.92f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 620.dp)
                )

                Spacer(Modifier.height(26.dp))
                val firstEpisode = full.episodes.firstOrNull()
                val playLabel = if (firstEpisode != null) {
                    "Reproducir T${firstEpisode.season}·E${firstEpisode.number}"
                } else {
                    "Reproducir"
                }
                Row(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PillButton(
                        text = playLabel,
                        iconRes = R.drawable.ic_play,
                        primary = true,
                        onFocus = { lastFocusedKey = "play" },
                        onClick = {
                            lastFocusedKey = "play"
                            onPlay(playTarget)
                        },
                        modifier = Modifier.focusRequester(playFocus)
                    )
                    val inMyList = ProfileStore.currentProfile?.myList?.contains(full.id) == true
                    PillButton(
                        text = if (inMyList) "En Mi lista" else "Mi lista",
                        iconRes = if (inMyList) R.drawable.ic_check else R.drawable.ic_add,
                        primary = false,
                        onFocus = { lastFocusedKey = "mylist" },
                        onClick = { ProfileStore.toggleMyList(full.id) },
                        modifier = if (pendingRestoreKey == "mylist") Modifier.focusRequester(restoreFocus) else Modifier
                    )
                    if (trailerPlayer != null) {
                        PillButton(
                            text = if (muted) "Sonido del tráiler" else "Silenciar",
                            iconRes = if (muted) R.drawable.ic_volume_off else R.drawable.ic_audio,
                            primary = false,
                            onFocus = { lastFocusedKey = "mute" },
                            onClick = {
                                muted = !muted
                                trailerPlayer.volume = if (muted) 0f else 1f
                            },
                            modifier = if (pendingRestoreKey == "mute") Modifier.focusRequester(restoreFocus) else Modifier
                        )
                    }
                }
            }

            Spacer(Modifier.height(44.dp))

            Column(Modifier.fillMaxWidth()) {
                // ---------------- Episodios (series y anime) ----------------
                if (full.episodes.isNotEmpty()) {
                    val seasons = full.episodes.map { it.season }.distinct().sorted()
                    val currentSeason = if (selectedSeason in seasons) selectedSeason else seasons.first()
                    val seasonEpisodes = full.episodes.filter { it.season == currentSeason }

                    Text(
                        "Episodios",
                        style = AppType.sectionTitle,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = AppSpacing.screenMargin)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (seasons.size > 1) "Temporada $currentSeason · ${seasonEpisodes.size} episodios"
                        else full.episodesLabel ?: "Temporada 1",
                        style = AppType.metadata,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = AppSpacing.screenMargin)
                    )

                    if (seasons.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = AppSpacing.screenMargin, vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer()
                        ) {
                            items(seasons) { season ->
                                TvChip(
                                    text = "Temporada $season",
                                    selected = season == currentSeason,
                                    onClick = { selectedSeason = season }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    var visibleEpisodes by rememberSaveable(video.id, currentSeason) { mutableStateOf(20) }
                    val shownEpisodes = seasonEpisodes.take(visibleEpisodes)
                    Column(
                        modifier = Modifier.padding(horizontal = AppSpacing.screenMargin),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        shownEpisodes.forEach { episode ->
                            val epKey = "ep:${episode.season}:${episode.number}"
                            EpisodeRow(
                                episode = episode,
                                onFocus = { lastFocusedKey = epKey },
                                onClick = {
                                    lastFocusedKey = epKey
                                    onPlay(CatalogRepository.episodeVideo(full, episode))
                                },
                                modifier = if (pendingRestoreKey == epKey) {
                                    Modifier.focusRequester(restoreFocus)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                    if (seasonEpisodes.size > shownEpisodes.size) {
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.padding(horizontal = AppSpacing.screenMargin)) {
                            TvChip(
                                text = "Mostrar más episodios (${seasonEpisodes.size - shownEpisodes.size})",
                                selected = false,
                                onClick = { visibleEpisodes += 20 }
                            )
                        }
                    }
                    Spacer(Modifier.height(36.dp))
                }

                // ---------------- Reparto ----------------
                if (full.cast.isNotEmpty()) {
                    Box(Modifier.padding(horizontal = AppSpacing.screenMargin)) { SectionLabel("Reparto") }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = AppSpacing.screenMargin),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(full.cast) { name -> InfoChip(name) }
                    }
                    Spacer(Modifier.height(28.dp))
                }

                // ---------------- Géneros ----------------
                if (full.genres.isNotEmpty()) {
                    Box(Modifier.padding(horizontal = AppSpacing.screenMargin)) { SectionLabel("Géneros") }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = AppSpacing.screenMargin),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(full.genres) { genre -> InfoChip(genre) }
                    }
                    Spacer(Modifier.height(28.dp))
                }

                // ---------------- Información ----------------
                Column(Modifier.padding(horizontal = AppSpacing.screenMargin)) {
                    SectionLabel("Información")
                    Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .clip(AppRadius.md)
                            .background(SurfaceCard)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), AppRadius.md)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InfoRow("Formato", full.type.label)
                        if (full.year > 0) InfoRow("Año", full.year.toString())
                        if (full.language.isNotEmpty()) InfoRow("Idioma / país", full.language)
                        if (full.quality.isNotEmpty()) InfoRow("Calidad", full.quality)
                        if (full.certification.isNotEmpty()) InfoRow("Clasificación", full.certification)
                        val spanLabel = full.episodesLabel ?: full.durationLabel.takeIf { full.durationMinutes > 0 }
                        if (!spanLabel.isNullOrBlank()) InfoRow("Duración", spanLabel)
                    }
                }

                // ---------------- Más como esto ----------------
                if (related.isNotEmpty()) {
                    Spacer(Modifier.height(36.dp))
                    Text(
                        "Más como esto",
                        style = AppType.sectionTitle,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = AppSpacing.screenMargin)
                    )
                    Spacer(Modifier.height(6.dp))
                    ProvideHorizontalTvBringIntoView {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = AppSpacing.screenMargin, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusGroup()
                                .focusRestorer()
                        ) {
                            items(related, key = { it.id }) { item ->
                                val relKey = "related:${item.id}"
                                PosterCard(
                                    video = item,
                                    onFocus = { lastFocusedKey = relKey },
                                    onClick = {
                                        lastFocusedKey = relKey
                                        onOpenDetail(item)
                                    },
                                    modifier = Modifier
                                        .width(156.dp)
                                        .then(
                                            if (pendingRestoreKey == relKey) Modifier.focusRequester(restoreFocus)
                                            else Modifier
                                        )
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(56.dp))
            }
        }

        // Volver (arriba a la izquierda)
        CircleIconButton(
            iconRes = R.drawable.ic_back,
            contentDescription = "Volver",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(AppSpacing.screenMargin)
        )

        // Estado del tráiler (arriba a la derecha, solo cuando hay tráiler local)
        if (!full.isRemote) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccentChip(if (muted) "TRÁILER · SILENCIADO" else "TRÁILER")
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Piezas de la ficha                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun TypeChip(type: VideoType) {
    Box(
        Modifier
            .clip(AppRadius.xs)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            type.label.uppercase(),
            style = AppType.chip,
            color = TextPrimary
        )
    }
}

@Composable
private fun AccentChip(text: String) {
    Box(
        Modifier
            .clip(AppRadius.xs)
            .background(Accent)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = AppType.chip, color = Color.White)
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(
        Modifier
            .clip(AppRadius.pill)
            .background(SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.10f), AppRadius.pill)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, style = AppType.metadata, color = TextPrimary)
    }
}

private fun estimatedFinishLabel(durationMinutes: Int): String? {
    if (durationMinutes <= 0) return null
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, durationMinutes)
    return String.format(
        Locale.US,
        "Termina ~%02d:%02d",
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = AppType.sectionTitle, color = TextPrimary)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppType.metadata, color = TextSecondary, modifier = Modifier.width(150.dp))
        Text(value, style = AppType.metadata, color = TextPrimary)
    }
}

@Composable
private fun Dot() {
    Box(
        Modifier
            .padding(horizontal = 10.dp)
            .size(3.dp)
            .clip(AppRadius.xs)
            .background(TextTertiary)
    )
}

/** Fila de episodio estilo Netflix (miniatura + número + sinopsis). */
@Composable
private fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.02f else 1f,
        tween(150),
        label = "episodeScale"
    )

    Row(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.md)
            .background(if (focused) SurfaceElevated.copy(alpha = 0.92f) else GlassFill)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.md)
                else Modifier
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${episode.number}",
            style = AppType.sectionTitle,
            color = if (focused) TextPrimary else TextSecondary,
            modifier = Modifier.width(34.dp)
        )
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .width(168.dp)
                .height(94.dp)
                .clip(AppRadius.sm)
                .background(SurfaceElevated)
        ) {
            AsyncImage(
                model = episode.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = if (focused) 0.78f else 0.56f))
                    .border(1.dp, Color.White.copy(alpha = if (focused) 0.85f else 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    episode.title,
                    style = AppType.cardHeading,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                Text(episode.durationLabel, style = AppType.metadata, color = TextSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                episode.description,
                style = AppType.metadata,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
