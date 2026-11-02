import { endpoint } from './endpoints.js';
import { getJson } from './lib/http.js';

function extractYear(value) {
  const match = String(value || '').match(/\b(?:19|20)\d{2}\b/);
  return match ? match[0] : '';
}

/**
 * Resuelve un id IMDb a título/año usando Cinemeta (el addon de metadatos por
 * defecto de Stremio). Los ids `release:` no necesitan metadatos externos.
 */
export async function resolveMeta(parsed, ctx) {
  if (!parsed) return null;

  if (parsed.releaseName) {
    return {
      name: parsed.releaseName,
      year: extractYear(parsed.releaseName),
      imdbId: null,
      type: parsed.type,
      fromRelease: true,
    };
  }
  if (!parsed.imdbId) return null;

  const url = `${endpoint('cinemeta', ctx.config)}/meta/${parsed.type}/${parsed.imdbId}.json`;
  const json = await ctx.cached(`cinemeta:${url}`, 24 * 60 * 60 * 1000, async () => {
    await ctx.limiters.cinemeta.acquire('cinemeta');
    return getJson(url, { timeout: Math.min(ctx.config.httpTimeout, 8000) });
  });

  const meta = json?.meta;
  if (!meta?.name) return null;
  return {
    name: String(meta.name),
    year: String(meta.year || meta.releaseInfo || '') || extractYear(meta.released),
    imdbId: parsed.imdbId,
    type: parsed.type,
    fromRelease: false,
    poster: meta.poster || '',
  };
}
