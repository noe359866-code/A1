package com.omnitv.app.data

import org.json.JSONObject

/** Tipo de tramo saltable. Cada API usa su propio nombre para el mismo concepto. */
enum class SkipKind(val label: String) {
    INTRO("intro"),
    RECAP("resumen"),
    CREDITS("créditos"),
    PREVIEW("avance"),
    POST_CREDITS("escena final");

    /** Duración máxima aceptada; por encima se descarta el dato por sospechoso. */
    val maxMs: Long
        get() = when (this) {
            INTRO -> 300_000
            RECAP -> 480_000
            CREDITS -> 900_000
            PREVIEW -> 900_000
            POST_CREDITS -> 600_000
        }
}

/**
 * Tramo confirmado por una base de datos comunitaria.
 * [endMs] null significa «hasta el final del vídeo» (créditos sin fin declarado).
 * [uncertain] marca datos cuya duración de origen no encaja con este.stream.
 */
data class SkipSegment(
    val kind: SkipKind,
    val startMs: Long,
    val endMs: Long?,
    val source: String,
    val confidence: Double? = null,
    val uncertain: Boolean = false
) {
    fun contains(positionMs: Long): Boolean =
        positionMs >= startMs && (endMs == null || positionMs < endMs)

    fun targetMs(durationMs: Long): Long = endMs ?: durationMs
}

/** Tramos de un título, ya ordenados y sin solapes imposibles. */
data class SkipTimeline(
    val segments: List<SkipSegment> = emptyList(),
    val sources: List<String> = emptyList(),
    /** Mediana de intro de la temporada (SkipDB) cuando este episodio no tiene intro. */
    val introEstimateMs: Long? = null
) {
    fun activeAt(positionMs: Long): SkipSegment? = segments.firstOrNull { it.contains(positionMs) }

    fun has(kind: SkipKind): Boolean = segments.any { it.kind == kind }

    fun hasSegmentAfter(positionMs: Long): Boolean =
        segments.any { it.startMs >= positionMs - 1_000L }

    /**
     * Verdadero cuando este tramo llega hasta el final del vídeo (sin fin declarado o en
     * los últimos 4 s) y no hay otro tramo después (p. ej. escena post-créditos).
     */
    fun isTailSegment(segment: SkipSegment, durationMs: Long): Boolean {
        val reachesEnd = when {
            segment.endMs == null -> true
            durationMs > 0 -> segment.endMs >= durationMs - 4_000L
            else -> false
        }
        if (!reachesEnd) return false
        val target = segment.endMs ?: if (durationMs > 0) durationMs else segment.startMs + 1_000L
        return segments.none { it !== segment && it.startMs >= target - 1_000L && it.startMs > segment.startMs }
    }

    companion object {
        val EMPTY = SkipTimeline()
    }
}

/** Identidad de un vídeo para las bases de datos de tramos. */
data class SkipTarget(
    val imdbId: String?,
    val tmdbId: String?,
    val season: Int?,
    val episode: Int?,
    val malId: String? = null,
    val kitsuId: String? = null
) {
    val isEpisode: Boolean get() = season != null && episode != null
    val cacheKey: String
        get() = "${imdbId ?: tmdbId?.let { "tmdb:$it" } ?: malId?.let { "mal:$it" } ?: "kitsu:$kitsuId"}:${season ?: 0}:${episode ?: 0}"

    companion object {
        /**
         * Combina `streamId` e `id` para no perder identidades al navegar desde
         * TMDb (`tmdb:…`) o anime (`mal:…`) ya resueltos a IMDb (`tt…`).
         */
        fun from(video: Video): SkipTarget? {
            val candidates = listOfNotNull(
                video.streamId?.takeIf { it.isNotBlank() },
                video.id.takeIf { it.isNotBlank() }
            )
            if (candidates.isEmpty()) return null

            var imdbId: String? = null
            var tmdbId: String? = null
            var malId: String? = null
            var kitsuId: String? = null
            var season: Int? = null
            var episode: Int? = null

            for (candidate in candidates) {
                val clean = candidate.substringBefore('~')
                val parts = clean.split(':')
                when {
                    clean.startsWith("tmdb:") -> {
                        val id = parts.getOrNull(1)?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                        if (id != null && tmdbId == null) tmdbId = id
                        if (season == null) season = parts.getOrNull(2)?.toIntOrNull()
                        if (episode == null) episode = parts.getOrNull(3)?.toIntOrNull()
                    }
                    clean.startsWith("mal:") -> {
                        val id = parts.getOrNull(1)?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                        if (id != null && malId == null) malId = id
                        if (season == null) season = parts.getOrNull(2)?.toIntOrNull()
                        if (episode == null) episode = parts.getOrNull(3)?.toIntOrNull()
                    }
                    clean.startsWith("kitsu:") -> {
                        val id = parts.getOrNull(1)?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                        if (id != null && kitsuId == null) kitsuId = id
                        if (season == null) season = parts.getOrNull(2)?.toIntOrNull()
                        if (episode == null) episode = parts.getOrNull(3)?.toIntOrNull()
                    }
                    clean.startsWith("tt") -> {
                        val id = parts[0].takeIf { it.length > 2 && it.drop(2).all(Char::isDigit) }
                        if (id != null && imdbId == null) imdbId = id
                        if (season == null) season = parts.getOrNull(1)?.toIntOrNull()
                        if (episode == null) episode = parts.getOrNull(2)?.toIntOrNull()
                    }
                }
                if ((season == null || episode == null) && '~' in candidate) {
                    val epSuffix = candidate.substringAfter('~')
                    val match = Regex("""^s(\d+)e(\d+)$""", RegexOption.IGNORE_CASE).matchEntire(epSuffix)
                    if (match != null) {
                        if (season == null) season = match.groupValues[1].toIntOrNull()
                        if (episode == null) episode = match.groupValues[2].toIntOrNull()
                    }
                }
            }

            if (imdbId == null && tmdbId == null && malId == null && kitsuId == null) return null
            return SkipTarget(imdbId, tmdbId, season, episode, malId, kitsuId)
        }
    }
}

