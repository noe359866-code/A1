package com.omnitv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.omnitv.app.R
import com.omnitv.app.data.ConfirmKeyUpGuard
import com.omnitv.app.data.TvKeyCodes
import com.omnitv.app.data.TvKeypad
import com.omnitv.app.ui.theme.BadgeAccent
import com.omnitv.app.ui.theme.AppRadius
import com.omnitv.app.ui.theme.AppStroke
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.FocusScale
import com.omnitv.app.ui.theme.GlassFill
import com.omnitv.app.ui.theme.GlassFillStrong
import com.omnitv.app.ui.theme.OnLight
import com.omnitv.app.ui.theme.SurfaceCard
import com.omnitv.app.ui.theme.SurfaceElevated
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.appTween

/**
 * Teclado propio para Android TV. No depende del IME del receptor.
 *
 * - Modo panel lateral (`embedded = false`, usado en edición de perfiles): mantiene
 *   el foco encerrado en los cuatro bordes hasta pulsar «LISTO» o Atrás.
 * - Modo buscador fijo a la izquierda (`embedded = true`): estética de rejilla
 *   compacta y oscura con borde blanco al enfocar; permite salir hacia arriba,
 *   abajo (sugerencias) o derecha (rejilla de resultados) mediante los callbacks
 *   de borde, y sustituye «LISTO» por «LIMPIAR».
 */
@Composable
fun TvKeyboard(
    onKey: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    firstKeyFocus: FocusRequester = remember { FocusRequester() },
    bottomFocusRequester: FocusRequester? = null,
    compact: Boolean = false,
    embedded: Boolean = false,
    onClear: (() -> Unit)? = null,
    onExitUp: (() -> Boolean)? = null,
    onExitRight: (() -> Boolean)? = null,
    onExitDown: (() -> Boolean)? = null
) {
    val keyWidth = when {
        embedded -> 31.dp
        compact -> 42.dp
        else -> 54.dp
    }
    val keyHeight = when {
        embedded -> 34.dp
        compact -> 42.dp
        else -> 54.dp
    }
    val gap = when {
        embedded -> 3.dp
        compact -> 4.dp
        else -> 8.dp
    }
    val padding = when {
        embedded -> 10.dp
        compact -> 12.dp
        else -> 18.dp
    }

    val initialRow = if (embedded) 1 else 0
    val initialCol = 0

    val labels = TvKeypad.rows.map { row -> row.map { it.toString() } } +
        listOf(TvKeypad.actions)
    val focusTargets = remember(firstKeyFocus, bottomFocusRequester, embedded) {
        labels.mapIndexed { rowIndex, row ->
            row.mapIndexed { colIndex, _ ->
                when {
                    rowIndex == initialRow && colIndex == initialCol -> firstKeyFocus
                    bottomFocusRequester != null && rowIndex == TvKeypad.rows.size && colIndex == 0 -> bottomFocusRequester
                    else -> FocusRequester()
                }
            }
        }
    }
    var current by remember(embedded) { mutableStateOf(TvKeypad.Position(initialRow, initialCol)) }
    val keyUpGuard = remember { ConfirmKeyUpGuard(initialSuppressUntilDown = true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .clip(if (embedded) AppRadius.md else AppRadius.lg)
            .background(if (embedded) SurfaceCard else SurfaceElevated)
            .border(
                width = if (embedded) 1.dp else 2.dp,
                color = Color.White.copy(alpha = if (embedded) 0.10f else 0.12f),
                shape = if (embedded) AppRadius.md else AppRadius.lg
            )
            .consumeStrayConfirmKeyUp(keyUpGuard)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                if (TvKeyCodes.isBackspace(code)) {
                    onKey(TvKeypad.BACKSPACE)
                    return@onPreviewKeyEvent true
                }
                val direction = when {
                    TvKeyCodes.isUp(code) -> TvKeypad.Direction.UP
                    TvKeyCodes.isDown(code) -> TvKeypad.Direction.DOWN
                    TvKeyCodes.isLeft(code) -> TvKeypad.Direction.LEFT
                    TvKeyCodes.isRight(code) -> TvKeypad.Direction.RIGHT
                    else -> return@onPreviewKeyEvent false
                }
                val lastRow = TvKeypad.rows.size
                val rowLength = if (current.row < TvKeypad.rows.size) {
                    TvKeypad.rows[current.row].length
                } else {
                    TvKeypad.actions.size
                }
                if (embedded) {
                    when {
                        direction == TvKeypad.Direction.UP && current.row == 0 -> {
                            if (onExitUp?.invoke() == true) return@onPreviewKeyEvent true
                        }
                        direction == TvKeypad.Direction.DOWN && current.row == lastRow -> {
                            if (onExitDown?.invoke() == true) return@onPreviewKeyEvent true
                        }
                        direction == TvKeypad.Direction.RIGHT && current.column >= rowLength - 1 -> {
                            if (onExitRight?.invoke() == true) return@onPreviewKeyEvent true
                        }
                    }
                }
                current = TvKeypad.move(current, direction)
                runCatching { focusTargets[current.row][current.column].requestFocus() }
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
                        width = keyWidth,
                        height = keyHeight,
                        embedded = embedded,
                        onFocus = { current = TvKeypad.Position(rowIndex, colIndex) },
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
                val isClearAction = action == TvKeypad.DONE && onClear != null
                val label = when {
                    action == TvKeypad.BACKSPACE -> if (embedded) "" else "BORRAR"
                    isClearAction -> "LIMPIAR"
                    else -> action
                }
                ActionKey(
                    label = label,
                    iconRes = if (action == TvKeypad.BACKSPACE) R.drawable.ic_backspace else null,
                    primary = action == TvKeypad.DONE && !embedded,
                    height = keyHeight,
                    compact = compact || embedded,
                    embedded = embedded,
                    onFocus = { current = TvKeypad.Position(rowIndex, colIndex) },
                    onClick = {
                        current = TvKeypad.Position(rowIndex, colIndex)
                        when {
                            isClearAction -> onClear?.invoke()
                            action == TvKeypad.DONE -> onDone()
                            else -> onKey(action)
                        }
                    },
                    modifier = Modifier
                        .then(
                            when {
                                embedded && action == TvKeypad.SPACE -> Modifier.weight(1.5f)
                                embedded -> Modifier.weight(1f)
                                action == TvKeypad.SPACE -> Modifier.weight(1f)
                                else -> Modifier
                            }
                        )
                        .focusRequester(focusTargets[rowIndex][colIndex])
                )
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    width: Dp,
    height: Dp,
    embedded: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> FocusScale.key * FocusScale.pressFactor
            focused -> FocusScale.key
            else -> 1f
        },
        appTween(110), label = "keyScale"
    )
    val radius = if (embedded) AppRadius.xs else AppRadius.sm
    val bg = when {
        focused && embedded -> SurfaceElevated
        focused -> Color.White
        embedded -> GlassFill
        else -> GlassFillStrong
    }
    val border = when {
        focused && embedded -> Modifier.border(AppStroke.focus, Color.White, radius)
        focused -> Modifier.border(AppStroke.focus, Background, radius)
        embedded -> Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), radius)
        else -> Modifier
    }
    val textColor = when {
        focused && !embedded -> OnLight
        else -> TextPrimary
    }

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .width(width)
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(radius)
            .background(bg)
            .then(border)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = if (embedded) AppType.cardTitle else AppType.body,
            color = textColor
        )
    }
}

