package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.material.Text
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
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.TvChip
import com.omnitv.app.ui.theme.*

@Composable
fun DetailScreen(video: Video, onBack: () -> Unit, onPlay: (Video) -> Unit, onOpenDetail: (Video) -> Unit) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var full by remember(video.id) { mutableStateOf(video) }
    LaunchedEffect(video.id) { if (video.isRemote && video.episodes.isEmpty()) RemoteCatalog.fetchDetail(video)?.let { full = it } }
    val related = remember(full.id, full.episodes.size) { CatalogRepository.related(full.id) }
    var muted by remember { mutableStateOf(true) }
    var selectedSeason by remember { mutableStateOf(1) }
    val playFocus = remember { FocusRequester() }
    RequestTvFocus(playFocus, key = video.id)
    val playTarget = remember(full.id, full.episodes.size) {
        val ep = full.episodes.firstOrNull()
        if (ep != null) CatalogRepository.episodeVideo(full, ep) else full
    }
    val trailerPlayer = remember(full.videoUrl) {
        if (full.videoUrl.isEmpty()) null else ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(), false)
            setMediaItem(ExoMediaItem.Builder().setUri(full.videoUrl).build())
            volume = 0f; repeatMode = Player.REPEAT_MODE_ALL; playWhenReady = true; prepare()
        }
    }
    DisposableEffect(trailerPlayer) { onDispose { trailerPlayer?.release() } }

    Box(Modifier.fillMaxSize().background(Background)) {
        // Fondo — tráiler o backdrop con velos editoriales
        if (trailerPlayer != null) {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = trailerPlayer; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; isFocusable = false } }, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(model = full.backdropUrl.ifEmpty { full.cardUrl }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Background.copy(alpha = 0.35f), 0.28f to Background.copy(alpha = 0.08f), 0.62f to Background.copy(alpha = 0.88f), 1f to Background)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Background.copy(alpha = 0.82f), 0.42f to Background.copy(alpha = 0.18f), 0.75f to Color.Transparent)))

        // Contenido
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(screenHeight * 0.30f))
            Column(Modifier.padding(horizontal = AppSpacing.screenMargin).widthIn(max = 760.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeChip(full.type)
                    full.badge?.let { AccentChip(it) }
                    if (full.hasRating) MatchPill(full.matchPercent)
                }
                Spacer(Modifier.height(14.dp))
                Text(full.title, style = AppType.hero, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                DetailMeta(full)
                Spacer(Modifier.height(18.dp))
                Text(full.description, style = AppType.body, color = Color.White.copy(alpha = 0.90f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 620.dp))
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Reproducir", R.drawable.ic_play, true, onClick = { onPlay(playTarget) }, modifier = Modifier.focusRequester(playFocus))
                    val inMyList = ProfileStore.currentProfile?.myList?.contains(full.id) == true
                    PillButton(if (inMyList) "En Mi lista" else "Mi lista", if (inMyList) R.drawable.ic_check else R.drawable.ic_add, false, onClick = { ProfileStore.toggleMyList(full.id) })
                    if (trailerPlayer != null) {
                        PillButton(if (muted) "Sonido" else "Silenciar", if (muted) R.drawable.ic_volume_off else R.drawable.ic_audio, false, onClick = { muted = !muted; trailerPlayer.volume = if (muted) 0f else 1f })
                    }
                }
                if (full.isRemote) {
                    Spacer(Modifier.height(14.dp))
                    Text("Origen: ${full.source} · ${full.remoteType ?: full.type.label.lowercase()}  ·  Addons en Ajustes", style = AppType.caption, color = TextMuted)
                }
            }
            Spacer(Modifier.height(40.dp))
            Column(Modifier.padding(horizontal = AppSpacing.screenMargin)) {
                if (full.episodes.isNotEmpty()) {
                    val seasons = full.episodes.map { it.season }.distinct().sorted()
                    val cur = if (selectedSeason in seasons) selectedSeason else seasons.first()
                    val eps = full.episodes.filter { it.season == cur }
                    Text("Episodios", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(if (seasons.size > 1) "Temporada $cur · ${eps.size} episodios" else full.episodesLabel ?: "Temporada 1", style = AppType.metadata, color = TextSecondary)
                    if (seasons.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(seasons) { s -> TvChip("Temporada $s", selected = s == cur, onClick = { selectedSeason = s }) } }
                    }
                    Spacer(Modifier.height(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { eps.forEach { ep -> EpisodeRow(episode = ep, onClick = { onPlay(CatalogRepository.episodeVideo(full, ep)) }) } }
                    Spacer(Modifier.height(32.dp))
                    Divider()
                    Spacer(Modifier.height(32.dp))
                }
                if (full.cast.isNotEmpty()) {
                    SectionLabel("Reparto")
                    LazyRow(contentPadding = PaddingValues(end = AppSpacing.screenMargin), horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(full.cast) { n -> InfoChip(n) } }
                    Spacer(Modifier.height(24.dp))
                }
                if (full.genres.isNotEmpty()) {
                    SectionLabel("Géneros")
                    LazyRow(contentPadding = PaddingValues(end = AppSpacing.screenMargin), horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(full.genres) { g -> InfoChip(g) } }
                    Spacer(Modifier.height(24.dp))
                }
                SectionLabel("Información")
                if (full.isRemote) InfoRow("Fuente", "${full.source} (${full.remoteType ?: full.type.name.lowercase()})")
                if (full.language.isNotEmpty()) InfoRow("Idioma", full.language)
                InfoRow("Calidad", full.quality)
                if (related.isNotEmpty()) {
                    Spacer(Modifier.height(36.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Más como esto", style = AppType.sectionTitle, color = TextPrimary)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.clip(AppRadius.pill).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 6.dp)) { Text("${related.size}", style = AppType.chip, color = TextSecondary) }
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyRow(contentPadding = PaddingValues(end = AppSpacing.screenMargin), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(related, key = { it.id }) { item -> PosterCard(item, onClick = { onOpenDetail(item) }, modifier = Modifier.size(220.dp, 124.dp)) }
                    }
                }
                Spacer(Modifier.height(56.dp))
            }
        }
        CircleIconButton(R.drawable.ic_back, "Volver", onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(AppSpacing.screenMargin))
        Box(Modifier.align(Alignment.TopEnd).padding(24.dp).clip(AppRadius.xs).background(if (full.isRemote) Accent else Color.White.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(if (full.isRemote) "CATÁLOGO · ${full.source.uppercase()}" else if (muted) "TRÁILER · SILENCIADO" else "TRÁILER", style = AppType.chip, color = Color.White)
        }
    }
}

@Composable private fun TypeChip(type: VideoType) { Box(Modifier.clip(AppRadius.xs).background(Color.White.copy(alpha = 0.14f)).border(1.dp, GlassBorder, AppRadius.xs).padding(horizontal = 10.dp, vertical = 4.dp)) { Text(type.label.uppercase(), style = AppType.chip, color = TextPrimary) } }
@Composable private fun AccentChip(text: String) { Box(Modifier.clip(AppRadius.xs).background(Accent).padding(horizontal = 10.dp, vertical = 4.dp)) { Text(text.uppercase(), style = AppType.chip, color = Color.White) } }
@Composable private fun MatchPill(percent: Int) { Box(Modifier.clip(AppRadius.xs).background(Brush.linearGradient(listOf(Color(0xFF30D158), Color(0xFF00C7BE)))).padding(horizontal = 10.dp, vertical = 4.dp)) { Text("$percent% para ti", style = AppType.chip, color = Color.White) } }
@Composable private fun InfoChip(text: String) { Box(Modifier.clip(AppRadius.sm).background(GlassFillStrong).border(1.dp, GlassBorder, AppRadius.sm).padding(horizontal = 14.dp, vertical = 8.dp)) { Text(text, style = AppType.metadata, color = TextPrimary) } }
@Composable private fun SectionLabel(text: String) { Text(text, style = AppType.sectionTitle, color = TextPrimary); Spacer(Modifier.height(12.dp)) }
@Composable private fun InfoRow(label: String, value: String) { Row(Modifier.padding(bottom = 8.dp)) { Text("$label", style = AppType.caption, color = TextSecondary, modifier = Modifier.width(140.dp)); Text(value, style = AppType.metadata, color = TextPrimary) } }
@Composable private fun Dot() { Box(Modifier.padding(horizontal = 10.dp).size(3.dp).clip(AppRadius.xs).background(TextTertiary)) }
@Composable private fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f))) }
@Composable private fun DetailMeta(video: Video) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (video.hasRating) { Text("${video.matchPercent}% para ti", style = AppType.match, color = AccentGreen); Dot() }
        val parts = listOfNotNull(video.year.takeIf { it > 0 }?.toString(), video.episodesLabel ?: video.durationLabel.takeIf { video.durationMinutes > 0 }, video.quality.takeIf { it.isNotBlank() }, video.certification.takeIf { it.isNotBlank() })
        parts.forEachIndexed { i, p -> if (i > 0) Dot(); Text(p, style = AppType.metadata, color = TextSecondary) }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.02f else 1f, tween(150), label = "epScale")
    Row(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.clip(AppRadius.md)
            .background(if (focused) SurfaceElevated else GlassFill).border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.06f), AppRadius.md)
            .clickable(remember { MutableInteractionSource() }, null, onClick).onFocusChanged { focused = it.isFocused }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (focused) Accent else Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text("${episode.number}", style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.Bold, 16.sp), color = if (focused) Color.White else TextSecondary)
        }
        Spacer(Modifier.width(14.dp))
        Box {
            AsyncImage(model = episode.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(160.dp).height(90.dp).clip(AppRadius.sm))
            Box(Modifier.align(Alignment.Center).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) { Text("▶", style = AppType.metadata, color = Color.White) }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(episode.title, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 19.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(episode.durationLabel, style = AppType.caption, color = TextTertiary)
            }
            Spacer(Modifier.height(4.dp))
            Text(episode.description, style = AppType.caption, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
