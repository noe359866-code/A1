package com.omnitv.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Escala de formas de Omni TV:
 *  - [xs] 4 dp — etiquetas muy pequeñas (badge, «KIDS», chips de cabecera).
 *  - [sm] 8 dp — miniaturas y elementos dentro de una tarjeta.
 *  - [md] 14 dp — tarjetas y campos de texto.
 *  - [lg] 22 dp — píldoras de navegación y paneles grandes.
 *  - [pill] — cápsula completa para chips y botones redondeados.
 */
object AppRadius {
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(999.dp)
}

/**
 * Márgenes laterales de seguridad de TV (zona segura de overscan).
 *
 * **Espaciado interno**: la app usa una escala informal de 4–48 dp aplicada con
 * literales (`Spacer(height(22.dp))`, `spacedBy(14.dp)`…). Se conserva así a
 * propósito: extraerla a tokens sin poder validar el resultado en pantalla
 * sería reorganizar 2–3 cientos de llamadas a ciegas. El único token
 * obligatorio es [screenMargin].
 */
object AppSpacing {
    val screenMargin = 48.dp
}

/**
 * Escala de foco por jerarquía para TV (10 pies). Un token por nivel; los
 * valores son los que la app ya usaba en producción, ahora con nombre.
 */
object FocusScale {
    /** Controles pequeños: chips, píldoras, categorías de Ajustes. */
    const val chip = 1.05f

    /** Pestañas y filas secundarias (Continuar viendo, tiles de plataforma). */
    const val tab = 1.06f

    /** Pósters y tarjetas (el tamaño más común). */
    const val card = 1.07f

    /** Controles medios (1,08): teclas del teclado, logo chips, «+», píldoras del reproductor. */
    const val key = 1.08f

    /** Controles grandes (botones circulares, tiles de gestión). */
    const val control = 1.10f

    /** Tiles de perfil y toggle (los elementos más grandes del mando). */
    const val hero = 1.12f

    /** Filas densas (episodio en detalle). */
    const val row = 1.02f

    /** Contracción al pulsar (con OK/Enter) el control enfocado. */
    const val pressFactor = 0.94f
}

/**
 * Trazos de foco para 10 pies: anillo blanco sólido de 3 dp (u oscuro sobre
 * superficies claras como los botones primarios).
 */
object AppStroke {
    val focus = 3.dp
}
