package com.omnitv.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.AVATAR_COUNT
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.TvKeypad
import com.omnitv.app.data.avatarRes
import com.omnitv.app.ui.components.AvatarOption
import com.omnitv.app.ui.components.AvatarView
import com.omnitv.app.ui.components.KidsChip
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.SettingRow
import com.omnitv.app.ui.components.TvKeyboard
import com.omnitv.app.ui.components.TvTextField
import com.omnitv.app.ui.components.TvToggle
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/** Editor de perfiles operable íntegramente con el D-pad, sin IME del sistema. */
@Composable
fun EditProfileScreen(
    profileId: String,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    val isNew = profileId == "new"
    val existing = if (isNew) null else ProfileStore.profiles.firstOrNull { it.id == profileId }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var avatarIndex by rememberSaveable { mutableStateOf(existing?.avatarIndex ?: 0) }
    var isKids by rememberSaveable { mutableStateOf(existing?.isKids ?: false) }
    val canDelete = !isNew && existing != null && ProfileStore.profiles.size > 1

    val nameFocus = remember { FocusRequester() }
    val firstKeyFocus = remember { FocusRequester() }
    var keyboardOpen by rememberSaveable { mutableStateOf(false) }
    var keyboardJustClosed by remember { mutableStateOf(false) }
    RequestTvFocus(nameFocus)

    LaunchedEffect(keyboardOpen) {
        if (keyboardOpen) {
            withFrameNanos { }
            runCatching { firstKeyFocus.requestFocus() }
        }
    }
    fun closeKeyboard() {
        keyboardJustClosed = true
        keyboardOpen = false
        runCatching { nameFocus.requestFocus() }
    }
    BackHandler(enabled = keyboardOpen) { closeKeyboard() }

    // El teclado ocupa su propia columna. El formulario se desplaza con el
    // foco y nunca queda debajo de las teclas (también en el editor de perfil).
    val keyboardWidth = 496.dp
    Box(Modifier.fillMaxSize().background(Background)) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(if (keyboardOpen) 368.dp else 688.dp)
                .align(Alignment.TopStart)
                .padding(start = 48.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarView(avatarIndex = avatarIndex, size = 72.dp)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(if (isNew) "Perfil nuevo" else "Editar perfil", style = AppType.sectionTitle, color = TextPrimary)
                    if (isKids) {
                        Spacer(Modifier.height(6.dp))
                        KidsChip()
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("NOMBRE", style = AppType.chip, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            TvTextField(
                value = name,
                onValueChange = { name = it.take(64) },
                placeholder = "Nombre del perfil",
                focusRequester = nameFocus,
                imeOnCenter = false,
                focusable = !keyboardOpen,
                onFocusChange = { focused ->
                    if (focused && !keyboardJustClosed) keyboardOpen = true
                    keyboardJustClosed = false
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Text("AVATAR · desliza con las flechas", style = AppType.chip, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AVATAR_COUNT) { index ->
                    AvatarOption(
                        resId = avatarRes(index),
                        selected = index == avatarIndex,
                        onClick = { avatarIndex = index }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            SettingRow(
                title = "Perfil infantil",
                subtitle = "Catálogo local TP/7+ · sin PIN",
                trailing = { TvToggle(isKids, onCheckedChange = { isKids = it }) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    text = "Guardar",
                    iconRes = R.drawable.ic_check,
                    onClick = {
                        if (isNew) ProfileStore.add(name, avatarIndex, isKids)
                        else if (existing != null) {
                            ProfileStore.update(existing.copy(
                                name = name.ifBlank { existing.name },
                                avatarIndex = avatarIndex,
                                isKids = isKids
                            ))
                        }
                        onSaved()
                    }
                )
                if (canDelete && existing != null) {
                    PillButton(
                        text = "Eliminar",
                        primary = false,
                        danger = true,
                        onClick = {
                            ProfileStore.delete(existing.id)
                            onDeleted()
                        }
                    )
                }
            }
        }
        if (keyboardOpen) {
            Box(
                Modifier.width(keyboardWidth).align(Alignment.CenterEnd).padding(end = 48.dp)
            ) {
                TvKeyboard(
                    compact = true,
                    onKey = { key -> name = TvKeypad.apply(name, key) },
                    onDone = { closeKeyboard() },
                    firstKeyFocus = firstKeyFocus
                )
            }
        }
    }
}
