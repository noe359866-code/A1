package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.HeroBanner
import com.omnitv.app.ui.components.SectionRow
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
    onPlay: (Video) -> Unit
) {
    val profile = ProfileStore.currentProfile

    // Carga del catálogo real (Cinemeta + Nuvio), una sola vez
    LaunchedEffect(Unit) {
        if (!RemoteCatalog.loaded.value) RemoteCatalog.refresh()
    }

    val localRows = CatalogRepository.rowsFor(profile)
    val kidsOnly = profile?.isKids == true
    val rows = if (kidsOnly) localRows else RemoteCatalog.rows + localRows
    val featured = if (kidsOnly) {
        CatalogRepository.featuredFor(profile)
    } else {
        RemoteCatalog.featured.value ?: CatalogRepository.featuredFor(profile)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        verticalArrangement = Arrangement.spacedBy(34.dp),
        contentPadding = PaddingValues(bottom = 56.dp)
    ) {
        item(key = "hero") {
            HeroBanner(
                video = featured,
                onPlay = { onPlay(featured) },
                onInfo = { onOpenDetail(featured) }
            )
        }
        items(rows, key = { it.title }) { row ->
            SectionRow(row = row, onOpenDetail = onOpenDetail)
        }
    }
}
