import * as knaben from './knaben.js';
import * as torrentsCsv from './torrents-csv.js';
import * as torlock from './torlock.js';
import * as predbNet from './predb-net.js';
import * as predatabaSe from './predataba-se.js';
import * as xrel from './xrel.js';

export const ALL = Object.freeze([knaben, torrentsCsv, torlock, predbNet, predatabaSe, xrel]);
export const TORRENT = Object.freeze(ALL.filter((provider) => provider.kind === 'torrent'));
export const RELEASE = Object.freeze(ALL.filter((provider) => provider.kind === 'release'));

export function byId(id) {
  return ALL.find((provider) => provider.id === id);
}

export function enabledTorrentProviders(config) {
  return TORRENT.filter((provider) => config.providers[provider.id]);
}

export function enabledReleaseProviders(config) {
  return RELEASE.filter((provider) => config.providers[provider.id]);
}
