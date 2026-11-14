package com.omnitv.app.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitv.app.ui.theme.*

// ────────────────────────────────────────────────────────────────────────────
// Tokens — solo reproductor
// ────────────────────────────────────────────────────────────────────────────

val GlassDark = Color(0xE60E0E13)
val GlassBorder = Color(0x26FFFFFF) // 15%
val PlayerScrimTop = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.82f), 0.55f to Color.Black.copy(alpha = 0.42f), 1f to Color.Transparent)
val PlayerScrimBottom = Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.88f))
val ControlWhite = SurfaceBright

private val FocusRing = Color.White
private val FocusRingInner = Color(0xFF0B0B0F)

// ────────────────────────────────────────────────────────────────────────────
// GlassPanel — premium, doble borde + sombra 24dp
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun GlassPanel(modifier: Modifier = Modifier, cornerRadius: Dp = 18.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .shadow(24.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF1C1C24).copy(alpha = 0.96f),
                    1f to Color(0xFF0E0E13).copy(alpha = 0.98f)
                )
            )
            .border(1.dp, GlassBorder, shape)
    ) {
        // Highlight interior superior — efecto cristal
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
                .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
        )
        Box(Modifier.padding(1.dp)) { content() }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Timeline — seekbar 3m, thumb grande al enfocar, tiempos legibles
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun PlayerTimeline(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    modifier: Modifier = Modifier,
    focused: Boolean = false
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFrac = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val trackH by animateDpAsState(if (focused) 6.dp else 4.dp, tween(AppMotion.Fast), label = "trackH")
    val thumbR by animateDpAsState(if (focused) 9.dp else 7.dp, tween(AppMotion.Fast), label = "thumbR")
    val elapsed = formatMs(positionMs)
    val remaining = if (durationMs > 0) "-${formatMs((durationMs - positionMs).coerceAtLeast(0))}" else "--:--"

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // tiempo izquierda — monoespaciado para no saltar
            Text(elapsed, style = AppType.metadata.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp), color = Color.White.copy(alpha = 0.92f))
            Spacer(Modifier.width(16.dp))
            BoxWithConstraints(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.CenterStart) {
                val w = maxWidth
                // base
                Box(Modifier.width(w).height(trackH).clip(AppRadius.pill).background(Color.White.copy(alpha = 0.18f)))
                // buffer
                if (bufferedFrac > 0.01f) {
                    Box(Modifier.width(w * bufferedFrac).height(trackH).clip(AppRadius.pill).background(Color.White.copy(alpha = 0.30f)))
                }
                // progreso
                Box(Modifier.width(w * fraction).height(trackH).clip(AppRadius.pill).background(Color.White))
                // chapita/indicador de posición
                Box(
                    Modifier
                        .offset(x = (w - thumbR * 2) * fraction)
                        .size(thumbR * 2)
                        .clip(CircleShape)
                        .background(Color.White)
                        .shadow(8.dp, CircleShape)
                        .border(2.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(remaining, style = AppType.metadata.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp), color = Color.White.copy(alpha = 0.72f))
        }
        // capítulo/marca sutil (opcional)
        if (durationMs > 0 && durationMs > 60_000) {
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                val step = size.width / 6f
                repeat(5) { i ->
                    drawLine(Color.White.copy(alpha = 0.12f), Offset(step * (i + 1), 0f), Offset(step * (i + 1), size.height), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(java.util.Locale.US, "%02d:%02d", m, sec)
}

// ────────────────────────────────────────────────────────────────────────────
// ControlPill — 44dp, icono 18dp, active=accent pill, focus=white
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun ControlPill(iconRes: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(AppMotion.Fast, easing = AppMotion.Emphasized), label = "pillScale")
    val bg = when {
        focused -> Color.White
        active -> Accent
        else -> Color.White.copy(alpha = 0.12f)
    }
    val content = when {
        focused -> OnLight
        active -> Color.White
        else -> Color.White.copy(alpha = 0.96f)
    }
    Row(
        modifier
            .height(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.pill)
            .background(bg)
            .border(
                width = when {
                    focused -> 2.dp
                    active -> 1.dp
                    else -> 0.dp
                },
                color = when {
                    focused -> FocusRingInner.copy(alpha = 0.9f)
                    active -> Accent.copy(alpha = 0.85f)
                    else -> Color.Transparent
                },
                shape = AppRadius.pill
            )
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(iconRes), null, Modifier.size(18.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(content))
        Spacer(Modifier.width(7.dp))
        Text(label, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 13.sp, letterSpacing = 0.2.sp), color = content, maxLines = 1)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// TransportButton — 56/64dp, glass 14%, primary blanca, focus halo 12dp
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun TransportButton(iconRes: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp, primary: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(AppMotion.Fast), label = "transportScale")
    val bg = when {
        focused -> Color.White
        primary -> Color.White
        else -> Color.White.copy(alpha = 0.14f)
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 14.dp else 0.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(bg)
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) FocusRingInner.copy(alpha = 0.9f) else Color.White.copy(alpha = if (primary) 0.82f else 0.14f),
                shape = CircleShape
            )
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(iconRes), contentDescription, Modifier.size(size * 0.46f), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (focused || primary) OnLight else Color.White))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// OptionMenu — 380dp, overline + divider + filas 48dp con check
// ────────────────────────────────────────────────────────────────────────────

