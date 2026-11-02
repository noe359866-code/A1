import test from 'node:test';
import assert from 'node:assert/strict';
import {
  base32ToHex, normalizeInfoHash, parseMagnet, normalizeTorrent, mergeTorrents,
  qualityOf, parseSeasonEpisode, rankTorrents, streamSources, humanSize,
} from '../src/lib/torrent.js';
import { sanitizeConfig, DEFAULTS } from '../src/config.js';

const HEX = '224bf45881252643dfc2e71abc7b2660a21c68c4';

test('normalizeInfoHash acepta hex, mayúsculas, URN, magnet y base32', () => {
  assert.equal(normalizeInfoHash(HEX.toUpperCase()), HEX);
  assert.equal(normalizeInfoHash(`urn:btih:${HEX}`), HEX);
  assert.equal(normalizeInfoHash(`magnet:?xt=urn:btih:${HEX}&dn=x`), HEX);
  assert.equal(normalizeInfoHash('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'), '0'.repeat(40));
  assert.equal(base32ToHex('ABCDEFGHIJKLMNOPQRSTUVWXYZ234567').length, 40);
  assert.equal(normalizeInfoHash('no-es-un-hash'), null);
  assert.equal(normalizeInfoHash(null), null);
});

test('parseMagnet extrae infohash, nombre y trackers', () => {
  const magnet = `magnet:?xt=urn:btih:${HEX}&dn=${encodeURIComponent('Inception 2010')}&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Ftracker.qu.ax%3A6969%2Fannounce`;
  const parsed = parseMagnet(magnet);
  assert.equal(parsed.infoHash, HEX);
  assert.equal(parsed.displayName, 'Inception 2010');
  assert.deepEqual(parsed.trackers, [
    'udp://open.stealth.si:80/announce', 'udp://tracker.qu.ax:6969/announce',
  ]);
  assert.equal(parseMagnet('https://example.com'), null);
});

test('normalizeTorrent descarta entradas sin infohash o sin nombre', () => {
  assert.equal(normalizeTorrent({ name: 'x' }), null);
  assert.equal(normalizeTorrent({ infoHash: HEX }), null);
  const torrent = normalizeTorrent({ name: 'Inception 2010 1080p', magnet: `magnet:?xt=urn:btih:${HEX}` });
  assert.equal(torrent.infoHash, HEX);
  assert.equal(torrent.seeders, null);
});

test('mergeTorrents unifica por infohash conservando el mejor dato', () => {
  const merged = mergeTorrents([
    { provider: 'knaben', name: 'Inception 2010 1080p', infoHash: HEX, seeders: 10, trackers: ['udp://a:1/announce'] },
    { provider: 'torlock', name: 'Inception.2010.1080p.BluRay.x264-GROUP', infoHash: HEX.toUpperCase(), seeders: 42, magnet: `magnet:?xt=urn:btih:${HEX}&tr=udp%3A%2F%2Fb%3A2%2Fannounce` },
    { provider: 'torrentsCsv', name: 'Inception 2010 720p', infoHash: '43e3691dc6f4172841e32b25b349e2b7a980b9c5', seeders: 5 },
  ]);
  assert.equal(merged.length, 2);
  const shared = merged.find((item) => item.infoHash === HEX);
  assert.equal(shared.seeders, 42);
  assert.deepEqual(shared.providers.sort(), ['knaben', 'torlock']);
  assert.equal(shared.trackers.length, 2);
  assert.ok(shared.name.includes('BluRay'));
  assert.ok(shared.magnet.startsWith('magnet:'));
});

