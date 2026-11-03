import { encodeBase64Url, decodeBase64Url } from './lib/encoding.js';
import { asEnv, envValue } from './lib/runtime-env.js';

export const TORRENT_PROVIDERS = Object.freeze(['knaben', 'torrentsCsv', 'torlock']);
export const RELEASE_PROVIDERS = Object.freeze(['predbNet', 'predatabaSe', 'xrel']);
export const PROVIDER_IDS = Object.freeze([...TORRENT_PROVIDERS, ...RELEASE_PROVIDERS]);

export const PROVIDER_LABELS = Object.freeze({
  knaben: 'Knaben',
  torrentsCsv: 'torrents-csv',
  torlock: 'Torlock',
  predbNet: 'PreDB.net',
  predatabaSe: 'predataba.se',
  xrel: 'xREL',
});

export const DEFAULTS = Object.freeze({
  providers: {
    knaben: true,
    torrentsCsv: true,
    torlock: true,
    predbNet: true,
    predatabaSe: true,
    xrel: true,
  },
  // Proveedores de metadatos (sin infohash) que alimentan la expansión
  expansion: true,
  maxExpansionQueries: 2,
  expansionProviders: ['knaben', 'torrentsCsv'],
  // Credenciales / variantes
  predatabaKey: '',
  predatabaType: 'scene',
  xrelAlternate: false,
  // Resultados
  maxStreams: 40,
  minSeeders: 1,
  perProviderLimit: 60,
  minResults: 8,
  sortBy: 'balanced',
  excludeCam: true,
  enableCatalog: false,
  cacheTtl: 900,
  httpTimeout: 9000,
});

function asBoolean(value, fallback) {
  if (value === undefined || value === null || value === '') return fallback;
  if (typeof value === 'boolean') return value;
  const text = String(value).toLowerCase();
  return ['1', 'true', 'on', 'yes', 'si', 'sí'].includes(text);
}

function asNumber(value, fallback, min, max) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, Math.round(parsed)));
}

function asChoice(value, fallback, choices) {
  return choices.includes(value) ? value : fallback;
}

function asProviderMap(value) {
  const result = { ...DEFAULTS.providers };
  if (Array.isArray(value)) {
    for (const id of PROVIDER_IDS) result[id] = value.includes(id);
  } else if (value && typeof value === 'object') {
    for (const id of PROVIDER_IDS) {
      if (value[id] !== undefined) result[id] = asBoolean(value[id], result[id]);
    }
  }
  return result;
}

/** Valida cualquier configuración (URL, env o UI) y aplica límites seguros. */
export function sanitizeConfig(raw = {}) {
  const input = raw && typeof raw === 'object' ? raw : {};
  const expansionProviders = Array.isArray(input.expansionProviders)
    ? input.expansionProviders.filter((id) => TORRENT_PROVIDERS.includes(id)).slice(0, 3)
    : [...DEFAULTS.expansionProviders];

  return {
    providers: asProviderMap(input.providers),
    expansion: asBoolean(input.expansion, DEFAULTS.expansion),
    maxExpansionQueries: asNumber(input.maxExpansionQueries, DEFAULTS.maxExpansionQueries, 0, 6),
    expansionProviders: expansionProviders.length ? expansionProviders : [...DEFAULTS.expansionProviders],
    predatabaKey: String(input.predatabaKey || '').slice(0, 200),
    predatabaType: asChoice(input.predatabaType, DEFAULTS.predatabaType, ['scene', 'p2p', 'both']),
    xrelAlternate: asBoolean(input.xrelAlternate, DEFAULTS.xrelAlternate),
    maxStreams: asNumber(input.maxStreams, DEFAULTS.maxStreams, 1, 150),
    minSeeders: asNumber(input.minSeeders, DEFAULTS.minSeeders, 0, 1000),
    perProviderLimit: asNumber(input.perProviderLimit, DEFAULTS.perProviderLimit, 5, 200),
    minResults: asNumber(input.minResults, DEFAULTS.minResults, 0, 100),
    sortBy: asChoice(input.sortBy, DEFAULTS.sortBy, ['balanced', 'seeders', 'quality']),
    excludeCam: asBoolean(input.excludeCam, DEFAULTS.excludeCam),
    enableCatalog: asBoolean(input.enableCatalog, DEFAULTS.enableCatalog),
    cacheTtl: asNumber(input.cacheTtl, DEFAULTS.cacheTtl, 60, 86_400),
    httpTimeout: asNumber(input.httpTimeout, DEFAULTS.httpTimeout, 1000, 60_000),
  };
}

export function encodeConfig(config) {
  return encodeBase64Url(JSON.stringify(sanitizeConfig(config)));
}

/** Devuelve null cuando el segmento no es una configuración válida. */
export function decodeConfig(segment) {
  if (!segment) return null;
  let parsed = null;
  try {
    parsed = JSON.parse(decodeBase64Url(String(segment)));
  } catch {
    // También aceptamos JSON plano (útil para depurar desde la barra de URL).
    try {
      parsed = JSON.parse(decodeURIComponent(String(segment)));
    } catch {
      return null;
    }
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null;
  return sanitizeConfig(parsed);
}

/**
 * Atajos por entorno. `env` son las variables de proceso en Node o los
 * bindings/vars en Cloudflare: el mismo nombre funciona en los dos runtimes.
 */
function envOverrides(env) {
  const source = asEnv(env);
  const overrides = {};
  try {
    const raw = envValue(source, 'OMNI_CONFIG');
    if (raw) Object.assign(overrides, JSON.parse(raw));
  } catch {
    // Configuración por entorno inválida: se ignoran las claves malformadas.
  }
  if (envValue(source, 'OMNI_PREDATABA_KEY')) overrides.predatabaKey = envValue(source, 'OMNI_PREDATABA_KEY');
  if (envValue(source, 'OMNI_CACHE_TTL')) overrides.cacheTtl = envValue(source, 'OMNI_CACHE_TTL');
  if (envValue(source, 'OMNI_MAX_STREAMS')) overrides.maxStreams = envValue(source, 'OMNI_MAX_STREAMS');
  if (envValue(source, 'OMNI_ENABLE_CATALOG')) overrides.enableCatalog = envValue(source, 'OMNI_ENABLE_CATALOG');
  return overrides;
}

/** Configuración del manifiesto raíz. Sin argumento se usa el entorno de Node. */
export function defaultConfig(env) {
  return sanitizeConfig({ ...DEFAULTS, ...envOverrides(env) });
}
