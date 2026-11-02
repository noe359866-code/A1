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

/** Convierte un id de vídeo de Stremio en datos estructurados. */
export function parseVideoId(type, id) {
  const raw = String(id || '');
  const result = {
    type: type === 'series' ? 'series' : 'movie',
    rawId: raw,
    imdbId: null,
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

  const [head, season, episode] = raw.split(':');
  if (!/^tt\d{5,}$/.test(head)) return null;
  result.imdbId = head;
  if (result.type === 'series' && season !== undefined && episode !== undefined) {
    const s = Number.parseInt(season, 10);
    const e = Number.parseInt(episode, 10);
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
