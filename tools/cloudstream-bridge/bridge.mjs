#!/usr/bin/env node
/**
 * Puente Cloudstream para Omni TV. NO carga .cs3 en este proceso.
 * Delega en un runtime Cloudstream externo (por defecto Stream Goat CLI),
 * que el operador instala y configura con sus repositorios/extensiones.
 *
 * node bridge.mjs --host 0.0.0.0 --port 8789 --runtime cloudstream-linux
 * TV: Extensiones Cloudstream = http://IP:8789
 *
 * Contrato: GET /health; POST /resolve {title,type,year,imdbId,tmdbId,season,episode}.
 * El runtime debe ofrecer search/info/links. Se parsea su salida CLI documentada;
 * no se ejecutan comandos recibidos desde la red.
 */
import http from 'node:http';
import { spawn } from 'node:child_process';

const args = process.argv.slice(2);
const opt = (name, fallback) => { const i = args.indexOf(name); return i >= 0 && args[i + 1] ? args[i + 1] : fallback; };
const host = opt('--host', '0.0.0.0');
const port = Number(opt('--port', '8789'));
const runtime = opt('--runtime', 'cloudstream-linux');
const providers = args.flatMap((v, i) => v === '--provider' && args[i + 1] ? [args[i + 1]] : []);
const timeoutMs = Math.min(120000, Math.max(5000, Number(opt('--timeout', '45000'))));
let active = 0;

function run(commandArgs) {
  return new Promise((resolve, reject) => {
    const child = spawn(runtime, commandArgs, { stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '', err = '', size = 0;
    const timer = setTimeout(() => { child.kill('SIGKILL'); reject(new Error('Tiempo límite del runtime Cloudstream')); }, timeoutMs);
    child.stdout.on('data', b => { size += b.length; if (size > 4 * 1024 * 1024) child.kill('SIGKILL'); else out += b; });
    child.stderr.on('data', b => { if (err.length < 16000) err += b; });
    child.once('error', e => { clearTimeout(timer); reject(e); });
    child.once('exit', code => { clearTimeout(timer); code === 0 ? resolve(out) : reject(new Error(`Runtime salió ${code}: ${err.slice(-500)}`)); });
  });
}
const norm = s => String(s || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, ' ').trim();

export function parseSearch(output) {
  return output.split(/\r?\n/).map(line => {
    const m = line.match(/^\[([^\]]+)\]\s+(.*?)\s+\(([^)]*)\)\t(.+)$/);
    return m ? { provider: m[1], title: m[2], kind: m[3], url: m[4].trim() } : null;
  }).filter(Boolean);
}
export function parseInfo(output) {
  return output.split(/\r?\n/).map(line => line.match(/^\s+(\d+)\.\s+(.+)$/)).filter(Boolean)
    .map(m => ({ index: Number(m[1]), label: m[2].trim() }));
}
export function parseLinks(output) {
  const streams = []; let current = null;
  for (const line of output.split(/\r?\n/)) {
    const head = line.match(/^\s*\d+\.\s+\[([^\]]*)\]\s+(.*?)\s+\(([^)]*)\)\s*$/);
    if (head) { current = { quality: head[1] === '?' ? '' : head[1], name: head[2], kind: head[3] }; continue; }
    const url = line.trim();
    if (current && /^https?:\/\//.test(url)) { streams.push({ ...current, url }); current = null; }
  }
  return streams;
}
export function chooseResult(results, body) {
  const wanted = norm(body.title);
  const kind = body.type === 'series' ? /tv|series|show|anime/i : /movie|film/i;
  return results.filter(r => norm(r.title) === wanted && (!r.kind || r.kind === '?' || kind.test(r.kind)))
    [0] || null;
}
export function choosePlayable(items, body) {
  if (body.type !== 'series' || body.season == null || body.episode == null) return items[0] || null;
  const s = Number(body.season), e = Number(body.episode);
  const patterns = [
    new RegExp(`\\bs0*${s}\\s*e0*${e}\\b`, 'i'),
    new RegExp(`\\b${s}x0*${e}\\b`, 'i'),
    new RegExp(`season\\s*${s}.*episode\\s*${e}\\b`, 'i'),
    new RegExp(`temporada\\s*${s}.*episodio\\s*${e}\\b`, 'i'),
    new RegExp(`\\bepisode\\s*${e}\\b`, 'i')
  ];
  for (const p of patterns) { const found = items.find(i => p.test(i.label)); if (found) return found; }
  return null; // No reproducir el primer episodio si no se pudo identificar el pedido.
}

async function resolve(body) {
  if (!body || typeof body.title !== 'string' || !body.title.trim() || !['movie', 'series'].includes(body.type)) throw Object.assign(new Error('title/type inválidos'), { status: 400 });
  const targets = providers.length ? providers : [null];
  const matches = [];
  for (const provider of targets) {
    const out = await run(['search', body.title.trim(), ...(provider ? ['--provider', provider] : [])]);
    matches.push(...parseSearch(out).filter(r => !provider || r.provider === provider));
  }
  const match = chooseResult(matches, body);
  if (!match) return { streams: [], error: 'Cloudstream no encontró el título exacto' };
  const info = await run(['info', match.provider, match.url]);
  const item = choosePlayable(parseInfo(info), body);
  if (!item) return { streams: [], error: 'Cloudstream no identificó el episodio solicitado' };
  const output = await run(['links', match.provider, match.url, '--item', String(item.index)]);
  return { streams: parseLinks(output), provider: match.provider };
}
function send(res, status, obj) { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(obj)); }

if (process.argv[1] && import.meta.url === new URL(process.argv[1], 'file://').href) {
  const server = http.createServer(async (req, res) => {
    try {
      const path = new URL(req.url, 'http://localhost').pathname;
      if (req.method === 'GET' && path === '/health') {
        const out = await run(['providers']);
        const extensions = out.split(/\r?\n/).filter(l => l.trim() && !l.startsWith('warn:'));
        return send(res, 200, { ok: true, name: 'omni-cloudstream-bridge', runtime, extensions });
      }
      if (req.method !== 'POST' || path !== '/resolve') return send(res, 404, { error: 'ruta no encontrada' });
      let size = 0; const chunks = [];
      for await (const chunk of req) { size += chunk.length; if (size > 65536) return send(res, 413, { error: 'cuerpo demasiado grande' }); chunks.push(chunk); }
      if (active >= 2) return send(res, 429, { error: 'puente ocupado' });
      active++;
      try { send(res, 200, await resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')))); } finally { active--; }
    } catch (e) { if (!res.headersSent) send(res, e.status || 502, { error: String(e.message || e).slice(0, 500), streams: [] }); }
  });
  server.listen(port, host, () => console.log(`Omni Cloudstream bridge: http://${host}:${port} · runtime=${runtime}. Instala/configura las extensiones en el runtime externo.`));
}
