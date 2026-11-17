@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.MediaRow
import com.omnitv.app.data.PlayerScrubRates
import com.omnitv.app.data.SkipKind
import com.omnitv.app.data.SkipSegment
import com.omnitv.app.data.TvKeyCodes
import com.omnitv.app.data.Video
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AccentGreen
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.BadgeAccent
import com.omnitv.app.ui.theme.Danger
import com.omnitv.app.ui.theme.FocusScale
import com.omnitv.app.ui.theme.GlassBorder
import com.omnitv.app.ui.theme.GlassDark
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.GlassFillStrong
import com.omnitv.app.ui.theme.LiveAccentText
import com.omnitv.app.ui.theme.MedalBronze
import com.omnitv.app.ui.theme.OnLight
import com.omnitv.app.ui.theme.RatingGold
import com.omnitv.app.ui.theme.Surface
import com.omnitv.app.ui.theme.SurfaceBright
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import com.omnitv.app.ui.theme.TextTertiary
import com.omnitv.app.ui.theme.appTween
import com.omnitv.app.ui.theme.rememberReducedMotion
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
/*  Navegación lateral Android TV                                             */
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
    activeTabFocus: FocusRequester? = null,
    onEnterContent: (() -> Unit)? = null,
    onBarFocusChanged: ((Boolean) -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    var railHasFocus by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (railHasFocus) 224.dp else 76.dp,
        animationSpec = appTween(180),
        label = "sidebarWidth"
    )
    val focusManager = LocalFocusManager.current

    // La barra ya no se estira a toda la altura: es una superficie flotante con
    // margen de overscan (27 dp arriba/abajo), para que el contenido se vea
    // detrás y la expansión no recorte el layout de la pantalla.
    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .padding(vertical = AppSpacing.safeTop, horizontal = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppRadius.lg)
                .background(GlassDark)
                .border(1.dp, GlassBorder, AppRadius.lg)
                .onFocusChanged {
                    railHasFocus = it.hasFocus
                    onBarFocusChanged?.invoke(it.hasFocus)
                }
                .focusGroup()
                .padding(horizontal = if (railHasFocus) 14.dp else 10.dp, vertical = 24.dp),
            horizontalAlignment = if (railHasFocus) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("O", style = AppType.brand, color = Accent)
                if (railHasFocus) {
                    Spacer(Modifier.width(8.dp))
                    Text("Omni TV", style = AppType.brand, color = TextPrimary, maxLines = 1)
                }
            }

            tabs.forEach { tab ->
                val selected = tab.route == currentRoute
                SidebarItem(
                    tab = tab,
                    selected = selected,
                    expanded = railHasFocus,
                    modifier = if (selected && activeTabFocus != null) {
                        Modifier.focusRequester(activeTabFocus)
                    } else Modifier,
                    onActivate = {
                        if (selected) {
                            if (onEnterContent != null) onEnterContent()
                            else focusManager.moveFocus(FocusDirection.Right)
                        } else onSelect(tab)
                    }
                )
            }

            Spacer(Modifier.height(14.dp))
            // El perfil permanece accesible en el extremo inferior de la barra.
            trailing()
        }
    }
}

