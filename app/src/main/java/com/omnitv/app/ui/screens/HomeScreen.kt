package com.omnitv.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.data.Studio
import com.omnitv.app.data.StreamingProvider
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.HeroBanner
import com.omnitv.app.ui.components.LoadingSpinner
import com.omnitv.app.ui.components.OnlyOnSection
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.SectionRow
import com.omnitv.app.ui.components.StudioStripSection
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

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
    val remoteRows = RemoteCatalog.rows
    val rows = if (kidsOnly) localRows else remoteRows + localRows
    val featured: Video? = RemoteCatalog.featured.value
    val heroFocus = remember { FocusRequester() }
    if (featured != null) RequestTvFocus(heroFocus, key = featured.id)

    when {
        RemoteCatalog.loading.value && remoteRows.isEmpty() && localRows.isEmpty() -> {
            Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingSpinner()
                    Spacer(Modifier.height(20.dp))
                    Text("Preparando tu catálogo", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Cinemeta  ·  Nuvio  ·  Tus addons", style = AppType.metadata, color = TextSecondary)
                    Spacer(Modifier.height(24.dp))
                    ShimmerRow()
                }
            }
        }
        featured == null && rows.isEmpty() -> {
            Box(Modifier.fillMaxSize().background(Background).padding(horizontal = AppSpacing.screenMargin), contentAlignment = Alignment.Center) {
                EmptyHomeState()
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Background),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap),
                contentPadding = PaddingValues(bottom = 56.dp)
            ) {
                if (featured != null) {
                    item(key = "hero") {
                        HeroBanner(video = featured, onPlay = { onPlay(featured) }, onInfo = { onOpenDetail(featured) }, playFocusRequester = heroFocus)
                    }
                }
                items(rows.take(2), key = { it.title }) { row -> SectionRow(row = row, onOpenDetail = onOpenDetail) }
                if (!kidsOnly) {
                    item(key = "onlyOn") {
                        Column(Modifier.padding(start = AppSpacing.screenMargin)) {
                            OnlyOnSection(onOpenDetail = onOpenDetail, onSeeAll = onSeeAll, onOpenPlatforms = onOpenPlatforms)
                        }
                    }
                    item(key = "studios") {
                        Column(Modifier.padding(start = AppSpacing.screenMargin)) {
                            StudioStripSection(onOpenStudio = onOpenStudio)
                        }
                    }
                }
                items(rows.drop(2), key = { it.title }) { row -> SectionRow(row = row, onOpenDetail = onOpenDetail) }
                if (rows.isEmpty() && featured != null) {
                    item(key = "empty-after-hero") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenMargin, vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text("Añade títulos a tu lista para verlos aquí", style = AppType.metadata, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(GlassFill),
            contentAlignment = Alignment.Center
        ) {
            Text("◐", style = AppType.display, color = Accent)
        }
        Spacer(Modifier.height(20.dp))
        Text("Tu catálogo está vacío", style = AppType.screenTitle, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "Conecta addons en Ajustes → Vincular cuenta\no espera a que Cinemeta y Nuvio carguen",
            style = AppType.body, color = TextSecondary,
            modifier = Modifier.widthIn(max = 520.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) }
        }
    }
}

@Composable
private fun ShimmerRow() {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "x")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = AppSpacing.screenMargin)) {
        repeat(4) {
            Box(
                Modifier.width(220.dp).height(124.dp).clip(com.omnitv.app.ui.theme.AppRadius.md)
                    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.06f)), start = androidx.compose.ui.geometry.Offset(x * 400f, 0f), end = androidx.compose.ui.geometry.Offset(x * 400f + 300f, 0f)))
            )
        }
    }
}
