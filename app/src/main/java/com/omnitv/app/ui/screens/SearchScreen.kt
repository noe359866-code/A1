package com.omnitv.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.data.TvKeypad
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.LoadingSpinner
import com.omnitv.app.ui.components.LocalActiveTabFocus
import com.omnitv.app.ui.components.LocalSuppressContentAutoFocus
import com.omnitv.app.ui.components.LocalTabContentFocus
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.TvChip
import com.omnitv.app.ui.components.TvKeyboard
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.SurfaceCard
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private val quickSearchChips = listOf(
    "Tendencias", "Acción", "Animación", "Ciencia ficción", "Comedia", "Drama", "Fantasía", "Terror", "Documental"
)

private val defaultSuggestions = listOf(
    "Acción", "Anime", "Ciencia ficción", "Comedia", "Drama", "Fantasía", "Terror", "Aventura"
)

/**
 * Buscador para Android TV con teclado QWERTY fijo a la izquierda,
 * sugerencias verticales debajo, chips rápidos arriba y rejilla de pósters
 * a la derecha («Tendencias» al abrir y resultados en directo al escribir).
 */
@Composable
fun SearchScreen(onOpenDetail: (Video) -> Unit) {
    val profile = ProfileStore.currentProfile
    val kidsOnly = profile?.isKids == true
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(SettingsStore.revision) {
        if (RemoteCatalog.needsReload()) RemoteCatalog.refresh()
    }

    val localResults = remember(query, kidsOnly) { CatalogRepository.search(query, kidsOnly = kidsOnly) }
    var remoteResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query, kidsOnly, SettingsStore.revision) {
        val q = query.trim()
        if (q.length >= 2 && !kidsOnly) {
            delay(300)
            searching = true
            remoteResults = RemoteCatalog.search(q)
            searching = false
        } else {
            searching = false
            remoteResults = emptyList()
        }
    }

    val results = (localResults + remoteResults).distinctBy { it.id }

    val trendingVideos = remember(
        RemoteCatalog.rows.size,
        RemoteCatalog.gridVideos.size,
        kidsOnly
    ) {
        val source = if (kidsOnly) {
            CatalogRepository.catalogFor(profile)
        } else {
            val fromRows = RemoteCatalog.rows.flatMap { it.items }
            (fromRows + RemoteCatalog.gridVideos + CatalogRepository.catalogFor(profile))
        }
        source
            .filter { !kidsOnly || it.certification == "TP" || it.certification == "7+" }
            .distinctBy { it.id }
            .take(40)
    }

    val displayedVideos = if (query.isBlank()) trendingVideos else results
    val suggestions = remember(query, results) {
        if (query.isBlank()) {
            defaultSuggestions
        } else {
            val dynamic = results.map { it.title }.distinct().take(7)
            dynamic.ifEmpty { defaultSuggestions }
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val gridColumns = if (screenWidthDp >= 1120) 4 else 3

    val activeTabFocus = LocalActiveTabFocus.current
    val gridState = rememberLazyGridState()
    val firstKeyFocus = remember { FocusRequester() }
    val keyboardBottomFocus = remember { FocusRequester() }
    val firstGridFocus = remember { FocusRequester() }
    val firstChipFocus = remember { FocusRequester() }
    val firstSuggestionFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }

    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    val pendingRestoreKey = remember { lastFocusedKey }
    var mountRestoreDone by remember { mutableStateOf(false) }
    val suppressAutoFocus = LocalSuppressContentAutoFocus.current
    val tabContentFocus = LocalTabContentFocus.current
    SideEffect { tabContentFocus?.focus = firstKeyFocus }

    LaunchedEffect(displayedVideos.size, suppressAutoFocus) {
        if (suppressAutoFocus || mountRestoreDone) return@LaunchedEffect
        if (pendingRestoreKey != null && pendingRestoreKey != "keyboard") {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { restoreFocus.requestFocus() }.isSuccess) {
                    mountRestoreDone = true
                    return@LaunchedEffect
                }
            }
            if (runCatching { firstKeyFocus.requestFocus() }.isSuccess) {
                initialFocusDone = true
                mountRestoreDone = true
            }
        } else if (!initialFocusDone) {
            for (attempt in 0 until 6) {
                withFrameNanos { }
                if (runCatching { firstKeyFocus.requestFocus() }.isSuccess) {
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
            .padding(horizontal = AppSpacing.screenMargin)
    ) {
        Spacer(Modifier.height(10.dp))

        // Fila superior: caja de consulta visible encima del teclado + chips rápidos a la derecha
        Row(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SearchQueryPill(
                query = query,
                modifier = Modifier.width(360.dp)
            )
            LazyRow(
                modifier = Modifier.weight(1f).focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                itemsIndexed(quickSearchChips) { index, label ->
                    val selected = if (label == "Tendencias") {
                        query.isBlank()
                    } else {
                        query.equals(label, ignoreCase = true)
                    }
                    val chipKey = "chip:$label"
                    TvChip(
                        text = label,
                        selected = selected,
                        onFocus = { lastFocusedKey = chipKey },
                        onClick = {
                            lastFocusedKey = chipKey
                            query = if (label == "Tendencias") "" else label
                        },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstChipFocus) else Modifier)
                            .then(if (pendingRestoreKey == chipKey) Modifier.focusRequester(restoreFocus) else Modifier)
                            .then(
                                if (activeTabFocus != null) Modifier.focusProperties { up = activeTabFocus }
                                else Modifier
                            )
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Cuerpo principal en dos columnas: Teclado + sugerencias a la izquierda | Pósters a la derecha
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Columna izquierda: teclado QWERTY fijo + sugerencias debajo
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
            ) {
                TvKeyboard(
                    embedded = true,
                    onKey = { key ->
                        lastFocusedKey = "keyboard"
                        query = TvKeypad.apply(query, key)
                    },
                    onDone = { },
                    onClear = {
                        lastFocusedKey = "keyboard"
                        query = ""
                    },
                    firstKeyFocus = firstKeyFocus,
                    bottomFocusRequester = keyboardBottomFocus,
                    onExitUp = {
                        runCatching { firstChipFocus.requestFocus() }.isSuccess ||
                            (activeTabFocus != null && runCatching { activeTabFocus.requestFocus() }.isSuccess)
                    },
                    onExitDown = {
                        runCatching { firstSuggestionFocus.requestFocus() }.isSuccess ||
                            focusManager.moveFocus(FocusDirection.Down)
                    },
                    onExitRight = {
                        focusManager.moveFocus(FocusDirection.Right) ||
                            runCatching { firstGridFocus.requestFocus() }.isSuccess ||
                            runCatching { firstChipFocus.requestFocus() }.isSuccess
                    }
                )

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusGroup(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(suggestions) { index, suggestion ->
                        val suggKey = "sugg:$suggestion"
                        SuggestionItem(
                            text = suggestion,
                            onFocus = { lastFocusedKey = suggKey },
                            onClick = {
                                lastFocusedKey = suggKey
                                query = suggestion
                            },
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstSuggestionFocus) else Modifier)
                                .then(if (pendingRestoreKey == suggKey) Modifier.focusRequester(restoreFocus) else Modifier)
                                .focusProperties {
                                    if (index == 0) up = keyboardBottomFocus
                                }
                        )
                    }
                }
            }

            // Columna derecha: cabecera + rejilla de pósters verticales 2:3
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Text(
                    when {
                        query.isBlank() -> "Tendencias"
                        searching -> "Buscando «$query»…"
                        else -> "${results.size} resultados para «$query»"
                    },
                    style = AppType.sectionTitle,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))

                when {
                    searching && displayedVideos.isEmpty() -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingSpinner()
                            Spacer(Modifier.height(14.dp))
                            Text("Buscando «$query»…", style = AppType.sectionTitle, color = TextPrimary)
                        }
                    }

                    displayedVideos.isEmpty() && !searching -> EmptyState(
                        iconRes = R.drawable.ic_search,
                        title = when {
                            query.isNotBlank() && RemoteCatalog.searchError.value.isNotBlank() ->
                                "No se pudo buscar en el catálogo"
                            query.isNotBlank() -> "Sin resultados para «$query»"
                            else -> "Catálogo cargando"
                        },
                        subtitle = when {
                            query.isNotBlank() ->
                                RemoteCatalog.searchError.value.ifBlank { "Prueba con otro término o pulsa un género arriba" }
                            else -> "Usa el teclado o los géneros superiores para buscar"
                        }
                    )

                    else -> LazyVerticalGrid(
                        state = gridState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusGroup(),
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        itemsIndexed(displayedVideos, key = { _, it -> it.id }) { index, video ->
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
                                    .then(if (index == 0) Modifier.focusRequester(firstGridFocus) else Modifier)
                                    .then(if (pendingRestoreKey == itemKey) Modifier.focusRequester(restoreFocus) else Modifier)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchQueryPill(
    query: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(AppRadius.md)
            .background(SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.12f), AppRadius.md)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(TextSecondary)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (query.isEmpty()) "Escribe con el teclado…" else "$query|",
            style = AppType.metadata,
            color = if (query.isEmpty()) TextSecondary else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SuggestionItem(
    text: String,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppRadius.sm)
            .background(if (focused) SurfaceElevated else Color.Transparent)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.sm)
                else Modifier
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = AppType.metadata,
            color = if (focused) TextPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Estado vacío con icono. */
@Composable
private fun EmptyState(iconRes: Int, title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GlassFill),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                colorFilter = ColorFilter.tint(TextSecondary)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = AppType.sectionTitle, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = AppType.body, color = TextSecondary)
    }
}