/**
 * Parsers puro JVM de las cuatro APIs públicas. Ninguna necesita clave para leer:
 * - TheIntroDB v4: arrays por tipo, `start_ms`/`end_ms`; 0/0 = «no hay tramo».
 * - SkipDB: `segments.{intro,recap,outro,preview}` + `match` y estimación de temporada.
 * - IntroDB: objetos por tipo + banderas `no_intro`/`no_recap`/`no_outro`.
 * - AniSkip v2: `results[]` con `skipType` (`op`/`ed`/`recap`) e `interval.{startTime,endTime}`.
 */
object SkipSegmentParser {

    const val SOURCE_THEINTRODB = "TheIntroDB"
    const val SOURCE_SKIPDB = "SkipDB"
    const val SOURCE_INTRODB = "IntroDB"
    const val SOURCE_ANISKIP = "AniSkip"

    /** Orden de preferencia por tipo: el que ajusta por duración manda. */
    private val PRIORITY = listOf(SOURCE_SKIPDB, SOURCE_THEINTRODB, SOURCE_INTRODB, SOURCE_ANISKIP)

    fun theIntroDb(json: JSONObject, durationMs: Long): List<SkipSegment> = buildList {
        mapOf("intro" to SkipKind.INTRO, "recap" to SkipKind.RECAP, "credits" to SkipKind.CREDITS, "preview" to SkipKind.PREVIEW)
            .forEach { (key, kind) ->
                json.optJSONArray(key)?.let { array ->
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        // 0/0 es el centinela «este episodio no tiene ese tramo».
                        if (item.optLong("start_ms", -1) == 0L && item.optLong("end_ms", -1) == 0L) continue
                        val start = item.nullableLong("start_ms") ?: 0L
                        val end = item.nullableLong("end_ms")?.takeIf { it > 0 }
                        normalized(kind, start, end, SOURCE_THEINTRODB, null, false, durationMs)?.let { add(it) }
                    }
                }
            }
    }

    fun skipDb(json: JSONObject, durationMs: Long): List<SkipSegment> = buildList {
        val segments = json.optJSONObject("segments") ?: return@buildList
        mapOf("intro" to SkipKind.INTRO, "recap" to SkipKind.RECAP, "outro" to SkipKind.CREDITS, "preview" to SkipKind.PREVIEW)
            .forEach { (key, kind) ->
                val item = segments.optJSONObject(key) ?: return@forEach
                val start = item.nullableLong("start_ms") ?: return@forEach
                val end = item.nullableLong("end_ms")
                val match = item.optString("match", "agnostic")
                val confidence = item.optDouble("confidence", Double.NaN).takeIf { !it.isNaN() }
                normalized(kind, start, end, SOURCE_SKIPDB, confidence, match == "out-of-range", durationMs)?.let { add(it) }
            }
    }

    fun skipDbIntroEstimate(json: JSONObject): Long? =
        json.nullableLong("intro_length_estimate_ms")?.takeIf { it in 5_000..SkipKind.INTRO.maxMs }

    fun introDb(json: JSONObject, durationMs: Long): List<SkipSegment> = buildList {
        mapOf("intro" to SkipKind.INTRO, "recap" to SkipKind.RECAP, "outro" to SkipKind.CREDITS, "post_credits" to SkipKind.POST_CREDITS)
            .forEach { (key, kind) ->
                val item = json.optJSONObject(key) ?: return@forEach
                val start = item.nullableLong("start_ms") ?: item.optDouble("start_sec", Double.NaN)
                    .takeIf { !it.isNaN() }?.let { (it * 1000).toLong() } ?: return@forEach
                val end = item.nullableLong("end_ms") ?: item.optDouble("end_sec", Double.NaN)
                    .takeIf { !it.isNaN() }?.let { (it * 1000).toLong() }
                val confidence = item.optDouble("confidence", Double.NaN).takeIf { !it.isNaN() }
                normalized(kind, start, end, SOURCE_INTRODB, confidence, false, durationMs)?.let { add(it) }
            }
    }

    fun aniSkip(json: JSONObject, durationMs: Long): List<SkipSegment> = buildList {
        if (!json.optBoolean("found", true)) return@buildList
        val results = json.optJSONArray("results") ?: return@buildList
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val kind = when (item.optString("skipType", "").lowercase()) {
                "op", "mixed-op" -> SkipKind.INTRO
                "recap" -> SkipKind.RECAP
                "ed", "mixed-ed" -> SkipKind.CREDITS
                else -> continue
            }
            val interval = item.optJSONObject("interval") ?: continue
            val startSec = interval.optDouble("startTime", Double.NaN).takeIf { !it.isNaN() } ?: continue
            val endSec = interval.optDouble("endTime", Double.NaN).takeIf { !it.isNaN() }
            val startMs = (startSec * 1000.0).toLong()
            val endMs = endSec?.let { (it * 1000.0).toLong() }
            normalized(kind, startMs, endMs, SOURCE_ANISKIP, null, false, durationMs)?.let { add(it) }
        }
    }

    /** Valida un tramo y lo recorta a la duración real del stream cuando se conoce. */
    internal fun normalized(
        kind: SkipKind,
        startMs: Long,
        endMs: Long?,
        source: String,
        confidence: Double?,
        uncertain: Boolean,
        durationMs: Long
    ): SkipSegment? {
        if (startMs < 0) return null
        if (durationMs > 0 && startMs >= durationMs) return null
        val end = endMs?.let { if (durationMs > 0) minOf(it, durationMs) else it }
        if (end != null && end <= startMs) return null
        val length = (end ?: (durationMs.takeIf { it > 0 } ?: return null)) - startMs
        if (length < 3_000 || length > kind.maxMs) return null
        return SkipSegment(kind, startMs, end, source, confidence, uncertain)
    }

    /**
     * Une respuestas: por tipo gana la fuente prioritaria con datos fiables;
     * se conservan varios tramos del mismo tipo si no se solapan.
     */
    fun merge(results: List<List<SkipSegment>>, introEstimateMs: Long? = null): SkipTimeline {
        val all = results.flatten()
        val chosen = mutableListOf<SkipSegment>()
        SkipKind.entries.forEach { kind ->
            val ofType = all.filter { it.kind == kind }
            if (ofType.isEmpty()) return@forEach
            val reliable = ofType.filter { !it.uncertain }
            val pool = reliable.ifEmpty { ofType }
            val bestSource = PRIORITY.firstOrNull { source -> pool.any { it.source == source } } ?: pool.first().source
            pool.filter { it.source == bestSource }
                .sortedBy { it.startMs }
                .forEach { candidate ->
                    val overlaps = chosen.any { it.kind == kind && candidate.startMs < (it.endMs ?: Long.MAX_VALUE) && it.startMs < (candidate.endMs ?: Long.MAX_VALUE) }
                    if (!overlaps) chosen += candidate
                }
        }
        return SkipTimeline(
            segments = chosen.sortedBy { it.startMs },
            sources = chosen.map { it.source }.distinct(),
            introEstimateMs = introEstimateMs?.takeIf { !chosen.any { c -> c.kind == SkipKind.INTRO } }
        )
    }
}

/** `null` de JSON no es 0: un extremo nulo significa principio/fin del vídeo. */
internal fun JSONObject.nullableLong(key: String): Long? =
    if (isNull(key) || !has(key)) null else optLong(key, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
