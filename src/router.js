/**
 * Núcleo HTTP del addon, escrito sólo con APIs web (Request / Response / fetch).
 *
 * Lo usan los dos runtimes:
 *   - src/server.js  -> adaptador node:http (local, Docker, Beamup)
 *   - src/worker.js  -> Cloudflare Workers (handler `fetch`)
 *
 * IMPORTANTE: no importar aquí módulos de Node (`node:*`), ni usar la API
 * Buffer ni el objeto global process. El bundle del Worker se construye sin
 * `nodejs_compat`; tests/worker.test.js verifica este contrato.
 */
import {
  defaultConfig, decodeConfig, DEFAULTS, PROVIDER_LABELS, PROVIDER_IDS,
} from './config.js';
import { createContext, cacheSnapshot } from './context.js';
import {
  buildManifest, ADDON_VERSION, ADDON_NAME, ADDON_TAGLINE,
} from './manifest.js';
import { handleStream } from './stream.js';
import { handleCatalog, handleMeta } from './catalog.js';
import { envValue } from './lib/runtime-env.js';
import { ASSET_FILES, contentTypeFor } from './lib/assets.js';

export const bootstrap = Object.freeze({
  defaults: DEFAULTS,
  labels: PROVIDER_LABELS,
  ids: PROVIDER_IDS,
  version: ADDON_VERSION,
  name: ADDON_NAME,
  tagline: ADDON_TAGLINE,
});

const CORS_HEADERS = Object.freeze({
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Content-Type, Accept',
  'Access-Control-Allow-Methods': 'GET, OPTIONS',
});

const NO_ASSETS_ERROR =
  'Assets no disponibles: configura [assets] y el binding ASSETS en wrangler.toml';

function cacheHeader(seconds) {
  return seconds > 0 ? `public, max-age=${seconds}` : 'no-store';
}

function json(body, { status = 200, cache = 0 } = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...CORS_HEADERS,
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': cacheHeader(cache),
    },
  });
}

function html(page) {
  return new Response(page, {
    status: 200,
    headers: {
      ...CORS_HEADERS,
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-store',
    },
  });
}

function binary(body, contentType, cache = 86_400) {
  return new Response(body, {
    status: 200,
    headers: {
      ...CORS_HEADERS,
      'Content-Type': contentType,
      'Cache-Control': cacheHeader(cache),
    },
  });
}

function noContent() {
  return new Response(null, {
    status: 204,
    headers: { ...CORS_HEADERS, 'Cache-Control': 'no-store' },
  });
}

/**
 * Origen público para las URLs absolutas de `logo` y `background`.
 *
 * En Cloudflare `request.url` ya es la URL pública (HTTPS), así que NO se
 * confía en cabeceras X-Forwarded-* que podría enviar un cliente. El adaptador
 * Node, en cambio, pasa `originOverride` calculado a partir de Host /
 * X-Forwarded-* para seguir funcionando detrás del proxy de Beamup.
 * `OMNI_PUBLIC_URL` (variable o binding) gana en ambos casos.
 */
export function resolveOrigin(request, env, originOverride = '') {
  const configured = envValue(env, 'OMNI_PUBLIC_URL').trim().replace(/\/+$/, '');
  if (configured) return configured;
  if (originOverride) return originOverride;
  try {
    return new URL(request.url).origin;
  } catch {
    return 'http://localhost:7000';
  }
}

function stripJson(segment = '') {
  return segment.endsWith('.json') ? segment.slice(0, -5) : segment;
}

/** Extra props de catálogo: `search=foo&skip=20.json` */
function parseExtra(segment = '') {
  const result = {};
  const raw = stripJson(segment);
  if (!raw) return result;
  for (const pair of raw.split('&')) {
    const index = pair.indexOf('=');
    if (index < 1) continue;
    const key = pair.slice(0, index);
    try {
      result[key] = decodeURIComponent(pair.slice(index + 1));
    } catch {
      result[key] = pair.slice(index + 1);
    }
  }
  return result;
}

function emptyFor(resource) {
  if (resource === 'stream') return { streams: [] };
  if (resource === 'catalog') return { metas: [] };
  if (resource === 'meta') return { meta: null };
  return {};
}

function decodeSegment(segment) {
  try {
    return decodeURIComponent(segment);
  } catch {
    return segment;
  }
}

