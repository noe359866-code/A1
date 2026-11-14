#!/usr/bin/env node
/**
 * Puente multimedia externo para Omni TV (Node 20+).
 * FFmpeg/ffprobe se ejecutan en este equipo, nunca dentro del APK.
 *
 * node bridge.mjs --host 0.0.0.0 --port 8790 \
 *   --allow-origin https://cdn.example.com --token SECRETO
 *
 * Contrato: GET /health; POST /prepare {url, headers, reason}.
 * Respuesta: {action:"remux"|"transcode", url:"/media/<id>/playlist.m3u8", format:"hls"}.
 * El vídeo H.264 se copia; otros códecs de vídeo requieren --video-transcode.
 * El audio que no sea AAC se convierte a AAC estéreo. No se conservan subtítulos.
 */
import http from 'node:http';
import { spawn } from 'node:child_process';
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';
import { mkdir, readFile, rm, stat } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const MAX_BODY = 64 * 1024;
const HEADER_NAME = /^[A-Za-z0-9-]+$/;
const BLOCKED_HEADERS = new Set(['host', 'cookie', 'content-length', 'connection', 'transfer-encoding', 'keep-alive', 'upgrade', 'proxy-authorization']);

export function option(args, name, fallback) {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
}

export function normalizeOrigin(value) {
  if (typeof value !== 'string' || !value.trim()) return null;
  let url;
  try { url = new URL(value.trim()); } catch { return null; }
  if (!['http:', 'https:'].includes(url.protocol)) return null;
  if (url.username || url.password || url.pathname !== '/' || url.search || url.hash) return null;
  return url.origin;
}

export function validatePrepare(body, allowlist) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw httpError(400, 'cuerpo inválido');
  if (typeof body.url !== 'string' || body.url.length > 8192) throw httpError(400, 'url inválida');
  let url;
  try { url = new URL(body.url); } catch { throw httpError(400, 'url inválida'); }
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) throw httpError(400, 'url no permitida');
  if (!allowlist.includes(url.origin)) throw httpError(403, 'origen no permitido; añade --allow-origin al iniciar el puente');
  if (body.headers != null && (typeof body.headers !== 'object' || Array.isArray(body.headers))) throw httpError(400, 'headers inválido');
  if (body.reason != null && typeof body.reason !== 'string') throw httpError(400, 'reason inválido');
  return { url: url.href, headers: sanitizeHeaders(body.headers || {}), reason: String(body.reason || '').slice(0, 200) };
}

export function sanitizeHeaders(headers) {
  const out = {};
  let count = 0;
  for (const [rawName, rawValue] of Object.entries(headers)) {
    const name = rawName.toLowerCase();
    if (!HEADER_NAME.test(rawName) || BLOCKED_HEADERS.has(name)) continue;
    if (typeof rawValue !== 'string' || !rawValue || /[\r\n\0]/.test(rawValue)) continue;
    out[rawName] = rawValue.slice(0, 2048);
    if (++count >= 16) break;
  }
  return out;
}

export function httpError(status, message) {
  return Object.assign(new Error(message), { status });
}

export function inputOptions(headers) {
  const args = ['-protocol_whitelist', 'http,https,tcp,tls,crypto', '-rw_timeout', '15000000'];
  const rest = [];
  for (const [name, value] of Object.entries(headers)) {
    if (name.toLowerCase() === 'user-agent') args.push('-user_agent', value);
    else rest.push(`${name}: ${value}`);
  }
  if (rest.length) args.push('-headers', rest.join('\r\n') + '\r\n');
  return args;
}

export function probeArgs(url, headers) {
  return ['-v', 'error', '-print_format', 'json', '-show_format', '-show_streams', ...inputOptions(headers), '-i', url];
}

export function planConversion(probe, { videoTranscode = false } = {}) {
  const streams = Array.isArray(probe?.streams) ? probe.streams : [];
  const video = streams.find(s => s.codec_type === 'video');
  const audio = streams.find(s => s.codec_type === 'audio');
  if (!video) throw httpError(422, 'ffprobe no encontró una pista de vídeo');
  const vcodec = String(video.codec_name || '').toLowerCase();
  const acodec = String(audio?.codec_name || '').toLowerCase();
  let videoCodec;
  if (vcodec === 'h264') videoCodec = 'copy';
  else if (videoTranscode) videoCodec = 'libx264';
  else throw httpError(422, `vídeo ${vcodec || 'desconocido'} no compatible; activa --video-transcode en el puente`);
  const audioCodec = !audio ? null : acodec === 'aac' ? 'copy' : 'aac';
  const action = videoCodec === 'copy' && audioCodec !== 'aac' ? 'remux' : 'transcode';
  return { action, videoCodec, audioCodec, videoSource: vcodec, audioSource: acodec || null };
}

