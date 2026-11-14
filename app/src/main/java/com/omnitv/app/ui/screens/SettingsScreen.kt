package com.omnitv.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.Text
import com.omnitv.app.R
import com.omnitv.app.data.PairingServer
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.ui.components.*
import com.omnitv.app.ui.theme.*

@Composable
fun SettingsScreen(onSwitchProfile: () -> Unit) {
    val profile = ProfileStore.currentProfile
    val firstFocus = remember { FocusRequester() }
    RequestTvFocus(firstFocus)
    Column(Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.screenMargin)) {
        Spacer(Modifier.height(16.dp))
        Text("AJUSTES", style = AppType.overline, color = TextMuted)
        Spacer(Modifier.height(6.dp))
        Text("Ajustes", style = AppType.screenTitle, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Gestiona tu perfil, reproducción y conexiones", style = AppType.metadata, color = TextMuted)
        Spacer(Modifier.height(28.dp))

        SettingsCard(R.drawable.ic_settings, "Perfil") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (profile != null) {
                    AvatarView(profile.avatarIndex, 52.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, style = AppType.bodyStrong, color = TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        if (profile.isKids) KidsChip() else Text("Perfil estándar · Desliza para cambiar", style = AppType.caption, color = TextMuted)
                    }
                }
                PillButton("Cambiar", primary = false, onClick = onSwitchProfile)
            }
        }
        Spacer(Modifier.height(16.dp))

        SettingsCard(R.drawable.ic_link, "Vinculación") {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCode(PairingServer.pairingUrl(), Modifier.size(168.dp).clip(AppRadius.md).border(1.dp, GlassBorder, AppRadius.md))
                    Spacer(Modifier.height(10.dp))
                    Text(PairingServer.panelUrl(), style = AppType.caption, color = Accent, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.clip(AppRadius.pill).background(Accent.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("CÓDIGO  ${SettingsStore.pairingCode}", style = AppType.chip, color = Accent)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text("Conecta tu móvil", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text("Escanea el QR para abrir el panel web. Desde allí podrás añadir catálogos, fuentes de reproducción y claves API sin tocar el mando.", style = AppType.caption, color = TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    StatusGrid()
                    if (PairingServer.lastAction.value.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(PairingServer.lastAction.value, style = AppType.caption, color = Accent) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        SettingsCard(R.drawable.ic_play, "Reproducción") {
            SettingRow("Reproducción automática", "Inicia el vídeo al abrir la ficha", trailing = { TvToggle(SettingsStore.autoplay) { SettingsStore.setAutoplayEnabled(it) } })
            DividerThin()
            SettingRow("Siguiente automático", "Cuenta atrás de 10 s al terminar", trailing = { TvToggle(SettingsStore.autoNext) { SettingsStore.setAutoNextEnabled(it) } })
            DividerThin()
            SettingRow("Iniciar silenciado", "Comienza sin audio", trailing = { TvToggle(SettingsStore.mutedStart) { SettingsStore.setMutedStartEnabled(it) } })
        }
        Spacer(Modifier.height(16.dp))

        SettingsCard(R.drawable.ic_quality, "Calidad y audio") {
            SettingRow("Calidad preferida", "Se aplica al iniciar cada vídeo", trailing = { ValuePill(SettingsStore.preferredQuality) }, modifier = Modifier.focusRequester(firstFocus), onClick = { val p = SettingsStore.QUALITY_PRESETS; SettingsStore.savePreferredQuality(p[(p.indexOf(SettingsStore.preferredQuality) + 1) % p.size]) })
            DividerThin()
            SettingRow("Velocidad", "Recordada entre vídeos", trailing = { ValuePill(SettingsStore.speedLabel()) }, onClick = { val p = SettingsStore.SPEED_PRESETS; SettingsStore.savePlaybackSpeed(p[(p.indexOf(SettingsStore.playbackSpeed) + 1) % p.size]) })
            DividerThin()
            SettingRow("Subtítulos por defecto", "Actívalos si hay pistas disponibles", trailing = { TvToggle(SettingsStore.subtitlesDefault) { SettingsStore.setSubtitlesByDefault(it) } })
        }
        Spacer(Modifier.height(16.dp))

        SettingsCard(R.drawable.ic_server, "Red y sistema") {
            SettingRow("DNS de Cloudflare", "Resuelve nombres por HTTPS cuando el router falla", trailing = { TvToggle(SettingsStore.cloudflareDns) { SettingsStore.saveCloudflareDns(it) } })
            DividerThin()
            SettingRow("Motor", "Media3 ExoPlayer · MP4 · HLS · 4K", trailing = { ValuePill("Media3") })
            DividerThin()
            SettingRow("Versión", "Omni TV para Android TV", trailing = { ValuePill("1.1.0") })
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable private fun SettingsCard(iconRes: Int, title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(AppRadius.lg).background(SurfaceCard).border(1.dp, Color.White.copy(alpha = 0.06f), AppRadius.lg)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(AppRadius.sm).background(Accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Image(painterResource(iconRes), null, Modifier.size(18.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Accent))
            }
            Spacer(Modifier.width(12.dp))
            Text(title.uppercase(), style = AppType.overline, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Accent.copy(alpha = 0.9f)))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
        content()
    }
}
@Composable private fun DividerThin() { Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Color.White.copy(alpha = 0.06f))) }
@Composable private fun ValuePill(v: String) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.clip(AppRadius.pill).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 6.dp)) { Text(v, style = AppType.caption, color = TextPrimary) }; Spacer(Modifier.width(8.dp)); Text("›", style = AppType.sectionTitle, color = TextMuted) } }
@Composable private fun StatusGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusLine("Servidor", if (PairingServer.running.value) "Activo · ${PairingServer.localIpv4()}:8080" else "Iniciando…")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusLine("Addons", "${SettingsStore.customAddons.size}")
            StatusLine("Fuentes", "${SettingsStore.streamAddons.size}")
            StatusLine("APIs", "${SettingsStore.apiKeys.size}")
        }
        StatusLine("Cuenta", SettingsStore.linkedAccount.ifEmpty { "Sin enlazar" })
        StatusLine("Seguridad", if (SettingsStore.apiKeys.isEmpty()) "Sin claves" else if (SettingsStore.secretsEncrypted) "Cifradas en el televisor" else "Sin cifrar")
    }
}
@Composable private fun StatusLine(label: String, value: String) { Row { Text("$label  ", style = AppType.caption, color = TextMuted); Text(value, style = AppType.caption, color = TextPrimary) } }

@Composable private fun QrCode(data: String, modifier: Modifier = Modifier) {
    val bmp = remember(data) {
        runCatching {
            val hints = mapOf(com.google.zxing.EncodeHintType.MARGIN to 1, com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M)
            val m = com.google.zxing.qrcode.QRCodeWriter().encode(data, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512, hints)
            val b = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
            for (x in 0 until 512) for (y in 0 until 512) b.setPixel(x, y, if (m.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE); b
        }.getOrNull()
    }
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp.asImageBitmap(), "QR de vinculación", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) else Text("QR", style = AppType.sectionTitle, color = Color.Black)
    }
}
