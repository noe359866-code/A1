@file:OptIn(ExperimentalComposeUiApi::class)

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.AnimeCatalog
import com.omnitv.app.data.MediaRow
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.LoadingSpinner
import com.omnitv.app.ui.components.LocalActiveTabFocus
import com.omnitv.app.ui.components.LocalSuppressContentAutoFocus
import com.omnitv.app.ui.components.LocalTabContentFocus
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.ProvideHorizontalTvBringIntoView
import com.omnitv.app.ui.components.TvChip
import com.omnitv.app.ui.components.bringIntoViewIfChildrenAreFocused
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Catálogo **solo anime**: filas curadas de Jikan (MyAnimeList) y Kitsu,
 * filtro de fuente, rejilla completa y paginación con el mando.
 */
@Composable
fun AnimeScreen(onOpenDetail: (Video) -> Unit) {
    val profile = ProfileStore.currentProfile
    val scope = rememberCoroutineScope()
    var sourceFilter by rememberSaveable { mutableStateOf("Todos") }
    val activeTabFocus = LocalActiveTabFocus.current
    val listState = rememberLazyListState()

    val firstChipFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    val pendingRestoreKey = remember { lastFocusedKey }
    var mountRestoreDone by remember { mutableStateOf(false) }

    LaunchedEffect(SettingsStore.revision) {
        if (AnimeCatalog.needsReload()) AnimeCatalog.refresh()
    }

    val rows = AnimeCatalog.rowsFor(profile, sourceFilter)
    val catalog = AnimeCatalog.catalogFor(profile, sourceFilter)
    val kidsOnly = profile?.isKids == true
    val status = when {
        AnimeCatalog.loadingMore.value -> "Cargando más títulos…"
        AnimeCatalog.loading.value && catalog.isEmpty() -> "Cargando catálogo de anime…"
        else -> "${catalog.size} títulos de anime" +
            if (sourceFilter == "Todos") "" else " · $sourceFilter"
    }

    val suppressAutoFocus = LocalSuppressContentAutoFocus.current
    val tabContentFocus = LocalTabContentFocus.current
    SideEffect { tabContentFocus?.focus = firstChipFocus }

    LaunchedEffect(rows.size, catalog.size, suppressAutoFocus) {
        if (suppressAutoFocus || mountRestoreDone) return@LaunchedEffect
        if (pendingRestoreKey != null) {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { restoreFocus.requestFocus() }.isSuccess) {
                    mountRestoreDone = true
                    return@LaunchedEffect
                }
            }
            if (rows.isNotEmpty() || catalog.isNotEmpty() || !AnimeCatalog.loading.value) {
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

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 56.dp)
    ) {
        item(key = "anime-header") {
            Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenMargin)) {
                Spacer(Modifier.height(12.dp))
                Text("Anime", style = AppType.screenTitle, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Solo anime · Jikan (MyAnimeList) y Kitsu",
                    style = AppType.metadata,
                    color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimeCatalog.SOURCES.forEachIndexed { index, source ->
                        val chipKey = "chip:$source"
                        TvChip(
                            text = source,
                            selected = sourceFilter == source,
                            onFocus = { lastFocusedKey = chipKey },
                            onClick = {
                                lastFocusedKey = chipKey
                                sourceFilter = source
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
                    if (!AnimeCatalog.loading.value && AnimeCatalog.canLoadMore.value) {
                        Spacer(Modifier.weight(1f))
                        TvChip(
                            text = if (AnimeCatalog.loadingMore.value) "Cargando…" else "+ Cargar más",
                            selected = false,
                            onFocus = { lastFocusedKey = "loadMoreTop" },
                            onClick = {
                                if (!AnimeCatalog.loadingMore.value) scope.launch { AnimeCatalog.loadMore() }
                            },
                            modifier = Modifier
                                .then(if (pendingRestoreKey == "loadMoreTop") Modifier.focusRequester(restoreFocus) else Modifier)
                                .then(
                                    if (activeTabFocus != null) Modifier.focusProperties { left = activeTabFocus }
                                    else Modifier
                                )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(status, style = AppType.metadata, color = TextSecondary)
                if (kidsOnly) {
                    Text(
                        "Perfil infantil: solo títulos clasificados TP o 7+.",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                }
                if (AnimeCatalog.lastError.value.isNotEmpty()) {
                    Text(
                        AnimeCatalog.lastError.value,
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                }
            }
        }

        if (rows.isEmpty() && catalog.isEmpty()) {
            item(key = "anime-empty") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (AnimeCatalog.loading.value) {
                            LoadingSpinner()
                            Spacer(Modifier.height(16.dp))
                            Text("Cargando catálogo de anime…", style = AppType.sectionTitle, color = TextPrimary)
                        } else {
                            Text("Sin anime por ahora", style = AppType.sectionTitle, color = TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (kidsOnly) "No hay títulos de anime con clasificación infantil."
                                else "No se pudo consultar Jikan ni Kitsu. Revisa la conexión.",
                                style = AppType.metadata,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(16.dp))
                            PillButton(
                                text = "Reintentar",
                                primary = false,
                                onClick = { scope.launch { AnimeCatalog.refresh(force = true) } }
                            )
                        }
                    }
                }
            }
        }

        items(rows, key = { "row:" + it.key }) { row ->
            AnimeRow(
                row = row,
                onOpenDetail = { video ->
                    lastFocusedKey = "row:${row.key}:${video.id}"
                    onOpenDetail(video)
                },
                onItemFocused = { video ->
                    lastFocusedKey = "row:${row.key}:${video.id}"
                },
                cardModifier = { video ->
                    if (pendingRestoreKey == "row:${row.key}:${video.id}") {
                        Modifier.focusRequester(restoreFocus)
                    } else {
                        Modifier
                    }
                }
            )
        }

        if (catalog.isNotEmpty()) {
            item(key = "anime-all-title") {
                Text(
                    "Todos los títulos",
                    style = AppType.sectionTitle,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = AppSpacing.screenMargin)
                )
            }
            items(catalog.chunked(5), key = { "grid:" + it.first().id }) { chunk ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup()
                        .padding(horizontal = AppSpacing.screenMargin, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    for (slot in 0 until 5) {
                        val video = chunk.getOrNull(slot)
                        if (video != null) {
                            val gridKey = "grid:${video.id}"
                            PosterCard(
                                video = video,
                                onFocus = { lastFocusedKey = gridKey },
                                onClick = {
                                    lastFocusedKey = gridKey
                                    onOpenDetail(video)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (pendingRestoreKey == gridKey) Modifier.focusRequester(restoreFocus) else Modifier)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (!AnimeCatalog.loading.value && AnimeCatalog.canLoadMore.value) {
                item(key = "anime-load-more-footer") {
                    Box(Modifier.padding(horizontal = AppSpacing.screenMargin, vertical = 8.dp)) {
                        PillButton(
                            text = if (AnimeCatalog.loadingMore.value) "Cargando…" else "Cargar más anime",
                            primary = false,
                            onFocus = { lastFocusedKey = "loadMoreBottom" },
                            onClick = {
                                if (!AnimeCatalog.loadingMore.value) scope.launch { AnimeCatalog.loadMore() }
                            },
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

@Composable
private fun AnimeRow(
    row: MediaRow,
    onOpenDetail: (Video) -> Unit,
    onItemFocused: (Video) -> Unit,
    cardModifier: (Video) -> Modifier
) {
    Column(Modifier.fillMaxWidth().bringIntoViewIfChildrenAreFocused()) {
        Text(
            row.title,
            style = AppType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = AppSpacing.screenMargin)
        )
        Spacer(Modifier.height(4.dp))
        ProvideHorizontalTvBringIntoView {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(horizontal = AppSpacing.screenMargin, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer()
            ) {
                items(row.items, key = { it.id }) { video ->
                    PosterCard(
                        video = video,
                        onFocus = { onItemFocused(video) },
                        onClick = { onOpenDetail(video) },
                        modifier = Modifier
                            .width(156.dp)
                            .then(cardModifier(video))
                    )
                }
            }
        }
    }
}
