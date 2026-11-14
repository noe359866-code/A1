#!/usr/bin/env node
/**
 * Puente Nuvio de referencia para Omni TV (Node 20+).
 * Ejecuta proveedores JS de confianza en un worker desechable con tiempo límite.
 * NO es un sandbox de seguridad: ejecutar solo en una máquina/contenedor aislado.
 *
 * node bridge.mjs --repo https://.../manifest.json --host 0.0.0.0 --port 8788
 * En TV: Plugins Nuvio = esa URL; Puente JS = http://IP-DE-ESTE-EQUIPO:8788
 *
 * Contrato: GET /health; POST /scrape/{id} con repoUrl, filename, type, id, extra.
 * Los JS deben estar compilados para Nuvio y exportar getStreams(tmdbId,type,season,episode).
 * Para ids IMDb se necesita TMDB_API_KEY (consulta find de TMDB); no se inventa el id.
 */
import http from 'node:http';
import { Worker } from 'node:worker_threads';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const args = process.argv.slice(2);
function option(name, fallback) { const i = args.indexOf(name); return i >= 0 && args[i + 1] ? args[i + 1] : fallback; }
const repos = args.flatMap((v, i) => v === '--repo' && args[i + 1] ? [args[i + 1]] : []);
const host = option('--host', '0.0.0.0');
const port = Number(option('--port', '8788'));
const timeoutMs = Math.min(60000, Math.max(1000, Number(option('--timeout', '20000'))));
if (!repos.length) { console.error('Falta --repo URL (se puede repetir). No se ejecutan URLs arbitrarias enviadas por clientes.'); process.exit(2); }
const index = new Map();
async function jsonGet(url) {
  const r = await fetch(url, { signal: AbortSignal.timeout(12000), headers: { Accept: 'application/json' } });
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}
async function refreshRepos() {
  index.clear();
  for (const url of repos) {
    const manifest = await jsonGet(url);
    if (!Array.isArray(manifest.scrapers)) throw new Error(`No es un repo Nuvio: ${url}`);
    for (const s of manifest.scrapers) {
      if (s.enabled === false || !/^[A-Za-z0-9_-]+$/.test(s.id || '')) continue;
      const filename = s.filename || `providers/${s.id}.js`;
      if (filename.startsWith('/') || filename.includes('..') || filename.includes('\\')) continue;
      const sourceUrl = new URL(filename, url).href;
      index.set(`${url}\0${s.id}`, { ...s, filename, sourceUrl, repoUrl: url });
    }
  }
}
await refreshRepos();
let active = 0;
const workerFile = fileURLToPath(new URL('./worker.cjs', import.meta.url));
const moduleRoot = option('--modules', process.cwd());

async function scrape(scraper, body) {
  const response = await fetch(scraper.sourceUrl, { signal: AbortSignal.timeout(12000) });
  if (!response.ok) throw new Error(`JS HTTP ${response.status}`);
  const source = await response.text();
  if (source.length > 8 * 1024 * 1024) throw new Error('JS demasiado grande');
  return new Promise((resolve, reject) => {
    const worker = new Worker(workerFile, {
      workerData: { source, filename: scraper.filename, moduleRoot, type: body.type === 'series' ? 'tv' : body.type, extra: body.extra || {} },
      resourceLimits: { maxOldGenerationSizeMb: 192, maxYoungGenerationSizeMb: 32 }
    });
    const timer = setTimeout(() => { worker.terminate(); reject(new Error('Tiempo límite del scraper')); }, timeoutMs);
    let settled = false;
    const finish = (fn, value) => { if (settled) return; settled = true; clearTimeout(timer); fn(value); };
    worker.once('message', msg => { finish(resolve, msg); worker.terminate(); });
    worker.once('error', e => finish(reject, e));
    worker.once('exit', code => { if (!settled) finish(reject, new Error(`Worker terminó (${code})`)); });
  });
}
function send(res, status, obj) { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(obj)); }
const server = http.createServer(async (req, res) => {
  try {
    const path = new URL(req.url, 'http://localhost').pathname;
    if (req.method === 'GET' && path === '/health') return send(res, 200, { ok: true, name: 'omni-nuvio-bridge', scrapers: index.size, execution: 'external-js' });
    if (req.method === 'POST' && path === '/refresh') { await refreshRepos(); return send(res, 200, { ok: true, scrapers: index.size }); }
    if (req.method !== 'POST' || !path.startsWith('/scrape/')) return send(res, 404, { error: 'ruta no encontrada' });
    let size = 0; const chunks = [];
    for await (const chunk of req) { size += chunk.length; if (size > 65536) return send(res, 413, { error: 'cuerpo demasiado grande' }); chunks.push(chunk); }
    const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    const scraper = index.get(`${body.repoUrl}\0${decodeURIComponent(path.slice('/scrape/'.length))}`);
    if (!scraper || (body.filename && body.filename !== scraper.filename)) return send(res, 404, { error: 'scraper no permitido; añadir --repo al iniciar el puente' });
    if (!['movie', 'tv', 'series'].includes(body.type) || typeof body.id !== 'string') return send(res, 400, { error: 'type/id inválidos' });
    if (active >= 6) return send(res, 429, { error: 'puente ocupado' });
    active++;
    try { const result = await scrape(scraper, body); send(res, result.error ? 502 : 200, result); } finally { active--; }
  } catch (e) { if (!res.headersSent) send(res, 502, { error: String(e.message || e).slice(0, 300), streams: [] }); }
});
server.listen(port, host, () => console.log(`Omni Nuvio bridge: http://${host}:${port} · ${index.size} scrapers permitidos. Ejecuta solo JS de confianza en un entorno aislado.`));
