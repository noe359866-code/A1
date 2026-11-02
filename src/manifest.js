export const ADDON_ID = 'org.omniindex.addon';
export const ADDON_VERSION = '1.1.0';
export const ADDON_NAME = 'Omni Index';
export const ADDON_TAGLINE = 'Seis índices, una búsqueda';
export const ADDON_DESCRIPTION = 'Seis índices, una búsqueda: Knaben, torrents-csv, Torlock, PreDB.net, predataba.se y xREL. Cruza torrents con nombres de releases scene/P2P.';
export const CATALOG_NAME = 'Omni Index · Scene / P2P';

/**
 * Stremio espera URLs ABSOLUTAS para `logo` y `background`. Como el addon se
 * instala en hosts distintos (localhost, un proxy o el subdominio de Beamup),
 * las construimos a partir del origen de la petición. `OMNI_PUBLIC_URL` permite
 * forzarlas cuando un proxy no reenvía Host / X-Forwarded-*.
 */
export function brandAssets(origin = '') {
  const clean = String(origin || '').trim().replace(/\/+$/, '');
  if (!/^https?:\/\//i.test(clean)) return {};
  return {
    logo: `${clean}/logo.png`,
    background: `${clean}/background.png`,
  };
}

/**
 * El manifiesto depende de la configuración: los catálogos (y por tanto el
 * recurso `meta`) sólo se anuncian cuando el usuario los activa. Así el addon
 * no contamina la pantalla de inicio por defecto.
 */
export function buildManifest(config, origin = '') {
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
        name: CATALOG_NAME,
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
    name: ADDON_NAME,
    description: ADDON_DESCRIPTION,
    ...brandAssets(origin),
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
