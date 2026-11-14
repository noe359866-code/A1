package com.omnitv.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.omnitv.app.BuildConfig
import com.omnitv.app.R
import com.omnitv.app.data.PairingServer
import com.omnitv.app.data.AddonRegistry
import com.omnitv.app.data.NuvioPluginManager
import com.omnitv.app.data.NuvioScraperResolver
import com.omnitv.app.data.CloudstreamBridge
import com.omnitv.app.data.MediaBridge
import com.omnitv.app.data.PlaybackDiagnostics
import com.omnitv.app.data.SkipKind
import com.omnitv.app.data.SkipSegmentParser
import com.omnitv.app.data.SkipSegmentService
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.ui.components.AvatarView
import com.omnitv.app.ui.components.KidsChip
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PinDialog
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.SettingRow
import com.omnitv.app.ui.components.TvToggle
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.SurfaceCard
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.compose.material.Text

/**
 * Ajustes de Omni TV: tarjetas con iconos, interruptores tipo iOS y
 * valores ciclables (calidad preferida, velocidad) persistidos en
 * [SettingsStore].
 */
/** Motivo por el que se pide el PIN en esta pantalla. */
private enum class PinTask { GATE, SET, CLEAR }

@Composable
fun SettingsScreen(
    onSwitchProfile: () -> Unit,
    onExit: () -> Unit = {}
) {
    val profile = ProfileStore.currentProfile
    // El QR ya NO incluye el código: el móvil lo pide al abrir el panel y lo
    // envía por POST. Así no queda en el historial ni en cabeceras Referer.
    val panelUrl = remember(PairingServer.running.value) { PairingServer.panelUrl() }
    val pairingUrl = remember(PairingServer.running.value) { PairingServer.pairingUrl() }

    // Un perfil infantil no puede ver ni cambiar ajustes sensibles sin el PIN.
    var pinUnlocked by remember { mutableStateOf(false) }
    var pinTask by remember { mutableStateOf<PinTask?>(null) }
    val gated = SettingsStore.parentalPinEnabled && profile?.isKids == true && !pinUnlocked
    LaunchedEffect(gated) { if (gated) pinTask = PinTask.GATE }

    // Foco inicial en «Calidad preferida», la primera fila pulsable del
    // listado: el mando entra en el contenido y no tiene que recorrer la
    // tarjeta de perfil para empezar a ajustar cosas.
    val firstFocus = remember { FocusRequester() }
    // Con el PIN pendiente, el foco inicial debe ir al teclado del diálogo,
    // no a una fila de ajustes tapada por la capa.
    if (!gated) RequestTvFocus(firstFocus)
    val scope = rememberCoroutineScope()
    var bridgeStatus by remember { mutableStateOf("Comprobando puente…") }
    var cloudstreamStatus by remember { mutableStateOf("Comprobando Cloudstream…") }
    var mediaStatus by remember { mutableStateOf("Comprobando puente multimedia…") }
    var refreshing by remember { mutableStateOf(false) }
    suspend fun checkIntegrations() {
        refreshing = true
        try {
            AddonRegistry.refresh(force = true)
            NuvioPluginManager.refreshAll(force = true)
            bridgeStatus = NuvioScraperResolver.checkBridge()
            cloudstreamStatus = CloudstreamBridge.check()
            mediaStatus = MediaBridge.check()
        } finally { refreshing = false }
    }
    LaunchedEffect(SettingsStore.revision) { checkIntegrations() }

    Box(Modifier.fillMaxSize()) {
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

        /* ---------------- Control parental ---------------- */
        SettingsCard(iconRes = R.drawable.ic_settings, title = "Control parental") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
                Text(
                    "Con un PIN de 4 cifras, un perfil infantil no puede salir a un perfil " +
                        "estándar, gestionar perfiles ni abrir estos ajustes. Se guarda solo " +
                        "como huella con salt en el televisor. Es un bloqueo de uso doméstico, " +
                        "no una restricción del sistema operativo.",
                    style = AppType.metadata,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                StatusLine("PIN parental", if (SettingsStore.parentalPinEnabled) "Activado" else "Sin configurar")
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(
                        text = if (SettingsStore.parentalPinEnabled) "Quitar PIN" else "Configurar PIN",
                        primary = false,
                        danger = SettingsStore.parentalPinEnabled,
                        onClick = {
                            pinTask = if (SettingsStore.parentalPinEnabled) PinTask.CLEAR else PinTask.SET
                        }
                    )
                }
                if (SettingsStore.parentalPinEnabled) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Para cambiarlo, quítalo con el PIN actual y vuelve a configurarlo.",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                }
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
                    if (pairingUrl.isNotEmpty()) {
                        QrCode(
                            data = pairingUrl,
                            modifier = Modifier.size(176.dp).clip(AppRadius.md)
                        )
                    } else {
                        Text("Sin red local", style = AppType.metadata, color = TextSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        panelUrl.ifEmpty { "Conecta el televisor a la red local" },
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
                        "Escanea el QR e introduce en el móvil el código de abajo. El código " +
                            "NO viaja en la URL: se envía por formulario y el panel abre una " +
                            "sesión temporal. Solo entonces podrás enlazar tu cuenta, instalar " +
                            "addons Stremio, guardar repositorios/puente Nuvio y registrar APIs.",
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
                    PillButton(
                        text = "Regenerar código",
                        primary = false,
                        onClick = { SettingsStore.regeneratePairingCode() }
                    )
                    Spacer(Modifier.height(12.dp))
                    StatusLine(
                        "Servidor",
                        when {
                            PairingServer.running.value -> "Activo · solo mientras la app está visible"
                            PairingServer.lastError.value.isNotEmpty() -> PairingServer.lastError.value
                            else -> "Detenido"
                        }
                    )
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

        /* ---------------- Addons y plugins ---------------- */
        SettingsCard(iconRes = R.drawable.ic_link, title = "Addons Stremio · Plugins Nuvio") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                Text("Stremio: manifests con resources. Nuvio: índices de scrapers JS.", style = AppType.metadata, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                if (AddonRegistry.statuses.value.isEmpty()) {
                    StatusLine("Stremio", "Sin addons instalados")
                } else AddonRegistry.statuses.value.forEach { StatusLine(it.label, it.summary) }
                Spacer(Modifier.height(8.dp))
                if (NuvioPluginManager.repositories.value.isEmpty()) {
                    StatusLine("Nuvio", "Sin repositorios guardados")
                } else NuvioPluginManager.repositories.value.forEach { StatusLine(it.label, it.summary) }
                StatusLine("Puente JS", bridgeStatus)
                StatusLine("Cloudstream", cloudstreamStatus)
                Text(
                    "Los repositorios Nuvio no se ejecutan en el televisor. Sin puente solo quedan guardados. " +
                        "Los torrents/P2P no se reproducen.",
                    style = AppType.metadata, color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                PillButton(
                    text = if (refreshing) "Comprobando…" else "Comprobar manifests y puentes",
                    primary = false,
                    onClick = { if (!refreshing) scope.launch { checkIntegrations() } }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        /* ---------------- Compatibilidad multimedia ---------------- */
        SettingsCard(iconRes = R.drawable.ic_play, title = "Compatibilidad multimedia") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                StatusLine("Puente FFmpeg externo", mediaStatus)
                StatusLine("URL", SettingsStore.ffmpegBridge.ifBlank { "Sin configurar · panel QR" })
                StatusLine("Último error", PlaybackDiagnostics.summary())
                PlaybackDiagnostics.current()?.let { report ->
                    Spacer(Modifier.height(6.dp))
                    Text("Fuente: ${report.sourceLabel}", style = AppType.metadata, color = TextSecondary)
                    Text(report.advice, style = AppType.metadata, color = TextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "El APK usa los decodificadores del dispositivo. FFmpeg/ffprobe solo se ejecutan en el puente opcional; " +
                        "si Media3 falla por códec/contenedor, el TV puede pedir una versión HLS compatible. " +
                        "Configura el puente y su token (FFMPEG_BRIDGE) desde el panel QR.",
                    style = AppType.metadata, color = TextSecondary
                )
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

        /* ---------------- Saltar intro y créditos ---------------- */
        SettingsCard(iconRes = R.drawable.ic_skip_next, title = "Saltar intro y créditos") {
            SettingRow(
                title = "Tramos saltables",
                subtitle = "Botón «Saltar» con los tiempos de la comunidad. Funciona sin clave: " +
                    "las bases de datos se leen de forma anónima desde tu propia conexión",
                trailing = { TvToggle(SettingsStore.skipSegmentsEnabled, onCheckedChange = { SettingsStore.saveSkipSegmentsEnabled(it) }) }
            )
            listOf(SkipKind.INTRO, SkipKind.RECAP, SkipKind.CREDITS).forEach { kind ->
                SettingRow(
                    title = "Saltar ${kind.label} automáticamente",
                    subtitle = "Si lo dejas apagado, aparece el botón y decides tú",
                    trailing = {
                        TvToggle(kind.name in SettingsStore.autoSkipKinds, onCheckedChange = { SettingsStore.setAutoSkip(kind, it) })
                    }
                )
            }
            SettingsStore.SKIP_SOURCE_NAMES.forEach { source ->
                SettingRow(
                    title = source,
                    subtitle = when (source) {
                        SkipSegmentParser.SOURCE_SKIPDB -> "Ajusta los tiempos a la duración de tu stream · necesita id IMDb"
                        SkipSegmentParser.SOURCE_THEINTRODB -> "Intro, resumen, créditos y avance · admite id TMDB o IMDb"
                        else -> "Intro, resumen y créditos · necesita id IMDb"
                    },
                    trailing = {
                        TvToggle(source in SettingsStore.skipSources, onCheckedChange = { SettingsStore.setSkipSource(source, it) })
                    }
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                StatusLine("Último título", SkipSegmentService.lastStatus.value.ifBlank { "Sin consultas todavía" })
                Text(
                    "Datos de SkipDB (ODbL 1.0), TheIntroDB e IntroDB. Omni TV no incluye ninguna clave API: " +
                        "cada usuario puede añadir la suya en el panel QR (THEINTRODB=tu_clave) para subir su " +
                        "límite de consultas. Los tiempos son comunitarios y pueden no encajar con tu versión del vídeo.",
                    style = AppType.metadata, color = TextSecondary
                )
            }
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
                subtitle = "Media3 ExoPlayer · MP4, HLS y DASH con los códecs del dispositivo; conversión externa opcional",
                trailing = { ValueText("Media3 ExoPlayer") }
            )
            SettingRow(
                title = "Acerca de",
                subtitle = "Omni TV · diseñado para Android TV",
                trailing = { ValueText(BuildConfig.VERSION_NAME) }
            )
        }

        Spacer(Modifier.height(56.dp))
    }

    // Capa de PIN: se dibuja encima del listado para que el mando no pueda
    // llegar a las filas de ajustes mientras esté bloqueada.
    val task = pinTask
    if (task != null) {
        key(task) {
            PinDialog(
                title = when (task) {
                    PinTask.GATE -> "Ajustes bloqueados"
                    PinTask.SET -> "Configurar PIN parental"
                    PinTask.CLEAR -> "Quitar PIN parental"
                },
                subtitle = when (task) {
                    PinTask.GATE -> "Introduce el PIN para ver los ajustes desde un perfil infantil"
                    PinTask.SET -> "Elige 4 cifras. Se guardará solo como huella cifrada en el televisor."
                    PinTask.CLEAR -> "Introduce el PIN actual para desactivar el control parental"
                },
                confirmEntry = task == PinTask.SET,
                onDismiss = if (task == PinTask.GATE) onExit else { pinTask = null },
                onSubmit = { pin ->
                    when (task) {
                        PinTask.GATE -> if (SettingsStore.verifyParentalPin(pin)) {
                            pinUnlocked = true
                            pinTask = null
                            true
                        } else false
                        PinTask.SET -> if (SettingsStore.saveParentalPin(pin)) {
                            pinTask = null
                            true
                        } else false
                        PinTask.CLEAR -> if (SettingsStore.verifyParentalPin(pin)) {
                            SettingsStore.clearParentalPin()
                            pinTask = null
                            true
                        } else false
                    }
                }
            )
        }
    }
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
            .clip(AppRadius.lg)
            .background(SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), AppRadius.lg)
            .padding(vertical = 10.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(AppRadius.sm)
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
