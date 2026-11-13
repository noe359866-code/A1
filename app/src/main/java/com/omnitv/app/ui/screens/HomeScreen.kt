package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.HeroBanner
import com.omnitv.app.ui.components.SectionRow
import com.omnitv.app.ui.theme.Background

/**
 * Pantalla de inicio tipo "Watch Now" de Apple TV:
 * banner héroe a pantalla grande + filas horizontales de contenido.
 *
 * Las filas «Mi lista» y «Continuar viendo» son propias del perfil activo;
 * los perfiles infantiles solo ven contenido apto para todos los públicos.
 */
@Composable
fun HomeScreen(
    onOpenDetail: (Video) -> Unit,
    onPlay: (Video) -> Unit
) {
    val profile = ProfileStore.currentProfile
    val featured = CatalogRepository.featuredFor(profile)
    val rows = CatalogRepository.rowsFor(profile)

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
