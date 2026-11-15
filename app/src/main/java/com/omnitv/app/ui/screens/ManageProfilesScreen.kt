package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.ui.components.AddProfileTile
import com.omnitv.app.ui.components.ManageProfileTile
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/**
 * Gestión de perfiles para Android TV:
 * cada avatar muestra un icono de edición; al pulsar con el D-pad se abre el editor.
 */
@Composable
fun ManageProfilesScreen(
    onEdit: (String) -> Unit,
    onAddProfile: () -> Unit,
    onDone: () -> Unit
) {
    val profiles = ProfileStore.profiles
    val firstFocus = remember { FocusRequester() }
    RequestTvFocus(firstFocus)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Gestionar perfiles",
            style = AppType.hero,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Selecciona un perfil para editar su nombre, avatar o preferencias",
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
                ManageProfileTile(
                    profile = profile,
                    onEdit = { onEdit(profile.id) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                )
            }
            AddProfileTile(onClick = onAddProfile)
        }

        Spacer(Modifier.height(48.dp))

        PillButton(
            text = "Listo",
            iconRes = R.drawable.ic_check,
            primary = true,
            onClick = onDone
        )
    }
}
