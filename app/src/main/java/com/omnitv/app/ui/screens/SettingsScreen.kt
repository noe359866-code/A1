@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.omnitv.app.BuildConfig
import com.omnitv.app.R
import com.omnitv.app.data.AddonRegistry
import com.omnitv.app.data.AnimeCatalog
import com.omnitv.app.data.CloudstreamBridge
import com.omnitv.app.data.ConfirmKeyUpGuard
import com.omnitv.app.data.MediaBridge
import com.omnitv.app.data.NuvioPluginManager
import com.omnitv.app.data.NuvioScraperResolver
import com.omnitv.app.data.PairingServer
import com.omnitv.app.data.PlaybackDiagnostics
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.data.SkipKind
import com.omnitv.app.data.SkipSegmentParser
import com.omnitv.app.data.SkipSegmentService
import com.omnitv.app.data.TraktProtocol
import com.omnitv.app.data.TraktService
import com.omnitv.app.data.TvKeyCodes
import com.omnitv.app.ui.components.AvatarView
import com.omnitv.app.ui.components.KidsChip
import com.omnitv.app.ui.components.LocalActiveTabFocus
import com.omnitv.app.ui.components.LocalSuppressContentAutoFocus
import com.omnitv.app.ui.components.LocalTabContentFocus
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PinDialog
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.SettingRow
import com.omnitv.app.ui.components.TvToggle
import com.omnitv.app.ui.components.consumeStrayConfirmKeyUp
import com.omnitv.app.ui.theme.FocusScale
import com.omnitv.app.ui.theme.WarnOrange
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AccentGreen
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.SurfaceCard
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import com.omnitv.app.ui.theme.appTween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.PROFILE) }
    val activeTabFocus = LocalActiveTabFocus.current
    val focusManager = LocalFocusManager.current

    val panelUrl = remember(PairingServer.running.value) { PairingServer.panelUrl() }
    val pairingUrl = remember(PairingServer.running.value) { PairingServer.pairingUrl() }

    // Control parental en perfil infantil
    var pinUnlocked by remember { mutableStateOf(false) }
    var pinTask by remember { mutableStateOf<PinTask?>(null) }
    val gated = SettingsStore.parentalPinEnabled && profile?.isKids == true && !pinUnlocked
    LaunchedEffect(gated) { if (gated) pinTask = PinTask.GATE }

    val categoryRequesters = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    val qualityRowFocus = remember { FocusRequester() }
    val speedRowFocus = remember { FocusRequester() }
    val pinButtonFocus = remember { FocusRequester() }
    var restoreAfterModal by remember { mutableStateOf<FocusRequester?>(null) }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var bridgeStatus by remember { mutableStateOf("Comprobando puente…") }
    var cloudstreamStatus by remember { mutableStateOf("Comprobando Cloudstream…") }
    var mediaStatus by remember { mutableStateOf("Comprobando puente multimedia…") }
    var refreshing by remember { mutableStateOf(false) }

    var qualityDialogVisible by remember { mutableStateOf(false) }
    var speedDialogVisible by remember { mutableStateOf(false) }
    var traktDialogVisible by remember { mutableStateOf(false) }
    val traktLinkFocus = remember { FocusRequester() }

    val anyModalOpen = qualityDialogVisible || speedDialogVisible || traktDialogVisible || pinTask != null
    val suppressAutoFocus = LocalSuppressContentAutoFocus.current
    val tabContentFocus = LocalTabContentFocus.current
    SideEffect {
        tabContentFocus?.focus = categoryRequesters[selectedCategory]
            ?: categoryRequesters.getValue(SettingsCategory.PROFILE)
    }

    LaunchedEffect(gated, suppressAutoFocus) {
        if (suppressAutoFocus) return@LaunchedEffect
        if (!gated && !initialFocusDone) {
            initialFocusDone = true
            val target = categoryRequesters[selectedCategory] ?: categoryRequesters.getValue(SettingsCategory.PROFILE)
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(anyModalOpen) {
        if (!anyModalOpen) {
            val target = restoreAfterModal
            if (target != null) {
                restoreAfterModal = null
                for (attempt in 0 until 6) {
                    withFrameNanos { }
                    if (runCatching { target.requestFocus() }.isSuccess) break
                }
            }
        }
    }

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

    // Solo se recomprueba cuando cambia algo de integraciones, no con cualquier
    // ajuste (antes: cada toggle disparaba red forzada a todos los addons).
    val integrationSignature = listOf(
        SettingsStore.customAddons.joinToString(","),
        SettingsStore.streamAddons.joinToString(","),
        SettingsStore.nuvioPlugins.joinToString(","),
        SettingsStore.disabledNuvioScrapers.sorted().joinToString(","),
        SettingsStore.cloudstreamBridge,
        SettingsStore.nuvioBridge,
        SettingsStore.ffmpegBridge
    ).joinToString("|")
    LaunchedEffect(integrationSignature) { checkIntegrations() }
    LaunchedEffect(selectedCategory) {
        if (selectedCategory == SettingsCategory.PAIRING && !PairingServer.running.value) {
            withContext(Dispatchers.IO) { PairingServer.start() }
        }
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        if (!gated) Row(
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
                    .focusGroup()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 24.dp)
            ) {
                Text("Ajustes", style = AppType.hero, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text("Configuración de Omni TV", style = AppType.metadata, color = TextSecondary)
                Spacer(Modifier.height(24.dp))

                SettingsCategory.entries.forEachIndexed { index, category ->
                    val integrationsTotal = SettingsStore.customAddons.size +
                        SettingsStore.streamAddons.size +
                        SettingsStore.nuvioPlugins.size +
                        (if (SettingsStore.cloudstreamBridge.isNotBlank()) 1 else 0)
                    val badgeText = when (category) {
                        SettingsCategory.PROFILE -> profile?.name
                        SettingsCategory.QUALITY -> SettingsStore.preferredQuality
                        SettingsCategory.PAIRING -> "${SettingsStore.customAddons.size + SettingsStore.streamAddons.size + SettingsStore.nuvioPlugins.size} configs"
                        SettingsCategory.INTEGRATIONS -> integrationsTotal.takeIf { it > 0 }?.let { "$it activas" }
                        SettingsCategory.NETWORK -> if (SettingsStore.cloudflareDns) "1.1.1.1" else null
                        SettingsCategory.SYSTEM -> "v${BuildConfig.VERSION_NAME}"
                        else -> null
                    }

                    CategoryItem(
                        category = category,
                        isSelected = category == selectedCategory,
                        badgeText = badgeText,
                        onFocus = { selectedCategory = category },
                        onClick = {
                            selectedCategory = category
                            focusManager.moveFocus(FocusDirection.Right)
                        },
                        modifier = Modifier
                            .focusRequester(categoryRequesters.getValue(category))
                            .then(
                                if (index == 0 && activeTabFocus != null) {
                                    Modifier.focusProperties { up = activeTabFocus }
                                } else {
                                    Modifier
                                }
                            )
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
                    .focusGroup()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, bottom = 48.dp)
            ) {
                SectionHeader(category = selectedCategory)
                Spacer(Modifier.height(20.dp))

                when (selectedCategory) {
                    SettingsCategory.PROFILE -> {
                        ProfileSettingsContent(
                            profile = profile,
                            onSwitchProfile = onSwitchProfile,
                            onConfigurePin = {
                                restoreAfterModal = pinButtonFocus
                                pinTask = PinTask.SET
                            },
                            onClearPin = {
                                restoreAfterModal = pinButtonFocus
                                pinTask = PinTask.CLEAR
                            },
                            pinButtonModifier = Modifier.focusRequester(pinButtonFocus)
                        )
                    }

                    SettingsCategory.PLAYBACK -> {
                        PlaybackSettingsContent()
                    }

                    SettingsCategory.QUALITY -> {
                        QualitySettingsContent(
                            onOpenQualityDialog = {
                                restoreAfterModal = qualityRowFocus
                                qualityDialogVisible = true
                            },
                            onOpenSpeedDialog = {
                                restoreAfterModal = speedRowFocus
                                speedDialogVisible = true
                            },
                            qualityRowModifier = Modifier.focusRequester(qualityRowFocus),
                            speedRowModifier = Modifier.focusRequester(speedRowFocus)
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
                            onRefresh = { scope.launch { checkIntegrations() } },
                            onOpenPairing = { selectedCategory = SettingsCategory.PAIRING },
                            onOpenTraktLink = {
                                if (TraktService.isConfigured()) {
                                    restoreAfterModal = traktLinkFocus
                                    traktDialogVisible = true
                                } else {
                                    // Sin claves no hay flujo de dispositivo: se manda al panel.
                                    selectedCategory = SettingsCategory.PAIRING
                                }
                            },
                            onUnlinkTrakt = { SettingsStore.clearTraktTokens() },
                            traktButtonModifier = Modifier.focusRequester(traktLinkFocus)
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

        if (traktDialogVisible) {
            TraktLinkDialog(onDismiss = { traktDialogVisible = false })
        }

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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) FocusScale.chip else 1f, appTween(140), label = "catScale")

    Row(
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
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
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(AppRadius.pill)
                .background(
                    when {
                        isFocused -> Color.White
                        isSelected -> Accent
                        else -> Color.Transparent
                    }
                )
        )
        Spacer(Modifier.width(10.dp))
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
                    style = AppType.chip,
                    color = TextSecondary,
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
        isWarning -> WarnOrange
        isSuccess -> AccentGreen
        else -> TextSecondary
    }
    val bg = when {
        isWarning -> WarnOrange.copy(alpha = 0.16f)
        isSuccess -> AccentGreen.copy(alpha = 0.16f)
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
    onClearPin: () -> Unit,
    pinButtonModifier: Modifier = Modifier
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
                    modifier = pinButtonModifier,
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
            onClick = { SettingsStore.setAutoplayEnabled(!SettingsStore.autoplay) },
            trailing = { TvToggle(SettingsStore.autoplay, onCheckedChange = { SettingsStore.setAutoplayEnabled(it) }) }
        )
        SettingRow(
            title = "Siguiente automático",
            subtitle = "Al terminar un episodio, reproduce el siguiente con cuenta atrás de 10 s",
            onClick = { SettingsStore.setAutoNextEnabled(!SettingsStore.autoNext) },
            trailing = { TvToggle(SettingsStore.autoNext, onCheckedChange = { SettingsStore.setAutoNextEnabled(it) }) }
        )
        SettingRow(
            title = "Iniciar silenciado",
            subtitle = "Los vídeos comienzan con el volumen silenciado",
            onClick = { SettingsStore.setMutedStartEnabled(!SettingsStore.mutedStart) },
            trailing = { TvToggle(SettingsStore.mutedStart, onCheckedChange = { SettingsStore.setMutedStartEnabled(it) }) }
        )
        SettingRow(
            title = "Subtítulos por defecto",
            subtitle = "Activa los subtítulos automáticamente si hay pistas disponibles",
            onClick = { SettingsStore.setSubtitlesByDefault(!SettingsStore.subtitlesDefault) },
            trailing = { TvToggle(SettingsStore.subtitlesDefault, onCheckedChange = { SettingsStore.setSubtitlesByDefault(it) }) }
        )
    }
}

@Composable
private fun QualitySettingsContent(
    onOpenQualityDialog: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    qualityRowModifier: Modifier = Modifier,
    speedRowModifier: Modifier = Modifier
) {
    SettingsCard(iconRes = R.drawable.ic_quality, title = "Calidad y velocidad de reproducción") {
        SettingRow(
            title = "Calidad preferida",
            subtitle = "Resolución seleccionada por defecto al iniciar cada vídeo",
            trailing = { ValueText(SettingsStore.preferredQuality) },
            modifier = qualityRowModifier,
            onClick = onOpenQualityDialog
        )
        SettingRow(
            title = "Velocidad de reproducción",
            subtitle = "Ritmo de reproducción predeterminado",
            trailing = { ValueText(SettingsStore.speedLabel()) },
            modifier = speedRowModifier,
            onClick = onOpenSpeedDialog
        )
    }
}

@Composable
private fun SkipSettingsContent() {
    var sourceHint by remember { mutableStateOf<String?>(null) }
    val enabled = SettingsStore.skipSegmentsEnabled
    SettingsCard(iconRes = R.drawable.ic_skip_next, title = "Saltar intro y créditos") {
        SettingRow(
            title = "Tramos saltables",
            subtitle = "Muestra el botón «Saltar» y marca los tramos en la barra con marcas de la comunidad",
            onClick = { SettingsStore.saveSkipSegmentsEnabled(!enabled) },
            trailing = { TvToggle(enabled, onCheckedChange = { SettingsStore.saveSkipSegmentsEnabled(it) }) }
        )
        listOf(SkipKind.INTRO, SkipKind.RECAP, SkipKind.CREDITS, SkipKind.PREVIEW).forEach { kind ->
            SettingRow(
                title = "Saltar ${kind.label} automáticamente",
                subtitle = if (enabled) {
                    if (kind == SkipKind.CREDITS) {
                        "Abre «A continuación» en créditos finales o salta a la escena post-créditos"
                    } else {
                        "Salto instantáneo sin requerir pulsar el botón del mando"
                    }
                } else {
                    "Activa «Tramos saltables» arriba para habilitar el salto automático"
                },
                onClick = {
                    val next = !(kind.name in SettingsStore.autoSkipKinds && enabled)
                    if (!enabled) SettingsStore.saveSkipSegmentsEnabled(true)
                    SettingsStore.setAutoSkip(kind, next)
                },
                trailing = {
                    TvToggle(
                        kind.name in SettingsStore.autoSkipKinds && enabled,
                        onCheckedChange = {
                            if (!enabled) SettingsStore.saveSkipSegmentsEnabled(true)
                            SettingsStore.setAutoSkip(kind, it)
                        }
                    )
                }
            )
        }
        SettingsStore.SKIP_SOURCE_NAMES.forEach { source ->
            val active = source in SettingsStore.skipSources
            SettingRow(
                title = "Fuente $source",
                subtitle = when (source) {
                    SkipSegmentParser.SOURCE_SKIPDB -> "Ajusta marcas a la duración del stream · Requiere id IMDb"
                    SkipSegmentParser.SOURCE_THEINTRODB -> "Intro, resumen, créditos y avance · Admite id TMDB o IMDb"
                    SkipSegmentParser.SOURCE_ANISKIP -> "Opening, ending y resumen de anime · Usa id MyAnimeList (los ids de Kitsu se traducen automáticamente)"
                    else -> "Intro, resumen, créditos y escena final · Requiere id IMDb"
                },
                onClick = {
                    val checked = !active
                    if (!checked && active && SettingsStore.skipSources.size <= 1) {
                        sourceHint = "Debe quedar al menos una fuente activa"
                    } else {
                        sourceHint = null
                        SettingsStore.setSkipSource(source, checked)
                    }
                },
                trailing = {
                    TvToggle(
                        active,
                        onCheckedChange = { checked ->
                            if (!checked && active && SettingsStore.skipSources.size <= 1) {
                                sourceHint = "Debe quedar al menos una fuente activa"
                            } else {
                                sourceHint = null
                                SettingsStore.setSkipSource(source, checked)
                            }
                        }
                    )
                }
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
            if (sourceHint != null) {
                Text(sourceHint!!, style = AppType.metadata, color = Accent)
                Spacer(Modifier.height(6.dp))
            }
            StatusLine("Última consulta", SkipSegmentService.lastStatus.value.ifBlank { "Sin consultas todavía" })
            Text(
                "Datos de SkipDB (ODbL 1.0), TheIntroDB, IntroDB y AniSkip. Pulsa OK para saltar o Atrás para ocultar el aviso.",
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
                if (!PairingServer.running.value) {
                    // Un QR que no responde es peor que ninguno: se explica el fallo.
                    Box(
                        Modifier
                            .size(188.dp)
                            .clip(AppRadius.md)
                            .background(SurfaceElevated)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StatusBadge(text = "SERVIDOR DETENIDO", isSuccess = false, isWarning = true)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                PairingServer.lastError.value.ifBlank { "Vuelve a abrir Ajustes o reinicia la app." },
                                style = AppType.chip,
                                color = TextSecondary
                            )
                        }
                    }
                } else if (pairingUrl.isNotEmpty()) {
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
                    "Abre la cámara, escanea el QR y el panel se conecta solo. " +
                        "Si tu navegador no abriera, entra en la URL de la izquierda y escribe este código. " +
                        "Desde el panel podrás instalar tus addons de Stremio, repositorios Nuvio y claves de API.",
                    style = AppType.body,
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "CÓDIGO DE EMPAREJAMIENTO",
                    style = AppType.chip,
                    color = TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .clip(AppRadius.sm)
                        .background(Accent.copy(alpha = 0.20f))
                        .border(2.dp, Accent.copy(alpha = 0.6f), AppRadius.sm)
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Text(
                        SettingsStore.pairingCode,
                        style = AppType.pairingCode,
                        color = Accent
                    )
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
                StatusLine("Repositorios Nuvio", "${SettingsStore.nuvioPlugins.size} guardados")
                StatusLine(
                    "Puente Cloudstream",
                    if (SettingsStore.cloudstreamBridge.isNotBlank()) "Configurado" else "No configurado"
                )
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
    onRefresh: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenTraktLink: () -> Unit = {},
    onUnlinkTrakt: () -> Unit = {},
    traktButtonModifier: Modifier = Modifier
) {
    val stremioStatuses = AddonRegistry.statuses.value
    val streamEndpoints = SettingsStore.streamAddons
    val nuvioRepos = NuvioPluginManager.repositories.value
    val nuvioScrapers = nuvioRepos.flatMap { it.manifest?.scrapers.orEmpty() }.distinctBy { it.id }
    val disabledScrapers = SettingsStore.disabledNuvioScrapers
    val csExtensions = CloudstreamBridge.extensions.value

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsCard(iconRes = R.drawable.ic_link, title = "Addons de Stremio y Omni Streams") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
                Text(
                    "Addons con manifest.json (catálogo, fichas y streams HTTP) y endpoints compatibles con Omni Streams.",
                    style = AppType.body,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                if (stremioStatuses.isEmpty()) {
                    StatusLine("Addons Stremio", "Sin addons instalados · añádelos en Vincular (QR)")
                } else {
                    stremioStatuses.forEach { st ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(st.label, style = AppType.body, color = TextPrimary)
                                Text(st.summary, style = AppType.metadata, color = TextSecondary)
                            }
                            Spacer(Modifier.width(12.dp))
                            StatusBadge(
                                text = when {
                                    st.checking -> "COMPROBANDO"
                                    st.manifest != null -> "ACTIVO"
                                    else -> "ERROR"
                                },
                                isSuccess = st.manifest != null && !st.checking,
                                isWarning = st.manifest == null && !st.checking
                            )
                        }
                    }
                }
                if (streamEndpoints.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    streamEndpoints.forEach { endpoint ->
                        val host = runCatching { java.net.URI(endpoint).host }.getOrNull() ?: endpoint
                        StatusLine("Omni Streams · $host", endpoint)
                    }
                }
            }
        }

        SettingsCard(iconRes = R.drawable.ic_server, title = "Plugins de Nuvio") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Puente JS externo", style = AppType.body, color = TextPrimary)
                        Text(bridgeStatus, style = AppType.metadata, color = TextSecondary)
                    }
                    Spacer(Modifier.width(12.dp))
                    val connected = bridgeStatus.startsWith("Puente conectado")
                    StatusBadge(
                        text = if (connected) "CONECTADO" else if (SettingsStore.nuvioBridge.isBlank()) "SIN PUENTE" else "AVISO",
                        isSuccess = connected,
                        isWarning = !connected && SettingsStore.nuvioBridge.isNotBlank()
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (nuvioRepos.isEmpty()) {
                    StatusLine("Repositorios Nuvio", "Sin repositorios guardados")
                } else {
                    nuvioRepos.forEach { StatusLine(it.label, it.summary) }
                }
            }

            if (nuvioScrapers.isNotEmpty()) {
                nuvioScrapers.forEach { scraper ->
                    val repoEnabled = scraper.enabled
                    val userEnabled = scraper.id !in disabledScrapers
                    val formatsText = scraper.formatsLabel
                    val subtitle = buildString {
                        append(formatsText)
                        if (!repoEnabled) append(" · desactivado por el repositorio")
                    }
                    SettingRow(
                        title = scraper.name,
                        subtitle = subtitle,
                        onClick = {
                            if (repoEnabled) SettingsStore.setNuvioScraperEnabled(scraper.id, !(repoEnabled && userEnabled))
                        },
                        trailing = {
                            TvToggle(
                                checked = repoEnabled && userEnabled,
                                onCheckedChange = { enabled ->
                                    if (repoEnabled) {
                                        SettingsStore.setNuvioScraperEnabled(scraper.id, enabled)
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }

        TraktSettingsCard(
            onLink = onOpenTraktLink,
            onUnlink = onUnlinkTrakt,
            linkButtonModifier = traktButtonModifier
        )

        SettingsCard(iconRes = R.drawable.ic_server, title = "Extensiones de Cloudstream y FFmpeg") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Puente Cloudstream", style = AppType.body, color = TextPrimary)
                        Text(cloudstreamStatus, style = AppType.metadata, color = TextSecondary)
                    }
                    Spacer(Modifier.width(12.dp))
                    val csConnected = cloudstreamStatus.startsWith("Puente Cloudstream conectado")
                    StatusBadge(
                        text = if (csConnected) "CONECTADO" else if (SettingsStore.cloudstreamBridge.isBlank()) "DESACTIVADO" else "AVISO",
                        isSuccess = csConnected,
                        isWarning = !csConnected && SettingsStore.cloudstreamBridge.isNotBlank()
                    )
                }
                if (csExtensions.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    StatusLine(
                        "Extensiones informadas",
                        csExtensions.take(12).joinToString(" · ") +
                            if (csExtensions.size > 12) " (+${csExtensions.size - 12})" else ""
                    )
                }
                Spacer(Modifier.height(6.dp))
                StatusLine("Puente multimedia FFmpeg", mediaStatus)

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(
                        text = if (refreshing) "Comprobando…" else "Comprobar todo",
                        iconRes = R.drawable.ic_refresh,
                        primary = true,
                        onClick = onRefresh
                    )
                    PillButton(
                        text = "Configurar en Vincular (QR)",
                        iconRes = R.drawable.ic_link,
                        primary = false,
                        onClick = onOpenPairing
                    )
                }
            }
        }
    }
}

@Composable
private fun TraktSettingsCard(
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    linkButtonModifier: Modifier = Modifier
) {
    val configured = TraktService.isConfigured()
    val linked = SettingsStore.traktLinked
    SettingsCard(iconRes = R.drawable.ic_check, title = "Trakt · progreso en tu cuenta") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Sincronizar con Trakt", style = AppType.body, color = TextPrimary)
                    Text(
                        when {
                            linked -> "Vinculado · los vídeos que empieces, pauses y termines se marcarán en tu cuenta de trakt.tv"
                            configured -> "Claves de tu app Trakt guardadas · falta vincular el televisor en trakt.tv"
                            else -> "Opcional · necesitas una app Trakt gratuita y sus claves en Vincular (QR)"
                        },
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.width(12.dp))
                StatusBadge(
                    text = when {
                        linked -> "CONECTADO"
                        configured -> "SIN VINCULAR"
                        else -> "SIN CONFIGURAR"
                    },
                    isSuccess = linked,
                    isWarning = !linked && configured
                )
            }
            Spacer(Modifier.height(10.dp))
            if (!configured) {
                StatusLine(
                    "Cómo activarlo",
                    "1) Crea una app en trakt.tv/oauth/applications · 2) En Vincular (QR) → Añadir APIs: TRAKT=tu_client_id y TRAKT_SECRET=tu_client_secret"
                )
            } else if (linked) {
                StatusLine("Qué se sincroniza", "Inicio, pausa y fin de reproducción (scrobble). Nada más: el historial completo no se sube.")
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (linked) {
                    PillButton(
                        text = "Desvincular Trakt",
                        iconRes = R.drawable.ic_link,
                        primary = false,
                        onClick = onUnlink,
                        modifier = linkButtonModifier
                    )
                } else {
                    PillButton(
                        text = if (configured) "Vincular Trakt" else "Configurar TRAKT en Vincular (QR)",
                        iconRes = R.drawable.ic_link,
                        primary = configured,
                        onClick = onLink,
                        modifier = linkButtonModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun TraktLinkDialog(onDismiss: () -> Unit) {
    var deviceCode by remember { mutableStateOf<TraktProtocol.DeviceCode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var linked by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(true) }
    val closeFocus = remember { FocusRequester() }
    val keyUpGuard = remember { ConfirmKeyUpGuard(initialSuppressUntilDown = true) }

    BackHandler(onBack = onDismiss)

    // Paso 1: pedir código de dispositivo a Trakt.
    LaunchedEffect(Unit) {
        requesting = true
        error = null
        deviceCode = withContext(Dispatchers.IO) { TraktService.requestDeviceCode() }
        requesting = false
        if (deviceCode == null) {
            error = "Trakt no devolvió un código. Revisa TRAKT/TRAKT_SECRET en Vincular (QR) y la conexión a trakt.tv."
        }
    }

    // Paso 2: sondear el canje hasta que el usuario autorice en trakt.tv.
    LaunchedEffect(deviceCode) {
        val dc = deviceCode ?: return@LaunchedEffect
        val deadline = System.currentTimeMillis() + dc.expiresInSec * 1000
        while (System.currentTimeMillis() < deadline && !linked && error == null) {
            delay(dc.intervalSec * 1000)
            when (withContext(Dispatchers.IO) { TraktService.pollDeviceToken(dc.deviceCode) }) {
                TraktService.PollResult.Linked -> linked = true
                TraktService.PollResult.Expired -> error = "El código caducó antes de vincular."
                TraktService.PollResult.Denied -> error = "Has denegado el acceso en Trakt."
                TraktService.PollResult.Failed -> error = "Trakt no respondió. Inténtalo de nuevo."
                TraktService.PollResult.Pending -> Unit
            }
        }
        if (!linked && error == null) error = "El código caducó antes de vincular."
    }

    RequestTvFocus(closeFocus, key = "traktLink")

    Box(
        Modifier
            .fillMaxSize()
            .background(Background.copy(alpha = 0.95f))
            .consumeStrayConfirmKeyUp(keyUpGuard)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && TvKeyCodes.isBack(event.nativeKeyEvent.keyCode)) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(600.dp)
                .focusGroup()
                .clip(AppRadius.lg)
                .background(SurfaceCard)
                .border(1.dp, Color.White.copy(alpha = 0.12f), AppRadius.lg)
                .padding(28.dp)
        ) {
            Text("Vincular Trakt", style = AppType.sectionTitle, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            when {
                linked -> {
                    Text(
                        "Trakt conectado ✓ A partir de ahora tus reproducciones se sincronizarán con tu cuenta.",
                        style = AppType.body,
                        color = TextPrimary
                    )
                }
                error != null -> {
                    Text(error.orEmpty(), style = AppType.body, color = TextPrimary)
                }
                requesting -> {
                    Text("Pidiendo código a Trakt…", style = AppType.body, color = TextPrimary)
                }
                deviceCode != null -> {
                    val dc = deviceCode!!
                    Text(
                        "Abre ${dc.verificationUrl} en tu móvil o PC e introduce este código:",
                        style = AppType.body,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(18.dp))
                    Box(
                        Modifier
                            .clip(AppRadius.sm)
                            .background(Accent.copy(alpha = 0.20f))
                            .border(2.dp, Accent.copy(alpha = 0.6f), AppRadius.sm)
                            .padding(horizontal = 22.dp, vertical = 8.dp)
                    ) {
                        Text(dc.userCode, style = AppType.pairingCode, color = Accent)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Esperando a que confirmes en Trakt… el televisor se vinculará solo.",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PillButton(
                text = if (linked || error != null) "Cerrar" else "Cancelar",
                iconRes = R.drawable.ic_check,
                primary = linked || error != null,
                onClick = onDismiss,
                modifier = Modifier.focusRequester(closeFocus)
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
            onClick = { SettingsStore.saveCloudflareDns(!SettingsStore.cloudflareDns) },
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
    var cacheCleared by remember { mutableStateOf(false) }

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
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
            StatusLine(
                "Caché de catálogos",
                if (cacheCleared) "Caché limpiada · se actualizará al abrir el catálogo"
                else "Incluye portada, plataformas, estudios, anime, plugins Nuvio y sus enlaces resueltos"
            )
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = if (cacheCleared) "Caché limpiada" else "Limpiar caché de catálogos",
                iconRes = R.drawable.ic_delete,
                primary = false,
                onClick = {
                    RemoteCatalog.invalidateCache()
                    AnimeCatalog.invalidateCache()
                    NuvioPluginManager.clearCache()
                    NuvioScraperResolver.clearResultCache()
                    cacheCleared = true
                }
            )
        }
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
    val optionRequesters = remember(options.size) { List(options.size) { FocusRequester() } }
    val closeFocus = remember { FocusRequester() }
    val initialIndex = remember(options, selectedOption) {
        val idx = options.indexOf(selectedOption)
        if (idx >= 0) idx else 0
    }
    val initialFocus = optionRequesters.getOrElse(initialIndex) { closeFocus }
    val keyUpGuard = remember { ConfirmKeyUpGuard(initialSuppressUntilDown = true) }

    RequestTvFocus(initialFocus, key = title)
    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(Background.copy(alpha = 0.95f))
            .consumeStrayConfirmKeyUp(keyUpGuard)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && TvKeyCodes.isBack(event.nativeKeyEvent.keyCode)) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(600.dp)
                .focusGroup()
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
                            .zIndex(if (isFocused) 1f else 0f)
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
                            .focusRequester(optionRequesters[index])
                            .focusProperties {
                                up = if (index > 0) optionRequesters[index - 1] else FocusRequester.Cancel
                                down = if (index < optionRequesters.lastIndex) optionRequesters[index + 1] else closeFocus
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            }
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(option) }
                            )
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
                    onClick = onDismiss,
                    modifier = Modifier
                        .focusRequester(closeFocus)
                        .focusProperties {
                            up = optionRequesters.lastOrNull() ?: FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
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
            val size = 512
            val pixels = IntArray(size * size) { i ->
                if (matrix.get(i % size, i / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
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
