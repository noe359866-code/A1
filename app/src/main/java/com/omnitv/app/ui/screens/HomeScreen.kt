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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.data.Studio
import com.omnitv.app.data.StreamingProvider
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.HeroBanner
import com.omnitv.app.ui.components.OnlyOnSection
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.SectionRow
import com.omnitv.app.ui.components.StudioStripSection
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.compose.material.Text

/**
 * Pantalla de inicio tipo "Watch Now" de Apple TV:
 * banner héroe + filas horizontales de contenido real.
 *
 * Todo el catálogo proviene de RemoteCatalog (Cinemeta / Nuvio / addons).
 * Las filas personales ("Mi lista", "Continuar viendo") son del perfil activo.
 */
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

    // Héroe: título destacado remoto. Si aún no hay datos, muestra estado vacío.
    val featured: Video? = RemoteCatalog.featured.value

    // Foco inicial en el héroe cuando existe
    val heroFocus = remember { FocusRequester() }
    if (featured != null) {
        RequestTvFocus(heroFocus, key = featured.id)
    }

    when {
        // Cargando catálogo inicial
        RemoteCatalog.loading.value && remoteRows.isEmpty() && localRows.isEmpty() -> {
            Box(
                Modifier.fillMaxSize().background(Background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Cargando catálogo…", style = AppType.sectionTitle, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Cinemeta · Nuvio · addons configurados", style = AppType.metadata, color = TextSecondary)
                }
            }
        }

        // Héroe no disponible pero hay filas
        featured == null -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Background),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                contentPadding = PaddingValues(bottom = 56.dp, top = 32.dp)
            ) {
                if (rows.isEmpty()) {
                    item(key = "empty") {
                        EmptyHomeState()
                    }
                } else {
                    items(rows.take(2), key = { it.title }) { row ->
                        SectionRow(row = row, onOpenDetail = onOpenDetail)
                    }
                    if (!kidsOnly) {
                        item(key = "onlyOn") {
                            OnlyOnSection(
                                onOpenDetail = onOpenDetail,
                                onSeeAll = onSeeAll,
                                onOpenPlatforms = onOpenPlatforms
                            )
                        }
                        item(key = "studios") {
                            StudioStripSection(onOpenStudio = onOpenStudio)
                        }
                    }
                    items(rows.drop(2), key = { it.title }) { row ->
                        SectionRow(row = row, onOpenDetail = onOpenDetail)
                    }
                }
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Background),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                contentPadding = PaddingValues(bottom = 56.dp)
            ) {
                item(key = "hero") {
                    HeroBanner(
                        video = featured,
                        onPlay = { onPlay(featured) },
                        onInfo = { onOpenDetail(featured) },
                        playFocusRequester = heroFocus
                    )
                }
                items(rows.take(2), key = { it.title }) { row ->
                    SectionRow(row = row, onOpenDetail = onOpenDetail)
                }
                if (!kidsOnly) {
                    item(key = "onlyOn") {
                        OnlyOnSection(
                            onOpenDetail = onOpenDetail,
                            onSeeAll = onSeeAll,
                            onOpenPlatforms = onOpenPlatforms
                        )
                    }
                    item(key = "studios") {
                        StudioStripSection(onOpenStudio = onOpenStudio)
                    }
                }
                items(rows.drop(2), key = { it.title }) { row ->
                    SectionRow(row = row, onOpenDetail = onOpenDetail)
                }
                if (rows.isEmpty()) {
                    item(key = "empty-after-hero") {
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Añade títulos a tu lista o explora el catálogo",
                                style = AppType.metadata,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeState() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tu catálogo está vacío", style = AppType.sectionTitle, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Conecta addons en Ajustes → Vincular cuenta o espera a que cargue el catálogo",
                style = AppType.metadata,
                color = TextSecondary
            )
        }
    }
}
