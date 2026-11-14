package com.omnitv.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omnitv.app.R
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.*
import com.omnitv.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(onOpenDetail: (Video) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val kidsOnly = ProfileStore.currentProfile?.isKids == true
    val fieldFocus = remember { FocusRequester() }
    RequestTvFocus(fieldFocus)

    // Debounce + búsqueda viva (local + remota)
    val trimmed = query.trim()
    var remoteResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val localResults = remember(trimmed, kidsOnly) { CatalogRepository.search(trimmed, kidsOnly = kidsOnly) }

    LaunchedEffect(trimmed, kidsOnly) {
        if (trimmed.length >= 2 && !kidsOnly) {
            delay(280) // debounce para no acribillar la red al teclear
            searching = true
            remoteResults = RemoteCatalog.search(trimmed)
            searching = false
        } else {
            remoteResults = emptyList()
            searching = false
        }
    }
    val results = remember(localResults, remoteResults) { (localResults + remoteResults).distinctBy { it.id } }

    // Tops al lado del teclado — del catálogo real (trending / populares)
    val tops: List<Video> = remember(RemoteCatalog.gridVideos.size, RemoteCatalog.rows.size) {
        val pool = RemoteCatalog.gridVideos.ifEmpty { RemoteCatalog.rows.flatMap { it.items } }
        pool.distinctBy { it.id }.take(10)
    }

    val suggestions = listOf("Acción", "Aventura", "Animación", "Ciencia ficción", "Comedia", "Drama", "Terror", "Familia")

    Row(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = AppSpacing.screenMargin, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ── PANEL IZQUIERDO: teclado nativo + tops ───────────────────────
        Column(
            Modifier
                .width(420.dp)
                .fillMaxHeight()
                .clip(AppRadius.lg)
                .background(Surface)
                .border(1.dp, Color.White.copy(alpha = 0.06f), AppRadius.lg)
                .padding(20.dp)
        ) {
            Text("BUSCAR", style = AppType.overline, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            Text("Buscar", style = AppType.screenTitle, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Teclado nativo + resultados en vivo", style = AppType.caption, color = TextTertiary)

            Spacer(Modifier.height(18.dp))

            TvTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Títulos, géneros, reparto…",
                leadingIconRes = R.drawable.ic_search,
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                focusRequester = fieldFocus,
                onImeAction = { keyboard?.hide() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Chips de atajo — rellenan query en 1 click
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.take(4).forEach { s ->
                    TvChip(
                        text = s,
                        selected = query.equals(s, ignoreCase = true),
                        onClick = { query = s },
                        style = AppType.caption
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.drop(4).take(4).forEach { s ->
                    TvChip(
                        text = s,
                        selected = query.equals(s, ignoreCase = true),
                        onClick = { query = s },
                        style = AppType.caption
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (trimmed.isBlank()) "Top 10 hoy" else "Sugerencias",
                    style = AppType.sectionTitle,
                    color = TextPrimary
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(AppRadius.pill).background(Accent.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (trimmed.isBlank()) "${tops.size} títulos" else "${results.size} resultados",
                        style = AppType.caption,
                        color = Accent
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (trimmed.isBlank()) "Al lado del teclado · toca para ver"
                else "Filtra al escribir · toca para abrir",
                style = AppType.caption,
                color = TextMuted
            )

            Spacer(Modifier.height(12.dp))

            // Lista tops / sugerencias vivas (filtra al escribir)
            val sideList = if (trimmed.isBlank()) tops else results.take(10)
            if (sideList.isEmpty() && !searching) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 18.dp)
                        .clip(AppRadius.md).background(GlassFill)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (trimmed.isBlank() && tops.isEmpty()) "Cargando tops…"
                        else "Sin sugerencias",
                        style = AppType.caption,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (searching && trimmed.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                                LoadingSpinner(size = 16.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Buscando…", style = AppType.caption, color = TextSecondary)
                            }
                        }
                    }
                    itemsIndexed(sideList, key = { _, v -> v.id }) { index, video ->
                        TopRankRow(
                            rank = index + 1,
                            video = video,
                            onClick = { keyboard?.hide(); onOpenDetail(video) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Accent))
                Spacer(Modifier.width(6.dp))
                Text("OK abre Gboard · Escribe y mira a la derecha", style = AppType.caption, color = TextTertiary)
            }
        }

        // ── PANEL DERECHO: resultados en vivo ────────────────────────────
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        trimmed.isBlank() -> "Descubre"
                        searching -> "Buscando…"
                        results.isEmpty() -> "Sin resultados"
                        else -> "Resultados"
                    },
                    style = AppType.sectionTitle,
                    color = TextPrimary
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.clip(AppRadius.pill).background(GlassFill)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        when {
                            trimmed.isBlank() -> "${tops.size} destacados"
                            searching -> "Cinemeta + Nuvio…"
                            else -> "${results.size} títulos"
                        },
                        style = AppType.caption,
                        color = TextSecondary
                    )
                }
                if (trimmed.isNotBlank() && !searching) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(AppRadius.pill).background(Accent.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("En vivo", style = AppType.caption, color = Accent)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    trimmed.isBlank() -> "Empieza a escribir y la grilla cambia sola"
                    searching -> "Filtrando al escribir…"
                    results.isEmpty() -> "Prueba con 2–3 letras clave o un género"
                    else -> "Actualizado al escribir · ${results.size} coincidencias"
                },
                style = AppType.caption,
                color = TextMuted
            )

            Spacer(Modifier.height(14.dp))

            Box(Modifier.weight(1f)) {
                when {
                    trimmed.isBlank() -> {
                        if (tops.isEmpty()) {
                            SearchEmptyState(
                                iconRes = R.drawable.ic_search,
                                title = "Explora sin límites",
                                subtitle = "Toca Top 10 a la izquierda o escribe arriba",
                                hint = "Cinemeta · Nuvio · Plataformas"
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(tops, key = { it.id }) { v ->
                                    PosterCard(
                                        video = v,
                                        onClick = { onOpenDetail(v) },
                                        modifier = Modifier.fillMaxWidth().height(176.dp)
                                    )
                                }
                            }
                        }
                    }
                    searching -> {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 24.dp)) {
                            LoadingSpinner(size = 20.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Buscando en Cinemeta y Nuvio…", style = AppType.metadata, color = TextSecondary)
                        }
                    }
                    results.isEmpty() -> SearchEmptyState(
                        iconRes = R.drawable.ic_search,
                        title = "Sin resultados para “$trimmed”",
                        subtitle = "Revisa la escritura o toca un género a la izquierda",
                        hint = "Sugerencia: usa solo 2–3 letras clave"
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(results, key = { it.id }) { v ->
                            PosterCard(
                                video = v,
                                onClick = { keyboard?.hide(); onOpenDetail(v) },
                                modifier = Modifier.fillMaxWidth().height(176.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopRankRow(rank: Int, video: Video, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppRadius.md)
            .background(if (focused) Color.White.copy(alpha = 0.08f) else GlassFill)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.06f),
                shape = AppRadius.md
            )
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            String.format("%02d", rank),
            style = AppType.overline,
            color = if (rank <= 3) Accent else TextTertiary,
            modifier = Modifier.width(28.dp)
        )
        AsyncImage(
            model = video.cardUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp, 68.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceElevated)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = AppType.bodyStrong, color = if (focused) Color.White else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    video.year.takeIf { it > 0 }?.toString(),
                    video.genres.firstOrNull(),
                    video.ratingLabel.takeIf { video.hasRating }?.let { "★ $it" }
                ).joinToString(" · "),
                style = AppType.caption,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (focused) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(Accent))
        }
    }
}

@Composable
private fun SearchEmptyState(iconRes: Int, title: String, subtitle: String, hint: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Accent.copy(alpha = 0.18f), GlassFill))),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TextSecondary)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = AppType.sectionTitle, color = TextPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = AppType.body, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(10.dp))
            Text(hint, style = AppType.caption, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
