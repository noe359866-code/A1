import { ADDON_VERSION } from '../manifest.js';
import { envValue } from './runtime-env.js';

const DEFAULT_USER_AGENT =
  `OmniIndexStremioAddon/${ADDON_VERSION} (+https://github.com/noe359866-code/A1)`;

export class HttpError extends Error {
  constructor(message, status = 0, url = '', cause) {
    super(message, { cause });
    this.name = 'HttpError';
    this.status = status;
    this.url = url;
  }
}

export function defaultTimeout(env) {
  const value = Number(envValue(env, 'OMNI_HTTP_TIMEOUT') || 9000);
  return Number.isFinite(value) && value > 0 ? value : 9000;
}

/**
 * AbortSignal.timeout está disponible en Node 18+ y en workerd moderno, pero
 * dejamos un respaldo con AbortController para que el addon funcione también
 * con fechas de compatibilidad antiguas de Cloudflare.
 */
function timeoutSignal(ms) {
  if (typeof AbortSignal !== 'undefined' && typeof AbortSignal.timeout === 'function') {
    return { signal: AbortSignal.timeout(ms), clear() {} };
  }
  const controller = new AbortController();
  const timer = setTimeout(() => {
    const error = new Error('timeout');
    error.name = 'TimeoutError';
    controller.abort(error);
  }, ms);
  return { signal: controller.signal, clear: () => clearTimeout(timer) };
}

/** Lee el cuerpo con límite de bytes sin usar Buffer (TextDecoder es estándar). */
async function readLimited(response, maxBytes) {
  const reader = response.body?.getReader?.();
  if (!reader) return response.text();
  const decoder = new TextDecoder();
  const encoder = new TextEncoder();
  let text = '';
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    const chunk = typeof value === 'string' ? encoder.encode(value) : value;
    total += chunk.byteLength ?? chunk.length;
    if (total > maxBytes) {
      await reader.cancel().catch(() => {});
      throw new HttpError(
        `Respuesta demasiado grande (>${maxBytes} bytes)`, 502, response.url || '',
      );
    }
    text += decoder.decode(chunk, { stream: true });
  }
  return text + decoder.decode();
}

/**
 * Petición HTTP con timeout, límite de tamaño y User-Agent propio.
 * Todas las llamadas a las APIs pasan por aquí. Los tests redirigen las URLs
 * base a un servidor HTTP local; en Cloudflare se redirigen con bindings.
 * `options.env` es el entorno del runtime (variables o bindings).
 */
export async function request(url, options = {}) {
  const {
    method = 'GET',
    headers = {},
    body,
    maxBytes = 4_000_000,
    accept,
    env,
  } = options;
  const timeout = options.timeout ?? defaultTimeout(env);

  const mergedHeaders = {
    'User-Agent': envValue(env, 'OMNI_USER_AGENT') || DEFAULT_USER_AGENT,
    ...(accept ? { Accept: accept } : {}),
    ...headers,
  };

  const { signal, clear } = timeoutSignal(timeout);
  let response;
  try {
    response = await fetch(String(url), {
      method,
      headers: mergedHeaders,
      body,
      signal,
      redirect: 'follow',
    });
  } catch (error) {
    clear();
    const reason = error?.name === 'TimeoutError' ? 'timeout' : error?.message || 'error de red';
    throw new HttpError(`No se pudo consultar ${new URL(String(url)).host}: ${reason}`, 0, String(url), error);
  }

  if (!response.ok) {
    clear();
    throw new HttpError(`HTTP ${response.status} en ${new URL(String(url)).host}`, response.status, String(url));
  }

  try {
    const text = await readLimited(response, maxBytes);
    return { status: response.status, text, headers: response.headers, url: String(url) };
  } finally {
    clear();
  }
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
