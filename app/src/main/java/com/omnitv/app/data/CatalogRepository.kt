package com.omnitv.app.data

/**
 * Repositorio local de Omni TV — catálogo profesional.
 *
 * Ya no contiene contenido de demostración. Actúa como:
 *  - Caché en memoria de títulos remotos (Cinemeta / Nuvio / addons)
 *  - Orquestador de filas personales ("Mi lista", "Continuar viendo")
 *  - Resolución de episodios y títulos relacionados sobre el catálogo real
 *
 * Todo el catálogo visible proviene de [RemoteCatalog] (Cinemeta + Nuvio +
 * addons configurados por el usuario). Cuando no hay contenido remoto, la UI
 * muestra estados vacíos profesionales en lugar de contenido falso.
 */
object CatalogRepository {

    /** Caché de títulos remotos indexados por id, para resolución rápida. */
    private val remoteCache = java.util.concurrent.ConcurrentHashMap<String, Video>()

    /** Registra un título remoto (se sobrescribe con la ficha completa). */
    fun registerRemote(video: Video) {
        remoteCache[video.id] = video
    }

    /** Limpia el caché (usado en logout o cambio de cuenta). */
    fun clearCache() = remoteCache.clear()

    /** Acceso de solo lectura al caché para diagnósticos. */
    fun cachedIds(): Set<String> = remoteCache.keys

    // -----------------------------------------------------------------------
    // Compatibilidad: catálogo local vacío en la versión profesional
    // -----------------------------------------------------------------------

    /**
     * Catálogo local: vacío en producción. Se mantiene como lista vacía para
     * compatibilidad binaria; el catálogo real vive en [RemoteCatalog].
     */
    val catalog: List<Video> = emptyList()

    /** Contenido disponible para un perfil (filtrado infantil). */
    fun catalogFor(profile: Profile?): List<Video> {
        // Sin catálogo local, el catálogo es puramente remoto.
        // El filtrado infantil se aplica aquí si el remoto trae certificación.
        if (profile?.isKids != true) return emptyList()
        // Perfiles infantiles: solo títulos TP/7+ del caché remoto
        return remoteCache.values.filter {
            it.certification == "TP" || it.certification == "7+" || it.certification.isBlank()
        }
    }

    /**
     * Búsqueda local (actualmente delega en remoto; el índice local es el caché).
     * Busca en títulos, géneros, descripción y reparto del caché remoto.
     */
    fun search(query: String, kidsOnly: Boolean = false): List<Video> {
        val q = SearchText.normalize(query)
        if (q.isEmpty()) return emptyList()
        return remoteCache.values
            .filter { video -> !kidsOnly || video.certification == "TP" || video.certification == "7+" || video.certification.isBlank() }
            .filter { video ->
                SearchText.matches(video.title, q) ||
                    video.genres.any { SearchText.matches(it, q) } ||
                    SearchText.matches(video.description, q) ||
                    video.cast.any { SearchText.matches(it, q) }
            }
    }

    /**
     * Título destacado para el héroe. En producción proviene de [RemoteCatalog];
     * este método retorna null si no hay catálogo remoto cargado.
     */
    fun featuredFor(profile: Profile?): Video? = null

    /**
     * Filas personales para la pantalla de inicio.
     * Solo "Mi lista" y "Continuar viendo" (propias de cada perfil).
     * El resto de filas las aporta [RemoteCatalog].
     */
    fun rowsFor(profile: Profile?): List<MediaRow> {
        val rows = mutableListOf<MediaRow>()
        val kidsOnly = profile?.isKids == true

        val myList = profile?.myList.orEmpty()
            .mapNotNull { id -> find(id) }
            .filter { !kidsOnly || it.certification == "TP" || it.certification == "7+" || it.certification.isBlank() }
        if (myList.isNotEmpty()) {
            rows += MediaRow("Mi lista", myList)
        }

        val continueItems = profile?.progress.orEmpty()
            .mapNotNull { (id, _) -> find(id) }
            .filter { !kidsOnly || it.certification == "TP" || it.certification == "7+" || it.certification.isBlank() }
        if (continueItems.isNotEmpty()) {
            // Inyecta el progreso real del perfil en cada tarjeta
            val withProgress = continueItems.map { video ->
                val fraction = profile?.progress?.get(video.id) ?: video.progress
                video.copy(progress = fraction)
            }
            rows += MediaRow("Continuar viendo", withProgress)
        }

        return rows
    }

    // -----------------------------------------------------------------------
    // Resolución de títulos y episodios
    // -----------------------------------------------------------------------

    /**
     * Busca un título por id. Los episodios se referencian como
     * `idDelTítulo~idDelEpisodio` y se devuelven como [Video] transitorio
     * para que el reproductor y el auto-next funcionen sin cambios.
     */
    fun find(id: String): Video? {
        if ('~' in id) {
            val parentId = id.substringBefore('~')
            val episodeId = id.substringAfter('~')
            val parent = find(parentId) ?: return null
            val episode = parent.episodes.firstOrNull {
                it.id == episodeId || it.streamId == episodeId
            } ?: return null
            return episodeVideo(parent, episode)
        }
        return remoteCache[id]
    }

    /** Convierte un episodio en un [Video] transitorio reproducible. */
    fun episodeVideo(parent: Video, episode: Episode): Video {
        // Episodios remotos sin stream propio: se conserva el id Stremio
        // del episodio para que StreamHub pida los enlaces a los addons.
        if (episode.videoUrl.isEmpty()) {
            return parent.copy(
                id = "${parent.id}~${episode.id}",
                title = "${parent.title} · ${episode.title}",
                description = episode.description.ifBlank { parent.description },
                backdropUrl = episode.thumbnailUrl.ifEmpty { parent.backdropUrl },
                cardUrl = episode.thumbnailUrl.ifEmpty { parent.cardUrl },
                durationMinutes = episode.durationMinutes,
                videoUrl = "",
                streamFormat = StreamFormat.MP4,
                sources = emptyList(),
                badge = null,
                progress = 0f,
                episodes = emptyList(),
                streamId = episode.streamId ?: "${parent.id}:${episode.season}:${episode.number}"
            )
        }
        // Episodio con URL directa: fuente única (sin espejos demo)
        return parent.copy(
            id = "${parent.id}~${episode.id}",
            title = "${parent.title} · ${episode.title}",
            description = episode.description,
            cardUrl = episode.thumbnailUrl,
            durationMinutes = episode.durationMinutes,
            videoUrl = episode.videoUrl,
            streamFormat = episode.videoUrl.let { if (it.contains(".m3u8")) StreamFormat.HLS else StreamFormat.MP4 },
            sources = listOf(StreamSource("Servidor 1", episode.videoUrl, StreamFormat.MP4)),
            badge = null,
            progress = 0f,
            episodes = emptyList()
        )
    }

    /** Títulos relacionados: comparten al menos un género. */
    fun related(id: String, limit: Int = 6): List<Video> {
        if ('~' in id) {
            val parentId = id.substringBefore('~')
            val episodeId = id.substringAfter('~')
            val parent = find(parentId) ?: return emptyList()
            val index = parent.episodes.indexOfFirst { it.id == episodeId || it.streamId == episodeId }
            if (index < 0) return emptyList()
            return parent.episodes.drop(index + 1).take(limit).map { episodeVideo(parent, it) }
        }
        val current = find(id) ?: return emptyList()
        if (current.genres.isEmpty()) return emptyList()
        return remoteCache.values
            .filter { it.id != id && it.genres.any { g -> g in current.genres } }
            .distinctBy { it.id }
            .take(limit)
    }
}
