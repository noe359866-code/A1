@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.material.Text
import com.omnitv.app.R
import com.omnitv.app.data.Profile
import com.omnitv.app.data.avatarRes
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.GlassFillStrong
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/* -------------------------------------------------------------------------- */
/*  Avatar ilustrado estilo Netflix (tile cuadrado redondeado)                */
/* -------------------------------------------------------------------------- */

/** Radio de esquina proporcional al tamaño, como los tiles de Netflix. */
private fun avatarShape(size: Dp): RoundedCornerShape = RoundedCornerShape(size * 0.22f)

@Composable
fun AvatarView(
    avatarIndex: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    ringColor: Color? = null
) {
    val shape = avatarShape(size)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(
                if (ringColor != null) Modifier.border(3.dp, ringColor, shape)
                else Modifier
            )
    ) {
        Image(
            painter = painterResource(avatarRes(avatarIndex)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun KidsChip(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(AppRadius.xs)
            .background(Accent)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text("KIDS", style = AppType.chip, color = Color.White)
    }
}

/* -------------------------------------------------------------------------- */
/*  Ficha de perfil (selector «¿Quién está viendo?»)                          */
/* -------------------------------------------------------------------------- */

@Composable
fun ProfileTile(
    profile: Profile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.10f else 1f, tween(160), label = "tileScale")

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.lg)
            .then(if (focused) Modifier.border(3.dp, Color.White, AppRadius.lg) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AvatarView(
            avatarIndex = profile.avatarIndex,
            size = 132.dp,
            ringColor = if (focused) Color.White else null
        )
        Spacer(Modifier.height(14.dp))
        Text(
            profile.name,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp
            ),
            color = if (focused) TextPrimary else TextSecondary
        )
        if (profile.isKids) {
            Spacer(Modifier.height(8.dp))
            KidsChip()
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
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(160), label = "manageScale")
    val shape = avatarShape(118.dp)

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEdit)
            .onFocusChanged { focused = it.isFocused }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            AvatarView(
                avatarIndex = profile.avatarIndex,
                size = 118.dp,
                ringColor = if (focused) Color.White else null
            )
            // Velo + lápiz centrado, como el modo edición de Netflix
            Box(
                Modifier
                    .size(118.dp)
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = "Editar ${profile.name}",
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            profile.name,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp
            ),
            color = if (focused) TextPrimary else TextSecondary
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Ficha "Añadir perfil"                                                     */
/* -------------------------------------------------------------------------- */

@Composable
fun AddProfileTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(160), label = "addScale")
    val shape = avatarShape(132.dp)

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(shape)
                .background(if (focused) GlassFillStrong else GlassFill)
                .then(
                    if (focused) Modifier.border(3.dp, Color.White, shape)
                    else Modifier.border(3.dp, TextSecondary.copy(alpha = 0.4f), shape)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "Añadir perfil",
                modifier = Modifier.size(40.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    if (focused) TextPrimary else TextSecondary
                )
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Añadir perfil",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp
            ),
            color = if (focused) TextPrimary else TextSecondary
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Avatar pequeño para la barra superior                                     */
/* -------------------------------------------------------------------------- */

@Composable
fun ProfileBadge(profile: Profile?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.10f else 1f, tween(150), label = "badgeScale")
    // 48 dp: el mínimo que se puede enfocar con un mando sin que cueste acertar.
    val shape = avatarShape(48.dp)

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(
                if (focused) Modifier.border(3.dp, Color.White, shape)
                else Modifier
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
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
                    .size(42.dp)
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
fun AvatarOption(resId: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, tween(140), label = "optionScale")
    val shape = AppRadius.md

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(64.dp)
            .clip(shape)
            .then(
                if (selected || focused) Modifier.border(3.dp, Color.White, shape)
                else Modifier
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Campo de nombre (estilo campo de búsqueda)                                */
/* -------------------------------------------------------------------------- */

@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    // Mismo tratamiento que la búsqueda: el mando abre el teclado en pantalla
    // con OK. Sin eso, en un televisor no se puede escribir el nombre.
    // Se crea siempre (no solo cuando falta el del llamante) para no hacer un
    // `remember` condicional, que rompe la composición si el parámetro cambia.
    val ownRequester = remember { FocusRequester() }
    TvTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "Nombre del perfil",
        modifier = modifier,
        // El `FocusRequester` tiene que ir al campo (nodo enfocable), no al Row
        // que lo envuelve: si va al Row, `requestFocus` no mueve nada.
        focusRequester = focusRequester ?: ownRequester
    )
}
