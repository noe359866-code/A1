package com.omnitv.app.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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

// ── Tokens ──────────────────────────────────────────────────────────────────

val GlassDark = Color(0xE60E0E13)
val GlassBorder = Color(0x26FFFFFF)
val PlayerScrimTop = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.88f),
    0.48f to Color.Black.copy(alpha = 0.45f),
    1f to Color.Transparent
)
val PlayerScrimBottom = Brush.verticalGradient(
    0f to Color.Transparent,
    0.28f to Color.Black.copy(alpha = 0.55f),
    1f to Color.Black.copy(alpha = 0.92f)
)
val ControlWhite = SurfaceBright
private val FocusInner = Color(0xFF0E0E13)

// ── GlassPanel ──────────────────────────────────────────────────────────────

@Composable
fun GlassPanel(modifier: Modifier = Modifier, cornerRadius: Dp = 18.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .shadow(24.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.verticalGradient(0f to Color(0xFF1D1D26).copy(0.98f), 1f to Color(0xFF0E0E13).copy(0.98f)))
            .border(1.dp, GlassBorder, shape)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.08f)).clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)))
        Box(Modifier.padding(1.dp)) { content() }
    }
}

// ── Timeline ────────────────────────────────────────────────────────────────

@Composable
fun PlayerTimeline(positionMs: Long, durationMs: Long, bufferedMs: Long, modifier: Modifier = Modifier, focused: Boolean = false) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFrac = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val trackH by animateDpAsState(if (focused) 6.dp else 4.dp, tween(AppMotion.Fast), label = "trackH")
    val thumbR by animateDpAsState(if (focused) 9.dp else 7.dp, tween(AppMotion.Fast), label = "thumbR")
    val elapsed = formatMs(positionMs)
    val remaining = if (durationMs > 0) "-${formatMs((durationMs - positionMs).coerceAtLeast(0))}" else "--:--"
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(elapsed, style = AppType.metadata.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 0.2.sp), color = Color.White.copy(0.96f))
            Spacer(Modifier.width(14.dp))
            BoxWithConstraints(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.CenterStart) {
                val w = maxWidth
                Box(Modifier.width(w).height(trackH).clip(AppRadius.pill).background(Color.White.copy(0.18f)))
                if (bufferedFrac > 0.01f) Box(Modifier.width(w * bufferedFrac).height(trackH).clip(AppRadius.pill).background(Color.White.copy(0.28f)))
                Box(Modifier.width(w * fraction).height(trackH).clip(AppRadius.pill).background(Color.White))
                Box(Modifier.offset(x = (w - thumbR * 2) * fraction).size(thumbR * 2).clip(CircleShape).background(Color.White).shadow(8.dp, CircleShape).border(2.dp, Color.Black.copy(0.14f), CircleShape))
            }
            Spacer(Modifier.width(14.dp))
            Text(remaining, style = AppType.metadata.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp), color = Color.White.copy(0.62f))
        }
        if (durationMs > 60_000) {
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                val step = size.width / 6f
                repeat(5) { i -> drawLine(Color.White.copy(0.10f), Offset(step * (i + 1), 0f), Offset(step * (i + 1), size.height), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round) }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(java.util.Locale.US, "%02d:%02d", m, sec)
}

// ── ControlPill ─────────────────────────────────────────────────────────────

@Composable
fun ControlPill(iconRes: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(AppMotion.Fast, easing = AppMotion.Emphasized), label = "pillScale")
    val bg = when { focused -> Color.White; active -> Accent; else -> Color.White.copy(0.12f) }
    val fg = when { focused -> OnLight; active -> Color.White; else -> Color.White.copy(0.96f) }
    Row(
        modifier.height(42.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(AppRadius.pill).background(bg)
            .border(if (focused) 2.dp else if (active) 1.dp else 0.dp, if (focused) FocusInner.copy(0.92f) else Accent.copy(0.9f), AppRadius.pill)
            .clickable(remember { MutableInteractionSource() }, null, onClick).onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(iconRes), null, Modifier.size(16.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(fg))
        Spacer(Modifier.width(7.dp))
        Text(label, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 12.5.sp, letterSpacing = 0.15.sp), color = fg, maxLines = 1)
    }
}

