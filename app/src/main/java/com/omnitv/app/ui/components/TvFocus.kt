@file:OptIn(ExperimentalFoundationApi::class)

package com.omnitv.app.ui.components

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import com.omnitv.app.data.ConfirmKeyUpGuard
import com.omnitv.app.data.DpadRepeatGate
import com.omnitv.app.ui.theme.AppSpacing
import kotlin.math.abs

/**
 * Permite que las pantallas hijas dirijan el foco hacia la pestaña activa
 * de [TopTabBar] cuando el usuario pulsa `Arriba` desde el borde superior.
 */
val LocalActiveTabFocus = staticCompositionLocalOf<FocusRequester?> { null }

/**
 * Permite que [TopTabBar] transfiera el foco al elemento principal de la
 * pestaña activa al pulsar `OK` o `Abajo` en la barra superior.
 */
class TabContentFocusTarget {
    var focus: FocusRequester? = null
}

val LocalTabContentFocus = staticCompositionLocalOf<TabContentFocusTarget?> { null }

/**
 * Vale `true` mientras el usuario navega lateralmente dentro de [TopTabBar]
 * sin haber pulsado `OK` ni `Abajo`, evitando que el contenido robe el foco
 * durante la vista previa de pestañas.
 */
val LocalSuppressContentAutoFocus = staticCompositionLocalOf { false }

/**
 * Solicita el foco para [requester] esperando a que el nodo esté colocado.
 * Reintenta varios fotogramas porque `LazyColumn` / `LazyVerticalGrid` o
 * `AnimatedVisibility` pueden tardar 2-3 fotogramas en adjuntar el hijo.
 */
@Composable
fun RequestTvFocus(
    requester: FocusRequester,
    key: Any? = Unit,
    enabled: Boolean = true,
    attempts: Int = 6
) {
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        if (inputModeManager.inputMode != InputMode.Keyboard) {
            inputModeManager.requestInputMode(InputMode.Keyboard)
        }
        for (attempt in 0 until attempts) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
}

/**
 * Limita la cadencia de pulsaciones repetidas al mantener pulsadas las flechas D-pad.
 */
fun Modifier.dpadRepeatThrottle(
    horizontalGateMs: Long = 75L,
    verticalGateMs: Long = 105L
): Modifier = composed {
    val gate = remember(horizontalGateMs, verticalGateMs) {
        DpadRepeatGate(horizontalGateMs, verticalGateMs)
    }
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val native = event.nativeKeyEvent
        val allowed = gate.shouldAllow(
            keyCode = native.keyCode,
            repeatCount = native.repeatCount,
            nowMs = SystemClock.uptimeMillis()
        )
        !allowed
    }
}

/**
 * Descarta el `ACTION_UP` huérfano que llega justo al abrir una capa/teclado/menú
 * para no pulsar involuntariamente el primer elemento que acaba de recibir foco.
 */
fun Modifier.consumeStrayConfirmKeyUp(guard: ConfirmKeyUpGuard): Modifier =
    onPreviewKeyEvent { event ->
        val code = event.nativeKeyEvent.keyCode
        when (event.type) {
            KeyEventType.KeyDown -> {
                guard.onKeyDown(code)
                false
            }
            KeyEventType.KeyUp -> guard.shouldConsumeKeyUp(code)
            else -> false
        }
    }

/**
 * Mantiene los elementos enfocados de una `LazyRow` dentro de una zona segura
 * horizontal con margen ([leadingPadding]) a ambos lados, evitando que las tarjetas
 * escaladas queden recortadas contra los bordes o provoquen saltos innecesarios.
 */
@Composable
fun ProvideHorizontalTvBringIntoView(
    leadingPadding: Dp = AppSpacing.screenMargin,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val spec = remember(density, leadingPadding) {
        val leadingPx = with(density) { leadingPadding.toPx() }
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                val childSize = abs(size)
                if (childSize >= containerSize) return 0f
                val margin = leadingPx.coerceAtMost(((containerSize - childSize) / 2f).coerceAtLeast(0f))
                val delta = when {
                    offset < margin -> offset - margin
                    offset + childSize > containerSize - margin ->
                        (offset + childSize) - (containerSize - margin)
                    else -> 0f
                }
                return if (abs(delta) < 1f) 0f else delta
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Hace que el contenedor vertical padre (por ejemplo, `LazyColumn`) traiga a vista
 * todo el bloque (título + botones o título + fila) cuando cualquiera de sus hijos
 * recibe foco, evitando que un `HeroBanner` se desplace hacia arriba al enfocar su botón.
 */
@Suppress("DEPRECATION")
fun Modifier.bringIntoViewIfChildrenAreFocused(
    paddingValues: PaddingValues = PaddingValues()
): Modifier = composed {
    val pxOffset = with(LocalDensity.current) {
        val y = (paddingValues.calculateBottomPadding() - paddingValues.calculateTopPadding()).toPx()
        Offset(0f, y)
    }
    var containerRect by remember { mutableStateOf(Rect.Zero) }
    val responder = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect = containerRect
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {}
        }
    }
    this
        .onSizeChanged { containerRect = Rect(pxOffset, it.toSize()) }
        .bringIntoViewResponder(responder)
}
