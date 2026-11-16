@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.AVATAR_COUNT
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.TvKeypad
import com.omnitv.app.data.avatarLabel
import com.omnitv.app.data.avatarRes
import com.omnitv.app.ui.components.AvatarOption
import com.omnitv.app.ui.components.AvatarView
import com.omnitv.app.ui.components.KidsChip
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.SettingRow
import com.omnitv.app.ui.components.TvConfirmDialog
import com.omnitv.app.ui.components.TvKeyboard
import com.omnitv.app.ui.components.TvTextField
import com.omnitv.app.ui.components.TvToggle
import com.omnitv.app.ui.components.dpadRepeatThrottle
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/** Editor de perfiles optimizado para Android TV y D-pad. */
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
    var autoPlayNext by rememberSaveable { mutableStateOf(existing?.autoPlayNext ?: true) }
    val canDelete = !isNew && existing != null && ProfileStore.profiles.size > 1

    var confirmDeleteOpen by remember { mutableStateOf(false) }
    var confirmClearHistoryOpen by remember { mutableStateOf(false) }
    var confirmClearMyListOpen by remember { mutableStateOf(false) }
    var restoreAfterDialog by remember { mutableStateOf<FocusRequester?>(null) }

    val nameFocus = remember { FocusRequester() }
    val clearHistoryFocus = remember { FocusRequester() }
    val clearMyListFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }
    val firstKeyFocus = remember { FocusRequester() }

    var keyboardOpen by rememberSaveable { mutableStateOf(false) }
    var wasKeyboardOpen by remember { mutableStateOf(false) }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!initialFocusDone && !keyboardOpen) {
            initialFocusDone = true
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { nameFocus.requestFocus() }.isSuccess) break
            }
        }
    }

    LaunchedEffect(keyboardOpen) {
        if (keyboardOpen) {
            wasKeyboardOpen = true
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { firstKeyFocus.requestFocus() }.isSuccess) break
            }
        } else if (wasKeyboardOpen) {
            wasKeyboardOpen = false
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { nameFocus.requestFocus() }.isSuccess) break
            }
        }
    }

    val anyDialogOpen = confirmDeleteOpen || confirmClearHistoryOpen || confirmClearMyListOpen
    LaunchedEffect(anyDialogOpen) {
        if (!anyDialogOpen) {
            val target = restoreAfterDialog
            if (target != null) {
                restoreAfterDialog = null
                for (attempt in 0 until 6) {
                    withFrameNanos { }
                    if (runCatching { target.requestFocus() }.isSuccess) break
                }
            }
        }
    }

    BackHandler(enabled = keyboardOpen) { keyboardOpen = false }

    val keyboardWidth = 496.dp
    Box(Modifier.fillMaxSize().background(Background).dpadRepeatThrottle()) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(if (keyboardOpen) 400.dp else 740.dp)
                .align(Alignment.TopStart)
                .padding(start = AppSpacing.screenMargin)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp)
        ) {
            // Cabecera con avatar grande y estado
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarView(avatarIndex = avatarIndex, size = 80.dp)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(
                        if (isNew) "Crear perfil" else "Editar perfil",
                        style = AppType.hero,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(avatarLabel(avatarIndex), style = AppType.metadata, color = TextSecondary)
                        if (isKids) KidsChip()
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Nombre del perfil (solo abre teclado al pulsar OK)
            Text("NOMBRE DEL PERFIL", style = AppType.chip, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            TvTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                placeholder = "Nombre del perfil",
                focusRequester = nameFocus,
                imeOnCenter = false,
                focusable = !keyboardOpen && !anyDialogOpen,
                onActivate = { keyboardOpen = true },
                actionHint = "OK para escribir",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            // Selector de avatar
            Text("AVATAR · Navega con las flechas", style = AppType.chip, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer()
            ) {
                items(AVATAR_COUNT) { index ->
                    AvatarOption(
                        resId = avatarRes(index),
                        selected = index == avatarIndex,
                        label = avatarLabel(index),
                        onClick = { avatarIndex = index }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Ajustes del perfil
            Text("PREFERENCIAS DEL PERFIL", style = AppType.chip, color = TextSecondary)
            Spacer(Modifier.height(12.dp))

            SettingRow(
                title = "Perfil infantil",
                subtitle = "Filtra el catálogo a contenido para toda la familia",
                trailing = { TvToggle(isKids, onCheckedChange = { isKids = it }) },
                modifier = Modifier.fillMaxWidth()
            )

            SettingRow(
                title = "Reproducción automática",
                subtitle = "Inicia el siguiente episodio al terminar el actual",
                trailing = { TvToggle(autoPlayNext, onCheckedChange = { autoPlayNext = it }) },
                modifier = Modifier.fillMaxWidth()
            )

            if (!isNew && existing != null) {
                if (existing.progress.isNotEmpty()) {
                    SettingRow(
                        title = "Continuar viendo (${existing.progress.size} títulos)",
                        subtitle = "Borra el progreso guardado de reproducción",
                        trailing = {
                            PillButton(
                                text = "Limpiar",
                                primary = false,
                                onClick = {
                                    restoreAfterDialog = clearHistoryFocus
                                    confirmClearHistoryOpen = true
                                },
                                modifier = Modifier.focusRequester(clearHistoryFocus)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (existing.myList.isNotEmpty()) {
                    SettingRow(
                        title = "Mi lista (${existing.myList.size} títulos)",
                        subtitle = "Vacía todos los títulos guardados como favoritos",
                        trailing = {
                            PillButton(
                                text = "Vaciar",
                                primary = false,
                                onClick = {
                                    restoreAfterDialog = clearMyListFocus
                                    confirmClearMyListOpen = true
                                },
                                modifier = Modifier.focusRequester(clearMyListFocus)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Botones de acción
            Row(
                modifier = Modifier.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PillButton(
                    text = "Guardar perfil",
                    iconRes = R.drawable.ic_check,
                    primary = true,
                    onClick = {
                        val finalName = name.ifBlank { if (isNew) "Perfil ${ProfileStore.profiles.size + 1}" else existing?.name ?: "Perfil" }
                        if (isNew) {
                            ProfileStore.add(finalName, avatarIndex, isKids, autoPlayNext)
                        } else if (existing != null) {
                            ProfileStore.update(
                                existing.copy(
                                    name = finalName,
                                    avatarIndex = avatarIndex,
                                    isKids = isKids,
                                    autoPlayNext = autoPlayNext
                                )
                            )
                        }
                        onSaved()
                    }
                )

                if (canDelete && existing != null) {
                    PillButton(
                        text = "Eliminar",
                        iconRes = R.drawable.ic_delete,
                        primary = false,
                        danger = true,
                        onClick = {
                            restoreAfterDialog = deleteFocus
                            confirmDeleteOpen = true
                        },
                        modifier = Modifier.focusRequester(deleteFocus)
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
        }

        // Teclado lateral para D-pad
        if (keyboardOpen) {
            Box(
                Modifier
                    .width(keyboardWidth)
                    .align(Alignment.CenterEnd)
                    .padding(end = AppSpacing.screenMargin)
            ) {
                TvKeyboard(
                    compact = true,
                    onKey = { key -> name = TvKeypad.apply(name, key).take(32) },
                    onDone = { keyboardOpen = false },
                    firstKeyFocus = firstKeyFocus
                )
            }
        }

        // Diálogos de confirmación
        if (confirmDeleteOpen && existing != null) {
            TvConfirmDialog(
                title = "¿Eliminar perfil?",
                message = "Se borrará permanentemente el perfil \"${existing.name}\", su lista personal y su progreso de reproducción.",
                confirmText = "Eliminar",
                cancelText = "Cancelar",
                isDanger = true,
                onConfirm = {
                    confirmDeleteOpen = false
                    ProfileStore.delete(existing.id)
                    onDeleted()
                },
                onDismiss = { confirmDeleteOpen = false }
            )
        }

        if (confirmClearHistoryOpen && existing != null) {
            TvConfirmDialog(
                title = "¿Limpiar 'Continuar viendo'?",
                message = "Se restablecerá el progreso de reproducción de todos los títulos para \"${existing.name}\".",
                confirmText = "Limpiar",
                cancelText = "Cancelar",
                isDanger = false,
                onConfirm = {
                    confirmClearHistoryOpen = false
                    ProfileStore.clearProgress(existing.id)
                },
                onDismiss = { confirmClearHistoryOpen = false }
            )
        }

        if (confirmClearMyListOpen && existing != null) {
            TvConfirmDialog(
                title = "¿Vaciar 'Mi lista'?",
                message = "Se eliminarán todos los títulos guardados en la lista de \"${existing.name}\".",
                confirmText = "Vaciar",
                cancelText = "Cancelar",
                isDanger = false,
                onConfirm = {
                    confirmClearMyListOpen = false
                    ProfileStore.clearMyList(existing.id)
                },
                onDismiss = { confirmClearMyListOpen = false }
            )
        }
    }
}
