import test from 'node:test';
import assert from 'node:assert/strict';
import {
  parseVideoId, buildQueries, toTsQuery, toPredbPrefix, matchesTitle,
  encodeReleaseId, releaseTitle, normalizeText,
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
