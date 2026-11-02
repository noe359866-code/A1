import { endpoint } from '../endpoints.js';
import { getJson } from '../lib/http.js';
import { toPredbPrefix } from '../lib/query.js';
import { PROVIDER_LABELS } from '../config.js';

export const id = 'predbNet';
export const label = PROVIDER_LABELS.predbNet;
export const kind = 'release';

/**
 * PreDB.net (GET /?type=search&q=...).
 * https://predb.net/api-documentation/
 * Límite: 30 peticiones / 60 s. `q` busca desde el INICIO del nombre del
 * release, por lo que el título se envía con puntos ("The.Matrix").
 */
export async function search(query, ctx) {
  const prefix = toPredbPrefix(query.mode === 'exact' ? query.text : query.title);
  if (!prefix) return [];

  const url = new URL(`${endpoint('predbNet', ctx.config)}/`);
  url.searchParams.set('type', 'search');
  url.searchParams.set('q', prefix);
  url.searchParams.set('limit', '25');
  url.searchParams.set('order_by', 'pretime');
  url.searchParams.set('sort', 'DESC');

  const data = await ctx.cached(`predbNet:${url}`, ctx.config.cacheTtl * 1000, async () => {
    await ctx.limiters.predbNet.acquire('predbNet');
    return getJson(url.toString(), { timeout: ctx.config.httpTimeout });
  });

  if (data?.status !== 'success' || !Array.isArray(data.data)) return [];
  return data.data
    .filter((item) => item?.release && Number(item.status) !== 1)
    .map((item) => ({
      name: String(item.release),
      provider: id,
      source: label,
      section: item.section || '',
      group: item.group || '',
      sizeBytes: Number(item.size) > 0 ? Math.round(Number(item.size) * 1024 * 1024) : null,
      date: Number(item.pretime) ? new Date(Number(item.pretime) * 1000).toISOString() : '',
      url: item.url ? `https://predb.net${item.url}` : '',
      imdb: null,
    }));
}

/** Feed de releases recientes para el catálogo (sin parámetro q). */
export async function recent(ctx, limit = 20) {
  const url = new URL(`${endpoint('predbNet', ctx.config)}/`);
  url.searchParams.set('type', 'search');
  url.searchParams.set('limit', String(Math.min(100, limit)));
  url.searchParams.set('order_by', 'pretime');
  url.searchParams.set('sort', 'DESC');
  const data = await ctx.cached(`predbNet:recent:${url}`, 5 * 60 * 1000, async () => {
    await ctx.limiters.predbNet.acquire('predbNet');
    return getJson(url.toString(), { timeout: ctx.config.httpTimeout });
  });
  if (!Array.isArray(data?.data)) return [];
  return data.data.filter((item) => item?.release).map((item) => ({
    name: String(item.release),
    provider: id,
    source: label,
    section: item.section || '',
    group: item.group || '',
    url: item.url ? `https://predb.net${item.url}` : '',
  }));
}