@Composable
private fun ActionKey(
    label: String,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    primary: Boolean = false,
    height: Dp = 54.dp,
    compact: Boolean = false,
    embedded: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> FocusScale.chip * FocusScale.pressFactor
            focused -> FocusScale.chip
            else -> 1f
        },
        appTween(110), label = "actionKeyScale"
    )
    val radius = if (embedded) AppRadius.xs else AppRadius.sm
    val background = when {
        focused && embedded -> SurfaceElevated
        focused -> Color.White
        primary -> BadgeAccent // fill con texto blanco: 4,78:1 (AA)
        embedded -> GlassFill
        else -> GlassFillStrong
    }
    val border = when {
        focused && embedded -> Modifier.border(AppStroke.focus, Color.White, radius)
        focused -> Modifier.border(AppStroke.focus, Background, radius)
        embedded -> Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), radius)
        else -> Modifier
    }
    val content = if (focused && !embedded) OnLight else Color.White

    Row(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(radius)
            .background(background)
            .then(border)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = if (embedded) 6.dp else if (compact) 10.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = if (label.isEmpty()) "Borrar" else null,
                modifier = Modifier.size(if (embedded) 18.dp else 20.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(content)
            )
            if (label.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
            }
        }
        if (label.isNotEmpty()) {
            Text(
                label,
                style = if (embedded) AppType.chip else if (compact) AppType.metadata else AppType.button,
                color = content,
                maxLines = 1
            )
        }
    }
}
