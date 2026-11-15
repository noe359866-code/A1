@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material.Text
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.MediaRow
import com.omnitv.app.data.Video
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.Danger
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.GlassFillStrong
import com.omnitv.app.ui.theme.OnLight
import com.omnitv.app.ui.theme.Surface
import com.omnitv.app.ui.theme.SurfaceBright
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import com.omnitv.app.ui.theme.TextTertiary
import java.util.Locale

/* -------------------------------------------------------------------------- */
/*  Utilidades                                                                */
/* -------------------------------------------------------------------------- */

/** Formatea milisegundos como "m:ss" o "h:mm:ss". */
fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

/* -------------------------------------------------------------------------- */
/*  Pestañas superiores (barra tipo tvOS)                                     */
/* -------------------------------------------------------------------------- */

data class TabItem(
    val label: String,
    val route: String,
    val iconRes: Int
)

@Composable
fun TopTabBar(
    tabs: List<TabItem>,
    currentRoute: String?,
    onSelect: (TabItem) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    val compact = tabs.size > 5
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.screenMargin, end = AppSpacing.screenMargin, top = 32.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
        ) {
            // Marca de la app
            Text("Omni", style = AppType.brand, color = TextPrimary)
            Text("TV", style = AppType.brand, color = Accent)
            Spacer(Modifier.width(if (compact) 12.dp else 22.dp))
            tabs.forEach { tab ->
                TabPill(
                    tab = tab,
                    selected = tab.route == currentRoute,
                    compact = compact,
                    onClick = { onSelect(tab) }
                )
            }
            Spacer(Modifier.weight(1f))
            trailing()
        }
        // Separador sutil bajo la barra
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}

@Composable
private fun TabPill(tab: TabItem, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(150), label = "tabScale")
    val showLabel = !compact || selected || focused

    val background = when {
        focused -> Color.White
        selected -> GlassFillStrong
        else -> Color.Transparent
    }
    val content = if (focused) Background else if (selected) TextPrimary else TextSecondary

    Row(
        modifier = Modifier
            .then(if (compact) Modifier.widthIn(min = 48.dp) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.lg)
            .background(background)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Background.copy(alpha = 0.85f), AppRadius.lg)
                else Modifier
            )
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = if (compact) 14.dp else 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(tab.iconRes),
            contentDescription = if (showLabel) null else tab.label,
            modifier = Modifier.size(if (compact) 20.dp else 22.dp),
            colorFilter = ColorFilter.tint(content)
        )
        if (showLabel) {
            Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
            Text(tab.label, style = AppType.tab, color = content)
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Scaffold con barra de pestañas persistente                                */
/* -------------------------------------------------------------------------- */

@Composable
fun TvScaffold(
    tabs: List<TabItem>,
    currentRoute: String?,
    onTabSelected: (TabItem) -> Unit,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TopTabBar(tabs, currentRoute, onTabSelected, trailing = trailing)
        Box(Modifier.weight(1f)) {
            content()
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Botón píldora estilo Apple                                                */
/* -------------------------------------------------------------------------- */

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
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(150), label = "pillScale")

    val background = when {
        danger && focused -> Danger
        danger -> Danger.copy(alpha = 0.22f)
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
    val ringColor = if (primary) Background.copy(alpha = 0.85f) else Color.White

    Row(
        modifier = modifier
            .height(60.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.pill)
            .background(background)
            .then(
                if (focused) Modifier.border(AppStroke.focus, ringColor, AppRadius.pill) else Modifier
            )
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(contentColor)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = AppType.button, color = contentColor)
    }
}

/* -------------------------------------------------------------------------- */
/*  Botón circular (atrás, play/pause)                                        */
/* -------------------------------------------------------------------------- */

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
    val scale by animateFloatAsState(if (focused) 1.10f else 1f, tween(150), label = "circleScale")

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (focused) Color.White else background)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Background.copy(alpha = 0.85f), CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.42f),
            colorFilter = ColorFilter.tint(if (focused) Background else iconTint)
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Chips, badges y metadatos                                                 */
/* -------------------------------------------------------------------------- */

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(AppRadius.xs)
            .background(Accent)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, style = AppType.chip, color = Color.White)
    }
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
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(horizontal = 10.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(TextTertiary)
                )
            }
            Text(part, style = AppType.metadata, color = TextSecondary)
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Tarjeta de póster (con foco: escala 1.08 + sombra como en tvOS)           */
/* -------------------------------------------------------------------------- */

