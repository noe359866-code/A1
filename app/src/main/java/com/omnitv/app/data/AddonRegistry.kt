package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Estado real de instalación: una URL guardada no significa que el servidor responda. */
data class AddonStatus(
    val url: String,
    val manifest: StremioManifest? = null,
    val error: String = "",
    val checking: Boolean = false
) {
    val label: String get() = manifest?.name ?: runCatching { java.net.URI(url).host }.getOrDefault("Addon")
    val summary: String get() = when {
        checking -> "Comprobando manifest…"
        manifest == null -> error.ifBlank { "Pendiente de comprobar" }
        else -> listOfNotNull(
            "catálogo".takeIf { manifest.isCatalogAddon() },
            "fichas".takeIf { manifest.isMetaAddon() },
            "streams HTTP".takeIf { manifest.isStreamAddon() }
        ).joinToString(" · ").ifBlank { "Sin recursos de catálogo, fichas o streams" }
    }
}

/**
 * Descubre las capacidades de los manifests instalados, con URL normalizada,
 * caché corta y un único rastreo por cambio de ajustes. No ejecuta JavaScript.
 */
object AddonRegistry {
    const val TTL_MS = 5 * 60_000L
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Pair<Long, AddonStatus>>()
    val statuses = mutableStateOf<List<AddonStatus>>(emptyList())
    val checking = mutableStateOf(false)

    suspend fun refresh(urls: List<String> = SettingsStore.customAddons, force: Boolean = false): List<AddonStatus> {
        val normalized = urls.mapNotNull(StremioUrl::normalize).distinct()
        return mutex.withLock {
            checking.value = true
            try {
                val now = System.currentTimeMillis()
                val results = coroutineScope {
                    normalized.map { url ->
                        async {
                            val old = entries[url]
                            if (!force && old != null && now - old.first < TTL_MS && old.second.manifest != null) {
                                old.second
                            } else {
                                val status = inspect(url)
                                entries[url] = now to status
                                status
                            }
                        }
                    }.awaitAll()
                }
                entries.keys.retainAll(normalized.toSet())
                statuses.value = results
                results
            } finally {
                checking.value = false
            }
        }
    }

    suspend fun inspect(url: String): AddonStatus {
        val normalized = StremioUrl.normalize(url) ?: return AddonStatus(url, error = "URL no permitida")
        val json = StremioClient.fetchManifest(normalized)
            ?: return AddonStatus(normalized, error = "No se pudo descargar un manifest JSON válido")
        val error = StremioManifestParser.validate(json)
        return if (error != null) AddonStatus(normalized, error = error)
        else AddonStatus(normalized, manifest = StremioManifestParser.parse(json))
    }

    fun clearCache() {
        entries.clear()
        statuses.value = emptyList()
    }
}

/** Resultado tipado de un catálogo; distingue fallo de una respuesta vacía. */
data class CatalogPage(val metas: List<JSONObject>, val hasMore: Boolean)
