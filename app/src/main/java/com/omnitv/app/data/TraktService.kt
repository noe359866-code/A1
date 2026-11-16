package com.omnitv.app.data

import org.json.JSONObject

/**
 * Sincronización opcional con Trakt (trakt.tv): vinculación por código de
 * dispositivo (el flujo pensado para pantallas sin teclado cómodo, como una
 * TV) y *scrobble* de inicio/pausa/fin para que el progreso aparezca en la
 * cuenta del usuario.
 *
 * Omni TV **no** incluye credenciales propias de Trakt. El usuario crea una
 * app gratuita en `trakt.tv/oauth/applications` y guarda sus claves en el
 * panel Vincular (QR) como `TRAKT` / `TRAKT_SECRET`. Sin ellas el token no se
 * pide y el reproductor no llama a Trakt en absoluto.
 *
 * Los tokens viven cifrados en el televisor (ver [SettingsStore]); las
 * funciones de red son bloqueantes y deben llamarse desde `Dispatchers.IO`.
 * Todos los fallos son silenciosos para el usuario salvo en la vinculación:
 * un scrobble fallido **nunca** interrumpe ni altera la reproducción.
 */
object TraktService {

    enum class PollResult { Linked, Pending, Expired, Denied, Failed }

    /** ¿Hay credenciales de la app Trakt del usuario en el panel? */
    fun isConfigured(): Boolean = SettingsStore.traktCredentials() != null

    fun isLinked(): Boolean = SettingsStore.traktLinked

    /** `POST /oauth/device/code`. Null si no hay credenciales o Trakt no responde. */
    internal fun requestDeviceCode(): TraktProtocol.DeviceCode? {
        val (clientId, _) = SettingsStore.traktCredentials() ?: return null
        val body = JSONObject().put("client_id", clientId).toString()
        val result = AddonHttp.request(
            "POST",
            "${TraktProtocol.AUTH_BASE}/oauth/device/code",
            body,
            mapOf("Content-Type" to "application/json"),
        )
        val json = result.value as? JSONObject ?: return null
        return TraktProtocol.parseDeviceCode(json)
    }

    /**
     * Un intento de canje del código. `Pending` = el usuario aún no autorizó
     * (HTTP 400 con `authorization_pending` o 429 con `slow_down`); los demás
     * terminan el flujo. Un 400 con otro `error` (p. ej. `invalid_client` por
     * claves incorrectas) NO es «pendiente»: sería un bucle infinito silencioso.
     */
    fun pollDeviceToken(deviceCode: String): PollResult {
        val (clientId, clientSecret) = SettingsStore.traktCredentials() ?: return PollResult.Failed
        val body = JSONObject()
            .put("code", deviceCode)
            .put("client_id", clientId)
            .put("client_secret", clientSecret)
            .put("redirect_uri", TraktProtocol.OOB_REDIRECT_URI)
            .put("grant_type", "urn:ietf:wg:oauth:2.0:device_code")
            .toString()
        val result = AddonHttp.request(
            "POST",
            "${TraktProtocol.AUTH_BASE}/oauth/token",
            body,
            mapOf("Content-Type" to "application/json"),
        )
        return when (result.status) {
            200 -> {
                val json = result.value as? JSONObject ?: return PollResult.Failed
                val tokens = TraktProtocol.parseToken(json, System.currentTimeMillis())
                    ?: return PollResult.Failed
                if (!SettingsStore.saveTraktTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresAtMs)) {
                    return PollResult.Failed
                }
                PollResult.Linked
            }
            400 -> when (val error = (result.value as? JSONObject)?.optString("error").orEmpty()) {
                "authorization_pending", "slow_down" -> PollResult.Pending
                else -> PollResult.Failed // incluye invalid_client y cuerpos ilegibles
            }
            429 -> PollResult.Pending      // slow_down: el llamante espacia los intentos
            410 -> PollResult.Expired      // el código caducó
            418 -> PollResult.Denied       // el usuario denegó el acceso
            else -> PollResult.Failed
        }
    }

    /** Renueva el token si le queda poco; true si queda un token válido usable. */
    fun ensureValidToken(): Boolean {
        if (!SettingsStore.traktLinked) return false
        if (!TraktProtocol.needsRefresh(SettingsStore.traktExpiresAtMs, System.currentTimeMillis())) return true
        return synchronized(refreshLock) {
            // Otro hilo pudo renovarlo mientras esperábamos el cerrojo.
            if (!TraktProtocol.needsRefresh(SettingsStore.traktExpiresAtMs, System.currentTimeMillis())) {
                return true
            }
            val (clientId, clientSecret) = SettingsStore.traktCredentials() ?: return false
            val refresh = SettingsStore.traktRefreshToken ?: return false
            val body = JSONObject()
                .put("refresh_token", refresh)
                .put("client_id", clientId)
                .put("client_secret", clientSecret)
                .put("redirect_uri", TraktProtocol.OOB_REDIRECT_URI)
                .put("grant_type", "refresh_token")
                .toString()
            val result = AddonHttp.request(
                "POST",
                "${TraktProtocol.AUTH_BASE}/oauth/token",
                body,
                mapOf("Content-Type" to "application/json"),
            )
            val json = result.value as? JSONObject
            val tokens = json?.let { TraktProtocol.parseToken(it, System.currentTimeMillis()) }
            if (result.status == 200 && tokens != null) {
                SettingsStore.saveTraktTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresAtMs)
            } else if (result.status in listOf(400, 401)) {
                // Refresh rechazado de forma definitiva: la vinculación murió.
                // Se limpia para que la interfaz diga «sin vincular» en vez de
                // fallar en silencio para siempre.
                SettingsStore.clearTraktTokens()
                false
            } else {
                // Fallo transitorio (red, 403, 5xx): NO se borra la vinculación;
                // se reintentará en el próximo scrobble.
                false
            }
        }
    }

    enum class ScrobbleEvent(val path: String) {
        START("start"),
        PAUSE("pause"),
        STOP("stop"),
    }

    /**
     * Envía un scrobble. Bloqueante; llamar desde `Dispatchers.IO`.
     * No lanza nunca: cualquier problema termina en «sin marcar».
     */
    fun scrobble(event: ScrobbleEvent, video: Video, positionMs: Long, durationMs: Long) {
        runCatching {
            if (!SettingsStore.traktLinked) return
            if (!ensureValidToken()) return
            val body = TraktProtocol.buildScrobbleBody(
                video,
                TraktProtocol.clampProgress(positionMs, durationMs),
            ) ?: return
            val clientId = SettingsStore.traktCredentials()?.first ?: return
            val headers = TraktProtocol.apiHeaders(clientId, SettingsStore.traktAccessToken)
            AddonHttp.request("POST", "${TraktProtocol.API_BASE}/scrobble/${event.path}", body, headers)
        }
    }

    private val refreshLock = Any()
}
