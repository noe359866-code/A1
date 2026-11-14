package com.omnitv.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

/** Manifiesto Stremio tipado. Las restricciones pueden ser globales o por recurso. */
data class StremioManifest(
    val id: String,
    val version: String,
    val name: String,
    val description: String,
    val logo: String?,
    val resources: List<StremioResource>,
    val catalogs: List<StremioCatalog>,
    val types: List<String>,
    val idPrefixes: List<String>,
    val behaviorHints: Map<String, String>,
    val addonCatalogs: List<StremioCatalog> = emptyList(),
    val raw: JSONObject
) {
    fun supportsResource(name: String): Boolean = resources.any { it.name == name }
    fun isCatalogAddon(): Boolean = catalogs.any { supports("catalog", it.type, "") }
    fun isStreamAddon(): Boolean = resources.any { it.name == "stream" }
    fun isMetaAddon(): Boolean = resources.any { it.name == "meta" }
    fun supportsType(requestedType: String): Boolean = types.isEmpty() || requestedType in types
    fun supportsId(id: String): Boolean = matchesPrefix(id, idPrefixes)

    /** Comprueba conjuntamente el recurso, el tipo y los prefijos admitidos. */
    fun supports(resource: String, type: String, id: String): Boolean {
        if (!supportsType(type) || !supportsId(id)) return false
        return resources.any {
            it.name == resource &&
                (it.types.isEmpty() || type in it.types) &&
                matchesPrefix(id, it.idPrefixes)
        }
    }

    fun catalogFor(type: String, id: String): StremioCatalog? =
        catalogs.firstOrNull { it.type == type && it.id == id && supports("catalog", type, "") }

    /** Un extra obligatorio sin valor/opción hace que la petición no sea válida. */
    fun catalogExtras(catalog: StremioCatalog, requested: Map<String, String> = emptyMap()): Map<String, String>? =
        catalog.normalizedExtras(requested)

    private fun matchesPrefix(id: String, prefixes: List<String>): Boolean =
        id.isEmpty() || prefixes.isEmpty() || prefixes.any { id.startsWith(it) }
}

data class StremioResource(
    val name: String,
    val types: List<String> = emptyList(),
    val idPrefixes: List<String> = emptyList()
)

data class StremioCatalog(
    val type: String,
    val id: String,
    val name: String,
    val extra: List<StremioExtra> = emptyList(),
    val extraSupported: List<String> = emptyList(),
    val extraRequired: List<String> = emptyList(),
    val raw: JSONObject? = null
) {
    /** No se presupone search/skip: un addon debe declararlos. */
    fun supportsExtra(key: String): Boolean =
        extra.any { it.name == key } || key in extraSupported || key in extraRequired

    fun normalizedExtras(requested: Map<String, String> = emptyMap()): Map<String, String>? {
        val result = requested.filterKeys(::supportsExtra).filterValues { it.isNotBlank() }.toMutableMap()
        for (name in requiredExtras) {
            if (!result[name].isNullOrBlank()) continue
            val default = extra.firstOrNull { it.name == name }?.options?.firstOrNull()
            if (default.isNullOrBlank()) return null
            result[name] = default
        }
        return result
    }

    val requiredExtras: Set<String>
        get() = (extraRequired + extra.filter { it.isRequired }.map { it.name }).toSet()
}

data class StremioExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
    val optionsLimit: Int = 0
)

object StremioManifestParser {
    /** Valida lo indispensable; `scrapers[]` por sí solo NO es un addon Stremio. */
    fun validate(json: JSONObject): String? = when {
        json.optString("id").isBlank() -> "Falta id en el manifest"
        json.optString("name").isBlank() -> "Falta name en el manifest"
        json.optJSONArray("resources")?.length() == 0 || json.optJSONArray("resources") == null ->
            "Falta resources: ¿es un repositorio de plugins Nuvio?"
        parseResources(json.optJSONArray("resources")).isEmpty() -> "resources no contiene recursos válidos"
        json.optJSONArray("types")?.length() == 0 || json.optJSONArray("types") == null -> "Falta types en el manifest"
        else -> null
    }

    fun parse(json: JSONObject): StremioManifest {
        val id = json.optString("id")
        return StremioManifest(
            id = id,
            version = json.optString("version", "0.0.0"),
            name = json.optString("name", id.ifEmpty { "Addon" }),
            description = json.optString("description", ""),
            logo = json.optString("logo").takeIf { it.isNotBlank() },
            resources = parseResources(json.optJSONArray("resources")),
            catalogs = parseCatalogs(json.optJSONArray("catalogs")),
            types = json.optJSONArray("types").toStringList(),
            idPrefixes = json.optJSONArray("idPrefixes").toStringList(),
            behaviorHints = json.optJSONObject("behaviorHints")?.let { obj ->
                buildMap { obj.keys().forEach { key -> put(key, obj.optString(key)) } }
            }.orEmpty(),
            addonCatalogs = parseCatalogs(json.optJSONArray("addonCatalogs")),
            raw = json
        )
    }

    private fun parseResources(array: JSONArray?): List<StremioResource> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                when (val value = array.opt(i)) {
                    is String -> if (value.isNotBlank()) add(StremioResource(value))
                    is JSONObject -> {
                        val name = value.optString("name")
                        if (name.isNotBlank()) add(
                            StremioResource(name, value.optJSONArray("types").toStringList(), value.optJSONArray("idPrefixes").toStringList())
                        )
                    }
                }
            }
        }.distinctBy { it.name + it.types + it.idPrefixes }
    }

    private fun parseCatalogs(array: JSONArray?): List<StremioCatalog> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val type = obj.optString("type")
                val id = obj.optString("id")
                if (type.isBlank() || id.isBlank()) continue
                add(
                    StremioCatalog(
                        type, id, obj.optString("name", id), parseExtras(obj.optJSONArray("extra")),
                        obj.optJSONArray("extraSupported").toStringList(), obj.optJSONArray("extraRequired").toStringList(), obj
                    )
                )
            }
        }
    }

    private fun parseExtras(array: JSONArray?): List<StremioExtra> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                if (name.isNotBlank()) add(
                    StremioExtra(name, obj.optBoolean("isRequired", false), obj.optJSONArray("options").toStringList(), obj.optInt("optionsLimit", 0))
                )
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = optString(i).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }
}

/** Conserva el prefijo de configuración del addon (p.ej. /opciones/manifest.json). */
object StremioUrl {
    fun normalize(input: String): String? {
        val value = input.trim().let { if (it.startsWith("stremio://")) "https://" + it.removePrefix("stremio://") else it }
        if (!NetPolicy.isAllowed(value)) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        return value.trimEnd('/').let { if (it.endsWith("/manifest.json")) it else "$it/manifest.json" }
    }

    fun base(manifestUrl: String): String = manifestUrl.trimEnd('/').removeSuffix("/manifest.json").trimEnd('/')

    fun encodeSegment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** Formato real Stremio: /catalog/movie/top/genre=Action&skip=20.json. */
    fun catalog(base: String, type: String, id: String, extras: Map<String, String> = emptyMap()): String {
        val extra = extras.entries.sortedBy { it.key }.joinToString("&") { "${encodeSegment(it.key)}=${encodeSegment(it.value)}" }
        return "${base.trimEnd('/')}/catalog/${encodeSegment(type)}/${encodeSegment(id)}" +
            (if (extra.isEmpty()) "" else "/$extra") + ".json"
    }

    fun resource(base: String, resource: String, type: String, id: String): String =
        "${base.trimEnd('/')}/${encodeSegment(resource)}/${encodeSegment(type)}/${encodeSegment(id)}.json"
}
