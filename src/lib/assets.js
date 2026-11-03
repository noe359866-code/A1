/** Catálogo de estáticos de marca compartido por ambos runtimes. */

export const ASSET_FILES = Object.freeze([
  'logo.png', 'favicon.png', 'favicon.ico', 'background.png',
]);

export function contentTypeFor(name) {
  return String(name).endsWith('.ico') ? 'image/x-icon' : 'image/png';
}
