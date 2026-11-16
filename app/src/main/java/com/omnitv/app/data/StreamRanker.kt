package com.omnitv.app.data

/**
 * Orden de fuentes de reproducción: la mejor calidad primero.
 *
 * Inspirado en el «Stream ranking» de VortX (VortXTV/VortX), reducido a lo que
 * Omni TV puede aplicar sin conocer el tipo de addon: resolución detectada en
 * [StreamSource.quality] + etiqueta, y la preferencia «Calidad preferida» de
 * los Ajustes ([SettingsStore.preferredQuality]), que hasta ahora se guardaba
 * pero no tenía efecto alguno.
 *
 * Es lógica pura y estable: a igualdad de puntuación se conserva el orden de
 * los addons/dentro del addon. A diferencia del ranker de VortX NO se puntúan
 * HDR, audio, seeders ni «remux»: los addons de Omni TV no exponen esos datos
 * de forma fiable, y puntuarlos a ciegas sería inventar calidad.
 */
object StreamRanker {

    private const val PREFERRED_BONUS = 1_000
    private const val UNKNOWN_SCORE = 15

    /** Puntuación de resolución a partir de cualquier texto («2160p», «4K»…). */
    fun scoreText(text: String): Int {
        val t = text.lowercase()
        return when {
            "2160" in t || "4k" in t || "uhd" in t -> 40
            "1440" in t || "2k" in t || "qhd" in t -> 35
            "1080" in t || "fhd" in t -> 30
            "720" in t -> 20
            "480" in t -> 10
            "360" in t -> 5
            " hd" in " $t" || t == "hd" -> 20
            "sd" in t -> 10
            else -> UNKNOWN_SCORE
        }
    }

    /** Puntuación de una fuente: gana su etiqueta de calidad sobre la descripción. */
    fun score(source: StreamSource): Int {
        val fromQuality = source.quality.takeIf { it.isNotBlank() }?.let(::scoreText)
        return fromQuality ?: scoreText(source.label)
    }

    private fun preferredTarget(preferred: String): Int? = when (preferred) {
        "4K" -> 40
        "1080p" -> 30
        "720p" -> 20
        "480p" -> 10
        else -> null // "Auto": sin bonus; el orden es puramente por calidad
    }

    /**
     * Ordena mejor-primero y de forma estable. Con una preferencia concreta
     * (p. ej. «720p»), las fuentes que coinciden la superan a todo lo demás;
     * el resto conserva su orden relativo dentro de cada grupo.
     */
    fun sort(sources: List<StreamSource>, preferred: String = SettingsStore.preferredQuality): List<StreamSource> {
        val target = preferredTarget(preferred)
        if (target == null && sources.size < 2) return sources
        return sources.sortedByDescending { source ->
            val base = score(source)
            if (target != null && base == target) base + PREFERRED_BONUS else base
        }
    }
}
