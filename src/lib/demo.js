/**
 * Modo demo / offline (OMNI_DEMO=1). Devuelve respuestas sintéticas con la misma
 * forma que las APIs reales para poder probar el addon sin acceso a Internet
 * (útil para la vista previa, desarrollo y demostraciones).
 *
 * No sustituye a los tests: la suite levanta un servidor HTTP local real.
 */

export function isDemo() {
  return process.env.OMNI_DEMO === '1' || process.env.OMNI_DEMO === 'true';
}

const HASH_A = '224bf45881252643dfc2e71abc7b2660a21c68c4';
const HASH_B = '43e3691dc6f4172841e32b25b349e2b7a980b9c5';
const HASH_C = '1c6b2179bcd73efd07fb8018268598c062cf95af';
const TRACKER = 'udp://tracker.opentrackr.org:1337/announce';

function magnet(hash, name) {
  return `magnet:?xt=urn:btih:${hash}&dn=${encodeURIComponent(name)}&tr=${encodeURIComponent(TRACKER)}`;
}

function titlesFor(q) {
  const base = (q || 'Big Buck Bunny').trim().slice(0, 80);
  // Si la consulta ya es un nombre de release completo, no duplicamos las
  // etiquetas de calidad (sucede en la expansión por nombres scene/P2P).
  if (/\b(2160p|1080p|720p|4k|uhd|bluray|x26[45]|h26[45])\b/i.test(base)) {
    return [
      base,
      base.replace(/\b2160p\b|\b4k\b|\buhd\b/gi, '1080p'),
      base.replace(/\b2160p\b|\b4k\b|\buhd\b/gi, '720p'),
    ];
  }
  return [
    `${base} 2160p UHD BluRay x265 HDR10-GROUP`,
    `${base} 1080p WEB-DL DDP5.1 H264-GROUP`,
    `${base} 720p HDTV x264-OTHER`,
  ];
}

function json(body) {
  return { status: 200, text: JSON.stringify(body), headers: new Headers({ 'content-type': 'application/json' }) };
}

function xml(body) {
  return { status: 200, text: body, headers: new Headers({ 'content-type': 'application/xml' }) };
}

function knaben(url) {
  const q = url.searchParams.get('q') || '';
  const hits = titlesFor(q).map((title, i) => ({
    title,
    hash: [HASH_A, HASH_B, HASH_C][i].toUpperCase(),
    magnetUrl: magnet([HASH_A, HASH_B, HASH_C][i], title),
    bytes: [7_132_263_739, 3_082_568_603, 1_152_559_047][i],
    seeders: [230, 90, 12][i],
    liveSeeders: [230, 90, 12][i],
    peers: [40, 20, 3][i],
    title_: undefined,
    tracker: 'The Pirate Bay',
    details: 'https://knaben.xyz/demo',
    date: '2024-01-01T00:00:00+00:00',
    virusDetection: 0.01,
    category: 'Movies / HD',
  }));
  return json({ hits, total: { relation: 'eq', value: hits.length }, max_score: null });
}

function torrentsCsv(url) {
  const q = url.searchParams.get('q') || '';
  const torrents = titlesFor(q).map((name, i) => ({
    infohash: [HASH_A, HASH_B, HASH_C][i],
    name,
    size_bytes: [7_132_263_739, 3_082_568_603, 1_152_559_047][i],
    created_unix: 1_700_000_000,
    seeders: [832, 66, 40][i],
    leechers: [58, 23, 22][i],
    completed: 100,
    scraped_date: 1_790_000_000,
    id: 1000 + i,
  }));
  return json({ torrents, next: 1000 + torrents.length });
}

function torlock(url) {
  const q = url.searchParams.get('q') || '';
  const items = titlesFor(q).slice(0, 2).map((title, i) => {
    const hash = [HASH_B, HASH_C][i];
    return `<item>
      <title>${title.replace(/&/g, '&amp;')}</title>
      <link>https://www.torlock.com/torrent/${1000 + i}/demo.html</link>
      <guid>https://www.torlock.com/torrent/${1000 + i}/demo.html</guid>
      <pubDate>Mon, 15 Apr 2024 18:11:14 +0000</pubDate>
      <size>${[7_132_263_739, 3_082_568_603][i]}</size>
      <category>${url.searchParams.get('cat') || '2000'}</category>
      <enclosure url="${magnet(hash, title).replace(/&/g, '&amp;')}" length="${[7_132_263_739, 3_082_568_603][i]}" type="application/x-bittorrent" />
      <torznab:attr name="seeders" value="${[100, 50][i]}" />
      <torznab:attr name="peers" value="${[120, 60][i]}" />
      <torznab:attr name="infohash" value="${hash}" />
      <torznab:attr name="magneturl" value="${magnet(hash, title).replace(/&/g, '&amp;')}" />
    </item>`;
  }).join('');
  return xml(`<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:torznab="http://torznab.com/schemas/2015/feed"><channel>
<title>Torlock</title><description>Torlock Torznab feed</description>${items}
</channel></rss>`);
}

