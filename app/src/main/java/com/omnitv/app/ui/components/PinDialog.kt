package com.omnitv.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.ParentalPin
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
 * que buscar un botón «Aceptar».
 *
 * @param confirmEntry pedir el PIN dos veces (al crearlo).
 * @param onSubmit devuelve `true` si el PIN es aceptado; entonces la pantalla
 *   que llama cierra la capa. `false` cuenta como intento fallido.
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
    var failures by rememberSaveable { mutableStateOf(0) }
    var lockedUntil by rememberSaveable { mutableStateOf(0L) }
    val firstKey = remember { FocusRequester() }
    RequestTvFocus(firstKey)

    BackHandler(onBack = onDismiss)

    fun locked(): Boolean = lockedUntil > System.currentTimeMillis()

    fun submit(pin: String) {
        val now = System.currentTimeMillis()
        if (lockedUntil > 0L && now >= lockedUntil) {
            // El bloqueo ya cumplió su tiempo: se vuelve a permitir probar.
            lockedUntil = 0L
            failures = 0
            error = ""
        }
        if (locked()) {
            error = "Demasiados intentos. Espera unos segundos."
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
        if (onSubmit(pin)) return // la pantalla cierra la capa
        failures++
        if (failures >= 5) lockedUntil = now + 30_000L
        entry = ""
        error = "PIN incorrecto. Inténtalo de nuevo."
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
            .background(Background.copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
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
                    row.forEachIndexed { index, key ->
                        PillButton(
                            text = key,
                            primary = key.length == 1,
                            modifier = Modifier
                                .width(120.dp)
                                .then(if (rowIndex == 0 && index == 0) Modifier.focusRequester(firstKey) else Modifier),
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
            )
        }
    }
}
