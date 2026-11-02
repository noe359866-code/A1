export const ADDON_ID = 'org.arena.omniindex';
export const ADDON_VERSION = '1.0.0';

/**
 * El manifiesto depende de la configuración: los catálogos (y por tanto el
 * recurso `meta`) sólo se anuncian cuando el usuario los activa. Así el addon
 * no contamina la pantalla de inicio por defecto.
 */
export function buildManifest(config) {
  const resources = [
    { name: 'stream', types: ['movie', 'series'], idPrefixes: ['tt', 'release:'] },
  ];
  const catalogs = [];

  if (config.enableCatalog) {
    resources.unshift({ name: 'meta', types: ['movie', 'series'], idPrefixes: ['release:'] });
    resources.unshift({ name: 'catalog', types: ['movie', 'series'] });
    for (const type of ['movie', 'series']) {
      catalogs.push({
        id: 'omni-scene',
        type,
        name: 'Scene / P2P releases',
        extra: [
          { name: 'search', isRequired: false },
          { name: 'skip', isRequired: false },
        ],
      });
    }
  }

  return {
    id: ADDON_ID,
    version: ADDON_VERSION,
    name: 'Omni Index',
    description: 'Busca en Knaben, torrents-csv, Torlock, PreDB.net, predataba.se y xREL. Combina índices de torrents con nombres de releases scene/P2P.',
    resources,
    types: ['movie', 'series'],
    idPrefixes: ['tt', 'release:'],
    catalogs,
    behaviorHints: {
      configurable: true,
      p2p: true,
      adult: false,
    },
  };
}
