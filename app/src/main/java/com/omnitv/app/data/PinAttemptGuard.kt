package com.omnitv.app.data

/**
 * Control de intentos del PIN parental a nivel de aplicación (no del diálogo).
 *
 * Antes el contador de fallos y el bloqueo de 30 s vivían en estado de la UI
 * del propio diálogo, así que un niño con el mando podía descartar y reabrir
 * el diálogo del PIN para reiniciar el bloqueo y probar sin límite real.
 *
 * Ahora el estado vive aquí: se comparte entre todas las aperturas del diálogo
 * dentro del mismo proceso. Al quinto fallo consecutivo se bloquea 30 s.
 *
 * No persiste entre reinicios del proceso (sin archivo de intentos en disco):
 * sigue siendo un obstáculo de uso doméstico, no criptografía de alta seguridad.
 */
object PinAttemptGuard {

    private var failures = 0
    private var lockedUntil = 0L

    private const val MAX_FAILURES = 5
    private const val LOCKOUT_MS = 30_000L

    /** `true` mientras el bloqueo esté vigente. */
    fun isLocked(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (lockedUntil <= nowMs) {
            // El bloqueo ha caducado: se restablece el contador.
            if (lockedUntil != 0L) {
                lockedUntil = 0L
                failures = 0
            }
            return false
        }
        return true
    }

    /** Segundos restantes de bloqueo (0 si no hay bloqueo). */
    fun lockedSecondsRemaining(nowMs: Long = System.currentTimeMillis()): Int =
        if (lockedUntil > nowMs) (((lockedUntil - nowMs + 999) / 1000)).toInt() else 0

    /** Registra un fallo; al quinto consecutivo bloquea durante 30 segundos. */
    fun recordFailure(nowMs: Long = System.currentTimeMillis()) {
        failures += 1
        if (failures >= MAX_FAILURES) {
            lockedUntil = nowMs + LOCKOUT_MS
            failures = 0
        }
    }

    /** Reinicia el contador tras un PIN correcto. */
    fun recordSuccess() {
        failures = 0
        lockedUntil = 0L
    }
}
