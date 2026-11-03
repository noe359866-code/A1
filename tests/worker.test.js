/**
 * Contrato de ejecución en Cloudflare Workers.
 *
 * Llama directamente al handler `fetch(request, env, ctx)` del Worker con un
 * doble del binding ASSETS y con las seis APIs apuntando al mock local. Así se
 * comprueba que el addon funciona SIN variables de proceso de Node: todo llega
 * por el objeto `env`, igual que en producción en Cloudflare.
 */
import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import worker from '../src/worker.js';
import { startMockServer } from './mock-server.js';
import { setLimiterIntervals, resetCache } from '../src/context.js';
import { encodeConfig, DEFAULTS, sanitizeConfig } from '../src/config.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const publicDir = path.join(root, 'src', 'public');
const ORIGIN = 'https://omni-index.ejemplo.workers.dev';

const BASE_NAMES = [
  'OMNI_KNABEN_BASE', 'OMNI_TORRENTS_CSV_BASE', 'OMNI_TORLOCK_BASE',
  'OMNI_PREDB_NET_BASE', 'OMNI_PREDATABA_SE_BASE', 'OMNI_XREL_BASE',
  'OMNI_CINEMETA_BASE',
];

/** Doble de `env.ASSETS`: sirve los ficheros reales de src/public. */
const assetsBinding = {
  async fetch(request) {
    const name = new URL(request.url).pathname.replace(/^\/+/, '');
    const file = path.join(publicDir, name);
    if (!file.startsWith(publicDir + path.sep) || !fs.existsSync(file)) {
      return new Response('not found', { status: 404 });
    }
    return new Response(new Uint8Array(fs.readFileSync(file)), { status: 200 });
  },
};

function makeEnv(mockUrl, extra = {}) {
  const env = { ASSETS: assetsBinding, OMNI_HTTP_TIMEOUT: '3000' };
  for (const name of BASE_NAMES) env[name] = mockUrl;
  return { ...env, ...extra };
}

let mock;
let env;

before(async () => {
  mock = await startMockServer();
  env = makeEnv(mock.url);
  setLimiterIntervals(0);
});

after(async () => { await mock.close(); });

beforeEach(() => {
  mock.requests.length = 0;
  mock.clearFailures();
  resetCache();
});

function call(route, { init, envOverride, extra = {} } = {}) {
  const bindings = envOverride === undefined ? { ...env, ...extra } : envOverride;
  return worker.fetch(new Request(`${ORIGIN}${route}`, init), bindings, { waitUntil() {} });
}

