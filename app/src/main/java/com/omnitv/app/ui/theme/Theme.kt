package com.omnitv.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material.MaterialTheme
import androidx.tv.material.darkColorScheme

/**
 * Tema de Omni TV: modo oscuro con el acento azul de Apple.
 * Usa el [MaterialTheme] de Compose for TV (androidx.tv.material).
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
