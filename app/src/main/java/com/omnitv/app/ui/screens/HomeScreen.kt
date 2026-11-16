package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.omnitv.app.data.StreamingProvider
import com.omnitv.app.data.Studio
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.HeroBanner
import com.omnitv.app.ui.components.LoadingSpinner
import com.omnitv.app.ui.components.LocalSuppressContentAutoFocus
import com.omnitv.app.ui.components.LocalTabContentFocus
import com.omnitv.app.ui.components.OnlyOnSection
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.SectionRow
import com.omnitv.app.ui.components.StudioStripSection
import com.omnitv.app.ui.components.Top10SectionRow
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de inicio tipo "Watch Now" de Apple TV:
 * banner héroe a pantalla grande + filas horizontales de contenido.
 *
 * Recuerda el último elemento enfocado al volver de una ficha o del reproductor
 * para no devolver el foco arriba del todo tras explorar el catálogo.
 */
@Composable
fun HomeScreen(
    onOpenDetail: (Video) -> Unit,
    onPlay: (Video) -> Unit,
    onSeeAll: (StreamingProvider) -> Unit,
    onOpenPlatforms: () -> Unit,
    onOpenStudio: (Studio) -> Unit
) {
    val profile = ProfileStore.currentProfile

    LaunchedEffect(SettingsStore.revision) {
        if (RemoteCatalog.needsReload()) RemoteCatalog.refresh()
    }

    val localRows = CatalogRepository.rowsFor(profile)
    val kidsOnly = profile?.isKids == true
    val allRows = if (kidsOnly) localRows else (RemoteCatalog.rows + localRows).distinctBy { it.key }
    val trendingRow = if (!kidsOnly) allRows.firstOrNull { it.key == "Tendencias hoy" } else null
    val top10Items = when {
        kidsOnly -> emptyList()
        trendingRow != null && trendingRow.items.isNotEmpty() -> trendingRow.items.take(10)
        else -> allRows
            .filter { r -> r.items.none { it.progress > 0f } }
            .drop(2)
            .flatMap { it.items }
            .distinctBy { it.id }
            .take(10)
    }
    val rows = if (trendingRow != null) allRows.filterNot { it.key == trendingRow.key } else allRows
    val featured = if (kidsOnly) CatalogRepository.featuredFor(profile)
    else RemoteCatalog.featured.value ?: CatalogRepository.featuredFor(profile)

    val scope = rememberCoroutineScope()
    val featuredPool = remember(featured?.id, allRows.size, kidsOnly) {
        val candidates = buildList {
            featured?.let(::add)
            allRows
                .filter { r -> r.items.none { it.progress > 0f } }
                .flatMap { it.items }
                .filter { it.backdropUrl.isNotBlank() || it.cardUrl.isNotBlank() }
                .forEach(::add)
        }
        candidates.distinctBy { it.id }.take(5)
    }
    var heroIndex by rememberSaveable { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }
    val activeFeatured = featuredPool.getOrNull(heroIndex.coerceIn(0, (featuredPool.size - 1).coerceAtLeast(0)))
        ?: featured

    LaunchedEffect(featuredPool.size, heroFocused) {
        if (featuredPool.size > 1 && !heroFocused) {
            while (true) {
                delay(7500)
                heroIndex = (heroIndex + 1) % featuredPool.size
            }
        }
    }

    val listState = rememberLazyListState()
    val heroFocus = remember { FocusRequester() }
    val heroInfoFocus = remember { FocusRequester() }
    val heroListFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }

    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    val pendingRestoreKey = remember { lastFocusedKey }
    var mountRestoreDone by remember { mutableStateOf(false) }
    val suppressAutoFocus = LocalSuppressContentAutoFocus.current
    val tabContentFocus = LocalTabContentFocus.current
    val activeTabFocus = com.omnitv.app.ui.components.LocalActiveTabFocus.current
    SideEffect { tabContentFocus?.focus = heroFocus }

    LaunchedEffect(activeFeatured?.id, rows.size, suppressAutoFocus) {
        if (suppressAutoFocus || mountRestoreDone) return@LaunchedEffect
        if (pendingRestoreKey != null) {
            val target = when (pendingRestoreKey) {
                "hero:play" -> heroFocus
                "hero:info" -> heroInfoFocus
                "hero:list" -> heroListFocus
                else -> restoreFocus
            }
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { target.requestFocus() }.isSuccess) {
                    mountRestoreDone = true
                    return@LaunchedEffect
                }
            }
            if ((activeFeatured != null || !RemoteCatalog.loading.value) && (rows.isNotEmpty() || !RemoteCatalog.loading.value)) {
                if (runCatching { heroFocus.requestFocus() }.isSuccess) {
                    initialFocusDone = true
                    mountRestoreDone = true
                }
            }
        } else if (!initialFocusDone && (activeFeatured != null || !RemoteCatalog.loading.value)) {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { heroFocus.requestFocus() }.isSuccess) {
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
            .background(Background)
            .then(if (activeTabFocus != null) Modifier.focusProperties { left = activeTabFocus } else Modifier),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 56.dp)
    ) {
        item(key = "hero") {
            if (activeFeatured != null) {
                val inMyList = profile?.myList?.contains(activeFeatured.id) == true
                HeroBanner(
                    video = activeFeatured,
                    onPlay = {
                        lastFocusedKey = "hero:play"
                        onPlay(activeFeatured)
                    },
                    onInfo = {
                        lastFocusedKey = "hero:info"
                        onOpenDetail(activeFeatured)
                    },
                    onToggleMyList = {
                        lastFocusedKey = "hero:list"
                        ProfileStore.toggleMyList(activeFeatured.id)
                    },
                    inMyList = inMyList,
                    slideIndex = heroIndex.coerceIn(0, (featuredPool.size - 1).coerceAtLeast(0)),
                    slideCount = featuredPool.size,
                    onHeroFocusChanged = { heroFocused = it },
                    playFocusRequester = heroFocus,
                    infoFocusRequester = heroInfoFocus,
                    listFocusRequester = heroListFocus,
                    onPlayFocused = { lastFocusedKey = "hero:play" },
                    onInfoFocused = { lastFocusedKey = "hero:info" },
                    onListFocused = { lastFocusedKey = "hero:list" }
                )
            } else {
                Box(Modifier.fillParentMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (RemoteCatalog.loading.value) {
                            LoadingSpinner()
                            Spacer(Modifier.height(16.dp))
                        }
                        Text(
                            if (RemoteCatalog.loading.value) "Cargando catálogo…" else "No hay títulos disponibles",
                            style = AppType.sectionTitle, color = TextPrimary
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when {
                                kidsOnly -> "El perfil infantil no carga catálogos remotos sin clasificación."
                                RemoteCatalog.lastError.value.isNotBlank() -> RemoteCatalog.lastError.value
                                else -> "Comprueba tu conexión o vuelve a intentarlo más tarde."
                            },
                            style = AppType.metadata, color = TextSecondary
                        )
                        if (!RemoteCatalog.loading.value && !kidsOnly) {
                            Spacer(Modifier.height(18.dp))
                            PillButton(
                                text = "Reintentar",
                                primary = false,
                                onClick = { scope.launch { RemoteCatalog.refresh() } },
                                modifier = Modifier.focusRequester(heroFocus)
                            )
                        }
                    }
                }
            }
        }
        if (top10Items.isNotEmpty()) {
            item(key = "top10") {
                Top10SectionRow(
                    title = "Top 10 hoy",
                    items = top10Items,
                    onOpenDetail = { video ->
                        lastFocusedKey = "top10:${video.id}"
                        onOpenDetail(video)
                    },
                    onItemFocused = { video ->
                        lastFocusedKey = "top10:${video.id}"
                    },
                    cardModifier = { video ->
                        if (pendingRestoreKey == "top10:${video.id}") {
                            Modifier.focusRequester(restoreFocus)
                        } else {
                            Modifier
                        }
                    }
                )
            }
        }
        items(rows.take(2), key = { it.key }) { row ->
            SectionRow(
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
        if (!kidsOnly) {
            item(key = "onlyOn") {
                OnlyOnSection(
                    onOpenDetail = { video ->
                        lastFocusedKey = "onlyon:${video.id}"
                        onOpenDetail(video)
                    },
                    onSeeAll = onSeeAll,
                    onOpenPlatforms = onOpenPlatforms,
                    onItemFocused = { video ->
                        lastFocusedKey = "onlyon:${video.id}"
                    },
                    cardModifier = { video ->
                        if (pendingRestoreKey == "onlyon:${video.id}") {
                            Modifier.focusRequester(restoreFocus)
                        } else {
                            Modifier
                        }
                    }
                )
            }
            item(key = "studios") {
                StudioStripSection(
                    onOpenStudio = { studio ->
                        lastFocusedKey = "studio:${studio.id}"
                        onOpenStudio(studio)
                    },
                    onStudioFocused = { studio ->
                        lastFocusedKey = "studio:${studio.id}"
                    },
                    tileModifier = { studio ->
                        if (pendingRestoreKey == "studio:${studio.id}") {
                            Modifier.focusRequester(restoreFocus)
                        } else {
                            Modifier
                        }
                    }
                )
            }
        }
        items(rows.drop(2), key = { it.key }) { row ->
            SectionRow(
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
    }
}
