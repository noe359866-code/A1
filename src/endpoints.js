/**
 * Bases URL de cada proveedor. Se pueden sobreescribir por variable de entorno
 * (OMNI_<PROVIDER>_BASE) para apuntar a un servidor propio, a un proxy o al
 * mock server de los tests. Imprescindible para no hardcodear hosts.
 */
export const DEFAULT_ENDPOINTS = Object.freeze({
  knaben: 'https://api.knaben.org',
  torrentsCsv: 'https://torrents-csv.com',
  torlock: 'https://www.torlock.com',
  predbNet: 'https://api.predb.net',
  predatabaSe: 'https://predataba.se',
  xrel: 'https://api.xrel.to',
  cinemeta: 'https://v3-cinemeta.strem.io',
});

export const XREL_ALTERNATE = 'https://api-alternate.xrel.top';

const ENV_NAMES = {
  knaben: 'OMNI_KNABEN_BASE',
  torrentsCsv: 'OMNI_TORRENTS_CSV_BASE',
  torlock: 'OMNI_TORLOCK_BASE',
  predbNet: 'OMNI_PREDB_NET_BASE',
  predatabaSe: 'OMNI_PREDATABA_SE_BASE',
  xrel: 'OMNI_XREL_BASE',
  cinemeta: 'OMNI_CINEMETA_BASE',
};

export function endpoint(name, config = {}) {
  if (name === 'xrel' && config.xrelAlternate && !process.env[ENV_NAMES.xrel]) {
    return XREL_ALTERNATE;
  }
  const override = process.env[ENV_NAMES[name]];
  const value = override || DEFAULT_ENDPOINTS[name];
  if (!value) throw new Error(`Endpoint desconocido: ${name}`);
  return value.replace(/\/+$/, '');
}
