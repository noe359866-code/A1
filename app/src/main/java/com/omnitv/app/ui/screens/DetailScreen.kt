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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.CircleIconButton
import com.omnitv.app.ui.components.MetaLine
import com.omnitv.app.ui.components.PillButton
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.Badge
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.tv.material.Text

/**
 * Ficha de detalle estilo Apple TV: fondo cinematográfico con velo,
 * título grande, metadatos, botones y recomendaciones.
 */
@Composable
fun DetailScreen(
    video: Video,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onOpenDetail: (Video) -> Unit
) {
    val related = remember(video.id) { CatalogRepository.related(video.id) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Fondo cinematográfico
        AsyncImage(
            model = video.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(540.dp)
                .align(Alignment.TopCenter)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(540.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Background
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(540.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.horizontalGradient(
                        0f to Background.copy(alpha = 0.9f),
                        0.5f to Background.copy(alpha = 0.25f),
                        1f to Color.Transparent
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 48.dp, end = 48.dp, top = 130.dp, bottom = 56.dp)
                .widthIn(max = 860.dp)
        ) {
            video.badge?.let { Badge(it) }
            Spacer(Modifier.height(14.dp))
            Text(video.title, style = AppType.hero, color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            MetaLine(video)
            Spacer(Modifier.height(26.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton(
                    text = "Reproducir",
                    iconRes = R.drawable.ic_play,
                    primary = true,
                    onClick = onPlay
                )
                val inMyList = ProfileStore.currentProfile?.myList?.contains(video.id) == true
                PillButton(
                    text = if (inMyList) "En Mi lista" else "Mi lista",
                    iconRes = if (inMyList) R.drawable.ic_check else R.drawable.ic_add,
                    primary = false,
                    onClick = { ProfileStore.toggleMyList(video.id) }
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                video.description,
                style = AppType.body,
                color = TextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 640.dp)
            )

            Spacer(Modifier.height(28.dp))
            InfoRow("Géneros", video.genres.joinToString(" · "))
            InfoRow("Idioma", video.language)
            InfoRow("Calidad", video.quality)
            InfoRow("Formato", video.streamFormat.name)
            if (video.cast.isNotEmpty()) {
                InfoRow("Reparto", video.cast.joinToString(" · "))
            }

            if (related.isNotEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "También te puede gustar",
                    style = AppType.sectionTitle,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 18.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(end = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(related, key = { it.id }) { item ->
                        PosterCard(item, onClick = { onOpenDetail(item) })
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }

        CircleIconButton(
            iconRes = R.drawable.ic_back,
            contentDescription = "Volver",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(28.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(bottom = 12.dp)) {
        Text(
            "$label: ",
            style = AppType.metadata,
            color = TextSecondary,
            modifier = Modifier.width(110.dp)
        )
        Text(value, style = AppType.metadata, color = TextPrimary)
    }
}
