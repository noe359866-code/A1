package com.omnitv.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.TvKeypad
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.*
import com.omnitv.app.ui.theme.*

@Composable
fun SearchScreen(onOpenDetail: (Video) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val kidsOnly = ProfileStore.currentProfile?.isKids == true
    val localResults = remember(query, kidsOnly) { CatalogRepository.search(query, kidsOnly = kidsOnly) }
    var remoteResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length >= 2 && !kidsOnly) { searching = true; remoteResults = RemoteCatalog.search(q); searching = false } else remoteResults = emptyList()
    }
    val results = (localResults + remoteResults).distinctBy { it.id }
    val suggestions = listOf("Acción", "Aventura", "Animación", "Ciencia ficción", "Comedia", "Drama", "Terror", "Familia")
    val fieldFocus = remember { FocusRequester() }
    RequestTvFocus(fieldFocus)
    var keyboardOpen by rememberSaveable { mutableStateOf(false) }
    var keyboardJustClosed by remember { mutableStateOf(false) }
    val firstKeyFocus = remember { FocusRequester() }
    LaunchedEffect(keyboardOpen) { if (keyboardOpen) { withFrameNanos { }; runCatching { firstKeyFocus.requestFocus() } } }
    BackHandler(enabled = keyboardOpen) { keyboardOpen = false; keyboardJustClosed = true; runCatching { fieldFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = AppSpacing.screenMargin)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buscar", style = AppType.screenTitle, color = TextPrimary)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.clip(AppRadius.pill).background(GlassFill).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(if (query.isBlank()) "Todo el catálogo" else "${results.size} resultados", style = AppType.caption, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(20.dp))
            TvTextField(value = query, onValueChange = { query = it }, placeholder = "Títulos, géneros, reparto…", leadingIconRes = R.drawable.ic_search, imeAction = ImeAction.Search, focusRequester = fieldFocus, imeOnCenter = false, focusable = !keyboardOpen, onFocusChange = { f -> if (f && !keyboardJustClosed) keyboardOpen = true; keyboardJustClosed = false }, modifier = Modifier.width(640.dp))
            Spacer(Modifier.height(16.dp))
            if (!keyboardOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    suggestions.take(6).forEach { s -> TvChip(s, onClick = { query = s }, style = AppType.caption) }
                }
                Spacer(Modifier.height(20.dp))
            }
            Box(Modifier.weight(1f).padding(bottom = 56.dp)) {
                when {
                    query.isBlank() && keyboardOpen -> Unit
                    query.isBlank() -> EmptyState(R.drawable.ic_search, "Explora sin límites", "Escribe un título o elige un género de arriba", "Cinemeta  ·  Nuvio  ·  Plataformas")
                    searching -> Column { Spacer(Modifier.height(24.dp)); Row(verticalAlignment = Alignment.CenterVertically) { LoadingSpinner(size = 20.dp); Spacer(Modifier.width(12.dp)); Text("Buscando en Cinemeta y Nuvio…", style = AppType.metadata, color = TextSecondary) } }
                    results.isEmpty() -> EmptyState(R.drawable.ic_search, "Sin resultados para “$query”", "Revisa la escritura o prueba con otro género", "Sugerencia: usa solo 2–3 letras clave")
                    else -> {
                        LazyVerticalGrid(columns = GridCells.Fixed(5), contentPadding = PaddingValues(bottom = 56.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            items(results, key = { it.id }) { v -> PosterCard(v, onClick = { onOpenDetail(v) }, modifier = Modifier.fillMaxWidth().height(190.dp)) }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = keyboardOpen, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Background.copy(alpha = 0f), 0.28f to Background.copy(alpha = 0.92f), 1f to Background)).padding(start = AppSpacing.screenMargin, end = AppSpacing.screenMargin, top = 40.dp, bottom = 20.dp)) {
                TvKeyboard(onKey = { k -> query = TvKeypad.apply(query, k) }, onDone = { keyboardOpen = false; keyboardJustClosed = true; runCatching { fieldFocus.requestFocus() } }, firstKeyFocus = firstKeyFocus)
            }
        }
    }
}

@Composable
private fun EmptyState(iconRes: Int, title: String, subtitle: String, hint: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(80.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Accent.copy(alpha = 0.22f), GlassFill))), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(34.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TextSecondary))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = AppType.sectionTitle, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = AppType.body, color = TextSecondary, textAlign = TextAlign.Center)
        if (hint != null) { Spacer(Modifier.height(6.dp)); Text(hint, style = AppType.caption, color = TextMuted) }
    }
}
