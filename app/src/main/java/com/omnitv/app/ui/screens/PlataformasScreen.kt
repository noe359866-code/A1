package com.omnitv.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.Providers
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.StreamingProvider
import com.omnitv.app.data.Studio
import com.omnitv.app.data.Studios
import com.omnitv.app.data.Video
import com.omnitv.app.data.VideoType
import com.omnitv.app.ui.components.CircleIconButton
import com.omnitv.app.ui.components.LoadingSpinner
import com.omnitv.app.ui.components.LocalActiveTabFocus
import com.omnitv.app.ui.components.LocalSuppressContentAutoFocus
import com.omnitv.app.ui.components.LocalTabContentFocus
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.StudioTile
import com.omnitv.app.ui.components.TvChip
import com.omnitv.app.ui.components.dpadRepeatThrottle
import com.omnitv.app.ui.theme.FocusScale
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import com.omnitv.app.ui.theme.appTween

/**
 * «Plataformas» — como el «Browse by Provider» de cinejoy.pk:
 * rejilla de proveedores (Netflix, Disney+, Prime Video, HBO Max,
 * Crunchyroll…) con sus logos. Al elegir uno se abre su catálogo real.
 */
@Composable
fun PlataformasScreen(
    onOpenProvider: (StreamingProvider) -> Unit,
    onOpenStudio: (Studio) -> Unit,
    onGoHome: () -> Unit = {}
) {
    val kidsOnly = ProfileStore.currentProfile?.isKids == true
    val activeTabFocus = LocalActiveTabFocus.current
    val suppressAutoFocus = LocalSuppressContentAutoFocus.current
    val tabContentFocus = LocalTabContentFocus.current
    val gridState = rememberLazyGridState()
    val firstProviderFocus = remember { FocusRequester() }
    val kidsHomeFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    val pendingRestoreKey = remember { lastFocusedKey }
    var mountRestoreDone by remember { mutableStateOf(false) }

    SideEffect {
        tabContentFocus?.focus = if (kidsOnly) kidsHomeFocus else firstProviderFocus
    }

    LaunchedEffect(kidsOnly, suppressAutoFocus) {
        if (suppressAutoFocus) return@LaunchedEffect
        if (kidsOnly) {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { kidsHomeFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
            return@LaunchedEffect
        }
        if (mountRestoreDone) return@LaunchedEffect
        if (pendingRestoreKey != null) {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { restoreFocus.requestFocus() }.isSuccess) {
                    mountRestoreDone = true
                    return@LaunchedEffect
                }
            }
            if (runCatching { firstProviderFocus.requestFocus() }.isSuccess) {
                initialFocusDone = true
                mountRestoreDone = true
            }
        } else if (!initialFocusDone) {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { firstProviderFocus.requestFocus() }.isSuccess) {
                    initialFocusDone = true
                    mountRestoreDone = true
                    return@LaunchedEffect
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(Modifier.padding(start = AppSpacing.screenMargin + AppSpacing.navRail, end = AppSpacing.screenMargin)) {
            Spacer(Modifier.height(AppSpacing.safeTop))
            Text("Plataformas", style = AppType.screenTitle, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Explora películas y series por plataforma y estudio",
                style = AppType.metadata,
                color = TextSecondary
            )
            Spacer(Modifier.height(18.dp))
        }

        if (kidsOnly) {
            Box(Modifier.fillMaxSize().padding(horizontal = AppSpacing.screenMargin), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Plataformas no está disponible", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Los perfiles infantiles usan el catálogo curado de Omni TV",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(20.dp))
                    PillButton(
                        text = "Volver a Inicio",
                        iconRes = R.drawable.ic_home,
                        primary = false,
                        onClick = onGoHome,
                        modifier = Modifier
                            .focusRequester(kidsHomeFocus)
                            .then(
                                if (activeTabFocus != null) Modifier.focusProperties { left = activeTabFocus }
                                else Modifier
                            )
                    )
                }
            }
            return
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(5),
            modifier = Modifier
                .fillMaxSize()
                .focusGroup(),
            contentPadding = PaddingValues(start = AppSpacing.screenMargin + AppSpacing.navRail, end = AppSpacing.screenMargin, top = 12.dp, bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Plataformas de streaming", style = AppType.sectionTitle, color = TextPrimary)
            }
            itemsIndexed(Providers.all, key = { _, it -> "provider-${it.id}" }) { index, provider ->
                val itemKey = "provider-${provider.id}"
                ProviderTile(
                    provider = provider,
                    onFocus = { lastFocusedKey = itemKey },
                    onClick = {
                        lastFocusedKey = itemKey
                        onOpenProvider(provider)
                    },
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstProviderFocus) else Modifier)
                        .then(if (pendingRestoreKey == itemKey) Modifier.focusRequester(restoreFocus) else Modifier)
                        .then(
                            if (index < 5 && activeTabFocus != null) {
                                Modifier.focusProperties { left = activeTabFocus }
                            } else {
                                Modifier
                            }
                        )
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text("Estudios · Browse by studio", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "DreamWorks, Marvel, Pixar, Ghibli, A24, DC, Warner Bros.…",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                }
            }
            items(Studios.all, key = { "studio-${it.id}" }) { studio ->
                val itemKey = "studio-${studio.id}"
                StudioTile(
                    studio = studio,
                    onFocus = { lastFocusedKey = itemKey },
                    onClick = {
                        lastFocusedKey = itemKey
                        onOpenStudio(studio)
                    },
                    modifier = if (pendingRestoreKey == itemKey) {
                        Modifier.focusRequester(restoreFocus)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

/** Tile de proveedor: logo sobre tarjeta de vidrio + nombre (estilo cinejoy). */
@Composable
private fun ProviderTile(
    provider: StreamingProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) FocusScale.tab else 1f,
        appTween(150),
        label = "providerScale"
    )

    Column(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.md)
            .background(if (focused) GlassFill.copy(alpha = 0.9f) else GlassFill)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.md)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), AppRadius.md)
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var logoLoaded by remember(provider.id) { mutableStateOf(false) }
        var logoFailed by remember(provider.id) { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(AppRadius.sm)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!logoLoaded || logoFailed) {
                Text(
                    provider.name.take(2).uppercase(),
                    style = AppType.sectionTitle,
                    color = Color.White.copy(alpha = 0.22f)
                )
            }
            AsyncImage(
                model = provider.logoUrl,
                contentDescription = provider.name,
                contentScale = ContentScale.Fit,
                onSuccess = { logoLoaded = true; logoFailed = false },
                onError = { logoFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(AppRadius.xs)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            provider.name,
            style = AppType.metadata,
            color = if (focused) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Catálogo de una plataforma                                                */
/* -------------------------------------------------------------------------- */

/**
 * Catálogo real de una plataforma: títulos de cinejoy.pk (películas) +
 * catálogos Nuvio de la plataforma (películas y series, cuando existen),
 * con filtro Todas / Películas / Series.
 */
@Composable
fun PlatformCatalogScreen(
    provider: StreamingProvider,
    onBack: () -> Unit,
    onOpenDetail: (Video) -> Unit
) {
    var videos by remember(provider.id) { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember(provider.id) { mutableStateOf(true) }
    var reloadTick by remember(provider.id) { mutableIntStateOf(0) }
    var filter by rememberSaveable(provider.id) { mutableStateOf("Todas") }

    val gridState = rememberLazyGridState()
    val firstChipFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    var lastFocusedKey by rememberSaveable(provider.id) { mutableStateOf<String?>(null) }
    var initialFocusDone by rememberSaveable(provider.id) { mutableStateOf(false) }
    val pendingRestoreKey = remember { lastFocusedKey }

    LaunchedEffect(provider.id, reloadTick) {
        loading = true
        videos = runCatching {
            RemoteCatalog.fetchProviderCached(provider, force = reloadTick > 0)
        }.getOrDefault(emptyList())
        loading = false
    }

    val filtered = when (filter) {
        "Películas" -> videos.filter { it.type == VideoType.MOVIE }
        "Series" -> videos.filter { it.type != VideoType.MOVIE }
        else -> videos
    }

    var mountRestoreDone by remember(provider.id) { mutableStateOf(false) }

    LaunchedEffect(filtered.size, loading) {
        if (mountRestoreDone) return@LaunchedEffect
        if (pendingRestoreKey != null) {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { restoreFocus.requestFocus() }.isSuccess) {
                    mountRestoreDone = true
                    return@LaunchedEffect
                }
            }
            if (!loading || filtered.isNotEmpty()) {
                if (runCatching { firstChipFocus.requestFocus() }.isSuccess) {
                    initialFocusDone = true
                    mountRestoreDone = true
                }
            }
        } else if (!initialFocusDone) {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { firstChipFocus.requestFocus() }.isSuccess) {
                    initialFocusDone = true
                    mountRestoreDone = true
                    return@LaunchedEffect
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .dpadRepeatThrottle()
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = AppSpacing.screenMargin)) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIconButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = "Volver",
                        size = 48.dp,
                        onClick = onBack
                    )
                    Spacer(Modifier.width(16.dp))
                    AsyncImage(
                        model = provider.logoUrl,
                        contentDescription = provider.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(72.dp)
                            .height(56.dp)
                            .clip(AppRadius.sm)
                    )
                    Spacer(Modifier.width(22.dp))
                    Column {
                        Text(provider.name, style = AppType.screenTitle, color = TextPrimary)
                        Text(
                            "Películas y series de la plataforma",
                            style = AppType.metadata,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Todas", "Películas", "Series").forEachIndexed { index, option ->
                        val chipKey = "filter:$option"
                        TvChip(
                            text = option,
                            selected = filter == option,
                            onFocus = { lastFocusedKey = chipKey },
                            onClick = {
                                lastFocusedKey = chipKey
                                filter = option
                            },
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstChipFocus) else Modifier)
                                .then(if (pendingRestoreKey == chipKey) Modifier.focusRequester(restoreFocus) else Modifier)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        loading -> "Cargando el catálogo de ${provider.name}…"
                        else -> "${filtered.size} títulos"
                    },
                    style = AppType.metadata,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
            }

            if (loading) {
                Box(Modifier.fillMaxSize().padding(horizontal = AppSpacing.screenMargin), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingSpinner()
                        Spacer(Modifier.height(14.dp))
                        Text("Cargando ${provider.name}…", style = AppType.sectionTitle, color = TextPrimary)
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(horizontal = AppSpacing.screenMargin), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sin títulos por ahora", style = AppType.sectionTitle, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (videos.isEmpty()) {
                                "No se pudo cargar ${provider.name}. Revisa la red o añade addons / clave TMDB en Ajustes."
                            } else {
                                "No hay títulos en la categoría «$filter» para ${provider.name}."
                            },
                            style = AppType.metadata,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        PillButton(
                            text = if (videos.isEmpty()) "Reintentar" else "Ver todas",
                            primary = false,
                            onClick = { if (videos.isEmpty()) reloadTick++ else filter = "Todas" }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup(),
                    contentPadding = PaddingValues(start = AppSpacing.screenMargin + AppSpacing.navRail, end = AppSpacing.screenMargin, top = 14.dp, bottom = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    items(filtered, key = { it.id }) { video ->
                        val itemKey = "video:${video.id}"
                        PosterCard(
                            video = video,
                            onFocus = { lastFocusedKey = itemKey },
                            onClick = {
                                lastFocusedKey = itemKey
                                onOpenDetail(video)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (pendingRestoreKey == itemKey) Modifier.focusRequester(restoreFocus) else Modifier)
                        )
                    }
                }
            }
        }
    }
}
