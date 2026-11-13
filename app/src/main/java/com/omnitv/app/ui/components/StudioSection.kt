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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.material.Text
import coil.compose.AsyncImage
import com.omnitv.app.data.Studio
import com.omnitv.app.data.Studios
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/**
 * Sección «Por estudio» — como el «Browse by studio» de sweetfox.app:
 * tira de logos de estudios (DreamWorks, Marvel, Pixar, Ghibli…) que abren
 * el catálogo real de cada estudio.
 */
@Composable
fun StudioStripSection(
    onOpenStudio: (Studio) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Por estudio", style = AppType.sectionTitle, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Browse by studio · DreamWorks, Marvel, Pixar, Ghibli, A24…",
            style = AppType.metadata,
            color = TextSecondary
        )
        Spacer(Modifier.height(18.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 48.dp)
        ) {
            items(Studios.all, key = { it.id }) { studio ->
                StudioTile(studio, onClick = { onOpenStudio(studio) })
            }
        }
    }
}

/** Tile de estudio (logo TMDB sobre tarjeta de vidrio). */
@Composable
fun StudioTile(
    studio: Studio,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (focused) 1.07f else 1f,
        androidx.compose.animation.core.tween(150),
        label = "studioScale"
    )

    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) GlassFill.copy(alpha = 0.95f) else GlassFill)
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp))
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = studio.logoUrl,
            contentDescription = studio.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(120.dp)
                .height(48.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            studio.name,
            style = AppType.metadata,
            color = if (focused) TextPrimary else TextSecondary
        )
    }
}
