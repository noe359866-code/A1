package com.omnitv.app.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Tramos saltables para todos los usuarios, sin configurar nada.
 *
 * Las tres bases de datos permiten **lectura anónima**: la app no lleva ninguna
 * clave dentro del APK ni depende de un servidor propio. Cada televisor consulta
 * desde su propia IP, así que los límites son por hogar y no compartidos.
 *
 * Quien tenga cuenta puede añadir su clave en el panel QR (`THEINTRODB=…`); se usa
 * solo en TheIntroDB, donde sube el límite de lecturas e incluye sus propios
 * envíos pendientes. SkipDB e IntroDB reservan la clave para escribir.
 */
object SkipSegmentService {

    const val THEINTRODB_BASE = "https://api.theintrodb.org/v4/media"
    const val SKIPDB_BASE = "https://api.skipdb.tv/api/segments"
    const val INTRODB_BASE = "https://api.introdb.app/segments"
    const val ANISKIP_BASE = "https://api.aniskip.com/v2/skip-times"

    private const val HIT_TTL_MS = 6 * 60 * 60_000L
    private const val MISS_TTL_MS = 10 * 60_000L
    private const val RATE_LIMIT_COOLDOWN_MS = 60_000L
    private const val MAX_CACHE = 64

    /** Estado legible para Ajustes; nunca promete datos que no llegaron. */
    val lastStatus = mutableStateOf("")

    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, Pair<Long, SkipTimeline>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, SkipTimeline>>?): Boolean = size > MAX_CACHE
    }
    private val cooldownUntil = mutableMapOf<String, Long>()

    /**
     * Consulta en paralelo las fuentes activadas y las combina.
     * [durationMs] permite a SkipDB/TheIntroDB elegir la versión del stream;
     * con 0 se consulta igual, pero el ajuste por duración es menos preciso.
     */
    suspend fun fetch(
        video: Video,
        durationMs: Long,
        sources: Set<String> = SettingsStore.skipSources,
        force: Boolean = false
    ): SkipTimeline {
        if (!SettingsStore.skipSegmentsEnabled || sources.isEmpty()) {
            lastStatus.value = "Desactivado en Ajustes"
            return SkipTimeline.EMPTY
        }
        val target = SkipTarget.from(video) ?: run {
            lastStatus.value = "Este título no tiene id IMDb/TMDB/MAL: sin tramos que consultar"
            return SkipTimeline.EMPTY
        }
        // Un episodio necesita temporada y número; sin ellos no se consulta TV.
        val isSeries = video.remoteType == "series" ||
            (video.remoteType != "movie" && (video.type == VideoType.SERIES || video.type == VideoType.ANIME))
        if (isSeries && !target.isEpisode) {
            lastStatus.value = "Falta temporada/episodio para buscar tramos"
            return SkipTimeline.EMPTY
        }
        val seconds = (durationMs / 1000L).coerceAtLeast(0)
        val key = "${target.cacheKey}|${seconds / 5}|${sources.sorted().joinToString(",")}"
        if (!force) {
            synchronized(cache) { cache[key] }?.let { (storedAt, timeline) ->
                val ttl = if (timeline.segments.isEmpty()) MISS_TTL_MS else HIT_TTL_MS
                if (System.currentTimeMillis() - storedAt < ttl) {
                    lastStatus.value = formatStatus(timeline, responded = true)
                    return timeline
                }
            }
        }

        return mutex.withLock {
            val results = coroutineScope {
                sources.map { source ->
                    async {
                        withTimeoutOrNull(8_000L) { runCatching { query(source, target, isSeries, seconds) }.getOrNull() }
                    }
                }.awaitAll()
            }
            val segments = results.mapNotNull { it?.first }
            val estimate = results.firstNotNullOfOrNull { it?.second }
            val timeline = SkipSegmentParser.merge(segments, estimate)
            synchronized(cache) { cache[key] = System.currentTimeMillis() to timeline }
            lastStatus.value = formatStatus(timeline, responded = segments.isNotEmpty())
            timeline
        }
    }

    private fun formatStatus(timeline: SkipTimeline, responded: Boolean): String = when {
        !responded -> "Sin respuesta de las bases de tramos (revisa la red)"
        timeline.segments.isEmpty() -> "Nadie ha marcado todavía tramos para este título"
        else -> "${timeline.segments.size} tramos · ${timeline.sources.joinToString(" + ")}"
    }

    /** Devuelve (tramos, estimación de intro de temporada) o null si no respondió. */
    private suspend fun query(
        source: String,
        target: SkipTarget,
        isSeries: Boolean,
        seconds: Long
    ): Pair<List<SkipSegment>, Long?>? {
        if (System.currentTimeMillis() < (cooldownUntil[source] ?: 0L)) return null
        val request = buildRequest(source, target, isSeries, seconds) ?: return null
        val result = withContext(Dispatchers.IO) { AddonHttp.getJsonWithStatus(request.first, request.second) }
        if (result.status == 429) {
            cooldownUntil[source] = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
            return null
        }
        val json = result.value as? JSONObject ?: return null
        val durationMs = seconds * 1000
        return when (source) {
            SkipSegmentParser.SOURCE_SKIPDB ->
                SkipSegmentParser.skipDb(json, durationMs) to SkipSegmentParser.skipDbIntroEstimate(json)
            SkipSegmentParser.SOURCE_THEINTRODB -> SkipSegmentParser.theIntroDb(json, durationMs) to null
            SkipSegmentParser.SOURCE_INTRODB -> SkipSegmentParser.introDb(json, durationMs) to null
            SkipSegmentParser.SOURCE_ANISKIP -> SkipSegmentParser.aniSkip(json, durationMs) to null
            else -> null
        }
    }

    /** URL + cabeceras; null cuando la fuente no puede direccionar este título. */
    internal fun buildRequest(
        source: String,
        target: SkipTarget,
        isSeries: Boolean,
        seconds: Long
    ): Pair<String, Map<String, String>>? {
        if (source == SkipSegmentParser.SOURCE_ANISKIP) {
            val malId = target.malId ?: return null
            val ep = target.episode ?: if (!isSeries) 1 else return null
            val lenParam = if (seconds > 0) "&episodeLength=$seconds" else ""
            val url = "$ANISKIP_BASE/${encode(malId)}/$ep?types[]=op&types[]=ed&types[]=recap&types[]=mixed-op&types[]=mixed-ed$lenParam"
            return url to emptyMap()
        }
        val params = mutableListOf<Pair<String, String>>()
        val headers = mutableMapOf<String, String>()
        when (source) {
            SkipSegmentParser.SOURCE_THEINTRODB -> {
                // v4 prefiere TMDB; IMDb sirve de respaldo.
                when {
                    target.tmdbId != null -> params += "tmdb_id" to target.tmdbId
                    target.imdbId != null -> params += "imdb_id" to target.imdbId
                    else -> return null
                }
                if (seconds > 0) params += "duration_ms" to (seconds * 1000).toString()
                SettingsStore.apiKeys["THEINTRODB"]?.takeIf { it.isNotBlank() }?.let {
                    headers["Authorization"] = "Bearer $it"
                }
            }
            SkipSegmentParser.SOURCE_SKIPDB -> {
                // SkipDB solo trabaja con IMDb: no se inventa la conversión.
                params += "imdb_id" to (target.imdbId ?: return null)
                if (seconds > 0) params += "duration" to seconds.toString()
            }
            SkipSegmentParser.SOURCE_INTRODB -> {
                params += "imdb_id" to (target.imdbId ?: return null)
                if (!isSeries) params += "is_movie" to "true"
            }
            else -> return null
        }
        if (isSeries) {
            params += "season" to (target.season ?: return null).toString()
            params += "episode" to (target.episode ?: return null).toString()
        }
        val query = params.joinToString("&") { "${it.first}=${encode(it.second)}" }
        val base = when (source) {
            SkipSegmentParser.SOURCE_THEINTRODB -> THEINTRODB_BASE
            SkipSegmentParser.SOURCE_SKIPDB -> SKIPDB_BASE
            else -> INTRODB_BASE
        }
        return "$base?$query" to headers
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
        cooldownUntil.clear()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
