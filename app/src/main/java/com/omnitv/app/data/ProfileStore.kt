package com.omnitv.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Almacén de perfiles, persistido en SharedPreferences como JSON.
 *
 * Sin dependencias externas: usa org.json del SDK de Android.
 * El estado es observable por Compose ([profiles] / [currentProfileId]).
 */
object ProfileStore {

    private const val PREFS = "omni_tv_profiles"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_CURRENT = "current_profile"

    private var prefs: SharedPreferences? = null

    var profiles by mutableStateOf(defaultProfiles())
        private set

    var currentProfileId by mutableStateOf<String?>(null)
        private set

    /** Perfil activo (o null si aún no se ha elegido). */
    val currentProfile: Profile?
        get() = profiles.firstOrNull { it.id == currentProfileId }

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs!!.getString(KEY_PROFILES, null)
        if (json != null) {
            runCatching { profiles = parse(json) }
                .onFailure { profiles = defaultProfiles() }
        }
        currentProfileId = prefs!!.getString(KEY_CURRENT, null)
            ?.takeIf { id -> profiles.any { it.id == id } }
    }

    fun select(id: String) {
        currentProfileId = id
        prefs?.edit()?.putString(KEY_CURRENT, id)?.apply()
    }

    fun add(name: String, colorIndex: Int, isKids: Boolean) {
        profiles = profiles + Profile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Perfil" },
            colorIndex = colorIndex,
            isKids = isKids
        )
        persist()
    }

    fun update(profile: Profile) {
        profiles = profiles.map { if (it.id == profile.id) profile else it }
        persist()
    }

    /** No se puede eliminar el último perfil que queda. */
    fun delete(id: String) {
        if (profiles.size <= 1) return
        profiles = profiles.filterNot { it.id == id }
        if (currentProfileId == id) currentProfileId = null
        persist()
    }

    /**
     * Registra el progreso de reproducción del perfil actual.
     * Si el título se ha visto casi entero (≥ 95 %) o casi nada (< 2 %),
     * se elimina de "Continuar viendo".
     */
    fun setProgress(videoId: String, fraction: Float) {
        val current = currentProfile ?: return
        val progress = current.progress.toMutableMap()
        if (fraction >= 0.95f || fraction < 0.02f) {
            progress.remove(videoId)
        } else {
            progress[videoId] = fraction
        }
        update(current.copy(progress = progress))
    }

    /** Añade o quita un título de "Mi lista" del perfil actual. */
    fun toggleMyList(videoId: String) {
        val current = currentProfile ?: return
        val list = current.myList.toMutableSet()
        if (!list.add(videoId)) list.remove(videoId)
        update(current.copy(myList = list))
    }

    /* ------------------------------------------------------------------ */
    /*  Persistencia                                                      */
    /* ------------------------------------------------------------------ */

    private fun persist() {
        val p = prefs ?: return
        p.edit()
            .putString(KEY_PROFILES, serialize(profiles))
            .putString(KEY_CURRENT, currentProfileId)
            .apply()
    }

    private fun serialize(profiles: List<Profile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            val progress = JSONObject()
            profile.progress.forEach { (id, value) -> progress.put(id, value.toDouble()) }
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("colorIndex", profile.colorIndex)
                    .put("isKids", profile.isKids)
                    .put("progress", progress)
                    .put("myList", JSONArray(profile.myList.toList()))
            )
        }
        return array.toString()
    }

    private fun parse(json: String): List<Profile> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val progressObj = obj.optJSONObject("progress") ?: JSONObject()
                val progress = mutableMapOf<String, Float>()
                progressObj.keys().forEach { key ->
                    progress[key] = progressObj.getDouble(key).toFloat()
                }
                val listArr = obj.optJSONArray("myList")
                val myList = buildSet {
                    if (listArr != null) for (j in 0 until listArr.length()) add(listArr.getString(j))
                }
                add(
                    Profile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        colorIndex = obj.optInt("colorIndex", 0),
                        isKids = obj.optBoolean("isKids", false),
                        progress = progress,
                        myList = myList
                    )
                )
            }
        }
    }

    private fun defaultProfiles(): List<Profile> = listOf(
        Profile(
            id = "sofia",
            name = "Sofía",
            colorIndex = 6,
            progress = mapOf(
                "sintel" to 0.35f,
                "big-buck-bunny" to 0.72f,
                "blazes" to 0.18f
            ),
            myList = setOf("tears-of-steel", "joyrides")
        ),
        Profile(
            id = "marco",
            name = "Marco",
            colorIndex = 5,
            progress = mapOf(
                "bullrun" to 0.40f,
                "gti" to 0.22f
            )
        ),
        Profile(
            id = "peques",
            name = "Peques",
            colorIndex = 3,
            isKids = true,
            progress = mapOf(
                "big-buck-bunny" to 0.55f,
                "fun" to 0.30f
            )
        )
    )
}
