@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.OnLight
import com.omnitv.app.ui.theme.SurfaceBright
import com.omnitv.app.ui.theme.TextSecondary

/* -------------------------------------------------------------------------- */
/*  Estilo «vidrio» oscuro del reproductor                                    */
/* -------------------------------------------------------------------------- */

val GlassDark = Color(0xE60E0E13)
val GlassBorder = Color(0x33FFFFFF)
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
            .background(GlassDark)
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
    active: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(140), label = "pillScale")

    val background = when {
        focused -> ControlWhite
        active -> Accent.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val contentColor = if (focused) OnLight else ControlWhite

    Row(
        modifier = modifier
            .height(52.dp)
            .widthIn(max = 180.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.pill)
            .background(background)
            .then(
                if (active && !focused) Modifier.border(2.dp, Accent.copy(alpha = 0.8f), AppRadius.pill)
                else if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.pill)
                else Modifier
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
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
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(140), label = "transportScale")

    val background = when {
        focused -> ControlWhite
        primary -> ControlWhite.copy(alpha = 0.92f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .then(if (focused) Modifier.border(AppStroke.focus, Color.White, CircleShape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
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
    val onSelect: () -> Unit
)

@Composable
fun OptionMenu(
    title: String,
    options: List<PlayerOption>,
    modifier: Modifier = Modifier
) {
    val initialFocus = remember(title) { FocusRequester() }
    val initialIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    RequestTvFocus(initialFocus, key = title)
    GlassPanel(modifier = modifier.width(360.dp)) {
        Column(
            Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(vertical = 14.dp)
        ) {
            Text(
                title.uppercase(),
                style = AppType.menuHeader,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            options.forEachIndexed { index, option ->
                OptionRow(option, if (index == initialIndex) Modifier.focusRequester(initialFocus) else Modifier)
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
            .background(if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .then(if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.sm) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = option.onSelect)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (option.selected) Accent else Color.Transparent)
                .then(
                    if (!option.selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    else Modifier
                )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            option.label,
            style = AppType.controlLabel,
            color = if (focused) Color.White else ControlWhite.copy(alpha = if (option.selected) 1f else 0.8f)
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
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, tween(150), label = "centerScale")

    Box(
        modifier = modifier
            .size(110.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .border(AppStroke.focus, Color.White, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = "Reproducir",
            modifier = Modifier.size(44.dp),
            colorFilter = ColorFilter.tint(OnLight)
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

/** Contenedor con disposición horizontal de controles. */
@Composable
fun ControlRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement
    ) {
        content()
    }
}
