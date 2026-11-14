package com.omnitv.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de formas de Omni TV.
 *
 * Antes había dieciséis radios distintos (2, 4, 5, 9, 10, 11, 12, 14, 16, 17,
 * 18, 20, 22, 26, 30…) elegidos a ojo en cada componente, así que dos tarjetas
 * vecinas podían tener esquinas distintas. Cuatro pasos bastan:
 *
 *  - [xs] 4 dp — etiquetas muy pequeñas (badge, «KIDS», versalitas).
 *  - [sm] 8 dp — miniaturas y elementos dentro de una tarjeta.
 *  - [md] 14 dp — **tarjetas y campos de texto** (el valor por defecto).
 *  - [lg] 22 dp — píldoras y paneles grandes.
 *
 * Para formas completamente redondas (círculos, chips cápsula) se usa
 * [RoundedCornerShape] al 50 % con [Pill].
 */
object AppRadius {
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(22.dp)

    /** Cápsula: chips, píldoras y botones redondeados. */
    val pill = RoundedCornerShape(999.dp)

    /**
     * Cápsula exacta para un alto conocido: el radio es la mitad del alto.
     *
     * Es preferible a [pill] cuando el componente tiene altura fija, porque el
     * radio se ajusta solo si mañana cambia el alto y no hay forma de que dos
     * esquinas queden descuadradas.
     */
    fun capsule(height: Dp) = RoundedCornerShape(height / 2)
}

/**
 * Escala de espaciado (múltiplos de 2, con los pasos que se usan de verdad).
 *
 * Se usa para separar bloques y para el relleno interno, de modo que la
 * distancia entre dos secciones no dependa de quién escribió cada pantalla.
 */
object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Margen lateral de todas las pantallas (zona segura de TV). */
    val screenMargin = 48.dp
}

/**
 * Trazos y adornos.
 *
 * En un televisor el foco tiene que verse a 3 m: el anillo es **siempre** de
 * 3 dp y blanco. Antes convivían 1,5, 2, 2,5 y 3 dp, y con 1,5 el elemento
 * enfocado era indistinguible del resto.
 */
object AppStroke {
    /**
     * Anillo de foco: **3 dp y blanco, siempre**.
     *
     * Regla del sistema de diseño, para que no vuelva a haber dos estilos:
     *  - **foco** → filo blanco de 3 dp (+ escala y sombra);
     *  - **seleccionado** → filo azul de 2 dp o relleno azul, nunca blanco;
     *  - **reposo** → filo blanco al 10 % de 1 dp.
     *
     * Si el relleno del elemento es claro (botón primario, pestaña activa), el
     * anillo de foco va oscuro en lugar de blanco: un filo blanco sobre blanco
     * no existe. Es la única excepción.
     */
    val focus = 3.dp

    /** Filo sutil de las superficies en reposo (sin foco). */
    val hairline = 1.dp

    /** Opacidad del filo en reposo, sobre blanco. */
    const val hairlineAlpha = 0.10f
}