export function ffmpegArgs(url, headers, plan, outputDir) {
  const args = ['-hide_banner', '-loglevel', 'error', '-y', ...inputOptions(headers), '-i', url,
    '-map', '0:v:0', '-map', '0:a:0?', '-sn', '-dn', '-c:v', plan.videoCodec];
  if (plan.videoCodec === 'libx264') args.push('-preset', 'veryfast', '-crf', '21', '-pix_fmt', 'yuv420p');
  if (plan.audioCodec) {
    args.push('-c:a', plan.audioCodec);
    if (plan.audioCodec === 'aac') args.push('-b:a', '192k', '-ac', '2');
  }
  args.push('-max_muxing_queue_size', '1024', '-hls_time', '4', '-hls_list_size', '0',
    '-hls_playlist_type', 'event', '-hls_flags', 'independent_segments+temp_file',
    '-hls_segment_type', 'mpegts', '-hls_segment_filename', path.join(outputDir, 'seg_%05d.ts'),
    '-f', 'hls', path.join(outputDir, 'playlist.m3u8'));
  return args;
}

function run(command, args, timeoutMs, maxOutput = 4 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '', err = '', size = 0, settled = false;
    const finish = (fn, value) => { if (settled) return; settled = true; clearTimeout(timer); fn(value); };
    const timer = setTimeout(() => { child.kill('SIGKILL'); finish(reject, httpError(504, 'tiempo límite del proceso multimedia')); }, timeoutMs);
    child.stdout.on('data', b => { size += b.length; if (size > maxOutput) child.kill('SIGKILL'); else out += b; });
    child.stderr.on('data', b => { if (err.length < 16000) err += b; });
    child.once('error', e => finish(reject, httpError(503, `no se pudo ejecutar ${path.basename(command)}: ${e.code || e.message}`)));
    child.once('exit', code => {
      if (size > maxOutput) return finish(reject, httpError(502, 'salida del proceso demasiado grande'));
      if (code === 0) finish(resolve, out);
      else finish(reject, httpError(502, `proceso multimedia salió ${code}: ${err.slice(-300)}`));
    });
  });
}

function send(res, status, obj) {
  if (res.headersSent || res.writableEnded) return;
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(obj));
}

async function readBody(req) {
  let size = 0; const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY) throw httpError(413, 'cuerpo demasiado grande');
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw httpError(400, 'JSON inválido'); }
}

function authorized(req, token) {
  if (!token) return true;
  const header = req.headers.authorization || '';
  const bearer = header.startsWith('Bearer ') ? header.slice(7) : '';
  const candidate = bearer || req.headers['x-bridge-token'] || '';
  if (typeof candidate !== 'string' || !candidate) return false;
  const a = createHash('sha256').update(candidate).digest();
  const b = createHash('sha256').update(token).digest();
  return timingSafeEqual(a, b);
}

async function version(command) {
  try {
    const out = await run(command, ['-version'], 5000, 64 * 1024);
    return out.split(/\r?\n/)[0].slice(0, 120);
  } catch { return null; }
}

