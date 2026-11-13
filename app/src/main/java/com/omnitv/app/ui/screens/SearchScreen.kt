package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.omnitv.app.R
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary
import androidx.tv.material.Text

/**
 * Buscador sobre todo el catálogo: resultados **locales + remotos**
 * (Cinemeta y Nuvio). El campo recibe el foco del mando y muestra
 * sugerencias de géneros cuando la consulta está vacía.
 */
@Composable
fun SearchScreen(onOpenDetail: (Video) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val kidsOnly = ProfileStore.currentProfile?.isKids == true
    val localResults = remember(query, kidsOnly) { CatalogRepository.search(query, kidsOnly) }

    // Búsqueda remota (catálogo real) con rebote
    var remoteResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length >= 2 && !kidsOnly) {
            searching = true
            remoteResults = RemoteCatalog.search(q)
            searching = false
        } else {
            remoteResults = emptyList()
        }
    }
    val results = (localResults + remoteResults).distinctBy { it.id }
    val suggestions = listOf("Animación", "Ciencia ficción", "Motor", "Demo", "Fantasía")

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 48.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Buscar", style = AppType.screenTitle, color = TextPrimary)
        Spacer(Modifier.height(22.dp))
        SearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.width(560.dp)
        )

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            suggestions.forEach { suggestion ->
                SuggestionChip(suggestion) { query = suggestion }
            }
        }

        Spacer(Modifier.height(28.dp))

        when {
            query.isBlank() -> EmptyState(
                iconRes = R.drawable.ic_search,
                title = "Busca en todo el catálogo",
                subtitle = "Cinemeta · Nuvio · contenido demo · escribe un título o género"
            )
            searching -> Text(
                "Buscando en Cinemeta y Nuvio…",
                style = AppType.metadata,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 18.dp)
            )
            results.isEmpty() -> EmptyState(
                iconRes = R.drawable.ic_search,
                title = "Sin resultados para «$query»",
                subtitle = "Prueba con otro término o género"
            )
            else -> {
                Text(
                    "${results.size} resultados",
                    style = AppType.metadata,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 18.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(bottom = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(results, key = { it.id }) { video ->
                        PosterCard(
                            video = video,
                            onClick = { onOpenDetail(video) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) SurfaceElevated else GlassFill)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TextSecondary)
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Títulos, géneros, sinopsis…", style = AppType.body, color = TextSecondary)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 17.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (focused) Accent else GlassFill)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(label, style = AppType.metadata, color = TextPrimary)
    }
}

/** Estado vacío con icono (sin resultados / búsqueda inicial). */
@Composable
private fun EmptyState(iconRes: Int, title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GlassFill),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TextSecondary)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = AppType.sectionTitle, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = AppType.body, color = TextSecondary)
    }
}
