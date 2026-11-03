import { endpoint } from './endpoints.js';
import { getJson } from './lib/http.js';

function extractYear(value) {
  const match = String(value || '').match(/\b(?:19|20)\d{2}\b/);
  return match ? match[0] : '';
}

function firstYear(value) {
  return extractYear(value);
}

/** Episodio normalizado a partir de un objeto `videos` de Stremio. */
function normalizeEpisode(video, index) {
  const season = Number.parseInt(video?.season, 10);
  const episode = Number.parseInt(video?.episode, 10);
  return {
    season: Number.isFinite(season) ? season : null,
    episode: Number.isFinite(episode) ? episode : (index + 1),
    absolute: Number.parseInt(video?.absoluteEpisode, 10),
  };
}

/**
 * Resuelve un episodio absoluto (`kitsu:46474:3`) a temporada/episodio usando
 * la lista `videos` del addon de anime: es la misma fuente que usa Stremio, de
 * modo que el número de episodio coincide con el que ve el usuario.
 */
export function resolveEpisode(videos, season, episode) {
  if (!Array.isArray(videos) || !videos.length || episode === null) return { season, episode };
  const entries = videos.map(normalizeEpisode);

  if (season !== null) {
    const exact = entries.find((entry) => entry.season === season && entry.episode === episode);
    if (exact) return { season: exact.season, episode: exact.episode };
  }

  // Sin temporada: el número puede ser absoluto o relativo a la única temporada.
  const byAbsolute = entries.find((entry) => entry.absolute === episode);
  if (byAbsolute) {
    return { season: byAbsolute.season ?? season ?? 1, episode: byAbsolute.episode };
  }

  const seasons = [...new Set(entries.map((entry) => entry.season).filter((value) => value !== null))];
  if (seasons.length === 1) {
    return { season: seasons[0], episode };
  }

  const sameEpisode = entries.filter((entry) => entry.episode === episode);
  if (sameEpisode.length === 1) {
    return { season: sameEpisode[0].season ?? season ?? 1, episode: sameEpisode[0].episode };
  }

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
