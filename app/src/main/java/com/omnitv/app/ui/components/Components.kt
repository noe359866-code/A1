@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.Text
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.MediaRow
import com.omnitv.app.data.Video
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AccentHover
import com.omnitv.app.ui.theme.AppElevation
import com.omnitv.app.ui.theme.AppMotion
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.Danger
import com.omnitv.app.ui.theme.GlassBorder
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.GlassFillStrong
import com.omnitv.app.ui.theme.GradientTopBar
import com.omnitv.app.ui.theme.OnLight
import com.omnitv.app.ui.theme.Surface
import com.omnitv.app.ui.theme.SurfaceBright
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import com.omnitv.app.ui.theme.TextTertiary
import java.util.Locale

/* ────────────────────────────────────────────────────────────────────────── */
/*  Utilidades                                                                */
/* ────────────────────────────────────────────────────────────────────────── */

fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  Top bar — tvOS con vidrio esmerilado                                      */
/* ────────────────────────────────────────────────────────────────────────── */

data class TabItem(val label: String, val route: String, val iconRes: Int)

@Composable
fun TopTabBar(
    tabs: List<TabItem>,
    currentRoute: String?,
    onSelect: (TabItem) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(GradientTopBar)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.screenMargin, end = AppSpacing.screenMargin, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Marca con leve tracking — como el logo del sistema
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Omni", style = com.omnitv.app.ui.theme.AppType.brand, color = TextPrimary)
                Text("TV", style = com.omnitv.app.ui.theme.AppType.brand, color = Accent)
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Accent)
                )
            }
            Spacer(Modifier.width(18.dp))
            tabs.forEach { tab ->
                TabPill(tab = tab, selected = tab.route == currentRoute, onClick = { onSelect(tab) })
            }
            Spacer(Modifier.weight(1f))
            trailing()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    }
}

