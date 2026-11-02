/**
 * Parser mínimo para feeds RSS Torznab/Newznab (lo que devuelve Torlock).
 * No usa dependencias externas ni un parser XML completo: extrae los elementos
 * que el estándar define para un <item>, incluyendo los atributos
 * <torznab:attr name="..." value="..."/> y el magnet del enclosure.
 *
 * Se aceptan las tres variantes habituales: <torznab:attr>, <newznab:attr>
 * y <attr>. Los valores se decodifican (entidades y CDATA).
 */

const NAMED_ENTITIES = {
  amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ',
};

export function decodeXml(input = '') {
  return String(input)
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, (_, inner) => inner)
    .replace(/&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);/g, (match, entity) => {
      if (entity[0] === '#') {
        const code = entity[1] === 'x' || entity[1] === 'X'
          ? parseInt(entity.slice(2), 16)
          : parseInt(entity.slice(1), 10);
        return Number.isFinite(code) && code > 0 && code <= 0x10ffff ? String.fromCodePoint(code) : match;
      }
      return NAMED_ENTITIES[entity.toLowerCase()] ?? match;
    });
}

function tagText(block, tag) {
  const pattern = new RegExp(`<${tag}\\b[^>]*>([\\s\\S]*?)</${tag}>`, 'i');
  const match = block.match(pattern);
  return match ? decodeXml(match[1]).trim() : '';
}

function parseAttributes(raw = '') {
  const result = {};
  const pattern = /([:A-Za-z_][-:\w.]*)\s*=\s*("([^"]*)"|'([^']*)')/g;
  let match;
  while ((match = pattern.exec(raw)) !== null) {
    result[match[1].toLowerCase()] = decodeXml(match[3] ?? match[4] ?? '');
  }
  return result;
}

function firstTagAttributes(block, tag) {
  const pattern = new RegExp(`<${tag}\\b([^>]*)\\/?>`, 'i');
  const match = block.match(pattern);
  return match ? parseAttributes(match[1]) : {};
}

function toInt(value) {
  if (value === undefined || value === null || value === '') return undefined;
  const parsed = Number.parseInt(String(value).replace(/[^\d-]/g, ''), 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function findMagnet(...values) {
  for (const value of values) {
    if (!value) continue;
    const match = String(value).match(/magnet:\?[^\s"'<>]+/i);
    if (match) return decodeXml(match[0]);
  }
  return '';
}

/**
 * @param {string} xml
 * @returns {{title:string,link:string,guid:string,pubDate:string,description:string,
 *   category:string,magnet:string,infoHashAttr:string,seeders?:number,
 *   leechers?:number,peers?:number,sizeBytes?:number,attrs:Record<string,string>}[]}
 */
export function parseTorznab(xml) {
  const source = String(xml || '');
  const items = source.match(/<item\b[^>]*>[\s\S]*?<\/item>/gi) || [];
  return items.map((raw) => {
    const block = decodeXml(raw);
    const attrs = {};
    const attrPattern = /<(?:[A-Za-z0-9_-]+:)?attr\b([^>]*)\/?>/gi;
    let attrMatch;
    while ((attrMatch = attrPattern.exec(block)) !== null) {
      const parsed = parseAttributes(attrMatch[1]);
      if (parsed.name) attrs[parsed.name.toLowerCase()] = parsed.value ?? '';
    }

    const enclosure = firstTagAttributes(block, 'enclosure');
    const link = tagText(block, 'link') || attrs.link || '';
    const guid = tagText(block, 'guid') || '';
    const comments = tagText(block, 'comments');
    const description = tagText(block, 'description');
    const magnet = findMagnet(
      attrs.magneturl, attrs.magnet, enclosure.url, link, guid, comments, description, block,
    );

    const seeders = toInt(attrs.seeders ?? attrs.seeds);
    const peers = toInt(attrs.peers);
    const leechers = toInt(attrs.leechers) ?? (peers !== undefined && seeders !== undefined
      ? Math.max(0, peers - seeders)
      : undefined);
    const sizeBytes = toInt(attrs.size) ?? toInt(enclosure.length) ?? toInt(tagText(block, 'size'));

    return {
      title: tagText(block, 'title'),
      link,
      guid,
      pubDate: tagText(block, 'pubDate') || tagText(block, 'date') || '',
      description,
      category: tagText(block, 'category') || attrs.category || '',
      magnet,
      infoHashAttr: attrs.infohash || '',
      seeders,
      leechers,
      peers,
      sizeBytes,
      attrs,
    };
  });
}
