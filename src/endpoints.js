/**
 * Bases URL de cada proveedor. Se pueden sobreescribir por variable de entorno
 * (OMNI_<PROVIDER>_BASE) para apuntar a un servidor propio, a un proxy o al
 * mock server de los tests. En Cloudflare los mismos nombres se definen como
 * `vars` / `secrets` y llegan en el objeto `env` de la petición.
 */
import { asEnv, envValue } from './lib/runtime-env.js';

export const DEFAULT_ENDPOINTS = Object.freeze({
  knaben: 'https://api.knaben.org',
  torrentsCsv: 'https://torrents-csv.com',
  torlock: 'https://www.torlock.com',
  predbNet: 'https://api.predb.net',
  predatabaSe: 'https://predataba.se',
  xrel: 'https://api.xrel.to',
  cinemeta: 'https://v3-cinemeta.strem.io',
  animeKitsu: 'https://anime-kitsu.strem.fun',
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
  animeKitsu: 'OMNI_ANIME_KITSU_BASE',
};

/** @param {object} env variables (Node) o bindings (Cloudflare Workers) */
export function endpoint(name, config = {}, env) {
  const override = envValue(asEnv(env), ENV_NAMES[name]);
  if (name === 'xrel' && config.xrelAlternate && !override) return XREL_ALTERNATE;
  const value = override || DEFAULT_ENDPOINTS[name];
  if (!value) throw new Error(`Endpoint desconocido: ${name}`);
  return value.replace(/\/+$/, '');
}
