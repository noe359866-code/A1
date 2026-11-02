/**
 * Limitador sencillo: garantiza un intervalo mínimo entre llamadas a un mismo
 * proveedor, incluso cuando llegan varias peticiones en paralelo. Los límites
 * reales de cada API están documentados en docs/API-RESEARCH.md.
 */
export class RateLimiter {
  constructor(minIntervalMs = 0) {
    this.minIntervalMs = Math.max(0, minIntervalMs);
    this.nextSlot = new Map();
  }

  async acquire(key = 'default') {
    if (this.minIntervalMs === 0) return;
    const now = Date.now();
    const start = Math.max(now, this.nextSlot.get(key) ?? 0);
    this.nextSlot.set(key, start + this.minIntervalMs);
    const wait = start - now;
    if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
  }

  setInterval(ms) {
    this.minIntervalMs = Math.max(0, ms);
  }
}
