@file:OptIn(ExperimentalComposeUiApi::class)

package com.omnitv.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.omnitv.app.ui.theme.*

@Composable
fun RequestTvFocus(requester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

/**
 * Campo de texto para Android TV — usa exclusivamente el teclado nativo del sistema.
 *
 * Comportamiento:
 * - Enfocado: anillo blanco 3dp + fondo elevado, legible a 3m.
 * - OK / Enter del mando: pide el IME nativo (Gboard / Leanback).
 * - IME “Buscar”/“Listo”: cierra el teclado y quita el foco (vuelve a la grilla).
 * - Sin teclado propio superpuesto: menos capas, foco del D-pad siempre predecible.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIconRes: Int? = null,
    imeAction: ImeAction = ImeAction.Search,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onImeAction: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    showClearAction: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val shape = AppRadius.md

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(shape)
            .background(if (focused) SurfaceElevated else GlassFill)
            .then(
                if (focused) Modifier.border(3.dp, Color.White, shape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIconRes != null) {
            Image(
                painter = painterResource(leadingIconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    if (focused) TextPrimary else TextSecondary
                )
            )
            Spacer(Modifier.width(12.dp))
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = AppType.body, color = TextTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AppType.body.copy(color = TextPrimary),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        focusManager.clearFocus(force = true)
                        onImeAction()
                    },
                    onDone = {
                        keyboard?.hide()
                        focusManager.clearFocus(force = true)
                        onImeAction()
                    },
                    onGo = {
                        keyboard?.hide()
                        focusManager.clearFocus(force = true)
                        onImeAction()
                    },
                    onSend = {
                        keyboard?.hide()
                        focusManager.clearFocus(force = true)
                        onImeAction()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            // En TV el IME no siempre se abre solo al enfocar;
                            // forzar show en OK hace que Gboard aparezca al primer toque.
                            keyboard?.show()
                            false // no consumir: dejar que el sistema también abra el IME
                        } else false
                    }
                    .onFocusChanged {
                        focused = it.isFocused
                        onFocusChange(it.isFocused)
                        if (it.isFocused) keyboard?.show()
                    }
            )
        }

        // Acción limpiar — visible solo con texto, útil con mando (1 click borra)
        if (showClearAction && value.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (focused) 0.14f else 0.10f))
                    .clickable(
                        remember { MutableInteractionSource() },
                        indication = null
                    ) { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                Text("×", style = AppType.bodyStrong.copy(color = TextSecondary))
            }
        }

        // Indicador IME nativo — sutil, confirma que hay teclado del sistema
        if (focused) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(AppRadius.pill)
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("OK ↵", style = AppType.caption, color = TextSecondary)
            }
        }
    }
}
