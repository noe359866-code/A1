package com.omnitv.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
            profiles = ProfileCodec.decode(json).ifEmpty { defaultProfiles() }
        }
        currentProfileId = prefs!!.getString(KEY_CURRENT, null)
            ?.takeIf { id -> profiles.any { it.id == id } }
    }

    fun select(id: String) {
        if (profiles.none { it.id == id }) return
        currentProfileId = id
        prefs?.edit()?.putString(KEY_CURRENT, id)?.apply()
    }

    fun add(name: String, avatarIndex: Int, isKids: Boolean, autoPlayNext: Boolean = true) {
        profiles = profiles + Profile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Perfil" },
            avatarIndex = avatarIndex,
            isKids = isKids,
            autoPlayNext = autoPlayNext
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
        if (videoId.isBlank() || !fraction.isFinite()) return
        val safeFraction = fraction.coerceIn(0f, 1f)
        val progress = current.progress.toMutableMap()
        if (safeFraction >= 0.95f || safeFraction < 0.02f) {
            progress.remove(videoId)
        } else {
            progress[videoId] = safeFraction
        }
        update(current.copy(progress = progress))
    }

    /** Borra todo el progreso de reproducción ("Continuar viendo") de un perfil. */
    fun clearProgress(profileId: String) {
        val target = profiles.firstOrNull { it.id == profileId } ?: return
        update(target.copy(progress = emptyMap()))
    }

    /** Añade o quita un título de "Mi lista" del perfil actual. */
    fun toggleMyList(videoId: String) {
        val current = currentProfile ?: return
        val list = current.myList.toMutableSet()
        if (!list.add(videoId)) list.remove(videoId)
        update(current.copy(myList = list))
    }

    /** Vacía completamente "Mi lista" de un perfil. */
    fun clearMyList(profileId: String) {
        val target = profiles.firstOrNull { it.id == profileId } ?: return
        update(target.copy(myList = emptySet()))
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

    private fun serialize(profiles: List<Profile>): String = ProfileCodec.encode(profiles)

    private fun defaultProfiles(): List<Profile> = listOf(
        Profile(id = "perfil-1", name = "Perfil 1", avatarIndex = 0)
    )
}
