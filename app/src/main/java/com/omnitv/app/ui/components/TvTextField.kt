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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.omnitv.app.data.TvKeyCodes
import com.omnitv.app.ui.theme.Accent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextMuted
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/**
 * Campo de texto pensado para un mando de TV.
 *
 * El problema de abrir un teclado solo con ganar el foco es que al navegar con
 * las flechas (por ejemplo, subiendo desde los resultados o desde el selector
 * de avatar) el usuario queda atrapado dentro del teclado. Aquí:
 *
 *  - Ganar foco solo resalta el campo con su anillo blanco de 3 dp; nunca abre
 *    el teclado por sorpresa.
 *  - Pulsar OK / Enter abre el teclado propio mediante [onActivate] (al soltar
 *    la tecla para no filtrar pulsaciones al teclado) o muestra el IME si
 *    [imeOnCenter] es `true`.
 *  - Si hay un teclado físico/Bluetooth conectado, también se puede escribir
 *    directamente mientras el campo está enfocado.
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
    onActivate: (() -> Unit)? = null,
    actionHint: String? = null,
    imeOnCenter: Boolean = true,
    focusable: Boolean = true,
    fieldFocusProperties: (androidx.compose.ui.focus.FocusProperties.() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    var sawConfirmDown by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = AppRadius.md

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(if (focused) SurfaceElevated else GlassFill)
            .then(
                if (focused) Modifier.border(AppStroke.focus, Color.White, shape)
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
                    .focusProperties {
                        this.canFocus = focusable
                        fieldFocusProperties?.invoke(this)
                    }
                    .onFocusChanged {
                        val nowFocused = it.isFocused
                        if (!nowFocused) sawConfirmDown = false
                        focused = nowFocused
                        onFocusChange(nowFocused)
                    }
                    .onPreviewKeyEvent { event ->
                        val code = event.nativeKeyEvent.keyCode
                        if (!TvKeyCodes.isConfirm(code)) return@onPreviewKeyEvent false
                        if (onActivate != null) {
                            when (event.type) {
                                KeyEventType.KeyDown -> {
                                    sawConfirmDown = true
                                    true
                                }
                                KeyEventType.KeyUp -> {
                                    if (sawConfirmDown) {
                                        sawConfirmDown = false
                                        keyboard?.hide()
                                        onActivate()
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else if (imeOnCenter && event.type == KeyEventType.KeyDown) {
                            keyboard?.show()
                            true
                        } else {
                            false
                        }
                    }
            )
        }

        if (focused && actionHint != null && onActivate != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                actionHint,
                style = AppType.chip,
                color = TextMuted
            )
        }
    }
}
