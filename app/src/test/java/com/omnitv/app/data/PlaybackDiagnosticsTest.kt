package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDiagnosticsTest {

    private val unsupportedAudio = TrackFacts("audio/eac3", "ec-3", supported = false)
    private val supportedVideo = TrackFacts("video/avc", "avc1.640028", supported = true)

    @Test fun `los fallos de red no recomiendan convertir`() {
        assertFalse(PlaybackDiagnostics.recommendBridge("ERROR_CODE_IO_BAD_HTTP_STATUS", supportedVideo, unsupportedAudio))
        assertTrue(PlaybackDiagnostics.adviceFor("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", null, null).contains("red"))
    }

    @Test fun `audio no soportado o decoder recomiendan puente`() {
        assertTrue(PlaybackDiagnostics.recommendBridge("ERROR_CODE_DECODER_INIT_FAILED", supportedVideo, unsupportedAudio))
        assertTrue(PlaybackDiagnostics.recommendBridge("ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED", null, null))
        assertTrue(PlaybackDiagnostics.adviceFor("ERROR_CODE_DECODING_FAILED", supportedVideo, unsupportedAudio).contains("AAC"))
    }

    @Test fun `el informe no afirma licencias y sobrevive al json`() {
        val report = PlaybackDiagnostics.record("ERROR_CODE_DECODER_INIT_FAILED", "CDN · 1080p", supportedVideo, unsupportedAudio)
        val parsed = PlaybackDiagnosticsJson.parse(report.toJson())
        requireNotNull(parsed)
        assertEquals(report.errorCode, parsed.errorCode)
        assertEquals(report.sourceLabel, parsed.sourceLabel)
        assertEquals("audio/eac3", parsed.audio?.mime)
        assertEquals(false, parsed.audio?.supported)
        assertTrue(parsed.bridgeRecommended)
        assertFalse(parsed.advice.contains("licencia", ignoreCase = true))
    }

    @Test fun `json vacio o corrupto no inventa diagnostico`() {
        assertNull(PlaybackDiagnosticsJson.parse(""))
        assertNull(PlaybackDiagnosticsJson.parse("no-json"))
        assertNull(PlaybackDiagnosticsJson.parse("""{"source":"x"}"""))
    }
}
