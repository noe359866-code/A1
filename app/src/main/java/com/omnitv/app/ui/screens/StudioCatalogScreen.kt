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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.material.Text
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.Studio
import com.omnitv.app.data.Video
import com.omnitv.app.data.VideoType
import com.omnitv.app.ui.components.CircleIconButton
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.TvChip
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
    var typeFilter by remember(studio.id) { mutableStateOf("Todas") }
    var sortBy by remember(studio.id) { mutableStateOf("Populares") }

    LaunchedEffect(studio.id) {
        videos = RemoteCatalog.fetchStudio(studio)
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
                else -> list // «Populares»: orden del catálogo real
            }
        }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = studio.logoUrl,
                    contentDescription = studio.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(140.dp)
                        .height(64.dp)
                )
                Spacer(Modifier.width(22.dp))
                Column {
                    Text(studio.name, style = AppType.screenTitle, color = TextPrimary)
                    Text(
                        "Catálogo real · sweetfox.app",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Todas", "Películas", "Series").forEach { option ->
                    TvChip(
                        text = option,
                        selected = typeFilter == option,
                        onClick = { typeFilter = option }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Populares", "Mejor valoradas", "Recientes").forEach { option ->
                    TvChip(
                        text = option,
                        selected = sortBy == option,
                        onClick = { sortBy = option }
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
            Spacer(Modifier.height(20.dp))

            if (!loading && filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sin títulos por ahora", style = AppType.sectionTitle, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No se pudo cargar el catálogo de ${studio.name}",
                            style = AppType.metadata,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(bottom = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(filtered, key = { it.id }) { video ->
                        PosterCard(
                            video = video,
                            onClick = { onOpenDetail(video) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        CircleIconButton(
            iconRes = R.drawable.ic_back,
            contentDescription = "Volver",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(48.dp)
        )
    }
}
