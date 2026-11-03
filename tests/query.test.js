import test from 'node:test';
import assert from 'node:assert/strict';
import {
  parseVideoId, buildQueries, toTsQuery, toPredbPrefix, matchesTitle,
  encodeReleaseId, releaseTitle, normalizeText, alternateTitles,
} from '../src/lib/query.js';

test('parseVideoId: película IMDb', () => {
  const parsed = parseVideoId('movie', 'tt1375666');
  assert.equal(parsed.imdbId, 'tt1375666');
  assert.equal(parsed.type, 'movie');
  assert.equal(parsed.season, null);
});

test('parseVideoId: serie con temporada y episodio', () => {
  const parsed = parseVideoId('series', 'tt0903747:1:2');
  assert.equal(parsed.imdbId, 'tt0903747');
  assert.equal(parsed.season, 1);
  assert.equal(parsed.episode, 2);
});

test('parseVideoId: rechaza ids no IMDb', () => {
  assert.equal(parseVideoId('movie', 'tmdb:123'), null);
  assert.equal(parseVideoId('movie', ''), null);
});

test('ids release: codifican y decodifican nombres con acentos', () => {
  const name = 'El.Secreto.de.Sus.Ojos.2009.1080p.BluRay-GROUP';
  const id = encodeReleaseId(name);
  assert.ok(id.startsWith('release:'));
  assert.equal(parseVideoId('movie', id).releaseName, name);
  assert.equal(releaseTitle(name), 'El Secreto de Sus Ojos');
});

test('buildQueries: película incluye año y respaldo sin año', () => {
  const parsed = parseVideoId('movie', 'tt1375666');
  const queries = buildQueries(parsed, { name: 'Inception', year: '2010' });
  assert.deepEqual(queries.map((q) => q.text), ['Inception 2010', 'Inception']);
  assert.equal(queries[0].year, '2010');
  assert.equal(queries[0].imdbId, 'tt1375666');
});

test('buildQueries: serie genera SxxExx, temporada y título', () => {
  const parsed = parseVideoId('series', 'tt0903747:1:2');
  const queries = buildQueries(parsed, { name: 'Breaking Bad', year: '2008' });
  assert.deepEqual(queries.map((q) => q.text), [
    'Breaking Bad S01E02', 'Breaking Bad S01', 'Breaking Bad',
  ]);
  assert.equal(queries[0].season, 1);
  assert.equal(queries[0].episode, 2);
});

test('buildQueries: release exacto usa título corto para el ranking', () => {
  const name = 'The.Matrix.1999.2160p.UHD.BluRay.REMUX-GROUP';
  const queries = buildQueries(parseVideoId('movie', encodeReleaseId(name)), { name, year: '1999' });
  assert.equal(queries.length, 1);
  assert.equal(queries[0].mode, 'exact');
  assert.equal(queries[0].text, name);
  assert.equal(queries[0].title, 'The Matrix');
});

test('toTsQuery sanea el texto para Postgres to_tsquery', () => {
  assert.equal(toTsQuery('Inception 2010! (x) | &'), 'inception 2010');
  assert.equal(toTsQuery('The.Matrix.Reloaded'), 'the matrix reloaded');
  assert.equal(toTsQuery('a'), '');
  assert.equal(toTsQuery(''), '');
});

test('toPredbPrefix genera prefijos con puntos para búsqueda por inicio', () => {
  assert.equal(toPredbPrefix('The Matrix: Reloaded'), 'The.Matrix.Reloaded');
  assert.equal(toPredbPrefix('  Inception  '), 'Inception');
});

test('matchesTitle exige una mayoría de tokens y normaliza acentos', () => {
  assert.equal(matchesTitle('Inception.2010.1080p.BluRay.x264-GROUP', 'Inception'), true);
  assert.equal(matchesTitle('El.Secreto.de.Sus.Ojos.2009.1080p-GROUP', 'El Secreto de Sus Ojos'), true);
  assert.equal(matchesTitle('Something.Else.2020.720p-OTHER', 'Inception'), false);
  assert.equal(normalizeText('À bientôt'), 'a bientot');
});

test('parseVideoId: ids de anime de los catálogos de Stremio', () => {
  const anime = parseVideoId('anime', 'kitsu:46474:1:3');
  assert.equal(anime.animeSource, 'kitsu');
  assert.equal(anime.animeId, 46474);
  assert.equal(anime.imdbId, null);
  assert.equal(anime.season, 1);
  assert.equal(anime.episode, 3);
  assert.equal(anime.type, 'series', 'sin metadatos se asume serie');

  for (const prefix of ['kitsu', 'mal', 'anilist', 'anidb', 'anime']) {
    const parsed = parseVideoId('series', `${prefix}:16498`);
    assert.equal(parsed.animeSource, prefix);
    assert.equal(parsed.animeId, 16498);
  }

  // `kitsu:46474:7` -> episodio suelto; la temporada se resuelve con los videos.
  const onlyEpisode = parseVideoId('series', 'kitsu:46474:7');
  assert.equal(onlyEpisode.season, null);
  assert.equal(onlyEpisode.episode, 7);

  assert.equal(parseVideoId('series', 'kitsu:abc:1'), null, 'id no numérico');
  assert.equal(parseVideoId('series', 'kitsu::1'), null, 'id vacío');
});

test('parseVideoId: `anime` con id IMDb sigue funcionando', () => {
  const parsed = parseVideoId('anime', 'tt22248376:1:2');
  assert.equal(parsed.imdbId, 'tt22248376');
  assert.equal(parsed.season, 1);
  assert.equal(parsed.episode, 2);
});

test('buildQueries: anime usa temporada/episodio, absoluto y respaldo', () => {
  const meta = { name: 'Sousou no Frieren', year: '2023' };
  const parsed = parseVideoId('anime', 'kitsu:46474:1:1');
  const queries = buildQueries({ ...parsed, season: 1, episode: 1 }, meta);
  const texts = queries.map((query) => query.text);
  assert.equal(texts[0], 'Sousou no Frieren S01E01');
  assert.ok(texts.includes('Sousou no Frieren 01'), 'numeración absoluta');
  assert.ok(texts.includes('Sousou no Frieren 2023'), 'respaldo por título y año');
  assert.ok(texts.every((text, index) => texts.indexOf(text) === index), 'sin duplicados');

  const longRunning = buildQueries(
    { ...parseVideoId('series', 'kitsu:12:1071'), season: 1, episode: 1071 },
    { name: 'One Piece', year: '1999' },
  );
  assert.ok(longRunning.some((query) => query.text === 'One Piece 1071'));
});

test('alternateTitles: alias sin repetir el título principal', () => {
  const titles = alternateTitles({
    name: 'Sousou no Frieren',
    titles: ['Sousou no Frieren', 'Frieren: Beyond Journey’s End', 'Frieren at the Funeral', 'X'],
  });
  assert.deepEqual(titles, ['Frieren: Beyond Journey’s End', 'Frieren at the Funeral']);
});
