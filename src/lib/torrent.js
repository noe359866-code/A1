import { normalizeText, titleTokens, matchesTitle } from './query.js';

/** Trackers públicos usados cuando un proveedor sólo entrega el infohash. */
export const DEFAULT_TRACKERS = Object.freeze([
  'udp://tracker.opentrackr.org:1337/announce',
  'udp://open.demonii.com:1337/announce',
  'udp://open.stealth.si:80/announce',
  'udp://tracker.torrent.eu.org:451/announce',
  'udp://tracker.qu.ax:6969/announce',
  'udp://tracker.theoks.net:6969/announce',
  'udp://tracker.corpscorp.online:80/announce',
  'udp://tracker.tiny-vps.com:6969/announce',
]);

const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
const HEX40 = /^[0-9a-f]{40}$/i;
const BASE32_32 = /^[a-z2-7]{32}$/i;
const NON_VIDEO = /\.(mp3|flac|wav|aac|m4a|ogg|epub|pdf|mobi|azw3|cbr|cbz|zip|rar|7z|exe|msi|apk|nfo|txt|jpe?g|png|torrent|srt|iso|bin|cue)\b/i;
const CAM = /\b(cam|camrip|hdcam|telesync|telecine|hc-?hdrip|ts-?rip|hcts)\b/i;

export function base32ToHex(value) {
  let binary = '';
  for (const char of String(value).toUpperCase()) {
    const index = BASE32.indexOf(char);
    if (index < 0) return null;
    binary += index.toString(2).padStart(5, '0');
  }
  if (binary.length < 160) return null;
  const bytes = [];
  for (let i = 0; i + 8 <= binary.length && bytes.length < 20; i += 8) {
    bytes.push(Number.parseInt(binary.slice(i, i + 8), 2));
  }
  return bytes.length === 20 ? Buffer.from(bytes).toString('hex') : null;
}

export function normalizeInfoHash(value) {
  if (!value) return null;
  let candidate = String(value).trim();
  if (candidate.toLowerCase().startsWith('magnet:')) return parseMagnet(candidate)?.infoHash ?? null;
  const urn = candidate.match(/urn:btih:([^&\s]+)/i);
  if (urn) candidate = urn[1];
  candidate = candidate.replace(/[^a-zA-Z0-9]/g, '');
  if (HEX40.test(candidate)) return candidate.toLowerCase();
  if (BASE32_32.test(candidate)) return base32ToHex(candidate);
  return null;
}

