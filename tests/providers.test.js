import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { startMockServer, pointEnvAt, H } from './mock-server.js';
import { createContext, setLimiterIntervals, resetCache } from '../src/context.js';
import { sanitizeConfig, DEFAULTS } from '../src/config.js';
import * as knaben from '../src/providers/knaben.js';
import * as torrentsCsv from '../src/providers/torrents-csv.js';
import * as torlock from '../src/providers/torlock.js';
import * as predbNet from '../src/providers/predb-net.js';
import * as predatabaSe from '../src/providers/predataba-se.js';
import * as xrel from '../src/providers/xrel.js';
import { enabledTorrentProviders, enabledReleaseProviders, byId } from '../src/providers/index.js';

let mock;
const movieQuery = {
  text: 'Inception 2010', title: 'Inception', year: '2010', imdbId: 'tt1375666',
  type: 'movie', season: null, episode: null, mode: 'title',
};
const seriesQuery = {
  text: 'Breaking Bad S01E02', title: 'Breaking Bad', year: '2008', imdbId: 'tt0903747',
  type: 'series', season: 1, episode: 2, mode: 'title',
};

function context(overrides = {}) {
  return createContext(sanitizeConfig({ ...DEFAULTS, ...overrides }));
}

before(async () => {
  mock = await startMockServer();
  pointEnvAt(mock.url);
  setLimiterIntervals(0);
});
after(async () => { await mock.close(); });
beforeEach(() => {
  mock.requests.length = 0;
  mock.clearFailures();
  resetCache();
});

test('registro de proveedores: 3 con infohash y 3 de metadatos', () => {
  assert.equal(enabledTorrentProviders(context().config).length, 3);
  assert.equal(enabledReleaseProviders(context().config).length, 3);
  assert.equal(byId('xrel').label, 'xREL');
  assert.equal(byId('no-existe'), undefined);
});

test('Knaben v2: GET /v2/search con q, sf=title, orden por seeders y sin flags inseguros', async () => {
  const results = await knaben.search(movieQuery, context());
  const request = mock.requests.find((entry) => entry.path === '/v2/search');
  assert.ok(request);
  assert.equal(request.method, 'GET');
  assert.equal(request.query.q, 'Inception 2010');
  assert.equal(request.query.sf, 'title');
  assert.equal(request.query.o, 'seeders');
  assert.equal(request.query.xxx, undefined);
  assert.equal(request.query.unsafe, undefined);
  assert.equal(request.query.dead, undefined);
  assert.equal(results.length, 3);
  assert.equal(results[0].infoHash, H.inception1080);
  assert.equal(results[0].seeders, 900);
  assert.ok(results[0].magnet.startsWith('magnet:'));
  assert.ok(results[0].trackers.length > 0);
});

test('torrents-csv: usa to_tsquery, omite size y pagina con after', async () => {
  const results = await torrentsCsv.search(movieQuery, context());
  const request = mock.requests.find((entry) => entry.path === '/service/search');
  assert.equal(request.query.q, 'inception 2010');
  assert.equal(request.query.size, undefined); // el servidor no soporta `size`
  assert.equal(results.length, 4);

  resetCache();
  mock.requests.length = 0;
  await torrentsCsv.search({ ...movieQuery, text: 'paginate' }, context({ perProviderLimit: 60 }));
  assert.ok(mock.requests.some((entry) => entry.query.after === '999999'),
    'debe solicitar la segunda página con el cursor next');
});

test('Torlock: Torznab movie-search con cat=2000 y attrs parseados', async () => {
  const results = await torlock.search(movieQuery, context());
  const request = mock.requests.find((entry) => entry.path === '/torznab/api');
  assert.equal(request.query.t, 'movie-search');
  assert.equal(request.query.cat, '2000');
  assert.ok(Number(request.query.limit) <= 100);
  assert.equal(results.length, 1);
  assert.equal(results[0].infoHash, H.inception4k);
  assert.equal(results[0].seeders, 100);
  assert.equal(results[0].leechers, 30);
  assert.equal(results[0].sizeBytes, 7_132_263_739);
});

test('Torlock: tvsearch envía season y ep', async () => {
  await torlock.search(seriesQuery, context());
  const request = mock.requests.find((entry) => entry.path === '/torznab/api');
  assert.equal(request.query.t, 'tvsearch');
  assert.equal(request.query.cat, '5000');
  assert.equal(request.query.season, '1');
  assert.equal(request.query.ep, '2');
});

test('PreDB.net: prefijo con puntos, límite 25 y releases nuked excluidos', async () => {
  const results = await predbNet.search(movieQuery, context());
  const request = mock.requests.find((entry) => entry.path === '/');
  assert.equal(request.query.type, 'search');
  assert.equal(request.query.q, 'Inception');
  assert.equal(request.query.limit, '25');
  assert.equal(results.length, 2, 'el release con status=1 (nuked) se descarta');
  assert.equal(results[0].sizeBytes, 4500 * 1024 * 1024);
  assert.equal(results[0].source, 'PreDB.net');
  assert.ok(results[0].url.startsWith('https://predb.net/'));
});

test('predataba.se: POST con array de filtros, cabecera x-type y filtrado por título', async () => {
  const results = await predatabaSe.search(movieQuery, context());
  const request = mock.requests.find((entry) => entry.method === 'POST' && entry.path === '/api/search');
  assert.ok(request, 'debe usar POST /api/search');
  assert.deepEqual(JSON.parse(request.body), ['Inception']);
  assert.equal(request.headers['x-type'], 'scene');
  assert.equal(results.length, 1, 'descarta resultados que no coinciden con el título');
  assert.match(results[0].name, /^Inception/);
});

test('predataba.se: si POST falla, cae a GET con query JSON', async () => {
  mock.forceFailure('/api/search', 405, 'POST');
  const results = await predatabaSe.search(movieQuery, context());
  const get = mock.requests.find((entry) => entry.method === 'GET' && entry.path === '/api/search');
  assert.ok(get, 'debe intentar GET como respaldo');
  assert.deepEqual(JSON.parse(get.query.query), ['Inception']);
  assert.equal(results.length, 1);
});

test('xREL: usa results + p2p_results y filtra por IMDb', async () => {
  const results = await xrel.search(movieQuery, context());
  const request = mock.requests.find((entry) => entry.path === '/v2/search/releases.json');
  assert.equal(request.query.scene, '1');
  assert.equal(request.query.p2p, '1');
  assert.equal(results.length, 2, 'descarta el release con imdb:tt9999999');
  assert.ok(results.every((item) => item.name.startsWith('Inception')));
  assert.equal(results[0].imdb, 'tt1375666');
  assert.equal(results[0].sizeBytes, 4500 * 1024 * 1024);
  assert.equal(results[1].sizeBytes, 52400 * 1024 * 1024);
});

test('proveedores de releases ofrecen recent() para el catálogo', async () => {
  const ctx = context();
  for (const provider of [predbNet, predatabaSe, xrel]) {
    const releases = await provider.recent(ctx, 20);
    assert.ok(Array.isArray(releases));
    assert.ok(releases.length > 0);
    assert.ok(releases[0].name);
  }
  assert.ok(mock.requests.some((entry) => entry.path === '/v2/p2p/releases.json'));
});
