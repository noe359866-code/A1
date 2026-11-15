@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.Profile
import com.omnitv.app.data.avatarLabel
import com.omnitv.app.data.avatarRes
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.Danger
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.GlassFillStrong
import com.omnitv.app.ui.theme.SurfaceCard
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/* -------------------------------------------------------------------------- */
/*  Avatar ilustrado estilo Netflix / Android TV (tile cuadrado redondeado)    */
/* -------------------------------------------------------------------------- */

/** Radio de esquina proporcional al tamaño, como los tiles de Netflix / Apple TV. */
fun avatarShape(size: Dp): RoundedCornerShape = RoundedCornerShape(size * 0.22f)

@Composable
fun AvatarView(
    avatarIndex: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    ringColor: Color? = null,
    badgeText: String? = null
) {
    val shape = avatarShape(size)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(
                if (ringColor != null) Modifier.border(AppStroke.focus, ringColor, shape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            )
    ) {
        Image(
            painter = painterResource(avatarRes(avatarIndex)),
            contentDescription = avatarLabel(avatarIndex),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (badgeText != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(AppRadius.xs)
                    .background(Accent)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(badgeText, style = AppType.chip, color = Color.White)
            }
        }
    }
}

@Composable
fun KidsChip(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(AppRadius.xs)
            .background(Accent)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text("KIDS", style = AppType.chip, color = Color.White)
    }
}

@Composable
fun ActiveProfileChip(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(AppRadius.xs)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text("ACTIVO", style = AppType.chip, color = Color.White)
    }
}

/* -------------------------------------------------------------------------- */
/*  Ficha de perfil (selector «¿Quién está viendo?»)                          */
/* -------------------------------------------------------------------------- */

@Composable
fun ProfileTile(
    profile: Profile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(160), label = "tileScale")

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.lg)
            .background(if (focused) SurfaceElevated else Color.Transparent)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.lg)
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            AvatarView(
                avatarIndex = profile.avatarIndex,
                size = 136.dp,
                ringColor = if (focused) Color.White else null
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            profile.name,
            style = AppType.profileName,
            color = if (focused) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profile.isKids) {
                KidsChip()
            }
            if (isActive) {
                ActiveProfileChip()
            }
            if (!profile.isKids && !isActive) {
                val subtitle = when {
                    profile.progress.isNotEmpty() -> "${profile.progress.size} en curso"
                    profile.myList.isNotEmpty() -> "${profile.myList.size} en Mi Lista"
                    else -> "Estándar"
                }
                Text(subtitle, style = AppType.metadata, color = if (focused) TextSecondary else Color.White.copy(alpha = 0.45f))
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Ficha para "gestionar perfiles" (superposición de lápiz)                  */
/* -------------------------------------------------------------------------- */

@Composable
fun ManageProfileTile(
    profile: Profile,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.10f else 1f, tween(160), label = "manageScale")
    val shape = avatarShape(126.dp)

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.lg)
            .background(if (focused) SurfaceElevated else Color.Transparent)
            .then(if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.lg) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEdit
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            AvatarView(
                avatarIndex = profile.avatarIndex,
                size = 126.dp,
                ringColor = if (focused) Color.White else null
            )
            Box(
                Modifier
                    .size(126.dp)
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.48f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (focused) 0.95f else 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = "Editar ${profile.name}",
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(if (focused) Background else Color.White)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            profile.name,
            style = AppType.profileName,
            color = if (focused) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        if (profile.isKids) {
            KidsChip()
        } else {
            Text("Pulsar para editar", style = AppType.metadata, color = if (focused) Accent else TextSecondary)
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Ficha "Añadir perfil"                                                     */
/* -------------------------------------------------------------------------- */

@Composable
fun AddProfileTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.10f else 1f, tween(160), label = "addScale")
    val shape = avatarShape(136.dp)

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.lg)
            .background(if (focused) SurfaceElevated else Color.Transparent)
            .then(if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.lg) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(136.dp)
                .clip(shape)
                .background(if (focused) GlassFillStrong else GlassFill)
                .then(
                    if (focused) Modifier.border(AppStroke.focus, Color.White, shape)
                    else Modifier.border(2.dp, TextSecondary.copy(alpha = 0.35f), shape)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "Añadir perfil",
                modifier = Modifier.size(44.dp),
                colorFilter = ColorFilter.tint(
                    if (focused) TextPrimary else TextSecondary
                )
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Añadir perfil",
            style = AppType.profileName,
            color = if (focused) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text("Nuevo usuario", style = AppType.metadata, color = Color.White.copy(alpha = 0.45f))
    }
}

/* -------------------------------------------------------------------------- */
/*  Avatar pequeño para la barra superior                                     */
/* -------------------------------------------------------------------------- */

@Composable
fun ProfileBadge(profile: Profile?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(150), label = "badgeScale")
    val shape = avatarShape(48.dp)

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, shape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
    ) {
        if (profile != null) {
            Image(
                painter = painterResource(avatarRes(profile.avatarIndex)),
                contentDescription = "Perfil ${profile.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(shape)
                    .background(GlassFillStrong),
                contentAlignment = Alignment.Center
            ) {
                Text("?", style = AppType.cardTitle, color = TextPrimary)
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Selector de avatar para el editor de perfiles                              */
/* -------------------------------------------------------------------------- */

@Composable
fun AvatarOption(
    resId: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, tween(140), label = "optionScale")
    val shape = avatarShape(72.dp)

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.md)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(shape)
                .then(
                    when {
                        focused -> Modifier.border(AppStroke.focus, Color.White, shape)
                        selected -> Modifier.border(AppStroke.focus, Accent, shape)
                        else -> Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                    }
                )
        ) {
            Image(
                painter = painterResource(resId),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = "Seleccionado",
                        modifier = Modifier.size(12.dp),
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                }
            }
        }
        if (label.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = AppType.chip,
                color = if (focused || selected) TextPrimary else TextSecondary,
                maxLines = 1
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Diálogo de confirmación para TV D-pad                                     */
/* -------------------------------------------------------------------------- */

@Composable
fun TvConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirmar",
    cancelText: String = "Cancelar",
    isDanger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }
    RequestTvFocus(cancelFocus)
    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(Background.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(540.dp)
                .clip(AppRadius.lg)
                .background(SurfaceCard)
                .border(1.dp, Color.White.copy(alpha = 0.12f), AppRadius.lg)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = AppType.sectionTitle, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text(message, style = AppType.body, color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton(
                    text = cancelText,
                    primary = !isDanger,
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(cancelFocus)
                )
                PillButton(
                    text = confirmText,
                    primary = isDanger,
                    danger = isDanger,
                    onClick = onConfirm
                )
            }
        }
    }
}
