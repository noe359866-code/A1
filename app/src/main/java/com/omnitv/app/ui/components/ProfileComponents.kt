@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitv.app.R
import com.omnitv.app.data.Profile
import com.omnitv.app.data.avatarRes
import com.omnitv.app.ui.theme.*

// ────────────────────────────────────────────────────────────────────────────
//  Avatar — forma Netflix (22% radio), con borde interior sutil y sombra
// ────────────────────────────────────────────────────────────────────────────

private fun avatarShape(size: Dp): RoundedCornerShape = RoundedCornerShape(size * 0.22f)

@Composable
fun AvatarView(
    avatarIndex: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    ringColor: Color? = null,
    showBorder: Boolean = true
) {
    val shape = avatarShape(size)
    Box(
        modifier
            .size(size)
            .shadow(if (ringColor != null) 14.dp else 6.dp, shape, clip = false)
            .clip(shape)
            .then(if (ringColor != null) Modifier.border(3.dp, ringColor, shape) else Modifier)
            .then(if (showBorder && ringColor == null) Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), shape) else Modifier)
    ) {
        Image(
            painter = painterResource(avatarRes(avatarIndex)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Brillo superior sutil — efecto vidrio
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.08f),
                    0.45f to Color.Transparent
                )
            )
        )
    }
}

@Composable
fun KidsChip(modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier
            .clip(AppRadius.xs)
            .background(Brush.linearGradient(listOf(Color(0xFFFFD60A), Color(0xFFFF9F0A))))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 3.dp)
    ) {
        Text("KIDS", style = AppType.chip.copy(fontSize = if (compact) 11.sp else 13.sp, letterSpacing = 0.8.sp), color = Color(0xFF1A1A1A))
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  ProfileTile — selector principal, con halo y tipografía premium
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfileTile(
    profile: Profile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(AppMotion.Normal, easing = AppMotion.Emphasized), label = "tileScale")
    val labelColor by androidx.compose.animation.animateColorAsState(if (focused) TextPrimary else TextSecondary, tween(120), label = "labelColor")

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.lg)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Contenedor con halo exterior al enfocar
        Box(contentAlignment = Alignment.Center) {
            if (focused) {
                Box(
                    Modifier.size(148.dp).clip(avatarShape(148.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(4.dp)
                ) {}
            }
            AvatarView(
                avatarIndex = profile.avatarIndex,
                size = 140.dp,
                ringColor = if (focused) Color.White else null,
                showBorder = !focused
            )
            // Indicador KIDS sobre avatar
            if (profile.isKids) {
                Box(Modifier.align(Alignment.BottomCenter).offset(y = 8.dp)) {
                    KidsChip(compact = true)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            profile.name,
            style = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 20.sp, letterSpacing = 0.1.sp),
            color = labelColor,
            maxLines = 1
        )
        if (profile.isKids) {
            Spacer(Modifier.height(4.dp))
            Text("Infantil", style = AppType.caption, color = if (focused) TextSecondary else TextTertiary)
        }
        // Línea de acento sutil bajo el nombre al enfocar
        if (focused) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(24.dp).height(2.dp).clip(AppRadius.pill).background(Accent))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  ManageProfileTile — modo edición con velo y lápiz en círculo
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun ManageProfileTile(
    profile: Profile,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(AppMotion.Normal, easing = AppMotion.Emphasized), label = "manageScale")
    val shape = avatarShape(124.dp)

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onEdit)
            .onFocusChanged { focused = it.isFocused }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            AvatarView(
                avatarIndex = profile.avatarIndex,
                size = 124.dp,
                ringColor = if (focused) Color.White else null
            )
            // Velo oscuro permanente en modo gestión + icono lápiz
            Box(
                Modifier.size(124.dp).clip(shape).background(Color.Black.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (focused) Color.White else Color.White.copy(alpha = 0.92f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = "Editar ${profile.name}",
                        modifier = Modifier.size(20.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF0B0B0F))
                    )
                }
            }
            // Badge KIDS si aplica
            if (profile.isKids) {
                Box(Modifier.align(Alignment.BottomCenter).offset(y = 6.dp)) { KidsChip(compact = true) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            profile.name,
            style = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 18.sp),
            color = if (focused) TextPrimary else TextSecondary
        )
        Text("Editar", style = AppType.caption, color = if (focused) Accent else TextTertiary)
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  AddProfileTile — minimalista con borde discontinuo simulado
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun AddProfileTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(AppMotion.Normal, easing = AppMotion.Emphasized), label = "addScale")
    val shape = avatarShape(140.dp)

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(140.dp).clip(shape)
                .background(
                    if (focused) Color.White.copy(alpha = 0.10f)
                    else Brush.verticalGradient(0f to Color.White.copy(alpha = 0.06f), 1f to Color.White.copy(alpha = 0.03f))
                )
                .border(
                    width = if (focused) 3.dp else 1.5.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.22f),
                    shape = shape
                )
                // Borde interior punteado simulado con segundo borde
                .then(
                    if (!focused) Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(140.dp * 0.20f))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(if (focused) Color.White else Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text("+", style = TextStyle(FontFamily.SansSerif, FontWeight.Light, 36.sp, lineHeight = 36.sp), color = if (focused) Background else TextSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Añadir perfil", style = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 18.sp), color = if (focused) TextPrimary else TextSecondary)
        Text("Nuevo", style = AppType.caption, color = TextTertiary)
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  ProfileBadge — avatar compacto para TopBar
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfileBadge(profile: Profile?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(AppMotion.Fast), label = "badgeScale")
    val shape = avatarShape(44.dp)

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(shape)
                .border(1.5.dp, Color.White.copy(alpha = 0.18f), shape)
        ) {
            if (profile != null) {
                Image(
                    painter = painterResource(avatarRes(profile.avatarIndex)),
                    contentDescription = "Perfil ${profile.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(GlassFillStrong), contentAlignment = Alignment.Center) {
                    Text("?", style = AppType.caption, color = TextPrimary)
                }
            }
        }
        if (profile != null) {
            Spacer(Modifier.width(8.dp))
            Column {
                Text(profile.name, style = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 14.sp), color = TextPrimary, maxLines = 1)
                if (profile.isKids) Text("KIDS", style = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 10.sp, letterSpacing = 0.8.sp), color = Accent)
            }
            Spacer(Modifier.width(6.dp))
            Text("▾", style = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 10.sp), color = TextTertiary)
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  AvatarOption — selector de avatar en editor
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun AvatarOption(resId: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(AppMotion.Fast), label = "optionScale")
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(72.dp)
            .shadow(if (selected || focused) 8.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .border(
                width = when { focused -> 3.dp; selected -> 2.dp; else -> 0.dp },
                color = when { focused -> Color.White; selected -> Accent; else -> Color.Transparent },
                shape = shape
            )
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
    ) {
        Image(painter = painterResource(resId), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (selected) {
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Accent.copy(alpha = 0.35f)))
            )
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(20.dp).clip(CircleShape)
                    .background(Accent).border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", style = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 11.sp), color = Color.White)
            }
        }
        if (focused && !selected) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  NameField — campo de texto para TV, con focus y placeholder premium
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val ownRequester = remember { FocusRequester() }
    TvTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "Nombre del perfil",
        modifier = modifier,
        focusRequester = focusRequester ?: ownRequester
    )
}
