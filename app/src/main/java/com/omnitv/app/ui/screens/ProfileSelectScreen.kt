package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.Profile
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.ui.components.*
import com.omnitv.app.ui.theme.*

@Composable
fun ProfileSelectScreen(onSelected: (Profile) -> Unit, onManage: () -> Unit, onAddProfile: () -> Unit) {
    val profiles = ProfileStore.profiles
    val firstFocus = remember { FocusRequester() }
    RequestTvFocus(firstFocus, key = profiles.firstOrNull()?.id)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0B0B0F),
                    0.25f to Color(0xFF101018),
                    0.60f to Background,
                    1f to Background
                )
            )
    ) {
        // Halo superior — profundidad cinematográfica
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.10f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0.5f * 1920f, 0f),
                        radius = 900f
                    )
                )
        )

        // Contenido centrado
        Column(
            Modifier.fillMaxSize().padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Marca
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("OMNI", style = AppType.brand, color = TextPrimary)
                Text("TV", style = AppType.brand, color = Accent)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Accent).padding(2.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("OMNI TV", style = AppType.overline, color = TextTertiary)
            Spacer(Modifier.height(28.dp))

            // Título
            Text(
                "¿Quién está viendo?",
                style = AppType.display,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Elige tu perfil para una experiencia personalizada",
                style = AppType.body,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            // Perfiles — con contenedor que centra incluso con pocos
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                profiles.forEachIndexed { i, p ->
                    ProfileTile(
                        profile = p,
                        onClick = { onSelected(p) },
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
                AddProfileTile(onClick = onAddProfile)
            }

            Spacer(Modifier.height(52.dp))

            // Acciones secundarias
            PillButton(
                text = "Gestionar perfiles",
                iconRes = R.drawable.ic_settings,
                primary = false,
                onClick = onManage
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "${profiles.size} perfiles  ·  ∞ listas personales  ·  Pulsa OK para entrar",
                style = AppType.caption,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }

        // Footer sutil
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(Success))
                Spacer(Modifier.width(8.dp))
                Text("Listo para ver", style = AppType.caption, color = TextTertiary)
            }
        }
    }
}
