/**
 * Construcción y saneamiento de consultas. Cada proveedor tiene sus propias
 * reglas (ver docs/API-RESEARCH.md):
 *  - Knaben / Torlock: texto libre con espacios.
 *  - torrents-csv: Postgres to_tsquery (los espacios se convierten en &).
 *  - predb.net: búsqueda por PREFIJO, funciona mejor con puntos.
 *  - xREL: búsqueda por texto libre; devuelve URIs IMDb.
 *  - predataba.se: array de filtros (con clave API admite texto libre).
 */

import { decodeBase64Url, encodeBase64Url } from './encoding.js';

export function normalizeText(value = '') {
  return String(value)
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

export function titleTokens(value = '') {
  const stop = new Set(['the', 'a', 'an', 'of', 'and', 'el', 'la', 'los', 'las', 'de', 'del']);
  return normalizeText(value).split(' ').filter((token) => token && !stop.has(token));
}

export function pad2(value) {
  return String(Number(value) || 0).padStart(2, '0');
}

/**
 * Prefijos de id que Stremio envía cuando la ficha viene de un addon de
 * catálogo de anime (Anime Kitsu, AniList, MAL, AniDB, TMDB...). Antes de este
 * soporte el addon descartaba el id y devolvía `streams: []` para todas las
 * series y películas de anime abiertas desde esos catálogos.
 */
export const ANIME_ID_PREFIXES = Object.freeze(['kitsu', 'mal', 'anilist', 'anidb', 'anime']);

const ANIME_ID = /^(kitsu|mal|anilist|anidb|anime):([A-Za-z0-9-]+)$/;

/**
 * Convierte el id de vídeo de Stremio en datos estructurados.
 *
 * Formatos aceptados:
 *  - `tt1234567` y `tt1234567:2:5`      -> IMDb (Cinemeta)
 *  - `kitsu:46474:1:3`, `mal:16498`,
 *    `anilist:21`, `anidb:1`, `anime:21` -> anime (Anime Kitsu / Kitsu API)
 *  - `release:<base64url>`              -> nombre de release scene/P2P
 *
 * En los ids de anime Stremio usa `id:temporada:episodio` igual que en IMDb,
 * pero también llega `kitsu:46474:3` (sólo episodio) cuando el addon de origen
 * numera en absoluto: ese tercer segmento se interpreta como episodio de la
 * temporada 1 y se resuelve después con los metadatos del anime.
 */
export function parseVideoId(type, id) {
  const raw = String(id || '');
  const result = {
    // `anime` no distingue película de serie: se resuelve con los metadatos.
    type: type === 'movie' ? 'movie' : 'series',
    rawId: raw,
    imdbId: null,
    animeId: null,
    animeSource: null,
    season: null,
    episode: null,
    releaseName: null,
  };

  if (raw.startsWith('release:')) {
    const encoded = raw.slice('release:'.length);
    try {
      result.releaseName = decodeBase64Url(encoded).trim().slice(0, 200) || null;
    } catch {
      result.releaseName = null;
    }
    return result;
  }

  // `kitsu:46474:1:3` -> prefijo `kitsu`, id `46474`, y los segmentos que
  // siguen son temporada/episodio (o sólo episodio).
  const [prefix, head, season, episode] = raw.split(':');
  if (ANIME_ID_PREFIXES.includes(prefix)) {
    const anime = ANIME_ID.exec(`${prefix}:${head || ''}`);
    result.animeSource = prefix;
    result.animeId = anime ? Number.parseInt(anime[2], 10) : Number.NaN;
    if (!Number.isFinite(result.animeId)) return null;
    if (episode !== undefined) {
      const s = Number.parseInt(season, 10);
      const e = Number.parseInt(episode, 10);
      if (Number.isFinite(s)) result.season = s;
      if (Number.isFinite(e)) result.episode = e;
    } else if (season !== undefined) {
      // `kitsu:46474:3` -> episodio 3; la temporada se deduce de los metadatos.
      const e = Number.parseInt(season, 10);
      if (Number.isFinite(e)) {
        result.season = null;
        result.episode = e;
      }
    }
    return result;
  }

  // El manifiesto anuncia `tt` (no `tt:`), así que el head de IMDb llega entero
  // en el primer segmento: `tt1375666`, `tt0903747:1:2`.
  const imdbHead = /^tt\d{5,}$/.test(prefix) ? prefix : head;
  if (!/^tt\d{5,}$/.test(imdbHead || '')) return null;
  result.imdbId = imdbHead;
  const rawSeason = imdbHead === prefix ? head : season;
  const rawEpisode = imdbHead === prefix ? season : episode;
  if (result.type === 'series' && rawSeason !== undefined && rawEpisode !== undefined) {
    const s = Number.parseInt(rawSeason, 10);
    const e = Number.parseInt(rawEpisode, 10);
    if (Number.isFinite(s) && Number.isFinite(e)) {
      result.season = s;
      result.episode = e;
    }
  }
  return result;
}

export function encodeReleaseId(name) {
  return `release:${encodeBase64Url(String(name).slice(0, 200))}`;
}

/** Extrae el título legible de un nombre de release (antes del año/calidad). */
export function releaseTitle(name = '') {
  const parts = String(name).replace(/[._+]+/g, ' ').split(/\s+/).filter(Boolean);
  const stop = /^(?:(?:19|20)\d{2}|\d{3,4}p|4k|uhd|bluray|bdrip|brrip|web-?dl|web-?rip|hdtv|x26[45]|h26[45]|hevc|av1|remux|hdr|dts|aac|ac3|ddp|proper|repack|internal|complete|season|s\d+e?\d*|e\d+)$/i;
  const kept = [];
  for (const part of parts) {
    if (stop.test(part)) break;
    kept.push(part);
    if (kept.length >= 6) break;
  }
  return kept.join(' ') || String(name).slice(0, 60);
}

/**
 * Consultas ordenadas por precisión. La primera se usa siempre; las siguientes
 * sólo si la primera no devuelve suficientes resultados.
 */
export function buildQueries(parsed, meta) {
  const title = meta.name;
  if (!title) return [];

  if (parsed.releaseName) {
    return [makeQuery({ text: parsed.releaseName, title: releaseTitle(parsed.releaseName), mode: 'exact', parsed })];
  }

  const year = meta.year ? String(meta.year).slice(0, 4) : '';
  const common = { title, year, imdbId: parsed.imdbId, parsed, mode: 'title' };

  // Los nombres de release de anime mezclan numeración absoluta (One Piece 1071),
  // temporada/episodio (S01E03) y packs de temporada. Probamos las tres formas.
  if (isAnime(parsed) && parsed.type === 'series' && parsed.episode !== null) {
    const absolute = Math.max(0, Number(parsed.episode) || 0);
    const absoluteTag = absolute >= 1000 ? String(absolute) : String(absolute).padStart(2, '0');
    const queries = [];
    if (parsed.season !== null) {
      queries.push(makeQuery({
        ...common,
        text: `${title} S${pad2(parsed.season)}E${pad2(parsed.episode)}`,
        season: parsed.season,
        episode: parsed.episode,
      }));
    }
    queries.push(makeQuery({
      ...common,
      text: `${title} ${absoluteTag}`,
      season: parsed.season ?? 1,
      episode: absolute,
    }));
    queries.push(makeQuery({
      ...common,
      text: year ? `${title} ${year}` : title,
      season: parsed.season ?? 1,
      episode: null,
    }));
    queries.push(makeQuery({ ...common, text: title, season: null, episode: null }));
    return dedupeQueries(queries);
  }

  if (parsed.type === 'series' && parsed.season !== null && parsed.episode !== null) {
    const tag = `S${pad2(parsed.season)}E${pad2(parsed.episode)}`;
    const seasonTag = `S${pad2(parsed.season)}`;
    return [
      makeQuery({ ...common, text: `${title} ${tag}` }),
      makeQuery({ ...common, text: `${title} ${seasonTag}` }),
      makeQuery({ ...common, text: title }),
    ];
  }

  return [
    makeQuery({ ...common, text: year ? `${title} ${year}` : title }),
    makeQuery({ ...common, text: title }),
  ];
}

/** ¿El id viene de un catálogo de anime en lugar de IMDb? */
export function isAnime(parsed) {
  return Boolean(parsed && parsed.animeId);
}

function dedupeQueries(queries) {
  const seen = new Set();
  return queries.filter((query) => {
    if (!query.text || seen.has(query.text)) return false;
    seen.add(query.text);
    return true;
  });
}

/** Título alternativo de anime (romaji/inglés) útil para buscar en los índices. */
export function alternateTitles(meta) {
  const primary = normalizeText(meta?.name || '');
  const list = Array.isArray(meta?.titles) ? meta.titles : [];
  const seen = new Set();
  return list
    .map((title) => String(title || '').trim())
    .filter((title) => {
      if (title.length < 3) return false;
      const key = normalizeText(title);
      if (!key || key === primary || seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .slice(0, 3);
}


function makeQuery({ text, title, year = '', imdbId = null, season = null, episode = null, parsed = {}, mode = 'title' }) {
  return {
    text: String(text).trim().slice(0, 180),
    title: String(title).trim().slice(0, 150),
    year,
    imdbId,
    type: parsed.type || 'movie',
    season: season ?? parsed.season ?? null,
    episode: episode ?? parsed.episode ?? null,
    mode,
  };
}

/** Convierte texto libre en algo seguro para to_tsquery (torrents-csv). */
export function toTsQuery(text) {
  const terms = normalizeText(text)
    .split(' ')
    .map((term) => term.replace(/[^a-z0-9]/g, ''))
    .filter((term) => term.length >= 2)
    .slice(0, 6);
  return terms.join(' ');
}

/** predb.net busca desde el inicio del nombre del release: "The.Matrix". */
export function toPredbPrefix(title) {
  return String(title)
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9]+/g, '.')
    .replace(/^\.+|\.+$/g, '')
    .slice(0, 90);
}

/** ¿El nombre de un release/torrent contiene suficientes tokens del título? */
export function matchesTitle(name, title, ratio = 0.6) {
  const tokens = titleTokens(title);
  if (!tokens.length) return true;
  const haystack = normalizeText(name);
  const found = tokens.filter((token) => haystack.includes(token)).length;
  return found >= Math.max(1, Math.ceil(tokens.length * ratio));
}
