package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.AnimeCatalog
import com.omnitv.app.data.MediaRow
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.TvChip
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Catálogo **solo anime**: filas curadas de Jikan (MyAnimeList) y Kitsu,
 * filtro de fuente, rejilla completa y paginación con el mando.
 *
 * No mezcla películas ni series del catálogo general. Los perfiles infantiles
 * solo ven clasificaciones TP/7+. Si una de las dos APIs cae, la otra sigue
 * mostrando sus títulos y el estado lo explica en lugar de inventar datos.
 */
@Composable
fun AnimeScreen(onOpenDetail: (Video) -> Unit) {
    val profile = ProfileStore.currentProfile
    val scope = rememberCoroutineScope()
    var sourceFilter by rememberSaveable { mutableStateOf("Todos") }
    val firstChipFocus = remember { FocusRequester() }
    RequestTvFocus(firstChipFocus)

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(bottom = 56.dp)
    ) {
        item(key = "anime-header") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 48.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("Anime", style = AppType.screenTitle, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Solo anime · Jikan (MyAnimeList) y Kitsu",
                    style = AppType.metadata,
                    color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnimeCatalog.SOURCES.forEachIndexed { index, source ->
                        TvChip(
                            text = source,
                            selected = sourceFilter == source,
                            onClick = { sourceFilter = source },
                            modifier = if (index == 0) Modifier.focusRequester(firstChipFocus) else Modifier
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
                if (!AnimeCatalog.loading.value && AnimeCatalog.canLoadMore.value) {
                    Spacer(Modifier.height(12.dp))
                    PillButton(
                        text = if (AnimeCatalog.loadingMore.value) "Cargando…" else "Cargar más anime",
                        primary = false,
                        onClick = {
                            if (!AnimeCatalog.loadingMore.value) scope.launch { AnimeCatalog.loadMore() }
                        }
                    )
                }
            }
        }

        if (rows.isEmpty() && catalog.isEmpty() && !AnimeCatalog.loading.value) {
            item(key = "anime-empty") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

        items(rows, key = { "row:" + it.key }) { row ->
            AnimeRow(row = row, onOpenDetail = onOpenDetail)
        }

        if (catalog.isNotEmpty()) {
            item(key = "anime-all-title") {
                Text(
                    "Todos los títulos",
                    style = AppType.sectionTitle,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 48.dp)
                )
            }
            items(catalog.chunked(5), key = { "grid:" + it.first().id }) { chunk ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    chunk.forEach { video ->
                        PosterCard(
                            video = video,
                            onClick = { onOpenDetail(video) },
                            modifier = Modifier.width(150.dp).height(220.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeRow(row: MediaRow, onOpenDetail: (Video) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            row.title,
            style = AppType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = 48.dp)
        ) {
            items(row.items, key = { it.id }) { video ->
                PosterCard(
                    video = video,
                    onClick = { onOpenDetail(video) },
                    modifier = Modifier.width(150.dp).height(220.dp)
                )
            }
        }
    }
}
