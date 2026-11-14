package com.omnitv.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser tolerante de manifest.json de Stremio — soporta todas las variantes
 * que circulan (Cinemeta, Nuvio, Torrentio, Comet, AIOStreams, etc.).
 *
 * Un manifest puede traer:
 * - `resources: ["catalog","meta","stream"]`  o
 * - `resources: [{name:"stream", types:["movie"], idPrefixes:["tt"]}, "catalog"]`
 * - `catalogs: [{type:"movie", id:"top", name:"…", extra:[{name:"search",isRequired:false}]}]`
 * - `types: ["movie","series"]`, `idPrefixes: ["tt","tmdb"]`, `behaviorHints:{adult:false}`
 */
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
    fun isCatalogAddon(): Boolean = supportsResource("catalog") && catalogs.isNotEmpty()
    fun isStreamAddon(): Boolean = supportsResource("stream")
    fun isMetaAddon(): Boolean = supportsResource("meta")

    fun supportsType(requestedType: String): Boolean {
        if (types.isEmpty()) return true
        return requestedType in types
    }

    fun supportsId(id: String): Boolean {
        if (idPrefixes.isEmpty()) return true
        return idPrefixes.any { prefix -> id.startsWith(prefix) }
    }
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
    /** El catálogo acepta el extra `key` (ej: search, genre, skip). */
    fun supportsExtra(key: String): Boolean {
        if (extra.any { it.name == key }) return true
        if (extraSupported.contains(key)) return true
        if (extraRequired.contains(key)) return true
        // legacy: si no declara extra, algunos addons aceptan search igual — tratar como soportado si es search/skip
        if (extra.isEmpty() && extraSupported.isEmpty() && extraRequired.isEmpty()) return key in setOf("search", "genre", "skip")
        return false
    }
}

data class StremioExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
    val optionsLimit: Int = 0
)

object StremioManifestParser {

    fun parse(json: JSONObject): StremioManifest {
        val id = json.optString("id")
        val version = json.optString("version", "0.0.0")
        val name = json.optString("name", id.ifEmpty { "Addon" })
        val description = json.optString("description", "")
        val logo = json.optString("logo").takeIf { it.isNotBlank() }

        val resources = parseResources(json.optJSONArray("resources"))
        val catalogs = parseCatalogs(json.optJSONArray("catalogs"))
        val addonCatalogs = parseCatalogs(json.optJSONArray("addonCatalogs"))
        val types = json.optJSONArray("types").toStringList()
        val idPrefixes = json.optJSONArray("idPrefixes").toStringList()
        val behaviorHints = json.optJSONObject("behaviorHints")?.let { obj ->
            buildMap { obj.keys().forEach { k -> obj.optString(k).takeIf { it.isNotBlank() }?.let { put(k, it) } } }
        } ?: emptyMap()

        return StremioManifest(
            id = id,
            version = version,
            name = name,
            description = description,
            logo = logo,
            resources = resources,
            catalogs = catalogs,
            types = types,
            idPrefixes = idPrefixes,
            behaviorHints = behaviorHints,
            addonCatalogs = addonCatalogs,
            raw = json
        )
    }

    private fun parseResources(arr: JSONArray?): List<StremioResource> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                when (val v = arr.opt(i)) {
                    is String -> add(StremioResource(name = v))
                    is JSONObject -> {
                        val name = v.optString("name").ifEmpty { v.optString("resource") }
                        if (name.isBlank()) continue
                        add(
                            StremioResource(
                                name = name,
                                types = v.optJSONArray("types").toStringList(),
                                idPrefixes = v.optJSONArray("idPrefixes").toStringList()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun parseCatalogs(arr: JSONArray?): List<StremioCatalog> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val type = obj.optString("type")
                val id = obj.optString("id")
                if (id.isBlank() || type.isBlank()) continue
                val name = obj.optString("name", id)
                val extra = parseExtras(obj.optJSONArray("extra"))
                val extraSupported = obj.optJSONArray("extraSupported").toStringList()
                val extraRequired = obj.optJSONArray("extraRequired").toStringList()
                add(StremioCatalog(type, id, name, extra, extraSupported, extraRequired, obj))
            }
        }
    }

    private fun parseExtras(arr: JSONArray?): List<StremioExtra> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                if (name.isBlank()) continue
                add(
                    StremioExtra(
                        name = name,
                        isRequired = obj.optBoolean("isRequired", false),
                        options = obj.optJSONArray("options").toStringList(),
                        optionsLimit = obj.optInt("optionsLimit", 0)
                    )
                )
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
    }
}
