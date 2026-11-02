/**
 * Proveedor de estáticos para Cloudflare Workers.
 *
 * wrangler.toml declara `[assets]` con `binding = "ASSETS"` y
 * `run_worker_first = true`: todas las peticiones pasan por el Worker (así
 * podemos añadir CORS, inyectar el bootstrap en /configure y aceptar el
 * prefijo /c/<config>/logo.png), y los ficheros se sirven desde la red de
 * Cloudflare con `env.ASSETS.fetch()`.
 */
import { ASSET_FILES, contentTypeFor } from './lib/assets.js';

export function createWorkerAssets(env) {
  const binding = env?.ASSETS;

  async function fetchAsset(name, baseUrl) {
    if (!binding || typeof binding.fetch !== 'function') return null;
    // El binding espera una URL absoluta; usamos el origen de la petición.
    const url = new URL(`/${name}`, baseUrl || 'https://assets.invalid');
    const response = await binding.fetch(new Request(url, { method: 'GET' }));
    return response.ok ? response : null;
  }

  return {
    async html(baseUrl) {
      const response = await fetchAsset('index.html', baseUrl);
      return response ? await response.text() : null;
    },
    async file(name, baseUrl) {
      // Lista blanca: nunca dejamos que una ruta arbitraria llegue al binding.
      if (!ASSET_FILES.includes(name)) return null;
      const response = await fetchAsset(name, baseUrl);
      if (!response) return null;
      return {
        body: await response.arrayBuffer(),
        contentType: contentTypeFor(name),
      };
    },
  };
}
