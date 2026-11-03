import {
  parseVideoId, buildQueries, matchesTitle, normalizeText, alternateTitles, isAnime,
} from './lib/query.js';
import { mergeTorrents, rankTorrents, streamSources, humanSize, qualityOf } from './lib/torrent.js';
import { resolveMeta, resolveEpisode } from './metadata.js';
import { enabledTorrentProviders, enabledReleaseProviders, byId } from './providers/index.js';
import { PROVIDER_LABELS } from './config.js';

async function safe(ctx, providerId, task) {
  try {
    const result = await task();
    return Array.isArray(result) ? result : [];
  } catch (error) {
    ctx.noteFailure(providerId, error);
    return [];
  }
}

const NON_VIDEO_SECTION = /^(mp3|flac|xxx|ebook|book|games?|apps?|software|0day|pre)/i;

/** Elige nombres de release útiles para ampliar la búsqueda de torrents. */
export function pickReleaseNames(releases, limit) {
  const seen = new Set();
  const candidates = [];
  for (const release of releases) {
    const name = String(release.name || '').trim();
    if (name.length < 8) continue;
    if (NON_VIDEO_SECTION.test(release.section || '')) continue;
    const key = normalizeText(name);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    candidates.push(name);
  }
  candidates.sort((a, b) => qualityOf(b).score - qualityOf(a).score || b.length - a.length);
  return candidates.slice(0, Math.max(0, limit));
}

function toStream(torrent) {
  const providers = torrent.providers.map((id) => PROVIDER_LABELS[id] || id).join(' + ');
  const quality = torrent.quality?.label || qualityOf(torrent.name).label;
  const description = [
    torrent.name,
    `👤 ${torrent.seeders ?? '?'} seeders  ·  💾 ${humanSize(torrent.sizeBytes)}  ·  ${quality}`,
    `🔎 ${providers}`,
    ...(torrent.fromExpansion ? ['🎯 Localizado a partir de un release scene/P2P'] : []),
  ].join('\n');

  return {
    name: `Omni ${quality}`,
    title: description,
    description,
    infoHash: torrent.infoHash,
    sources: streamSources(torrent),
    behaviorHints: {
      bingeGroup: `omniindex-${quality.toLowerCase()}`,
    },
  };
}

/**
 * Flujo de una petición /stream/{type}/{id}.json:
 *  1. Resolver título/año (Cinemeta) o nombre de release.
 *  2. Buscar en paralelo en los 3 índices con infohash.
 *  3. Buscar nombres de release en PreDB.net / predataba.se / xREL.
 *  4. Usar esos nombres exactos como consultas adicionales ("expansión").
 *  5. Consulta de respaldo si hay pocos resultados.
 *  6. Unificar por infohash, filtrar, puntuar y convertir a Stream Objects.
 */
