package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.*
import com.omnitv.app.ui.components.*
import com.omnitv.app.ui.theme.*

@Composable
fun PlataformasScreen(onOpenProvider: (StreamingProvider) -> Unit, onOpenStudio: (Studio) -> Unit) {
    val kidsOnly = ProfileStore.currentProfile?.isKids == true
    Column(Modifier.fillMaxSize().background(Background).padding(horizontal = AppSpacing.screenMargin)) {
        Spacer(Modifier.height(14.dp))
        Text("PLATAFORMAS", style = AppType.overline, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        Text("Plataformas", style = AppType.screenTitle, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Proveedor y estudio · catálogo real de cinejoy.pk, Nuvio y sweetfox", style = AppType.metadata, color = TextMuted)
        Spacer(Modifier.height(24.dp))
        if (kidsOnly) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No disponible en perfil infantil", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Cambia a un perfil estándar para explorar plataformas", style = AppType.body, color = TextSecondary)
                }
            }
            return
        }
        LazyVerticalGrid(columns = GridCells.Fixed(5), contentPadding = PaddingValues(bottom = 56.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("Plataformas de streaming", style = AppType.sectionTitle, color = TextPrimary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
            items(Providers.all, key = { "p-${it.id}" }) { p -> ProviderTile(p, onClick = { onOpenProvider(p) }) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("Estudios", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("DreamWorks · Marvel · Pixar · Ghibli · A24 · Warner Bros.", style = AppType.caption, color = TextMuted)
                }
            }
            items(Studios.all, key = { "s-${it.id}" }) { s -> StudioTile(s, onClick = { onOpenStudio(s) }) }
        }
    }
}

@Composable
private fun ProviderTile(provider: StreamingProvider, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.06f else 1f, androidx.compose.animation.core.tween(AppMotion.Normal, easing = AppMotion.Emphasized), label = "pScale")
    Column(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.shadow(if (focused) 14.dp else 0.dp, AppRadius.md).clip(AppRadius.md)
            .background(if (focused) Color.White.copy(alpha = 0.10f) else GlassFill).border(if (focused) AppStroke.focus else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.07f), AppRadius.md)
            .clickable(remember { MutableInteractionSource() }, null, onClick).onFocusChanged { focused = it.isFocused }.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(provider.logoUrl, provider.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(10.dp))
        Text(provider.name, style = AppType.caption, color = if (focused) TextPrimary else TextSecondary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PlatformCatalogScreen(provider: StreamingProvider, onBack: () -> Unit, onOpenDetail: (Video) -> Unit) {
    var videos by remember(provider.id) { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember(provider.id) { mutableStateOf(true) }
    var filter by remember(provider.id) { mutableStateOf("Todas") }
    LaunchedEffect(provider.id) { videos = RemoteCatalog.fetchProviderCached(provider); loading = false }
    val filtered = when (filter) { "Películas" -> videos.filter { it.type == VideoType.MOVIE }; "Series" -> videos.filter { it.type != VideoType.MOVIE }; else -> videos }
    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = AppSpacing.screenMargin)) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(provider.logoUrl, provider.name, contentScale = ContentScale.Fit, modifier = Modifier.width(96.dp).height(44.dp).clip(AppRadius.sm))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(provider.name, style = AppType.sectionTitle, color = TextPrimary)
                    Text("cinejoy.pk${if (provider.nuvioMoviesCatalog != null) " · Nuvio" else ""}", style = AppType.caption, color = TextMuted)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(AppRadius.pill).background(GlassFill).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("${filtered.size} títulos", style = AppType.caption, color = TextSecondary) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Todas", "Películas", "Series").forEach { o -> TvChip(o, selected = filter == o, onClick = { filter = o }, style = AppType.caption) } }
            Spacer(Modifier.height(12.dp))
            if (!loading && filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Sin títulos", style = AppType.sectionTitle, color = TextPrimary); Spacer(Modifier.height(6.dp)); Text("No se pudo cargar ${provider.name}", style = AppType.metadata, color = TextSecondary) } }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(5), contentPadding = PaddingValues(bottom = 56.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(filtered, key = { it.id }) { v -> PosterCard(v, onClick = { onOpenDetail(v) }, modifier = Modifier.fillMaxWidth().height(168.dp)) }
                }
            }
        }
        CircleIconButton(R.drawable.ic_back, "Volver", onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(AppSpacing.screenMargin))
    }
}
