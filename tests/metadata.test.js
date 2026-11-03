/**
 * Resolución de metadatos de anime y traducción de numeración de episodios.
 * El addon recibe de Anime Kitsu / AniList / MAL ids con numeración absoluta
 * (`kitsu:12:1071`) o sin temporada (`kitsu:46474:7`); aquí se comprueba que se
 * convierten a SxxExx antes de buscar en los índices.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveEpisode } from '../src/metadata.js';

const videos = [
  { season: 1, episode: 1, absoluteEpisode: 1 },
  { season: 1, episode: 12, absoluteEpisode: 12 },
  { season: 2, episode: 1, absoluteEpisode: 13 },
  { season: 2, episode: 5, absoluteEpisode: 17 },
];

test('resolveEpisode: temporada + episodio exactos se respetan', () => {
  assert.deepEqual(resolveEpisode(videos, 2, 5), { season: 2, episode: 5 });
});

test('resolveEpisode: episodio absoluto -> temporada/episodio', () => {
  assert.deepEqual(resolveEpisode(videos, null, 17), { season: 2, episode: 5 });
  assert.deepEqual(resolveEpisode(videos, 1, 13), { season: 2, episode: 1 });
});

test('resolveEpisode: sin tabla de episodios no rompe', () => {
  assert.deepEqual(resolveEpisode([], 3, 4), { season: 3, episode: 4 });
  assert.deepEqual(resolveEpisode(null, null, 4), { season: null, episode: 4 });
});
