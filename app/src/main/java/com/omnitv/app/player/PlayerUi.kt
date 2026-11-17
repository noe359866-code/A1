@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.omnitv.app.data.ConfirmKeyUpGuard
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.consumeStrayConfirmKeyUp
import com.omnitv.app.ui.theme.FocusScale
import com.omnitv.app.ui.theme.GlassBorder
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.OnLight
import com.omnitv.app.ui.theme.SurfaceBright
import com.omnitv.app.ui.theme.TextSecondary
import com.omnitv.app.ui.theme.appTween

/* -------------------------------------------------------------------------- */
/*  Estilo «vidrio» oscuro del reproductor (tokens en ui.theme.Color.kt)      */
/* -------------------------------------------------------------------------- */

val ControlWhite = SurfaceBright

/** Panel translúcido tipo overlay de reproductor. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xF01C1C1E))
            .border(1.dp, GlassBorder, RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

/* -------------------------------------------------------------------------- */
/*  Botón de control grande (mando de TV)                                     */
/* -------------------------------------------------------------------------- */

@Composable
fun ControlPill(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    maxWidth: Dp = 180.dp,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FocusScale.key else 1f, appTween(140), label = "pillScale")

    val background = when {
        focused -> ControlWhite
        active -> Color.White.copy(alpha = 0.24f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val contentColor = if (focused) OnLight else ControlWhite

    Row(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .height(52.dp)
            .widthIn(max = maxWidth)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.pill)
            .background(background)
            .then(
                if (active && !focused) Modifier.border(1.dp, Color.White.copy(alpha = 0.45f), AppRadius.pill)
                else if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.pill)
                else Modifier
            )
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged?.invoke(it.isFocused)
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            colorFilter = ColorFilter.tint(contentColor)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            style = AppType.controlLabel,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/** Botón de transporte circular (⏪ ▶ ⏩) sin texto. */
@Composable
fun TransportButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    primary: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FocusScale.hero else 1f, appTween(140), label = "transportScale")

    val background = when {
        focused -> ControlWhite
        primary -> ControlWhite.copy(alpha = 0.92f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .then(if (focused) Modifier.border(AppStroke.focus, Color.White, CircleShape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.44f),
            colorFilter = ColorFilter.tint(
                if (focused || primary) OnLight else ControlWhite
            )
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Menú emergente de opciones (servidor, calidad, audio, subtítulos…)        */
/* -------------------------------------------------------------------------- */

data class PlayerOption(
    val label: String,
    val selected: Boolean,
    /**
     * Icono opcional de la fila.
     *
     * La guía de Android TV pide omitir iconos en listas homogéneas (todas las
     * calidades, todos los idiomas) y usarlos cuando distinguen funciones. El
     * único menú que los usa es «Ajustes de reproducción», donde cada fila es
     * una cosa distinta: dos submenús, el temporizador y una acción destructiva.
     */
    val iconRes: Int? = null,
    val onSelect: () -> Unit
)

@Composable
fun OptionMenu(
    title: String,
    options: List<PlayerOption>,
    modifier: Modifier = Modifier
) {
    val rowRequesters = remember(title, options.size) { List(options.size) { FocusRequester() } }
    val initialIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    val initialFocus = rowRequesters.getOrNull(initialIndex)
    val keyUpGuard = remember(title) { ConfirmKeyUpGuard(initialSuppressUntilDown = true) }
    if (initialFocus != null) {
        RequestTvFocus(initialFocus, key = title)
    }
    GlassPanel(
        modifier = modifier
            .width(360.dp)
            .consumeStrayConfirmKeyUp(keyUpGuard)
    ) {
        Column(
            Modifier
                .focusGroup()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 14.dp)
        ) {
            Text(
                title.uppercase(),
                style = AppType.menuHeader,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            options.forEachIndexed { index, option ->
                OptionRow(
                    option = option,
                    modifier = Modifier
                        .focusRequester(rowRequesters[index])
                        .focusProperties {
                            up = if (index > 0) rowRequesters[index - 1] else FocusRequester.Cancel
                            down = if (index < rowRequesters.lastIndex) rowRequesters[index + 1] else FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                )
            }
        }
    }
}

@Composable
private fun OptionRow(option: PlayerOption, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(AppRadius.sm)
            .background(if (focused) Color.White else Color.Transparent)
            .then(if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.sm) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = option.onSelect)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (option.selected) (if (focused) OnLight else Color.White) else Color.Transparent)
                .then(
                    if (!option.selected) Modifier.border(2.dp, (if (focused) OnLight else Color.White).copy(alpha = 0.35f), CircleShape)
                    else Modifier
                )
        )
        Spacer(Modifier.width(12.dp))
        if (option.iconRes != null) {
            Image(
                painter = painterResource(option.iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(if (focused) OnLight else ControlWhite.copy(alpha = 0.85f))
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            option.label,
            style = AppType.controlLabel,
            color = if (focused) OnLight else ControlWhite.copy(alpha = if (option.selected) 1f else 0.8f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Franja informativa (reproducción / estado)                                */
/* -------------------------------------------------------------------------- */

@Composable
fun EpgChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(AppRadius.xs)
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = AppType.chip,
            color = Color.White
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Botón grande centrado de reproducción (pausa)                             */
/* -------------------------------------------------------------------------- */

@Composable
fun CenterPlayButton(iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, appTween(150), label = "centerScale")

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .size(88.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.48f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .focusProperties { canFocus = false }
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = "Reproducir",
            modifier = Modifier.size(36.dp),
            colorFilter = ColorFilter.tint(Color.White)
        )
    }
}

/** Chip informativo (badges de calidad, avisos). */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(AppRadius.xs)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = AppType.chip,
            color = ControlWhite
        )
    }
}
