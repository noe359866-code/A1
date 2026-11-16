package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.Profile
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.ui.components.AddProfileTile
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PinDialog
import com.omnitv.app.ui.components.ProfileTile
import com.omnitv.app.ui.components.ProvideHorizontalTvBringIntoView
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.dpadRepeatThrottle
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary

/** Acción que queda en espera de verificar el PIN parental. */
private sealed interface PendingAction {
    data class Select(val profile: Profile) : PendingAction
    data object Manage : PendingAction
    data object Add : PendingAction
}

/**
 * Puerta de entrada de la app, diseñada para Android TV:
 * «¿Quién está viendo?» con avatares cuadrados limpios y doble marco blanco al enfocar.
 *
 * Si hay un PIN parental y el perfil activo es infantil, salir a un perfil
 * estándar o abrir la gestión de perfiles exige el PIN.
 */
@Composable
fun ProfileSelectScreen(
    onSelected: (Profile) -> Unit,
    onManage: () -> Unit,
    onAddProfile: () -> Unit
) {
    val profiles = ProfileStore.profiles
    val currentId = ProfileStore.currentProfileId
    val pinRequired = SettingsStore.parentalPinEnabled && ProfileStore.currentProfile?.isKids == true
    var pending by remember { mutableStateOf<PendingAction?>(null) }

    val initialFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }
    val manageFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }

    val initialProfileIndex = remember(profiles, currentId) {
        val idx = profiles.indexOfFirst { it.id == currentId }
        if (idx >= 0) idx else 0
    }
    val targetRequester = when {
        lastFocusedKey == "add" -> addFocus
        lastFocusedKey == "manage" -> manageFocus
        lastFocusedKey != null && profiles.any { "profile:${it.id}" == lastFocusedKey } -> restoreFocus
        else -> initialFocus
    }
    RequestTvFocus(targetRequester, key = (pending == null) to (profiles.firstOrNull()?.id ?: ""), enabled = pending == null)

    fun runAction(action: PendingAction) = when (action) {
        is PendingAction.Select -> onSelected(action.profile)
        PendingAction.Manage -> onManage()
        PendingAction.Add -> onAddProfile()
    }

    /**
     * Solo hace falta el PIN para acciones que **escalan** desde el perfil infantil
     * a contenido o gestión de adultos:
     *  - Select a un perfil estándar (o al mismo estándar) desde un perfil infantil.
     *  - Gestionar o crear perfiles (editar/eliminar/crear perfiles estándar).
     *
     * Moverse lateralmente entre perfiles infantiles, o re-elegir el perfil ya
     * activo, no eleva privilegios y no debe pedir PIN.
     */
    fun needsPin(action: PendingAction): Boolean = when (action) {
        is PendingAction.Select ->
            action.profile.id != ProfileStore.currentProfileId && !action.profile.isKids
        PendingAction.Manage, PendingAction.Add -> true
    }

    fun request(action: PendingAction) {
        if (pinRequired && needsPin(action)) pending = action else runAction(action)
    }

    Box(Modifier.fillMaxSize().background(Background).dpadRepeatThrottle()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Accent.copy(alpha = 0.14f),
                        0.45f to Color.Transparent
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.screenMargin, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("OMNI TV", style = AppType.brandMark, color = Accent)

            Spacer(Modifier.height(10.dp))

            Text(
                "¿Quién está viendo?",
                style = AppType.hero,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            ProvideHorizontalTvBringIntoView(leadingPadding = AppSpacing.screenMargin) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .focusGroup()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Top
                ) {
                    profiles.forEachIndexed { index, profile ->
                        val key = "profile:${profile.id}"
                        ProfileTile(
                            profile = profile,
                            isActive = profile.id == currentId,
                            onFocus = { lastFocusedKey = key },
                            onClick = { request(PendingAction.Select(profile)) },
                            modifier = Modifier
                                .then(if (index == initialProfileIndex) Modifier.focusRequester(initialFocus) else Modifier)
                                .then(if (lastFocusedKey == key) Modifier.focusRequester(restoreFocus) else Modifier)
                        )
                    }
                    AddProfileTile(
                        onFocus = { lastFocusedKey = "add" },
                        onClick = { request(PendingAction.Add) },
                        modifier = Modifier.focusRequester(addFocus)
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            PillButton(
                text = "Gestionar perfiles",
                iconRes = R.drawable.ic_edit,
                primary = false,
                onFocus = { lastFocusedKey = "manage" },
                onClick = { request(PendingAction.Manage) },
                modifier = Modifier.focusRequester(manageFocus)
            )
        }

        val action = pending
        if (action != null) {
            PinDialog(
                title = "PIN de control parental",
                subtitle = "Introduce el PIN para salir del perfil infantil",
                onDismiss = { pending = null },
                onSubmit = { pin ->
                    if (SettingsStore.verifyParentalPin(pin)) {
                        pending = null
                        runAction(action)
                        true
                    } else false
                }
            )
        }
    }
}