test('qualityOf y parseSeasonEpisode reconocen formatos habituales', () => {
  assert.equal(qualityOf('Movie.2160p.UHD.BluRay.x265').label, '4K');
  assert.equal(qualityOf('Movie.1080p.WEB-DL').label, '1080p');
  assert.equal(qualityOf('Movie.720p.HDTV').label, '720p');
  assert.ok(qualityOf('Movie.HD-CAM.TS').score < 0);
  assert.deepEqual(parseSeasonEpisode('Show.S01E02.1080p'), { season: 1, episode: 2, isPack: false });
  assert.deepEqual(parseSeasonEpisode('Show.1x02.720p'), { season: 1, episode: 2, isPack: false });
  assert.deepEqual(parseSeasonEpisode('Show.Season.3.COMPLETE'), { season: 3, episode: null, isPack: true });
  assert.deepEqual(parseSeasonEpisode('Movie.2010.1080p'), { season: null, episode: null, isPack: false });
});

test('rankTorrents filtra no-vídeo/CAM/seeders bajos y respeta maxStreams', () => {
  const config = sanitizeConfig({ ...DEFAULTS, maxStreams: 3, minSeeders: 1 });
  const query = { title: 'Inception', text: 'Inception 2010', year: '2010', type: 'movie', season: null, episode: null, mode: 'title' };
  const input = [
    { infoHash: HEX, name: 'Inception 2010 1080p BluRay x264', seeders: 100, providers: ['knaben'] },
    { infoHash: '43e3691dc6f4172841e32b25b349e2b7a980b9c5', name: 'Inception 2010 2160p UHD BluRay', seeders: 50, providers: ['torlock'] },
    { infoHash: '1c6b2179bcd73efd07fb8018268598c062cf95af', name: 'Inception 2010 720p HDTV', seeders: 20, providers: ['torrentsCsv'] },
    { infoHash: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', name: 'Inception OST 2010.flac', seeders: 90, providers: ['torrentsCsv'] },
    { infoHash: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', name: 'Inception 2010 HD-CAM.mp4', seeders: 80, providers: ['knaben'] },
    { infoHash: 'cccccccccccccccccccccccccccccccccccccccc', name: 'Inception 2010 480p', seeders: 0, providers: ['knaben'] },
    { infoHash: 'dddddddddddddddddddddddddddddddddddddddd', name: 'Otra película 2010 1080p', seeders: 500, providers: ['knaben'] },
  ].map(normalizeTorrent);

  const ranked = rankTorrents(input, { query, config });
  assert.equal(ranked.length, 3);
  assert.equal(ranked[0].name.includes('2160p'), true); // 4K por delante de 1080p
  assert.ok(ranked.every((item) => !item.name.includes('.flac')));
  assert.ok(ranked.every((item) => !item.name.includes('HD-CAM')));
  assert.ok(ranked.every((item) => item.name.includes('Inception')));
  assert.ok(ranked.every((item) => item.quality.label));
});

test('rankTorrents penaliza episodios equivocados en series', () => {
  const config = sanitizeConfig({ ...DEFAULTS, maxStreams: 10 });
  const query = { title: 'Breaking Bad', text: 'Breaking Bad S01E01', year: '2008', type: 'series', season: 1, episode: 1, mode: 'title' };
  const input = [
    { infoHash: HEX, name: 'Breaking.Bad.S01E01.1080p.WEB-DL', seeders: 100, providers: ['knaben'] },
    { infoHash: '43e3691dc6f4172841e32b25b349e2b7a980b9c5', name: 'Breaking.Bad.S02E03.2160p.UHD', seeders: 900, providers: ['torlock'] },
  ].map(normalizeTorrent);
  const ranked = rankTorrents(input, { query, config });
  assert.equal(ranked[0].name, 'Breaking.Bad.S01E01.1080p.WEB-DL');
});

test('streamSources y humanSize', () => {
  const sources = streamSources({ trackers: ['udp://custom:1/announce'] });
  assert.equal(sources[0], 'tracker:udp://custom:1/announce');
  assert.ok(sources.every((source) => source.startsWith('tracker:')));
  assert.ok(sources.length <= 14);
  assert.equal(humanSize(0), '?');
  assert.match(humanSize(1_073_741_824), /1(\.0)? GB/);
});