async function waitForReady(dir, child, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  const playlist = path.join(dir, 'playlist.m3u8');
  while (Date.now() < deadline) {
    if (child.exitCode !== null && child.exitCode !== 0) throw httpError(502, 'FFmpeg terminó antes de preparar el stream');
    try {
      const text = await readFile(playlist, 'utf8');
      const match = text.match(/^([^#\s][^\r\n]*)$/m);
      if (text.includes('#EXTINF') && match) {
        const segment = path.join(dir, match[1]);
        const info = await stat(segment);
        if (info.isFile() && info.size > 0) return;
      }
    } catch { /* aún escribiendo */ }
    await new Promise(r => setTimeout(r, 200));
  }
  child.kill('SIGKILL');
  throw httpError(504, 'FFmpeg no produjo el primer segmento a tiempo');
}

export function parseConfig(argv, env = process.env) {
  const host = option(argv, '--host', '127.0.0.1');
  const port = Number(option(argv, '--port', '8790'));
  const token = option(argv, '--token', env.OMNI_FFMPEG_TOKEN || '');
  const rawOrigins = argv.flatMap((v, i) => v === '--allow-origin' ? [argv[i + 1] || ''] : []);
  const normalizedOrigins = rawOrigins.map(normalizeOrigin);
  const allowlist = [...new Set(normalizedOrigins.filter(Boolean))];
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('--port inválido');
  if (!allowlist.length || normalizedOrigins.some(o => !o)) throw new Error('añade al menos un --allow-origin válido (solo esquema://host:puerto)');
  const loopback = ['127.0.0.1', '::1', 'localhost'].includes(host);
  if (!loopback && !token) throw new Error('al escuchar fuera de loopback exige --token o OMNI_FFMPEG_TOKEN');
  return {
    host, port, token, allowlist,
    ffmpeg: option(argv, '--ffmpeg', env.FFMPEG_PATH || 'ffmpeg'),
    ffprobe: option(argv, '--ffprobe', env.FFPROBE_PATH || 'ffprobe'),
    videoTranscode: argv.includes('--video-transcode'),
    maxJobs: Math.min(4, Math.max(1, Number(option(argv, '--max-jobs', '2')))),
    probeTimeoutMs: Math.min(30000, Math.max(1000, Number(option(argv, '--probe-timeout', '15000')))),
    readyTimeoutMs: Math.min(50000, Math.max(1000, Number(option(argv, '--ready-timeout', '45000')))),
    jobTimeoutMs: Math.min(4 * 60 * 60 * 1000, Math.max(60000, Number(option(argv, '--job-timeout', String(2 * 60 * 60 * 1000))))),
    ttlMs: Math.min(24 * 60 * 60 * 1000, Math.max(60000, Number(option(argv, '--ttl', String(15 * 60 * 1000)))))
  };
}

async function main() {
  let config;
  try { config = parseConfig(process.argv.slice(2)); }
  catch (e) { console.error(String(e.message || e)); process.exit(2); }
  const root = path.join(os.tmpdir(), `omni-ffmpeg-${randomBytes(6).toString('hex')}`);
  await mkdir(root, { recursive: true });
  const jobs = new Map();
  const [ffmpegVersion, ffprobeVersion] = await Promise.all([version(config.ffmpeg), version(config.ffprobe)]);
  const ready = Boolean(ffmpegVersion && ffprobeVersion);
  let active = 0;

  async function cleanup(force = false) {
    const now = Date.now();
    for (const [id, job] of jobs) {
      const idle = now - job.lastAccess > config.ttlMs;
      const expired = now - job.started > config.jobTimeoutMs;
      if (force || expired || (idle && job.child.exitCode !== null)) {
        if (job.child.exitCode === null) job.child.kill('SIGKILL');
        jobs.delete(id);
        await rm(job.dir, { recursive: true, force: true }).catch(() => {});
      }
    }
  }
  const sweeper = setInterval(() => { cleanup().catch(() => {}); }, 60000);
  sweeper.unref();

  async function prepare(body) {
    if (!ready) throw httpError(503, 'FFmpeg/ffprobe no están disponibles en el puente');
    const input = validatePrepare(body, config.allowlist);
    const running = [...jobs.values()].filter(j => j.child.exitCode === null).length;
    if (active >= config.maxJobs || running >= config.maxJobs) throw httpError(429, 'puente ocupado');
    active++;
    const id = randomBytes(16).toString('hex');
    const dir = path.join(root, id);
    let child = null;
    try {
      await mkdir(dir, { recursive: true });
      const probeText = await run(config.ffprobe, probeArgs(input.url, input.headers), config.probeTimeoutMs);
      let probe;
      try { probe = JSON.parse(probeText); } catch { throw httpError(502, 'ffprobe no devolvió JSON válido'); }
      const plan = planConversion(probe, { videoTranscode: config.videoTranscode });
      child = spawn(config.ffmpeg, ffmpegArgs(input.url, input.headers, plan, dir), { stdio: ['ignore', 'ignore', 'pipe'] });
      let stderr = '';
      child.stderr.on('data', b => { if (stderr.length < 16000) stderr += b; });
      const job = { id, dir, child, started: Date.now(), lastAccess: Date.now(), plan };
      jobs.set(id, job);
      child.once('error', e => { job.error = e.code || e.message; });
      child.once('exit', code => { job.exitCode = code; if (code !== 0) job.error = stderr.slice(-300) || `FFmpeg salió ${code}`; });
      await waitForReady(dir, child, config.readyTimeoutMs);
      if (job.error) throw httpError(502, 'FFmpeg no pudo preparar el stream');
      return { action: plan.action, url: `/media/${id}/playlist.m3u8`, format: 'hls', video: plan.videoCodec, audio: plan.audioCodec || 'none' };
    } catch (e) {
      if (child && child.exitCode === null) child.kill('SIGKILL');
      jobs.delete(id);
      await rm(dir, { recursive: true, force: true }).catch(() => {});
      throw e;
    } finally { active--; }
  }

  async function serveMedia(req, res, id, file) {
    const job = jobs.get(id);
    if (!job || !/^[a-f0-9]{32}$/.test(id)) return send(res, 404, { error: 'medio no encontrado' });
    if (!['playlist.m3u8'].includes(file) && !/^seg_\d{5}\.ts$/.test(file)) return send(res, 404, { error: 'archivo no permitido' });
    const full = path.join(job.dir, file);
    if (!full.startsWith(job.dir + path.sep)) return send(res, 404, { error: 'ruta inválida' });
    try {
      const info = await stat(full);
      if (!info.isFile()) return send(res, 404, { error: 'archivo no encontrado' });
      job.lastAccess = Date.now();
      res.writeHead(200, {
        'Content-Type': file.endsWith('.m3u8') ? 'application/vnd.apple.mpegurl' : 'video/mp2t',
        'Content-Length': info.size,
        'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff'
      });
      if (req.method === 'HEAD') return res.end();
      const stream = createReadStream(full);
      stream.on('error', () => res.destroy());
      res.on('close', () => stream.destroy());
      stream.pipe(res);
    } catch { send(res, 404, { error: 'archivo no encontrado' }); }
  }

  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost');
      const pathname = url.pathname;
      if (!authorized(req, config.token)) return send(res, 401, { error: 'token requerido' });
      if (req.method === 'GET' && pathname === '/health') {
        return send(res, 200, ready
          ? { ok: true, name: 'omni-ffmpeg-bridge', ffmpeg: ffmpegVersion, ffprobe: ffprobeVersion, videoTranscode: config.videoTranscode }
          : { ok: false, error: 'FFmpeg/ffprobe no están disponibles' });
      }
      if (req.method === 'POST' && pathname === '/prepare') return send(res, 200, await prepare(await readBody(req)));
      const media = pathname.match(/^\/media\/([a-f0-9]{32})\/([^/]+)$/);
      if ((req.method === 'GET' || req.method === 'HEAD') && media) return await serveMedia(req, res, media[1], media[2]);
      send(res, 404, { error: 'ruta no encontrada' });
    } catch (e) { send(res, e.status || 502, { error: String(e.message || e).slice(0, 500) }); }
  });
  server.headersTimeout = 10000;
  server.requestTimeout = 70000;
  server.keepAliveTimeout = 5000;
  server.on('clientError', (_e, socket) => { if (!socket.destroyed) socket.end('HTTP/1.1 400 Bad Request\r\n\r\n'); });
  server.listen(config.port, config.host, () => {
    console.log(`Omni FFmpeg bridge: http://${config.host}:${config.port} · orígenes=${config.allowlist.length} · vídeo=${config.videoTranscode ? 'transcode' : 'copy'}`);
    if (!ready) console.error('FFmpeg/ffprobe no encontrados; /health devolverá ok:false.');
  });
  const shutdown = async () => { clearInterval(sweeper); server.close(); await cleanup(true); await rm(root, { recursive: true, force: true }).catch(() => {}); process.exit(0); };
  process.once('SIGINT', shutdown);
  process.once('SIGTERM', shutdown);
}

if (process.argv[1] && import.meta.url === new URL(process.argv[1], 'file://').href) {
  main().catch(e => { console.error(String(e.message || e)); process.exit(1); });
}
