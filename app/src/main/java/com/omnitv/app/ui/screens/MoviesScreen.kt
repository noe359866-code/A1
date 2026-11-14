package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.compose.material.Text

/**
 * Rejilla con todo el catálogo (pestaña "Películas"): catálogo real
 * (Cinemeta + Nuvio + addons instalados).
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 48.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Películas", style = AppType.screenTitle, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            if (RemoteCatalog.loading.value && catalog.isEmpty()) {
                "Cargando catálogo (Cinemeta · Nuvio)…"
            } else {
                "${catalog.size} títulos en el catálogo"
            },
            style = AppType.metadata,
            color = TextSecondary
        )
        if (!kidsOnly && RemoteCatalog.canLoadMore.value) {
            PillButton(
                text = if (RemoteCatalog.loadingMore.value) "Cargando…" else "Cargar más títulos",
                primary = false,
                onClick = { if (!RemoteCatalog.loadingMore.value) scope.launch { RemoteCatalog.loadMore() } }
            )
            Spacer(Modifier.height(10.dp))
        }
        if (RemoteCatalog.lastError.value.isNotEmpty()) {
            Text(RemoteCatalog.lastError.value, style = AppType.metadata, color = TextSecondary)
        }
        Spacer(Modifier.height(18.dp))
        if (catalog.isEmpty() && !RemoteCatalog.loading.value) {
            Text(
                if (kidsOnly) "No hay películas guardadas con clasificación infantil." else "No hay películas disponibles. Revisa tus addons en Ajustes.",
                style = AppType.metadata, color = TextSecondary
            )
        }

        LazyVerticalGrid(
            // 5 columnas: en 960 dp de ancho, 4 dejan pósters de 220 dp
            // con 20 dp de hueco; 5 encajan con la zona segura.
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(catalog, key = { it.id }) { video ->
                PosterCard(
                    video = video,
                    onClick = { onOpenDetail(video) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}
