package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.ui.components.AddProfileTile
import com.omnitv.app.ui.components.ManageProfileTile
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.compose.material.Text

/**
 * Gestión de perfiles (modo edición de Netflix):
 * cada avatar muestra un lápiz; al pulsar se abre el editor.
 */
@Composable
fun ManageProfilesScreen(
    onEdit: (String) -> Unit,
    onAddProfile: () -> Unit,
    onDone: () -> Unit
) {
    val profiles = ProfileStore.profiles

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Text(
            "Gestionar perfiles",
            style = AppType.screenTitle,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Selecciona un perfil para editarlo o elimínalo",
            style = AppType.body,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(48.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)
        ) {
            profiles.forEach { profile ->
                ManageProfileTile(profile = profile, onEdit = { onEdit(profile.id) })
            }
            AddProfileTile(onClick = onAddProfile)
        }

        Spacer(Modifier.weight(1f))

        PillButton(
            text = "Listo",
            iconRes = R.drawable.ic_check,
            primary = true,
            onClick = onDone
        )

        Spacer(Modifier.height(64.dp))
    }
}