@Composable
private fun SidebarItem(
    tab: TabItem,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> FocusScale.tab * FocusScale.pressFactor
            focused -> FocusScale.tab
            else -> 1f
        },
        appTween(150), label = "sidebarItemScale"
    )
    val foreground = if (focused) Background else if (selected) TextPrimary else TextSecondary
    val fill = when {
        focused -> Color.White
        selected -> Accent.copy(alpha = 0.20f)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.md)
            .background(fill)
            .then(if (focused) Modifier.border(AppStroke.focus, Background, AppRadius.md) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onActivate)
            .padding(horizontal = if (expanded) 14.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
    ) {
        Image(
            painter = painterResource(tab.iconRes),
            contentDescription = tab.label,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(foreground)
        )
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(tab.label, style = AppType.tab, color = foreground, maxLines = 1, softWrap = false)
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Scaffold con navegación lateral persistente                              */
/* -------------------------------------------------------------------------- */

@Composable
fun TvScaffold(
    tabs: List<TabItem>,
    currentRoute: String?,
    onTabSelected: (TabItem) -> Unit,
    showTopBar: Boolean = true,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val activeTabFocus = remember { FocusRequester() }
    val tabContentFocus = remember { TabContentFocusTarget() }
    val focusManager = LocalFocusManager.current
    var railFocused by remember { mutableStateOf(false) }
    var enterContentRequested by remember { mutableStateOf(false) }
    var enterTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(showTopBar) {
        if (!showTopBar) {
            railFocused = false
            enterContentRequested = false
        }
    }

    LaunchedEffect(enterTick) {
        if (enterTick == 0) return@LaunchedEffect
        repeat(8) {
            if (!railFocused) {
                enterContentRequested = false
                return@LaunchedEffect
            }
            val target = tabContentFocus.focus
            if (target != null && runCatching { target.requestFocus() }.isSuccess) {
                enterContentRequested = false
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
        if (railFocused) focusManager.moveFocus(FocusDirection.Right)
        enterContentRequested = false
    }

    val suppressAutoContentFocus = showTopBar && railFocused && !enterContentRequested

    CompositionLocalProvider(
        LocalActiveTabFocus provides activeTabFocus,
        LocalTabContentFocus provides tabContentFocus,
        LocalSuppressContentAutoFocus provides suppressAutoContentFocus
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Background)
                .dpadRepeatThrottle()
        ) {
            // El contenido ocupa SIEMPRE el ancho completo. La barra lateral
            // flota encima: si empujara el contenido, expandirse de 76 a 224 dp
            // al enfocar recompondría las rejillas mientras el usuario navega
            // (el póster de Películas pasaba de 142 a 112 dp, −21 %).
            Box(Modifier.fillMaxSize()) {
                content()
            }
            if (showTopBar) {
                TopTabBar(
                    tabs = tabs,
                    currentRoute = currentRoute,
                    onSelect = { tab ->
                        enterContentRequested = true
                        onTabSelected(tab)
                        enterTick++
                    },
                    onEnterContent = {
                        enterContentRequested = true
                        enterTick++
                    },
                    onBarFocusChanged = { focused ->
                        railFocused = focused
                        if (!focused) enterContentRequested = false
                    },
                    activeTabFocus = activeTabFocus,
                    trailing = trailing,
                    modifier = Modifier.align(Alignment.CenterStart).zIndex(2f)
                )
            }
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
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
    /**
     * Estado deshabilitado de la matriz oficial de foco de Android TV
     * (default/focused/pressed × enabled/disabled). Antes no existía: una acción
     * inactiva no tenía forma visual de comunicarlo.
     */
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> FocusScale.chip * FocusScale.pressFactor
            focused -> FocusScale.chip
            else -> 1f
        },
        appTween(150), label = "pillScale"
    )

    val background = when {
        !enabled -> GlassFill.copy(alpha = 0.10f)
        danger && focused -> Danger
        danger -> Danger.copy(alpha = 0.22f)
        primary && focused -> Color.White
        primary -> SurfaceBright
        focused -> GlassFillStrong
        else -> GlassFill
    }
    val contentColor = when {
        !enabled -> TextTertiary               // inactivo: sin promesa de pulsación
        danger && !focused -> LiveAccentText // 5,1:1 sobre el relleno translúcido (AA)
        danger -> OnLight                    // enfocado: oscuro sobre Danger (≈5,6:1, simétrico al primario)
        primary -> OnLight
        else -> TextPrimary
    }
    val ringColor = if (primary) Background.copy(alpha = 0.85f) else Color.White

    Row(
        modifier = modifier
            .zIndex(if (focused && enabled) 1f else 0f)
            .height(60.dp)
            .graphicsLayer { scaleX = if (enabled) scale else 1f; scaleY = if (enabled) scale else 1f }
            .shadow(if (focused && primary && enabled) 14.dp else 0.dp, AppRadius.pill, clip = false)
            .clip(AppRadius.pill)
            .background(background)
            .then(
                if (focused && enabled) Modifier.border(AppStroke.focus, ringColor, AppRadius.pill) else Modifier
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> FocusScale.control * FocusScale.pressFactor
            focused -> FocusScale.control
            else -> 1f
        },
        appTween(150), label = "circleScale"
    )

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (focused) Color.White else background)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Background.copy(alpha = 0.85f), CircleShape)
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
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
            // Badge sólido: 4,78:1 con blanco (AA); el acento de sistema se queda para foco/rellenos.
            .background(BadgeAccent)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, style = AppType.chip, color = Color.White)
    }
}

