import http from 'node:http';

/** Infohashes de ejemplo (40 hex) usados en todos los fixtures. */
export const H = Object.freeze({
  inception1080: '224bf45881252643dfc2e71abc7b2660a21c68c4',
  inception4k: '43e3691dc6f4172841e32b25b349e2b7a980b9c5',
  inception265: '1c6b2179bcd73efd07fb8018268598c062cf95af',
  inception720: '2b4fba86244a317b5e607fbff1755cb4d8599281',
  soundtrack: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  cam: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
  torlockExtra: '6bbad06245a0d631b0a5e47c8cd6e5abc9a70211',
});

const TRACKER = 'udp://tracker.opentrackr.org:1337/announce';

export function magnet(hash, name) {
  return `magnet:?xt=urn:btih:${hash}&dn=${encodeURIComponent(name)}&tr=${encodeURIComponent(TRACKER)}`;
}

function movieTitles(q) {
  const isSeries = /breaking|s\d{2}e\d{2}|season/i.test(q || '');
  if (isSeries) {
    return [
      ['Breaking.Bad.S01E01.1080p.WEB-DL.x264-GROUP', H.inception1080, 2_100_000_000, 120],
      ['Breaking.Bad.S02E03.720p.HDTV.x264-OTHER', H.inception720, 1_100_000_000, 25],
      ['Breaking.Bad.S01.COMPLETE.1080p.BluRay-GROUP', H.inception265, 12_000_000_000, 60],
    ];
  }
  return [
    ['Inception (2010) 1080p BluRay x264-YIFY', H.inception1080, 1_986_422_374, 900],
    ['Inception (2010) 2160p UHD BluRay x265-GROUP', H.inception4k, 7_132_263_739, 230],
    ['Inception 2010 1080p BluRay HEVC x265 10bit BONE.mkv', H.inception265, 2_138_804_320, 66],
  ];
}

function knaben(url) {
  const q = url.searchParams.get('q') || '';
  const hits = movieTitles(q).map(([title, hash, bytes, seeders]) => ({
    title,
    hash: hash.toUpperCase(),
    magnetUrl: magnet(hash, title),
    bytes,
    seeders,
    liveSeeders: seeders,
    peers: Math.round(seeders / 4),
    livePeers: Math.round(seeders / 4),
    tracker: 'The Pirate Bay',
    details: 'https://knaben.xyz/detail',
    date: '2024-04-15T17:20:32+00:00',
    virusDetection: 0.01,
    category: 'Movies / HD',
  }));
  return { payload: { hits, total: { relation: 'eq', value: hits.length }, max_score: null } };
}

function torrentsCsv(url) {
  const q = url.searchParams.get('q') || '';
  const after = Number(url.searchParams.get('after') || 0);
  if (q === 'paginate') {
    if (after > 0) return { payload: { torrents: [], next: null } };
    const rows = Array.from({ length: 25 }, (_, index) => ({
      infohash: index.toString(16).padStart(40, '0'),
      name: `Paginate Result ${index}`,
      size_bytes: 1_000_000,
      seeders: 10,
      leechers: 1,
      created_unix: 1_600_000_000,
      id: 900_000 + index,
    }));
    return { payload: { torrents: rows, next: 999_999 } };
  }
  if (after > 0) return { payload: { torrents: [], next: null } };
  const [t1, t2, t3] = movieTitles(q);
  const rows = [
    { infohash: t1[1], name: t1[0], size_bytes: t1[2], seeders: 832, leechers: 58, created_unix: 1_339_547_627, id: 1183 },
    { infohash: H.inception720, name: 'Inception (2010) 720p - mkv - 1.0GB - YIFY', size_bytes: 1_152_559_047, seeders: 40, leechers: 22, created_unix: 1_289_908_776, id: 49785 },
    { infohash: H.soundtrack, name: 'Hans Zimmer - Inception OST (2010) [FLAC].flac', size_bytes: 500_000_000, seeders: 30, leechers: 3, created_unix: 1_288_879_283, id: 204799 },
    { infohash: H.cam, name: 'Inception 2010 HD-CAM TS RIP.mp4', size_bytes: 700_000_000, seeders: 12, leechers: 4, created_unix: 1_300_000_000, id: 204800 },
  ];
  if (/breaking|s\d{2}e\d{2}/i.test(q)) {
    rows[1] = { ...rows[1], name: 'Breaking.Bad.S01E01.720p.HDTV.x264-OTHER' };
    rows[2] = { ...rows[2], name: 'Breaking Bad Theme Song.mp3' };
    rows[3] = { ...rows[3], name: 'Breaking.Bad.S01E01.HD-CAM.mp4' };
  }
  return { payload: { torrents: rows, next: 204800 } };
}

