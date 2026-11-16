package com.omnitv.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Curva de transición única de la app (la del sistema de VortX:
 * cubic-bezier(.2,.8,.2,1)) y soporte de **movimiento reducido**, inspirados
 * en VortX (`ui/theme/Motion.kt` de VortXTV/VortX).
 *
 * En Android TV el equivalente al «Reduce Motion» de accesibilidad es
 * `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (Ajustes del desarrollador →
 * Escala de animación de transición → Animación desactivada). Con ese valor a
 * 0, [appTween] devuelve un [snap] en lugar de animar: los estados de foco
 * cambian al instante en vez de con retardo/ease — sigue siendo usable con el
 * mando y no marean a quien tiene sensibilidad al movimiento.
 *
 * Los giros infinitos (p. ej. `LoadingSpinner`) se dejan como están: son
 * indicadores de progreso, no transiciones.
 */
object AppMotion {
    val easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** Especificación de animación consciente del movimiento reducido. */
    fun <T> spec(durationMs: Int, reducedMotion: Boolean): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else tween(durationMs, easing = easing)
}

/** `true` si el dispositivo tiene las animaciones desactivadas a nivel de sistema. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context: Context = LocalContext.current
    return remember(context) {
        // getFloat puede lanzar en dispositivos exóticos; si no podemos saberlo, animamos.
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * `FiniteAnimationSpec<T>` para los [animateFloatAsState]/[animateDpAsState]/[Crossfade]
 * de la app: `snap()` si el sistema tiene el movimiento reducido; si no, `tween` con la
 * curva [AppMotion.easing]. Sustituye a los `tween(...)` sueltos.
 */
@Composable
fun <T> appTween(durationMs: Int): FiniteAnimationSpec<T> =
    AppMotion.spec(durationMs, rememberReducedMotion())
