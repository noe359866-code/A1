import { endpoint } from '../endpoints.js';
import { getJson } from '../lib/http.js';
import { normalizeTorrent } from '../lib/torrent.js';
import { PROVIDER_LABELS } from '../config.js';

export const id = 'knaben';
export const label = PROVIDER_LABELS.knaben;
export const kind = 'torrent';

/**
 * Knaben API v2 (GET /v2/search).
 * https://knaben.org/api/v2/
 * No enviamos los flags `xxx`, `unsafe` ni `dead`: por defecto el índice ya
 * oculta contenido adulto, resultados inseguros y torrents sin seeders.
 */
export async function search(query, ctx) {
  const url = new URL(`${endpoint('knaben', ctx.config)}/v2/search`);
  url.searchParams.set('q', query.text.replace(/[._+]/g, ' ').replace(/\s+/g, ' ').trim());
  url.searchParams.set('sf', 'title');
  url.searchParams.set('o', 'seeders');
  url.searchParams.set('d', 'desc');
  url.searchParams.set('s', String(Math.min(300, ctx.config.perProviderLimit)));

  const data = await ctx.cached(`knaben:${url}`, ctx.config.cacheTtl * 1000, async () => {
    await ctx.limiters.knaben.acquire('knaben');
    return getJson(url.toString(), { timeout: ctx.config.httpTimeout });
  });

  const hits = Array.isArray(data?.hits) ? data.hits : [];
  return hits.map((hit) => {
    if (Number(hit?.virusDetection) > 0.75) return null;
    return normalizeTorrent({
      provider: id,
      name: hit.title || hit.description || '',
      infoHash: hit.hash || '',
      magnet: hit.magnetUrl || '',
      sizeBytes: hit.bytes,
      seeders: hit.liveSeeders ?? hit.seeders,
      leechers: hit.livePeers ?? hit.peers,
      detailUrl: hit.details || '',
      published: hit.date || hit.lastSeen || '',
      query: query.text,
    });
  }).filter(Boolean);
}
