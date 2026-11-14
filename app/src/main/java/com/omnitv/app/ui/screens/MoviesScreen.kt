package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.compose.material.Text

/**
 * Rejilla con todo el catálogo: contenido real de Cinemeta, Nuvio y addons.
 * Diseño profesional con estados vacíos y contador preciso.
 */
@Composable
fun MoviesScreen(onOpenDetail: (Video) -> Unit) {
    val profile = ProfileStore.currentProfile
    LaunchedEffect(Unit) {
        if (!RemoteCatalog.loaded.value) RemoteCatalog.refresh()
    }

    val kidsOnly = profile?.isKids == true
    val catalog = if (kidsOnly) {
        // Perfiles infantiles: filtrar solo contenido apto (cuando el remoto lo trae)
        RemoteCatalog.gridVideos.filter {
            it.certification == "TP" || it.certification == "7+" || it.certification.isBlank()
        }
    } else {
        RemoteCatalog.gridVideos
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
            when {
                RemoteCatalog.loading.value && catalog.isEmpty() -> "Cargando catálogo (Cinemeta · Nuvio)…"
                catalog.isEmpty() -> "Sin títulos disponibles"
                else -> "${catalog.size} títulos disponibles"
            },
            style = AppType.metadata,
            color = TextSecondary
        )
        Spacer(Modifier.height(28.dp))

        when {
            RemoteCatalog.loading.value && catalog.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando…", style = AppType.metadata, color = TextSecondary)
                }
            }
            catalog.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No hay títulos para mostrar", style = AppType.sectionTitle, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Verifica tu conexión o añade addons en Ajustes",
                            style = AppType.metadata,
                            color = TextSecondary
                        )
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
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
    }
}