// ── TransportButton ───────────────────────────────────────────────────────

@Composable
fun TransportButton(iconRes: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp, primary: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(AppMotion.Fast), label = "tScale")
    val bg = when { focused -> Color.White; primary -> Color.White; else -> Color.White.copy(0.14f) }
    Box(
        modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.shadow(if (focused) 14.dp else 0.dp, CircleShape, false)
            .clip(CircleShape).background(bg)
            .border(if (focused) 2.5.dp else 1.dp, if (focused) FocusInner.copy(0.92f) else Color.White.copy(if (primary) 0.86f else 0.14f), CircleShape)
            .clickable(remember { MutableInteractionSource() }, null, onClick).onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) { Image(painterResource(iconRes), contentDescription, Modifier.size(size * 0.46f), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (focused || primary) OnLight else Color.White)) }
}

// ── OptionMenu ────────────────────────────────────────────────────────────

data class PlayerOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

@Composable
fun OptionMenu(title: String, options: List<PlayerOption>, modifier: Modifier = Modifier) {
    GlassPanel(modifier.width(380.dp)) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(title.uppercase(), style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.Bold, 11.sp, letterSpacing = 1.1.sp), color = TextMuted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Color.White.copy(0.07f)))
            Spacer(Modifier.height(6.dp))
            options.forEach { OptionRow(it) }
        }
    }
}

@Composable
private fun OptionRow(option: PlayerOption) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(AppRadius.md)
            .background(if (focused) Color.White else if (option.selected) Color.White.copy(0.06f) else Color.Transparent)
            .border(if (option.selected && !focused) 1.dp else 0.dp, Color.White.copy(0.06f), AppRadius.md)
            .clickable(remember { MutableInteractionSource() }, null, option.onSelect).onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(if (option.selected) Accent else Color.Transparent).border(1.8.dp, if (option.selected) Accent else Color.White.copy(0.34f), CircleShape), contentAlignment = Alignment.Center) {
            if (option.selected) Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        }
        Spacer(Modifier.width(12.dp))
        Text(option.label, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, if (option.selected) FontWeight.SemiBold else FontWeight.Normal, 13.5.sp), color = if (focused) OnLight else Color.White.copy(if (option.selected) 1f else 0.86f), maxLines = 1)
        if (option.selected && !focused) { Spacer(Modifier.weight(1f)); Box(Modifier.size(7.dp).clip(CircleShape).background(Accent)) }
    }
}

// ── EpgChip ───────────────────────────────────────────────────────────────

@Composable
fun EpgChip(text: String, color: Color, modifier: Modifier = Modifier) {
    val live = text.contains("AHORA", true)
    Row(modifier.clip(AppRadius.xs).background(color).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (live) { PulsingDot(); Spacer(Modifier.width(6.dp)) }
        Text(text, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.ExtraBold, 11.sp, letterSpacing = 0.6.sp), color = Color.White)
    }
}

@Composable
private fun PulsingDot() {
    val t = rememberInfiniteTransition(label = "pulse")
    val alpha by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900)), label = "a")
    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha)))
}

// ── CenterPlayButton ──────────────────────────────────────────────────────

@Composable
fun CenterPlayButton(iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(AppMotion.Normal, easing = AppMotion.Emphasized), label = "centerScale")
    Box(
        modifier.size(104.dp).graphicsLayer { scaleX = scale; scaleY = scale }.shadow(28.dp, CircleShape, false)
            .clip(CircleShape).background(Color.White).border(3.dp, Color.White, CircleShape)
            .clickable(remember { MutableInteractionSource() }, null, onClick).onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().border(1.dp, Color.Black.copy(0.06f), CircleShape))
        Image(painterResource(iconRes), "Reproducir", Modifier.size(38.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(OnLight))
    }
}

// ── InfoChip ──────────────────────────────────────────────────────────────

@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(0.14f)).border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(text, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 12.sp, letterSpacing = 0.15.sp), color = Color.White.copy(0.96f))
    }
}

@Composable
fun ControlRow(modifier: Modifier = Modifier, horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(10.dp), content: @Composable () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = horizontalArrangement) { content() }
}
