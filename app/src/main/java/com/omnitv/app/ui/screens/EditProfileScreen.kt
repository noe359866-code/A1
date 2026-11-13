package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.AvatarPalette
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.ui.components.AvatarCircle
import com.omnitv.app.ui.components.ColorDot
import com.omnitv.app.ui.components.NameField
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.SettingRow
import com.omnitv.app.ui.components.TvToggle
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.tv.material.Text

/**
 * Editor de perfil: nombre, color de avatar y modo infantil.
 * Con `profileId == "new"` crea un perfil nuevo.
 */
@Composable
fun EditProfileScreen(
    profileId: String,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    val isNew = profileId == "new"
    val existing = if (isNew) null else ProfileStore.profiles.firstOrNull { it.id == profileId }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var colorIndex by rememberSaveable { mutableStateOf(existing?.colorIndex ?: 0) }
    var isKids by rememberSaveable { mutableStateOf(existing?.isKids ?: false) }

    val canDelete = !isNew && ProfileStore.profiles.size > 1

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 48.dp, vertical = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vista previa del avatar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(280.dp)
        ) {
            AvatarCircle(
                colorIndex = colorIndex,
                initial = name.trim().firstOrNull()?.uppercase() ?: "?",
                size = 170.dp
            )
            Spacer(Modifier.height(18.dp))
            Text(
                if (isNew) "Perfil nuevo" else "Editar perfil",
                style = AppType.sectionTitle,
                color = TextPrimary
            )
            if (isKids) {
                Spacer(Modifier.height(10.dp))
                Text("Perfil infantil", style = AppType.metadata, color = TextSecondary)
            }
        }

        Spacer(Modifier.width(56.dp))

        Column(Modifier.weight(1f)) {
            Text("NOMBRE", style = AppType.chip, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            NameField(value = name, onValueChange = { name = it }, modifier = Modifier.width(480.dp))

            Spacer(Modifier.height(28.dp))
            Text("COLOR DEL AVATAR", style = AppType.chip, color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AvatarPalette.forEachIndexed { index, color ->
                    ColorDot(
                        color = color,
                        selected = index == colorIndex,
                        onClick = { colorIndex = index }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            SettingRow(
                title = "Perfil infantil",
                subtitle = "Solo muestra contenido apto para todos los públicos",
                trailing = { TvToggle(isKids, onCheckedChange = { isKids = it }) },
                modifier = Modifier.width(560.dp)
            )

            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton(
                    text = "Guardar",
                    iconRes = R.drawable.ic_check,
                    primary = true,
                    onClick = {
                        if (isNew) {
                            ProfileStore.add(name, colorIndex, isKids)
                        } else if (existing != null) {
                            ProfileStore.update(
                                existing.copy(
                                    name = name.ifBlank { existing.name },
                                    colorIndex = colorIndex,
                                    isKids = isKids
                                )
                            )
                        }
                        onSaved()
                    }
                )
                if (canDelete && existing != null) {
                    PillButton(
                        text = "Eliminar perfil",
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
    }
}