data class PlayerOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

@Composable
fun OptionMenu(title: String, options: List<PlayerOption>, modifier: Modifier = Modifier) {
    GlassPanel(modifier.width(380.dp)) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(title.uppercase(), style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.Bold, 11.sp, letterSpacing = 1.1.sp), color = TextMuted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Color.White.copy(alpha = 0.07f)))
            Spacer(Modifier.height(6.dp))
            options.forEach { OptionRow(it) }
        }
    }
}

@Composable
private fun OptionRow(option: PlayerOption) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(AppRadius.md)
            .background(if (focused) Color.White else if (option.selected) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .border(if (option.selected && !focused) 1.dp else 0.dp, Color.White.copy(alpha = 0.06f), AppRadius.md)
            .clickable(remember { MutableInteractionSource() }, null, option.onSelect)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .background(if (option.selected) Accent else Color.Transparent)
                .border(1.8.dp, if (option.selected) Accent else Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (option.selected) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(option.label, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, if (option.selected) FontWeight.SemiBold else FontWeight.Normal, 14.sp), color = if (focused) OnLight else Color.White.copy(alpha = if (option.selected) 1f else 0.88f), maxLines = 1)
        if (option.selected && !focused) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// EpgChip — AHORA rojo vivo, A CONTINUACIÓN gris
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun EpgChip(text: String, color: Color, modifier: Modifier = Modifier) {
    val isLive = text.contains("AHORA", ignoreCase = true)
    Row(
        modifier.clip(AppRadius.xs).background(color).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLive) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.ExtraBold, 11.sp, letterSpacing = 0.7.sp), color = Color.White)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// CenterPlayButton — 112dp, blanco puro, sombra 28dp
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun CenterPlayButton(iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(AppMotion.Normal, easing = AppMotion.Emphasized), label = "centerScale")
    Box(
        modifier.size(112.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(28.dp, CircleShape, clip = false)
            .clip(CircleShape).background(Color.White)
            .border(3.dp, Color.White, CircleShape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        // anillo interior premium
        Box(Modifier.fillMaxSize().border(1.dp, Color.Black.copy(alpha = 0.06f), CircleShape))
        Image(painterResource(iconRes), "Reproducir", Modifier.size(44.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(OnLight))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// InfoChip — 4dp, 14% glass
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.14f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(text, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 12.sp, letterSpacing = 0.2.sp), color = Color.White.copy(alpha = 0.96f))
    }
}

@Composable
fun ControlRow(modifier: Modifier = Modifier, horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(10.dp), content: @Composable () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = horizontalArrangement) { content() }
}
