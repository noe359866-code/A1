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

    // ---------------- Vinculación (QR del panel de Ajustes) ----------------

    /** Código de emparejamiento del televisor (se muestra junto al QR). */
    var pairingCode by mutableStateOf("------")
        private set

    /** Cuenta enlazada desde el panel web (email). */
    var linkedAccount by mutableStateOf("")
        private set

    /** URLs de manifest.json de addons Stremio añadidos desde el panel. */
    var customAddons by mutableStateOf<List<String>>(emptyList())
        private set

    /** APIs añadidas desde el panel (nombre → clave). */
    var apiKeys by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** Se incrementa con cada cambio para refrescar listas observables. */
    var revision by mutableStateOf(0)
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
            pairingCode = p.getString("pairingCode", null) ?: generatePairingCode().also { code ->
                p.edit().putString("pairingCode", code).apply()
            }
            linkedAccount = p.getString("linkedAccount", "") ?: ""
            customAddons = parseStringList(p.getString("customAddons", "[]"))
            apiKeys = parseStringMap(p.getString("apiKeys", "{}"))
        }
    }

    fun setAutoplay(value: Boolean) = update { putBoolean("autoplay", value) }.also { autoplay = value }

    fun setAutoNext(value: Boolean) = update { putBoolean("autoNext", value) }.also { autoNext = value }

    fun setMutedStart(value: Boolean) = update { putBoolean("mutedStart", value) }.also { mutedStart = value }

    fun setSubtitlesDefault(value: Boolean) = update { putBoolean("subtitlesDefault", value) }.also { subtitlesDefault = value }

    fun setPlaybackSpeed(value: Float) = update { putFloat("playbackSpeed", value) }.also { playbackSpeed = value }

    fun setPreferredQuality(value: String) = update { putString("preferredQuality", value) }.also { preferredQuality = value }

    /** Enlaza una cuenta desde el panel web del QR. */
    fun setLinkedAccount(email: String) = update { putString("linkedAccount", email) }.also {
        linkedAccount = email
        revision++
    }

    /** Guarda los addons (URLs de manifest.json) añadidos desde el panel. */
    fun setCustomAddons(urls: List<String>) {
        val cleaned = urls.map { it.trim() }.filter { it.startsWith("http") }.distinct()
        update { putString("customAddons", org.json.JSONArray(cleaned).toString()) }.also {
            customAddons = cleaned
            revision++
        }
    }

    /** Guarda las APIs añadidas desde el panel (nombre → clave). */
    fun setApiKeys(keys: Map<String, String>) {
        val cleaned = keys.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() }
        update { putString("apiKeys", org.json.JSONObject(cleaned).toString()) }.also {
            apiKeys = cleaned
            revision++
        }
    }

    /** Etiqueta legible de una velocidad (1,25×). */
    fun speedLabel(speed: Float = playbackSpeed): String =
        String.format(java.util.Locale.US, "%.2f", speed).replace('.', ',').trimEnd('0').trimEnd(',') + "×"

    private fun generatePairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sin 0/O/1/I
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun parseStringList(json: String?): List<String> = runCatching {
        val array = org.json.JSONArray(json ?: "[]")
        buildList { for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
    }.getOrDefault(emptyList())

    private fun parseStringMap(json: String?): Map<String, String> = runCatching {
        val obj = org.json.JSONObject(json ?: "{}")
        buildMap { obj.keys().forEach { key -> obj.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) } } }
    }.getOrDefault(emptyMap())

    private inline fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs?.edit()?.apply(block)?.apply()
    }
}