@Composable
private fun TabPill(tab: TabItem, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.06f else 1f, tween(AppMotion.Fast, easing = AppMotion.Emphasized), label = "tabScale"
    )
    val bg = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val content = if (focused) Background else if (selected) TextPrimary else TextSecondary

    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.lg)
            .background(bg)
            .border(
                width = if (focused) AppStroke.focus else if (selected) 1.dp else 0.dp,
                color = if (focused) Background.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.10f),
                shape = AppRadius.lg
            )
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(tab.iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(content)
        )
        Spacer(Modifier.width(8.dp))
        Text(tab.label, style = com.omnitv.app.ui.theme.AppType.tab, color = content)
        if (selected && !focused) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(Accent))
        }
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  Scaffold — barra fija + contenido                                         */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun TvScaffold(
    tabs: List<TabItem>,
    currentRoute: String?,
    onTabSelected: (TabItem) -> Unit,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Background)) {
        TopTabBar(tabs, currentRoute, onTabSelected, trailing = trailing)
        Box(Modifier.weight(1f)) { content() }
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  PillButton — primario y vidrio con estados premium                        */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun PillButton(
    text: String,
    iconRes: Int? = null,
    primary: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(AppMotion.Fast, easing = AppMotion.Emphasized), label = "pillScale")

    val bg = when {
        danger && focused -> Danger
        danger -> Danger.copy(alpha = 0.18f)
        primary && focused -> Color.White
        primary -> SurfaceBright
        focused -> GlassFillStrong
        else -> GlassFill
    }
    val contentColor = when {
        danger && !focused -> Danger
        primary -> OnLight
        else -> TextPrimary
    }
    val ring = if (primary) Background.copy(alpha = 0.85f) else Color.White

    Row(
        modifier = modifier
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 12.dp else 0.dp, AppRadius.pill, clip = false)
            .clip(AppRadius.pill)
            .background(bg)
            .then(if (focused) Modifier.border(AppStroke.focus, ring, AppRadius.pill) else Modifier)
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(contentColor)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = com.omnitv.app.ui.theme.AppType.button, color = contentColor)
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  CircleIconButton                                                            */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun CircleIconButton(
    iconRes: Int,
    contentDescription: String? = null,
    size: Dp = 56.dp,
    background: Color = GlassFillStrong,
    iconTint: Color = TextPrimary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(AppMotion.Fast), label = "circleScale")
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 10.dp else 0.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(if (focused) Color.White else background)
            .border(if (focused) AppStroke.focus else 0.dp, if (focused) Background.copy(alpha = 0.85f) else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.42f),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (focused) Background else iconTint)
        )
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  Badges & MetaLine — pulidos, con soporte para variantes                   */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, accent: Boolean = true) {
    Box(
        modifier
            .clip(AppRadius.xs)
            .background(if (accent) Accent else Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(text.uppercase(), style = com.omnitv.app.ui.theme.AppType.chip, color = Color.White) }
}

@Composable
fun MetaLine(video: Video, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val parts = listOfNotNull(
            video.year.takeIf { it > 0 }?.toString(),
            video.durationLabel.takeIf { video.durationMinutes > 0 },
            "★ ${video.ratingLabel}".takeIf { video.hasRating },
            video.quality.takeIf { it.isNotBlank() },
            video.certification.takeIf { it.isNotBlank() }
        )
        parts.forEachIndexed { i, part ->
            if (i > 0) Box(Modifier.padding(horizontal = 10.dp).size(3.dp).clip(CircleShape).background(TextTertiary))
            Text(part, style = com.omnitv.app.ui.theme.AppType.metadata, color = TextSecondary)
        }
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  PosterCard — foco tvOS con sombra, borde y overlay editorial              */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun PosterCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(260.dp, 150.dp)
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(AppMotion.Normal, easing = AppMotion.Emphasized), label = "cardScale")
    val elevation by animateDpAsState(if (focused) AppElevation.cardFocused else AppElevation.cardRest, tween(AppMotion.Normal), label = "cardElevation")

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation, AppRadius.md, clip = false)
            .clip(AppRadius.md)
            .background(Surface)
            .border(
                width = if (focused) AppStroke.cardFocused else AppStroke.cardIdle,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.07f),
                shape = AppRadius.md
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
    ) {
        AsyncImage(
            model = video.cardUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Shimmer placeholder sutil si no hay imagen
        if (video.cardUrl.isBlank()) {
            Box(Modifier.fillMaxSize().background(SurfaceElevated))
        }
        // Degradado editorial — texto siempre legible
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))
            )
        )
        // Borde interior luminoso al enfocar
        if (focused) {
            Box(
                Modifier.fillMaxSize()
                    .border(1.dp, Color.White.copy(alpha = 0.18f), AppRadius.md)
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(video.title, style = com.omnitv.app.ui.theme.AppType.cardTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            if (video.progress > 0f) ProgressBar(video.progress)
            else if (video.durationMinutes > 0) Text(video.durationLabel, style = com.omnitv.app.ui.theme.AppType.caption, color = Color.White.copy(alpha = 0.85f))
        }
        video.badge?.let { Badge(it, Modifier.align(Alignment.TopStart).padding(8.dp)) }
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(AppRadius.pill).background(Color.White.copy(alpha = 0.28f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(AppRadius.pill).background(Accent))
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  SectionRow — título con tracking y “Ver todo” opcional                    */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun SectionRow(row: MediaRow, onOpenDetail: (Video) -> Unit, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = AppSpacing.screenMargin, end = AppSpacing.screenMargin, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(row.title, style = com.omnitv.app.ui.theme.AppType.sectionTitle, color = TextPrimary)
            if (onSeeAll != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    "Ver todo ›",
                    style = com.omnitv.app.ui.theme.AppType.metadata,
                    color = TextSecondary,
                    modifier = Modifier
                        .clip(AppRadius.pill)
                        .clickable(onClick = onSeeAll)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = AppSpacing.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.cardGap),
            modifier = Modifier.fillMaxWidth().focusRestorer()
        ) {
            items(row.items, key = { it.id }) { v -> PosterCard(v, onClick = { onOpenDetail(v) }) }
        }
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  HeroBanner — cinemático, velos multicapa y jerarquía editorial            */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun HeroBanner(
    video: Video,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
    playFocusRequester: FocusRequester? = null
) {
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.60f).dp.coerceAtMost(480.dp)
    Box(modifier.fillMaxWidth().height(heroHeight)) {
        AsyncImage(model = video.backdropUrl.ifBlank { video.cardUrl }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        // Velo superior — funde con top bar
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Background.copy(alpha = 0.55f), 0.22f to Color.Transparent)))
        // Velo inferior — base para texto
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Background.copy(alpha = 0.65f), 1f to Background)))
        // Velo lateral — legibilidad izquierda
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Background.copy(alpha = 0.88f), 0.5f to Background.copy(alpha = 0.35f), 0.85f to Color.Transparent)))

        Column(Modifier.align(Alignment.BottomStart).padding(start = AppSpacing.screenMargin, bottom = 40.dp, end = 48.dp).widthIn(max = 660.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (video.badge != null) Badge(video.badge)
                Box(Modifier.clip(AppRadius.xs).background(Color.White.copy(alpha = 0.12f)).border(1.dp, GlassBorder, AppRadius.xs).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(video.type.label.uppercase(), style = com.omnitv.app.ui.theme.AppType.overline, color = Color.White.copy(alpha = 0.9f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(video.title, style = com.omnitv.app.ui.theme.AppType.hero, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            MetaLine(video)
            Spacer(Modifier.height(12.dp))
            Text(video.description, style = com.omnitv.app.ui.theme.AppType.body, color = Color.White.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Reproducir", R.drawable.ic_play, true, onClick = onPlay, modifier = if (playFocusRequester != null) Modifier.focusRequester(playFocusRequester) else Modifier)
                PillButton("Más información", R.drawable.ic_info, false, onClick = onInfo)
            }
        }
        // Indicador sutil inferior
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(32.dp, 3.dp).clip(AppRadius.pill).background(Color.White.copy(alpha = 0.18f)))
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  TvToggle — iOS con spring y halo de foco                                  */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun TvToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.10f else 1f, tween(AppMotion.Fast, easing = AppMotion.Emphasized), label = "toggleScale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .width(72.dp).height(40.dp)
            .clip(AppRadius.lg)
            .background(if (checked) Accent else Color.White.copy(alpha = 0.20f))
            .border(if (focused) AppStroke.focus else 0.dp, if (focused) Color.White else Color.Transparent, AppRadius.lg)
            .clickable { onCheckedChange(!checked) }
            .onFocusChanged { focused = it.isFocused }
    ) {
        Box(
            Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(4.dp).size(32.dp).clip(CircleShape).background(Color.White)
                .shadow(2.dp, CircleShape)
        )
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  SeekBar — pista doble + tirador con sombra                                */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun SeekBar(positionMs: Long, durationMs: Long, bufferedMs: Long, modifier: Modifier = Modifier) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
        val w = maxWidth
        Box(Modifier.width(w).height(6.dp).clip(AppRadius.pill).background(Color.White.copy(alpha = 0.20f)))
        Box(Modifier.width(w * buffered).height(6.dp).clip(AppRadius.pill).background(Color.White.copy(alpha = 0.36f)))
        Box(Modifier.width(w * fraction).height(6.dp).clip(AppRadius.pill).background(Color.White))
        Box(Modifier.offset(x = ((w - 18.dp) * fraction).coerceAtLeast(0.dp)).size(18.dp).clip(CircleShape).background(Color.White).shadow(4.dp, CircleShape))
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  LoadingSpinner — anillo con head redondeado                                */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun LoadingSpinner(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val t = rememberInfiniteTransition(label = "spinner")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Canvas(modifier.size(size)) {
        drawArc(Accent, angle, 270f, false, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  SettingRow — con highlight sutil y foco dentro                             */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    var highlighted by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth().clip(AppRadius.md)
            .background(if (highlighted) SurfaceElevated else Color.Transparent)
            .border(if (highlighted) 1.dp else 0.dp, if (highlighted) Color.White.copy(alpha = 0.10f) else Color.Transparent, AppRadius.md)
            .onFocusChanged { highlighted = it.hasFocus }
            .then(if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, null, onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = com.omnitv.app.ui.theme.AppType.bodyStrong, color = TextPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = com.omnitv.app.ui.theme.AppType.caption, color = TextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  TvChip — filtro único con 3 estados                                        */
/* ────────────────────────────────────────────────────────────────────────── */

@Composable
fun TvChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, style: androidx.compose.ui.text.TextStyle = com.omnitv.app.ui.theme.AppType.body) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Accent
        selected -> Color.White.copy(alpha = 0.14f)
        else -> GlassFill
    }
    Box(
        modifier.clip(AppRadius.pill).background(bg)
            .border(if (focused) AppStroke.focus else if (selected) 1.dp else 0.dp, if (focused) Color.White else Color.White.copy(alpha = 0.10f), AppRadius.pill)
            .clickable(remember { MutableInteractionSource() }, null, onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(text, style = style, color = if (focused) Color.White else if (selected) TextPrimary else TextSecondary)
    }
}
