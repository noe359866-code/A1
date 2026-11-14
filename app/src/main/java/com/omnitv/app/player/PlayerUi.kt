package com.omnitv.app.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitv.app.ui.theme.*

// Glass premium — con blur simulado y borde sutil
val GlassDark = Color(0xE60E0E13)
val GlassBorder = Color(0x33FFFFFF)
val ControlWhite = SurfaceBright

@Composable
fun GlassPanel(modifier: Modifier = Modifier, cornerRadius: Dp = 18.dp, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(cornerRadius)).background(Brush.verticalGradient(0f to Color(0xFF1A1A22).copy(alpha = 0.96f), 1f to Color(0xFF0E0E13).copy(alpha = 0.98f))).border(1.dp, GlassBorder, RoundedCornerShape(cornerRadius)).shadow(16.dp, RoundedCornerShape(cornerRadius))) { content() })
}

@Composable
fun ControlPill(iconRes: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(AppMotion.Fast, easing = AppMotion.Emphasized), label = "pillScale")
    val bg = when { focused -> Color.White; active -> Accent.copy(alpha = 0.32f); else -> Color.White.copy(alpha = 0.13f) }
    Row(modifier.height(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(AppRadius.pill).background(bg).border(if (focused) 2.dp else if (active) 1.dp else 0.dp, if (focused) Background else Accent.copy(alpha = 0.6f), AppRadius.pill).clickable(remember { MutableInteractionSource() }, null, onClick).onFocusChanged { focused = it.isFocused }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(iconRes), null, Modifier.size(20.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (focused) OnLight else Color.White))
        Spacer(Modifier.width(8.dp))
        Text(label, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 17.sp), color = if (focused) OnLight else Color.White)
    }
}

@Composable
fun TransportButton(iconRes: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp, primary: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, tween(AppMotion.Fast), label = "transportScale")
    val bg = when { focused -> Color.White; primary -> Color.White.copy(alpha = 0.95f); else -> Color.White.copy(alpha = 0.14f) }
    Box(modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(bg).shadow(if (focused) 12.dp else 0.dp, CircleShape).clickable(remember { MutableInteractionSource() }, null, onClick).onFocusChanged { focused = it.isFocused }, contentAlignment = Alignment.Center) {
        Image(painterResource(iconRes), contentDescription, Modifier.size(size * 0.44f), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (focused || primary) OnLight else Color.White))
    }
}

data class PlayerOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

@Composable
fun OptionMenu(title: String, options: List<PlayerOption>, modifier: Modifier = Modifier) {
    GlassPanel(modifier.width(340.dp)) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(title.uppercase(), style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.Bold, 12.sp, letterSpacing = 1.2.sp), color = TextMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 12.dp))
            options.forEach { OptionRow(it) }
        }
    }
}

@Composable
private fun OptionRow(option: PlayerOption) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(AppRadius.sm).background(if (focused) Color.White.copy(alpha = 0.14f) else Color.Transparent).clickable(remember { MutableInteractionSource() }, null, option.onSelect).onFocusChanged { focused = it.isFocused }.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (option.selected) Accent else Color.Transparent).border(if (option.selected) 0.dp else 2.dp, Color.White.copy(alpha = 0.35f), CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(option.label, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, if (option.selected) FontWeight.SemiBold else FontWeight.Normal, 17.sp), color = if (focused) Color.White else Color.White.copy(alpha = if (option.selected) 1f else 0.78f))
        if (option.selected) { Spacer(Modifier.weight(1f)); Text("●", style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.Bold, 10.sp), color = Accent) }
    }
}

@Composable
fun EpgChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(AppRadius.xs).background(color).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.Bold, 12.sp, letterSpacing = 0.8.sp), color = Color.White)
    }
}

@Composable
fun CenterPlayButton(iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.10f else 1f, tween(AppMotion.Normal), label = "centerScale")
    Box(modifier.size(108.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(Color.White).border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape).shadow(20.dp, CircleShape).clickable(remember { MutableInteractionSource() }, null, onClick).onFocusChanged { focused = it.isFocused }, contentAlignment = Alignment.Center) {
        Image(painterResource(iconRes), "Reproducir", Modifier.size(42.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(OnLight))
    }
}

@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(AppRadius.xs).background(Color.White.copy(alpha = 0.14f)).border(1.dp, Color.White.copy(alpha = 0.06f), AppRadius.xs).padding(horizontal = 9.dp, vertical = 4.dp)) {
        Text(text, style = androidx.compose.ui.text.TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 13.sp), color = Color.White.copy(alpha = 0.92f))
    }
}

@Composable
fun ControlRow(modifier: Modifier = Modifier, horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp), content: @Composable () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = horizontalArrangement) { content() }
}
