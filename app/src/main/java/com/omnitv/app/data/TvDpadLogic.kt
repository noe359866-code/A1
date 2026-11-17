package com.omnitv.app.data

/**
 * Lógica pura de manejo D-pad y mando a distancia para Android TV.
 *
 * Sin dependencias de Android ni Compose para poder probarla con tests JVM:
 *  - mapeo completo de códigos de tecla de mandos Android TV (`DPAD_*`,
 *    `SYSTEM_NAVIGATION_*`, `ENTER`, `NUMPAD_ENTER`, `MEDIA_*`, dígitos),
 *  - limitador de frecuencia de repetición al mantener pulsada una flecha,
 *  - aceleración progresiva de avance/retroceso en el reproductor,
 *  - protección contra `ACTION_UP` huérfanos al abrir capas o teclados.
 */
object TvKeyCodes {
    const val KEYCODE_BACK = 4
    const val KEYCODE_0 = 7
    const val KEYCODE_9 = 16
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_SPACE = 62
    const val KEYCODE_ENTER = 66
    const val KEYCODE_DEL = 67
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_STOP = 86
    const val KEYCODE_MEDIA_NEXT = 87
    const val KEYCODE_MEDIA_PREVIOUS = 88
    const val KEYCODE_MEDIA_REWIND = 89
    const val KEYCODE_MEDIA_FAST_FORWARD = 90
    const val KEYCODE_ESCAPE = 111
    const val KEYCODE_FORWARD_DEL = 112
    const val KEYCODE_MEDIA_PLAY = 126
    const val KEYCODE_MEDIA_PAUSE = 127
    const val KEYCODE_NUMPAD_0 = 144
    const val KEYCODE_NUMPAD_9 = 153
    const val KEYCODE_NUMPAD_ENTER = 160
    const val KEYCODE_INFO = 165
    /** Tecla de color azul de los mandos de TV: atajo de búsqueda en Ajustes. */
    const val KEYCODE_PROG_BLUE = 186
    const val KEYCODE_CAPTIONS = 175
    const val KEYCODE_MEDIA_AUDIO_TRACK = 222
    const val KEYCODE_MEDIA_STEP_FORWARD = 272
    const val KEYCODE_MEDIA_STEP_BACKWARD = 273
    const val KEYCODE_MEDIA_SKIP_FORWARD = 274
    const val KEYCODE_MEDIA_SKIP_BACKWARD = 275
    const val KEYCODE_SYSTEM_NAVIGATION_UP = 280
    const val KEYCODE_SYSTEM_NAVIGATION_DOWN = 281
    const val KEYCODE_SYSTEM_NAVIGATION_LEFT = 282
    const val KEYCODE_SYSTEM_NAVIGATION_RIGHT = 283

    fun isUp(keyCode: Int): Boolean =
        keyCode == KEYCODE_DPAD_UP || keyCode == KEYCODE_SYSTEM_NAVIGATION_UP

    fun isDown(keyCode: Int): Boolean =
        keyCode == KEYCODE_DPAD_DOWN || keyCode == KEYCODE_SYSTEM_NAVIGATION_DOWN

    fun isLeft(keyCode: Int): Boolean =
        keyCode == KEYCODE_DPAD_LEFT || keyCode == KEYCODE_SYSTEM_NAVIGATION_LEFT

    fun isRight(keyCode: Int): Boolean =
        keyCode == KEYCODE_DPAD_RIGHT || keyCode == KEYCODE_SYSTEM_NAVIGATION_RIGHT

    fun isDirectional(keyCode: Int): Boolean =
        isUp(keyCode) || isDown(keyCode) || isLeft(keyCode) || isRight(keyCode)

    fun isHorizontal(keyCode: Int): Boolean = isLeft(keyCode) || isRight(keyCode)

    fun isConfirm(keyCode: Int): Boolean =
        keyCode == KEYCODE_DPAD_CENTER ||
            keyCode == KEYCODE_ENTER ||
            keyCode == KEYCODE_NUMPAD_ENTER

    /**
     * Tecla de color azul: atajo del buscador de Ajustes.
     *
     * Se usa una tecla de color, y no OK ni una letra, porque los mandos de TV
     * la traen dedicada y la app no la usaba: así el atajo no le quita el OK a
     * ninguna fila ni depende de que haya teclado.
     */
    fun isSearchShortcut(keyCode: Int): Boolean = keyCode == KEYCODE_PROG_BLUE

    fun isBack(keyCode: Int): Boolean =
        keyCode == KEYCODE_BACK || keyCode == KEYCODE_ESCAPE

