@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.ConfirmKeyUpGuard
import com.omnitv.app.data.PinAttemptGuard
import com.omnitv.app.data.ParentalPin
import com.omnitv.app.data.TvKeyCodes
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.SurfaceCard
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/**
 * Teclado numérico para mando, usado para crear/verificar el PIN parental.
 *
 * Se dibuja como una capa encima de la pantalla (no como `Dialog` de ventana
 * nueva): en Android TV el foco D-pad se comporta mejor dentro del mismo
 * árbol de composición. Con 4 cifras se valida automáticamente, sin tener
 * que buscar un botón «Aceptar». Además encierra el foco dentro del teclado
 * 4×3 para que el D-pad nunca escape a elementos detrás de la capa.
 */
@Composable
fun PinDialog(
    title: String,
    subtitle: String,
    onSubmit: (String) -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEntry: Boolean = false
) {
    var entry by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val keyGrid = remember { List(4) { List(3) { FocusRequester() } } }
    val keyUpGuard = remember { ConfirmKeyUpGuard(initialSuppressUntilDown = true) }
    RequestTvFocus(keyGrid[0][0])

    BackHandler(onBack = onDismiss)

    /** El bloqueo vive en [PinAttemptGuard] para que descartar el diálogo no lo reinicie. */
    fun locked(): Boolean {
        if (!PinAttemptGuard.isLocked()) return false
        error = "Demasiados intentos. Espera ${PinAttemptGuard.lockedSecondsRemaining()} s."
        return true
    }

    fun submit(pin: String) {
        if (locked()) {
            entry = ""
            return
        }
        if (confirmEntry && !confirming) {
            first = pin
            confirming = true
            entry = ""
            error = ""
            return
        }
        if (confirmEntry && pin != first) {
            first = ""
            confirming = false
            entry = ""
            error = "Los dos PIN no coinciden. Vuelve a empezar."
            return
        }
        if (onSubmit(pin)) {
            PinAttemptGuard.recordSuccess()
            return
        }
        PinAttemptGuard.recordFailure()
        entry = ""
        error = if (PinAttemptGuard.isLocked()) {
            "Demasiados intentos. Espera ${PinAttemptGuard.lockedSecondsRemaining()} s."
        } else {
            "PIN incorrecto. Inténtalo de nuevo."
        }
    }

    fun pressDigit(digit: Char) {
        if (entry.length >= ParentalPin.LENGTH || locked()) return
        val next = entry + digit
        entry = next
        if (next.length == ParentalPin.LENGTH) submit(next)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Background.copy(alpha = 0.97f))
            .consumeStrayConfirmKeyUp(keyUpGuard)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                val digit = TvKeyCodes.digitOrNull(code)
                when {
                    digit != null -> {
                        pressDigit(digit)
                        true
                    }
                    TvKeyCodes.isBackspace(code) -> {
                        if (entry.isNotEmpty()) entry = entry.dropLast(1)
                        true
                    }
                    TvKeyCodes.isBack(code) -> {
                        onDismiss()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .focusGroup()
                .clip(AppRadius.lg)
                .background(SurfaceCard)
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = AppType.sectionTitle, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                if (confirmEntry && confirming) "Repite el PIN para confirmarlo" else subtitle,
                style = AppType.metadata,
                color = TextSecondary
            )
            Spacer(Modifier.height(24.dp))

            // Indicador de cifras introducidas (nunca muestra el PIN en claro).
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(ParentalPin.LENGTH) { index ->
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(AppRadius.pill)
                            .background(if (index < entry.length) Accent else Background)
                            .border(2.dp, if (index < entry.length) Accent else TextSecondary, AppRadius.pill)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                error.ifEmpty { " " },
                style = AppType.metadata,
                color = if (error.isEmpty()) TextSecondary else com.omnitv.app.ui.theme.Danger
            )
            Spacer(Modifier.height(16.dp))

            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("Borrar", "0", "Cancelar")
            )
            keys.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { colIndex, key ->
                        PillButton(
                            text = key,
                            primary = key.length == 1,
                            modifier = Modifier
                                .width(120.dp)
                                .focusRequester(keyGrid[rowIndex][colIndex])
                                .focusProperties {
                                    up = if (rowIndex > 0) keyGrid[rowIndex - 1][colIndex] else FocusRequester.Cancel
                                    down = if (rowIndex < 3) keyGrid[rowIndex + 1][colIndex] else FocusRequester.Cancel
                                    left = if (colIndex > 0) keyGrid[rowIndex][colIndex - 1] else FocusRequester.Cancel
                                    right = if (colIndex < 2) keyGrid[rowIndex][colIndex + 1] else FocusRequester.Cancel
                                },
                            onClick = {
                                when (key) {
                                    "Borrar" -> if (entry.isNotEmpty()) entry = entry.dropLast(1)
                                    "Cancelar" -> onDismiss()
                                    else -> pressDigit(key.single())
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
