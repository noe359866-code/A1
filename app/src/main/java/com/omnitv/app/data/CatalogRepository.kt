package com.omnitv.app.data

/**
 * Catálogo de demostración de Omni TV.
 *
 * Todos los flujos son públicos y se usan habitualmente como material de
 * prueba (Google sample bucket, Apple HLS bipbop y Mux):
 *  - MP4: https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/…
 *  - HLS: master.m3u8 de Apple devstreaming y Mux test-streams.
 *  - Imágenes: https://storage.googleapis.com/gtv-videos-bucket/sample/images/…
 */
object CatalogRepository {

    private const val IMG = "https://storage.googleapis.com/gtv-videos-bucket/sample/images"
    private const val VID = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample"

    /** Espejo del bucket de samples de Google (segundo servidor). */
    private const val VID_ALT = "https://storage.googleapis.com/gtv-videos-bucket/sample"

    /**
     * Multi-servidor estilo Xuper TV: «Servidor 1» es el enlace principal y
     * «Servidor 2» una fuente alternativa (espejo o flujo equivalente).
     */
    private fun withServers(video: Video): Video {
        val servers = buildList {
            add(StreamSource("Servidor 1", video.videoUrl, video.streamFormat))
            val mirror = video.videoUrl.replace(VID, VID_ALT)
            when {
                mirror != video.videoUrl ->
                    add(StreamSource("Servidor 2", mirror, video.streamFormat))
                video.id == "apple-hls" ->
                    add(StreamSource("Servidor 2", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", StreamFormat.HLS))
                video.id == "mux-hls" ->
                    add(StreamSource("Servidor 2", "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8", StreamFormat.HLS))
            }
        }
        return video.copy(sources = servers)
    }

    val catalog: List<Video> = listOf(
        Video(
            id = "sintel",
            title = "Sintel",
            description = "Una joven guerrera recorre un mundo hostil en busca del dragón que se llevó a su cría. " +
                "Un viaje épico de amor, pérdida y dragones con un final inolvidable.",
            backdropUrl = "$IMG/Sintel.jpg",
            cardUrl = "$IMG/Sintel.jpg",
            year = 2010,
            durationMinutes = 15,
            rating = 8.1,
            certification = "16+",
            quality = "4K HDR",
            genres = listOf("Aventura", "Animación", "Fantasía"),
            videoUrl = "$VID/Sintel.mp4",
            badge = "NUEVO",
            progress = 0.35f,
            cast = listOf("Halina Reijn", "Thom Hoffman", "Daniel Salguera")
        ),
        Video(
            id = "big-buck-bunny",
            title = "Big Buck Bunny",
            description = "Un gigantesco conejo de buen corazón se enfrenta a tres roedores matones en este clásico " +
                "de la animación libre. Diversión para toda la familia.",
            backdropUrl = "$IMG/BigBuckBunny.jpg",
            cardUrl = "$IMG/BigBuckBunny.jpg",
            year = 2008,
            durationMinutes = 10,
            rating = 8.4,
            certification = "TP",
            quality = "HD",
            genres = listOf("Animación", "Comedia", "Familia"),
            videoUrl = "$VID/BigBuckBunny.mp4",
            progress = 0.72f,
            cast = listOf("Jan Morgenstern")
        ),
        Video(
            id = "tears-of-steel",
            title = "Tears of Steel",
            description = "En un Ámsterdam futurista, un equipo de científicos intenta salvar el mundo de las máquinas " +
                "con la ayuda de un viejo recuerdo. Ciencia ficción rodada con efectos reales.",
            backdropUrl = "$IMG/TearsOfSteel.jpg",
            cardUrl = "$IMG/TearsOfSteel.jpg",
            year = 2012,
            durationMinutes = 12,
            rating = 7.9,
            certification = "12+",
            quality = "4K",
            genres = listOf("Ciencia ficción", "Acción"),
            videoUrl = "$VID/TearsOfSteel.mp4",
            badge = "ESTRENO",
            cast = listOf("Derek de Lint", "Sergio Hasselbaink", "Rogier Schippers")
        ),
        Video(
            id = "elephants-dream",
            title = "Elephants Dream",
            description = "Dos hombres exploran una máquina colosal y surrealista donde nada es lo que parece. " +
                "El primer cortometraje de la fundación Blender.",
            backdropUrl = "$IMG/ElephantsDream.jpg",
            cardUrl = "$IMG/ElephantsDream.jpg",
            year = 2006,
            durationMinutes = 11,
            rating = 7.5,
            certification = "7+",
            quality = "HD",
            genres = listOf("Fantasía", "Ciencia ficción"),
            videoUrl = "$VID/ElephantsDream.mp4",
            cast = listOf("Cas Jansen", "Tygo Gernandt")
        ),
        Video(
            id = "blazes",
            title = "For Bigger Blazes",
            description = "Vídeos y música al rojo vivo en todas tus pantallas. Demuestra de lo que es capaz tu televisor.",
            backdropUrl = "$IMG/ForBiggerBlazes.jpg",
            cardUrl = "$IMG/ForBiggerBlazes.jpg",
            year = 2013,
            durationMinutes = 1,
            rating = 6.8,
            certification = "TP",
            quality = "HD",
            genres = listOf("Demo", "Tecnología"),
            videoUrl = "$VID/ForBiggerBlazes.mp4",
            progress = 0.18f
        ),
        Video(
            id = "escapes",
            title = "For Bigger Escapes",
            description = "Escápate de la rutina donde quiera que estés. Entretenimiento sin límites.",
            backdropUrl = "$IMG/ForBiggerEscapes.jpg",
            cardUrl = "$IMG/ForBiggerEscapes.jpg",
            year = 2013,
            durationMinutes = 1,
            rating = 6.5,
            certification = "TP",
            quality = "HD",
            genres = listOf("Demo", "Viajes"),
            videoUrl = "$VID/ForBiggerEscapes.mp4"
        ),
        Video(
            id = "fun",
            title = "For Bigger Fun",
            description = "Diversión sin límites para toda la familia, en el sofá y más allá.",
            backdropUrl = "$IMG/ForBiggerFun.jpg",
            cardUrl = "$IMG/ForBiggerFun.jpg",
            year = 2013,
            durationMinutes = 1,
            rating = 6.4,
            certification = "TP",
            quality = "HD",
            genres = listOf("Demo", "Familia"),
            videoUrl = "$VID/ForBiggerFun.mp4"
        ),
        Video(
            id = "joyrides",
            title = "For Bigger Joyrides",
            description = "Aventuras sobre ruedas sin moverte del sofá. Sube el volumen y disfruta.",
            backdropUrl = "$IMG/ForBiggerJoyrides.jpg",
            cardUrl = "$IMG/ForBiggerJoyrides.jpg",
            year = 2013,
            durationMinutes = 1,
            rating = 6.6,
            certification = "7+",
            quality = "HD",
            genres = listOf("Demo", "Motor"),
            videoUrl = "$VID/ForBiggerJoyrides.mp4"
        ),
        Video(
            id = "meltdowns",
            title = "For Bigger Meltdowns",
            description = "Cuando todo se sale de control, tú sigues conectado. El espectáculo de la tecnología al límite.",
            backdropUrl = "$IMG/ForBiggerMeltdowns.jpg",
            cardUrl = "$IMG/ForBiggerMeltdowns.jpg",
            year = 2013,
            durationMinutes = 1,
            rating = 6.3,
            certification = "7+",
            quality = "HD",
            genres = listOf("Demo", "Tecnología"),
            videoUrl = "$VID/ForBiggerMeltdowns.mp4"
        ),
        Video(
            id = "subaru",
            title = "Subaru Outback: calle y tierra",
            description = "Un viaje de prueba que empieza en el asfalto y termina en el barro. Todo terreno de verdad.",
            backdropUrl = "$IMG/SubaruOutbackOnStreetAndDirt.jpg",
            cardUrl = "$IMG/SubaruOutbackOnStreetAndDirt.jpg",
            year = 2013,
            durationMinutes = 1,
            rating = 6.9,
            certification = "TP",
            quality = "HD",
            genres = listOf("Motor", "Deportes"),
            videoUrl = "$VID/SubaruOutbackOnStreetAndDirt.mp4"
        ),
        Video(
            id = "gti",
            title = "Volkswagen GTI: análisis",
            description = "El compacto deportivo más famoso del mundo, analizado al detalle en carretera abierta.",
            backdropUrl = "$IMG/VolkswagenGTIReview.jpg",
            cardUrl = "$IMG/VolkswagenGTIReview.jpg",
            year = 2013,
            durationMinutes = 1,
            rating = 7.1,
            certification = "TP",
            quality = "HD",
            genres = listOf("Motor"),
            videoUrl = "$VID/VolkswagenGTIReview.mp4"
        ),
        Video(
            id = "bullrun",
            title = "Ruta Bullrun",
            description = "La legendaria carrera de autos deportivos, minuto a minuto, por las mejores carreteras del mundo.",
            backdropUrl = "$IMG/WeAreGoingOnBullrun.jpg",
            cardUrl = "$IMG/WeAreGoingOnBullrun.jpg",
            year = 2013,
            durationMinutes = 1,
            rating = 6.7,
            certification = "7+",
            quality = "HD",
            genres = listOf("Motor", "Deportes"),
            videoUrl = "$VID/WeAreGoingOnBullrun.mp4"
        ),
        Video(
            id = "grand",
            title = "¿Qué coche compras por mil dólares?",
            description = "Tres expertos, mil dólares y un solo objetivo: llevarse el mejor coche usado a casa.",
            backdropUrl = "$IMG/WhatCarCanYouGetForAGrand.jpg",
            cardUrl = "$IMG/WhatCarCanYouGetForAGrand.jpg",
            year = 2013,
            durationMinutes = 1,
            rating = 7.3,
            certification = "TP",
            quality = "HD",
            genres = listOf("Motor", "Reality"),
            videoUrl = "$VID/WhatCarCanYouGetForAGrand.mp4"
        ),
        Video(
            id = "apple-hls",
            title = "Señal de prueba HLS",
            description = "Flujo HLS de Apple con múltiples pistas de audio y subtítulos. Perfecto para probar la " +
                "reproducción adaptativa de Media3.",
            backdropUrl = "$IMG/Sintel.jpg",
            cardUrl = "$IMG/Sintel.jpg",
            year = 2023,
            durationMinutes = 30,
            rating = 7.0,
            certification = "TP",
            quality = "Adaptativo",
            genres = listOf("Demo", "Tecnología"),
            videoUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8",
            streamFormat = StreamFormat.HLS,
            badge = "HLS"
        ),
        Video(
            id = "mux-hls",
            title = "Big Buck Bunny · HLS multibitrate",
            description = "Transmisión HLS multibitrate del clásico de la animación. Cambia de calidad en directo según tu conexión.",
            backdropUrl = "$IMG/BigBuckBunny.jpg",
            cardUrl = "$IMG/BigBuckBunny.jpg",
            year = 2021,
            durationMinutes = 10,
            rating = 8.0,
            certification = "TP",
            quality = "Adaptativo",
            genres = listOf("Demo", "Animación"),
            videoUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            streamFormat = StreamFormat.HLS,
            badge = "HLS"
        )
    ).map { withServers(it) }

    /** Contenido disponible para un perfil (los perfiles infantiles ven solo TP/7+). */
    fun catalogFor(profile: Profile?): List<Video> =
        if (profile?.isKids == true) {
            catalog.filter { it.certification == "TP" || it.certification == "7+" }
        } else {
            catalog
        }

    fun featuredFor(profile: Profile?): Video =
        if (profile?.isKids == true) {
            catalog.first { it.id == "big-buck-bunny" }
        } else {
            catalog.first { it.id == "sintel" }
        }

    /**
     * Filas de la pantalla de inicio para un perfil:
     * "Mi lista" y "Continuar viendo" son propias de cada perfil;
     * el resto se filtra para perfiles infantiles.
     */
    fun rowsFor(profile: Profile?): List<MediaRow> {
        val available = catalogFor(profile)
        val availableIds = available.map { it.id }.toSet()
        val rows = mutableListOf<MediaRow>()

        val myList = profile?.myList.orEmpty()
            .mapNotNull { id -> available.find { it.id == id } }
        if (myList.isNotEmpty()) {
            rows += MediaRow("Mi lista", myList)
        }

        val continueItems = profile?.progress.orEmpty()
            .filterKeys { it in availableIds }
            .mapNotNull { (id, _) -> available.find { it.id == id } }
        if (continueItems.isNotEmpty()) {
            rows += MediaRow("Continuar viendo", continueItems)
        }

        rows += listOf(
            MediaRow("Estrenos destacados", byIds("tears-of-steel", "sintel", "big-buck-bunny", "escapes", "joyrides", "gti")),
            MediaRow("Top 10 hoy", byIds("big-buck-bunny", "sintel", "fun", "meltdowns", "elephants-dream", "grand", "subaru", "bullrun", "blazes", "escapes")),
            MediaRow("Cortometrajes premiados", byIds("sintel", "big-buck-bunny", "elephants-dream", "tears-of-steel")),
            MediaRow("Transmisiones en directo · HLS", byIds("apple-hls", "mux-hls"))
        ).mapNotNull { row ->
            val items = row.items.filter { it.id in availableIds }
            if (items.isEmpty()) null else row.copy(items = items)
        }

        return rows
    }

    private fun byIds(vararg ids: String): List<Video> = ids.mapNotNull { find(it) }

    fun find(id: String): Video? = catalog.firstOrNull { it.id == id }

    fun search(query: String, kidsOnly: Boolean = false): List<Video> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val source = if (kidsOnly) catalogFor(Profile(id = "", name = "", isKids = true)) else catalog
        return source.filter { v ->
            v.title.lowercase().contains(q) ||
                v.description.lowercase().contains(q) ||
                v.genres.any { it.lowercase().contains(q) }
        }
    }

    /** Títulos relacionados: comparten al menos un género. */
    fun related(id: String, limit: Int = 6): List<Video> {
        val current = find(id) ?: return emptyList()
        return catalog
            .filter { it.id != id && it.genres.any { g -> g in current.genres } }
            .take(limit)
    }
}