@Composable
fun MetaLine(video: Video, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        var hasBefore = false
        if (video.hasRating) {
            Text("★", style = AppType.metadata, color = RatingGold)
            Spacer(Modifier.width(4.dp))
            Text(video.ratingLabel, style = AppType.controlLabel, color = TextPrimary)
            hasBefore = true
        }
        val parts = listOfNotNull(
            video.year.takeIf { it > 0 }?.toString(),
            video.durationLabel.takeIf { video.durationMinutes > 0 },
            video.quality.takeIf { it.isNotBlank() },
            video.certification.takeIf { it.isNotBlank() }
        )
        parts.forEach { part ->
            if (hasBefore) {
                Box(
                    Modifier
                        .padding(horizontal = 10.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(TextTertiary)
                )
            }
            Text(part, style = AppType.metadata, color = TextSecondary)
            hasBefore = true
        }
    }
}

@Composable
private fun RatingBadge(rating: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(AppRadius.xs)
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), AppRadius.xs)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("★", style = AppType.chip, color = RatingGold)
        Spacer(Modifier.width(4.dp))
        Text(rating, style = AppType.chip, color = Color.White)
    }
}

/* -------------------------------------------------------------------------- */
/*  Tarjeta de póster 2:3 (estilo Cinejoy / GreyBox / NovaHD, limpio)         */
/* -------------------------------------------------------------------------- */

