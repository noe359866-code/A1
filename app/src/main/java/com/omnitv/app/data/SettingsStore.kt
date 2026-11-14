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

    // ---------------- Saltar intro / créditos ----------------

    /**
     * Tramos saltables activos para todo el mundo: las tres bases de datos se
     * leen de forma anónima, sin clave incluida en la app.
     */
    var skipSegmentsEnabled by mutableStateOf(true)
        private set

    /** Salto automático por tipo. Por defecto solo se ofrece el botón. */
    var autoSkipKinds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Bases de datos consultadas (nombres de [SkipSegmentParser]). */
    var skipSources by mutableStateOf(
        setOf(SkipSegmentParser.SOURCE_SKIPDB, SkipSegmentParser.SOURCE_THEINTRODB, SkipSegmentParser.SOURCE_INTRODB)
    )
        private set

    // ---------------- Vinculación (QR del panel de Ajustes) ----------------

    /** Código de emparejamiento del televisor (se muestra junto al QR). */
    var pairingCode by mutableStateOf("------")
        private set

    /** Hay un PIN de control parental configurado. El PIN nunca se guarda en claro. */
    var parentalPinEnabled by mutableStateOf(false)
        private set

    private var parentalSalt = ""
    private var parentalHash = ""

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

    /** Índices de plugins Nuvio (`scrapers[]`), NO addons Stremio. */
    var nuvioPlugins by mutableStateOf<List<String>>(emptyList())
        private set

    /** Puente HTTP opcional que ejecuta los JS de Nuvio en otro equipo. */
    var nuvioBridge by mutableStateOf("")
        private set

    /** Servicio externo que ejecuta extensiones Cloudstream; vacío = desactivado. */
    var cloudstreamBridge by mutableStateOf("")
        private set

    /** Puente multimedia externo (FFmpeg/ffprobe); vacío = desactivado. */
    var ffmpegBridge by mutableStateOf("")
        private set

    /** Último diagnóstico de reproducción persistido como JSON; vacío = ninguno. */
    var lastPlaybackDiagnostic by mutableStateOf("")
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
        skipSegmentsEnabled = p.getBoolean("skipSegmentsEnabled", true)
        autoSkipKinds = parseStringList(p.getString("autoSkipKinds", "[]")).toSet()
        skipSources = parseStringList(p.getString("skipSources", null)).toSet().filter { it in SKIP_SOURCE_NAMES }.toSet()
            .ifEmpty { setOf(SkipSegmentParser.SOURCE_SKIPDB, SkipSegmentParser.SOURCE_THEINTRODB, SkipSegmentParser.SOURCE_INTRODB) }
        cloudflareDns = p.getBoolean("cloudflareDns", true)
        Doh.enabled = cloudflareDns
        pairingCode = p.getString("pairingCode", null) ?: generatePairingCode().also { code ->
            p.edit().putString("pairingCode", code).apply()
        }
        parentalSalt = p.getString("parentalSalt", "") ?: ""
        parentalHash = p.getString("parentalHash", "") ?: ""
        parentalPinEnabled = parentalSalt.isNotBlank() && parentalHash.isNotBlank()
        linkedAccount = p.getString("linkedAccount", "") ?: ""
        // Las URLs guardadas por versiones anteriores también pasan la política de red
        customAddons = parseStringList(p.getString("customAddons", "[]")).filter { NetPolicy.isAllowed(it) }
        streamAddons = parseStringList(p.getString("streamAddons", "[]")).filter { NetPolicy.isAllowed(it) }
        nuvioPlugins = parseStringList(p.getString("nuvioPlugins", "[]")).filter { NetPolicy.isAllowed(it) }
        nuvioBridge = (p.getString("nuvioBridge", "") ?: "").takeIf { NetPolicy.isAllowed(it) }.orEmpty()
        cloudstreamBridge = CloudstreamBridge.normalize(p.getString("cloudstreamBridge", "") ?: "").orEmpty()
        ffmpegBridge = MediaBridge.normalize(p.getString("ffmpegBridge", "") ?: "").orEmpty()
        lastPlaybackDiagnostic = p.getString("lastPlaybackDiagnostic", "") ?: ""
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

    /** Cambia el código del QR. Las sesiones abiertas quedan invalidadas al instante. */
    fun regeneratePairingCode() {
        val code = generatePairingCode()
        update { putString("pairingCode", code) }
        pairingCode = code
    }

    /** Guarda un PIN de 4 cifras como huella con salt; devuelve false si no es válido. */
    fun saveParentalPin(pin: String): Boolean {
        val salt = ParentalPin.newSalt()
        val hash = ParentalPin.hash(pin, salt) ?: return false
        update { putString("parentalSalt", salt); putString("parentalHash", hash) }
        parentalSalt = salt
        parentalHash = hash
        parentalPinEnabled = true
        return true
    }

    /** Quita el PIN (la UI exige verificarlo antes de llamar aquí). */
    fun clearParentalPin() {
        update { remove("parentalSalt"); remove("parentalHash") }
        parentalSalt = ""
        parentalHash = ""
        parentalPinEnabled = false
    }

    fun verifyParentalPin(pin: String): Boolean =
        parentalPinEnabled && ParentalPin.verify(pin, parentalSalt, parentalHash)

    /**
     * Guarda los addons (URLs de manifest.json) añadidos desde el panel.
     * Solo se aceptan las URLs que cumplen [NetPolicy] (https, o http en la red local).
     */
    fun saveCustomAddons(urls: List<String>) {
        val cleaned = urls.mapNotNull(StremioUrl::normalize).distinct()
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

    /** Guarda repositorios Nuvio; su contenido se valida al refrescar, no al escribir. */
    fun saveNuvioPlugins(urls: List<String>) {
        val cleaned = urls.map { it.trim() }.filter { NetPolicy.isAllowed(it) }.distinct()
        update { putString("nuvioPlugins", org.json.JSONArray(cleaned).toString()) }
        nuvioPlugins = cleaned
        revision++
    }

    /** Vaciar la URL desconecta el puente sin borrar los repositorios guardados. */
    fun saveNuvioBridge(url: String) {
        val cleaned = url.trim().trimEnd('/').takeIf {
            it.isEmpty() || (NetPolicy.isAllowed(it) && runCatching {
                val uri = java.net.URI(it); uri.rawQuery == null && uri.rawFragment == null
            }.getOrDefault(false))
        }.orEmpty()
        update { putString("nuvioBridge", cleaned) }
        nuvioBridge = cleaned
        revision++
    }

    fun saveCloudstreamBridge(url: String) {
        val cleaned = CloudstreamBridge.normalize(url).orEmpty()
        update { putString("cloudstreamBridge", cleaned) }
        cloudstreamBridge = cleaned
        revision++
    }

    fun saveFfmpegBridge(url: String) {
        val cleaned = MediaBridge.normalize(url).orEmpty()
        update { putString("ffmpegBridge", cleaned) }
        ffmpegBridge = cleaned
        revision++
    }

    /** Diagnóstico ligero del último fallo; no guarda URLs ni cabeceras del stream. */
    fun saveLastPlaybackDiagnostic(json: String) {
        val cleaned = json.take(4_000)
        update { putString("lastPlaybackDiagnostic", cleaned) }
        lastPlaybackDiagnostic = cleaned
    }

    /** Guarda las APIs añadidas desde el panel (nombre → clave). */
    fun saveApiKeys(keys: Map<String, String>) {
        val cleaned = keys.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() }
        persistApiKeys(cleaned)
        apiKeys = cleaned
        revision++
    }

    val SKIP_SOURCE_NAMES = listOf(
        SkipSegmentParser.SOURCE_SKIPDB, SkipSegmentParser.SOURCE_THEINTRODB, SkipSegmentParser.SOURCE_INTRODB
    )

    fun saveSkipSegmentsEnabled(enabled: Boolean) {
        update { putBoolean("skipSegmentsEnabled", enabled) }
        skipSegmentsEnabled = enabled
        SkipSegmentService.clearCache()
        revision++
    }

    /** Activa/desactiva el salto automático de un tipo (intro, resumen, créditos…). */
    fun setAutoSkip(kind: SkipKind, enabled: Boolean) {
        val next = autoSkipKinds.toMutableSet().apply { if (enabled) add(kind.name) else remove(kind.name) }
        update { putString("autoSkipKinds", org.json.JSONArray(next.toList()).toString()) }
        autoSkipKinds = next
        revision++
    }

    /** Al menos una base de datos queda activa: sin fuentes no hay tramos. */
    fun setSkipSource(source: String, enabled: Boolean) {
        if (source !in SKIP_SOURCE_NAMES) return
        val next = skipSources.toMutableSet().apply { if (enabled) add(source) else remove(source) }
        if (next.isEmpty()) return
        update { putString("skipSources", org.json.JSONArray(next.toList()).toString()) }
        skipSources = next
        SkipSegmentService.clearCache()
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
