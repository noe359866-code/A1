package com.omnitv.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitv.app.R
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.ui.components.AvatarView
import com.omnitv.app.ui.components.KidsChip
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.SettingRow
import com.omnitv.app.ui.components.TvToggle
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.tv.material.Text

/**
 * Ajustes de Omni TV: tarjetas con iconos, interruptores tipo iOS y
 * valores ciclables (calidad preferida, velocidad) persistidos en
 * [SettingsStore].
 */
@Composable
fun SettingsScreen(onSwitchProfile: () -> Unit) {
    val profile = ProfileStore.currentProfile

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 64.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Ajustes", style = AppType.screenTitle, color = TextPrimary)
        Spacer(Modifier.height(28.dp))

        /* ---------------- Perfil ---------------- */
        SettingsCard(iconRes = R.drawable.ic_settings, title = "Perfil") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (profile != null) {
                    AvatarView(avatarIndex = profile.avatarIndex, size = 56.dp)
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(profile.name, style = AppType.body, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        if (profile.isKids) KidsChip() else Text(
                            "Perfil estándar",
                            style = AppType.metadata,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                PillButton(
                    text = "Cambiar de perfil",
                    primary = false,
                    onClick = onSwitchProfile
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        /* ---------------- Reproducción ---------------- */
        SettingsCard(iconRes = R.drawable.ic_play, title = "Reproducción") {
            SettingRow(
                title = "Autoplay",
                subtitle = "Los vídeos empiezan a reproducirse solos al abrirlos",
                trailing = { TvToggle(SettingsStore.autoplay, onCheckedChange = { SettingsStore.setAutoplay(it) }) }
            )
            SettingRow(
                title = "Siguiente automático",
                subtitle = "Al terminar, reproduce el siguiente título con cuenta atrás de 10 s",
                trailing = { TvToggle(SettingsStore.autoNext, onCheckedChange = { SettingsStore.setAutoNext(it) }) }
            )
            SettingRow(
                title = "Iniciar silenciado",
                subtitle = "Los vídeos empiezan sin audio",
                trailing = { TvToggle(SettingsStore.mutedStart, onCheckedChange = { SettingsStore.setMutedStart(it) }) }
            )
        }

        Spacer(Modifier.height(20.dp))

        /* ---------------- Calidad y velocidad ---------------- */
        SettingsCard(iconRes = R.drawable.ic_quality, title = "Calidad y velocidad") {
            SettingRow(
                title = "Calidad preferida",
                subtitle = "Se aplica automáticamente al iniciar cada vídeo",
                trailing = { ValueText(SettingsStore.preferredQuality) },
                onClick = {
                    val presets = SettingsStore.QUALITY_PRESETS
                    val next = presets[(presets.indexOf(SettingsStore.preferredQuality) + 1) % presets.size]
                    SettingsStore.setPreferredQuality(next)
                }
            )
            SettingRow(
                title = "Velocidad de reproducción",
                subtitle = "Se recuerda entre vídeos · pulsa para cambiar",
                trailing = { ValueText(SettingsStore.speedLabel()) },
                onClick = {
                    val presets = SettingsStore.SPEED_PRESETS
                    val next = presets[(presets.indexOf(SettingsStore.playbackSpeed) + 1) % presets.size]
                    SettingsStore.setPlaybackSpeed(next)
                }
            )
            SettingRow(
                title = "Subtítulos por defecto",
                subtitle = "Activa los subtítulos automáticamente si hay pistas",
                trailing = { TvToggle(SettingsStore.subtitlesDefault, onCheckedChange = { SettingsStore.setSubtitlesDefault(it) }) }
            )
        }

        Spacer(Modifier.height(20.dp))

        /* ---------------- Sistema ---------------- */
        SettingsCard(iconRes = R.drawable.ic_server, title = "Sistema") {
            SettingRow(
                title = "Idioma",
                subtitle = "Idioma de la interfaz",
                trailing = { ValueText("Español") }
            )
            SettingRow(
                title = "Motor de reproducción",
                subtitle = "Reproductor integrado con soporte MP4, HLS y 4K",
                trailing = { ValueText("Media3 ExoPlayer") }
            )
            SettingRow(
                title = "Acerca de",
                subtitle = "Omni TV · diseñado para Android TV",
                trailing = { ValueText("1.0.0") }
            )
        }

        Spacer(Modifier.height(56.dp))
    }
}

/* -------------------------------------------------------------------------- */
/*  Tarjeta de ajustes                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun SettingsCard(
    iconRes: Int,
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassFill)
            .padding(vertical = 10.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Accent)
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                color = TextPrimary
            )
        }
        content()
    }
}

@Composable
private fun ValueText(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = AppType.metadata, color = Accent)
        Spacer(Modifier.width(8.dp))
        Text("›", style = AppType.sectionTitle, color = TextSecondary)
    }
}
