import { endpoint } from '../endpoints.js';
import { getJson } from '../lib/http.js';
import { PROVIDER_LABELS } from '../config.js';

export const id = 'xrel';
export const label = PROVIDER_LABELS.xrel;
export const kind = 'release';

/**
 * xREL v2 (GET /v2/search/releases.json).
 * https://www.xrel.to/wiki/6320/api-search-releases.html?lang=en_US
 * Sin autenticación. Rate limit: 900/h y 2 búsquedas / 5 s.
 * La respuesta real usa `results` y `p2p_results` (el OpenAPI dice `result`),
 * y `ext_info.uris` suele incluir `imdb:tt...`, lo que permite descartar
 * releases de otras películas/series con gran precisión.
 */

function sizeInBytes(item, isP2p) {
  if (isP2p && Number(item.size_mb) > 0) return Math.round(Number(item.size_mb) * 1024 * 1024);
  const number = Number(item.size?.number);
  if (!Number.isFinite(number) || number <= 0) return null;
  const unit = String(item.size?.unit || 'MB').toUpperCase();
  const factor = unit.startsWith('G') ? 1024 ** 3 : unit.startsWith('T') ? 1024 ** 4 : unit.startsWith('K') ? 1024 : 1024 ** 2;
  return Math.round(number * factor);
}

function mapRelease(item, isP2p) {
  const uris = Array.isArray(item.ext_info?.uris) ? item.ext_info.uris : [];
  const imdbMatch = uris.map((uri) => String(uri).match(/^imdb:(tt\d+)$/i)).find(Boolean);
  return {
    name: String(item.dirname || ''),
    provider: id,
    source: label,
    section: isP2p
      ? [item.category?.meta_cat, item.category?.sub_cat].filter(Boolean).join(' / ')
      : item.video_type || item.ext_info?.type || '',
    group: (isP2p ? item.group?.name : item.group_name) || '',
    sizeBytes: sizeInBytes(item, isP2p),
    date: Number(item.time || item.pub_time) ? new Date(Number(item.time || item.pub_time) * 1000).toISOString() : '',
    url: item.link_href || '',
    imdb: imdbMatch ? imdbMatch[1] : null,
    season: Number.isFinite(Number(item.tv_season)) ? Number(item.tv_season) : null,
    episode: Number.isFinite(Number(item.tv_episode)) ? Number(item.tv_episode) : null,
  };
}

export async function search(query, ctx) {
  const url = new URL(`${endpoint('xrel', ctx.config)}/v2/search/releases.json`);
  url.searchParams.set('q', (query.mode === 'exact' ? query.text : query.title)
    .replace(/[._+]/g, ' ').replace(/\s+/g, ' ').trim());
  url.searchParams.set('scene', '1');
  url.searchParams.set('p2p', '1');
  url.searchParams.set('limit', '25');

  const data = await ctx.cached(`xrel:${url}`, ctx.config.cacheTtl * 1000, async () => {
    await ctx.limiters.xrel.acquire('xrel:search');
    return getJson(url.toString(), { timeout: ctx.config.httpTimeout });
  });

  const releases = [
    ...(Array.isArray(data?.results) ? data.results.map((item) => mapRelease(item, false)) : []),
    ...(Array.isArray(data?.p2p_results) ? data.p2p_results.map((item) => mapRelease(item, true)) : []),
  ];

  return releases.filter((release) => {
    if (!release.name) return false;
    // Si xREL conoce el IMDb del release y no coincide, es otra producción.
    if (query.imdbId && release.imdb && release.imdb !== query.imdbId) return false;
    return true;
  });
}

/** Últimos releases P2P para el catálogo (endpoint sin límite de búsqueda). */
export async function recent(ctx, limit = 20) {
  const url = new URL(`${endpoint('xrel', ctx.config)}/v2/p2p/releases.json`);
  url.searchParams.set('per_page', String(Math.min(100, Math.max(5, limit))));
  const data = await ctx.cached(`xrel:recent:${url}`, 5 * 60 * 1000, async () => {
    await ctx.limiters.xrel.acquire('xrel:recent');
    return getJson(url.toString(), { timeout: ctx.config.httpTimeout });
  });
  const list = Array.isArray(data?.list) ? data.list : Array.isArray(data?.results) ? data.results : [];
  return list.map((item) => mapRelease(item, true)).filter((release) => release.name);
}