    fun isBackspace(keyCode: Int): Boolean =
        keyCode == KEYCODE_DEL || keyCode == KEYCODE_FORWARD_DEL

    fun isRewindKey(keyCode: Int): Boolean =
        keyCode == KEYCODE_MEDIA_REWIND ||
            keyCode == KEYCODE_MEDIA_STEP_BACKWARD ||
            keyCode == KEYCODE_MEDIA_SKIP_BACKWARD

    fun isFastForwardKey(keyCode: Int): Boolean =
        keyCode == KEYCODE_MEDIA_FAST_FORWARD ||
            keyCode == KEYCODE_MEDIA_STEP_FORWARD ||
            keyCode == KEYCODE_MEDIA_SKIP_FORWARD

    fun digitOrNull(keyCode: Int): Char? = when (keyCode) {
        in KEYCODE_0..KEYCODE_9 -> ('0'.code + (keyCode - KEYCODE_0)).toChar()
        in KEYCODE_NUMPAD_0..KEYCODE_NUMPAD_9 -> ('0'.code + (keyCode - KEYCODE_NUMPAD_0)).toChar()
        else -> null
    }
}

/**
 * Limita la cadencia de eventos repetidos al mantener pulsada una flecha del mando.
 *
 * Inspirado en `NuvioTV/DpadThrottleModifier`: las pulsaciones iniciales (`repeatCount == 0`)
 * pasan siempre sin retardo, mientras que las repeticiones continuas (`repeatCount > 0`)
 * se espacian para evitar saltos bruscos y caídas de fotogramas al reciclar tarjetas.
 */
class DpadRepeatGate(
    private val horizontalGateMs: Long = 75L,
    private val verticalGateMs: Long = 105L
) {
    private var lastHorizontalAt = 0L
    private var lastVerticalAt = 0L

    fun shouldAllow(keyCode: Int, repeatCount: Int, nowMs: Long): Boolean {
        if (!TvKeyCodes.isDirectional(keyCode)) return true
        val horizontal = TvKeyCodes.isHorizontal(keyCode)
        if (repeatCount <= 0) {
            if (horizontal) lastHorizontalAt = nowMs else lastVerticalAt = nowMs
            return true
        }
        val gate = if (horizontal) horizontalGateMs else verticalGateMs
        val last = if (horizontal) lastHorizontalAt else lastVerticalAt
        if (nowMs - last < gate) return false
        if (horizontal) lastHorizontalAt = nowMs else lastVerticalAt = nowMs
        return true
    }
}

/**
 * Escala progresiva de salto temporal al mantener ◀ / ▶ en el reproductor.
 *
 * Pulsación corta: 10 s. Mantener pulsado acelera a 20 s, 30 s y 60 s,
 * evitando tener que pulsar decenas de veces en películas largas.
 */
object PlayerScrubRates {
    fun seekStepMs(repeatCount: Int): Long = when {
        repeatCount >= 15 -> 60_000L
        repeatCount >= 8 -> 30_000L
        repeatCount >= 3 -> 20_000L
        else -> 10_000L
    }

    fun computeSeekTarget(currentMs: Long, durationMs: Long, deltaMs: Long): Long {
        if (durationMs <= 0L) return currentMs.coerceAtLeast(0L)
        return (currentMs.coerceAtLeast(0L) + deltaMs).coerceIn(0L, durationMs)
    }
}

/**
 * Evita que el evento `ACTION_UP` del botón OK que abrió una capa/diálogo/teclado
 * se filtre como un clic accidental sobre el primer elemento recién enfocado.
 *
 * Al abrir la capa (`armSuppressNextKeyUp` o en su estado inicial), cualquier `KeyUp`
 * de confirmación que llegue sin haber visto antes su `KeyDown` dentro de la capa se
 * descarta. En cuanto se ve un `KeyDown` dentro de la capa, el ciclo Down→Up funciona
 * con normalidad.
 */
class ConfirmKeyUpGuard(initialSuppressUntilDown: Boolean = true) {
    private var sawConfirmDownInside = !initialSuppressUntilDown

    fun armSuppressNextKeyUp() {
        sawConfirmDownInside = false
    }

    fun onKeyDown(keyCode: Int) {
        if (TvKeyCodes.isConfirm(keyCode)) {
            sawConfirmDownInside = true
        }
    }

    /** Devuelve `true` si este `KeyUp` debe consumirse e ignorarse. */
    fun shouldConsumeKeyUp(keyCode: Int): Boolean {
        if (!TvKeyCodes.isConfirm(keyCode)) return false
        if (!sawConfirmDownInside) return true
        sawConfirmDownInside = false
        return false
    }
}
