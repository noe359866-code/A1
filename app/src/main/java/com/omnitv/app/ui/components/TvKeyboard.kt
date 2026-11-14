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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
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
 * Teclado propio para Android TV. No depende del IME del receptor.
 *
 * [compact] se usa en un panel lateral: el campo y los resultados siguen
 * visibles a su izquierda. Una pulsación de flecha mueve UNA tecla y nunca
 * abandona el panel; OK activa la tecla enfocada y Atrás lo cierra la pantalla.
 * Los objetivos de 42 dp son para mando, no para una pantalla táctil.
 */
@Composable
fun TvKeyboard(
    onKey: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    firstKeyFocus: FocusRequester = remember { FocusRequester() },
    compact: Boolean = false
) {
    val keySize = if (compact) 42.dp else 54.dp
    val gap = if (compact) 4.dp else 8.dp
    val padding = if (compact) 12.dp else 18.dp
    val labels = TvKeypad.rows.map { row -> row.map { it.toString() } } +
        listOf(TvKeypad.actions)
    val focusTargets = remember(firstKeyFocus) {
        labels.mapIndexed { rowIndex, row ->
            row.mapIndexed { colIndex, _ ->
                if (rowIndex == 0 && colIndex == 0) firstKeyFocus else FocusRequester()
            }
        }
    }
    var current by remember { mutableStateOf(TvKeypad.Position(0, 0)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppRadius.lg)
            .background(SurfaceElevated)
            .border(2.dp, Color.White.copy(alpha = 0.12f), AppRadius.lg)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionUp -> TvKeypad.Direction.UP
                    Key.DirectionDown -> TvKeypad.Direction.DOWN
                    Key.DirectionLeft -> TvKeypad.Direction.LEFT
                    Key.DirectionRight -> TvKeypad.Direction.RIGHT
                    else -> return@onPreviewKeyEvent false
                }
                current = TvKeypad.move(current, direction)
                focusTargets[current.row][current.column].requestFocus()
                true
            }
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        TvKeypad.rows.forEachIndexed { rowIndex, letters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                letters.forEachIndexed { colIndex, char ->
                    KeyButton(
                        label = char.toString(),
                        size = keySize,
                        onClick = {
                            current = TvKeypad.Position(rowIndex, colIndex)
                            onKey(char.toString())
                        },
                        modifier = Modifier.focusRequester(focusTargets[rowIndex][colIndex])
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            TvKeypad.actions.forEachIndexed { colIndex, action ->
                val rowIndex = TvKeypad.rows.size
                ActionKey(
                    label = if (action == TvKeypad.BACKSPACE) "BORRAR" else action,
                    iconRes = if (action == TvKeypad.BACKSPACE) R.drawable.ic_backspace else null,
                    primary = action == TvKeypad.DONE,
                    height = keySize,
                    compact = compact,
                    onClick = {
                        current = TvKeypad.Position(rowIndex, colIndex)
                        if (action == TvKeypad.DONE) onDone() else onKey(action)
                    },
                    modifier = Modifier
                        .then(if (action == TvKeypad.SPACE) Modifier.weight(1f) else Modifier)
                        .focusRequester(focusTargets[rowIndex][colIndex])
                )
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(AppRadius.sm)
            .background(if (focused) Color.White else GlassFillStrong)
            .then(if (focused) Modifier.border(3.dp, Background, AppRadius.sm) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = AppType.body, color = if (focused) OnLight else TextPrimary)
    }
}

@Composable
private fun ActionKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    primary: Boolean = false,
    height: Dp = 54.dp,
    compact: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        primary -> Accent
        else -> GlassFillStrong
    }
    val content = if (focused) OnLight else Color.White
    Row(
        modifier = modifier
            .height(height)
            .clip(AppRadius.sm)
            .background(background)
            .then(if (focused) Modifier.border(3.dp, Background, AppRadius.sm) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = if (compact) 10.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(content)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = if (compact) AppType.metadata else AppType.button, color = content, maxLines = 1)
    }
}
