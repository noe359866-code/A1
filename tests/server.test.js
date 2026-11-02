import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { startMockServer, pointEnvAt } from './mock-server.js';
import { createServer } from '../src/server.js';
import { setLimiterIntervals, resetCache } from '../src/context.js';
import { encodeConfig, sanitizeConfig, DEFAULTS } from '../src/config.js';
import { encodeReleaseId } from '../src/lib/query.js';

let mock;
let addon;
let base;

async function startAddon() {
  const server = createServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return { server, base: `http://127.0.0.1:${server.address().port}` };
}

async function get(path) {
  const response = await fetch(`${base}${path}`);
  return { status: response.status, headers: response.headers, body: await response.json() };
}

function configUrl(config, path) {
  return `/c/${encodeConfig(sanitizeConfig({ ...DEFAULTS, ...config }))}${path}`;
}

before(async () => {
  mock = await startMockServer();
  pointEnvAt(mock.url);
  setLimiterIntervals(0);
  ({ server: addon, base } = await startAddon());
});
after(async () => {
  await new Promise((resolve) => addon.close(resolve));
  await mock.close();
});
beforeEach(() => {
  mock.requests.length = 0;
  mock.clearFailures();
  resetCache();
});

test('GET /health y /api/status', async () => {
  const health = await get('/health');
  assert.equal(health.status, 200);
  assert.equal(health.body.ok, true);
  const status = await get('/api/status');
  assert.equal(status.body.providers.knaben.enabled, true);
  assert.equal(Object.keys(status.body.providers).length, 6);
  assert.ok(status.body.cache);
});

test('GET /configure sirve la página de configuración', async () => {
  const response = await fetch(`${base}/configure`);
  const html = await response.text();
  assert.match(response.headers.get('content-type'), /text\/html/);
  assert.match(html, /window\.OMNI_BOOTSTRAP/);
  assert.match(html, /Generar enlace de instalación/);
});

test('manifiesto por defecto: streams para IMDb y release:, sin catálogos', async () => {
  const { body } = await get('/manifest.json');
  assert.equal(body.id, 'org.omniindex.addon');
  assert.equal(body.behaviorHints.configurable, true);
  assert.equal(body.behaviorHints.p2p, true, 'debe avisar de que usa P2P');
  assert.deepEqual(body.catalogs, []);
  const stream = body.resources.find((resource) => resource.name === 'stream');
  assert.deepEqual(stream.idPrefixes, ['tt', 'release:']);
});

test('manifiesto con catálogo activado anuncia catalog y meta', async () => {
  const { body } = await get(configUrl({ enableCatalog: true }, '/manifest.json'));
  assert.equal(body.catalogs.length, 2);
  assert.ok(body.resources.some((resource) => resource.name === 'catalog'));
  assert.ok(body.resources.some((resource) => resource.name === 'meta'));
});

test('stream de película: dedupe, infohash válido, sources y descripción', async () => {
  const { status, headers, body } = await get('/stream/movie/tt1375666.json');
  assert.equal(status, 200);
  assert.equal(headers.get('access-control-allow-origin'), '*');
  assert.ok(body.streams.length > 0);
  const hashes = body.streams.map((stream) => stream.infoHash);
  assert.equal(new Set(hashes).size, hashes.length, 'sin infohashes duplicados');
  assert.ok(hashes.every((hash) => /^[0-9a-f]{40}$/.test(hash)));
  for (const stream of body.streams) {
    assert.ok(stream.sources.every((source) => source.startsWith('tracker:')));
    assert.match(stream.description, /seeders/);
    assert.ok(stream.behaviorHints.bingeGroup);
  }
  assert.ok(body.streams.every((stream) => !/\.flac|HD-CAM/i.test(stream.description)));
  assert.ok(mock.requests.some((entry) => entry.path === '/meta/movie/tt1375666.json'));
});

test('config maxStreams y desactivar proveedores se respetan', async () => {
  const limited = await get(configUrl({ maxStreams: 2, expansion: false }, '/stream/movie/tt1375666.json'));
  assert.equal(limited.body.streams.length, 2);

  resetCache();
  mock.requests.length = 0;
  const providers = {
    knaben: true, torrentsCsv: false, torlock: false,
    predbNet: false, predatabaSe: false, xrel: false,
  };
  const single = await get(configUrl({ providers, expansion: false }, '/stream/movie/tt1375666.json'));
  assert.ok(single.body.streams.length > 0);
  assert.ok(mock.requests.some((entry) => entry.path === '/v2/search'));
  assert.ok(!mock.requests.some((entry) => entry.path === '/torznab/api'));
  assert.ok(!mock.requests.some((entry) => entry.path === '/service/search'));
  assert.ok(!mock.requests.some((entry) => entry.path === '/api/search'));
});

test('stream de serie: consulta S01E02 y prioriza el episodio correcto', async () => {
  const { body } = await get('/stream/series/tt0903747:1:2.json');
  assert.ok(body.streams.length > 0);
  const knabenRequest = mock.requests.find((entry) => entry.path === '/v2/search');
  assert.match(knabenRequest.query.q, /S01E02/);
  const torlockRequest = mock.requests.find((entry) => entry.path === '/torznab/api');
  assert.equal(torlockRequest.query.t, 'tvsearch');
  assert.equal(torlockRequest.query.season, '1');
  assert.equal(torlockRequest.query.ep, '2');
  assert.match(body.streams[0].description.split('\n')[0], /S01E01|S01\./,
    'el episodio correcto (o el pack de la temporada) va primero');
});

test('catálogo de búsqueda y ficha meta para ids release:', async () => {
  const catalog = await get(configUrl({ enableCatalog: true },
    '/catalog/movie/omni-scene/search=Inception.json'));
  assert.ok(catalog.body.metas.length > 0);
  assert.ok(catalog.body.metas.every((meta) => meta.id.startsWith('release:')));
  assert.equal(catalog.body.metas[0].type, 'movie');

  const releaseId = encodeReleaseId('Inception.2010.1080p.BluRay.x264-GROUP');
  const meta = await get(configUrl({ enableCatalog: true }, `/meta/movie/${encodeURIComponent(releaseId)}.json`));
  assert.equal(meta.body.meta.id, releaseId);
  assert.equal(meta.body.meta.name, 'Inception 2010 1080p BluRay x264-GROUP');

  const stream = await get(configUrl({ enableCatalog: true, expansion: false },
    `/stream/movie/${encodeURIComponent(releaseId)}.json`));
  assert.ok(stream.body.streams.length > 0, 'un id release: busca por nombre exacto');
});

test('fallo de un proveedor no rompe la respuesta de Stremio', async () => {
  mock.forceFailure('/v2/search', 500);
  const { status, body } = await get('/stream/movie/tt1375666.json');
  assert.equal(status, 200);
  assert.ok(body.streams.length > 0, 'los otros proveedores siguen aportando');
  const statusResponse = await get('/api/status');
  assert.ok(statusResponse.body.failures.some((failure) => failure.provider === 'knaben'));
});

test('CORS, OPTIONS, 404 y configuración inválida', async () => {
  const options = await fetch(`${base}/manifest.json`, { method: 'OPTIONS' });
  assert.equal(options.status, 204);
  assert.equal(options.headers.get('access-control-allow-origin'), '*');

  const notFound = await get('/recurso-inexistente.json');
  assert.equal(notFound.status, 404);

  const badConfig = await get('/c/no-es-base64-valida/manifest.json');
  assert.equal(badConfig.status, 200);
  assert.equal(badConfig.body.id, 'org.omniindex.addon');
});
