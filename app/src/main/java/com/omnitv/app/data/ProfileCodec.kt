package com.omnitv.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Codificación tolerante del almacenamiento de perfiles.
 *
 * Un registro dañado no debe hacer que desaparezcan todos los perfiles
 * válidos. Los campos variables se validan individualmente y los registros
 * imposibles se omiten.
 */
internal object ProfileCodec {
    internal const val MAX_PROFILES = 50
    private const val MAX_PROGRESS_ITEMS = 2_000
    private const val MAX_LIST_ITEMS = 2_000
    private const val MIN_PROGRESS = 0.02f
    private const val COMPLETED_PROGRESS = 0.95f

    fun encode(profiles: List<Profile>): String {
        val array = JSONArray()
        profiles.take(MAX_PROFILES).forEach { profile ->
            val progress = JSONObject()
            profile.progress.entries
                .asSequence()
                .filter { (id, value) ->
                    id.isNotBlank() && value.isFinite() &&
                        value >= MIN_PROGRESS && value < COMPLETED_PROGRESS
                }
                .take(MAX_PROGRESS_ITEMS)
                .forEach { (id, value) -> progress.put(id, value.toDouble()) }
            val myList = JSONArray(
                profile.myList.asSequence()
                    .filter { it.isNotBlank() }
                    .take(MAX_LIST_ITEMS)
                    .toList()
            )
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("avatarIndex", profile.avatarIndex.coerceIn(0, AVATAR_COUNT - 1))
                    .put("isKids", profile.isKids)
                    .put("autoPlayNext", profile.autoPlayNext)
                    .put("progress", progress)
                    .put("myList", myList)
            )
        }
        return array.toString()
    }

    fun decode(json: String): List<Profile> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val seenIds = mutableSetOf<String>()
        val decoded = mutableListOf<Profile>()

        for (i in 0 until minOf(array.length(), MAX_PROFILES)) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim().takeIf { it.isNotEmpty() } ?: continue
            val name = obj.optString("name").trim().takeIf { it.isNotEmpty() } ?: continue
            if (id in seenIds) continue

            val progressObject = obj.optJSONObject("progress") ?: JSONObject()
            val progress = buildMap {
                val keys = progressObject.keys()
                var accepted = 0
                while (keys.hasNext() && accepted < MAX_PROGRESS_ITEMS) {
                    val key = keys.next().takeIf { it.isNotBlank() } ?: continue
                    val value = runCatching { progressObject.getDouble(key).toFloat() }.getOrNull()
                        ?: continue
                    if (!value.isFinite() || value < MIN_PROGRESS || value >= COMPLETED_PROGRESS) continue
                    put(key, value)
                    accepted++
                }
            }

            val listArray = obj.optJSONArray("myList")
            val myList = buildSet {
                if (listArray != null) {
                    for (j in 0 until minOf(listArray.length(), MAX_LIST_ITEMS)) {
                        val item = listArray.optString(j).trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            }

            decoded += Profile(
                id = id,
                name = name.take(48),
                avatarIndex = obj.optInt("avatarIndex", 0).coerceIn(0, AVATAR_COUNT - 1),
                isKids = obj.optBoolean("isKids", false),
                autoPlayNext = obj.optBoolean("autoPlayNext", true),
                progress = progress,
                myList = myList
            )
            seenIds += id
        }
        return decoded
    }
}
