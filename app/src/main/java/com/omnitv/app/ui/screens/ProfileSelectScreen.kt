package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    Box(Modifier.fillMaxSize().background(Background)) {
        // Sutil viñeteado superior como en tvOS
        Box(Modifier.fillMaxWidth().height(220.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Accent.copy(alpha = 0.08f), 1f to Background.copy(alpha = 0f))))
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("OMNI", style = AppType.brand, color = TextPrimary)
                Text("TV", style = AppType.brand, color = Accent)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Accent))
            }
            Spacer(Modifier.height(10.dp))
            Text("OMNI TV", style = AppType.overline, color = TextMuted)
            Spacer(Modifier.height(16.dp))
            Text("¿Quién está viendo?", style = AppType.display, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("Elige un perfil para continuar", style = AppType.metadata, color = TextMuted)
            Spacer(Modifier.height(44.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                profiles.forEachIndexed { i, p -> ProfileTile(p, onClick = { onSelected(p) }, modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) }
                AddProfileTile(onClick = onAddProfile)
            }
            Spacer(Modifier.height(48.dp))
            PillButton("Gestionar perfiles", R.drawable.ic_settings, false, onClick = onManage)
            Spacer(Modifier.height(16.dp))
            Text("${profiles.size} perfiles · Pulsa OK para entrar", style = AppType.caption, color = TextTertiary)
        }
    }
}
