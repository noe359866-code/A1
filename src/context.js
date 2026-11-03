import { createCache, memo } from './lib/cache.js';
import { RateLimiter } from './lib/limiter.js';
import { asEnv, envValue } from './lib/runtime-env.js';

const sharedCache = createCache({ max: 1500 });

/**
 * Intervalos mínimos por proveedor, derivados de sus límites publicados:
 *  - predb.net: 30 peticiones / 60 s  -> 2100 ms
 *  - xREL: 2 búsquedas / 5 s         -> 2600 ms
 *  - Torlock: no repetir la misma URL en < 60 s (caché) + cortesía
 *  - predataba.se: 5 páginas para usuarios anónimos
 */
const limiters = {
  knaben: new RateLimiter(400),
  torrentsCsv: new RateLimiter(350),
  torlock: new RateLimiter(1200),
  predbNet: new RateLimiter(2100),
  predatabaSe: new RateLimiter(1200),
  xrel: new RateLimiter(2600),
  cinemeta: new RateLimiter(200),
};

const failures = [];

/**
 * Contexto de una petición. `overrides.env` es el entorno del runtime: si no
 * se pasa (tests que llaman a los proveedores directamente) se usa el de Node.
 * En Cloudflare la caché y los limiters viven en el *isolate*, igual que la
 * caché en memoria del servidor Node: se comparten entre peticiones del mismo
 * proceso pero no son durables entre reinicios.
 */
export function createContext(config, overrides = {}) {
  const env = overrides.env ?? asEnv();
  return {
    config,
    env,
    cache: sharedCache,
    limiters,
    failures,
    cached: (key, ttlMs, producer) => memo(sharedCache, key, ttlMs, producer),
    log: (...args) => {
      if (envValue(env, 'OMNI_QUIET') !== '1') console.error('[omni]', ...args);
    },
    noteFailure(provider, error) {
      failures.push({ provider, message: String(error?.message || error), at: Date.now() });
      while (failures.length > 50) failures.shift();
      this.log(`${provider}: ${error?.message || error}`);
    },
    ...overrides,
  };
}

export function cacheSnapshot() {
  return sharedCache.snapshot();
}

/** Utilidades para tests: desactivan el espaciado y limpian el estado global. */
export function setLimiterIntervals(ms) {
  for (const limiter of Object.values(limiters)) limiter.setInterval(ms);
}

export function resetCache() {
  sharedCache.clear();
  failures.length = 0;
}
