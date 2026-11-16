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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.omnitv.app.data.Studio
import com.omnitv.app.data.Studios
import com.omnitv.app.ui.theme.FocusScale
import com.omnitv.app.ui.theme.LogoPlate
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppSpacing
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import com.omnitv.app.ui.theme.appTween

/**
 * Sección «Por estudio» — como el «Browse by studio» de sweetfox.app:
 * tira de logos de estudios (DreamWorks, Marvel, Pixar, Ghibli…) que abren
 * el catálogo real de cada estudio.
 */
@Composable
fun StudioStripSection(
    onOpenStudio: (Studio) -> Unit,
    tileModifier: (Studio) -> Modifier = { Modifier },
    onStudioFocused: ((Studio) -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().bringIntoViewIfChildrenAreFocused()) {
        Text(
            "Por estudio",
            style = AppType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = AppSpacing.screenMargin)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "DreamWorks, Marvel, Pixar, Studio Ghibli, A24…",
            style = AppType.metadata,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = AppSpacing.screenMargin)
        )
        Spacer(Modifier.height(8.dp))
        ProvideHorizontalTvBringIntoView {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = AppSpacing.screenMargin, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer()
            ) {
                items(Studios.all, key = { it.id }) { studio ->
                    StudioTile(
                        studio = studio,
                        onClick = { onOpenStudio(studio) },
                        onFocus = onStudioFocused?.let { cb -> { cb(studio) } },
                        modifier = tileModifier(studio)
                    )
                }
            }
        }
    }
}

/** Tile de estudio (logo TMDB sobre tarjeta de vidrio). */
@Composable
fun StudioTile(
    studio: Studio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) FocusScale.card else 1f,
        appTween(150),
        label = "studioScale"
    )

    Column(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppRadius.md)
            .background(if (focused) GlassFill.copy(alpha = 0.95f) else GlassFill)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, AppRadius.md)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), AppRadius.md)
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(AppRadius.sm)
                .background(LogoPlate.copy(alpha = 0.94f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = studio.logoUrl,
                contentDescription = studio.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(116.dp)
                    .height(44.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            studio.name,
            style = AppType.metadata,
            color = if (focused) TextPrimary else TextSecondary
        )
    }
}
