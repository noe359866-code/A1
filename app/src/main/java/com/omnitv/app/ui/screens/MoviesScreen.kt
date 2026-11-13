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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.tv.material.Text

/**
 * Rejilla con todo el catálogo (pestaña "Películas"): catálogo real
 * (Cinemeta + Nuvio) + títulos demo locales.
 */
@Composable
fun MoviesScreen(onOpenDetail: (Video) -> Unit) {
    val profile = ProfileStore.currentProfile
    LaunchedEffect(Unit) {
        if (!RemoteCatalog.loaded.value) RemoteCatalog.refresh()
    }

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
            if (RemoteCatalog.loading.value && catalog.size <= local.size) {
                "Cargando catálogo (Cinemeta · Nuvio)…"
            } else {
                "${catalog.size} títulos en el catálogo"
            },
            style = AppType.metadata,
            color = TextSecondary
        )
        Spacer(Modifier.height(28.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
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
                        .height(230.dp)
                )
            }
        }
    }
}