async function route(request, { env, assets, origin: originOverride, runtime }) {
  let url;
  try {
    url = new URL(request.url);
  } catch {
    return json({ error: 'URL inválida' }, { status: 400 });
  }

  let segments = url.pathname.split('/').filter(Boolean).map(decodeSegment);

  let config = defaultConfig(env);
  // Configuración incrustada en la ruta: /c/<base64url>/manifest.json
  if (segments[0] === 'c' && segments[1]) {
    const decoded = decodeConfig(segments[1]);
    if (decoded) config = decoded;
    segments = segments.slice(2);
  } else if (url.searchParams.has('config')) {
    const decoded = decodeConfig(url.searchParams.get('config'));
    if (decoded) config = decoded;
  }

  const ctx = createContext(config, { env });
  const origin = resolveOrigin(request, env, originOverride);
  const [resource, a, b, c] = segments;

  try {
    switch (resource) {
      case undefined:
      case 'index.html':
      case 'configure': {
        const page = await assets?.html?.(request.url);
        if (!page) return json({ error: NO_ASSETS_ERROR }, { status: 503 });
        return html(page.replace('/*__OMNI_BOOTSTRAP__*/',
          `window.OMNI_BOOTSTRAP = ${JSON.stringify(bootstrap)};`));
      }
      case 'logo.png':
      case 'favicon.png':
      case 'favicon.ico':
      case 'background.png': {
        if (!ASSET_FILES.includes(resource)) break;
        const file = await assets?.file?.(resource, request.url);
        if (!file) return json({ error: NO_ASSETS_ERROR }, { status: 503 });
        return binary(file.body, file.contentType || contentTypeFor(resource));
      }
      case 'health': {
        return json({ ok: true, version: ADDON_VERSION, name: ADDON_NAME, runtime });
      }
      case 'api': {
        if (a !== 'status') break;
        return json({
          ok: true,
          version: ADDON_VERSION,
          runtime,
          origin,
          providers: Object.fromEntries(PROVIDER_IDS.map((id) => [id, {
            label: PROVIDER_LABELS[id],
            enabled: Boolean(config.providers[id]),
          }])),
          cache: cacheSnapshot(),
          failures: ctx.failures.slice(-10),
        });
      }
      case 'manifest.json': {
        return json(buildManifest(config, origin), { cache: 3600 });
      }
      case 'stream': {
        const result = await handleStream(a, stripJson(b || ''), ctx);
        const { cacheMaxAge, ...body } = result;
        return json(body, { cache: cacheMaxAge });
      }
      case 'catalog': {
        const result = await handleCatalog(a, stripJson(b || ''), parseExtra(c || ''), ctx);
        const { cacheMaxAge, ...body } = result;
        return json(body, { cache: cacheMaxAge });
      }
      case 'meta': {
        const result = await handleMeta(a, stripJson(b || ''), ctx);
        return json(result, { cache: 3600 });
      }
      default:
        break;
    }
    return json({ error: 'Recurso no encontrado' }, { status: 404 });
  } catch (error) {
    ctx.noteFailure(resource || 'server', error);
    // Stremio tolera mejor una respuesta vacía que un 500: no rompe la UI.
    return json(emptyFor(resource), { cache: 60 });
  }
}

/**
 * Maneja una petición web y devuelve una Response estándar.
 *
 * @param {Request} request
 * @param {{env?: object, assets?: object, origin?: string, runtime?: string}} options
 *   - `env`: variables de proceso (Node) o bindings/vars (Cloudflare).
 *   - `assets`: proveedor de estáticos con `html(baseUrl)` y `file(name, baseUrl)`.
 *   - `origin`: origen público precalculado por el adaptador Node (proxy).
 *   - `runtime`: etiqueta informativa para /health y /api/status.
 */
export async function handleRequest(request, options = {}) {
  if (request.method === 'OPTIONS') return noContent();
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    return json({ error: 'Método no permitido' }, { status: 405 });
  }

  const response = await route(request, {
    runtime: 'node',
    ...options,
  });

  // HEAD conserva estado y cabeceras, pero nunca cuerpo.
  if (request.method === 'HEAD' && response.status !== 204) {
    return new Response(null, { status: response.status, headers: response.headers });
  }
  return response;
}