function predbNet(url) {
  const q = (url.searchParams.get('q') || 'Big.Buck.Bunny').replace(/\s+/g, '.');
  return json({
    status: 'success',
    results: 2,
    time: '0.01s',
    page: 1,
    data: [
      { id: 1, pretime: 1_700_000_000, release: `${q}.2008.1080p.BluRay.x264-DEMO`, section: 'X264-HD', files: 1, size: 4500, status: 0, reason: '', group: 'DEMO', genre: '', url: `/rls/${q}` },
      { id: 2, pretime: 1_690_000_000, release: `${q}.2008.2160p.UHD.BluRay.H265-DEMO`, section: 'X265-HD', files: 1, size: 28000, status: 0, reason: '', group: 'DEMO', genre: '', url: `/rls/${q}` },
    ],
  });
}

function predatabaSe(url, options) {
  let filters = [];
  try {
    filters = JSON.parse(options.body || url.searchParams.get('query') || '[]');
  } catch { /* ignorar */ }
  const q = String(filters[0] || 'top:24h').replace(/^top:\w+$/, 'Big.Buck.Bunny');
  const topOnly = filters.some((f) => String(f).startsWith('top:'));
  return json({
    found: topOnly ? 2 : 20,
    limit: 10,
    offset: 0,
    next_page_id: 'WzEwXQ==',
    pages: 1,
    engine: 'elasticsearch',
    results: [
      { hash: '6f2ce4cd', has_nfo: true, files: 1, size: 4500, traces: 12, id: '1', section: 'X264', rlsname: `${q}.2008.1080p.BluRay.x264-DEMO`, grp: 'DEMO', status: 0, ctime: 1_700_000_000 },
      { hash: '3d5cdf92', has_nfo: true, files: 1, size: 28000, traces: 9, id: '2', section: 'X265', rlsname: `${q}.2008.2160p.UHD.BluRay.H265-DEMO`, grp: 'DEMO', status: 0, ctime: 1_690_000_000 },
    ],
  });
}

function xrel(url) {
  const q = (url.searchParams.get('q') || 'Big Buck Bunny').replace(/\s+/g, '.');
  const release = (name, id) => ({
    id,
    dirname: name,
    link_href: `https://www.xrel.to/movie-nfo/1/${name}.html`,
    time: 1_700_000_000,
    group_name: 'DEMO',
    size: { number: 4500, unit: 'MB' },
    ext_info: { type: 'movie', id: 'd80096c1bbcc', title: q, uris: ['imdb:tt1375666'] },
    comments: 0,
    flags: {},
  });
  return json({
    total: 2,
    results: [release(`${q}.2008.1080p.BluRay.x264-DEMO`, 'aaa')],
    p2p_results: [{
      id: 'bbb',
      dirname: `${q}.2008.2160p.UHD.BluRay.H265-DEMO`,
      link_href: 'https://www.xrel.to/p2p/1/nfo.html',
      category: { meta_cat: 'movie', sub_cat: 'HD-2160p', id: '894aced519' },
      main_lang: 'english',
      pub_time: 1_690_000_000,
      size_mb: 28000,
      group: { id: 'ccc', name: 'DEMO' },
      ext_info: { type: 'movie', id: 'd80096c1bbcc', title: q, uris: ['imdb:tt1375666'] },
      comments: 0,
    }],
  });
}

function cinemeta(url) {
  const id = url.pathname.split('/').pop().replace('.json', '');
  return json({
    meta: {
      id,
      imdb_id: id,
      type: url.pathname.includes('/series/') ? 'series' : 'movie',
      name: 'Big Buck Bunny',
      year: '2008',
      releaseInfo: '2008',
      poster: 'https://images.metahub.space/poster/small/tt1254207/img',
      description: 'Respuesta sintética del modo demo.',
    },
  });
}

/** @returns {{status:number,text:string,headers:Headers}} */
export function demoResponse(rawUrl, options = {}) {
  const url = new URL(rawUrl);
  const host = url.hostname;
  const path = url.pathname;

  if (path.startsWith('/v2/search')) return knaben(url);
  if (path.startsWith('/service/search')) return torrentsCsv(url);
  if (path.startsWith('/torznab/api')) return torlock(url);
  if (host.startsWith('api.predb.net') || host.startsWith('predb.net')) return predbNet(url);
  if (host.startsWith('predataba.se') || host.startsWith('api.predataba.se')) return predatabaSe(url, options);
  if (host.includes('xrel')) return url.pathname.includes('/search/releases') ? xrel(url) : json({ total: 0, results: [] });
  if (path.startsWith('/meta/')) return cinemeta(url);
  return json({});
}
