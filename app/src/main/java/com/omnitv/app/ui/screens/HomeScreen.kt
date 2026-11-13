package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.omnitv.app.ui.theme.Background

/**
 * Pantalla de inicio tipo "Watch Now" de Apple TV:
 * banner héroe a pantalla grande + filas horizontales de contenido.
 *
 * Combina el **catálogo real** (Cinemeta + Nuvio) con las filas propias del
 * perfil («Mi lista», «Continuar viendo»). Los perfiles infantiles solo ven
 * el catálogo local curado (los addons remotos no traen clasificación por
 * edades).
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

    // Carga del catálogo real (Cinemeta + Nuvio + addons del panel QR);
    // se recarga si la configuración cambia desde el panel web
    LaunchedEffect(SettingsStore.revision) {
        if (RemoteCatalog.needsReload()) RemoteCatalog.refresh()
    }

    val localRows = CatalogRepository.rowsFor(profile)
    val kidsOnly = profile?.isKids == true
    val rows = if (kidsOnly) localRows else RemoteCatalog.rows + localRows
    val featured = if (kidsOnly) {
        CatalogRepository.featuredFor(profile)
    } else {
        RemoteCatalog.featured.value ?: CatalogRepository.featuredFor(profile)
    }

    // Foco inicial en «Reproducir» del héroe: con el D-pad, arrancar en la
    // barra de pestañas obliga a bajar por toda la barra antes de ver algo.
    val heroFocus = remember { FocusRequester() }
    RequestTvFocus(heroFocus, key = featured.id)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        // 28 dp: las filas se leen como un bloque y no como pantallas
        // separadas; el héroe ya aporta su propio aire abajo.
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
        // «Solo en {plataforma}» — como el «Only on Netflix» de flystream.net
        if (!kidsOnly) {
            item(key = "onlyOn") {
                OnlyOnSection(
                    onOpenDetail = onOpenDetail,
                    onSeeAll = onSeeAll,
                    onOpenPlatforms = onOpenPlatforms
                )
            }
            // «Por estudio» — como el «Browse by studio» de sweetfox.app
            item(key = "studios") {
                StudioStripSection(onOpenStudio = onOpenStudio)
            }
        }
        items(rows.drop(2), key = { it.title }) { row ->
            SectionRow(row = row, onOpenDetail = onOpenDetail)
        }
    }
}