test('el grafo de módulos del Worker no importa nada de Node', () => {
  const entry = path.join(root, 'src', 'worker.js');
  const seen = new Set();
  const queue = [entry];
  while (queue.length) {
    const file = queue.shift();
    if (seen.has(file)) continue;
    seen.add(file);
    const source = fs.readFileSync(file, 'utf8');
    for (const match of source.matchAll(/(?:from|import)\s*['"](\.[^'"]+)['"]/g)) {
      queue.push(path.resolve(path.dirname(file), match[1]));
    }
  }

  assert.ok(seen.size > 10, 'debe recorrer el núcleo compartido');
  for (const file of seen) {
    const rel = path.relative(root, file);
    const source = fs.readFileSync(file, 'utf8');
    assert.doesNotMatch(source, /from\s*['"]node:|import\s*['"]node:/, `${rel}: importa node:*`);
    assert.doesNotMatch(source, /\bBuffer\./, `${rel}: usa la API Buffer`);
    assert.doesNotMatch(source, /\bprocess\.env\b/, `${rel}: lee variables de proceso`);
    assert.doesNotMatch(source, /\brequire\(/, `${rel}: usa CommonJS`);
  }
  for (const nodeOnly of ['src/server.js', 'src/index.js', 'src/assets-node.js']) {
    assert.equal(seen.has(path.join(root, nodeOnly)), false, `${nodeOnly} no debe entrar en el bundle`);
  }
});

test('wrangler.toml declara el entry point y el binding de estáticos', () => {
  const config = fs.readFileSync(path.join(root, 'wrangler.toml'), 'utf8');
  assert.match(config, /main\s*=\s*"src\/worker\.js"/);
  assert.match(config, /\[assets\]/);
  assert.match(config, /directory\s*=\s*"src\/public"/);
  assert.match(config, /binding\s*=\s*"ASSETS"/);
  assert.match(config, /run_worker_first\s*=\s*true/);
  assert.match(config, /compatibility_flags\s*=\s*\[\]/, 'no se necesita nodejs_compat');
});

test('GET /health y /api/status identifican el runtime de Cloudflare', async () => {
  const health = await (await call('/health')).json();
  assert.equal(health.ok, true);
  assert.equal(health.runtime, 'cloudflare');

  const status = await (await call('/api/status')).json();
  assert.equal(status.runtime, 'cloudflare');
  assert.equal(status.origin, ORIGIN);
  assert.equal(Object.keys(status.providers).length, 6);
  assert.ok(status.cache);
});

test('el manifiesto usa el origen real de la petición, no X-Forwarded-* ni process', async () => {
  const previous = process.env.OMNI_PUBLIC_URL;
  process.env.OMNI_PUBLIC_URL = 'https://no-deberia-usarse.example';
  try {
    const response = await call('/manifest.json', {
      init: { headers: { 'x-forwarded-host': 'spoofed.example', 'x-forwarded-proto': 'http' } },
    });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('access-control-allow-origin'), '*');
    assert.match(response.headers.get('cache-control'), /max-age=3600/);
    const manifest = await response.json();
    assert.equal(manifest.id, 'org.omniindex.addon');
    assert.equal(manifest.logo, `${ORIGIN}/logo.png`);
    assert.equal(manifest.background, `${ORIGIN}/background.png`);
    assert.deepEqual(manifest.catalogs, []);
  } finally {
    if (previous === undefined) delete process.env.OMNI_PUBLIC_URL;
    else process.env.OMNI_PUBLIC_URL = previous;
  }
});

test('OMNI_PUBLIC_URL y OMNI_CONFIG llegan como bindings/vars', async () => {
  const withVars = makeEnv(mock.url, {
    OMNI_PUBLIC_URL: 'https://addon.tudominio.com/',
    OMNI_CONFIG: '{"maxStreams":2,"enableCatalog":true}',
  });
  const manifest = await (await call('/manifest.json', { envOverride: withVars })).json();
  assert.equal(manifest.logo, 'https://addon.tudominio.com/logo.png');
  assert.equal(manifest.catalogs.length, 2);

  const streams = await (await call('/stream/movie/tt1375666.json', { envOverride: withVars })).json();
  assert.equal(streams.streams.length, 2, 'OMNI_CONFIG limita los streams del manifiesto raíz');
});

test('/configure inyecta el bootstrap desde el binding ASSETS', async () => {
  const response = await call('/configure');
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /text\/html/);
  const page = await response.text();
  assert.match(page, /Generar enlace de instalación/);
  const boot = JSON.parse(page.match(/window\.OMNI_BOOTSTRAP = (\{.*?\});/s)[1]);
  assert.equal(boot.name, 'Omni Index');
  assert.equal(boot.version, '1.0.0');
});

test('los cuatro estáticos se sirven con CORS y caché, también bajo /c/…', async () => {
  for (const [file, type] of [
    ['logo.png', 'image/png'],
    ['favicon.png', 'image/png'],
    ['favicon.ico', 'image/x-icon'],
    ['background.png', 'image/png'],
  ]) {
    const response = await call(`/${file}`);
    assert.equal(response.status, 200, file);
    assert.match(response.headers.get('content-type'), new RegExp(type.replace('/', '\\/')), file);
    assert.match(response.headers.get('cache-control'), /max-age=86400/, file);
    assert.equal(response.headers.get('access-control-allow-origin'), '*');
    const bytes = Buffer.from(await response.arrayBuffer());
    assert.deepEqual(bytes, fs.readFileSync(path.join(publicDir, file)), file);
  }

  const cfg = encodeConfig(sanitizeConfig({ ...DEFAULTS, maxStreams: 12 }));
  const prefixed = await call(`/c/${cfg}/logo.png`);
  assert.equal(prefixed.status, 200);
  assert.match(prefixed.headers.get('content-type'), /image\/png/);
});

test('/stream consulta las seis APIs usando las URLs del objeto env', async () => {
  const response = await call('/stream/movie/tt1375666.json');
  assert.equal(response.status, 200);
  const { streams } = await response.json();
  assert.ok(streams.length > 0);
  const hashes = streams.map((stream) => stream.infoHash);
  assert.equal(new Set(hashes).size, hashes.length, 'sin infohashes duplicados');
  assert.ok(hashes.every((hash) => /^[0-9a-f]{40}$/.test(hash)));
  assert.ok(mock.requests.some((entry) => entry.path === '/meta/movie/tt1375666.json'));
  assert.ok(mock.requests.some((entry) => entry.path === '/v2/search'));
  assert.ok(mock.requests.some((entry) => entry.path === '/api/search'));
});

test('un proveedor caído no rompe la respuesta (y queda en /api/status)', async () => {
  mock.forceFailure('/v2/search', 500);
  const response = await call('/stream/movie/tt1375666.json');
  assert.equal(response.status, 200);
  const { streams } = await response.json();
  assert.ok(streams.length > 0);
  const status = await (await call('/api/status')).json();
  assert.ok(status.failures.some((failure) => failure.provider === 'knaben'));
});

test('CORS, OPTIONS, HEAD, 404, 405 y configuración inválida', async () => {
  const options = await call('/manifest.json', { init: { method: 'OPTIONS' } });
  assert.equal(options.status, 204);
  assert.equal(options.headers.get('access-control-allow-origin'), '*');

  const head = await call('/manifest.json', { init: { method: 'HEAD' } });
  assert.equal(head.status, 200);
  assert.equal(await head.text(), '');

  assert.equal((await call('/recurso-inexistente.json')).status, 404);
  assert.equal((await call('/health', { init: { method: 'POST' } })).status, 405);
  const badConfig = await (await call('/c/no-es-base64-valida/manifest.json')).json();
  assert.equal(badConfig.id, 'org.omniindex.addon');
});

test('sin binding ASSETS se responde un error claro en vez de un 500 opaco', async () => {
  const withoutAssets = makeEnv(mock.url);
  delete withoutAssets.ASSETS;
  const response = await call('/configure', { envOverride: withoutAssets });
  assert.equal(response.status, 503);
  const body = await response.json();
  assert.match(body.error, /ASSETS/);
});
