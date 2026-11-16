package com.omnitv.app.data

import org.json.JSONObject

/**
 * Protocolo público de la API de Trakt (trakt.tv): constantes, parsers y
 * construcción de cuerpos. Código **original** escrito a partir de la
 * documentación pública de Trakt; no deriva del código de otros proyectos.
 *
 * Todo lo que hay aquí es puro JVM (sin Android ni red) para que los tests
 * unitarios lo cubran directamente. La capa con red vive en [TraktService].
 *
 * La sincronización con Trakt es **opcional** y la configura el usuario con
 * sus propias claves (`TRAKT` / `TRAKT_SECRET` en el panel Vincular), porque
 * Omni TV no incluye ninguna clave propia: cada instalación usa la app
 * Trakt de quien la configura.
 */
internal object TraktProtocol {

    const val AUTH_BASE = "https://auth.trakt.tv"
    const val API_BASE = "https://api.trakt.tv"
    const val OOB_REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
    const val API_VERSION = "2"

    /** Renueva el token cuando le queda menos de un día de vida. */
    const val REFRESH_SKEW_MS = 24L * 60 * 60 * 1000

    /** Duración por defecto de un token si la respuesta no trae `expires_in`. */
    const val DEFAULT_TOKEN_TTL_SEC = 7L * 24 * 3600

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val expiresInSec: Long,
        val intervalSec: Long
    )

    data class TokenSet(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtMs: Long
    )

    /** Recurso que identifica a qué se refiere un scrobble. */
    enum class ItemKind { MOVIE, EPISODE }

    /**
     * `POST /oauth/device/code` → `{ device_code, user_code, verification_url, expires_in, interval }`.
     * Devuelve null ante payloads incompletos o una URL que no sea https
     * (Trakt la sirve en https; no se acepta otra cosa).
     */
    fun parseDeviceCode(json: JSONObject): DeviceCode? {
        val deviceCode = json.optString("device_code").takeIf { it.isNotBlank() } ?: return null
        val userCode = json.optString("user_code").trim().takeIf { it.isNotBlank() } ?: return null
        val url = json.optString("verification_url").takeIf { it.startsWith("https://") }
            ?: "https://trakt.tv/activate"
        val expiresIn = json.optLong("expires_in", 600).coerceIn(60, 3600)
        val interval = json.optLong("interval", 5).coerceIn(2, 60)
        return DeviceCode(deviceCode, userCode, url, expiresIn, interval)
    }

    /**
     * `POST /oauth/token` → `{ access_token, refresh_token, expires_in, … }`.
     */
    fun parseToken(json: JSONObject, nowMs: Long): TokenSet? {
        val access = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        val refresh = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: return null
        val expiresIn = json.optLong("expires_in", DEFAULT_TOKEN_TTL_SEC).coerceAtLeast(0)
        return TokenSet(access, refresh, nowMs + expiresIn * 1000)
    }

    fun needsRefresh(expiresAtMs: Long, nowMs: Long): Boolean =
        expiresAtMs - nowMs < REFRESH_SKEW_MS

    /** Progreso 0–100 para Trakt; nunca NaN ni negativo. */
    fun clampProgress(positionMs: Long, durationMs: Long): Double {
        if (durationMs <= 0) return 0.0
        return (100.0 * positionMs.coerceAtLeast(0) / durationMs).coerceIn(0.0, 100.0)
    }

    /**
     * Cuerpo de `/scrobble/start|pause|stop`. **Fail-closed:** sin id IMDb ni
     * TMDB devuelve `null` (mejor no marcar nada que marcar la ficha equivocada).
     * Los ids de anime (MAL/Kitsu) no los entiende Trakt: también se rechazan.
     *
     * Para episodios se envían `show.ids` + `episode{season,number}`, que es lo
     * que Trakt espera; `video.streamId` ya conserva el IMDb/TMDB de la serie.
     */
    fun buildScrobbleBody(video: Video, progressPercent: Double): String? {
        val target = SkipTarget.from(video) ?: return null
        val ids = buildString {
            // Solo IMDb con la forma real (`tt` + 6-10 dígitos); el resto se rechaza.
            target.imdbId?.takeIf { it.matches(Regex("tt[0-9]{6,10}")) }?.let { append("\"imdb\":\"$it\"") }
            target.tmdbId?.toIntOrNull()?.let { tmdb ->
                if (isNotEmpty()) append(',')
                append("\"tmdb\":$tmdb")
            }
        }
        if (ids.isEmpty()) return null
        val progress = String.format(java.util.Locale.US, "%.2f", progressPercent.coerceIn(0.0, 100.0))
        return if (target.isEpisode) {
            """{"show":{"ids":{$ids}},"episode":{"season":${target.season},"number":${target.episode}},"progress":$progress}"""
        } else {
            """{"movie":{"ids":{$ids}},"progress":$progress}"""
        }
    }

    /** Cabeceras comunes a todas las llamadas autenticadas de la API. */
    fun apiHeaders(clientId: String, accessToken: String?): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("trakt-api-version", API_VERSION)
        put("trakt-api-key", clientId)
        if (!accessToken.isNullOrBlank()) put("Authorization", "Bearer $accessToken")
    }
}
