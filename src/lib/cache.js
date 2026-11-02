/**
 * Pequeña caché en memoria con TTL, límite de entradas y deduplicación de
 * peticiones concurrentes. Además permite servir un valor caducado si el
 * proveedor falla (stale-if-error), algo importante porque varias de las APIs
 * consultadas tienen límites de uso estrictos.
 */

export function createCache({ max = 1000 } = {}) {
  const store = new Map();
  const inflight = new Map();
  const stats = { hits: 0, misses: 0, stale: 0, errors: 0 };

  function get(key) {
    const entry = store.get(key);
    if (!entry) return undefined;
    // LRU: refrescar posición
    store.delete(key);
    store.set(key, entry);
    if (entry.expires > Date.now()) {
      stats.hits++;
      return { value: entry.value, fresh: true };
    }
    return { value: entry.value, fresh: false };
  }

  function set(key, value, ttlMs = 60_000) {
    store.set(key, { value, expires: Date.now() + Math.max(1, ttlMs) });
    while (store.size > max) store.delete(store.keys().next().value);
  }

  function clear() {
    store.clear();
    inflight.clear();
  }

  function size() {
    return store.size;
  }

  function snapshot() {
    return { entries: store.size, inflight: inflight.size, ...stats };
  }

  return { get, set, clear, size, snapshot, inflight, stats };
}

/**
 * Ejecuta `producer` como máximo una vez por clave dentro de la ventana TTL.
 * Si varias peticiones llegan a la vez comparten la misma promesa.
 * `cache` debe ser el objeto devuelto por createCache.
 */
export async function memo(cache, key, ttlMs, producer) {
  const hit = cache.get(key);
  if (hit?.fresh) return hit.value;
  if (cache.inflight?.has(key)) return cache.inflight.get(key);

  const pending = (async () => {
    try {
      const value = await producer();
      cache.set(key, value, ttlMs);
      cache.stats.misses++;
      return value;
    } catch (error) {
      if (hit) {
        cache.stats.stale++;
        return hit.value;
      }
      cache.stats.errors++;
      throw error;
    } finally {
      cache.inflight?.delete(key);
    }
  })();

  cache.inflight?.set(key, pending);
  return pending;
}
