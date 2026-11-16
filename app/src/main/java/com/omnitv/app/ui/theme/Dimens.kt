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
 */
object AppSpacing {
    val screenMargin = 48.dp
}

/**
 * Trazos de foco para 10 pies: anillo blanco sólido de 3 dp (u oscuro sobre
 * superficies claras como los botones primarios).
 */
object AppStroke {
    val focus = 3.dp
}
