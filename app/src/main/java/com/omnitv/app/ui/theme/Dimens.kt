package com.omnitv.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sistema de forma, espaciado, foco y movimiento de Omni TV.
 * Un solo lugar para que dos tarjetas vecinas no acaben con radios distintos.
 */
object AppRadius {
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(22.dp)
    val xl = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(999.dp)
    fun capsule(height: Dp) = RoundedCornerShape(height / 2)
}

object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val screenMargin = 48.dp // zona segura 5% overscan
    val cardGap = 20.dp
    val sectionGap = 28.dp
}

object AppStroke {
    val focus = 3.dp
    val hairline = 1.dp
    const val hairlineAlpha = 0.10f
    val cardIdle = 1.dp
    val cardFocused = 3.dp
}

object AppElevation {
    val cardRest = 0.dp
    val cardFocused = 20.dp
    val hero = 0.dp
    val dialog = 24.dp
}

// ────────────────────────────────────────────────────────────────────────────
//  Movimiento — curvas y duraciones consistentes
// ────────────────────────────────────────────────────────────────────────────
object AppMotion {
    // Curvas
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f) // tvOS-like
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Entrance = CubicBezierEasing(0f, 0f, 0f, 1f)
    val Exit = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    // Duraciones
    const val Fast = 120
    const val Normal = 180
    const val Slow = 280
    const val Hero = 420

    fun focusSpring() = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
    fun scaleTween() = tween<Float>(Normal, easing = Emphasized)
    fun alphaTween() = tween<Float>(Fast, easing = Standard)
}
