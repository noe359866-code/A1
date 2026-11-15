package com.omnitv.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitv.app.BuildConfig
import com.omnitv.app.R
import com.omnitv.app.data.AddonRegistry
import com.omnitv.app.data.CloudstreamBridge
import com.omnitv.app.data.MediaBridge
import com.omnitv.app.data.NuvioPluginManager
import com.omnitv.app.data.NuvioScraperResolver
import com.omnitv.app.data.PairingServer
import com.omnitv.app.data.PlaybackDiagnostics
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.data.SkipKind
import com.omnitv.app.data.SkipSegmentParser
import com.omnitv.app.data.SkipSegmentService
import com.omnitv.app.ui.components.AvatarView
import com.omnitv.app.ui.components.KidsChip
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PinDialog
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.SettingRow
import com.omnitv.app.ui.components.TvToggle
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AccentGreen
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.Danger
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.SurfaceCard
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Categorías de ajustes en panel lateral para Android TV.
 */
enum class SettingsCategory(
    val title: String,
    val iconRes: Int,
    val subtitle: String
) {
    PROFILE("Perfil y Cuenta", R.drawable.ic_user, "Perfil activo, cambio de usuario y control parental"),
    PLAYBACK("Reproducción", R.drawable.ic_play, "Autoplay, siguiente episodio y subtítulos"),
    QUALITY("Calidad y Velocidad", R.drawable.ic_quality, "Resolución de vídeo y velocidad de reproducción"),
    SKIP("Saltar Intro", R.drawable.ic_skip_next, "Salto automático de intro, resumen y créditos"),
    PAIRING("Vincular y Addons", R.drawable.ic_link, "Código QR, panel web en red local y APIs"),
    INTEGRATIONS("Puentes y Plugins", R.drawable.ic_server, "Addons Stremio, Nuvio y FFmpeg externo"),
    NETWORK("Red y Privacidad", R.drawable.ic_server, "DNS sobre HTTPS de Cloudflare y seguridad local"),
    SYSTEM("Sistema", R.drawable.ic_info, "Versión de Omni TV, motor Media3 y soporte")
}

/** Motivo por el que se pide el PIN en esta pantalla. */
private enum class PinTask { GATE, SET, CLEAR }

/**
 * Pantalla de Ajustes de Omni TV para Android TV:
 *
 * Diseño Two-Pane de 10 pies inspirado en tvOS y Google TV:
 * - Panel lateral izquierdo con categorías para navegación rápida con D-pad.
 * - Panel derecho con las tarjetas y opciones de la categoría seleccionada.
 * - Diálogos modales con D-pad para selección de calidad y velocidad.
 */
