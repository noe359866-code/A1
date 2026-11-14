package com.omnitv.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.TvKeypad
import com.omnitv.app.data.Video
import com.omnitv.app.ui.components.PosterCard
import com.omnitv.app.ui.components.RequestTvFocus
import com.omnitv.app.ui.components.TvChip
import com.omnitv.app.ui.components.TvKeyboard
import com.omnitv.app.ui.components.TvTextField
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/**
 * Buscador sobre todo el catálogo: resultados **locales + remotos**
 * (Cinemeta y Nuvio).
 *
 * Al entrar, el foco va al campo y se abre el **teclado en pantalla** de la app
 * (`TvKeyboard`): en Android TV el teclado del sistema no aparece de forma
 * fiable con Compose, así que el buscador trae el suyo para que un mando sin
 * teclados pueda escribir. Se maneja con las flechas y OK, y se cierra con
 * «LISTO», que devuelve el foco al campo y deja ver la lista completa.
 *
 * La búsqueda no distingue acentos ni mayúsculas («accion» encuentra «Acción»),
 * porque el teclado de la tele no tiene tildes.
 */
@Composable
fun SearchScreen(onOpenDetail: (Video) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val kidsOnly = ProfileStore.currentProfile?.isKids == true
    val localResults = remember(query, kidsOnly) { CatalogRepository.search(query, kidsOnly = kidsOnly) }

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

    // Al entrar en la pantalla el foco va al campo: en un mando, tener que
    // atravesar la barra de pestañas para poder buscar es un peaje innecesario.
    val fieldFocus = remember { FocusRequester() }
    RequestTvFocus(fieldFocus)

    // Teclado en pantalla: se abre al entrar en el campo y se cierra con
    // «LISTO». Mientras está abierto ocupa el sitio de las sugerencias.
    var keyboardOpen by rememberSaveable { mutableStateOf(false) }
    var keyboardJustClosed by remember { mutableStateOf(false) }
    val firstKeyFocus = remember { FocusRequester() }

    // Cada vez que el teclado se abre, el foco entra en la primera tecla para
    // que el mando escriba sin pasos intermedios. Depende de `keyboardOpen`: si
    // fuera `Unit`, al reabrir el foco se quedaría donde estaba.
    LaunchedEffect(keyboardOpen) {
        if (keyboardOpen) {
            withFrameNanos { }
            runCatching { firstKeyFocus.requestFocus() }
        }
    }

    // «Atrás» con el teclado abierto lo cierra en vez de salir de la pantalla.
    BackHandler(enabled = keyboardOpen) {
        keyboardOpen = false
        keyboardJustClosed = true
        runCatching { fieldFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        // ---- Contenido de la pantalla (campo, sugerencias o resultados) ----
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text("Buscar", style = AppType.screenTitle, color = TextPrimary)
            Spacer(Modifier.height(22.dp))
            TvTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Títulos, géneros, sinopsis…",
                leadingIconRes = R.drawable.ic_search,
                imeAction = ImeAction.Search,
                focusRequester = fieldFocus,
                // El IME del sistema no se pide: el teclado es el de la app.
                imeOnCenter = false,
                // Con el teclado abierto el campo no debe poder recuperar el
                // foco, o el D-pad se sale de las teclas y el mando deja de
                // escribir sin que se vea por qué.
                focusable = !keyboardOpen,
                // Al enfocar el campo se abre el teclado de la app (no el del
                // sistema): en un televisor el IME no aparece de forma fiable.
                onFocusChange = { focused ->
                    // Solo abre al entrar con el mando, no cuando «LISTO»
                    // devuelve el foco al campo (si no, se reabriría solo).
                    if (focused && !keyboardJustClosed) keyboardOpen = true
                    keyboardJustClosed = false
                },
                modifier = Modifier.width(620.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Las sugerencias solo ocupan sitio cuando el teclado está cerrado.
            if (!keyboardOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    suggestions.forEach { suggestion ->
                        TvChip(suggestion, onClick = { query = suggestion })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            Box(Modifier.weight(1f).padding(bottom = 56.dp)) {
                when {
                    // Con el teclado abierto el estado vacío grande sobra: se
                    // queda detrás del teclado y no se lee. La consulta ya se ve
                    // en el campo, que está encima del teclado.
                    query.isBlank() && keyboardOpen -> Unit
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
                            columns = GridCells.Fixed(5),
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
                                        .height(210.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Teclado en pantalla ----
        //
        // Se dibuja **encima** del contenido, anclado abajo: con 4 filas de
        // teclas no cabe debajo del buscador sin dejar los resultados en una
        // franja inútil. Así se sigue viendo la consulta y lo que va saliendo
        // mientras se escribe; «LISTO» lo cierra y deja la lista completa.
        AnimatedVisibility(
            visible = keyboardOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // Velo: separa el teclado de los pósters que quedan detrás.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Background.copy(alpha = 0f),
                            0.28f to Background.copy(alpha = 0.92f),
                            1f to Background
                        )
                    )
                    .padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 24.dp)
            ) {
                TvKeyboard(
                    onKey = { key -> query = TvKeypad.apply(query, key) },
                    onDone = {
                        // «LISTO» no lanza la búsqueda: los resultados ya se
                        // están actualizando mientras se escribe. Solo cierra
                        // el teclado y devuelve el foco al campo.
                        keyboardOpen = false
                        keyboardJustClosed = true
                        runCatching { fieldFocus.requestFocus() }
                    },
                    firstKeyFocus = firstKeyFocus
                )
            }
        }
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
