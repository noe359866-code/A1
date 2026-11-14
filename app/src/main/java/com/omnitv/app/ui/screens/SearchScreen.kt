package com.omnitv.app.ui.screens

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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.*
import com.omnitv.app.ui.theme.*

@Composable
fun SearchScreen(onOpenDetail: (Video) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val kidsOnly = ProfileStore.currentProfile?.isKids == true

    val localResults = remember(query, kidsOnly) { CatalogRepository.search(query, kidsOnly = kidsOnly) }
    var remoteResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length >= 2 && !kidsOnly) {
            searching = true
            remoteResults = RemoteCatalog.search(q)
            searching = false
        } else remoteResults = emptyList()
    }

    val results = (localResults + remoteResults).distinctBy { it.id }
    val suggestions = listOf("Acción", "Aventura", "Animación", "Ciencia ficción", "Comedia", "Drama", "Terror", "Familia")
    val fieldFocus = remember { FocusRequester() }
    RequestTvFocus(fieldFocus)

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = AppSpacing.screenMargin)) {
            Spacer(Modifier.height(14.dp))

            // Header con contador vivo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BUSCAR", style = AppType.overline, color = TextMuted)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buscar", style = AppType.screenTitle, color = TextPrimary)
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.clip(AppRadius.pill).background(GlassFill)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        when {
                            query.isBlank() -> "Todo el catálogo"
                            searching -> "Buscando…"
                            else -> "${results.size} resultados"
                        },
                        style = AppType.caption,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Campo nativo — 640dp, IME Buscar, sin teclado propio
            TvTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Títulos, géneros, reparto…",
                leadingIconRes = R.drawable.ic_search,
                imeAction = ImeAction.Search,
                focusRequester = fieldFocus,
                onImeAction = { keyboard?.hide() },
                modifier = Modifier.width(640.dp)
            )

            Spacer(Modifier.height(14.dp))

            // Chips de género — filtro rápido en 1 click
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.take(6).forEach { s ->
                    TvChip(
                        text = s,
                        selected = query.equals(s, ignoreCase = true),
                        onClick = { query = s },
                        style = AppType.caption
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Contenido: vacío / cargando / grilla
            Box(Modifier.weight(1f)) {
                when {
                    query.isBlank() -> SearchEmptyState(
                        iconRes = R.drawable.ic_search,
                        title = "Explora sin límites",
                        subtitle = "Escribe arriba o toca un género — el teclado del sistema se abre con OK",
                        hint = "Cinemeta · Nuvio · Plataformas  ·  Pulsa OK en el campo para abrir Gboard"
                    )
                    searching -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 24.dp)) {
                        LoadingSpinner(size = 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Buscando en Cinemeta y Nuvio…", style = AppType.metadata, color = TextSecondary)
                    }
                    results.isEmpty() -> SearchEmptyState(
                        iconRes = R.drawable.ic_search,
                        title = "Sin resultados para “$query”",
                        subtitle = "Revisa la escritura o prueba con otro género",
                        hint = "Sugerencia: usa solo 2–3 letras clave"
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        contentPadding = PaddingValues(bottom = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(results, key = { it.id }) { v ->
                            PosterCard(
                                video = v,
                                onClick = { keyboard?.hide(); onOpenDetail(v) },
                                modifier = Modifier.fillMaxWidth().height(188.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(iconRes: Int, title: String, subtitle: String, hint: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
        Text(title, style = AppType.sectionTitle, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = AppType.body, color = TextSecondary, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(10.dp))
            Text(hint, style = AppType.caption, color = TextMuted, textAlign = TextAlign.Center)
        }
    }
}
