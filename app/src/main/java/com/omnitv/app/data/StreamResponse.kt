package com.omnitv.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Normaliza las respuestas HTTP de Stremio, Omni Streams y el puente Nuvio. */
internal object StreamResponse {
    fun parse(json: Any?, fallbackName: String): List<StreamLink> {
        val array = when (json) {
            is JSONArray -> json
            is JSONObject -> json.optJSONArray("streams") ?: json.optJSONArray("results") ?: json.optJSONArray("videos")
            else -> null
        } ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val hints = item.optJSONObject("behaviorHints")
                if (hints?.optBoolean("notWebReady", false) == true) continue
                val url = item.optString("url").ifBlank { item.optString("externalUrl") }.ifBlank { item.optString("link") }.trim()
                if (!NetPolicy.isAllowed(url)) continue // infoHash, magnet, ytId y HTTP público no son fuentes HTTP
                val rawName = item.optString("name").ifBlank { item.optString("label") }
                    .ifBlank { item.optString("provider") }.ifBlank { fallbackName }
                val title = item.optString("title")
                val quality = item.optString("quality").ifBlank { item.optString("resolution") }.ifBlank {
                    listOf("2160p", "1440p", "1080p", "720p", "480p").firstOrNull { it in "$rawName $title" }.orEmpty()
                }
                val label = listOf(rawName, title.lineSequence().firstOrNull().orEmpty())
                    .filter { it.isNotBlank() }.distinct().joinToString(" · ").take(80)
                val headers = parseHeaders(hints?.optJSONObject("proxyHeaders")?.optJSONObject("request"))
                    .ifEmpty { parseHeaders(item.optJSONObject("headers")) }
                add(StreamLink(label.ifBlank { fallbackName }, url, quality, format(url, item.optString("format")), headers))
            }
        }.distinctBy { it.url }
    }

    fun format(url: String, declared: String = ""): StreamFormat {
        val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault(url.lowercase())
        return when {
            path.endsWith(".m3u8") || declared.equals("hls", true) -> StreamFormat.HLS
            path.endsWith(".mpd") || declared.equals("dash", true) || declared.equals("mpd", true) -> StreamFormat.DASH
            else -> StreamFormat.MP4
        }
    }

    /** Solo cabeceras de petición HTTP; no cookies de otros dominios ni saltos de línea. */
    fun parseHeaders(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        json.keys().forEach { key ->
            val value = json.optString(key)
            if (key.matches(Regex("[A-Za-z0-9-]+")) && value.isNotBlank() && '\r' !in value && '\n' !in value &&
                key.lowercase() !in setOf("host", "cookie", "content-length", "connection", "transfer-encoding") && result.size < 16) {
                result[key] = value.take(2048)
            }
        }
        return result
    }
}
