package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnitv.app.ui.components.SettingRow
import com.omnitv.app.ui.components.TvToggle
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.tv.material.Text

/**
 * Ajustes de la app (solo demostración: los valores no se persisten).
 */
@Composable
fun SettingsScreen() {
    var autoplay by remember { mutableStateOf(true) }
    var mutedStart by remember { mutableStateOf(false) }
    var previews by remember { mutableStateOf(true) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Ajustes", style = AppType.screenTitle, color = TextPrimary)
        Spacer(Modifier.height(28.dp))

        SectionLabel("Reproducción")
        SettingRow(
            title = "Reproducción automática",
            subtitle = "Continuar con el siguiente título sin preguntar",
            trailing = { TvToggle(autoplay, onCheckedChange = { autoplay = it }) }
        )
        SettingRow(
            title = "Iniciar silenciado",
            subtitle = "Los vídeos empiezan sin audio",
            trailing = { TvToggle(mutedStart, onCheckedChange = { mutedStart = it }) }
        )
        SettingRow(
            title = "Avances automáticos",
            subtitle = "Mostrar tráilers en la ficha de detalle",
            trailing = { TvToggle(previews, onCheckedChange = { previews = it }) }
        )

        Spacer(Modifier.height(24.dp))
        SectionLabel("General")
        SettingRow(
            title = "Calidad de vídeo",
            subtitle = "Automática (según tu conexión)",
            trailing = { ValueText("Automática") }
        )
        SettingRow(
            title = "Idioma",
            subtitle = "Idioma de la interfaz",
            trailing = { ValueText("Español") }
        )
        SettingRow(
            title = "Acerca de",
            subtitle = "Omni TV 1.0.0 · Media3 ExoPlayer · Compose for TV",
            trailing = { ValueText("") }
        )

        Spacer(Modifier.height(56.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = AppType.sectionTitle,
        color = TextPrimary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun ValueText(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = AppType.metadata, color = TextSecondary)
        Spacer(Modifier.width(10.dp))
    }
}
