package com.omnitv.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.omnitv.app.R
import com.omnitv.app.data.PairingServer
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.ui.components.AvatarView
import com.omnitv.app.ui.components.KidsChip
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.SettingRow
import com.omnitv.app.ui.components.TvToggle
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.compose.material.Text

/**
 * Ajustes de Omni TV: tarjetas con iconos, interruptores tipo iOS y
 * valores ciclables (calidad preferida, velocidad) persistidos en
 * [SettingsStore].
 */
@Composable
fun SettingsScreen(onSwitchProfile: () -> Unit) {
    val profile = ProfileStore.currentProfile

    // Foco inicial en «Calidad preferida», la primera fila pulsable del
    // listado: el mando entra en el contenido y no tiene que recorrer la
    // tarjeta de perfil para empezar a ajustar cosas.
    val firstFocus = remember { FocusRequester() }
    RequestTvFocus(firstFocus)

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

        /* ---------------- Vincular (QR) ---------------- */
        SettingsCard(iconRes = R.drawable.ic_link, title = "Vincular cuenta · Addons · APIs") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.Top
            ) {
                // QR con la URL debajo (como el ejemplo del panel)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCode(
                        data = PairingServer.pairingUrl(),
                        modifier = Modifier
                            .size(176.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        PairingServer.panelUrl(),
                        style = AppType.metadata,
                        color = Accent,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.width(30.dp))

                // Información e instrucciones
                Column(Modifier.weight(1f)) {
                    Text("Escanea el QR con tu móvil", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "El QR abre el panel web ya desbloqueado; si escribes la dirección a mano, " +
                            "te pedirá el código de abajo. Solo entonces podrás enlazar tu cuenta, " +
                            "añadir addons (manifest.json de Stremio) y registrar APIs.",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Código de emparejamiento: ${SettingsStore.pairingCode}",
                        style = AppType.metadata,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(12.dp))
                    StatusLine("Servidor", if (PairingServer.running.value) "Activo en ${PairingServer.localIpv4()}:8080" else "Iniciando…")
                    StatusLine("Cuenta enlazada", SettingsStore.linkedAccount.ifEmpty { "Sin enlazar" })
                    StatusLine("Addons añadidos", "${SettingsStore.customAddons.size}")
                    StatusLine("Addons de streams", "${SettingsStore.streamAddons.size}")
                    StatusLine("APIs añadidas", "${SettingsStore.apiKeys.size}")
                    StatusLine(
                        "Claves de API",
                        if (SettingsStore.apiKeys.isEmpty()) {
                            "Sin claves"
                        } else if (SettingsStore.secretsEncrypted) {
                            "Cifradas en el televisor"
                        } else {
                            "Sin cifrar (Android 5.1 no admite el cifrado)"
                        }
                    )
                    if (PairingServer.lastAction.value.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(PairingServer.lastAction.value, style = AppType.metadata, color = Accent)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        /* ---------------- Reproducción ---------------- */
        SettingsCard(iconRes = R.drawable.ic_play, title = "Reproducción") {
            SettingRow(
                title = "Autoplay",
                subtitle = "Los vídeos empiezan a reproducirse solos al abrirlos",
                trailing = { TvToggle(SettingsStore.autoplay, onCheckedChange = { SettingsStore.setAutoplayEnabled(it) }) }
            )
            SettingRow(
                title = "Siguiente automático",
                subtitle = "Al terminar, reproduce el siguiente título con cuenta atrás de 10 s",
                trailing = { TvToggle(SettingsStore.autoNext, onCheckedChange = { SettingsStore.setAutoNextEnabled(it) }) }
            )
            SettingRow(
                title = "Iniciar silenciado",
                subtitle = "Los vídeos empiezan sin audio",
                trailing = { TvToggle(SettingsStore.mutedStart, onCheckedChange = { SettingsStore.setMutedStartEnabled(it) }) }
            )
        }

        Spacer(Modifier.height(20.dp))

        /* ---------------- Calidad y velocidad ---------------- */
        SettingsCard(iconRes = R.drawable.ic_quality, title = "Calidad y velocidad") {
            SettingRow(
                title = "Calidad preferida",
                subtitle = "Se aplica automáticamente al iniciar cada vídeo",
                trailing = { ValueText(SettingsStore.preferredQuality) },
                modifier = Modifier.focusRequester(firstFocus),
                onClick = {
                    val presets = SettingsStore.QUALITY_PRESETS
                    val next = presets[(presets.indexOf(SettingsStore.preferredQuality) + 1) % presets.size]
                    SettingsStore.savePreferredQuality(next)
                }
            )
            SettingRow(
                title = "Velocidad de reproducción",
                subtitle = "Se recuerda entre vídeos · pulsa para cambiar",
                trailing = { ValueText(SettingsStore.speedLabel()) },
                onClick = {
                    val presets = SettingsStore.SPEED_PRESETS
                    val next = presets[(presets.indexOf(SettingsStore.playbackSpeed) + 1) % presets.size]
                    SettingsStore.savePlaybackSpeed(next)
                }
            )
            SettingRow(
                title = "Subtítulos por defecto",
                subtitle = "Activa los subtítulos automáticamente si hay pistas",
                trailing = { TvToggle(SettingsStore.subtitlesDefault, onCheckedChange = { SettingsStore.setSubtitlesByDefault(it) }) }
            )
        }

        Spacer(Modifier.height(20.dp))

        /* ---------------- Red ---------------- */
        SettingsCard(iconRes = R.drawable.ic_server, title = "Red") {
            SettingRow(
                title = "DNS de Cloudflare",
                subtitle = "Si el DNS de tu router falla o filtra, los nombres se resuelven " +
                    "por DNS sobre HTTPS (cloudflare-dns.com, 1.1.1.1). El certificado se " +
                    "sigue comprobando contra el nombre original; solo se usa https://",
                trailing = { TvToggle(SettingsStore.cloudflareDns, onCheckedChange = { SettingsStore.saveCloudflareDns(it) }) }
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
            .background(Color(0xFF15151B))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
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
                    fontSize = 22.sp
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

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.padding(bottom = 5.dp)) {
        Text("$label: ", style = AppType.metadata, color = TextSecondary)
        Text(value, style = AppType.metadata, color = TextPrimary)
    }
}

/** QR generado con ZXing a partir del texto dado (URL de emparejamiento). */
@Composable
private fun QrCode(data: String, modifier: Modifier = Modifier) {
    val bitmap = remember(data) {
        runCatching {
            val hints = mapOf(
                com.google.zxing.EncodeHintType.MARGIN to 1,
                com.google.zxing.EncodeHintType.ERROR_CORRECTION to
                    com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
            )
            val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                data,
                com.google.zxing.BarcodeFormat.QR_CODE,
                512,
                512,
                hints
            )
            val bmp = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
            for (x in 0 until 512) {
                for (y in 0 until 512) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        }.getOrNull()
    }

    Box(
        modifier.background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Código QR de vinculación",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            )
        } else {
            Text("QR", style = AppType.sectionTitle, color = Color.Black)
        }
    }
}
