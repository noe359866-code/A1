package com.omnitv.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ajustes globales de la app, persistidos en SharedPreferences.
 * El estado es observable por Compose.
 */
object SettingsStore {

    /** Presets de calidad preferida (se aplican al iniciar cada vídeo). */
    val QUALITY_PRESETS = listOf("Auto", "4K", "1080p", "720p", "480p")

    /** Presets de velocidad de reproducción. */
    val SPEED_PRESETS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

    private const val PREFS = "omni_tv_settings"
    private var prefs: SharedPreferences? = null

    /** Los vídeos empiezan a reproducirse solos al abrirlos. */
    var autoplay by mutableStateOf(true)
        private set

    /** Al terminar un vídeo se reproduce el siguiente (con cuenta atrás). */
    var autoNext by mutableStateOf(true)
        private set

    /** Los vídeos empiezan sin audio. */
    var mutedStart by mutableStateOf(false)
        private set

    /** Activa los subtítulos por defecto si hay pistas disponibles. */
    var subtitlesDefault by mutableStateOf(false)
        private set

    /** Velocidad de reproducción recordada entre vídeos. */
    var playbackSpeed by mutableStateOf(1f)
        private set

    /** Calidad preferida aplicada al iniciar cada vídeo. */
    var preferredQuality by mutableStateOf("Auto")
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            autoplay = p.getBoolean("autoplay", true)
            autoNext = p.getBoolean("autoNext", true)
            mutedStart = p.getBoolean("mutedStart", false)
            subtitlesDefault = p.getBoolean("subtitlesDefault", false)
            playbackSpeed = p.getFloat("playbackSpeed", 1f)
            preferredQuality = p.getString("preferredQuality", "Auto") ?: "Auto"
        }
    }

    fun setAutoplay(value: Boolean) = update { putBoolean("autoplay", value) }.also { autoplay = value }

    fun setAutoNext(value: Boolean) = update { putBoolean("autoNext", value) }.also { autoNext = value }

    fun setMutedStart(value: Boolean) = update { putBoolean("mutedStart", value) }.also { mutedStart = value }

    fun setSubtitlesDefault(value: Boolean) = update { putBoolean("subtitlesDefault", value) }.also { subtitlesDefault = value }

    fun setPlaybackSpeed(value: Float) = update { putFloat("playbackSpeed", value) }.also { playbackSpeed = value }

    fun setPreferredQuality(value: String) = update { putString("preferredQuality", value) }.also { preferredQuality = value }

    /** Etiqueta legible de una velocidad (1,25×). */
    fun speedLabel(speed: Float = playbackSpeed): String =
        String.format(java.util.Locale.US, "%.2f", speed).replace('.', ',').trimEnd('0').trimEnd(',') + "×"

    private inline fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs?.edit()?.apply(block)?.apply()
    }
}
