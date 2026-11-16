package com.omnitv.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Protocolo Trakt: parsers y construcción de cuerpos (puro JVM).
 * La red y el Keystore se cubren en dispositivo/CI, no aquí.
 */
class TraktServiceTest {

    private fun episode(id: String) = Video(
        id = id.substringBefore(':') + "~e", title = "Serie · 1x02", description = "", backdropUrl = "", cardUrl = "",
        year = 2024, durationMinutes = 45, rating = 0.0, certification = "", quality = "", genres = emptyList(),
        videoUrl = "", type = VideoType.SERIES, remoteType = "series", streamId = id
    )

    @Test
    fun `el codigo de dispositivo se parsea con sus intervalos`() {
        val json = JSONObject("""{"device_code":"dc","user_code":"ABCD-1234","verification_url":"https://trakt.tv/activate","expires_in":600,"interval":5}""")
        val code = TraktProtocol.parseDeviceCode(json)!!
        assertEquals("dc", code.deviceCode)
        assertEquals("ABCD-1234", code.userCode)
        assertEquals("https://trakt.tv/activate", code.verificationUrl)
        assertEquals(600, code.expiresInSec)
        assertEquals(5, code.intervalSec)
    }

    @Test
    fun `un codigo de dispositivo sin device_code se rechaza`() {
        assertNull(TraktProtocol.parseDeviceCode(JSONObject("""{"user_code":"ABCD"}""")))
        // verification_url que no sea https no se acepta (defensa en profundidad)
        val http = JSONObject("""{"device_code":"dc","user_code":"U","verification_url":"http://evil.example/x"}""")
        assertEquals("https://trakt.tv/activate", TraktProtocol.parseDeviceCode(http)!!.verificationUrl)
    }

    @Test
    fun `el token calcula su caducidad desde expires_in`() {
        val json = JSONObject("""{"access_token":"a","refresh_token":"r","expires_in":100}""")
        val tokens = TraktProtocol.parseToken(json, nowMs = 1_000_000)!!
        assertEquals("a", tokens.accessToken)
        assertEquals("r", tokens.refreshToken)
        assertEquals(1_000_000L + 100_000L, tokens.expiresAtMs)
    }

    @Test
    fun `token incompleto se rechaza y el refresco se pide un dia antes`() {
        assertNull(TraktProtocol.parseToken(JSONObject("""{"access_token":"a"}"""), 0L))
        assertTrue(TraktProtocol.needsRefresh(1_000_000L, 1_000_000L))          // ya caducado
        assertTrue(TraktProtocol.needsRefresh(86_400_000L + 1_000_000L, 1_000_000L + 1000L))  // queda < 1 día... igual
        assertFalse(TraktProtocol.needsRefresh(2 * 86_400_000L, 0L))           // queda 2 días
    }

    @Test
    fun `el progreso nunca supera 100 ni es NaN`() {
        assertEquals(50.0, TraktProtocol.clampProgress(500, 1000), 0.001)
        assertEquals(100.0, TraktProtocol.clampProgress(9999, 1000), 0.001)
        assertEquals(0.0, TraktProtocol.clampProgress(100, 0), 0.001)          // duración desconocida
        assertEquals(0.0, TraktProtocol.clampProgress(-5, 1000), 0.001)
    }

    @Test
    fun `scrobble de pelicula lleva el id imdb y tmdb`() {
        val movie = Video(
            id = "m1", title = "Película", description = "", backdropUrl = "", cardUrl = "",
            year = 2024, durationMinutes = 120, rating = 0.0, certification = "", quality = "",
            genres = emptyList(), videoUrl = "", type = VideoType.MOVIE, remoteType = "movie",
            streamId = "tt0133093"
        )
        val body = TraktProtocol.buildScrobbleBody(movie, 42.5)!!
        assertTrue(body.contains("\"movie\""))
        assertTrue(body.contains("\"imdb\":\"tt0133093\""))
        assertTrue(body.contains("\"progress\":42.50"))
    }

    @Test
    fun `scrobble de episodio envia show y season-number`() {
        val ep = episode("tt0903747:1:2")
        val body = TraktProtocol.buildScrobbleBody(ep, 10.0)!!
        assertTrue(body.contains("\"show\":{\"ids\":{\"imdb\":\"tt0903747\""))
        assertTrue(body.contains("\"episode\":{\"season\":1,\"number\":2}"))
    }

    @Test
    fun `scrobble con TMDB funciona sin IMDb`() {
        val ep = episode("tmdb:1396:1:2")
        val body = TraktProtocol.buildScrobbleBody(ep, 80.0)!!
        assertTrue(body.contains("\"tmdb\":1396"))
        assertTrue(body.contains("\"season\":1,\"number\":2"))
    }

    @Test
    fun `scrobble es fail-closed sin ids reconocibles o solo anime`() {
        assertNull(TraktProtocol.buildScrobbleBody(episode(""), 10.0))
        assertNull(TraktProtocol.buildScrobbleBody(episode("mal:5114:1:1"), 10.0))
        assertNull(TraktProtocol.buildScrobbleBody(episode("kitsu:123:1:1"), 10.0))
    }

    @Test
    fun `la firma Authorization solo viaja si hay token`() {
        val anon = TraktProtocol.apiHeaders("cid", null)
        assertEquals("cid", anon["trakt-api-key"])
        assertEquals("2", anon["trakt-api-version"])
        assertFalse(anon.containsKey("Authorization"))
        val authed = TraktProtocol.apiHeaders("cid", "tok")
        assertEquals("Bearer tok", authed["Authorization"])
        assertNotNull(TraktProtocol.apiHeaders("cid", "tok")["Authorization"])
    }
}
