/**
 * Adaptador node:http para local, Docker y Beamup.
 *
 * Toda la lógica de rutas vive en router.js y se comparte con el Worker; aquí
 * sólo convertimos IncomingMessage -> Request y Response -> ServerResponse,
 * y calculamos el origen público detrás del proxy de Dokku/Herokuish.
 */
import http from 'node:http';
import { handleRequest } from './router.js';
import { createNodeAssets } from './assets-node.js';
import { envValue } from './lib/runtime-env.js';

function firstHeader(value) {
  return String(Array.isArray(value) ? value[0] : value || '').split(',')[0].trim();
}

/** Host / X-Forwarded-* del proxy de Beamup (el Worker usa request.url). */
function nodePublicOrigin(req, env) {
  const configured = envValue(env, 'OMNI_PUBLIC_URL').trim().replace(/\/+$/, '');
  if (configured) return configured;

  const proto = firstHeader(req.headers['x-forwarded-proto'])
    || (req.socket.encrypted ? 'https' : 'http');
  const host = firstHeader(req.headers['x-forwarded-host'] || req.headers.host)
    || 'localhost:7000';
  try {
    return new URL(`${proto}://${host}`).origin;
  } catch {
    return 'http://localhost:7000';
  }
}

function toWebRequest(req) {
  const origin = nodePublicOrigin(req, undefined);
  let url;
  try {
    url = new URL(req.url, origin);
  } catch {
    url = new URL('/', 'http://localhost:7000');
  }
  return new Request(url, { method: req.method || 'GET' });
}

async function writeWebResponse(res, response, isHead) {
  const headers = Object.fromEntries(response.headers);
  if (isHead) {
    res.writeHead(response.status, headers);
    res.end();
    return;
  }
  const payload = Buffer.from(await response.arrayBuffer());
  headers['Content-Length'] = payload.length;
  res.writeHead(response.status, headers);
  res.end(payload);
}

/**
 * @param {{env?: object, assets?: object}} options
 *   Permite inyectar variables y estáticos (útil en tests sin tocar el
 *   entorno global del proceso).
 */
export function createServer(options = {}) {
  const env = options.env ?? process.env;
  const assets = options.assets ?? createNodeAssets();

  return http.createServer(async (req, res) => {
    const request = toWebRequest(req);
    const response = await handleRequest(request, {
      env,
      assets,
      runtime: 'node',
      origin: nodePublicOrigin(req, env),
    });
    await writeWebResponse(res, response, req.method === 'HEAD');
  });
}
