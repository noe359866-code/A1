import { ADDON_VERSION } from '../manifest.js';

const USER_AGENT = process.env.OMNI_USER_AGENT ||
  `OmniIndexStremioAddon/${ADDON_VERSION} (+https://github.com/noe359866-code/A1)`;

export class HttpError extends Error {
  constructor(message, status = 0, url = '', cause) {
    super(message, { cause });
    this.name = 'HttpError';
    this.status = status;
    this.url = url;
  }
}

export function defaultTimeout() {
  const value = Number(process.env.OMNI_HTTP_TIMEOUT || 9000);
  return Number.isFinite(value) && value > 0 ? value : 9000;
}

async function readLimited(response, maxBytes) {
  const reader = response.body?.getReader?.();
  if (!reader) return response.text();
  const chunks = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.length;
    if (total > maxBytes) {
      await reader.cancel().catch(() => {});
      throw new HttpError(`Respuesta demasiado grande (>${maxBytes} bytes)`, 502, response.url);
    }
    chunks.push(Buffer.from(value));
  }
  return Buffer.concat(chunks).toString('utf8');
}

/**
 * Petición HTTP con timeout, límite de tamaño y User-Agent propio.
 * Todas las llamadas a las APIs pasan por aquí. Los tests redirigen las URLs
 * base a un servidor HTTP local mediante variables de entorno.
 */
export async function request(url, options = {}) {
  const {
    method = 'GET',
    headers = {},
    body,
    timeout = defaultTimeout(),
    maxBytes = 4_000_000,
    accept,
  } = options;

  const mergedHeaders = {
    'User-Agent': USER_AGENT,
    ...(accept ? { Accept: accept } : {}),
    ...headers,
  };

  let response;
  try {
    response = await fetch(String(url), {
      method,
      headers: mergedHeaders,
      body,
      signal: AbortSignal.timeout(timeout),
      redirect: 'follow',
    });
  } catch (error) {
    const reason = error?.name === 'TimeoutError' ? 'timeout' : error?.message || 'error de red';
    throw new HttpError(`No se pudo consultar ${new URL(String(url)).host}: ${reason}`, 0, String(url), error);
  }

  if (!response.ok) {
    throw new HttpError(`HTTP ${response.status} en ${new URL(String(url)).host}`, response.status, String(url));
  }

  const text = await readLimited(response, maxBytes);
  return { status: response.status, text, headers: response.headers, url: String(url) };
}

export async function getJson(url, options = {}) {
  const response = await request(url, { ...options, method: 'GET', accept: 'application/json' });
  try {
    return JSON.parse(response.text);
  } catch (error) {
    throw new HttpError(`JSON inválido en ${new URL(String(url)).host}`, 502, String(url), error);
  }
}

export async function getText(url, options = {}) {
  const response = await request(url, { ...options, method: 'GET' });
  return response.text;
}

export async function postJson(url, payload, options = {}) {
  const body = JSON.stringify(payload);
  const response = await request(url, {
    ...options,
    method: 'POST',
    body,
    accept: 'application/json',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  });
  try {
    return JSON.parse(response.text);
  } catch (error) {
    throw new HttpError(`JSON inválido tras POST en ${new URL(String(url)).host}`, 502, String(url), error);
  }
}
