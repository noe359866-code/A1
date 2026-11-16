package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas JVM para las primitivas puras de navegación D-pad y mando TV. */
class TvDpadLogicTest {
    @Test
    fun `reconoce dpad y system navigation como teclas direccionales`() {
        assertTrue(TvKeyCodes.isUp(TvKeyCodes.KEYCODE_DPAD_UP))
        assertTrue(TvKeyCodes.isUp(TvKeyCodes.KEYCODE_SYSTEM_NAVIGATION_UP))
        assertTrue(TvKeyCodes.isDown(TvKeyCodes.KEYCODE_DPAD_DOWN))
        assertTrue(TvKeyCodes.isDown(TvKeyCodes.KEYCODE_SYSTEM_NAVIGATION_DOWN))
        assertTrue(TvKeyCodes.isLeft(TvKeyCodes.KEYCODE_DPAD_LEFT))
        assertTrue(TvKeyCodes.isLeft(TvKeyCodes.KEYCODE_SYSTEM_NAVIGATION_LEFT))
        assertTrue(TvKeyCodes.isRight(TvKeyCodes.KEYCODE_DPAD_RIGHT))
        assertTrue(TvKeyCodes.isRight(TvKeyCodes.KEYCODE_SYSTEM_NAVIGATION_RIGHT))

        assertTrue(TvKeyCodes.isHorizontal(TvKeyCodes.KEYCODE_SYSTEM_NAVIGATION_LEFT))
        assertTrue(TvKeyCodes.isDirectional(TvKeyCodes.KEYCODE_SYSTEM_NAVIGATION_DOWN))
        assertFalse(TvKeyCodes.isHorizontal(TvKeyCodes.KEYCODE_DPAD_UP))
        assertFalse(TvKeyCodes.isDirectional(TvKeyCodes.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `mapea confirmacion, retroceso, borrado, media y digitos`() {
        assertTrue(TvKeyCodes.isConfirm(TvKeyCodes.KEYCODE_DPAD_CENTER))
        assertTrue(TvKeyCodes.isConfirm(TvKeyCodes.KEYCODE_ENTER))
        assertTrue(TvKeyCodes.isConfirm(TvKeyCodes.KEYCODE_NUMPAD_ENTER))
        assertFalse(TvKeyCodes.isConfirm(TvKeyCodes.KEYCODE_SPACE))

        assertTrue(TvKeyCodes.isBack(TvKeyCodes.KEYCODE_BACK))
        assertTrue(TvKeyCodes.isBack(TvKeyCodes.KEYCODE_ESCAPE))

        assertTrue(TvKeyCodes.isBackspace(TvKeyCodes.KEYCODE_DEL))
        assertTrue(TvKeyCodes.isBackspace(TvKeyCodes.KEYCODE_FORWARD_DEL))

        assertTrue(TvKeyCodes.isRewindKey(TvKeyCodes.KEYCODE_MEDIA_REWIND))
        assertTrue(TvKeyCodes.isRewindKey(TvKeyCodes.KEYCODE_MEDIA_STEP_BACKWARD))
        assertTrue(TvKeyCodes.isRewindKey(TvKeyCodes.KEYCODE_MEDIA_SKIP_BACKWARD))
        assertTrue(TvKeyCodes.isFastForwardKey(TvKeyCodes.KEYCODE_MEDIA_FAST_FORWARD))
        assertTrue(TvKeyCodes.isFastForwardKey(TvKeyCodes.KEYCODE_MEDIA_STEP_FORWARD))
        assertTrue(TvKeyCodes.isFastForwardKey(TvKeyCodes.KEYCODE_MEDIA_SKIP_FORWARD))

        assertEquals('0', TvKeyCodes.digitOrNull(TvKeyCodes.KEYCODE_0))
        assertEquals('9', TvKeyCodes.digitOrNull(TvKeyCodes.KEYCODE_9))
        assertEquals('0', TvKeyCodes.digitOrNull(TvKeyCodes.KEYCODE_NUMPAD_0))
        assertEquals('9', TvKeyCodes.digitOrNull(TvKeyCodes.KEYCODE_NUMPAD_9))
        assertNull(TvKeyCodes.digitOrNull(TvKeyCodes.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `dpad repeat gate limita repeticiones sostenidas pero permite pulsaciones iniciales`() {
        val gate = DpadRepeatGate(horizontalGateMs = 75L, verticalGateMs = 105L)

        assertTrue(gate.shouldAllow(TvKeyCodes.KEYCODE_DPAD_RIGHT, repeatCount = 0, nowMs = 1000L))
        assertFalse(gate.shouldAllow(TvKeyCodes.KEYCODE_DPAD_RIGHT, repeatCount = 1, nowMs = 1040L))
        assertTrue(gate.shouldAllow(TvKeyCodes.KEYCODE_DPAD_RIGHT, repeatCount = 2, nowMs = 1080L))

        // La dirección vertical mantiene su propio reloj independiente
        assertTrue(gate.shouldAllow(TvKeyCodes.KEYCODE_DPAD_DOWN, repeatCount = 0, nowMs = 1090L))
        assertFalse(gate.shouldAllow(TvKeyCodes.KEYCODE_DPAD_DOWN, repeatCount = 1, nowMs = 1150L))
        assertTrue(gate.shouldAllow(TvKeyCodes.KEYCODE_DPAD_DOWN, repeatCount = 2, nowMs = 1200L))

        // Una nueva pulsación inicial siempre pasa aunque llegue rápido
        assertTrue(gate.shouldAllow(TvKeyCodes.KEYCODE_DPAD_DOWN, repeatCount = 0, nowMs = 1210L))
        // Teclas no direccionales no se limitan nunca
        assertTrue(gate.shouldAllow(TvKeyCodes.KEYCODE_DPAD_CENTER, repeatCount = 5, nowMs = 1215L))
    }

    @Test
    fun `aceleracion progresiva de scrub y acotado a duracion`() {
        assertEquals(10_000L, PlayerScrubRates.seekStepMs(0))
        assertEquals(10_000L, PlayerScrubRates.seekStepMs(2))
        assertEquals(20_000L, PlayerScrubRates.seekStepMs(3))
        assertEquals(20_000L, PlayerScrubRates.seekStepMs(7))
        assertEquals(30_000L, PlayerScrubRates.seekStepMs(8))
        assertEquals(30_000L, PlayerScrubRates.seekStepMs(14))
        assertEquals(60_000L, PlayerScrubRates.seekStepMs(15))

        assertEquals(0L, PlayerScrubRates.computeSeekTarget(5_000L, 120_000L, -20_000L))
        assertEquals(120_000L, PlayerScrubRates.computeSeekTarget(110_000L, 120_000L, 30_000L))
        assertEquals(40_000L, PlayerScrubRates.computeSeekTarget(30_000L, 120_000L, 10_000L))
        assertEquals(50_000L, PlayerScrubRates.computeSeekTarget(50_000L, 0L, 10_000L))
        assertEquals(0L, PlayerScrubRates.computeSeekTarget(-100L, -1L, 10_000L))
    }

    @Test
    fun `confirm key up guard consume action up huerfano al abrir capa y respeta pulsaciones internas`() {
        val guard = ConfirmKeyUpGuard(initialSuppressUntilDown = true)

        // El KeyUp residual del botón que abrió el overlay se descarta
        assertTrue(guard.shouldConsumeKeyUp(TvKeyCodes.KEYCODE_DPAD_CENTER))
        assertTrue(guard.shouldConsumeKeyUp(TvKeyCodes.KEYCODE_ENTER))
        assertFalse(guard.shouldConsumeKeyUp(TvKeyCodes.KEYCODE_BACK))

        // Un ciclo completo KeyDown -> KeyUp dentro de la capa sí pasa
        guard.onKeyDown(TvKeyCodes.KEYCODE_DPAD_CENTER)
        assertFalse(guard.shouldConsumeKeyUp(TvKeyCodes.KEYCODE_DPAD_CENTER))

        // Después del KeyUp consumado por el ciclo normal, un KeyUp huérfano posterior vuelve a bloquearse
        assertTrue(guard.shouldConsumeKeyUp(TvKeyCodes.KEYCODE_DPAD_CENTER))

        // Con initialSuppressUntilDown = false se permite el primer ciclo sin esperar KeyDown
        val permissive = ConfirmKeyUpGuard(initialSuppressUntilDown = false)
        assertFalse(permissive.shouldConsumeKeyUp(TvKeyCodes.KEYCODE_NUMPAD_ENTER))
        permissive.armSuppressNextKeyUp()
        assertTrue(permissive.shouldConsumeKeyUp(TvKeyCodes.KEYCODE_NUMPAD_ENTER))
    }
}
