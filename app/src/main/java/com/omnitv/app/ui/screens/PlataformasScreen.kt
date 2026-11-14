package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.Text
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.StreamingProvider
import com.omnitv.app.data.Studio
import com.omnitv.app.data.Studios
import com.omnitv.app.data.Video
import com.omnitv.app.data.VideoType
import com.omnitv.app.data.Providers
import com.omnitv.app.ui.components.CircleIconButton
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.TvChip
import com.omnitv.app.ui.components.StudioTile
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/**
 * «Plataformas» — como el «Browse by Provider» de cinejoy.pk:
 * rejilla de proveedores (Netflix, Disney+, Prime Video, HBO Max,
 * Crunchyroll…) con sus logos. Al elegir uno se abre su catálogo real.
 */
@Composable
fun PlataformasScreen(
    onOpenProvider: (StreamingProvider) -> Unit,
    onOpenStudio: (Studio) -> Unit
) {
    val kidsOnly = ProfileStore.currentProfile?.isKids == true

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 48.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Plataformas", style = AppType.screenTitle, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Explora por proveedor y por estudio · catálogo real de cinejoy.pk, Nuvio y sweetfox.app",
            style = AppType.metadata,
            color = TextSecondary
        )
        Spacer(Modifier.height(28.dp))

        if (kidsOnly) {
            // Perfiles infantiles: catálogo curado local, sin proveedores externos
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Plataformas no está disponible", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Los perfiles infantiles usan el catálogo curado de Omni TV",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                }
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Plataformas de streaming", style = AppType.sectionTitle, color = TextPrimary)
            }
            items(Providers.all, key = { "provider-${it.id}" }) { provider ->
                ProviderTile(provider, onClick = { onOpenProvider(provider) })
            }
            // Fusión con el «Browse by studio» de sweetfox.app
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text("Estudios · Browse by studio", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "DreamWorks, Marvel, Pixar, Ghibli, A24, DC, Warner Bros.…",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                }
            }
            items(Studios.all, key = { "studio-${it.id}" }) { studio ->
                StudioTile(studio, onClick = { onOpenStudio(studio) })
            }
        }
    }
}

/** Tile de proveedor: logo sobre tarjeta de vidrio + nombre (estilo cinejoy). */
@Composable
private fun ProviderTile(provider: StreamingProvider, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (focused) 1.06f else 1f,
        androidx.compose.animation.core.tween(150),
        label = "providerScale"
    )

    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.md)
            .background(if (focused) GlassFill.copy(alpha = 0.9f) else GlassFill)
            .then(
                if (focused) Modifier.border(3.dp, Accent, AppRadius.md)
                else Modifier.border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f), AppRadius.md)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = provider.logoUrl,
            contentDescription = provider.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            provider.name,
            style = AppType.metadata,
            color = if (focused) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Catálogo de una plataforma                                                */
/* -------------------------------------------------------------------------- */

/**
 * Catálogo real de una plataforma: títulos de cinejoy.pk (películas) +
 * catálogos Nuvio de la plataforma (películas y series, cuando existen),
 * con filtro Todas / Películas / Series.
 */
@Composable
fun PlatformCatalogScreen(
    provider: StreamingProvider,
    onBack: () -> Unit,
    onOpenDetail: (Video) -> Unit
) {
    var videos by remember(provider.id) { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember(provider.id) { mutableStateOf(true) }
    var filter by remember(provider.id) { mutableStateOf("Todas") }

    LaunchedEffect(provider.id) {
        videos = RemoteCatalog.fetchProviderCached(provider)
        loading = false
    }

    val filtered = when (filter) {
        "Películas" -> videos.filter { it.type == VideoType.MOVIE }
        "Series" -> videos.filter { it.type != VideoType.MOVIE }
        else -> videos
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
                CircleIconButton(
                    iconRes = R.drawable.ic_back,
                    contentDescription = "Volver",
                    size = 48.dp,
                    onClick = onBack
                )
                Spacer(Modifier.width(16.dp))
                AsyncImage(
                    model = provider.logoUrl,
                    contentDescription = provider.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(120.dp)
                        .height(56.dp)
                )
                Spacer(Modifier.width(22.dp))
                Column {
                    Text(provider.name, style = AppType.screenTitle, color = TextPrimary)
                    Text(
                        "Catálogo real · cinejoy.pk${if (provider.nuvioMoviesCatalog != null) " + Nuvio" else ""}",
                        style = AppType.metadata,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Todas", "Películas", "Series").forEach { option ->
                    TvChip(
                        text = option,
                        selected = filter == option,
                        onClick = { filter = option }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    loading -> "Cargando el catálogo de ${provider.name}…"
                    else -> "${filtered.size} títulos"
                },
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
                            "No se pudo cargar el catálogo de ${provider.name}",
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

    }
}
