@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.TvKeypad
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.GlassFillStrong
import com.omnitv.app.ui.theme.OnLight
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary

/**
 * Teclado en pantalla para el buscador, pensado para un mando de TV.
 *
 * Por qué no el teclado del sistema: en Android TV, un `BasicTextField` enfocado
 * abre el IME *leanback* solo si el receptor lo soporta, y con Compose el
 * resultado cambia según el fabricante. En muchos televisores el campo se queda
 * con el foco y **no aparece nada**, así que el usuario acaba pulsando «Atrás» a
 * ciegas. Este teclado es nuestro: siempre se ve, siempre responde al D-pad y no
 * depende de ningún IME del sistema.
 *
 * Cómo se usa con el mando: cuatro flechas para moverse, **OK** para escribir la
 * tecla enfocada, y «LISTO» para cerrarlo. No se capturan teclas de forma
 * global: el foco está dentro del teclado mientras está abierto, así que las
 * flechas mueven de tecla en tecla y un teclado físico (o el del móvil, por
 * Bluetooth) sigue escribiendo en el campo sin que este componente estorbe.
 */
@Composable
fun TvKeyboard(
    onKey: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    firstKeyFocus: FocusRequester = remember { FocusRequester() }
) {
    // Panel de 54 dp por tecla con 8 dp de hueco: por encima del mínimo de 48 dp
    // para un objetivo de mando, y lo bastante compacto (342 dp de alto) para
    // superponerse a la mitad inferior de un lienzo de 540 dp sin tapar el campo
    // de búsqueda, que se queda arriba.
    val rows = TvKeypad.rows

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppRadius.lg)
            .background(SurfaceElevated)
            .border(2.dp, Color.White.copy(alpha = 0.12f), AppRadius.lg)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Todas las filas centradas con el mismo ancho de tecla: las de 10, 9
        // y 7 letras quedan en columna (como en un teclado de verdad), así el
        // D-pad baja en vertical y no en diagonal.
        rows.forEachIndexed { rowIndex, letters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dos muelles a los lados centran la fila sin romper el
                // espaciado entre teclas.
                Spacer(Modifier.weight(1f))
                letters.forEachIndexed { colIndex, char ->
                    KeyButton(
                        label = char.toString(),
                        onClick = { onKey(char.toString()) },
                        modifier = if (rowIndex == 0 && colIndex == 0) {
                            Modifier.focusRequester(firstKeyFocus)
                        } else {
                            Modifier
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }

        // Fila de acciones: espacio, borrar y listo. Se queda pegada a la
        // izquierda para que no se mueva bajo el dedo al reordenar el foco.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionKey(
                label = TvKeypad.SPACE,
                onClick = { onKey(TvKeypad.SPACE) },
                modifier = Modifier.weight(1f)
            )
            ActionKey(
                label = "BORRAR",
                iconRes = R.drawable.ic_backspace,
                onClick = { onKey(TvKeypad.BACKSPACE) }
            )
            ActionKey(
                label = TvKeypad.DONE,
                onClick = onDone,
                primary = true
            )
        }
    }
}

/** Tecla cuadrada de un carácter. */
@Composable
private fun KeyButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(54.dp)
            .clip(AppRadius.sm)
            // GlassFillStrong y no GlassFill: sobre el panel, el relleno tenue
            // casi no se distingue y las teclas se leían como texto suelto.
            .background(if (focused) Color.White else GlassFillStrong)
            // El foco ya cambia el relleno a blanco; el anillo va oscuro para
            // que se siga viendo (regla del sistema: relleno claro → aro oscuro).
            .then(if (focused) Modifier.border(3.dp, Background, AppRadius.sm) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = AppType.body,
            color = if (focused) OnLight else TextPrimary
        )
    }
}

/** Tecla ancha de acción (espacio, borrar, listo). */
@Composable
private fun ActionKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    primary: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        primary -> Accent
        else -> GlassFillStrong
    }
    val content = when {
        focused -> OnLight
        else -> Color.White
    }

    Row(
        modifier = modifier
            .height(54.dp)
            .clip(AppRadius.sm)
            .background(background)
            .then(if (focused) Modifier.border(3.dp, Background, AppRadius.sm) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(content)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = AppType.button, color = content)
    }
}
