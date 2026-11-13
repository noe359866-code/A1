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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.Text
import com.omnitv.app.ui.theme.Accent

/* -------------------------------------------------------------------------- */
/*  Estilo «vidrio» oscuro del reproductor                                    */
/* -------------------------------------------------------------------------- */

val GlassDark = Color(0xE60E0E13)
val GlassBorder = Color(0x33FFFFFF)
val ControlWhite = Color(0xFFF2F2F7)

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
    val contentColor = if (focused) Color(0xFF0E0E13) else ControlWhite

    Row(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(26.dp))
            .background(background)
            .then(
                if (active && !focused) Modifier.border(1.5.dp, Accent.copy(alpha = 0.8f), RoundedCornerShape(26.dp))
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
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(contentColor)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            color = contentColor
        )
    }
}

/** Botón de transporte cuadrado redondeado (⏪ ▶ ⏩) sin texto. */
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
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.44f),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                if (focused || primary) Color(0xFF0E0E13) else ControlWhite
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
    GlassPanel(modifier = modifier.width(320.dp)) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Text(
                title.uppercase(),
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                ),
                color = Color(0xFF8E8E93),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            options.forEach { option ->
                OptionRow(option)
            }
        }
    }
}

@Composable
private fun OptionRow(option: PlayerOption) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent)
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
                    if (!option.selected) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    else Modifier
                )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            option.label,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 15.sp
            ),
            color = if (focused) Color.White else ControlWhite.copy(alpha = if (option.selected) 1f else 0.8f)
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Franja EPG «AHORA / A CONTINUACIÓN»                                       */
/* -------------------------------------------------------------------------- */

@Composable
fun EpgChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp
            ),
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
            .border(2.dp, Color.White, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = "Reproducir",
            modifier = Modifier.size(44.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF0E0E13))
        )
    }
}

/** Chip informativo (badges de calidad, avisos). */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
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
