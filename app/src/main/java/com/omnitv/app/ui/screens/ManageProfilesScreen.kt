package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.ui.components.AddProfileTile
import com.omnitv.app.ui.components.ManageProfileTile
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.theme.*

@Composable
fun ManageProfilesScreen(
    onEdit: (String) -> Unit,
    onAddProfile: () -> Unit,
    onDone: () -> Unit
) {
    val profiles = ProfileStore.profiles

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Background,
                    1f to Color(0xFF0E0E14)
                )
            )
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            // Header con overline
            Text("GESTIÓN", style = AppType.overline, color = TextMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Gestionar perfiles",
                style = AppType.screenTitle,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Toca un avatar para editarlo · Añade hasta 5 perfiles",
                style = AppType.metadata,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            // Indicador de progreso sutil
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { idx ->
                    Box(
                        Modifier
                            .size(if (idx < profiles.size) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (idx < profiles.size) Accent else Color.White.copy(alpha = 0.14f))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("${profiles.size}/5 perfiles", style = AppType.caption, color = TextTertiary)
            }

            Spacer(Modifier.height(48.dp))

            // Grid de perfiles
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                profiles.forEach { profile ->
                    ManageProfileTile(profile = profile, onEdit = { onEdit(profile.id) })
                }
                if (profiles.size < 5) {
                    AddProfileTile(onClick = onAddProfile)
                }
            }

            Spacer(Modifier.weight(1f))

            // Footer con acciones
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PillButton(
                    text = "Listo",
                    iconRes = R.drawable.ic_check,
                    primary = true,
                    onClick = onDone
                )
                Spacer(Modifier.height(12.dp))
                Text("Los cambios se guardan automáticamente", style = AppType.caption, color = TextTertiary)
            }

            Spacer(Modifier.height(40.dp))
        }

        // Brillo inferior sutil
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.04f))
        )
    }
}
