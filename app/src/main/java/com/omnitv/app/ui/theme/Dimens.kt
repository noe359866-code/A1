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

    /**
     * Margen vertical de overscan: **27 dp**.
     *
     * La guía de layouts de Android TV pide 48 dp a los lados y 27 dp arriba y
     * abajo (5 % de un canvas de 960×540 dp). El lateral ya estaba resuelto con
     * [screenMargin]; el vertical se aplicaba con literales de 10–12 dp en cada
     * pantalla, por debajo del área segura: con overscan real un título puede
     * recortarse (falla el criterio TV-OV de Google Play).
     */
    val safeTop = 27.dp

    /** Igual que [safeTop], para el borde inferior. */
    val safeBottom = 27.dp

    /**
     * Ancho que la barra lateral flotante ocupa del borde izquierdo en su
     * estado compacto: 8 dp de separación + 76 dp de barra.
     *
     * La barra **flota** sobre el contenido (no lo empuja) para que expandirse
     * al enfocar no recomponga las rejillas. El contenido reserva este hueco
     * con `startPadding`; cuando la barra crece, se superpone al contenido en
     * lugar de desplazarlo.
     */
    val navRail = 84.dp
}

/**
 * Escala de foco para TV (10 pies).
 *
 * La guía del sistema de foco de Android TV define **tres** factores:
 * `1.025`, `1.05` y `1.1`. Antes había siete (1.02, 1.05, 1.06, 1.07, 1.08,
 * 1.10 y 1.12); los intermedios existían para granularidad fina, pero el ojo no
 * distingue 1.06 de 1.07 a tres metros: eran reglas que recordar sin diferencia
 * perceptual. Ahora se consolidan en tres niveles y **los nombres anteriores se
 * conservan como alias** para no tocar las llamadas existentes.
 *
 * Regla de asignación:
 *  - [subtle] (1.025) — filas densas que no pueden crecer sin solaparse.
 *  - [base]   (1.05)  — la mayoría de controles: chips, píldoras, pestañas, tarjetas.
 *  - [large]  (1.10)  — elementos grandes donde el foco debe verse de lejos.
 */
object FocusScale {
    /** Nivel 1 — filas densas (episodio en detalle). */
    const val subtle = 1.025f

    /** Nivel 2 — controles pequeños y medianos (chips, píldoras, teclas). */
    const val base = 1.05f

    /** Nivel 3 — controles grandes (botones circulares, tiles de perfil). */
    const val large = 1.10f

    /** Filas densas (alias de [subtle]). */
    const val row = subtle

    /** Chips, píldoras y categorías de Ajustes (alias de [base]). */
    const val chip = base

    /** Pestañas de la barra lateral y filas secundarias (alias de [base]). */
    const val tab = base

    /** Pósters y tarjetas (alias de [base]). */
    const val card = base

    /** Teclas del teclado, logo chips, «+» y píldoras del reproductor (alias de [base]). */
    const val key = base

    /** Botones circulares y tiles de gestión (alias de [large]). */
    const val control = large

    /** Tiles de perfil y toggle, los elementos más grandes del mando (alias de [large]). */
    const val hero = large

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
