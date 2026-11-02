/**
 * Proveedor de estáticos para el runtime de Node (local, Docker y Beamup).
 * Los ficheros se leen una sola vez al crear el servidor, igual que antes.
 * No forma parte del grafo de imports del Worker: assets-worker.js lo sustituye.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { ASSET_FILES, contentTypeFor } from './lib/assets.js';

const here = path.dirname(fileURLToPath(import.meta.url));

export function createNodeAssets(dir = path.join(here, 'public')) {
  const indexHtml = fs.readFileSync(path.join(dir, 'index.html'), 'utf8');
  const files = Object.fromEntries(ASSET_FILES.map((name) => [name, {
    body: new Uint8Array(fs.readFileSync(path.join(dir, name))),
    contentType: contentTypeFor(name),
  }]));

  return {
    async html() {
      return indexHtml;
    },
    async file(name) {
      return files[name] || null;
    },
  };
}
