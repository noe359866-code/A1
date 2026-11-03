/**
 * Punto de entrada de Cloudflare Workers.
 *
 * No activamos `nodejs_compat`: todo el núcleo (router.js, proveedores y lib/)
 * usa APIs web estándar. Los `vars` y `secrets` de wrangler.toml llegan en
 * `env` y se propagan a cada petición, de modo que las mismas variables
 * OMNI_* documentadas para Beamup funcionan aquí sin cambios.
 */
import { handleRequest } from './router.js';
import { createWorkerAssets } from './assets-worker.js';

export default {
  async fetch(request, env) {
    return handleRequest(request, {
      env,
      assets: createWorkerAssets(env),
      runtime: 'cloudflare',
    });
  },
};