export async function handleStream(type, id, ctx) {
  const empty = { streams: [], cacheMaxAge: ctx.config.cacheTtl };
  const parsed = parseVideoId(type, id);
  if (!parsed) return empty;

  let meta;
  try {
    meta = await resolveMeta(parsed, ctx);
  } catch (error) {
    ctx.noteFailure('cinemeta', error);
    return empty;
  }
  if (!meta?.name) return empty;

  // Las fichas de anime llegan como type `anime` (o `series`): los metadatos
  // resueltos dicen si es una película, y eso decide cómo buscar.
  let effective = { ...parsed, type: meta.type || parsed.type };
  if (parsed.type === 'series' && meta.type === 'movie') effective.type = 'movie';

  // Anime: `kitsu:46474:3` puede venir sin temporada o con numeración absoluta
  // (One Piece 1071). La traducimos a SxxExx con la tabla de episodios.
  if (parsed.animeId && effective.type === 'series' && parsed.episode !== null) {
    const resolved = resolveEpisode(meta.videos, parsed.season, parsed.episode);
    effective = { ...effective, season: resolved.season, episode: resolved.episode };
  }

  const queries = buildQueries(effective, meta);
  if (!queries.length) return empty;
  const primary = queries[0];

  const torrentProviders = enabledTorrentProviders(ctx.config);
  const releaseProviders = enabledReleaseProviders(ctx.config);

  const [torrentGroups, releaseGroups] = await Promise.all([
    Promise.all(torrentProviders.map((provider) =>
      safe(ctx, provider.id, () => provider.search(primary, ctx)))),
    Promise.all(releaseProviders.map((provider) =>
      safe(ctx, provider.id, () => provider.search(primary, ctx)))),
  ]);

  const torrents = torrentGroups.flat();
  const releases = releaseGroups.flat().filter((release) =>
    primary.mode === 'exact' || matchesTitle(release.name, primary.title, 0.55));

  // Expansión: los índices scene/P2P no tienen infohash, pero sus nombres
  // exactos son excelentes consultas para los índices de torrents.
  if (ctx.config.expansion && ctx.config.maxExpansionQueries > 0 && releases.length) {
    const names = pickReleaseNames(releases, ctx.config.maxExpansionQueries);
    const expansionProviders = ctx.config.expansionProviders
      .map((providerId) => byId(providerId))
      .filter((provider) => provider && ctx.config.providers[provider.id]);

    const expanded = await Promise.all(names.flatMap((name) => {
      const exactQuery = {
        ...primary,
        text: name,
        mode: 'exact',
      };
      return expansionProviders.map(async (provider) => ({
        name,
        results: await safe(ctx, provider.id, () => provider.search(exactQuery, ctx)),
      }));
    }));

    for (const group of expanded) {
      for (const torrent of group.results) {
        torrents.push({ ...torrent, fromExpansion: true, queries: [...(torrent.queries || []), group.name] });
      }
    }
  }

  if (torrents.length < ctx.config.minResults && queries.length > 1) {
    const fallback = queries[1];
    const extra = await Promise.all(torrentProviders.slice(0, 2).map((provider) =>
      safe(ctx, provider.id, () => provider.search(fallback, ctx))));
    torrents.push(...extra.flat());
  }

  const merged = mergeTorrents(torrents);
  let ranked = rankTorrents(merged, { query: primary, config: ctx.config });
  // Para ids `release:` toleramos que el índice devuelva el título con otra
  // nomenclatura: relajamos a coincidencia por título si no hubo exactos.
  if (!ranked.length && primary.mode === 'exact') {
    ranked = rankTorrents(merged, { query: { ...primary, mode: 'title' }, config: ctx.config });
  }

  // Anime: los índices suelen usar el título inglés/romaji en lugar del que
  // muestra el catálogo. Si la búsqueda principal no dio nada, reintentamos con
  // los alias ("Shingeki no Kyojin" -> "Attack on Titan") y con la temporada.
  if (!ranked.length && isAnime(effective)) {
    // Cada reintento se puntúa con SU propia consulta: el ranking exige que el
    // nombre del torrent contenga el título buscado, así que usar el título
    // original descartaría los resultados encontrados con un alias.
    const candidates = [];
    for (const alias of alternateTitles(meta)) {
      for (const query of buildQueries(effective, { ...meta, name: alias })) {
        if (query.episode !== null) candidates.push(query);
      }
    }
    const seasonQuery = buildQueries(effective, meta)
      .find((query) => query.episode === null && query.season !== null);
    if (seasonQuery) candidates.push(seasonQuery);

    const seen = new Set();
    for (const query of candidates) {
      if (seen.has(query.text)) continue;
      seen.add(query.text);
      const groups = await Promise.all(torrentProviders.map((provider) =>
        safe(ctx, provider.id, () => provider.search(query, ctx))));
      const rankedTry = rankTorrents(mergeTorrents(groups.flat()), { query, config: ctx.config });
      if (rankedTry.length) {
        ranked = rankedTry;
        break;
      }
    }
  }

  return {
    streams: ranked.map(toStream),
    cacheMaxAge: ctx.config.cacheTtl,
  };
}
