package com.omnitv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Tema de Omni TV: modo oscuro con el acento azul de Apple.
 *
 * Usa el `MaterialTheme` de Compose Material 3 (versión del BOM): la app no
 * usa ningún componente con foco de TV Material, así que no hace falta
 * `androidx.tv:tv-material` (Material 2), que además no expone `Text` ni
 * `MaterialTheme` en la versión del catálogo.
 */
@Composable
fun OmniTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = Background,
            onBackground = TextPrimary,
            surface = Surface,
            onSurface = TextPrimary
        ),
        content = content
    )
}