function torlock(url) {
  const q = url.searchParams.get('q') || '';
  const [t1, t2] = movieTitles(q);
  const items = [
    { title: t2[0], hash: t2[1], size: t2[2], seeders: 100, peers: 130 },
    { title: 'Breaking.Bad.S01E01.1080p.BluRay.x264-TORLOCK', hash: H.torlockExtra, size: 3_000_000_000, seeders: 55, peers: 70 },
  ].filter((item) => !/breaking/i.test(item.title) || /breaking|s\d{2}/i.test(q));

  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:torznab="http://torznab.com/schemas/2015/feed">
<channel><title>Torlock</title><description>Torlock Torznab feed</description>
${items.map((item) => `<item>
  <title>${item.title.replace(/&/g, '&amp;')}</title>
  <link>https://www.torlock.com/torrent/1/torrent.html</link>
  <guid>https://www.torlock.com/torrent/1/torrent.html</guid>
  <pubDate>Mon, 15 Apr 2024 18:11:14 +0000</pubDate>
  <size>${item.size}</size>
  <category>${url.searchParams.get('cat') || '2000'}</category>
  <enclosure url="${magnet(item.hash, item.title).replace(/&/g, '&amp;')}" length="${item.size}" type="application/x-bittorrent" />
  <torznab:attr name="seeders" value="${item.seeders}" />
  <torznab:attr name="peers" value="${item.peers}" />
  <torznab:attr name="infohash" value="${item.hash}" />
  <torznab:attr name="magneturl" value="${magnet(item.hash, item.title).replace(/&/g, '&amp;')}" />
</item>`).join('\n')}
</channel></rss>`;
  return { payload: xml, contentType: 'application/rss+xml' };
}

function predbNet(url) {
  const q = url.searchParams.get('q') || 'Inception';
  return {
    payload: {
      status: 'success', results: 3, time: '0.01s', page: 1,
      data: [
        { id: 1, pretime: 1_700_000_000, release: `${q}.2010.1080p.BluRay.x264-GROUP`, section: 'X264-HD', files: 1, size: 4500, status: 0, group: 'GROUP', url: `/rls/${q}` },
        { id: 2, pretime: 1_690_000_000, release: `${q}.2010.2160p.UHD.BluRay.H265-GROUP`, section: 'X265-HD', files: 1, size: 28000, status: 0, group: 'GROUP', url: `/rls/${q}` },
        { id: 3, pretime: 1_680_000_000, release: `${q}.2010.NUKED-GROUP`, section: 'MP3', files: 1, size: 10, status: 1, group: 'GROUP', url: `/rls/${q}` },
      ],
    },
  };
}

function predatabaSe(url, body) {
  let filters = [];
  try { filters = JSON.parse(body || url.searchParams.get('query') || '[]'); } catch { /* noop */ }
  const q = String(filters[0] || 'top:24h');
  const title = q.startsWith('top:') ? 'Inception' : q.replace(/\s+/g, '.');
  return {
    payload: {
      found: 2, limit: 10, offset: 0, next_page_id: 'WzEwXQ==', pages: 1, engine: 'elasticsearch',
      results: [
        { hash: '6f2ce4cd', has_nfo: true, files: 1, size: 4500, traces: 12, id: '101', section: 'X264', rlsname: `${title}.2010.1080p.WEB.h264-ETHEL`, grp: 'ETHEL', status: 0, ctime: 1_700_000_000 },
        { hash: '3d5cdf92', has_nfo: false, files: 0, size: 0, traces: 1, id: '102', section: 'MP3', rlsname: 'Unrelated Music Release 2026-2CB', grp: '2CB', status: 0, ctime: 1_690_000_000 },
      ],
    },
  };
}

function xrel(url) {
  if (url.pathname.endsWith('/p2p/releases.json')) {
    return { payload: { total_count: 1, list: [{ id: 'p1', dirname: 'Some.Movie.2026.1080p.WEB-GROUP', link_href: 'https://www.xrel.to/p2p/1/nfo.html', size_mb: 4500, pub_time: 1_700_000_000, group: { name: 'GROUP' }, category: { meta_cat: 'movie', sub_cat: 'HD-1080p' } }] } };
  }
  const q = (url.searchParams.get('q') || 'Inception').replace(/\s+/g, '.');
  return {
    payload: {
      total: 3,
      results: [
        { id: 'a1', dirname: `${q}.2010.1080p.BluRay.x264-GROUP`, link_href: 'https://www.xrel.to/movie/1/nfo.html', time: 1_700_000_000, group_name: 'GROUP', size: { number: 4500, unit: 'MB' }, ext_info: { type: 'movie', uris: ['imdb:tt1375666'] } },
        { id: 'a2', dirname: 'Another.Movie.2011.720p-WRONG', link_href: 'https://www.xrel.to/movie/2/nfo.html', time: 1_690_000_000, group_name: 'WRONG', ext_info: { type: 'movie', uris: ['imdb:tt9999999'] } },
      ],
      p2p_results: [
        { id: 'p2', dirname: `${q}.1999.2160p.UHD.BluRay.REMUX-FraMeSToR`, link_href: 'https://www.xrel.to/p2p/2/nfo.html', pub_time: 1_538_961_549, size_mb: 52400, group: { name: 'FraMeSToR' }, category: { meta_cat: 'movie', sub_cat: 'HD-2160p' }, ext_info: { type: 'movie', uris: ['imdb:tt1375666'] } },
      ],
    },
  };
}

function cinemeta(url) {
  const id = url.pathname.split('/').pop().replace('.json', '');
  const isSeries = url.pathname.includes('/series/');
  return {
    payload: {
      meta: {
        id,
        imdb_id: id,
        type: isSeries ? 'series' : 'movie',
        name: isSeries ? 'Breaking Bad' : 'Inception',
        year: isSeries ? '2008' : '2010',
        releaseInfo: isSeries ? '2008' : '2010',
      },
    },
  };
}

function route(url, body) {
  const path = url.pathname;
  if (path === '/v2/search') return knaben(url);
  if (path === '/service/search') return torrentsCsv(url);
  if (path === '/torznab/api') return torlock(url);
  if (path === '/' || path === '') return predbNet(url);
  if (path === '/api/search') return predatabaSe(url, body);
  if (path.startsWith('/v2/') && path.includes('releases')) return xrel(url);
  if (path.startsWith('/meta/')) return cinemeta(url);
  return { payload: {} };
}

/** Levanta un servidor HTTP local que simula las seis APIs + Cinemeta. */
export function startMockServer() {
  const requests = [];
  const forcedFailures = new Map();

  const server = http.createServer((req, res) => {
    let body = '';
    req.on('data', (chunk) => { body += chunk; });
    req.on('end', () => {
      const url = new URL(req.url, 'http://localhost');
      requests.push({
        method: req.method,
        path: url.pathname,
        url: req.url,
        query: Object.fromEntries(url.searchParams),
        headers: req.headers,
        body,
      });

      const forced = forcedFailures.get(`${req.method} ${url.pathname}`) ?? forcedFailures.get(url.pathname);
      if (forced) {
        res.writeHead(forced, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'forced failure' }));
        return;
      }

      const { payload, contentType = 'application/json' } = route(url, body);
      const text = typeof payload === 'string' ? payload : JSON.stringify(payload);
      res.writeHead(200, { 'Content-Type': contentType, 'Access-Control-Allow-Origin': '*' });
      res.end(req.method === 'HEAD' ? '' : text);
    });
  });

  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address();
      resolve({
        url: `http://127.0.0.1:${port}`,
        requests,
        forceFailure(path, status = 500, method = null) {
          forcedFailures.set(method ? `${method} ${path}` : path, status);
        },
        clearFailures() { forcedFailures.clear(); },
        close: () => new Promise((done) => server.close(done)),
      });
    });
  });
}

/** Apunta todas las variables de entorno de endpoints al mock. */
export function pointEnvAt(url) {
  for (const name of [
    'OMNI_KNABEN_BASE', 'OMNI_TORRENTS_CSV_BASE', 'OMNI_TORLOCK_BASE',
    'OMNI_PREDB_NET_BASE', 'OMNI_PREDATABA_SE_BASE', 'OMNI_XREL_BASE',
    'OMNI_CINEMETA_BASE',
  ]) {
    process.env[name] = url;
  }
  process.env.OMNI_HTTP_TIMEOUT = '3000';
}
