package com.omnitv.app.data

/**
 * Catálogo remoto en memoria. No contiene películas de muestra, servidores
 * inventados ni progreso ficticio. La navegación guarda el objeto Video completo
 * en la ruta; este repositorio es el respaldo para ids y episodios conocidos.
 */
object CatalogRepository {
    private const val MAX_ENTRIES = 1000
    private val remoteCache = object : LinkedHashMap<String, Video>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Video>?): Boolean = size > MAX_ENTRIES
    }

    /** Fusiona sin borrar episodios/reparto ya conocidos cuando llega una tarjeta. */
    fun registerRemote(video: Video) {
        if (video.id.isBlank()) return
        synchronized(remoteCache) {
            val old = remoteCache[video.id]
            val merged = if (old == null) video else video.copy(
                description = video.description.ifBlank { old.description },
                backdropUrl = video.backdropUrl.ifBlank { old.backdropUrl },
                cardUrl = video.cardUrl.ifBlank { old.cardUrl },
                cast = video.cast.ifEmpty { old.cast },
                episodes = video.episodes.ifEmpty { old.episodes },
                metaAddonUrl = video.metaAddonUrl ?: old.metaAddonUrl,
                remoteType = video.remoteType ?: old.remoteType
            )
            remoteCache[video.id] = merged
        }
    }

    /** Conserva el id de navegación (incluido el sufijo de episodio). */
    fun registerNavigation(video: Video) {
        if (video.id.isBlank()) return
        synchronized(remoteCache) { remoteCache[video.id] = video }
    }

    fun catalogFor(profile: Profile?): List<Video> = synchronized(remoteCache) {
        remoteCache.values.filter { it.type == VideoType.MOVIE && visible(it, profile) }.distinctBy { it.id }
    }

    fun search(query: String, kidsOnly: Boolean = false): List<Video> {
        val q = SearchText.normalize(query)
        if (q.isEmpty()) return emptyList()
        return synchronized(remoteCache) { remoteCache.values.toList() }
            .filter { !kidsOnly || it.certification == "TP" || it.certification == "7+" }
            .filter { video ->
                SearchText.matches(video.title, q) || video.genres.any { SearchText.matches(it, q) } ||
                    SearchText.matches(video.description, q) || video.cast.any { SearchText.matches(it, q) }
            }.distinctBy { it.id }
    }

    /** Sin fallback: si no hay título remoto, la portada muestra estado vacío. */
    fun featuredFor(profile: Profile?): Video? = synchronized(remoteCache) {
        remoteCache.values.firstOrNull { visible(it, profile) }
    }

    fun rowsFor(profile: Profile?): List<MediaRow> {
        val rows = mutableListOf<MediaRow>()
        val myList = profile?.myList.orEmpty().mapNotNull { find(it) }.filter { visible(it, profile) }
        val continuing = profile?.progress.orEmpty().mapNotNull { (id, fraction) ->
            find(id)?.takeIf { visible(it, profile) }?.copy(progress = fraction)
        }
        if (myList.isNotEmpty()) rows += MediaRow("Mi lista", myList.distinctBy { it.id })
        if (continuing.isNotEmpty()) rows += MediaRow("Continuar viendo", continuing.distinctBy { it.id })
        return rows
    }

    fun find(id: String): Video? {
        if ('~' in id) {
            val parent = find(id.substringBefore('~')) ?: return null
            val episodeId = id.substringAfter('~')
            val episode = parent.episodes.firstOrNull { it.id == episodeId || it.streamId == episodeId } ?: return null
            return episodeVideo(parent, episode)
        }
        return synchronized(remoteCache) { remoteCache[id] }
    }

    fun episodeVideo(parent: Video, episode: Episode): Video {
        val sources = if (episode.videoUrl.isNotBlank() && NetPolicy.isAllowed(episode.videoUrl)) {
            listOf(StreamSource(episode.title.ifBlank { "Episodio" }, episode.videoUrl, StreamResponse.format(episode.videoUrl)))
        } else emptyList()
        return parent.copy(
            id = "${parent.id}~${episode.id}",
            title = "${parent.title} · ${episode.title}",
            description = episode.description,
            cardUrl = episode.thumbnailUrl.ifBlank { parent.cardUrl },
            durationMinutes = episode.durationMinutes,
            videoUrl = sources.firstOrNull()?.url.orEmpty(),
            streamFormat = sources.firstOrNull()?.format ?: StreamFormat.MP4,
            sources = sources,
            badge = null, progress = 0f, episodes = emptyList(),
            remoteType = "series",
            streamId = episode.streamId?.takeIf { it.isNotBlank() } ?: "${parent.id}:${episode.season}:${episode.number}"
        )
    }

    /** Siguiente episodio real; no se salta a un título relacionado por género. */
    fun nextEpisode(id: String): Video? {
        if ('~' !in id) return null
        val parent = find(id.substringBefore('~')) ?: return null
        val episodeId = id.substringAfter('~')
        val index = parent.episodes.indexOfFirst { it.id == episodeId || it.streamId == episodeId }
        if (index < 0) return null
        return parent.episodes.getOrNull(index + 1)?.let { episodeVideo(parent, it) }
    }

    fun related(id: String, limit: Int = 6): List<Video> {
        nextEpisode(id)?.let { return listOf(it) }
        val current = find(id) ?: return emptyList()
        return synchronized(remoteCache) { remoteCache.values.toList() }
            .filter { it.id != id && it.id.substringBefore('~') == it.id && it.genres.any { g -> g in current.genres } }
            .distinctBy { it.id }.take(limit)
    }

    private fun visible(video: Video, profile: Profile?): Boolean =
        profile?.isKids != true || video.certification == "TP" || video.certification == "7+"
}
