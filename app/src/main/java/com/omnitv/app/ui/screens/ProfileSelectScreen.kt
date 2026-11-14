package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * Puerta de entrada de la app, igual que Netflix:
 * «¿Quién está viendo?» con los avatares de los perfiles.
 *
 * Si hay un PIN parental y el perfil activo es infantil, salir a un perfil
 * estándar (o abrir la gestión de perfiles) exige el PIN. Es un bloqueo de
 * uso doméstico: no sustituye a un control de acceso del sistema.
 */
@Composable
fun ProfileSelectScreen(
    onSelected: (Profile) -> Unit,
    onManage: () -> Unit,
    onAddProfile: () -> Unit
) {
    val profiles = ProfileStore.profiles
    val pinRequired = SettingsStore.parentalPinEnabled && ProfileStore.currentProfile?.isKids == true
    var pending by remember { mutableStateOf<PendingAction?>(null) }

    // Foco inicial en el primer perfil: con el mando, el foco por defecto cae
    // en «Gestionar perfiles» y el primer OK no entra a ver nada. Mientras
    // esté abierta la capa del PIN, el foco debe quedarse en su teclado.
    val firstProfileFocus = remember { FocusRequester() }
    if (pending == null) RequestTvFocus(firstProfileFocus, key = profiles.firstOrNull()?.id)

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
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("OMNI TV", style = AppType.brandMark, color = com.omnitv.app.ui.theme.Accent)

            Spacer(Modifier.height(16.dp))

            Text(
                "¿Quién está viendo?",
                style = AppType.hero,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(52.dp))

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)
            ) {
                profiles.forEachIndexed { index, profile ->
                    ProfileTile(
                        profile = profile,
                        onClick = { request(PendingAction.Select(profile)) },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstProfileFocus)
                        } else {
                            Modifier
                        }
                    )
                }
                AddProfileTile(onClick = { request(PendingAction.Add) })
            }

            Spacer(Modifier.height(56.dp))

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
