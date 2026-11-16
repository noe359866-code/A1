package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.Studio
import com.omnitv.app.data.Video
import com.omnitv.app.data.VideoType
import com.omnitv.app.ui.components.CircleIconButton
import com.omnitv.app.ui.components.LoadingSpinner
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.TvChip
import com.omnitv.app.ui.components.dpadRepeatThrottle
import com.omnitv.app.ui.theme.LogoPlate
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/**
 * Catálogo real de un estudio («Browse by studio» de sweetfox.app):
 * títulos de `sweetfox.app/company/{id}` con filtros de tipo
 * (Todas / Películas / Series) y orden (Populares / Mejor valoradas /
 * Recientes), como las pestañas de la web original.
 */
@Composable
fun StudioCatalogScreen(
    studio: Studio,
    onBack: () -> Unit,
    onOpenDetail: (Video) -> Unit
) {
    var videos by remember(studio.id) { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember(studio.id) { mutableStateOf(true) }
    var reloadTick by remember(studio.id) { mutableIntStateOf(0) }
    var typeFilter by rememberSaveable(studio.id) { mutableStateOf("Todas") }
    var sortBy by rememberSaveable(studio.id) { mutableStateOf("Populares") }

    val gridState = rememberLazyGridState()
    val firstChipFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    var lastFocusedKey by rememberSaveable(studio.id) { mutableStateOf<String?>(null) }
    var initialFocusDone by rememberSaveable(studio.id) { mutableStateOf(false) }
    val pendingRestoreKey = remember { lastFocusedKey }

    LaunchedEffect(studio.id, reloadTick) {
        loading = true
        videos = runCatching {
            RemoteCatalog.fetchStudio(studio, force = reloadTick > 0)
        }.getOrDefault(emptyList())
        loading = false
    }

    val filtered = videos
        .filter {
            when (typeFilter) {
                "Películas" -> it.type == VideoType.MOVIE
                "Series" -> it.type != VideoType.MOVIE
                else -> true
            }
        }
        .let { list ->
            when (sortBy) {
                "Mejor valoradas" -> list.sortedByDescending { it.rating }
                "Recientes" -> list.sortedByDescending { it.year }
                else -> list
            }
        }

    var mountRestoreDone by remember(studio.id) { mutableStateOf(false) }

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
                    Box(
                        modifier = Modifier
                            .clip(AppRadius.sm)
                            .background(LogoPlate.copy(alpha = 0.94f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = studio.logoUrl,
                            contentDescription = studio.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width(124.dp)
                                .height(48.dp)
                        )
                    }
                    Spacer(Modifier.width(22.dp))
                    Column {
                        Text(studio.name, style = AppType.screenTitle, color = TextPrimary)
                        Text(
                            "Películas y series del estudio",
                            style = AppType.metadata,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Todas", "Películas", "Series").forEachIndexed { index, option ->
                        val chipKey = "type:$option"
                        TvChip(
                            text = option,
                            selected = typeFilter == option,
                            onFocus = { lastFocusedKey = chipKey },
                            onClick = {
                                lastFocusedKey = chipKey
                                typeFilter = option
                            },
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstChipFocus) else Modifier)
                                .then(if (pendingRestoreKey == chipKey) Modifier.focusRequester(restoreFocus) else Modifier)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Populares", "Mejor valoradas", "Recientes").forEach { option ->
                        val chipKey = "sort:$option"
                        TvChip(
                            text = option,
                            selected = sortBy == option,
                            onFocus = { lastFocusedKey = chipKey },
                            onClick = {
                                lastFocusedKey = chipKey
                                sortBy = option
                            },
                            modifier = if (pendingRestoreKey == chipKey) {
                                Modifier.focusRequester(restoreFocus)
                            } else {
                                Modifier
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    if (loading) "Cargando el catálogo de ${studio.name}…"
                    else "${filtered.size} títulos",
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
                        Text("Cargando ${studio.name}…", style = AppType.sectionTitle, color = TextPrimary)
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(horizontal = AppSpacing.screenMargin), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sin títulos por ahora", style = AppType.sectionTitle, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No se pudo cargar el catálogo de ${studio.name}",
                            style = AppType.metadata,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(18.dp))
                        PillButton(
                            text = "Reintentar",
                            primary = false,
                            onClick = { reloadTick++ }
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
                    contentPadding = PaddingValues(start = AppSpacing.screenMargin, end = AppSpacing.screenMargin, top = 14.dp, bottom = 56.dp),
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
