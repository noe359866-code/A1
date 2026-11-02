/**
 * Contrato de despliegue en Beamup.
 *
 * Beamup (Dokku/Herokuish) valida el addon antes de admitirlo: su plugin
 * `beamup-addon-linter` hace GET /manifest.json y ejecuta
 * `stremio-addon-linter.lintManifest()`. Estas pruebas reproducen ese chequeo
 * y verifican los requisitos del buildpack de Node.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import linter from 'stremio-addon-linter';
import { createServer } from '../src/server.js';
import { buildManifest } from '../src/manifest.js';
import { defaultConfig, sanitizeConfig, DEFAULTS } from '../src/config.js';

const { lintManifest } = linter;
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');

function assertValid(manifest) {
  const result = lintManifest(manifest);
  assert.equal(result.valid, true, result.errors.map((error) => error.message).join('; '));
}

async function withServer(fn) {
  const server = createServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    return await fn(base);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

async function freePort() {
  const probe = net.createServer();
  await new Promise((resolve) => probe.listen(0, '127.0.0.1', resolve));
  const port = probe.address().port;
  await new Promise((resolve) => probe.close(resolve));
  return port;
}

async function waitForHealth(base, timeoutMs = 8000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${base}/health`, { signal: AbortSignal.timeout(500) });
      if (response.ok) return await response.json();
    } catch { /* todavía arrancando */ }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error('El servidor no respondió /health a tiempo');
}

test('el manifiesto por defecto pasa el linter oficial que usa Beamup', () => {
  assertValid(buildManifest(defaultConfig()));
});

test('el manifiesto con catálogo activado también pasa el linter oficial', () => {
  const config = sanitizeConfig({ ...DEFAULTS, enableCatalog: true });
  const manifest = buildManifest(config);
  assertValid(manifest);
  assert.equal(manifest.catalogs.length, 2);
});

test('GET /manifest.json por HTTP pasa el linter (simula beamup-addon-linter)', async () => {
  await withServer(async (base) => {
    const response = await fetch(`${base}/manifest.json`);
    assert.equal(response.status, 200);
    assert.match(response.headers.get('content-type'), /application\/json/);
    const manifest = await response.json();
    assertValid(manifest);
    // Beamup sirve el addon detrás de un proxy: el manifiesto no debe depender
    // de ninguna API externa ni contener URLs absolutas a localhost.
    assert.ok(!JSON.stringify(manifest).includes('localhost'));
    assert.ok(!JSON.stringify(manifest).includes('127.0.0.1'));
  });
});

test('el repo cumple el contrato del buildpack Herokuish de Node', () => {
  const pkg = JSON.parse(read('package.json'));
  assert.match(pkg.scripts.start, /node src\/index\.js/);
  assert.equal(pkg.main, 'src/index.js');
  assert.ok(pkg.engines?.node);

  assert.match(read('Procfile'), /^web:\s*node src\/index\.js/);
  assert.ok(fs.existsSync(path.join(root, '.slugignore')));

  // Un Dockerfile en la RAÍZ haría que Dokku eligiera el builder de Dockerfile
  // en vez del buildpack Herokuish (el único plenamente soportado por Beamup).
  assert.equal(fs.existsSync(path.join(root, 'Dockerfile')), false);
  assert.ok(fs.existsSync(path.join(root, 'docker', 'Dockerfile')));

  assert.ok(!read('package-lock.json').includes('"lockfileVersion": 0'));
});

test('el punto de entrada respeta el PORT que inyecta Dokku/Beamup', async () => {
  const port = await freePort();
  const child = spawn(process.execPath, ['src/index.js'], {
    cwd: root,
    env: {
      ...process.env,
      PORT: String(port),
      HOST: '127.0.0.1',
      OMNI_HTTP_TIMEOUT: '1000',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let output = '';
  child.stdout.on('data', (chunk) => { output += chunk; });
  child.stderr.on('data', (chunk) => { output += chunk; });

  try {
    const health = await waitForHealth(`http://127.0.0.1:${port}`);
    assert.equal(health.ok, true);
    const manifest = await (await fetch(`http://127.0.0.1:${port}/manifest.json`)).json();
    assertValid(manifest);
    assert.match(output, new RegExp(`:${port}`));
  } finally {
    if (child.exitCode === null) {
      child.kill('SIGTERM');
      await Promise.race([
        new Promise((resolve) => child.once('exit', resolve)),
        new Promise((resolve) => setTimeout(resolve, 2000)),
      ]);
      if (child.exitCode === null) child.kill('SIGKILL');
    }
  }
});
