/**
 * Acceso a la configuración del entorno sin atarse a Node.
 *
 * En local / Docker / Beamup el entorno son las variables de proceso; en
 * Cloudflare Workers son los *bindings* que llegan como segundo argumento de
 * `fetch(request, env, ctx)`. Todos los módulos compartidos reciben el objeto
 * `env` de la petición en curso (vía `ctx.env`), así que aquí sólo se aporta
 * el valor por defecto que necesitan Node y los tests.
 *
 * No uses `process` directamente en los módulos del núcleo: el bundle del
 * Worker debe poder construirse sin `nodejs_compat`.
 */

export function nodeEnv() {
  // Con `globalThis` no aparece un identificador `process` suelto en el
  // bundle: en workerd simplemente no existe y devolvemos un objeto vacío.
  return globalThis.process?.env ?? {};
}

/** Normaliza `env` a un objeto (nunca `undefined`). */
export function asEnv(env) {
  return env && typeof env === 'object' ? env : nodeEnv();
}

/** Lee una variable/binding como string, o '' si no existe. */
export function envValue(env, name) {
  const value = asEnv(env)?.[name];
  return value === undefined || value === null ? '' : String(value);
}
