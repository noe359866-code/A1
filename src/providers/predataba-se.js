import { endpoint } from '../endpoints.js';
import { getJson, postJson } from '../lib/http.js';
import { matchesTitle } from '../lib/query.js';
import { PROVIDER_LABELS } from '../config.js';

export const id = 'predatabaSe';
export const label = PROVIDER_LABELS.predatabaSe;
export const kind = 'release';

/**
 * predatabase / predataba.se (GET|POST /api/search).
 * Swagger: https://api.predataba.se/ · especificación: https://predataba.se/api/documentation
 *
 * Notas verificadas:
 *  - El host `api.predataba.se` sirve la UI de Swagger; la API viva está en
 *    `https://predataba.se/api/...`.
 *  - `query` es un ARRAY JSON de filtros. POST lo envía como body; GET como
 *    query string. Usamos POST y caemos a GET si el servidor lo rechaza.
 *  - Sin `x-api-key` los filtros de texto libre son ignorados (sí funcionan
 *    `top:24h`, `top:week`, etc.). Por eso siempre filtramos por título en
 *    cliente: si la API devuelve el listado completo, se descarta.
 *  - `x-type` = scene | p2p. Usuarios anónimos: máximo 5 páginas.
 *  - `hash` es un identificador de release, NO un infohash de BitTorrent.
 */
function headersFor(ctx, type) {
  const headers = { 'x-type': type, engine: 'elasticsearch' };
  if (ctx.config.predatabaKey) headers['x-api-key'] = ctx.config.predatabaKey;
  return headers;
}

async function runSearch(filters, type, ctx) {
  const url = `${endpoint('predatabaSe', ctx.config, ctx.env)}/api/search?page=0`;
  const getUrl = new URL(url);
  getUrl.searchParams.set('query', JSON.stringify(filters));
  const key = `predatabaSe:${type}:${filters.join('|')}`;

  const data = await ctx.cached(key, ctx.config.cacheTtl * 1000, async () => {
    await ctx.limiters.predatabaSe.acquire(`predatabaSe:${type}`);
    try {
      return await postJson(url, filters, {
        env: ctx.env,
        headers: headersFor(ctx, type),
        timeout: ctx.config.httpTimeout,
      });
    } catch (error) {
      ctx.log(`predataba.se: POST falló (${error.message}), probando GET`);
      return getJson(getUrl.toString(), {
        env: ctx.env,
        headers: headersFor(ctx, type),
        timeout: ctx.config.httpTimeout,
      });
    }
  });

  const results = Array.isArray(data?.results) ? data.results : [];
  return results.filter((item) => item?.rlsname).map((item) => ({
    name: String(item.rlsname),
    provider: id,
    source: label,
    section: item.section || '',
    group: item.grp || '',
    sizeBytes: Number(item.size) > 0 ? Math.round(Number(item.size) * 1024 * 1024) : null,
    date: Number(item.ctime) ? new Date(Number(item.ctime) * 1000).toISOString() : '',
    url: item.hash && item.id ? `https://predataba.se/api/get/${item.id}/${item.hash}` : '',
    imdb: null,
  }));
}

function typesFor(ctx) {
  return ctx.config.predatabaType === 'both' ? ['scene', 'p2p'] : [ctx.config.predatabaType];
}

export async function search(query, ctx) {
  const filters = [query.mode === 'exact' ? query.text : query.title];
  const groups = await Promise.all(typesFor(ctx).map((type) => runSearch(filters, type, ctx)));
  return groups.flat().filter((release) => matchesTitle(release.name, query.title, 0.55));
}

/** Releases recientes (`top:24h`) para el catálogo; funciona sin API key. */
export async function recent(ctx, limit = 20) {
  const groups = await Promise.all(typesFor(ctx).map((type) => runSearch(['top:24h'], type, ctx)));
  return groups.flat().slice(0, Math.max(1, limit));
}
