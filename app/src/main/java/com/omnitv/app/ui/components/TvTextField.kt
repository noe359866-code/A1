@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/**
 * Lleva el foco a [requester] en cuanto la pantalla ha hecho su primer trazado.
 *
 * Pedirlo directamente al componer falla a menudo —el nodo todavía no está
 * colocado y `requestFocus` lanza `IllegalStateException`—, así que se espera un
 * fotograma. Se usa en las pantallas para que el mando entre con el foco donde
 * el usuario espera (el campo de búsqueda, «Reproducir»…) y no en el primer
 * botón del orden de composición.
 */
@Composable
fun RequestTvFocus(requester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

/**
 * Campo de texto pensado para un mando de TV.
 *
 * El problema de un `BasicTextField` a secas en Android TV es que el mando no
 * tiene teclado: si el usuario enfoca el campo y pulsa OK no pasa nada visible,
 * y se queda atascado sin poder escribir. Aquí:
 *
 *  - El IME del sistema **no entra aquí**: en Android TV no aparece de forma
 *    fiable con Compose (según fabricante, el campo se queda con el foco y no
 *    se abre nada). Las pantallas que necesitan escribir teclado —hoy el
 *    buscador— traen el suyo (`TvKeyboard`) y lo abren con [onFocusChange];
 *    ver [imeOnCenter] para desactivar también el intento por OK.
 *  - El foco se pinta con anillo blanco de 3 dp y fondo elevado, porque en un
 *    televisor el color de fondo solo no se distingue.
 *  - Si por [imeOnCenter] no se pide el IME, OK / Enter sobre el campo no hace
 *    nada especial: escriben las teclas del teclado propio.
 *
 * Se expone [focusRequester] para que una pantalla pueda llevar el foco al
 * campo al entrar (por ejemplo, la pantalla de búsqueda), y [onFocusChange]
 * para que la pantalla abra su propio teclado cuando el campo gana el foco.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIconRes: Int? = null,
    imeAction: ImeAction = ImeAction.Done,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onImeAction: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    /**
     * Si es `false`, OK / Enter sobre el campo **no** piden el teclado del
     * sistema. El buscador lo desactiva porque tiene teclado propio: si se
     * dejara, en un televisor que sí tenga IME saldrían dos teclados a la vez.
     */
    imeOnCenter: Boolean = true,
    /**
     * Si es `false`, el campo no acepta el foco del D-pad. El buscador lo
     * desactiva mientras su teclado está abierto: si no, las flechas podían
     * volver al campo y dejar el teclado sin foco, escribiendo a ciegas.
     */
    focusable: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = AppRadius.md

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(if (focused) SurfaceElevated else GlassFill)
            .then(
                if (focused) Modifier.border(3.dp, Color.White, shape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIconRes != null) {
            Image(
                painter = painterResource(leadingIconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    if (focused) TextPrimary else TextSecondary
                )
            )
            Spacer(Modifier.width(14.dp))
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = AppType.body, color = TextSecondary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AppType.body.copy(color = TextPrimary),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onSearch = { keyboard?.hide(); onImeAction() },
                    onGo = { keyboard?.hide(); onImeAction() },
                    onDone = { keyboard?.hide(); onImeAction() },
                    onSend = { keyboard?.hide(); onImeAction() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusProperties { this.canFocus = focusable }
                    .onPreviewKeyEvent { event ->
                        // OK / Enter del mando: pedir el IME del sistema solo si
                        // la pantalla lo quiere (`imeOnCenter`). Con teclado
                        // propio se ignora, para no acabar con dos teclados.
                        if (imeOnCenter &&
                            event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionCenter ||
                                event.key == Key.Enter ||
                                event.key == Key.NumPadEnter)
                        ) {
                            keyboard?.show()
                            true
                        } else {
                            false
                        }
                    }
                    .onFocusChanged {
                        focused = it.isFocused
                        onFocusChange(it.isFocused)
                    }
            )
        }
    }
}
