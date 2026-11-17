// Puente Nuvio: token obligatorio fuera de loopback y autenticación de /health
// y /scrape. No contacta con proveedores externos.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import net from 'node:net';
import { fileURLToPath } from 'node:url';
import { authorized } from './bridge.mjs';

function listen(server) { return new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server.address().port))); }

const req = (headers) => ({ headers });

test('authorized: sin token configurado todo pasa (loopback)', () => {
  assert.equal(authorized(req({}), ''), true);
  assert.equal(authorized(req({ authorization: 'Bearer x' }), undefined), true);
});

test('authorized: acepta Bearer y X-Bridge-Token, rechaza el resto', () => {
  assert.equal(authorized(req({ authorization: 'Bearer s3cr3t' }), 's3cr3t'), true);
  assert.equal(authorized(req({ 'x-bridge-token': 's3cr3t' }), 's3cr3t'), true);
  assert.equal(authorized(req({}), 's3cr3t'), false);
  assert.equal(authorized(req({ authorization: 'Bearer otro' }), 's3cr3t'), false);
  assert.equal(authorized(req({ authorization: 'Basic s3cr3t' }), 's3cr3t'), false);
  assert.equal(authorized(req({ authorization: 'Bearer ' }), 's3cr3t'), false);
});

test('el puente no arranca en 0.0.0.0 sin token', async () => {
  const child = spawn(
    process.execPath,
    [fileURLToPath(new URL('./bridge.mjs', import.meta.url)),
      '--repo', 'https://example.test/manifest.json', '--host', '0.0.0.0', '--port', '18788'],
    { stdio: ['ignore', 'pipe', 'pipe'] }
  );
  const [code] = await once(child, 'exit');
  assert.notEqual(code, 0);
});

test('sin token y en loopback: /health y /scrape abiertos (compatibilidad)', async () => {
  const source = `module.exports.getStreams = async () => [{ name: 'Fixture', url: 'https://cdn.example/v.m3u8' }];`;
  const repo = http.createServer((rq, rs) => {
    rs.setHeader('Content-Type', 'application/json');
    if (rq.url === '/manifest.json') rs.end(JSON.stringify({ name: 'F', scrapers: [{ id: 'f', filename: 'providers/f.js' }] }));
    else { rs.setHeader('Content-Type', 'text/javascript'); rs.end(source); }
  });
  const repoPort = await listen(repo);
  const repoUrl = `http://127.0.0.1:${repoPort}/manifest.json`;
  const probe = net.createServer(); const port = await listen(probe); await new Promise(r => probe.close(r));
  const child = spawn(process.execPath, [fileURLToPath(new URL('./bridge.mjs', import.meta.url)),
    '--repo', repoUrl, '--port', String(port)], { stdio: ['ignore', 'pipe', 'pipe'] });
  try {
    await new Promise((resolve, reject) => {
      let log = '';
      const timer = setTimeout(() => reject(new Error('Arranque lento: ' + log)), 8000);
      child.stdout.on('data', c => { log += c; if (/http:\/\/127\.0\.0\.1:\d+/.test(log)) { clearTimeout(timer); resolve(); } });
      child.once('exit', code => { clearTimeout(timer); reject(new Error('Puente salió: ' + code)); });
    });
    const health = await fetch(`http://127.0.0.1:${port}/health`).then(r => r.json());
    assert.equal(health.ok, true);
    assert.equal(health.execution, 'external-js');
  } finally {
    child.kill('SIGTERM'); await once(child, 'exit').catch(() => {});
    await new Promise(resolve => repo.close(resolve));
  }
});

test('con token: 401 sin credencial y 200 con Bearer', async () => {
  const repo = http.createServer((rq, rs) => {
    rs.setHeader('Content-Type', 'application/json');
    rs.end(JSON.stringify({ name: 'F', scrapers: [] }));
  });
  const repoPort = await listen(repo);
  const repoUrl = `http://127.0.0.1:${repoPort}/manifest.json`;
  const probe = net.createServer(); const port = await listen(probe); await new Promise(r => probe.close(r));
  const child = spawn(process.execPath, [fileURLToPath(new URL('./bridge.mjs', import.meta.url)),
    '--repo', repoUrl, '--port', String(port), '--token', 's3cr3t'], { stdio: ['ignore', 'pipe', 'pipe'] });
  try {
    await new Promise((resolve, reject) => {
      let log = '';
      const timer = setTimeout(() => reject(new Error('Arranque lento: ' + log)), 8000);
      child.stdout.on('data', c => { log += c; if (/http:\/\/127\.0\.0\.1:\d+/.test(log)) { clearTimeout(timer); resolve(); } });
      child.once('exit', code => { clearTimeout(timer); reject(new Error('Puente salió: ' + code)); });
    });
    const base = `http://127.0.0.1:${port}`;
    const anonymous = await fetch(base + '/health');
    assert.equal(anonymous.status, 401);

    const wrong = await fetch(base + '/health', { headers: { Authorization: 'Bearer otro' } });
    assert.equal(wrong.status, 401);

    const ok = await fetch(base + '/health', { headers: { Authorization: 'Bearer s3cr3t' } }).then(r => r.json());
    assert.equal(ok.ok, true);

    // La credencial también protege la ruta que ejecuta JS.
    const scrape = await fetch(base + '/scrape/f', { method: 'POST', body: '{}' });
    assert.equal(scrape.status, 401);
  } finally {
    child.kill('SIGTERM'); await once(child, 'exit').catch(() => {});
    await new Promise(resolve => repo.close(resolve));
  }
});