export function parseMagnet(magnet) {
  if (typeof magnet !== 'string' || !magnet.toLowerCase().startsWith('magnet:')) return null;
  let url;
  try {
    url = new URL(magnet);
  } catch {
    return null;
  }
  const xt = url.searchParams.get('xt') || '';
  const btih = /^urn:btih:(.+)$/i.exec(xt)?.[1] || '';
  const trackers = url.searchParams.getAll('tr')
    .map((tracker) => tracker.trim())
    .filter((tracker) => /^(udp|https?|wss?):\/\//i.test(tracker));
  return {
    infoHash: normalizeInfoHash(btih),
    trackers,
    displayName: url.searchParams.get('dn') || '',
  };
}

export function humanSize(bytes) {
  const value = Number(bytes);
  if (!Number.isFinite(value) || value <= 0) return '?';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const exponent = Math.min(units.length - 1, Math.floor(Math.log(value) / Math.log(1024)));
  const scaled = value / (1024 ** exponent);
  return `${scaled >= 100 || exponent === 0 ? Math.round(scaled) : scaled.toFixed(1)} ${units[exponent]}`;
}

export function qualityOf(name = '') {
  const text = String(name).toLowerCase();
  let label = 'SD';
  let score = 0;
  if (/2160p|\b4k\b|\buhd\b/.test(text)) { label = '4K'; score = 40; }
  else if (/1080[pi]/.test(text)) { label = '1080p'; score = 30; }
  else if (/720p/.test(text)) { label = '720p'; score = 20; }
  else if (/480p|dvd.?rip|\bdvdr\b/.test(text)) { label = '480p'; score = 10; }
  if (/remux|bluray|blu-ray|bdrip|brrip/.test(text)) score += 12;
  else if (/web-?dl|webdl|web-?rip|hdtv/.test(text)) score += 8;
  if (/hdr10|hdr|dolby.?vision|\b10bit\b|hevc|x265|av1/.test(text)) score += 6;
  if (/\bproper\b|\brepack\b/.test(text)) score += 3;
  if (CAM.test(text)) score -= 45;
  return { label, score };
}

export function parseSeasonEpisode(name = '') {
  const text = String(name).toLowerCase().replace(/[._+]/g, ' ');
  let match = text.match(/\bs(\d{1,2})\s*e(\d{1,3})\b/);
  if (!match) match = text.match(/\b(\d{1,2})x(\d{2,3})\b/);
  if (!match) match = text.match(/season\s*(\d{1,2})\s*episode\s*(\d{1,3})\b/);
  if (match) {
    return { season: Number(match[1]), episode: Number(match[2]), isPack: false };
  }
  const pack = text.match(/\bs(\d{1,2})\b/) || text.match(/season\s*(\d{1,2})\b/) ||
    text.match(/series\s*(\d{1,2})\b/);
  if (pack) return { season: Number(pack[1]), episode: null, isPack: true };
  return { season: null, episode: null, isPack: false };
}

function asInt(value) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

function asBytes(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? Math.round(parsed) : null;
}

/** Normaliza cualquier resultado de proveedor a la forma interna de torrent. */
export function normalizeTorrent(raw = {}) {
  const magnet = typeof raw.magnet === 'string' ? raw.magnet : '';
  const parsedMagnet = magnet ? parseMagnet(magnet) : null;
  const infoHash = normalizeInfoHash(raw.infoHash || raw.hash || magnet || parsedMagnet?.infoHash);
  const name = String(raw.name || raw.title || '').trim().slice(0, 260);
  if (!infoHash || !name) return null;

  const trackers = [...new Set([
    ...(Array.isArray(raw.trackers) ? raw.trackers : []),
    ...(parsedMagnet?.trackers || []),
  ])].filter((tracker) => typeof tracker === 'string' && /^(udp|https?|wss?):\/\//i.test(tracker));

  return {
    infoHash,
    name,
    sizeBytes: asBytes(raw.sizeBytes),
    seeders: asInt(raw.seeders),
    leechers: asInt(raw.leechers),
    magnet,
    trackers,
    detailUrl: raw.detailUrl || '',
    published: raw.published || '',
    providers: Array.isArray(raw.providers) && raw.providers.length ? [...new Set(raw.providers)] : [raw.provider || 'desconocido'],
    queries: Array.isArray(raw.queries) ? [...new Set(raw.queries)] : [raw.query || ''].filter(Boolean),
    fromExpansion: Boolean(raw.fromExpansion),
  };
}

function maxNullable(a, b) {
  if (a === null || a === undefined) return b ?? null;
  if (b === null || b === undefined) return a;
  return Math.max(a, b);
}

/** Dedupe por infohash: conserva el mejor dato de cada proveedor. */
export function mergeTorrents(torrents = []) {
  const map = new Map();
  for (const item of torrents) {
    const torrent = normalizeTorrent(item);
    if (!torrent) continue;
    const existing = map.get(torrent.infoHash);
    if (!existing) {
      map.set(torrent.infoHash, torrent);
      continue;
    }
    existing.seeders = maxNullable(existing.seeders, torrent.seeders);
    existing.leechers = maxNullable(existing.leechers, torrent.leechers);
    existing.sizeBytes = existing.sizeBytes ?? torrent.sizeBytes;
    existing.magnet = existing.magnet || torrent.magnet;
    existing.detailUrl = existing.detailUrl || torrent.detailUrl;
    existing.published = existing.published || torrent.published;
    existing.providers = [...new Set([...existing.providers, ...torrent.providers])];
    existing.queries = [...new Set([...existing.queries, ...torrent.queries])];
    existing.trackers = [...new Set([...existing.trackers, ...torrent.trackers])].slice(0, 20);
    existing.fromExpansion = existing.fromExpansion || torrent.fromExpansion;
    if (torrent.name.length > existing.name.length) existing.name = torrent.name;
  }
  return [...map.values()];
}

/** Trackers en el formato que espera Stremio (`sources`). */
export function streamSources(torrent) {
  const list = [...new Set([...(torrent.trackers || []), ...DEFAULT_TRACKERS])].slice(0, 14);
  return list.map((tracker) => `tracker:${tracker}`);
}

function scoreTorrent(torrent, query, config) {
  const name = torrent.name;
  const normalized = normalizeText(name);
  const tokens = titleTokens(query.title);
  let score = 0;

  if (query.mode === 'exact') {
    const queryTokens = normalizeText(query.text).split(' ').filter(Boolean);
    const overlap = queryTokens.length
      ? queryTokens.filter((token) => normalized.includes(token)).length / queryTokens.length
      : 0;
    if (overlap < 0.5) return Number.NEGATIVE_INFINITY;
    score += overlap * 180 + 40;
  } else {
    if (!matchesTitle(name, query.title, 0.6)) return Number.NEGATIVE_INFINITY;
    const found = tokens.filter((token) => normalized.includes(token)).length;
    score += (tokens.length ? found / tokens.length : 1) * 110;
  }

  if (query.year) {
    const years = (name.match(/\b(?:19|20)\d{2}\b/g) || []).map(Number);
    const wanted = Number(query.year);
    if (years.length && !years.some((year) => Math.abs(year - wanted) <= 1)) score -= 45;
    else if (normalized.includes(String(query.year))) score += 22;
  }

  if (query.type === 'series' && query.season !== null) {
    const found = parseSeasonEpisode(name);
    if (found.episode !== null && query.episode !== null &&
        (found.season !== query.season || found.episode !== query.episode)) {
      score -= 120;
    } else if (found.episode !== null && found.season === query.season && found.episode === query.episode) {
      score += 80;
    } else if (found.isPack && found.season === query.season) {
      score += 20;
    } else if (found.season !== null && found.season !== query.season) {
      score -= 90;
    } else if (found.season === null) {
      score -= 25;
    }
  }

  const quality = qualityOf(name);
  const seeders = torrent.seeders ?? 0;
  const weights = config.sortBy === 'seeders'
    ? { quality: 0.5, seeds: 1.8 }
    : config.sortBy === 'quality'
      ? { quality: 2, seeds: 0.4 }
      : { quality: 1, seeds: 1 };

  score += quality.score * weights.quality;
  score += Math.log10(seeders + 1) * 30 * weights.seeds;
  if (torrent.providers.length > 1) score += 15;
  if (torrent.fromExpansion) score += 10;
  if (torrent.sizeBytes && torrent.sizeBytes < 20 * 1024 * 1024) score -= 15;

  return score;
}

/**
 * Filtra, puntúa y ordena. Devuelve como mucho `config.maxStreams` torrents,
 * enriquecidos con `score` y `quality`.
 */
export function rankTorrents(torrents, { query, config }) {
  const minSeeders = Number(config.minSeeders) || 0;
  const ranked = [];

  for (const torrent of torrents) {
    if (!torrent?.infoHash || !torrent.name) continue;
    if (NON_VIDEO.test(torrent.name)) continue;
    if (config.excludeCam && CAM.test(torrent.name)) continue;
    if (minSeeders > 0 && torrent.seeders !== null && torrent.seeders < minSeeders) continue;

    const score = scoreTorrent(torrent, query, config);
    if (!Number.isFinite(score)) continue;
    ranked.push({ ...torrent, score, quality: qualityOf(torrent.name) });
  }

  ranked.sort((a, b) => b.score - a.score || (b.seeders ?? 0) - (a.seeders ?? 0) || a.name.localeCompare(b.name));
  return ranked.slice(0, Math.max(1, Number(config.maxStreams) || 40));
}