@Composable
fun PosterCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(260.dp, 150.dp)
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")
    val elevation by animateDpAsState(if (focused) 18.dp else 0.dp, label = "cardElevation")

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, AppRadius.md, clip = false)
            .clip(AppRadius.md)
            .background(Surface)
            .border(
                width = if (focused) AppStroke.focus else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = AppRadius.md
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        AsyncImage(
            model = video.cardUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.75f)
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                video.title,
                style = AppType.cardTitle,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            if (video.progress > 0f) {
                ProgressBar(video.progress)
            } else if (video.durationMinutes > 0) {
                Text(video.durationLabel, style = AppType.metadata, color = TextSecondary)
            }
        }
        video.badge?.let {
            Badge(it, Modifier.align(Alignment.TopStart).padding(10.dp))
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(AppRadius.pill)
            .background(Color.White.copy(alpha = 0.30f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(AppRadius.pill)
                .background(Accent)
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Fila horizontal de títulos                                                */
/* -------------------------------------------------------------------------- */

@Composable
fun SectionRow(row: MediaRow, onOpenDetail: (Video) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            row.title,
            style = AppType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(start = AppSpacing.screenMargin, bottom = 14.dp)
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = AppSpacing.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer()
        ) {
            items(row.items, key = { it.id }) { video ->
                PosterCard(video, onClick = { onOpenDetail(video) })
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Banner héroe (tipo "Watch Now" de Apple TV)                               */
/* -------------------------------------------------------------------------- */

@Composable
fun HeroBanner(
    video: Video,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
    playFocusRequester: FocusRequester? = null
) {
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.60f).dp.coerceAtMost(470.dp)
    Box(modifier.fillMaxWidth().height(heroHeight)) {
        AsyncImage(
            model = video.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Velo superior
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Background.copy(alpha = 0.38f),
                        0.22f to Color.Transparent
                    )
                )
        )
        // Velo vertical
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Background.copy(alpha = 0.2f),
                        0.55f to Background.copy(alpha = 0.25f),
                        1f to Background
                    )
                )
        )
        // Velo horizontal
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Background.copy(alpha = 0.92f),
                        0.45f to Background.copy(alpha = 0.35f),
                        0.75f to Color.Transparent
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = AppSpacing.screenMargin, bottom = 44.dp)
                .widthIn(max = 640.dp)
        ) {
            video.badge?.let { Badge(it) }
            Spacer(Modifier.height(14.dp))
            Text(video.title, style = AppType.hero, color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            MetaLine(video)
            Spacer(Modifier.height(14.dp))
            Text(
                video.description,
                style = AppType.body,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton(
                    text = "Reproducir",
                    iconRes = R.drawable.ic_play,
                    primary = true,
                    onClick = onPlay,
                    modifier = if (playFocusRequester != null) {
                        Modifier.focusRequester(playFocusRequester)
                    } else {
                        Modifier
                    }
                )
                PillButton(
                    text = "Más información",
                    iconRes = R.drawable.ic_info,
                    primary = false,
                    onClick = onInfo
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Interruptor tipo iOS                                                      */
/* -------------------------------------------------------------------------- */

@Composable
fun TvToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(140), label = "toggleScale")

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .width(72.dp)
            .height(40.dp)
            .clip(AppRadius.lg)
            .background(if (checked) Accent else Color.White.copy(alpha = 0.22f))
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.lg)
                else Modifier
            )
            .clickable { onCheckedChange(!checked) }
            .onFocusChanged { focused = it.isFocused }
    ) {
        Box(
            Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(4.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Barra de progreso del reproductor                                         */
/* -------------------------------------------------------------------------- */

@Composable
fun SeekBar(positionMs: Long, durationMs: Long, bufferedMs: Long, modifier: Modifier = Modifier) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        modifier.height(34.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = maxWidth

        Box(
            Modifier
                .width(trackWidth)
                .height(8.dp)
                .clip(AppRadius.pill)
                .background(Color.White.copy(alpha = 0.22f))
        )
        Box(
            Modifier
                .width(trackWidth * bufferedFraction)
                .height(8.dp)
                .clip(AppRadius.pill)
                .background(Color.White.copy(alpha = 0.38f))
        )
        Box(
            Modifier
                .width(trackWidth * fraction)
                .height(8.dp)
                .clip(AppRadius.pill)
                .background(Color.White)
        )
        Box(
            Modifier
                .offset(x = ((trackWidth - 20.dp) * fraction).coerceAtLeast(0.dp))
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Spinner de carga                                                          */
/* -------------------------------------------------------------------------- */

@Composable
fun LoadingSpinner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    Canvas(modifier.size(48.dp)) {
        drawArc(
            color = Accent,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Fila de ajustes                                                           */
/* -------------------------------------------------------------------------- */

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
        modifier = modifier
            .fillMaxWidth()
            .clip(AppRadius.md)
            .background(if (highlighted) SurfaceElevated else Color.Transparent)
            .then(if (highlighted) Modifier.border(AppStroke.focus, Color.White.copy(alpha = 0.35f), AppRadius.md) else Modifier)
            .onFocusChanged { highlighted = it.hasFocus }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.body, color = TextPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = AppType.metadata, color = TextSecondary)
            }
        }
        Spacer(Modifier.width(16.dp))
        trailing()
    }
}

/* -------------------------------------------------------------------------- */
/*  Chip de filtro / selección                                                */
/* -------------------------------------------------------------------------- */

@Composable
fun TvChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    style: TextStyle = AppType.body
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        selected -> GlassFillStrong
        else -> GlassFill
    }
    val contentColor = when {
        focused -> Background
        selected -> TextPrimary
        else -> TextSecondary
    }
    val ring = if (focused) Background.copy(alpha = 0.85f) else Color.Transparent

    Box(
        modifier = modifier
            .clip(AppRadius.pill)
            .background(background)
            .then(if (focused) Modifier.border(AppStroke.focus, ring, AppRadius.pill) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            style = style,
            color = contentColor
        )
    }
}
