import { endpoint } from './endpoints.js';
import { getJson } from './lib/http.js';

function extractYear(value) {
  const match = String(value || '').match(/\b(?:19|20)\d{2}\b/);
  return match ? match[0] : '';
}

function firstYear(value) {
  return extractYear(value);
}

function asInt(value) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : null;
}

/**
 * Episodio normalizado a partir de un objeto `videos` de Stremio.
 *
 * `season`/`episode` son la numeración del addon de anime (Kitsu) y
 * `imdbSeason`/`imdbEpisode` la de IMDb. No coinciden: en "100-man no Inochi
 * 2nd Season" Kitsu dice S01E01 pero IMDb dice S02E01, y los releases de los
 * índices usan la numeración IMDb. Por eso se busca en ambos espacios.
 */
function normalizeEpisode(video, index) {
  const season = asInt(video?.season);
  const episode = asInt(video?.episode);
  const imdbSeason = asInt(video?.imdbSeason);
  const imdbEpisode = asInt(video?.imdbEpisode);
  return {
    season,
    episode: episode ?? (index + 1),
    absolute: asInt(video?.absoluteEpisode),
    title: String(video?.title || '').trim().slice(0, 120),
    imdbSeason: imdbSeason ?? season,
    imdbEpisode: imdbEpisode ?? episode ?? (index + 1),
  };
}

/**
 * Resuelve un episodio de anime a temporada/episodio **de IMDb**, que es la
 * numeración que usan los nombres de release que indexan Knaben, torrents-csv
 * y Torlock.
 *
 * Acepta el número absoluto (`kitsu:12:1071`), el de Kitsu (`kitsu:46474:1:3`)
 * y el de IMDb. Devuelve también el título del episodio, útil para afinar la
 * búsqueda cuando el índice usa el nombre del capítulo.
 *
 * @returns {{season: number|null, episode: number|null, title?: string}}
 */
export function resolveEpisode(videos, season, episode) {
  if (!Array.isArray(videos) || !videos.length || episode === null) return { season, episode };
  const entries = videos.map(normalizeEpisode);
  const hit = (entry) => ({
    season: entry.imdbSeason ?? entry.season ?? season ?? 1,
    episode: entry.imdbEpisode ?? entry.episode,
    ...(entry.title ? { title: entry.title } : {}),
  });

  // 1) Ya viene en numeración IMDb (o coincide con ella).
  if (season !== null) {
    const exact = entries.find((entry) =>
      entry.imdbSeason === season && entry.imdbEpisode === episode);
    if (exact) return hit(exact);

    // 2) Viene en numeración Kitsu: se traduce a la de IMDb.
    const kitsu = entries.find((entry) => entry.season === season && entry.episode === episode);
    if (kitsu) return hit(kitsu);
  }

  // 3) Sin temporada: el número puede ser absoluto.
  const byAbsolute = entries.find((entry) => entry.absolute === episode);
  if (byAbsolute) return hit(byAbsolute);

  // 4) Id de Kitsu sin temporada: `kitsu:46474:7` es E07 de la única temporada.
  const kitsuOnly = entries.find((entry) => entry.episode === episode);
  if (kitsuOnly) return hit(kitsuOnly);

  // 5) Última red: se asume que el número ya es de IMDb.
  return { season: season ?? 1, episode };
}

/**
 * Metadatos de anime desde `anime-kitsu.strem.fun` (el addon de anime más
 * instalado): devuelve título, alias, año, IMDb id y la tabla de episodios con
 * la que se traducen episodios absolutos a SxxExx.
 */
async function resolveAnimeMeta(parsed, ctx) {
  const prefix = parsed.animeSource || 'kitsu';
  const id = `${prefix}:${parsed.animeId}`;
  const base = endpoint('animeKitsu', ctx.config, ctx.env);
  const url = `${base}/meta/${parsed.type}/${id}.json`;

  const json = await ctx.cached(`anime:${url}`, 24 * 60 * 60 * 1000, async () => {
    await ctx.limiters.animeKitsu.acquire('animeKitsu');
    return getJson(url, { env: ctx.env, timeout: Math.min(ctx.config.httpTimeout, 8000) });
  });

  const meta = json?.meta;
  if (!meta?.name) return null;

  const titles = [meta.name, ...(Array.isArray(meta.aliases) ? meta.aliases : [])]
    .map((title) => String(title || '').trim())
    .filter(Boolean);

  return {
    name: String(meta.name),
    titles: [...new Set(titles)],
    year: String(firstYear(meta.year || meta.releaseInfo || meta.released)),
    imdbId: meta.imdb_id && /^tt\d{5,}$/.test(meta.imdb_id) ? meta.imdb_id : null,
    type: meta.type === 'movie' ? 'movie' : 'series',
    fromRelease: false,
    poster: meta.poster || '',
    videos: Array.isArray(meta.videos) ? meta.videos : [],
    animeId: id,
  };
}

/**
 * Metadatos por defecto de IMDb (Cinemeta). Si el id es de anime intentamos
 * primero Anime Kitsu y, si falla, seguimos con Cinemeta/IMDb: así nunca se
 * pierde un resultado que antes funcionaba.
 */
export async function resolveMeta(parsed, ctx) {
  if (!parsed) return null;

  if (parsed.releaseName) {
    return {
      name: parsed.releaseName,
      titles: [parsed.releaseName],
      year: extractYear(parsed.releaseName),
      imdbId: null,
      type: parsed.type,
      fromRelease: true,
    };
  }

  if (parsed.animeId) {
    let anime = null;
    try {
      anime = await resolveAnimeMeta(parsed, ctx);
    } catch (error) {
      ctx.noteFailure('animeKitsu', error);
    }
    if (anime) return anime;
  }

  if (!parsed.imdbId) return null;

  const url = `${endpoint('cinemeta', ctx.config, ctx.env)}/meta/${parsed.type}/${parsed.imdbId}.json`;
  const json = await ctx.cached(`cinemeta:${url}`, 24 * 60 * 60 * 1000, async () => {
    await ctx.limiters.cinemeta.acquire('cinemeta');
    return getJson(url, { env: ctx.env, timeout: Math.min(ctx.config.httpTimeout, 8000) });
  });

  const meta = json?.meta;
  if (!meta?.name) return null;
  return {
    name: String(meta.name),
    titles: [String(meta.name)],
    year: String(meta.year || meta.releaseInfo || '') || extractYear(meta.released),
    imdbId: parsed.imdbId,
    type: parsed.type,
    fromRelease: false,
    poster: meta.poster || '',
  };
}
