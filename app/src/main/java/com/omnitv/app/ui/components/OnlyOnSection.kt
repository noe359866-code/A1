@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.omnitv.app.data.Providers
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.StreamingProvider
import com.omnitv.app.data.Video
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.BadgeAccent
import com.omnitv.app.ui.theme.FocusScale
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import com.omnitv.app.ui.theme.appTween

/**
 * Sección «Solo en {plataforma}» — como el «Only on Netflix» de
 * flystream.net: una tira de logos de plataformas que actúan de selector y
 * una fila con el catálogo real de la plataforma elegida.
 */
@Composable
fun OnlyOnSection(
    onOpenDetail: (Video) -> Unit,
    onSeeAll: (StreamingProvider) -> Unit,
    onOpenPlatforms: () -> Unit,
    cardModifier: (Video) -> Modifier = { Modifier },
    onItemFocused: ((Video) -> Unit)? = null
) {
    val selector = remember {
        listOf(8, 9, 1899, 337, 350, 2303, 15).mapNotNull { Providers.byId(it) }
    }
    var selected by remember { mutableStateOf(selector.first()) }
    var videos by remember(selected.id) { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember(selected.id) { mutableStateOf(true) }

    LaunchedEffect(selected.id) {
        loading = true
        videos = RemoteCatalog.fetchProviderCached(selected)
        loading = false
    }

    Column(Modifier.fillMaxWidth().bringIntoViewIfChildrenAreFocused()) {
        // Título dinámico
        Row(
            Modifier.padding(horizontal = AppSpacing.screenMargin),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Solo en ", style = AppType.sectionTitle, color = TextPrimary)
            Text(
                selected.name,
                style = AppType.sectionTitle,
                color = Accent
            )
        }

        Spacer(Modifier.height(6.dp))

        // Tira de logos (selector de plataforma + «Más»)
        ProvideHorizontalTvBringIntoView {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = AppSpacing.screenMargin, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer()
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
        }

        Spacer(Modifier.height(10.dp))

        if (loading) {
            Row(
                modifier = Modifier.padding(horizontal = AppSpacing.screenMargin, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LoadingSpinner(size = 24.dp)
                Text(
                    "Cargando el catálogo de ${selected.name}…",
                    style = AppType.metadata,
                    color = TextSecondary
                )
            }
        } else {
            ProvideHorizontalTvBringIntoView {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(horizontal = AppSpacing.screenMargin, vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup()
                        .focusRestorer()
                ) {
                    items(videos.take(14), key = { it.id }) { video ->
                        PosterCard(
                            video = video,
                            onClick = { onOpenDetail(video) },
                            onFocus = onItemFocused?.let { cb -> { cb(video) } },
                            modifier = Modifier
                                .width(156.dp)
                                .then(cardModifier(video))
                        )
                    }
                    item(key = "seeAll") {
                        SeeAllCard(provider = selected, onClick = { onSeeAll(selected) })
                    }
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
    val scale by animateFloatAsState(
        if (focused) FocusScale.key else 1f,
        appTween(150),
        label = "logoChipScale"
    )

    Box(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.md)
            .background(if (selected) GlassFill.copy(alpha = 0.95f) else GlassFill)
            .then(
                when {
                    focused -> Modifier.border(AppStroke.focus, Color.White, AppRadius.md)
                    selected -> Modifier.border(2.dp, Accent, AppRadius.md)
                    else -> Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), AppRadius.md)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        var logoLoaded by remember(provider.id) { mutableStateOf(false) }
        var logoFailed by remember(provider.id) { mutableStateOf(false) }
        Box(
            Modifier
                .width(72.dp)
                .height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!logoLoaded || logoFailed) {
                Text(
                    provider.name,
                    style = AppType.cardTitle,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AsyncImage(
                model = provider.logoUrl,
                contentDescription = provider.name,
                contentScale = ContentScale.Fit,
                onSuccess = { logoLoaded = true; logoFailed = false },
                onError = { logoFailed = true },
                modifier = Modifier
                    .width(72.dp)
                    .height(34.dp)
                    .clip(AppRadius.xs)
            )
        }
    }
}

/** Chip «Más» → abre la pestaña Plataformas. */
@Composable
private fun MoreChip(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FocusScale.key else 1f, appTween(150), label = "moreChipScale")
    Box(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.md)
            .background(if (focused) BadgeAccent else GlassFill)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.md)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), AppRadius.md)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
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
    val scale by animateFloatAsState(if (focused) FocusScale.tab else 1f, appTween(150), label = "seeAllScale")
    Box(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .width(156.dp)
            .aspectRatio(0.67f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.md)
            .background(if (focused) Accent.copy(alpha = 0.35f) else GlassFill)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.md)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), AppRadius.md)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ver todo", style = AppType.metadata, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "${provider.name} ›",
                style = AppType.cardTitle,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    }
}
