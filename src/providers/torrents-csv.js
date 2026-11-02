import { endpoint } from '../endpoints.js';
import { getJson } from '../lib/http.js';
import { normalizeTorrent } from '../lib/torrent.js';
import { toTsQuery } from '../lib/query.js';
import { PROVIDER_LABELS } from '../config.js';

export const id = 'torrentsCsv';
export const label = PROVIDER_LABELS.torrentsCsv;
export const kind = 'torrent';

const PAGE_SIZE = 25; // fijado en el servidor: src/main.rs (DEFAULT_SIZE)

/**
 * torrents-csv (GET /service/search?q=&after=).
 * https://torrents-csv.com/ · https://codeberg.org/heretic/torrents-csv-server
 *
 * Detalles verificados en el código del servidor:
 *  - `q` se convierte en `to_tsquery` reemplazando espacios por `&` (AND).
 *  - `size` NO existe como parámetro (la home lo menciona, el servidor lo ignora).
 *  - devuelve 25 resultados y un cursor `next` para `after`.
 *  - `q` debe tener al menos 3 caracteres o responde 400.
 */
export async function search(query, ctx) {
  const terms = toTsQuery(query.text.replace(/[._+]/g, ' '));
  if (terms.length < 3) return [];

  const pages = Math.min(3, Math.max(1, Math.ceil(ctx.config.perProviderLimit / PAGE_SIZE)));
  const base = `${endpoint('torrentsCsv', ctx.config)}/service/search`;
  const results = [];
  let after = 0;

  for (let page = 0; page < pages; page++) {
    const url = new URL(base);
    url.searchParams.set('q', terms);
    if (after) url.searchParams.set('after', String(after));

    const data = await ctx.cached(`torrentsCsv:${url}`, ctx.config.cacheTtl * 1000, async () => {
      await ctx.limiters.torrentsCsv.acquire('torrentsCsv');
      return getJson(url.toString(), { timeout: ctx.config.httpTimeout });
    });

    const torrents = Array.isArray(data?.torrents) ? data.torrents : [];
    for (const item of torrents) {
      const normalized = normalizeTorrent({
        provider: id,
        name: item.name,
        infoHash: item.infohash,
        sizeBytes: item.size_bytes,
        seeders: item.seeders,
        leechers: item.leechers,
        published: item.created_unix ? new Date(Number(item.created_unix) * 1000).toISOString() : '',
        query: query.text,
      });
      if (normalized) results.push(normalized);
    }

    const next = Number(data?.next);
    if (!Number.isFinite(next) || next <= after || torrents.length < PAGE_SIZE) break;
    after = next;
  }

  return results;
}
