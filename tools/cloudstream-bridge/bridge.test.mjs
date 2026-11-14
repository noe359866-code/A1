import test from 'node:test';
import assert from 'node:assert/strict';
import { parseSearch, parseInfo, parseLinks, chooseResult, choosePlayable } from './bridge.mjs';
import http from 'node:http';
import { spawn } from 'node:child_process';
import net from 'node:net';
import { fileURLToPath } from 'node:url';
import { writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

const listen = server => new Promise(r => server.listen(0, '127.0.0.1', () => r(server.address().port)));

test('parsers del CLI Cloudstream y seleccion exacta de episodio', () => {
  const results = parseSearch('[Provider] La Serie (TvSeries)\thttps://example.test/serie\n[Otro] La Serie (Movie)\thttps://example.test/movie');
  assert.equal(results.length, 2);
  assert.equal(chooseResult(results, { title: 'La Serie', type: 'series' }).provider, 'Provider');
  const items = parseInfo('  1. S01E01 Piloto\n  2. S02E03 Capítulo\n');
  assert.equal(choosePlayable(items, { type: 'series', season: 2, episode: 3 }).index, 2);
  assert.equal(choosePlayable(items, { type: 'series', season: 9, episode: 9 }), null);
  const links = parseLinks('1. [1080p] Servidor (hls)\n   https://cdn.example/v.m3u8\nsub: es\thttps://subs/vtt');
  assert.deepEqual(links, [{ quality: '1080p', name: 'Servidor', kind: 'hls', url: 'https://cdn.example/v.m3u8' }]);
});

test('puente HTTP delega en runtime externo y devuelve streams', async () => {
  const dir = mkdtempSync(path.join(tmpdir(), 'omni-cs-'));
  const fake = path.join(dir, 'runtime.mjs');
  writeFileSync(fake, `const a=process.argv.slice(2); if(a[0]==='providers') console.log('Provider es movie,series https://example.test'); else if(a[0]==='search') console.log('[Provider] Titulo (Movie)\\thttps://example.test/title'); else if(a[0]==='info') console.log('  1. Película'); else if(a[0]==='links') console.log('1. [720p] Server (mp4)\\n   https://cdn.example/v.mp4'); else process.exit(2);`);
  const probe = net.createServer(); const port = await listen(probe); await new Promise(r => probe.close(r));
  const shim = path.join(dir, 'runtime.sh');
  writeFileSync(shim, `#!/bin/sh\nexec "${process.execPath}" "${fake}" "$@"\n`, { mode: 0o755 });
  const bridge = spawn(process.execPath, [fileURLToPath(new URL('./bridge.mjs', import.meta.url)), '--runtime', shim, '--host', '127.0.0.1', '--port', String(port)], { stdio: ['ignore', 'pipe', 'pipe'] });
  try {
    await new Promise((resolve, reject) => { const t = setTimeout(() => reject(new Error('arranque lento')), 5000); bridge.stdout.on('data', b => { if (String(b).includes('Omni Cloudstream bridge')) { clearTimeout(t); resolve(); } }); bridge.once('exit', c => { clearTimeout(t); reject(new Error('exit ' + c)); }); });
    const base = `http://127.0.0.1:${port}`;
    const health = await fetch(base + '/health').then(r => r.json());
    assert.equal(health.ok, true); assert.equal(health.extensions.length, 1);
    const result = await fetch(base + '/resolve', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: 'Titulo', type: 'movie' }) }).then(r => r.json());
    assert.equal(result.streams[0].url, 'https://cdn.example/v.mp4');
    const bad = await fetch(base + '/resolve', { method: 'POST', body: '{}' }); assert.equal(bad.status, 400);
  } finally { bridge.kill(); await new Promise(r => bridge.once('exit', r)); rmSync(dir, { recursive: true, force: true }); }
});
