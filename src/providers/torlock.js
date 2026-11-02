import { endpoint } from '../endpoints.js';
import { getText } from '../lib/http.js';
import { parseTorznab } from '../lib/xml.js';
import { normalizeTorrent, normalizeInfoHash } from '../lib/torrent.js';
import { PROVIDER_LABELS } from '../config.js';

export const id = 'torlock';
export const label = PROVIDER_LABELS.torlock;
export const kind = 'torrent';

/**
 * Torlock Torznab (GET /torznab/api?t=...).
 * https://www.torlock.com/apis.html
 * Sin API key. Categorías Newznab: 2000 películas, 5000 TV.
 * "Please cache on your side and don't re-request the same URL more than
 *  once a minute" -> TTL mínimo de 60 s, incluso si el usuario pide menos.
 */
export async function search(query, ctx) {
  const isSeries = query.type === 'series' && query.season !== null;
  const t = isSeries ? 'tvsearch' : query.type === 'movie' ? 'movie-search' : 'search';
  const url = new URL(`${endpoint('torlock', ctx.config, ctx.env)}/torznab/api`);
  url.searchParams.set('t', t);
  url.searchParams.set('q', (query.mode === 'exact' ? query.text : query.title)
    .replace(/[._+]/g, ' ').replace(/\s+/g, ' ').trim());
  url.searchParams.set('cat', query.type === 'series' ? '5000' : '2000');
  url.searchParams.set('limit', String(Math.min(100, ctx.config.perProviderLimit)));
  if (isSeries) {
    url.searchParams.set('season', String(query.season));
    if (query.episode !== null) url.searchParams.set('ep', String(query.episode));
  }

  const ttl = Math.max(60, ctx.config.cacheTtl) * 1000;
  const xml = await ctx.cached(`torlock:${url}`, ttl, async () => {
    await ctx.limiters.torlock.acquire('torlock');
    return getText(url.toString(), { env: ctx.env, timeout: ctx.config.httpTimeout, accept: 'application/rss+xml, application/xml, text/xml' });
  });

  return parseTorznab(xml).map((item) => {
    const infoHash = normalizeInfoHash(item.infoHashAttr || item.magnet);
    if (!infoHash) return null;
    return normalizeTorrent({
      provider: id,
      name: item.title,
      infoHash,
      magnet: item.magnet,
      sizeBytes: item.sizeBytes,
      seeders: item.seeders ?? null,
      leechers: item.leechers ?? null,
      detailUrl: item.link || item.guid,
      published: item.pubDate,
      query: query.text,
    });
  }).filter(Boolean);
}
