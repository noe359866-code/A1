package com.omnitv.app.data

import org.json.JSONArray
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

    companion object {
        val EMPTY = SkipTimeline()
    }
}

/** Identidad de un vídeo para las bases de datos de tramos. */
data class SkipTarget(
    val imdbId: String?,
    val tmdbId: String?,
    val season: Int?,
    val episode: Int?
) {
    val isEpisode: Boolean get() = season != null && episode != null
    val cacheKey: String get() = "${imdbId ?: "tmdb:$tmdbId"}:${season ?: 0}:${episode ?: 0}"

    companion object {
        /** Los ids vienen de Cinemeta (`tt…`) o Nuvio (`tmdb:…`), con `:temporada:episodio`. */
        fun from(video: Video): SkipTarget? {
            val raw = (video.streamId ?: video.id).takeIf { it.isNotBlank() } ?: return null
            val parts = raw.split(':')
            return when {
                raw.startsWith("tmdb:") -> {
                    val id = parts.getOrNull(1)?.takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: return null
                    SkipTarget(null, id, parts.getOrNull(2)?.toIntOrNull(), parts.getOrNull(3)?.toIntOrNull())
                }
                raw.startsWith("tt") -> SkipTarget(
                    parts[0], null, parts.getOrNull(1)?.toIntOrNull(), parts.getOrNull(2)?.toIntOrNull()
                )
                else -> null
            }
        }
    }
}

/**
 * Parsers puro JVM de las tres APIs públicas. Ninguna necesita clave para leer:
 * - TheIntroDB v4: arrays por tipo, `start_ms`/`end_ms`; 0/0 = «no hay tramo».
 * - SkipDB: `segments.{intro,recap,outro,preview}` + `match` y estimación de temporada.
 * - IntroDB: objetos por tipo + banderas `no_intro`/`no_recap`/`no_outro`.
 */
object SkipSegmentParser {

    const val SOURCE_THEINTRODB = "TheIntroDB"
    const val SOURCE_SKIPDB = "SkipDB"
    const val SOURCE_INTRODB = "IntroDB"

    /** Orden de preferencia por tipo: el que ajusta por duración manda. */
    private val PRIORITY = listOf(SOURCE_SKIPDB, SOURCE_THEINTRODB, SOURCE_INTRODB)

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
