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

    /** Claves de API cifradas con [SecretBox] (Android Keystore). */
    private const val KEY_API_SEALED = "apiKeysEnc"

    /** Claves de API sin cifrar: formato antiguo y dispositivos sin Keystore (API 22). */
    private const val KEY_API_PLAIN = "apiKeys"
    private var prefs: SharedPreferences? = null

    // Los `var` publican un setter privado con la misma firma JVM que un
    // `fun setXxx(...)`; por eso los métodos que guardan se llaman setXxxEnabled,
    // savePlaybackSpeed, etc. (un `fun setAutoplay` no compila junto al `var`).

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

    /**
     * URLs de addons/proyectos de STREAMS añadidos desde el panel
     * (p. ej. StreamFlix Reborn, Torrentio, Comet…). El addon interno
     * «Omni Streams» les pide enlaces de reproducción.
     */
    var streamAddons by mutableStateOf<List<String>>(emptyList())
        private set

    /** Repositorios de plugins Nuvio (All-in-One, Yoru, etc.) — manifest con `scrapers[]`. */
    var nuvioPlugins by mutableStateOf<List<String>>(emptyList())
        private set

    /** APIs añadidas desde el panel (nombre → clave). */
    var apiKeys by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /**
     * Las claves de API están cifradas en disco (Android Keystore). Es
     * `false` en dispositivos sin Keystore AES (API 22) o si el cifrado
     * falló y hubo que guardarlas en claro: la interfaz lo dice tal cual.
     */
    var secretsEncrypted by mutableStateOf(false)
        private set

    /**
     * Resolver los nombres con el **DNS de Cloudflare** (DoH) cuando el DNS
     * del sistema falla. Apagado = comportamiento clásico.
     */
    var cloudflareDns by mutableStateOf(true)
        private set

    /** Se incrementa con cada cambio para refrescar listas observables. */
    var revision by mutableStateOf(0)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        autoplay = p.getBoolean("autoplay", true)
        autoNext = p.getBoolean("autoNext", true)
        mutedStart = p.getBoolean("mutedStart", false)
        subtitlesDefault = p.getBoolean("subtitlesDefault", false)
        playbackSpeed = p.getFloat("playbackSpeed", 1f)
        preferredQuality = p.getString("preferredQuality", "Auto") ?: "Auto"
        cloudflareDns = p.getBoolean("cloudflareDns", true)
        Doh.enabled = cloudflareDns
        pairingCode = p.getString("pairingCode", null) ?: generatePairingCode().also { code ->
            p.edit().putString("pairingCode", code).apply()
        }
        linkedAccount = p.getString("linkedAccount", "") ?: ""
        // Las URLs guardadas por versiones anteriores también pasan la política de red
        customAddons = parseStringList(p.getString("customAddons", "[]")).filter { NetPolicy.isAllowed(it) }
        streamAddons = parseStringList(p.getString("streamAddons", "[]")).filter { NetPolicy.isAllowed(it) }
        nuvioPlugins = parseStringList(p.getString("nuvioPlugins", "[]")).filter { NetPolicy.isAllowed(it) }
        loadApiKeys(p)
    }

    /**
     * Lee las claves de API. Si quedan en claro de una versión anterior y
     * el dispositivo puede cifrar, se migran al formato cifrado.
     */
    private fun loadApiKeys(p: SharedPreferences) {
        val sealed = p.getString(KEY_API_SEALED, null)
        val plain = p.getString(KEY_API_PLAIN, null)
        apiKeys = when {
            // Si no se puede descifrar (p. ej. datos restaurados en otro
            // dispositivo) se parte de cero: hay que volver a introducirlas
            sealed != null -> SecretBox.decrypt(sealed)?.let { parseStringMap(it) } ?: emptyMap()
            plain != null -> parseStringMap(plain)
            else -> emptyMap()
        }
        if (plain != null) {
            persistApiKeys(apiKeys)
        } else {
            secretsEncrypted = SecretBox.available
        }
    }

    /** Guarda las claves cifradas si es posible; si no, en claro (y lo refleja en [secretsEncrypted]). */
    private fun persistApiKeys(keys: Map<String, String>) {
        val json = org.json.JSONObject(keys).toString()
        val sealed = if (keys.isEmpty()) null else SecretBox.encrypt(json)
        update {
            when {
                keys.isEmpty() -> {
                    remove(KEY_API_SEALED)
                    remove(KEY_API_PLAIN)
                }
                sealed != null -> {
                    putString(KEY_API_SEALED, sealed)
                    remove(KEY_API_PLAIN)
                }
                else -> {
                    putString(KEY_API_PLAIN, json)
                    remove(KEY_API_SEALED)
                }
            }
        }
        secretsEncrypted = SecretBox.available && (keys.isEmpty() || sealed != null)
    }

    fun setAutoplayEnabled(value: Boolean) = update { putBoolean("autoplay", value) }.also { autoplay = value }

    fun setAutoNextEnabled(value: Boolean) = update { putBoolean("autoNext", value) }.also { autoNext = value }

    fun setMutedStartEnabled(value: Boolean) = update { putBoolean("mutedStart", value) }.also { mutedStart = value }

    fun setSubtitlesByDefault(value: Boolean) = update { putBoolean("subtitlesDefault", value) }.also { subtitlesDefault = value }

    fun savePlaybackSpeed(value: Float) = update { putFloat("playbackSpeed", value) }.also { playbackSpeed = value }

    fun savePreferredQuality(value: String) = update { putString("preferredQuality", value) }.also { preferredQuality = value }

    /** Activa o desactiva el DNS de Cloudflare para las descargas de la app. */
    fun saveCloudflareDns(value: Boolean) = update { putBoolean("cloudflareDns", value) }.also {
        cloudflareDns = value
        Doh.enabled = value
        revision++
    }

    /** Enlaza una cuenta desde el panel web del QR. */
    fun saveLinkedAccount(email: String) = update { putString("linkedAccount", email) }.also {
        linkedAccount = email
        revision++
    }

    /**
     * Guarda los addons (URLs de manifest.json) añadidos desde el panel.
     * Solo se aceptan las URLs que cumplen [NetPolicy] (https, o http en la red local).
     */
    fun saveCustomAddons(urls: List<String>) {
        val cleaned = urls.map { it.trim() }.filter { NetPolicy.isAllowed(it) }.distinct()
        update { putString("customAddons", org.json.JSONArray(cleaned).toString()) }.also {
            customAddons = cleaned
            revision++
        }
    }

    /**
     * Guarda los addons/proyectos de streams añadidos desde el panel.
     * Solo se aceptan las URLs que cumplen [NetPolicy] (https, o http en la red local).
     */
    fun saveStreamAddons(urls: List<String>) {
        val cleaned = urls.map { it.trim() }.filter { NetPolicy.isAllowed(it) }.distinct()
        update { putString("streamAddons", org.json.JSONArray(cleaned).toString()) }.also {
            streamAddons = cleaned
            revision++
        }
    }

    /** Guarda los repositorios de plugins Nuvio (manifest con `scrapers[]`). */
    fun saveNuvioPlugins(urls: List<String>) {
        val cleaned = urls.map { it.trim() }.filter { it.isNotBlank() && NetPolicy.isAllowed(it) }.distinct()
        update { putString("nuvioPlugins", org.json.JSONArray(cleaned).toString()) }.also {
            nuvioPlugins = cleaned
            revision++
        }
    }

    /** Guarda las APIs añadidas desde el panel (nombre → clave). */
    fun saveApiKeys(keys: Map<String, String>) {
        val cleaned = keys.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() }
        persistApiKeys(cleaned)
        apiKeys = cleaned
        revision++
    }

    /** Etiqueta legible de una velocidad (1,25×). */
    fun speedLabel(speed: Float = playbackSpeed): String =
        String.format(java.util.Locale.US, "%.2f", speed).replace('.', ',').trimEnd('0').trimEnd(',') + "×"

    private fun generatePairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sin 0/O/1/I
        // Es la única credencial del panel: generador criptográfico, no kotlin.random
        val random = java.security.SecureRandom()
        return (1..6).map { chars[random.nextInt(chars.length)] }.joinToString("")
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
