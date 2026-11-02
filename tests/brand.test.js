/** Identidad del addon: nombre, ID, versión, descripción y assets servidos. */
import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import {
  ADDON_ID, ADDON_VERSION, ADDON_NAME, ADDON_TAGLINE, ADDON_DESCRIPTION,
  CATALOG_NAME, brandAssets, buildManifest,
} from '../src/manifest.js';
import { defaultConfig, DEFAULTS, sanitizeConfig, encodeConfig } from '../src/config.js';
import { createServer } from '../src/server.js';

const PNG_MAGIC = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

let server;
let base;

before(async () => {
  server = createServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  await new Promise((resolve) => server.close(resolve));
});

test('identidad estable y coherente con el paquete', async () => {
  const pkg = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
  assert.equal(ADDON_ID, 'org.omniindex.addon');
  assert.equal(ADDON_NAME, 'Omni Index');
  assert.equal(ADDON_TAGLINE, 'Seis índices, una búsqueda');
  assert.equal(pkg.version, ADDON_VERSION);
  assert.match(ADDON_VERSION, /^\d+\.\d+\.\d+$/);
  assert.ok(ADDON_DESCRIPTION.length < 220, 'descripción corta para Stremio');
  assert.match(ADDON_DESCRIPTION, /Knaben/);
});

test('brandAssets sólo produce URLs absolutas y normaliza la barra final', () => {
  assert.deepEqual(brandAssets('https://addon.example/'), {
    logo: 'https://addon.example/logo.png',
    background: 'https://addon.example/background.png',
  });
  assert.deepEqual(brandAssets(''), {});
  assert.deepEqual(brandAssets('/relativo'), {});
  assert.deepEqual(brandAssets('javascript:alert(1)'), {});
});

test('el manifiesto anuncia logo y fondo cuando conoce el origen', () => {
  const manifest = buildManifest(defaultConfig(), 'https://addon.example');
  assert.equal(manifest.name, ADDON_NAME);
  assert.equal(manifest.description, ADDON_DESCRIPTION);
  assert.equal(manifest.logo, 'https://addon.example/logo.png');
  assert.equal(manifest.background, 'https://addon.example/background.png');
  assert.equal(buildManifest(defaultConfig()).logo, undefined);
});

test('los catálogos llevan el nombre de marca', () => {
  const config = sanitizeConfig({ ...DEFAULTS, enableCatalog: true });
  const manifest = buildManifest(config, base);
  assert.equal(manifest.catalogs.length, 2);
  assert.ok(manifest.catalogs.every((catalog) => catalog.name === CATALOG_NAME));
});

test('los cuatro assets de marca se sirven por HTTP', async () => {
  for (const [file, type] of [
    ['logo.png', 'image/png'],
    ['favicon.png', 'image/png'],
    ['favicon.ico', 'image/x-icon'],
    ['background.png', 'image/png'],
  ]) {
    const response = await fetch(`${base}/${file}`);
    assert.equal(response.status, 200, file);
    assert.match(response.headers.get('content-type'), new RegExp(type.replace('/', '\\/')), file);
    assert.match(response.headers.get('cache-control'), /max-age=86400/);
    const buffer = Buffer.from(await response.arrayBuffer());
    assert.ok(buffer.length > 1000, `${file} no vacío`);
    if (file.endsWith('.png')) assert.deepEqual(buffer.subarray(0, 8), PNG_MAGIC, file);
  }
});

test('los assets también funcionan bajo el prefijo de configuración /c/…', async () => {
  const cfg = encodeConfig(sanitizeConfig({ ...DEFAULTS, maxStreams: 12 }));
  const response = await fetch(`${base}/c/${cfg}/logo.png`);
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /image\/png/);
});

test('el origen del manifiesto se deduce de Host y X-Forwarded-*', async () => {
  const plain = await (await fetch(`${base}/manifest.json`)).json();
  assert.equal(new URL(plain.logo).origin, new URL(base).origin);

  const forwarded = await (await fetch(`${base}/manifest.json`, {
    headers: {
      'x-forwarded-proto': 'https',
      'x-forwarded-host': 'hash-omniindex.baby-beamup.club',
    },
  })).json();
  assert.equal(forwarded.logo, 'https://hash-omniindex.baby-beamup.club/logo.png');
  assert.equal(forwarded.background, 'https://hash-omniindex.baby-beamup.club/background.png');
});

test('OMNI_PUBLIC_URL fija las URLs aunque el proxy no reenvíe Host', async () => {
  const previous = process.env.OMNI_PUBLIC_URL;
  process.env.OMNI_PUBLIC_URL = 'https://public.example/';
  try {
    const manifest = await (await fetch(`${base}/manifest.json`)).json();
    assert.equal(manifest.logo, 'https://public.example/logo.png');
    assert.equal(manifest.background, 'https://public.example/background.png');
  } finally {
    if (previous === undefined) delete process.env.OMNI_PUBLIC_URL;
    else process.env.OMNI_PUBLIC_URL = previous;
  }
});

test('la página de configuración muestra logo, favicon, nombre y eslogan', async () => {
  const html = await (await fetch(`${base}/configure`)).text();
  assert.match(html, /rel="icon"/);
  assert.match(html, /src="\/logo\.png"/);
  assert.match(html, /id="addon-name"/);
  assert.match(html, /id="addon-tagline"/);
  const boot = JSON.parse(html.match(/window\.OMNI_BOOTSTRAP = (\{.*?\});/s)[1]);
  assert.equal(boot.name, ADDON_NAME);
  assert.equal(boot.tagline, ADDON_TAGLINE);
  assert.equal(boot.version, ADDON_VERSION);
});
