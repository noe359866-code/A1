import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  defaultConfig, decodeConfig, DEFAULTS, PROVIDER_LABELS, PROVIDER_IDS,
} from './config.js';
import { createContext, cacheSnapshot } from './context.js';
import { buildManifest, ADDON_VERSION } from './manifest.js';
import { handleStream } from './stream.js';
import { handleCatalog, handleMeta } from './catalog.js';
import { isDemo } from './lib/demo.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const indexHtml = fs.readFileSync(path.join(here, 'public', 'index.html'), 'utf8');

const bootstrap = {
  defaults: DEFAULTS,
  labels: PROVIDER_LABELS,
  ids: PROVIDER_IDS,
  version: ADDON_VERSION,
  demo: isDemo(),
};

function send(res, status, body, contentType, cacheSeconds = 0) {
  const payload = typeof body === 'string' ? body : JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': contentType,
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Accept',
    'Access-Control-Allow-Methods': 'GET, OPTIONS',
    'Cache-Control': cacheSeconds > 0 ? `public, max-age=${cacheSeconds}` : 'no-store',
    'Content-Length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

const sendJson = (res, body, options = {}) =>
  send(res, options.status || 200, body, 'application/json; charset=utf-8', options.cache || 0);

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

export function createServer() {
  return http.createServer(async (req, res) => {
    if (req.method === 'OPTIONS') {
      send(res, 204, '', 'text/plain; charset=utf-8');
      return;
    }
    if (req.method !== 'GET' && req.method !== 'HEAD') {
      sendJson(res, { error: 'Método no permitido' }, { status: 405 });
      return;
    }

    let url;
    try {
      url = new URL(req.url, 'http://localhost');
    } catch {
      sendJson(res, { error: 'URL inválida' }, { status: 400 });
      return;
    }

    let segments = url.pathname.split('/').filter(Boolean).map((segment) => {
      try { return decodeURIComponent(segment); } catch { return segment; }
    });

    let config = defaultConfig();
    // Configuración incrustada en la ruta: /c/<base64url>/manifest.json
    if (segments[0] === 'c' && segments[1]) {
      const decoded = decodeConfig(segments[1]);
      if (decoded) config = decoded;
      segments = segments.slice(2);
    } else if (url.searchParams.has('config')) {
      const decoded = decodeConfig(url.searchParams.get('config'));
      if (decoded) config = decoded;
    }

    const ctx = createContext(config);
    const [resource, a, b, c] = segments;

    try {
      switch (resource) {
        case undefined:
        case 'index.html':
        case 'configure': {
          const html = indexHtml.replace('/*__OMNI_BOOTSTRAP__*/',
            `window.OMNI_BOOTSTRAP = ${JSON.stringify(bootstrap)};`);
          send(res, 200, html, 'text/html; charset=utf-8', 0);
          return;
        }
        case 'health': {
          sendJson(res, { ok: true, version: ADDON_VERSION, demo: isDemo() });
          return;
        }
        case 'api': {
          if (a !== 'status') break;
          sendJson(res, {
            ok: true,
            version: ADDON_VERSION,
            demo: isDemo(),
            providers: Object.fromEntries(PROVIDER_IDS.map((id) => [id, {
              label: PROVIDER_LABELS[id],
              enabled: Boolean(config.providers[id]),
            }])),
            cache: cacheSnapshot(),
            failures: ctx.failures.slice(-10),
          });
          return;
        }
        case 'manifest.json': {
          sendJson(res, buildManifest(config), { cache: 3600 });
          return;
        }
        case 'stream': {
          const result = await handleStream(a, stripJson(b || ''), ctx);
          const { cacheMaxAge, ...body } = result;
          sendJson(res, body, { cache: cacheMaxAge });
          return;
        }
        case 'catalog': {
          const result = await handleCatalog(a, stripJson(b || ''), parseExtra(c || ''), ctx);
          const { cacheMaxAge, ...body } = result;
          sendJson(res, body, { cache: cacheMaxAge });
          return;
        }
        case 'meta': {
          const result = await handleMeta(a, stripJson(b || ''), ctx);
          sendJson(res, result, { cache: 3600 });
          return;
        }
        default:
          break;
      }
      sendJson(res, { error: 'Recurso no encontrado' }, { status: 404 });
    } catch (error) {
      ctx.noteFailure(resource || 'server', error);
      // Stremio tolera mejor una respuesta vacía que un 500: no rompe la UI.
      sendJson(res, emptyFor(resource), { cache: 60 });
    }
  });
}
