import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeXml, parseTorznab } from '../src/lib/xml.js';

const FEED = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:torznab="http://torznab.com/schemas/2015/feed">
<channel><title>Torlock</title><description>Torlock Torznab feed</description>
<item>
  <title><![CDATA[Inception 2010 &amp; friends 1080p BluRay x264-GROUP]]></title>
  <link>https://www.torlock.com/torrent/1/torrent.html</link>
  <guid>https://www.torlock.com/torrent/1/torrent.html</guid>
  <pubDate>Mon, 15 Apr 2024 18:11:14 +0000</pubDate>
  <size>3082568603</size>
  <category>2000</category>
  <enclosure url="magnet:?xt=urn:btih:1c6b2179bcd73efd07fb8018268598c062cf95af&amp;dn=Inception&amp;tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce" length="3082568603" type="application/x-bittorrent" />
  <torznab:attr name="seeders" value="100" />
  <torznab:attr name="peers" value="130" />
  <torznab:attr name="infohash" value="1c6b2179bcd73efd07fb8018268598c062cf95af" />
</item>
<item>
  <title>Breaking.Bad.S01E01.720p.HDTV</title>
  <guid>magnet:?xt=urn:btih:6bbad06245a0d631b0a5e47c8cd6e5abc9a70211&amp;dn=Breaking.Bad</guid>
  <attr name="seeders" value="12" />
  <attr name="leechers" value="4" />
  <newznab:attr name="size" value="1100000000" />
</item>
</channel></rss>`;

test('decodeXml maneja entidades, CDATA y entidades numéricas', () => {
  assert.equal(decodeXml('a &amp; b &lt;c&gt; &#65; &#x42;'), 'a & b <c> A B');
  assert.equal(decodeXml('<![CDATA[sin & escapar]]>'), 'sin & escapar');
});

test('parseTorznab extrae items, attrs namespaced y magnet del enclosure', () => {
  const items = parseTorznab(FEED);
  assert.equal(items.length, 2);
  const [first, second] = items;

  assert.equal(first.title, 'Inception 2010 & friends 1080p BluRay x264-GROUP');
  assert.equal(first.seeders, 100);
  assert.equal(first.leechers, 30); // peers - seeders
  assert.equal(first.sizeBytes, 3082568603);
  assert.equal(first.category, '2000');
  assert.equal(first.infoHashAttr, '1c6b2179bcd73efd07fb8018268598c062cf95af');
  assert.match(first.magnet, /^magnet:\?xt=urn:btih:/);
  assert.ok(first.magnet.includes('tr=udp'));

  assert.equal(second.title, 'Breaking.Bad.S01E01.720p.HDTV');
  assert.equal(second.seeders, 12);
  assert.equal(second.leechers, 4);
  assert.equal(second.sizeBytes, 1_100_000_000);
  assert.match(second.magnet, /6bbad06245a0d631b0a5e47c8cd6e5abc9a70211/);
});

test('parseTorznab tolera feeds vacíos y errores del proveedor', () => {
  assert.deepEqual(parseTorznab(''), []);
  assert.deepEqual(parseTorznab('<error code="100">bad key</error>'), []);
  assert.deepEqual(parseTorznab('<rss><channel></channel></rss>'), []);
});
