// Prueba de integración real del puente: HTTP -> worker -> getStreams -> JSON.
// No contacta con proveedores externos ni ejecuta código descargado de Internet.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import net from 'node:net';
import { fileURLToPath } from 'node:url';

function listen(server) { return new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server.address().port))); }

test('health, allowlist de repo, episodio y streams del worker', async () => {
  const source = `module.exports.getStreams = async (id, type, season, episode) => [{
    name: 'Fixture', url: 'https://cdn.example/video.m3u8', quality: '1080p',
    title: JSON.stringify({id, type, season, episode}), headers: {Referer: 'https://source.example/'}
  }];`;
  const repo = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'application/json');
    if (req.url === '/manifest.json') res.end(JSON.stringify({ name: 'Fixture', scrapers: [{ id: 'fixture', filename: 'providers/fixture.js', enabled: true }] }));
    else if (req.url === '/providers/fixture.js') { res.setHeader('Content-Type', 'text/javascript'); res.end(source); }
    else { res.statusCode = 404; res.end('{}'); }
  });
  const repoPort = await listen(repo);
  const repoUrl = `http://127.0.0.1:${repoPort}/manifest.json`;
  const probe = net.createServer(); const port = await listen(probe); await new Promise(r => probe.close(r));
  const child = spawn(process.execPath, [fileURLToPath(new URL('./bridge.mjs', import.meta.url)), '--repo', repoUrl, '--host', '127.0.0.1', '--port', String(port)], { stdio: ['ignore', 'pipe', 'pipe'] });
  try {
    await new Promise((resolve, reject) => {
      let log = '';
      const timer = setTimeout(() => reject(new Error('Arranque lento: ' + log)), 8000);
      child.stdout.on('data', chunk => { log += chunk; const m = log.match(/http:\/\/127\.0\.0\.1:(\d+)/); if (m) { clearTimeout(timer); resolve(); } });
      child.once('exit', code => { clearTimeout(timer); reject(new Error('Puente salió: ' + code)); });
    });
    const base = `http://127.0.0.1:${port}`;
    const health = await fetch(base + '/health').then(r => r.json());
    assert.equal(health.ok, true); assert.equal(health.scrapers, 1);
    const body = { repoUrl, filename: 'providers/fixture.js', type: 'series', id: 'tmdb:123:2:3', extra: { tmdbId: '123', season: 2, episode: 3 } };
    const result = await fetch(base + '/scrape/fixture', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(r => r.json());
    assert.equal(result.streams.length, 1);
    assert.equal(result.streams[0].url, 'https://cdn.example/video.m3u8');
    assert.deepEqual(JSON.parse(result.streams[0].title), { id: '123', type: 'tv', season: 2, episode: 3 });
    const denied = await fetch(base + '/scrape/fixture', { method: 'POST', body: JSON.stringify({ ...body, repoUrl: 'https://evil.example/manifest.json' }) });
    assert.equal(denied.status, 404);
  } finally {
    child.kill('SIGTERM'); await once(child, 'exit').catch(() => {});
    await new Promise(resolve => repo.close(resolve));
  }
});
