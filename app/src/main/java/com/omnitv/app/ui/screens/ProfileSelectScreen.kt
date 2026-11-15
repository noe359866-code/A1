package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/** Acción que queda en espera de verificar el PIN parental. */
private sealed interface PendingAction {
    data class Select(val profile: Profile) : PendingAction
    data object Manage : PendingAction
    data object Add : PendingAction
}

/**
 * Puerta de entrada de la app, diseñada para Android TV:
 * «¿Quién está viendo?» con los avatares ilustrados y navegación D-pad.
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

    // Foco inicial en el perfil activo (o el primero de la lista):
    // el usuario pulsa OK y entra inmediatamente a su perfil.
    val initialFocus = remember { FocusRequester() }
    val initialProfileIndex = remember(profiles, currentId) {
        val idx = profiles.indexOfFirst { it.id == currentId }
        if (idx >= 0) idx else 0
    }
    if (pending == null) RequestTvFocus(initialFocus, key = profiles.firstOrNull()?.id)

    fun runAction(action: PendingAction) = when (action) {
        is PendingAction.Select -> onSelected(action.profile)
        PendingAction.Manage -> onManage()
        PendingAction.Add -> onAddProfile()
    }

    fun request(action: PendingAction) {
        if (pinRequired) pending = action else runAction(action)
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("OMNI TV", style = AppType.brandMark, color = Accent)

            Spacer(Modifier.height(14.dp))

            Text(
                "¿Quién está viendo?",
                style = AppType.hero,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Elige tu perfil para continuar o gestiona tus cuentas",
                style = AppType.body,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                profiles.forEachIndexed { index, profile ->
                    ProfileTile(
                        profile = profile,
                        isActive = profile.id == currentId,
                        onClick = { request(PendingAction.Select(profile)) },
                        modifier = if (index == initialProfileIndex) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        }
                    )
                }
                AddProfileTile(onClick = { request(PendingAction.Add) })
            }

            Spacer(Modifier.height(48.dp))

            PillButton(
                text = "Gestionar perfiles",
                iconRes = R.drawable.ic_settings,
                primary = false,
                onClick = { request(PendingAction.Manage) }
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
