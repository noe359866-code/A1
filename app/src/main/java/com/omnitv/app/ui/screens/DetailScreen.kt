package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material.Text
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.Episode
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.Video
import com.omnitv.app.data.VideoType
import com.omnitv.app.ui.components.CircleIconButton
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AccentGreen
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.GlassFillStrong
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import com.omnitv.app.ui.theme.TextTertiary

/**
 * Ficha de detalle estilo Netflix, transparente:
 *
 * - El **tráiler se reproduce de fondo** en bucle y silenciado (como los
 *   previews de Netflix), con botón para activar el sonido.
 * - Toda la interfaz (título, metadatos, botones, episodios) flota encima
 *   sobre velos de degradado que mantienen la legibilidad.
 * - Series y anime muestran su lista de **episodios** reproducibles.
 */
@Composable
fun DetailScreen(
    video: Video,
    onBack: () -> Unit,
    onPlay: (Video) -> Unit,
    onOpenDetail: (Video) -> Unit
) {
    val context = LocalContext.current
    val related = remember(video.id) { CatalogRepository.related(video.id) }
    var muted by remember { mutableStateOf(true) }

    // Título a reproducir: primer episodio en series/anime, el vídeo en películas
    val playTarget = remember(video.id) {
        val firstEpisode = video.episodes.firstOrNull()
        if (firstEpisode != null) CatalogRepository.episodeVideo(video, firstEpisode) else video
    }

    // ---------------- Tráiler de fondo (Media3, en bucle, silenciado) ----------------
    val trailerPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ false // el tráiler no roba el foco de audio
            )
            setMediaItem(ExoMediaItem.Builder().setUri(video.videoUrl).build())
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose { trailerPlayer.release() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // 1 · Tráiler a pantalla completa
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = trailerPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            update = { it.player = trailerPlayer },
            modifier = Modifier.fillMaxSize()
        )

        // 2 · Velos de degradado para legibilidad (la interfaz es transparente)
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

        // 3 · Contenido transparente encima del tráiler
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Zona superior: el tráiler se ve a la izquierda con la info encima
            Spacer(Modifier.height(190.dp))
            Column(
                Modifier
                    .padding(horizontal = 48.dp)
                    .widthIn(max = 720.dp)
            ) {
                // Chip de tipo + badge
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TypeChip(video.type)
                    video.badge?.let { AccentChip(it) }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    video.title,
                    style = AppType.hero,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(16.dp))
                // Metadatos: match % estilo Netflix + año + duración/episodios + calidad
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${video.matchPercent} % para ti",
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = AccentGreen
                    )
                    Dot()
                    Text("${video.year}", style = AppType.metadata, color = TextSecondary)
                    Dot()
                    Text(
                        video.episodesLabel ?: video.durationLabel,
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                    Dot()
                    Text(video.quality, style = AppType.metadata, color = TextSecondary)
                    Dot()
                    Text(video.certification, style = AppType.metadata, color = TextSecondary)
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    video.description,
                    style = AppType.body,
                    color = TextPrimary.copy(alpha = 0.92f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 620.dp)
                )

                Spacer(Modifier.height(26.dp))
                // Botones: Reproducir · Mi lista · sonido del tráiler
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PillButton(
                        text = "Reproducir",
                        iconRes = R.drawable.ic_play,
                        primary = true,
                        onClick = { onPlay(playTarget) }
                    )
                    val inMyList = ProfileStore.currentProfile?.myList?.contains(video.id) == true
                    PillButton(
                        text = if (inMyList) "En Mi lista" else "Mi lista",
                        iconRes = if (inMyList) R.drawable.ic_check else R.drawable.ic_add,
                        primary = false,
                        onClick = { ProfileStore.toggleMyList(video.id) }
                    )
                    PillButton(
                        text = if (muted) "Sonido del tráiler" else "Silenciar",
                        iconRes = if (muted) R.drawable.ic_volume_off else R.drawable.ic_audio,
                        primary = false,
                        onClick = {
                            muted = !muted
                            trailerPlayer.volume = if (muted) 0f else 1f
                        }
                    )
                }
            }

            Spacer(Modifier.height(44.dp))

            Column(Modifier.padding(horizontal = 48.dp)) {
                // ---------------- Episodios (series y anime) ----------------
                if (video.episodes.isNotEmpty()) {
                    Text("Episodios", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        video.episodesLabel ?: "Temporada 1",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        video.episodes.forEach { episode ->
                            EpisodeRow(
                                episode = episode,
                                onClick = { onPlay(CatalogRepository.episodeVideo(video, episode)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(36.dp))
                }

                // ---------------- Reparto ----------------
                if (video.cast.isNotEmpty()) {
                    SectionLabel("Reparto")
                    LazyRow(
                        contentPadding = PaddingValues(end = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(video.cast) { name -> InfoChip(name) }
                    }
                    Spacer(Modifier.height(28.dp))
                }

                // ---------------- Géneros ----------------
                SectionLabel("Géneros")
                LazyRow(
                    contentPadding = PaddingValues(end = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(video.genres) { genre -> InfoChip(genre) }
                }
                Spacer(Modifier.height(28.dp))

                // ---------------- Información ----------------
                SectionLabel("Información")
                InfoRow("Idioma", video.language)
                InfoRow("Calidad", video.quality)
                InfoRow("Formato", video.streamFormat.name)

                // ---------------- Más como esto ----------------
                if (related.isNotEmpty()) {
                    Spacer(Modifier.height(36.dp))
                    Text("Más como esto", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(18.dp))
                    LazyRow(
                        contentPadding = PaddingValues(end = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(related, key = { it.id }) { item ->
                            PosterCard(item, onClick = { onOpenDetail(item) })
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
                .padding(28.dp)
        )

        // Indicador de tráiler (arriba a la derecha)
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

/* -------------------------------------------------------------------------- */
/*  Piezas de la ficha                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun TypeChip(type: VideoType) {
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
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
            .clip(RoundedCornerShape(5.dp))
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
            .clip(RoundedCornerShape(9.dp))
            .background(GlassFillStrong)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, style = AppType.metadata, color = TextPrimary)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = AppType.sectionTitle, color = TextPrimary)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(bottom = 10.dp)) {
        Text("$label: ", style = AppType.metadata, color = TextSecondary, modifier = Modifier.width(110.dp))
        Text(value, style = AppType.metadata, color = TextPrimary)
    }
}

@Composable
private fun Dot() {
    Box(
        Modifier
            .padding(horizontal = 10.dp)
            .size(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(TextTertiary)
    )
}

/** Fila de episodio estilo Netflix (miniatura + número + sinopsis). */
@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (focused) 1.02f else 1f,
        androidx.compose.animation.core.tween(150),
        label = "episodeScale"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) SurfaceElevated.copy(alpha = 0.85f) else GlassFill)
            .then(
                if (focused) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${episode.number}",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            ),
            color = if (focused) TextPrimary else TextSecondary,
            modifier = Modifier.width(34.dp)
        )
        Spacer(Modifier.width(14.dp))
        Box {
            AsyncImage(
                model = episode.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(168.dp)
                    .height(94.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", style = AppType.metadata, color = Color.White)
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    episode.title,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
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
