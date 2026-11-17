// Puente Cloudstream: token obligatorio fuera de loopback y autenticación de
// /health y /resolve. No necesita el runtime externo instalado.
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
  assert.equal(authorized(req({}), undefined), true);
});

test('authorized: acepta Bearer y X-Bridge-Token, rechaza el resto', () => {
  assert.equal(authorized(req({ authorization: 'Bearer s3cr3t' }), 's3cr3t'), true);
  assert.equal(authorized(req({ 'x-bridge-token': 's3cr3t' }), 's3cr3t'), true);
  assert.equal(authorized(req({}), 's3cr3t'), false);
  assert.equal(authorized(req({ authorization: 'Bearer otro' }), 's3cr3t'), false);
  assert.equal(authorized(req({ authorization: 'Bearer ' }), 's3cr3t'), false);
  assert.equal(authorized(req({ authorization: ['a', 'b'] }), 's3cr3t'), false);
});

/** Arranca el puente y espera a que anuncie su URL. */
async function startBridge(extraArgs) {
  const probe = net.createServer(); const port = await listen(probe); await new Promise(r => probe.close(r));
  const child = spawn(process.execPath, [fileURLToPath(new URL('./bridge.mjs', import.meta.url)),
    '--port', String(port), ...extraArgs], { stdio: ['ignore', 'pipe', 'pipe'] });
  await new Promise((resolve, reject) => {
    let log = '';
    const timer = setTimeout(() => reject(new Error('Arranque lento: ' + log)), 8000);
    child.stdout.on('data', c => { log += c; if (/http:\/\/127\.0\.0\.1:\d+/.test(log)) { clearTimeout(timer); resolve(); } });
    child.once('exit', code => { clearTimeout(timer); reject(new Error('Puente salió: ' + code)); });
  });
  return { child, base: `http://127.0.0.1:${port}` };
}

test('el puente no arranca en 0.0.0.0 sin token', async () => {
  const child = spawn(process.execPath, [fileURLToPath(new URL('./bridge.mjs', import.meta.url)),
    '--host', '0.0.0.0', '--port', '18789'], { stdio: ['ignore', 'pipe', 'pipe'] });
  const [code] = await once(child, 'exit');
  assert.notEqual(code, 0);
});

test('con token: 401 sin credencial y respuesta con Bearer', async () => {
  const { child, base } = await startBridge(['--token', 's3cr3t']);
  try {
    assert.equal((await fetch(base + '/health')).status, 401);
    assert.equal((await fetch(base + '/health', { headers: { Authorization: 'Bearer otro' } })).status, 401);

    // /health intenta ejecutar el runtime; lo relevante aquí es que ya pasó
    // la autenticación (no devuelve 401 aunque el runtime no exista).
    const ok = await fetch(base + '/health', { headers: { Authorization: 'Bearer s3cr3t' } });
    assert.notEqual(ok.status, 401);

    const resolve = await fetch(base + '/resolve', {
      method: 'POST', body: JSON.stringify({ title: 'x', type: 'movie' }),
      headers: { Authorization: 'Bearer s3cr3t' }
    });
    assert.notEqual(resolve.status, 401);

    const anonymous = await fetch(base + '/resolve', {
      method: 'POST', body: JSON.stringify({ title: 'x', type: 'movie' })
    });
    assert.equal(anonymous.status, 401);
  } finally {
    child.kill('SIGTERM'); await once(child, 'exit').catch(() => {});
  }
});