@Composable
fun PosterCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(156.dp),
    onFocus: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> FocusScale.card * FocusScale.pressFactor
            focused -> FocusScale.card
            else -> 1f
        },
        appTween(150), label = "cardScale"
    )
    val elevation by animateDpAsState(if (focused) 18.dp else 0.dp, appTween(150), label = "cardElevation")

    Column(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .shadow(elevation, AppRadius.md, clip = false)
                .clip(AppRadius.md)
                .background(Brush.linearGradient(listOf(SurfaceElevated, Surface)))
                .border(
                    width = if (focused) AppStroke.focus else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = AppRadius.md
                )
        ) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    video.title.take(1).uppercase(),
                    style = AppType.screenTitle,
                    color = Color.White.copy(alpha = 0.14f)
                )
            }
            AsyncImage(
                model = video.cardUrl.ifBlank { video.backdropUrl },
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.06f))
                )
            }
            if (video.progress > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.78f)
                            )
                        )
                )
                ProgressBar(
                    progress = video.progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                )
            }
            video.badge?.let {
                Badge(it, Modifier.align(Alignment.TopStart).padding(10.dp))
            }
            if (video.hasRating) {
                RatingBadge(
                    rating = video.ratingLabel,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            video.title,
            style = AppType.cardTitle,
            color = if (focused) Color.White else TextPrimary.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val subtitle = buildList {
            if (video.year > 0) add(video.year.toString())
            add(video.type.label)
        }.joinToString(" · ")
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            style = AppType.metadata,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Tarjeta 16:9 de «Continuar viendo»                                        */
/* -------------------------------------------------------------------------- */

@Composable
fun ContinueCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(276.dp),
    onFocus: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> FocusScale.tab * FocusScale.pressFactor
            focused -> FocusScale.tab
            else -> 1f
        },
        appTween(150), label = "continueScale"
    )
    val elevation by animateDpAsState(if (focused) 18.dp else 0.dp, appTween(150), label = "continueElevation")
    val previewUrl = if (video.streamId != null && video.cardUrl.isNotBlank()) {
        video.cardUrl
    } else {
        video.backdropUrl.ifBlank { video.cardUrl }
    }

    Column(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(elevation, AppRadius.md, clip = false)
                .clip(AppRadius.md)
                .background(Brush.linearGradient(listOf(SurfaceElevated, Surface)))
                .border(
                    width = if (focused) AppStroke.focus else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = AppRadius.md
                )
        ) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    video.title.take(1).uppercase(),
                    style = AppType.sectionTitle,
                    color = Color.White.copy(alpha = 0.14f)
                )
            }
            AsyncImage(
                model = previewUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.06f))
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.80f)
                        )
                    )
            )
            ProgressBar(
                progress = video.progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            video.badge?.let {
                Badge(it, Modifier.align(Alignment.TopStart).padding(10.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            video.title,
            style = AppType.cardTitle,
            color = if (focused) Color.White else TextPrimary.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val pct = (video.progress.coerceIn(0f, 1f) * 100).toInt()
        val info = buildList {
            add("$pct % visto")
            if (video.durationMinutes > 0) add(video.durationLabel)
        }.joinToString(" · ")
        Spacer(Modifier.height(3.dp))
        Text(
            info,
            style = AppType.metadata,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
fun SectionRow(
    row: MediaRow,
    onOpenDetail: (Video) -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: (Video) -> Modifier = { Modifier },
    onItemFocused: ((Video) -> Unit)? = null
) {
    val isContinueRow = row.items.any { it.progress > 0f }
    Column(modifier.fillMaxWidth().bringIntoViewIfChildrenAreFocused()) {
        Text(
            row.title,
            style = AppType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(start = AppSpacing.screenMargin + AppSpacing.navRail, bottom = 4.dp)
        )
        ProvideHorizontalTvBringIntoView(leadingPadding = AppSpacing.screenMargin + AppSpacing.navRail) {
            LazyRow(
                contentPadding = PaddingValues(start = AppSpacing.screenMargin + AppSpacing.navRail, end = AppSpacing.screenMargin, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer()
            ) {
                items(row.items, key = { it.id }) { video ->
                    if (isContinueRow) {
                        ContinueCard(
                            video = video,
                            onClick = { onOpenDetail(video) },
                            onFocus = onItemFocused?.let { cb -> { cb(video) } },
                            modifier = Modifier
                                .width(276.dp)
                                .then(cardModifier(video))
                        )
                    } else {
                        PosterCard(
                            video = video,
                            onClick = { onOpenDetail(video) },
                            onFocus = onItemFocused?.let { cb -> { cb(video) } },
                            modifier = Modifier
                                .width(156.dp)
                                .then(cardModifier(video))
                        )
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Fila numerada «Top 10 hoy» (estilo Cinejoy / NovaHD)                      */
/* -------------------------------------------------------------------------- */

@Composable
fun Top10SectionRow(
    title: String,
    items: List<Video>,
    onOpenDetail: (Video) -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: (Video) -> Modifier = { Modifier },
    onItemFocused: ((Video) -> Unit)? = null
) {
    if (items.isEmpty()) return
    var focusedRankIndex by remember { mutableIntStateOf(-1) }
    Column(modifier.fillMaxWidth().bringIntoViewIfChildrenAreFocused()) {
        Row(
            modifier = Modifier.padding(start = AppSpacing.screenMargin + AppSpacing.navRail, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style = AppType.sectionTitle,
                color = TextPrimary
            )
            Badge("HOY")
        }
        ProvideHorizontalTvBringIntoView {
            LazyRow(
                contentPadding = PaddingValues(start = AppSpacing.screenMargin + AppSpacing.navRail, end = AppSpacing.screenMargin, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer()
            ) {
                itemsIndexed(items.take(10), key = { _, it -> it.id }) { index, video ->
                    // Paleta de medallas «Top 10»: oro #1, plata #2, bronce #3 (tokens de theme/Color.kt).
                    val isFocusedItem = focusedRankIndex == index
                    val rankColor = when {
                        isFocusedItem -> Color.White
                        index == 0 -> RatingGold.copy(alpha = 0.88f)
                        index == 1 -> Color.White.copy(alpha = 0.52f)
                        index == 2 -> MedalBronze.copy(alpha = 0.82f)
                        else -> Color.White.copy(alpha = 0.22f)
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = AppType.rankDisplay,
                            color = rankColor,
                            modifier = Modifier.padding(bottom = 44.dp)
                        )
                        PosterCard(
                            video = video,
                            onClick = { onOpenDetail(video) },
                            onFocus = {
                                focusedRankIndex = index
                                onItemFocused?.invoke(video)
                            },
                            modifier = Modifier
                                .width(152.dp)
                                .onFocusChanged { if (!it.isFocused && focusedRankIndex == index) focusedRankIndex = -1 }
                                .then(cardModifier(video))
                        )
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Banner héroe (tipo "Watch Now" de Apple TV + Cinejoy / NovaHD)            */
/* -------------------------------------------------------------------------- */

@Composable
fun HeroBanner(
    video: Video,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleMyList: (() -> Unit)? = null,
    inMyList: Boolean = false,
    slideIndex: Int = 0,
    slideCount: Int = 0,
    onHeroFocusChanged: ((Boolean) -> Unit)? = null,
    playFocusRequester: FocusRequester? = null,
    infoFocusRequester: FocusRequester? = null,
    listFocusRequester: FocusRequester? = null,
    onPlayFocused: (() -> Unit)? = null,
    onInfoFocused: (() -> Unit)? = null,
    onListFocused: (() -> Unit)? = null
) {
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.60f).dp.coerceAtMost(470.dp)
    val activeTabFocus = LocalActiveTabFocus.current
    Box(
        modifier
            .fillMaxWidth()
            .height(heroHeight)
            .bringIntoViewIfChildrenAreFocused()
    ) {
        Crossfade(
            targetState = video.backdropUrl.ifBlank { video.cardUrl },
            animationSpec = appTween(550),
            label = "heroBackdrop"
        ) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Velo superior
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Background.copy(alpha = 0.42f),
                        0.24f to Color.Transparent
                    )
                )
        )
        // Velo vertical
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Background.copy(alpha = 0.22f),
                        0.52f to Background.copy(alpha = 0.28f),
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
                        0f to Background.copy(alpha = 0.95f),
                        0.48f to Background.copy(alpha = 0.42f),
                        0.80f to Color.Transparent
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = AppSpacing.screenMargin, bottom = 44.dp)
                .widthIn(max = 680.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                video.badge?.let { Badge(it) }
                Box(
                    Modifier
                        .clip(AppRadius.xs)
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        video.type.label.uppercase(),
                        style = AppType.chip,
                        color = TextPrimary
                    )
                }
                if (video.genres.isNotEmpty()) {
                    Text(
                        video.genres.take(3).joinToString(" · "),
                        style = AppType.metadata,
                        color = TextPrimary.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                video.title,
                style = AppType.hero,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
            Row(
                modifier = Modifier
                    .focusGroup()
                    .onFocusChanged { onHeroFocusChanged?.invoke(it.hasFocus) },
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PillButton(
                    text = "Reproducir",
                    iconRes = R.drawable.ic_play,
                    primary = true,
                    onClick = onPlay,
                    onFocus = onPlayFocused,
                    modifier = Modifier
                        .then(if (playFocusRequester != null) Modifier.focusRequester(playFocusRequester) else Modifier)
                        .then(
                            if (activeTabFocus != null) Modifier.focusProperties { up = activeTabFocus }
                            else Modifier
                        )
                )
                PillButton(
                    text = "Más información",
                    iconRes = R.drawable.ic_info,
                    primary = false,
                    onClick = onInfo,
                    onFocus = onInfoFocused,
                    modifier = Modifier
                        .then(if (infoFocusRequester != null) Modifier.focusRequester(infoFocusRequester) else Modifier)
                        .then(
                            if (activeTabFocus != null) Modifier.focusProperties { up = activeTabFocus }
                            else Modifier
                        )
                )
                if (onToggleMyList != null) {
                    PillButton(
                        text = if (inMyList) "En Mi lista" else "Mi lista",
                        iconRes = if (inMyList) R.drawable.ic_check else R.drawable.ic_add,
                        primary = false,
                        onClick = onToggleMyList,
                        onFocus = onListFocused,
                        modifier = Modifier
                            .then(if (listFocusRequester != null) Modifier.focusRequester(listFocusRequester) else Modifier)
                            .then(
                                if (activeTabFocus != null) Modifier.focusProperties { up = activeTabFocus }
                                else Modifier
                            )
                    )
                }
            }
        }
        if (slideCount > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = AppSpacing.screenMargin, bottom = 52.dp)
                    .clip(AppRadius.pill)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), AppRadius.pill)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(slideCount) { index ->
                    val active = index == slideIndex
                    Box(
                        Modifier
                            .width(if (active) 20.dp else 7.dp)
                            .height(7.dp)
                            .clip(AppRadius.pill)
                            .background(if (active) Accent else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Interruptor tipo iOS                                                      */
/* -------------------------------------------------------------------------- */

@Composable
fun TvToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * ¿El interruptor es su propia parada de foco?
     *
     * `false` para el caso «fila + interruptor» de Ajustes: la fila entera es el
     * objetivo del mando ([SettingRow] con `onClick`) y el interruptor es solo
     * el indicador de su valor. Con dos paradas de foco en la misma fila, el
     * anillo blanco del interruptor y el de la fila se encienden por separado y
     * el usuario no sabe cuál de los dos está a punto de activar.
     *
     * Se deja `true` por defecto para el caso en que la fila **no** sea
     * pulsable (edición de perfiles), donde el interruptor es la única parada.
     */
    focusable: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> FocusScale.hero * FocusScale.pressFactor
            focused -> FocusScale.hero
            else -> 1f
        },
        appTween(140), label = "toggleScale"
    )

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .width(72.dp)
            .height(40.dp)
            .clip(AppRadius.lg)
            .background(if (checked) Accent else Color.White.copy(alpha = 0.22f))
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.lg)
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { canFocus = focusable }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = focusable
            ) { onCheckedChange(!checked) }
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
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    modifier: Modifier = Modifier,
    skipSegments: List<SkipSegment> = emptyList(),
    onSeekBy: ((deltaMs: Long, repeatCount: Int) -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val trackHeight = if (focused) 10.dp else 8.dp
    val thumbSize = if (focused) 24.dp else 20.dp

    val interactiveModifier = if (onSeekBy != null) {
        Modifier
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged?.invoke(it.isFocused)
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                val code = native.keyCode
                when {
                    TvKeyCodes.isLeft(code) || TvKeyCodes.isRewindKey(code) -> {
                        val step = PlayerScrubRates.seekStepMs(native.repeatCount)
                        onSeekBy(-step, native.repeatCount)
                        true
                    }
                    TvKeyCodes.isRight(code) || TvKeyCodes.isFastForwardKey(code) -> {
                        val step = PlayerScrubRates.seekStepMs(native.repeatCount)
                        onSeekBy(step, native.repeatCount)
                        true
                    }
                    TvKeyCodes.isConfirm(code) && onConfirm != null -> {
                        if (native.repeatCount == 0) onConfirm()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier
            .height(36.dp)
            .then(interactiveModifier),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = maxWidth

        Box(
            Modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(AppRadius.pill)
                .background(Color.White.copy(alpha = if (focused) 0.30f else 0.22f))
        )
        Box(
            Modifier
                .width(trackWidth * bufferedFraction)
                .height(trackHeight)
                .clip(AppRadius.pill)
                .background(Color.White.copy(alpha = 0.38f))
        )
        Box(
            Modifier
                .width(trackWidth * fraction)
                .height(trackHeight)
                .clip(AppRadius.pill)
                .background(if (focused) Accent else Color.White)
        )
        if (durationMs > 0 && skipSegments.isNotEmpty()) {
            skipSegments.forEach { segment ->
                val startFrac = (segment.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
                val endMs = (segment.endMs ?: durationMs).coerceAtLeast(segment.startMs)
                val endFrac = (endMs.toFloat() / durationMs).coerceIn(startFrac, 1f)
                val widthFrac = (endFrac - startFrac).coerceAtLeast(0.012f).coerceAtMost(1f - startFrac)
                if (widthFrac > 0f) {
                    val markerColor = when (segment.kind) {
                        SkipKind.INTRO, SkipKind.RECAP -> Accent.copy(alpha = 0.78f)
                        SkipKind.CREDITS, SkipKind.PREVIEW -> RatingGold.copy(alpha = 0.78f)
                        SkipKind.POST_CREDITS -> AccentGreen.copy(alpha = 0.85f)
                    }
                    Box(
                        Modifier
                            .offset(x = trackWidth * startFrac)
                            .width(trackWidth * widthFrac)
                            .height(trackHeight)
                            .clip(AppRadius.pill)
                            .background(markerColor)
                    )
                }
            }
        }
        Box(
            Modifier
                .offset(x = ((trackWidth - thumbSize) * fraction).coerceAtLeast(0.dp))
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White)
                .then(
                    if (focused) Modifier.border(AppStroke.focus, Accent, CircleShape)
                    else Modifier
                )
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Spinner de carga                                                          */
/* -------------------------------------------------------------------------- */

@Composable
fun LoadingSpinner(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    Canvas(modifier.size(size)) {
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
/*  Esqueleto de carga (skeleton)                                             */
/* -------------------------------------------------------------------------- */

/**
 * Bloque rectangular con brillo deslizante. Es la pieza con la que se construye
 * un esqueleto con la **forma** del contenido que va a llegar.
 *
 * Por qué no un spinner centrado: 17 pantallas usaban `LoadingSpinner` como
 * estado principal. Un esqueleto conserva la geometría de la rejilla, evita el
 * salto de layout al llegar los datos y hace la espera perceptualmente más
 * corta. Es lo que hacen las apps de TV de referencia (StreamVault, Netflix,
 * Disney+) y lo que recomienda la guía de 10 pies.
 *
 * El brillo se desactiva con movimiento reducido (`appTween` ya resuelve la
 * escala de animación del sistema): en ese caso queda un bloque estático.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    radius: Dp = AppRadius.sm,
    baseColor: Color = SurfaceElevated
) {
    val reducedMotion = rememberReducedMotion()
    val shimmer = rememberInfiniteTransition(label = "skeleton")
    val shift by shimmer.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "skeletonShift"
    )
    val brush = if (reducedMotion) {
        SolidColor(baseColor)
    } else {
        Brush.linearGradient(
            colors = listOf(baseColor, baseColor.copy(alpha = 0.45f), baseColor),
            start = Offset(shift * 400f, 0f),
            end = Offset(shift * 400f + 320f, 320f)
        )
    }
    Box(modifier.clip(radius).background(brush))
}

/**
 * Esqueleto de una rejilla de pósters: misma proporción (2:3) y mismo
 * espaciado que [PosterCard], para que al llegar los datos no salte nada.
 */
@Composable
fun PosterGridSkeleton(
    columns: Int = 5,
    rows: Int = 2,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(columns) { column ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // La clave estable evita que el brillo se reinicie en cada recomposición.
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.67f)
                                .semantics { contentDescription = "Cargando contenido" },
                            radius = AppRadius.md
                        )
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.7f).height(14.dp),
                            radius = AppRadius.xs
                        )
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.45f).height(12.dp),
                            radius = AppRadius.xs
                        )
                    }
                }
            }
        }
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
    var directFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppRadius.md)
            .background(if (highlighted) SurfaceElevated else Color.Transparent)
            .then(
                when {
                    directFocused -> Modifier.border(AppStroke.focus, Color.White, AppRadius.md)
                    highlighted -> Modifier.border(AppStroke.focus, Color.White.copy(alpha = 0.40f), AppRadius.md)
                    else -> Modifier
                }
            )
            .onFocusChanged {
                highlighted = it.hasFocus
                directFocused = it.isFocused
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier.focusGroup()
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
/*  Chip de filtro / selección                                                 */
/* -------------------------------------------------------------------------- */

/**
 * Chip de filtro / selección.
 *
 * **Sobre el `style` por defecto**: [AppType.body] (21 sp) — deliberado. Los
 * chips de filtro son los controles más frecuentes a 3 m y el tamaño versalitas
 * de [AppType.chip] (15 sp) está pensado para etiquetas cortas («HD», «KIDS»),
 * no para filtros con varias palabras. Si se quiere un chip de etiqueta, pásese
 * `style = AppType.chip`.
 */
@Composable
fun TvChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    style: TextStyle = AppType.body,
    onFocus: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> FocusScale.chip * FocusScale.pressFactor
            focused -> FocusScale.chip
            else -> 1f
        },
        appTween(150), label = "chipScale"
    )
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
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.pill)
            .background(background)
            .then(if (focused) Modifier.border(AppStroke.focus, ring, AppRadius.pill) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            style = style,
            color = contentColor
        )
    }
}
