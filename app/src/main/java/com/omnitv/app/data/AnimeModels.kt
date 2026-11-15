package com.omnitv.app.data

/**
 * Contrato común del catálogo de anime.
 *
 * Jikan (MyAnimeList) y Kitsu son **bases de datos de metadatos**, no addons
 * de streams: no entregan enlaces de vídeo. Sus fichas se normalizan a
 * [Video] con ids estables `mal:{id}` / `kitsu:{id}` y se registran en
 * [CatalogRepository]. La reproducción sigue resolviéndose con los addons y
 * puentes configurados; cuando Jikan aporta un `tt…` externo se usa como id
 * de stream para maximizar la compatibilidad.
 */
internal data class AnimePage(
    val videos: List<Video> = emptyList(),
    val status: Int = 0
)

internal data class AnimeEpisodePage(
    val episodes: List<Episode> = emptyList(),
    val hasMore: Boolean = false,
    val status: Int = 0
)

internal val AnimePage.ok: Boolean get() = status in 200..299 && videos.isNotEmpty()
internal val AnimeEpisodePage.ok: Boolean get() = status in 200..299 && episodes.isNotEmpty()

/** Ids, clasificaciones por edad y tipos alternativos de Stremio para anime. */
internal object AnimeIds {
    const val JIKAN = "mal:"
    const val KITSU = "kitsu:"
    const val SOURCE_JIKAN = "Jikan"
    const val SOURCE_KITSU = "Kitsu"

    private val prefixes = listOf(JIKAN, KITSU)
    private val numeric = Regex("^\\d+$")
    private val imdb = Regex("tt\\d{5,}")

    fun isAnime(id: String): Boolean = prefixes.any { id.substringBefore('~').startsWith(it) }

    fun source(id: String): String? = when {
        id.startsWith(JIKAN) -> SOURCE_JIKAN
        id.startsWith(KITSU) -> SOURCE_KITSU
        else -> null
    }

    /** Id numérico del proveedor, incluso si la cadena trae temporada/episodio. */
    fun providerId(id: String): String? {
        val parts = id.substringBefore('~').split(':')
        if (parts.size < 2) return null
        if (parts[0] != "mal" && parts[0] != "kitsu") return null
        return parts[1].takeIf { numeric.matches(it) }
    }

    /**
     * Temporada/episodio de un id `mal:123:1:2` o `kitsu:123:1:2`.
     * Un id sin sufijo (`mal:123`) no representa un episodio.
     */
    fun episodeParts(id: String): Pair<Int, Int>? {
        val clean = id.substringBefore('~')
        if (!isAnime(clean)) return null
        val parts = clean.split(':')
        if (parts.size < 4) return null
        val season = parts[parts.size - 2].toIntOrNull() ?: return null
        val episode = parts[parts.size - 1].toIntOrNull() ?: return null
        return (season to episode).takeIf { season >= 0 && episode > 0 }
    }

    /** `tt…` de un enlace externo de Jikan, si existe. */
    fun imdbFromUrl(url: String): String? = imdb.find(url)?.value

    /** Algunos addons de anime anuncian `type: anime` en vez de `series`. */
    fun alternateType(type: String): String? = when (type.lowercase()) {
        "series" -> "anime"
        "anime" -> "series"
        else -> null
    }

    /** Id de stream de un episodio: IMDb cuando se conoce, si no el id nativo. */
    fun episodeStreamId(parentId: String, imdbId: String?, season: Int, number: Int): String {
        val prefix = imdbId?.takeIf { it.startsWith("tt") } ?: parentId.substringBefore('~')
        return "$prefix:$season:$number"
    }
}

/** Equivalencia de clasificaciones de Jikan/Kitsu a las etiquetas de la app. */
internal object AnimeAgeRating {
    fun jikan(raw: String): String {
        val value = raw.trim()
        return when {
            value.isEmpty() -> ""
            value.contains("rx", ignoreCase = true) -> "18+"
            value.startsWith("g", ignoreCase = true) -> "TP"
            value.startsWith("pg-13", ignoreCase = true) -> "12+"
            value.startsWith("pg", ignoreCase = true) -> "7+"
            value.startsWith("r+", ignoreCase = true) -> "18+"
            value.startsWith("r", ignoreCase = true) -> "16+"
            else -> ""
        }
    }

    fun kitsu(raw: String): String = when (raw.trim().uppercase()) {
        "G" -> "TP"
        "PG" -> "7+"
        "R" -> "16+"
        "R18" -> "18+"
        else -> ""
    }

    /** Sin contenido adulto en el catálogo, aunque la API olvide filtrarlo. */
    fun isAdultJikan(rating: String, explicitGenres: List<String>): Boolean =
        rating.contains("rx", ignoreCase = true) ||
            explicitGenres.any { it.contains("hentai", ignoreCase = true) || it.contains("erotic", ignoreCase = true) }

    fun isAdultKitsu(ageRating: String, nsfw: Boolean): Boolean =
        nsfw || ageRating.trim().equals("R18", ignoreCase = true)

    /** Perfil infantil: solo TP/7+, igual que [CatalogRepository]. */
    fun visible(video: Video, profile: Profile?): Boolean =
        video.type == VideoType.ANIME &&
            (profile?.isKids != true || video.certification == "TP" || video.certification == "7+")
}
