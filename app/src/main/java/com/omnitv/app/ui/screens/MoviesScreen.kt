package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.LoadingSpinner
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.theme.*

@Composable
fun MoviesScreen(onOpenDetail: (Video) -> Unit) {
    val profile = ProfileStore.currentProfile
    LaunchedEffect(Unit) { if (!RemoteCatalog.loaded.value) RemoteCatalog.refresh() }
    val kidsOnly = profile?.isKids == true
    val catalog = if (kidsOnly) RemoteCatalog.gridVideos.filter { it.certification == "TP" || it.certification == "7+" || it.certification.isBlank() } else RemoteCatalog.gridVideos
    Column(Modifier.fillMaxSize().background(Background).padding(horizontal = AppSpacing.screenMargin)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Películas", style = AppType.screenTitle, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(AppRadius.pill).background(GlassFill).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(when { RemoteCatalog.loading.value && catalog.isEmpty() -> "Cargando…" ; catalog.isEmpty() -> "0 títulos" ; else -> "${catalog.size} títulos" }, style = AppType.caption, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(when { RemoteCatalog.loading.value && catalog.isEmpty() -> "Cargando catálogo · Cinemeta  ·  Nuvio" ; catalog.isEmpty() -> "Sin títulos disponibles" ; else -> "Catálogo completo · toca un título para ver detalles" }, style = AppType.metadata, color = TextMuted)
        Spacer(Modifier.height(24.dp))
        when {
            RemoteCatalog.loading.value && catalog.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { LoadingSpinner(); Spacer(Modifier.height(16.dp)); Text("Cargando catálogo", style = AppType.metadata, color = TextSecondary) } }
            catalog.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(72.dp).clip(CircleShape).background(GlassFill), contentAlignment = Alignment.Center) { Text("◫", style = AppType.sectionTitle, color = TextSecondary) }; Spacer(Modifier.height(16.dp)); Text("No hay títulos", style = AppType.sectionTitle, color = TextPrimary); Spacer(Modifier.height(6.dp)); Text("Verifica tu conexión o añade addons en Ajustes → Vincular", style = AppType.body, color = TextSecondary) } }
            else -> LazyVerticalGrid(columns = GridCells.Fixed(5), contentPadding = PaddingValues(bottom = 56.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { items(catalog, key = { it.id }) { v -> PosterCard(v, onClick = { onOpenDetail(v) }, modifier = Modifier.fillMaxWidth().height(184.dp)) } }
        }
    }
}
