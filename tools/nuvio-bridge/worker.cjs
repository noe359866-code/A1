'use strict';
// Worker desechable. No es un sandbox: el operador debe aislar el proceso.
const { parentPort, workerData } = require('node:worker_threads');
const Module = require('node:module');
const path = require('node:path');

(async () => {
  const m = new Module(workerData.filename, module);
  m.filename = workerData.filename;
  m.paths = Module._nodeModulePaths(workerData.moduleRoot);
  m._compile(workerData.source, workerData.filename);
  const getStreams = m.exports.getStreams || m.exports.default?.getStreams;
  if (typeof getStreams !== 'function') throw new Error('El plugin no exporta getStreams');
  const e = workerData.extra || {};
  let tmdbId = e.tmdbId ? String(e.tmdbId) : null;
  if (!tmdbId && e.imdbId) {
    if (!process.env.TMDB_API_KEY) throw new Error('TMDB_API_KEY requerida para convertir IMDb a TMDB');
    const kind = workerData.type === 'movie' ? 'movie_results' : 'tv_results';
    const u = new URL(`https://api.themoviedb.org/3/find/${encodeURIComponent(e.imdbId)}`);
    u.searchParams.set('external_source', 'imdb_id');
    u.searchParams.set('api_key', process.env.TMDB_API_KEY);
    const r = await fetch(u, { signal: AbortSignal.timeout(8000) });
    if (!r.ok) throw new Error(`TMDB HTTP ${r.status}`);
    tmdbId = String((await r.json())[kind]?.[0]?.id || '');
    if (!tmdbId) throw new Error('TMDB no encontró el título');
  }
  if (!tmdbId) throw new Error('Falta tmdbId o imdbId');
  const streams = await getStreams(tmdbId, workerData.type, e.season ?? null, e.episode ?? null);
  const result = (Array.isArray(streams) ? streams : [])
    .map(s => {
      if (!s || typeof s !== 'object') return null;
      const url = typeof s.url === 'string' ? s.url : (typeof s.file === 'string' ? s.file : (typeof s.link === 'string' ? s.link : ''));
      if (!/^https?:\/\//.test(url)) return null;
      return {
        name: s.name || s.provider || 'Nuvio',
        title: s.title || '',
        url,
        quality: s.quality || '',
        headers: s.headers || {},
        subtitles: Array.isArray(s.subtitles) ? s.subtitles : []
      };
    })
    .filter(Boolean);
  parentPort.postMessage({ streams: result });
})().catch(e => parentPort.postMessage({ error: String(e.message || e).slice(0, 300), streams: [] }));
