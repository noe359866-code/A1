package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.Profile
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.ui.components.AddProfileTile
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.ProfileTile
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import androidx.tv.material.Text

/**
 * Puerta de entrada de la app, igual que Netflix:
 * «¿Quién está viendo?» con los avatares de los perfiles.
 */
@Composable
fun ProfileSelectScreen(
    onSelected: (Profile) -> Unit,
    onManage: () -> Unit,
    onAddProfile: () -> Unit
) {
    val profiles = ProfileStore.profiles

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "¿Quién está viendo?",
            style = AppType.hero,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(52.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            profiles.forEach { profile ->
                ProfileTile(profile = profile, onClick = { onSelected(profile) })
            }
            AddProfileTile(onClick = onAddProfile)
        }

        Spacer(Modifier.height(56.dp))

        PillButton(
            text = "Gestionar perfiles",
            iconRes = R.drawable.ic_settings,
            primary = false,
            onClick = onManage
        )
    }
}
