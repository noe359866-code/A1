@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.omnitv.app.R
import com.omnitv.app.data.ConfirmKeyUpGuard
import com.omnitv.app.data.Profile
import com.omnitv.app.data.TvKeyCodes
import com.omnitv.app.data.avatarLabel
import com.omnitv.app.data.avatarRes
import com.omnitv.app.ui.theme.BadgeAccent
import com.omnitv.app.ui.theme.FocusScale
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.GlassFillStrong
import com.omnitv.app.ui.theme.SurfaceCard
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import com.omnitv.app.ui.theme.appTween

/* -------------------------------------------------------------------------- */
/*  Avatar ilustrado estilo Netflix (tile cuadrado limpio con marco doble)    */
/* -------------------------------------------------------------------------- */

/** Cuadrado limpio de esquinas moderadas (estilo selector clásico de Netflix). */
fun avatarShape(size: Dp): RoundedCornerShape =
    RoundedCornerShape((size * 0.08f).coerceIn(6.dp, 14.dp))

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
                if (ringColor != null) Modifier.border(2.dp, ringColor, shape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), shape)
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
                    .padding(6.dp)
                    .clip(AppRadius.xs)
                    .background(BadgeAccent)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(badgeText, style = AppType.chip, color = Color.White)
            }
        }
    }
}

/** Marco doble blanco estilo Netflix al enfocar un tile cuadrado de perfil. */
@Composable
private fun ProfileTileFrame(
    focused: Boolean,
    size: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    val outerShape = AppRadius.md
    Box(
        modifier = Modifier
            .size(size + 12.dp)
            .then(
                if (focused) {
                    Modifier
                        .border(AppStroke.focus, Color.White, outerShape)
                        .padding(6.dp)
                } else {
                    Modifier.padding(6.dp)
                }
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun KidsChip(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(AppRadius.xs)
            .background(BadgeAccent)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text("KIDS", style = AppType.chip, color = Color.White)
    }
}

/* -------------------------------------------------------------------------- */
/*  Ficha de perfil (selector «¿Quién está viendo?», limpio estilo Netflix)   */
/* -------------------------------------------------------------------------- */

@Composable
fun ProfileTile(
    profile: Profile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onFocus: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FocusScale.hero else 1f, appTween(150), label = "tileScale")

    Column(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProfileTileFrame(focused = focused, size = 136.dp) {
            AvatarView(
                avatarIndex = profile.avatarIndex,
                size = 136.dp,
                ringColor = if (focused) Color.White else null,
                badgeText = if (profile.isKids) "KIDS" else null
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            profile.name,
            style = AppType.profileName,
            color = if (focused) Color.White else TextSecondary,
            textAlign = TextAlign.Center
        )
        if (isActive) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (focused) Color.White else Accent)
            )
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
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FocusScale.control else 1f, appTween(150), label = "manageScale")
    val shape = avatarShape(128.dp)

    Column(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEdit
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProfileTileFrame(focused = focused, size = 128.dp) {
            Box(contentAlignment = Alignment.Center) {
                AvatarView(
                    avatarIndex = profile.avatarIndex,
                    size = 128.dp,
                    ringColor = if (focused) Color.White else null,
                    badgeText = if (profile.isKids) "KIDS" else null
                )
                Box(
                    Modifier
                        .size(128.dp)
                        .clip(shape)
                        .background(Color.Black.copy(alpha = 0.50f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (focused) 0.96f else 0.28f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = "Editar ${profile.name}",
                            modifier = Modifier.size(22.dp),
                            colorFilter = ColorFilter.tint(if (focused) Background else Color.White)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            profile.name,
            style = AppType.profileName,
            color = if (focused) Color.White else TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Ficha "Añadir perfil" (cuadro limpio con + estilo Netflix)                */
/* -------------------------------------------------------------------------- */

@Composable
fun AddProfileTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FocusScale.hero else 1f, appTween(150), label = "addScale")
    val shape = avatarShape(136.dp)

    Column(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProfileTileFrame(focused = focused, size = 136.dp) {
            Box(
                Modifier
                    .size(136.dp)
                    .clip(shape)
                    .background(if (focused) Color.White.copy(alpha = 0.08f) else Background)
                    .border(
                        width = if (focused) 2.dp else 1.5.dp,
                        color = if (focused) Color.White else Color.White.copy(alpha = 0.30f),
                        shape = shape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "Añadir perfil",
                    modifier = Modifier.size(42.dp),
                    colorFilter = ColorFilter.tint(
                        if (focused) Color.White else TextSecondary
                    )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Añadir perfil",
            style = AppType.profileName,
            color = if (focused) Color.White else TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Avatar pequeño para la barra superior                                     */
/* -------------------------------------------------------------------------- */

@Composable
fun ProfileBadge(profile: Profile?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FocusScale.hero else 1f, appTween(150), label = "badgeScale")
    val shape = avatarShape(48.dp)

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, shape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
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
/*  Selector de avatar para el editor de perfiles                             */
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
    val scale by animateFloatAsState(if (focused) FocusScale.hero else 1f, appTween(140), label = "optionScale")
    val shape = avatarShape(72.dp)

    Column(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.md)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
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
    val confirmFocus = remember { FocusRequester() }
    val keyUpGuard = remember { ConfirmKeyUpGuard(initialSuppressUntilDown = true) }
    RequestTvFocus(cancelFocus)
    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(Background.copy(alpha = 0.95f))
            .consumeStrayConfirmKeyUp(keyUpGuard)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && TvKeyCodes.isBack(event.nativeKeyEvent.keyCode)) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(540.dp)
                .focusGroup()
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
                    modifier = Modifier
                        .focusRequester(cancelFocus)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = confirmFocus
                        }
                )
                PillButton(
                    text = confirmText,
                    primary = isDanger,
                    danger = isDanger,
                    onClick = onConfirm,
                    modifier = Modifier
                        .focusRequester(confirmFocus)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            left = cancelFocus
                            right = FocusRequester.Cancel
                        }
                )
            }
        }
    }
}
