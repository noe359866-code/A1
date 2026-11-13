package com.omnitv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material.Text
import coil.compose.AsyncImage
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.StreamingProvider
import com.omnitv.app.data.Video
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/**
 * Sección «Solo en {plataforma}» — como el «Only on Netflix» de
 * flystream.net: una tira de logos de plataformas que actúan de selector y
 * una fila con el catálogo real de la plataforma elegida.
 */
@Composable
fun OnlyOnSection(
    onOpenDetail: (Video) -> Unit,
    onSeeAll: (StreamingProvider) -> Unit,
    onOpenPlatforms: () -> Unit
) {
    // Las 7 plataformas del selector, en el orden del «Only on» original
    val selector = remember {
        listOf(8, 9, 1899, 337, 350, 2303, 15).mapNotNull { com.omnitv.app.data.Providers.byId(it) }
    }
    var selected by remember { mutableStateOf(selector.first()) }
    var videos by remember(selected.id) { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember(selected.id) { mutableStateOf(true) }

    LaunchedEffect(selected.id) {
        loading = true
        videos = RemoteCatalog.fetchProviderCached(selected)
        loading = false
    }

    Column(Modifier.fillMaxWidth()) {
        // Título dinámico
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Solo en ", style = AppType.sectionTitle, color = TextPrimary)
            Text(
                selected.name,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = Accent
            )
        }

        Spacer(Modifier.height(14.dp))

        // Tira de logos (selector de plataforma + «Más»)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 48.dp)
        ) {
            items(selector, key = { it.id }) { provider ->
                ProviderLogoChip(
                    provider = provider,
                    selected = provider.id == selected.id,
                    onClick = { selected = provider }
                )
            }
            item(key = "more") {
                MoreChip(onClick = onOpenPlatforms)
            }
        }

        Spacer(Modifier.height(22.dp))

        if (loading) {
            Text(
                "Cargando el catálogo de ${selected.name}…",
                style = AppType.metadata,
                color = TextSecondary
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(end = 48.dp)
            ) {
                items(videos.take(14), key = { it.id }) { video ->
                    PosterCard(video, onClick = { onOpenDetail(video) })
                }
                item(key = "seeAll") {
                    SeeAllCard(provider = selected, onClick = { onSeeAll(selected) })
                }
            }
        }
    }
}

/** Chip de logo de plataforma (selector). */
@Composable
private fun ProviderLogoChip(
    provider: StreamingProvider,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (focused) 1.08f else 1f,
        androidx.compose.animation.core.tween(150),
        label = "logoChipScale"
    )

    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) GlassFill.copy(alpha = 0.95f) else GlassFill)
            .then(
                when {
                    focused -> Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
                    selected -> Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp))
                    else -> Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = provider.logoUrl,
            contentDescription = provider.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(84.dp)
                .height(34.dp)
        )
    }
}

/** Chip «Más» → abre la pestaña Plataformas (como el «More›» original). */
@Composable
private fun MoreChip(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Accent else GlassFill)
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Más ›",
            style = AppType.metadata,
            color = TextPrimary
        )
    }
}

/** Tarjeta final «Ver todo» → abre el catálogo completo de la plataforma. */
@Composable
private fun SeeAllCard(provider: StreamingProvider, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(160.dp, 140.dp)
            .graphicsLayer { scaleX = if (focused) 1.05f else 1f; scaleY = if (focused) 1.05f else 1f }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Accent.copy(alpha = 0.35f) else GlassFill)
            .then(
                if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ver todo", style = AppType.metadata, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "${provider.name} ›",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    }
}