@Composable
fun SettingsScreen(
    onSwitchProfile: () -> Unit,
    onExit: () -> Unit = {}
) {
    val profile = ProfileStore.currentProfile
    var selectedCategory by remember { mutableStateOf(SettingsCategory.PROFILE) }

    val panelUrl = remember(PairingServer.running.value) { PairingServer.panelUrl() }
    val pairingUrl = remember(PairingServer.running.value) { PairingServer.pairingUrl() }

    // Control parental en perfil infantil
    var pinUnlocked by remember { mutableStateOf(false) }
    var pinTask by remember { mutableStateOf<PinTask?>(null) }
    val gated = SettingsStore.parentalPinEnabled && profile?.isKids == true && !pinUnlocked
    LaunchedEffect(gated) { if (gated) pinTask = PinTask.GATE }

    // Foco inicial en la primera categoría del panel lateral
    val firstCategoryFocus = remember { FocusRequester() }
    if (!gated) RequestTvFocus(firstCategoryFocus)

    val scope = rememberCoroutineScope()
    var bridgeStatus by remember { mutableStateOf("Comprobando puente…") }
    var cloudstreamStatus by remember { mutableStateOf("Comprobando Cloudstream…") }
    var mediaStatus by remember { mutableStateOf("Comprobando puente multimedia…") }
    var refreshing by remember { mutableStateOf(false) }

    // Selectores modales
    var qualityDialogVisible by remember { mutableStateOf(false) }
    var speedDialogVisible by remember { mutableStateOf(false) }

    suspend fun checkIntegrations() {
        refreshing = true
        try {
            AddonRegistry.refresh(force = true)
            NuvioPluginManager.refreshAll(force = true)
            bridgeStatus = NuvioScraperResolver.checkBridge()
            cloudstreamStatus = CloudstreamBridge.check()
            mediaStatus = MediaBridge.check()
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(SettingsStore.revision) { checkIntegrations() }

    Box(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.screenMargin, vertical = 24.dp)
        ) {
            /* ------------------------------------------------------------------ */
            /*  Panel Izquierdo: Categorías de Ajustes                            */
            /* ------------------------------------------------------------------ */
            Column(
                Modifier
                    .width(330.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 24.dp)
            ) {
                Text("Ajustes", style = AppType.hero, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text("Configuración de Omni TV", style = AppType.metadata, color = TextSecondary)
                Spacer(Modifier.height(24.dp))

                SettingsCategory.values().forEachIndexed { index, category ->
                    val badgeText = when (category) {
                        SettingsCategory.PROFILE -> profile?.name
                        SettingsCategory.QUALITY -> SettingsStore.preferredQuality
                        SettingsCategory.PAIRING -> "${SettingsStore.customAddons.size + SettingsStore.streamAddons.size} addons"
                        SettingsCategory.NETWORK -> if (SettingsStore.cloudflareDns) "1.1.1.1" else null
                        SettingsCategory.SYSTEM -> "v${BuildConfig.VERSION_NAME}"
                        else -> null
                    }

                    CategoryItem(
                        category = category,
                        isSelected = category == selectedCategory,
                        badgeText = badgeText,
                        onFocus = { selectedCategory = category },
                        modifier = if (index == 0) Modifier.focusRequester(firstCategoryFocus) else Modifier
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            /* ------------------------------------------------------------------ */
            /*  Panel Derecho: Contenido de la Categoría Seleccionada             */
            /* ------------------------------------------------------------------ */
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, bottom = 48.dp)
            ) {
                // Cabecera descriptiva de la sección seleccionada
                SectionHeader(category = selectedCategory)
                Spacer(Modifier.height(20.dp))

                when (selectedCategory) {
                    SettingsCategory.PROFILE -> {
                        ProfileSettingsContent(
                            profile = profile,
                            onSwitchProfile = onSwitchProfile,
                            onConfigurePin = { pinTask = PinTask.SET },
                            onClearPin = { pinTask = PinTask.CLEAR }
                        )
                    }

                    SettingsCategory.PLAYBACK -> {
                        PlaybackSettingsContent()
                    }

                    SettingsCategory.QUALITY -> {
                        QualitySettingsContent(
                            onOpenQualityDialog = { qualityDialogVisible = true },
                            onOpenSpeedDialog = { speedDialogVisible = true }
                        )
                    }

                    SettingsCategory.SKIP -> {
                        SkipSettingsContent()
                    }

                    SettingsCategory.PAIRING -> {
                        PairingSettingsContent(
                            pairingUrl = pairingUrl,
                            panelUrl = panelUrl
                        )
                    }

                    SettingsCategory.INTEGRATIONS -> {
                        IntegrationsSettingsContent(
                            bridgeStatus = bridgeStatus,
                            cloudstreamStatus = cloudstreamStatus,
                            mediaStatus = mediaStatus,
                            refreshing = refreshing,
                            onRefresh = { scope.launch { checkIntegrations() } }
                        )
                    }

                    SettingsCategory.NETWORK -> {
                        NetworkSettingsContent()
                    }

                    SettingsCategory.SYSTEM -> {
                        SystemSettingsContent()
                    }
                }
            }
        }

        /* ------------------------------------------------------------------ */
        /*  Diálogos Modales                                                  */
        /* ------------------------------------------------------------------ */

        // Selector modal de Calidad
        if (qualityDialogVisible) {
            OptionSelectDialog(
                title = "Calidad de vídeo preferida",
                subtitle = "Se aplicará automáticamente al iniciar la reproducción de cualquier título",
                options = SettingsStore.QUALITY_PRESETS,
                selectedOption = SettingsStore.preferredQuality,
                labelProvider = { preset ->
                    when (preset) {
                        "Auto" -> "Auto (Mejor resolución disponible)"
                        "4K" -> "4K · Ultra HD (2160p)"
                        "1080p" -> "1080p · Full HD"
                        "720p" -> "720p · Alta definición"
                        "480p" -> "480p · Definición estándar"
                        else -> preset
                    }
                },
                onSelect = {
                    SettingsStore.savePreferredQuality(it)
                    qualityDialogVisible = false
                },
                onDismiss = { qualityDialogVisible = false }
            )
        }

        // Selector modal de Velocidad
        if (speedDialogVisible) {
            OptionSelectDialog(
                title = "Velocidad de reproducción",
                subtitle = "Se recuerda entre vídeos y puede cambiarse durante la reproducción",
                options = SettingsStore.SPEED_PRESETS,
                selectedOption = SettingsStore.playbackSpeed,
                labelProvider = { speed ->
                    when (speed) {
                        1f -> "1.0x (Velocidad normal)"
                        else -> SettingsStore.speedLabel(speed)
                    }
                },
                onSelect = {
                    SettingsStore.savePlaybackSpeed(it)
                    speedDialogVisible = false
                },
                onDismiss = { speedDialogVisible = false }
            )
        }

        // Capa de PIN parental
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
                        PinTask.SET -> "Elige 4 cifras. Se guardará como huella cifrada en el televisor."
                        PinTask.CLEAR -> "Introduce el PIN actual para desactivar el control parental"
                    },
                    confirmEntry = task == PinTask.SET,
                    onDismiss = {
                        if (task == PinTask.GATE) onExit() else pinTask = null
                    },
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
/*  Cabecera de Sección                                                       */
/* -------------------------------------------------------------------------- */

@Composable
private fun SectionHeader(category: SettingsCategory) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(AppRadius.sm)
                .background(Accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(category.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(Accent)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(category.title, style = AppType.sectionTitle, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(category.subtitle, style = AppType.metadata, color = TextSecondary)
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Elemento de Categoría en el Panel Lateral                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun CategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    badgeText: String? = null,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, tween(140), label = "catScale")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.md)
            .background(
                when {
                    isFocused -> SurfaceElevated
                    isSelected -> Accent.copy(alpha = 0.22f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (isFocused) Modifier.border(AppStroke.focus, Color.White, AppRadius.md)
                else if (isSelected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), AppRadius.md)
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFocus
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(AppRadius.sm)
                .background(
                    if (isSelected || isFocused) Accent.copy(alpha = 0.35f)
                    else Color.White.copy(alpha = 0.08f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(category.iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    if (isFocused || isSelected) Color.White else TextSecondary
                )
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                category.title,
                style = AppType.controlLabel,
                color = if (isFocused || isSelected) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (badgeText != null && !isFocused) {
            Box(
                Modifier
                    .clip(AppRadius.xs)
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    badgeText,
                    style = TextStyle(fontSize = 11.sp, color = TextSecondary),
                    maxLines = 1
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Insignia de Estado                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun StatusBadge(
    text: String,
    isSuccess: Boolean = true,
    isWarning: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dotColor = when {
        isWarning -> Color(0xFFFF9F0A)
        isSuccess -> AccentGreen
        else -> TextSecondary
    }
    val bg = when {
        isWarning -> Color(0x28FF9F0A)
        isSuccess -> Color(0x2830D158)
        else -> Color.White.copy(alpha = 0.10f)
    }

    Row(
        modifier = modifier
            .clip(AppRadius.xs)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = AppType.chip, color = Color.White)
    }
}

/* -------------------------------------------------------------------------- */
/*  Secciones de Contenido                                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ProfileSettingsContent(
    profile: com.omnitv.app.data.Profile?,
    onSwitchProfile: () -> Unit,
    onConfigurePin: () -> Unit,
    onClearPin: () -> Unit
) {
    SettingsCard(iconRes = R.drawable.ic_user, title = "Perfil de espectador") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profile != null) {
                AvatarView(avatarIndex = profile.avatarIndex, size = 68.dp)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(profile.name, style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (profile.isKids) KidsChip() else Text(
                            "Perfil estándar",
                            style = AppType.metadata,
                            color = TextSecondary
                        )
                        Text("·", style = AppType.metadata, color = TextSecondary)
                        Text(
                            "${profile.myList.size} en Mi Lista",
                            style = AppType.metadata,
                            color = TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            PillButton(
                text = "Cambiar de perfil",
                iconRes = R.drawable.ic_user,
                primary = true,
                onClick = onSwitchProfile
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    SettingsCard(iconRes = R.drawable.ic_shield, title = "Control parental") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp)) {
            Text(
                "Con un PIN de 4 cifras, un perfil infantil no puede salir a un perfil estándar, " +
                    "gestionar perfiles ni abrir estos ajustes. Se guarda como huella cifrada con salt en el televisor.",
                style = AppType.body,
                color = TextSecondary
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Estado: ", style = AppType.metadata, color = TextSecondary)
                Spacer(Modifier.width(6.dp))
                if (SettingsStore.parentalPinEnabled) {
                    StatusBadge(text = "ACTIVADO Y PROTEGIDO", isSuccess = true)
                } else {
                    StatusBadge(text = "SIN CONFIGURAR", isSuccess = false, isWarning = true)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton(
                    text = if (SettingsStore.parentalPinEnabled) "Quitar PIN" else "Configurar PIN",
                    iconRes = R.drawable.ic_shield,
                    primary = !SettingsStore.parentalPinEnabled,
                    danger = SettingsStore.parentalPinEnabled,
                    onClick = {
                        if (SettingsStore.parentalPinEnabled) onClearPin() else onConfigurePin()
                    }
                )
            }
        }
    }

    if (SettingsStore.linkedAccount.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        SettingsCard(iconRes = R.drawable.ic_link, title = "Cuenta enlazada") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp)) {
                StatusLine("Email", SettingsStore.linkedAccount)
                Text(
                    "Esta cuenta se vinculó desde el panel web de red local.",
                    style = AppType.metadata,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun PlaybackSettingsContent() {
    SettingsCard(iconRes = R.drawable.ic_play, title = "Preferencias de reproducción") {
        SettingRow(
            title = "Autoplay",
            subtitle = "Los vídeos empiezan a reproducirse automáticamente al abrirlos",
            trailing = { TvToggle(SettingsStore.autoplay, onCheckedChange = { SettingsStore.setAutoplayEnabled(it) }) }
        )
        SettingRow(
            title = "Siguiente automático",
            subtitle = "Al terminar un episodio, reproduce el siguiente con cuenta atrás de 10 s",
            trailing = { TvToggle(SettingsStore.autoNext, onCheckedChange = { SettingsStore.setAutoNextEnabled(it) }) }
        )
        SettingRow(
            title = "Iniciar silenciado",
            subtitle = "Los vídeos comienzan con el volumen silenciado",
            trailing = { TvToggle(SettingsStore.mutedStart, onCheckedChange = { SettingsStore.setMutedStartEnabled(it) }) }
        )
        SettingRow(
            title = "Subtítulos por defecto",
            subtitle = "Activa los subtítulos automáticamente si hay pistas disponibles",
            trailing = { TvToggle(SettingsStore.subtitlesDefault, onCheckedChange = { SettingsStore.setSubtitlesByDefault(it) }) }
        )
    }
}

@Composable
private fun QualitySettingsContent(
    onOpenQualityDialog: () -> Unit,
    onOpenSpeedDialog: () -> Unit
) {
    SettingsCard(iconRes = R.drawable.ic_quality, title = "Calidad y velocidad de reproducción") {
        SettingRow(
            title = "Calidad preferida",
            subtitle = "Resolución seleccionada por defecto al iniciar cada vídeo",
            trailing = { ValueText(SettingsStore.preferredQuality) },
            onClick = onOpenQualityDialog
        )
        SettingRow(
            title = "Velocidad de reproducción",
            subtitle = "Ritmo de reproducción predeterminado",
            trailing = { ValueText(SettingsStore.speedLabel()) },
            onClick = onOpenSpeedDialog
        )
    }
}

@Composable
private fun SkipSettingsContent() {
    SettingsCard(iconRes = R.drawable.ic_skip_next, title = "Saltar intro y créditos") {
        SettingRow(
            title = "Tramos saltables",
            subtitle = "Muestra el botón «Saltar» con marcas temporales de la comunidad (SkipDB, TheIntroDB, IntroDB)",
            trailing = { TvToggle(SettingsStore.skipSegmentsEnabled, onCheckedChange = { SettingsStore.saveSkipSegmentsEnabled(it) }) }
        )
        listOf(SkipKind.INTRO, SkipKind.RECAP, SkipKind.CREDITS).forEach { kind ->
            SettingRow(
                title = "Saltar ${kind.label} automáticamente",
                subtitle = "Salto instantáneo sin requerir pulsar el botón del mando",
                trailing = {
                    TvToggle(
                        kind.name in SettingsStore.autoSkipKinds,
                        onCheckedChange = { SettingsStore.setAutoSkip(kind, it) }
                    )
                }
            )
        }
        SettingsStore.SKIP_SOURCE_NAMES.forEach { source ->
            SettingRow(
                title = "Fuente $source",
                subtitle = when (source) {
                    SkipSegmentParser.SOURCE_SKIPDB -> "Ajusta marcas a la duración del stream · Requiere id IMDb"
                    SkipSegmentParser.SOURCE_THEINTRODB -> "Intro, resumen, créditos y avance · Admite id TMDB o IMDb"
                    else -> "Intro, resumen y créditos · Requiere id IMDb"
                },
                trailing = {
                    TvToggle(
                        source in SettingsStore.skipSources,
                        onCheckedChange = { SettingsStore.setSkipSource(source, it) }
                    )
                }
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
            StatusLine("Última consulta", SkipSegmentService.lastStatus.value.ifBlank { "Sin consultas todavía" })
            Text(
                "Datos de SkipDB (ODbL 1.0), TheIntroDB e IntroDB. Las bases de datos se leen de forma anónima y segura.",
                style = AppType.metadata,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PairingSettingsContent(
    pairingUrl: String,
    panelUrl: String
) {
    SettingsCard(iconRes = R.drawable.ic_link, title = "Vincular cuenta y Addons Stremio") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (pairingUrl.isNotEmpty()) {
                    QrCode(
                        data = pairingUrl,
                        modifier = Modifier
                            .size(188.dp)
                            .clip(AppRadius.md)
                    )
                } else {
                    Box(
                        Modifier
                            .size(188.dp)
                            .clip(AppRadius.md)
                            .background(SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Sin red local", style = AppType.metadata, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    panelUrl.ifEmpty { "Conecta el televisor a la red local" },
                    style = AppType.metadata,
                    color = Accent,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.width(28.dp))

            Column(Modifier.weight(1f)) {
                Text("Escanea el código QR con tu móvil", style = AppType.sectionTitle, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Abre la cámara de tu móvil, escanea el QR e introduce el código de emparejamiento. " +
                        "Desde el panel podrás instalar tus addons de Stremio, repositorios Nuvio y claves de API.",
                    style = AppType.body,
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Código de emparejamiento: ", style = AppType.metadata, color = TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(AppRadius.sm)
                            .background(Accent.copy(alpha = 0.20f))
                            .border(1.dp, Accent.copy(alpha = 0.5f), AppRadius.sm)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            SettingsStore.pairingCode,
                            style = AppType.monospaceCode,
                            color = Accent
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                PillButton(
                    text = "Regenerar código",
                    iconRes = R.drawable.ic_refresh,
                    primary = false,
                    onClick = { SettingsStore.regeneratePairingCode() }
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Servidor de vinculación: ", style = AppType.metadata, color = TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    if (PairingServer.running.value) {
                        StatusBadge(text = "ACTIVO EN RED LOCAL", isSuccess = true)
                    } else {
                        StatusBadge(text = "DETENIDO", isSuccess = false, isWarning = true)
                    }
                }
                Spacer(Modifier.height(6.dp))
                StatusLine("Addons de catálogo", "${SettingsStore.customAddons.size} instalados")
                StatusLine("Addons de reproducción", "${SettingsStore.streamAddons.size} instalados")
                StatusLine("APIs configuradas", "${SettingsStore.apiKeys.size}")
                StatusLine(
                    "Cifrado de claves",
                    if (SettingsStore.apiKeys.isEmpty()) "Sin claves"
                    else if (SettingsStore.secretsEncrypted) "Cifradas con Android Keystore AES-GCM"
                    else "Almacenamiento local"
                )
            }
        }
    }
}

@Composable
private fun IntegrationsSettingsContent(
    bridgeStatus: String,
    cloudstreamStatus: String,
    mediaStatus: String,
    refreshing: Boolean,
    onRefresh: () -> Unit
) {
    SettingsCard(iconRes = R.drawable.ic_server, title = "Addons Stremio y Puentes") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp)) {
            Text(
                "Stremio: manifests con recursos de streams y catálogo. Nuvio/Cloudstream: scrapers con puente externo.",
                style = AppType.body,
                color = TextSecondary
            )
            Spacer(Modifier.height(14.dp))

            if (AddonRegistry.statuses.value.isEmpty()) {
                StatusLine("Addons Stremio", "Sin addons instalados")
            } else {
                AddonRegistry.statuses.value.forEach { StatusLine(it.label, it.summary) }
            }

            Spacer(Modifier.height(8.dp))

            if (NuvioPluginManager.repositories.value.isEmpty()) {
                StatusLine("Plugins Nuvio", "Sin repositorios guardados")
            } else {
                NuvioPluginManager.repositories.value.forEach { StatusLine(it.label, it.summary) }
            }

            StatusLine("Puente JS Nuvio", bridgeStatus)
            StatusLine("Puente Cloudstream", cloudstreamStatus)
            StatusLine("Puente multimedia FFmpeg", mediaStatus)

            Spacer(Modifier.height(18.dp))
            PillButton(
                text = if (refreshing) "Comprobando…" else "Comprobar manifests y puentes",
                iconRes = R.drawable.ic_refresh,
                primary = false,
                onClick = onRefresh
            )
        }
    }
}

@Composable
private fun NetworkSettingsContent() {
    SettingsCard(iconRes = R.drawable.ic_server, title = "Red y Privacidad") {
        SettingRow(
            title = "DNS sobre HTTPS de Cloudflare (1.1.1.1)",
            subtitle = "Resuelve nombres mediante DoH seguro (cloudflare-dns.com). El certificado se valida estrictamente contra el dominio original.",
            trailing = { TvToggle(SettingsStore.cloudflareDns, onCheckedChange = { SettingsStore.saveCloudflareDns(it) }) }
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Seguridad de red: ", style = AppType.metadata, color = TextSecondary)
                Spacer(Modifier.width(6.dp))
                StatusBadge(text = "HTTPS ESTRICTO", isSuccess = true)
            }
            Spacer(Modifier.height(8.dp))
            StatusLine("Política de red", "HTTPS obligatorio para internet · HTTP solo permitido en LAN para emparejamiento")
            StatusLine("Protección de datos", "Ninguna clave ni addon sale del televisor")
        }
    }
}

@Composable
private fun SystemSettingsContent() {
    SettingsCard(iconRes = R.drawable.ic_info, title = "Información del sistema") {
        SettingRow(
            title = "Versión de Omni TV",
            subtitle = "Aplicación exclusiva para Android TV y Google TV",
            trailing = { ValueText("v${BuildConfig.VERSION_NAME}") }
        )
        SettingRow(
            title = "Motor de reproducción",
            subtitle = "Media3 ExoPlayer · Soporte HLS, DASH y MP4 con aceleración de hardware",
            trailing = { ValueText("Media3 ExoPlayer") }
        )
        SettingRow(
            title = "Diagnóstico de reproducción",
            subtitle = PlaybackDiagnostics.summary(),
            trailing = {
                StatusBadge(
                    text = if (PlaybackDiagnostics.current() == null) "ESTADO OK" else "ADVERTENCIA",
                    isSuccess = PlaybackDiagnostics.current() == null,
                    isWarning = PlaybackDiagnostics.current() != null
                )
            }
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Tarjeta de Ajustes con estilo tvOS                                        */
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
            .border(1.dp, Color.White.copy(alpha = 0.08f), AppRadius.lg)
            .padding(vertical = 12.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(AppRadius.sm)
                    .background(Accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(Accent)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                title,
                style = AppType.cardHeading,
                color = TextPrimary
            )
        }
        content()
    }
}

@Composable
private fun ValueText(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = AppType.body, color = Accent)
        Spacer(Modifier.width(8.dp))
        Text("›", style = AppType.sectionTitle, color = TextSecondary)
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.padding(bottom = 6.dp)) {
        Text("$label: ", style = AppType.metadata, color = TextSecondary)
        Text(value, style = AppType.metadata, color = TextPrimary)
    }
}

/* -------------------------------------------------------------------------- */
/*  Diálogo Selector Modal con D-pad                                          */
/* -------------------------------------------------------------------------- */

@Composable
private fun <T> OptionSelectDialog(
    title: String,
    subtitle: String,
    options: List<T>,
    selectedOption: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val initialFocus = remember { FocusRequester() }
    val initialIndex = remember(options, selectedOption) {
        val idx = options.indexOf(selectedOption)
        if (idx >= 0) idx else 0
    }
    RequestTvFocus(initialFocus)
    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(Background.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(600.dp)
                .clip(AppRadius.lg)
                .background(SurfaceCard)
                .border(1.dp, Color.White.copy(alpha = 0.12f), AppRadius.lg)
                .padding(28.dp)
        ) {
            Text(title, style = AppType.sectionTitle, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = AppType.body, color = TextSecondary)
            Spacer(Modifier.height(24.dp))

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = option == selectedOption
                    var isFocused by remember { mutableStateOf(false) }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(AppRadius.md)
                            .background(
                                when {
                                    isFocused -> SurfaceElevated
                                    isSelected -> Accent.copy(alpha = 0.20f)
                                    else -> Color.Transparent
                                }
                            )
                            .then(
                                if (isFocused) Modifier.border(AppStroke.focus, Color.White, AppRadius.md)
                                else if (isSelected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), AppRadius.md)
                                else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(option) }
                            )
                            .onFocusChanged { isFocused = it.isFocused }
                            .then(if (index == initialIndex) Modifier.focusRequester(initialFocus) else Modifier)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Accent else Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Image(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    colorFilter = ColorFilter.tint(Color.White)
                                )
                            }
                        }

                        Spacer(Modifier.width(16.dp))

                        Text(
                            labelProvider(option),
                            style = AppType.body,
                            color = if (isFocused || isSelected) TextPrimary else TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                PillButton(
                    text = "Cerrar",
                    primary = false,
                    onClick = onDismiss
                )
            }
        }
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
