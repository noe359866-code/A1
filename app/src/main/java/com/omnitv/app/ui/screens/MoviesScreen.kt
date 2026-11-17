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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.data.Video
import com.omnitv.app.data.VideoType
import com.omnitv.app.ui.components.PosterGridSkeleton
import com.omnitv.app.ui.components.LocalActiveTabFocus
import com.omnitv.app.ui.components.LocalSuppressContentAutoFocus
import com.omnitv.app.ui.components.LocalTabContentFocus
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.TvChip
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Rejilla con todo el catálogo (pestaña "Películas"): catálogo real
 * (Cinemeta + Nuvio + addons instalados) con filtros por tipo, orden y género.
 */
@Composable
fun MoviesScreen(onOpenDetail: (Video) -> Unit) {
    val profile = ProfileStore.currentProfile
    LaunchedEffect(SettingsStore.revision) {
        if (RemoteCatalog.needsReload()) RemoteCatalog.refresh()
    }
    val scope = rememberCoroutineScope()

    val local = CatalogRepository.catalogFor(profile)
    val kidsOnly = profile?.isKids == true
    val catalog = if (kidsOnly) {
        local
    } else {
        (RemoteCatalog.gridVideos + local).distinctBy { it.id }
    }

    var selectedFilter by rememberSaveable { mutableStateOf("Todos") }
    val topGenres = remember(catalog.size) {
        catalog.flatMap { it.genres }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(4)
            .map { it.key }
    }
    val filterOptions = remember(topGenres) {
        listOf("Todos", "Películas", "Series", "Mejor valoradas") + topGenres
    }
    val filteredCatalog = remember(catalog, selectedFilter) {
        when (selectedFilter) {
            "Todos" -> catalog
            "Películas" -> catalog.filter { it.type == VideoType.MOVIE }
            "Series" -> catalog.filter { it.type != VideoType.MOVIE }
            "Mejor valoradas" -> catalog.sortedByDescending { it.rating }
            else -> catalog.filter { v -> v.genres.any { it.equals(selectedFilter, ignoreCase = true) } }
        }
    }

    val activeTabFocus = LocalActiveTabFocus.current
    val gridState = rememberLazyGridState()
    val firstChipFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    val pendingRestoreKey = remember { lastFocusedKey }
    var mountRestoreDone by remember { mutableStateOf(false) }

    val hasTopLoadMore = !kidsOnly && RemoteCatalog.canLoadMore.value
    val suppressAutoFocus = LocalSuppressContentAutoFocus.current
    val tabContentFocus = LocalTabContentFocus.current
    SideEffect { tabContentFocus?.focus = firstChipFocus }

    LaunchedEffect(filteredCatalog.size, hasTopLoadMore, suppressAutoFocus) {
        if (suppressAutoFocus || mountRestoreDone) return@LaunchedEffect
        if (pendingRestoreKey != null) {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { restoreFocus.requestFocus() }.isSuccess) {
                    mountRestoreDone = true
                    return@LaunchedEffect
                }
            }
            if (filteredCatalog.isNotEmpty() || !RemoteCatalog.loading.value) {
                val fallback = if (filteredCatalog.isNotEmpty()) firstItemFocus else firstChipFocus
                if (runCatching { fallback.requestFocus() }.isSuccess) {
                    initialFocusDone = true
                    mountRestoreDone = true
                }
            }
        } else if (!initialFocusDone && (filteredCatalog.isNotEmpty() || !RemoteCatalog.loading.value)) {
            val target = if (filteredCatalog.isNotEmpty()) firstItemFocus else firstChipFocus
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { target.requestFocus() }.isSuccess) {
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
        Column(Modifier.padding(horizontal = AppSpacing.screenMargin)) {
            Spacer(Modifier.height(AppSpacing.safeTop))
            Text("Películas", style = AppType.screenTitle, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                if (RemoteCatalog.loading.value && catalog.isEmpty()) {
                    "Cargando catálogo…"
                } else if (selectedFilter == "Todos") {
                    "${filteredCatalog.size} títulos en el catálogo"
                } else {
                    "${filteredCatalog.size} títulos · $selectedFilter"
                },
                style = AppType.metadata,
                color = TextSecondary
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LazyRow(
                    modifier = Modifier
                        .weight(1f)
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(filterOptions, key = { _, option -> option }) { index, option ->
                        val chipKey = "chip:$option"
                        TvChip(
                            text = option,
                            selected = selectedFilter == option,
                            onFocus = { lastFocusedKey = chipKey },
                            onClick = {
                                lastFocusedKey = chipKey
                                selectedFilter = option
                            },
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstChipFocus) else Modifier)
                                .then(if (pendingRestoreKey == chipKey) Modifier.focusRequester(restoreFocus) else Modifier)
                                .then(
                                    if (activeTabFocus != null) Modifier.focusProperties { left = activeTabFocus }
                                    else Modifier
                                )
                        )
                    }
                }
                if (hasTopLoadMore) {
                    TvChip(
                        text = if (RemoteCatalog.loadingMore.value) "Cargando…" else "+ Cargar más",
                        selected = false,
                        onFocus = { lastFocusedKey = "loadMoreTop" },
                        onClick = { if (!RemoteCatalog.loadingMore.value) scope.launch { RemoteCatalog.loadMore() } },
                        modifier = Modifier
                            .then(if (pendingRestoreKey == "loadMoreTop") Modifier.focusRequester(restoreFocus) else Modifier)
                            .then(
                                if (activeTabFocus != null) Modifier.focusProperties { left = activeTabFocus }
                                else Modifier
                            )
                    )
                }
            }

            if (RemoteCatalog.lastError.value.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(RemoteCatalog.lastError.value, style = AppType.metadata, color = TextSecondary)
            }
            Spacer(Modifier.height(8.dp))
        }

        if (filteredCatalog.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(horizontal = AppSpacing.screenMargin), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (RemoteCatalog.loading.value && catalog.isEmpty()) {
                        // Esqueleto con la forma de la rejilla: la pantalla no
                        // salta cuando llegan los datos.
                        PosterGridSkeleton(
                            columns = 5,
                            rows = 2,
                            modifier = Modifier.padding(
                                start = AppSpacing.navRail,
                                top = AppSpacing.safeTop
                            )
                        )
                    } else {
                        Text(
                            if (kidsOnly) "No hay títulos con clasificación infantil"
                            else if (selectedFilter != "Todos") "Sin resultados para «$selectedFilter»"
                            else "No hay películas disponibles en este momento",
                            style = AppType.sectionTitle,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(12.dp))
                        if (!kidsOnly && catalog.isEmpty()) {
                            PillButton(
                                text = "Reintentar",
                                primary = false,
                                onClick = { scope.launch { RemoteCatalog.refresh() } }
                            )
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup(),
                contentPadding = PaddingValues(start = AppSpacing.screenMargin + AppSpacing.navRail, end = AppSpacing.screenMargin, top = 12.dp, bottom = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                itemsIndexed(filteredCatalog, key = { _, it -> it.id }) { index, video ->
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
                            .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                            .then(if (pendingRestoreKey == itemKey) Modifier.focusRequester(restoreFocus) else Modifier)
                    )
                }
                if (hasTopLoadMore) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "movies-load-more-footer") {
                        Box(Modifier.padding(top = 8.dp)) {
                            PillButton(
                                text = if (RemoteCatalog.loadingMore.value) "Cargando…" else "Cargar más títulos",
                                primary = false,
                                onFocus = { lastFocusedKey = "loadMoreBottom" },
                                onClick = { if (!RemoteCatalog.loadingMore.value) scope.launch { RemoteCatalog.loadMore() } },
                                modifier = if (pendingRestoreKey == "loadMoreBottom") {
                                    Modifier.focusRequester(restoreFocus)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
