import { enabledReleaseProviders } from './providers/index.js';
import { encodeReleaseId, normalizeText, matchesTitle, parseVideoId } from './lib/query.js';

export const CATALOG_ID = 'omni-scene';

function prettyName(name = '') {
  return String(name).replace(/[._+]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 140);
}

function toMetaPreview(release, type) {
  const info = [release.section, release.group].filter(Boolean).join(' · ');
  return {
    id: encodeReleaseId(release.name),
    type,
    name: prettyName(release.name),
    releaseInfo: info.slice(0, 60) || release.date?.slice(0, 10) || '',
    description: [
      `Release: ${release.name}`,
      info ? `Sección / grupo: ${info}` : '',
      release.source ? `Fuente: ${release.source}` : '',
    ].filter(Boolean).join('\n'),
    posterShape: 'poster',
  };
}

async function safe(ctx, provider, task) {
  try {
    const result = await task();
    return Array.isArray(result) ? result : [];
  } catch (error) {
    ctx.noteFailure(provider.id, error);
    return [];
  }
}

/**
 * Catálogo opcional de releases scene/P2P. Si el usuario busca, consultamos
 * las tres APIs de metadatos; si no, mostramos los releases recientes.
 * Los ids `release:` permiten abrir la ficha y pedir streams por nombre exacto.
 */
export async function handleCatalog(type, catalogId, extra = {}, ctx) {
  const empty = { metas: [], cacheMaxAge: 300 };
  if (!ctx.config.enableCatalog || catalogId !== CATALOG_ID) return empty;

  const search = String(extra.search || '').trim();
  const skip = Math.max(0, Number.parseInt(extra.skip, 10) || 0);
  const providers = enabledReleaseProviders(ctx.config);

  let releases;
  if (search) {
    const query = {
      text: search,
      title: search,
      year: '',
      imdbId: null,
      type,
      season: null,
      episode: null,
      mode: 'title',
    };
    const groups = await Promise.all(providers.map((provider) =>
      safe(ctx, provider, () => provider.search(query, ctx))));
    releases = groups.flat().filter((release) => matchesTitle(release.name, search, 0.4));
  } else {
    const groups = await Promise.all(providers
      .filter((provider) => typeof provider.recent === 'function')
      .map((provider) => safe(ctx, provider, () => provider.recent(ctx, 30))));
    releases = groups.flat();
  }

  const seen = new Set();
  const unique = [];
  for (const release of releases) {
    const key = normalizeText(release.name);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    unique.push(release);
  }

  return {
    metas: unique.slice(skip, skip + 40).map((release) => toMetaPreview(release, type)),
    cacheMaxAge: 300,
  };
}

/** Ficha mínima para los items del catálogo (no hay metadatos en Cinemeta). */
export async function handleMeta(type, id, ctx) {
  const parsed = parseVideoId(type, id);
  if (!parsed?.releaseName) return { meta: null };
  return {
    meta: {
      id,
      type,
      name: prettyName(parsed.releaseName),
      releaseInfo: 'Scene / P2P release',
      description: `Release: ${parsed.releaseName}\nLos streams se buscan por nombre exacto en los índices de torrents configurados.`,
      posterShape: 'poster',
      behaviorHints: { defaultVideoId: id, hasScheduledVideos: false },
    },
  };
}
