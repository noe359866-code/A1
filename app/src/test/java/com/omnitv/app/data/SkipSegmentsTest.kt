package com.omnitv.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentsTest {

    private fun episode(id: String) = Video(
        id = id.substringBefore(':') + "~e", title = "Serie · Uno", description = "", backdropUrl = "", cardUrl = "",
        year = 2024, durationMinutes = 45, rating = 0.0, certification = "", quality = "", genres = emptyList(),
        videoUrl = "", type = VideoType.SERIES, remoteType = "series", streamId = id
    )

    // ------------------------- identidad -------------------------

    @Test
    fun `ids IMDb y TMDB con temporada y episodio`() {
        val imdb = SkipTarget.from(episode("tt0903747:1:2"))!!
        assertEquals("tt0903747", imdb.imdbId)
        assertEquals(1, imdb.season)
        assertEquals(2, imdb.episode)
        assertTrue(imdb.isEpisode)
        val tmdb = SkipTarget.from(episode("tmdb:1396:2:3"))!!
        assertEquals("1396", tmdb.tmdbId)
        assertNull(tmdb.imdbId)
        assertEquals(2, tmdb.season)
        assertEquals(3, tmdb.episode)
        assertNull(SkipTarget.from(episode("slug-local")))
    }

    @Test
    fun `una pelicula no arrastra temporada`() {
        val movie = episode("tt0371746").copy(remoteType = "movie", type = VideoType.MOVIE, streamId = "tt0371746")
        val target = SkipTarget.from(movie)!!
        assertFalse(target.isEpisode)
        val (url, _) = SkipSegmentService.buildRequest(SkipSegmentParser.SOURCE_INTRODB, target, false, 0)!!
        assertTrue(url.contains("is_movie=true"))
        assertFalse(url.contains("season="))
    }

    // ------------------------- URLs -------------------------

    @Test
    fun `cada API recibe sus parametros y SkipDB la duracion en segundos`() {
        val target = SkipTarget("tt0903747", "1396", 1, 2)
        val (skip, skipHeaders) = SkipSegmentService.buildRequest(SkipSegmentParser.SOURCE_SKIPDB, target, true, 2820)!!
        assertTrue(skip.startsWith("https://api.skipdb.tv/api/segments?"))
        assertTrue(skip.contains("imdb_id=tt0903747"))
        assertTrue(skip.contains("duration=2820"))
        assertTrue(skip.contains("season=1") && skip.contains("episode=2"))
        assertTrue(skipHeaders.isEmpty()) // lectura anónima: sin clave
        val (tidb, _) = SkipSegmentService.buildRequest(SkipSegmentParser.SOURCE_THEINTRODB, target, true, 2820)!!
        assertTrue(tidb.contains("tmdb_id=1396"))
        assertTrue(tidb.contains("duration_ms=2820000"))
        val (intro, _) = SkipSegmentService.buildRequest(SkipSegmentParser.SOURCE_INTRODB, target, true, 2820)!!
        assertTrue(intro.contains("imdb_id=tt0903747"))
    }

    @Test
    fun `sin IMDb no se consulta a las APIs que solo lo aceptan`() {
        val tmdbOnly = SkipTarget(null, "1396", 1, 2)
        assertNull(SkipSegmentService.buildRequest(SkipSegmentParser.SOURCE_SKIPDB, tmdbOnly, true, 0))
        assertNull(SkipSegmentService.buildRequest(SkipSegmentParser.SOURCE_INTRODB, tmdbOnly, true, 0))
        assertNotNull(SkipSegmentService.buildRequest(SkipSegmentParser.SOURCE_THEINTRODB, tmdbOnly, true, 0))
    }

    // ------------------------- TheIntroDB -------------------------

    @Test
    fun `TheIntroDB arrays extremos nulos y centinela cero`() {
        val json = JSONObject("""{
            "intro":[{"start_ms":null,"end_ms":90000}],
            "credits":[{"start_ms":2700000,"end_ms":null}],
            "recap":[{"start_ms":0,"end_ms":0}],
            "preview":[]
        }""")
        val segments = SkipSegmentParser.theIntroDb(json, 2_820_000)
        assertEquals(2, segments.size)
        val intro = segments.first { it.kind == SkipKind.INTRO }
        assertEquals(0L, intro.startMs)
        assertEquals(90_000L, intro.endMs)
        val credits = segments.first { it.kind == SkipKind.CREDITS }
        assertNull(credits.endMs)
        assertEquals(2_820_000L, credits.targetMs(2_820_000))
        assertFalse(segments.any { it.kind == SkipKind.RECAP })
    }

    // ------------------------- SkipDB -------------------------

    @Test
    fun `SkipDB marca como dudoso un match fuera de rango y guarda la estimacion`() {
        val json = JSONObject("""{
            "segments":{
                "intro":{"start_ms":61000,"end_ms":91000,"match":"exact","confidence":0.93},
                "recap":null,
                "outro":{"start_ms":2760000,"end_ms":2820000,"match":"shifted"},
                "preview":{"start_ms":2600000,"end_ms":2660000,"match":"out-of-range"}
            },
            "intro_length_estimate_ms":30000
        }""")
        val segments = SkipSegmentParser.skipDb(json, 2_820_000)
        assertEquals(3, segments.size)
        assertFalse(segments.first { it.kind == SkipKind.INTRO }.uncertain)
        assertEquals(0.93, segments.first { it.kind == SkipKind.INTRO }.confidence!!, 0.001)
        assertTrue(segments.first { it.kind == SkipKind.PREVIEW }.uncertain)
        assertEquals(30_000L, SkipSegmentParser.skipDbIntroEstimate(json))
    }

    // ------------------------- IntroDB -------------------------

    @Test
    fun `IntroDB nulos y banderas no crean tramos`() {
        val json = JSONObject("""{
            "intro":{"start_ms":2500,"end_ms":58000,"confidence":0.92},
            "recap":null,"outro":null,"post_credits":null,
            "no_intro":false,"no_recap":true,"no_outro":false
        }""")
        val segments = SkipSegmentParser.introDb(json, 1_800_000)
        assertEquals(1, segments.size)
        assertEquals(SkipKind.INTRO, segments.single().kind)
        assertEquals(2_500L, segments.single().startMs)
    }

    @Test
    fun `IntroDB tambien acepta segundos`() {
        val json = JSONObject("""{"intro":{"start_sec":2.5,"end_sec":58}}""")
        val segment = SkipSegmentParser.introDb(json, 1_800_000).single()
        assertEquals(2_500L, segment.startMs)
        assertEquals(58_000L, segment.endMs)
    }

    // ------------------------- validación y fusión -------------------------

    @Test
    fun `tramos imposibles se descartan y el fin se recorta al video`() {
        assertNull(SkipSegmentParser.normalized(SkipKind.INTRO, 1000, 2000, "X", null, false, 0)) // < 3 s
        assertNull(SkipSegmentParser.normalized(SkipKind.INTRO, 50_000, 40_000, "X", null, false, 0))
        assertNull(SkipSegmentParser.normalized(SkipKind.INTRO, 900_000, null, "X", null, false, 600_000))
        assertNull(SkipSegmentParser.normalized(SkipKind.INTRO, 0, 400_000, "X", null, false, 0)) // > máx
        val clipped = SkipSegmentParser.normalized(SkipKind.CREDITS, 1_700_000, 1_900_000, "X", null, false, 1_800_000)!!
        assertEquals(1_800_000L, clipped.endMs)
    }

    @Test
    fun `gana SkipDB por tipo y se conserva IntroDB donde los demas no tienen datos`() {
        val skip = listOf(SkipSegment(SkipKind.INTRO, 61_000, 91_000, SkipSegmentParser.SOURCE_SKIPDB))
        val tidb = listOf(
            SkipSegment(SkipKind.INTRO, 60_000, 95_000, SkipSegmentParser.SOURCE_THEINTRODB),
            SkipSegment(SkipKind.RECAP, 0, 40_000, SkipSegmentParser.SOURCE_THEINTRODB)
        )
        val intro = listOf(SkipSegment(SkipKind.POST_CREDITS, 1_750_000, 1_790_000, SkipSegmentParser.SOURCE_INTRODB))
        val timeline = SkipSegmentParser.merge(listOf(intro, tidb, skip), introEstimateMs = 30_000)
        assertEquals(3, timeline.segments.size)
        assertEquals(61_000L, timeline.segments.first { it.kind == SkipKind.INTRO }.startMs)
        assertEquals(SkipSegmentParser.SOURCE_SKIPDB, timeline.segments.first { it.kind == SkipKind.INTRO }.source)
        assertTrue(timeline.has(SkipKind.RECAP))
        assertTrue(timeline.has(SkipKind.POST_CREDITS))
        // Con intro confirmada la estimación de temporada no se ofrece.
        assertNull(timeline.introEstimateMs)
    }

    @Test
    fun `un dato dudoso no tapa a uno fiable de otra fuente`() {
        val uncertain = listOf(SkipSegment(SkipKind.INTRO, 10_000, 40_000, SkipSegmentParser.SOURCE_SKIPDB, uncertain = true))
        val reliable = listOf(SkipSegment(SkipKind.INTRO, 12_000, 42_000, SkipSegmentParser.SOURCE_THEINTRODB))
        val timeline = SkipSegmentParser.merge(listOf(uncertain, reliable))
        assertEquals(SkipSegmentParser.SOURCE_THEINTRODB, timeline.segments.single().source)
    }

    @Test
    fun `el tramo activo y su destino dependen de la posicion`() {
        val timeline = SkipSegmentParser.merge(
            listOf(listOf(SkipSegment(SkipKind.INTRO, 60_000, 90_000, "SkipDB"), SkipSegment(SkipKind.CREDITS, 1_700_000, null, "SkipDB")))
        )
        assertNull(timeline.activeAt(59_999))
        assertEquals(SkipKind.INTRO, timeline.activeAt(60_000)!!.kind)
        assertNull(timeline.activeAt(90_000))
        assertEquals(SkipKind.CREDITS, timeline.activeAt(1_750_000)!!.kind)
        assertEquals(1_800_000L, timeline.activeAt(1_750_000)!!.targetMs(1_800_000))
    }

    @Test
    fun `SkipTarget conserva ids combinados tmdb mas imdb y mal para anime`() {
        val fromPlatform = Video(
            id = "tmdb:940721", title = "Peli", description = "", backdropUrl = "", cardUrl = "",
            year = 2023, durationMinutes = 120, rating = 0.0, certification = "", quality = "",
            genres = emptyList(), videoUrl = "", type = VideoType.MOVIE, remoteType = "movie",
            streamId = "tt23289160"
        )
        val combined = SkipTarget.from(fromPlatform)!!
        assertEquals("tt23289160", combined.imdbId)
        assertEquals("940721", combined.tmdbId)
        assertFalse(combined.isEpisode)

        val animeEpisode = episode("tt0409591:1:12").copy(id = "mal:20~s1e12", type = VideoType.ANIME)
        val animeTarget = SkipTarget.from(animeEpisode)!!
        assertEquals("tt0409591", animeTarget.imdbId)
        assertEquals("20", animeTarget.malId)
        assertEquals(1, animeTarget.season)
        assertEquals(12, animeTarget.episode)

        val malOnly = episode("mal:5114:1:3").copy(id = "mal:5114~s1e3", type = VideoType.ANIME)
        val malTarget = SkipTarget.from(malOnly)!!
        assertNull(malTarget.imdbId)
        assertEquals("5114", malTarget.malId)
        assertEquals(1, malTarget.season)
        assertEquals(3, malTarget.episode)
    }

    @Test
    fun `AniSkip lee op, ed y recap y construye su peticion con malId`() {
        val target = SkipTarget(null, null, 1, 5, malId = "5114")
        val (url, headers) = SkipSegmentService.buildRequest(SkipSegmentParser.SOURCE_ANISKIP, target, true, 1440)!!
        assertTrue(url.startsWith("https://api.aniskip.com/v2/skip-times/5114/5?"))
        assertTrue(url.contains("episodeLength=1440"))
        assertTrue(headers.isEmpty())

        assertNull(SkipSegmentService.buildRequest(SkipSegmentParser.SOURCE_ANISKIP, SkipTarget("tt0903747", null, 1, 1), true, 0))

        val json = JSONObject("""{
            "found": true,
            "results": [
                {"skipType": "op", "interval": {"startTime": 89.5, "endTime": 179.5}},
                {"skipType": "recap", "interval": {"startTime": 12.0, "endTime": 42.0}},
                {"skipType": "ed", "interval": {"startTime": 1320.0, "endTime": 1445.0}}
            ]
        }""")
        val segments = SkipSegmentParser.aniSkip(json, 1_440_000)
        assertEquals(3, segments.size)
        assertEquals(89_500L, segments.first { it.kind == SkipKind.INTRO }.startMs)
        assertEquals(42_000L, segments.first { it.kind == SkipKind.RECAP }.endMs)
        assertEquals(1_440_000L, segments.first { it.kind == SkipKind.CREDITS }.endMs)

        val emptyJson = JSONObject("""{"found": false, "results": []}""")
        assertTrue(SkipSegmentParser.aniSkip(emptyJson, 1_440_000).isEmpty())
    }

    @Test
    fun `isTailSegment distingue creditos finales de creditos previos a escena final`() {
        val creditsTail = SkipSegment(SkipKind.CREDITS, 1_320_000, 1_438_000, "SkipDB")
        val tailTimeline = SkipSegmentParser.merge(listOf(listOf(creditsTail)))
        assertTrue(tailTimeline.isTailSegment(creditsTail, 1_440_000))

        val creditsBeforePost = SkipSegment(SkipKind.CREDITS, 1_320_000, 1_390_000, "IntroDB")
        val postCredits = SkipSegment(SkipKind.POST_CREDITS, 1_390_000, 1_438_000, "IntroDB")
        val postTimeline = SkipSegmentParser.merge(listOf(listOf(creditsBeforePost, postCredits)))
        assertFalse(postTimeline.isTailSegment(creditsBeforePost, 1_440_000))
        assertTrue(postTimeline.hasSegmentAfter(1_390_000))
    }
}
