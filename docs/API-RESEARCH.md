# Investigación de las APIs

Notas tomadas el **2026-11-02** leyendo la documentación oficial **y comprobando respuestas reales** de cada servicio. Este documento es la referencia que justifica las decisiones del código (`src/providers/*`).

Índice: [Knaben](#1-knaben-api-v2) · [torrents-csv](#2-torrents-csv) · [Torlock](#3-torlock-torznab) · [PreDB.net](#4-predbnet) · [predataba.se](#5-predatabase--predatabasese) · [xREL](#6-xrel-v2) · [Cinemeta / Stremio](#7-stremio-y-cinemeta) · [Límites](#8-límites-y-cómo-se-respetan)

---

## 1. Knaben API v2

- Docs: <https://knaben.org/api/v2/> (v1: <https://knaben.org/api/v1/>)
- Base: `https://api.knaben.org`; endpoint `GET /v2/search` (browse: `/v2/browse`).
- v2 usa **GET** en lugar del POST de v1 "para descargar trabajo al edge server".

| Parámetro | Uso en el addon | Notas |
| --- | --- | --- |
| `q` | texto libre (`Inception 2010`, `Breaking Bad S01E02`) | obligatorio |
| `sf` | `title` | por defecto `title`; admite `description`, `hash`… |
| `o`, `d` | `seeders`, `desc` | orden |
| `s` | `perProviderLimit` | por defecto 150, **máximo 300** |
| `f` | — | paginación (`size * page`) |
| `c`, `t` | — | categorías / trackers (lista separada por comas) |
| `xxx`, `unsafe`, `dead` | **no se envían** | son *flags sin valor*; al omitirlos se ocultan contenido adulto, resultados inseguros y torrents sin seeders |
| `seen` | — | segundos desde la última vez visto |

Respuesta verificada (`/v2/search?q=Inception%202010&s=5&o=seeders&sf=title`):

```json
{"hits":[{"title":"Inception (2010) 1080p BrRip x264 - 1.85GB - YIFY",
  "hash":"224BF45881252643DFC2E71ABC7B2660A21C68C4",
  "magnetUrl":"magnet:?xt=urn:btih:…&tr=…",
  "bytes":1986422374,"seeders":900,"liveSeeders":945,"peers":116,
  "tracker":"The Pirate Bay","details":"https://knaben.xyz/…","date":"2012-06-12T23:00:00+00:00",
  "category":"Movies / HD","categoryId":[2000000,3001000],"virusDetection":0.0078}],
 "total":{"relation":"eq","value":107},"max_score":null}
```

Decisiones: se usa `hash` (o el `btih` del magnet) como `infoHash`, `liveSeeders ?? seeders`, los trackers se extraen del magnet para `stream.sources`, y se descartan resultados con `virusDetection > 0.75`.

---

## 2. torrents-csv

- Home/API: <https://torrents-csv.com/> · código: <https://codeberg.org/heretic/torrents-csv-server> (`src/main.rs`, `README.md`).
- Endpoint: `GET https://torrents-csv.com/service/search?q=…&after=…`

**Hallazgos importantes (contrastados con el código del servidor Rust):**

1. La home menciona `size=[NUMBER_OF_RESULTS]`, pero el servidor **no define ese parámetro**: `SearchQuery` sólo acepta `q` y `after`. `DEFAULT_SIZE = 25` es fijo. Una petición real con `size=3` devolvió 25 resultados.
2. `q` se convierte en `to_tsquery($1)` mediante `query.replace(' ', "&")`: **los espacios son AND**. Por eso `src/lib/query.js#toTsQuery` elimina todo carácter que no sea alfanumérico (evita `& | ! : ( ) ' *` rotos) y limita a 6 términos.
3. `q` vacío o de menos de 3 caracteres devuelve **400**.
4. Paginación por cursor: `next` (último `id`) se pasa como `after`; la consulta es `id > $2 order by id limit 25`. **No está ordenado por seeders**, así que el addon reordena.
5. Cabeceras: `Access-Control-Allow-Origin: *`, `Cache-Control: public, max-age=31536000`, `ETag`.

Respuesta real:

```json
{"torrents":[{"infohash":"224bf458…","name":"Inception (2010) 1080p BrRip …",
  "size_bytes":1991613584,"created_unix":1339547627,"seeders":832,"leechers":106,
  "completed":52264,"scraped_date":1792916202,"id":1183}],"next":660930}
```

El addon no envía `size`, pide hasta 3 páginas según `perProviderLimit`, y añade trackers públicos por defecto porque aquí **no hay magnet**.

---

## 3. Torlock (Torznab)

- Docs: <https://www.torlock.com/apis.html>
- Endpoint: `GET https://www.torlock.com/torznab/api?t=<function>` — **sin API key ni registro**.

| `t` | Parámetros | Uso |
| --- | --- | --- |
| `caps` | — | capacidades (no lo necesitamos) |
| `search` | `q` | genérica |
| `tvsearch` | `q`, `season`, `ep` | series → lo usa el addon |
| `movie-search` | `q` | películas → lo usa el addon |
| `music-search`, `book-search` | `q` | no usados |

Comunes: `cat` (ids Newznab), `limit` (1–100, defecto 100), `offset`. Categorías documentadas: `2000` Movies, `5000`/`5070` TV/Anime, `3000` Music, `4000` Software, `6000` Adult, `7020` eBooks, `8000` Other.

Respuesta: RSS 2.0 con `<torznab:attr name="seeders|peers|size|infohash|magneturl" value="…"/>`, `<enclosure url="magnet:…" length="…"/>`, `<size>`, `<category>`, `<pubDate>`. Ejemplo real (`t=search&q=ubuntu&limit=3`) muestra título, enlace, fecha, tamaño `4351463424`, magnet y categoría `4000`.

> **Fair use (textual):** "please cache on your side and don't re-request the same URL more than once a minute". Por eso `torlock.js` fuerza un TTL de caché **≥ 60 s** aunque el usuario configure menos. También existe `/rss.xml` por categoría (no usado para no hacer scraping).

`src/lib/xml.js` es un parser tolerante: acepta `<torznab:attr>`, `<newznab:attr>` y `<attr>`; lee `<enclosure>`, `<size>`, `<guid>`, `<link>`; decodifica entidades y CDATA; y obtiene el infohash del magnet si falta el atributo.

---

## 4. PreDB.net

- Docs: <https://predb.net/api-documentation/> · base: `https://api.predb.net/`
- `GET` con parámetros; JSON; **rate limit 30 peticiones / 60 s**; máximo 100 páginas; actualizado 08.08.2025.

Parámetros usados: `type=search`, `q`, `limit=25`, `order_by=pretime`, `sort=DESC`. Otros disponibles: `release` (para `type=pre|nfo`), `group`, `section`, `genre`, `tag`, `status`, `page`. Los valores múltiples se separan con comas y se excluyen con `-` (p. ej. `tag=-foreign`).

**Detalle clave:** `q` "Search starts at the **BEGINNING** of a release name". Comprobado:

| Consulta | Resultado |
| --- | --- |
| `q=The.Matrix` | `The.Matrix.Generation…`, `The.Matrix.Revolutions…`, `The.Matrix.Reloaded…` ✅ |
| `q=The%20Matrix` | resultados apenas relacionados (`The.Drop…Street.Matrix`, `The.Animatrix`) ❌ |

Por eso `toPredbPrefix()` convierte el título a `The.Matrix` (puntos). Respuesta real:

```json
{"status":"success","results":3,"time":"0.008s","page":1,
 "data":[{"release":"Inception.2010.German.DL.COMPLETE.PAL.DVD9-HYPNOKROETE",
   "section":"DVDR","files":67,"size":6322,"status":0,"group":"HYPNOKROETE",
   "genre":"Action","url":"/rls/Inception.2010…"}]}
```

`size` está en **MB**; `status=1` significa *nuked* y se descarta. No hay infohash: esta fuente se usa para obtener **nombres de release exactos** (expansión y catálogo).

---

## 5. predatabase / predataba.se

- Swagger UI: <https://api.predataba.se/> · especificación YAML: <https://predataba.se/api/documentation> · repo del concepto: OAS 3.0.2.
- **Ojo:** `api.predataba.se` sirve la UI de Swagger en cualquier ruta; la API que responde JSON está en **`https://predataba.se/api/…`**.
- `GET|POST /api/search?page=0`: `query` es un **array JSON de filtros** (GET: query string; POST: body JSON). Cabeceras: `x-type: scene|p2p` (defecto `scene`), `engine: elasticsearch|solr`, autenticación `x-api-key` o `Authorization: Bearer <JWT>`.
- Filtros documentados: `top:24h|48h|week|2weeks|month`, `group:X` / `!group:X`, `nfo:true`, `section:`, `site:`, `sort:traces`, `cast:`, `genre:`, y texto libre (AND por defecto; los negados con `!` son OR).
- Respuesta: `{found, limit, offset, pages, next_page_id, results:[{id, hash, rlsname, has_nfo, files, size, traces, section, grp, ctime, status}], engine}`.

> ⚠️ `hash` aquí **NO es un infohash**: es el identificador del release para `/api/get/{id}/{hash}`. No se usa como torrent.

**Comportamiento real sin API key (comprobado con peticiones GET):**

| Filtros | `found` | Resultado |
| --- | --- | --- |
| `["top:24h"]` | 2.576 | releases recientes reales ✅ |
| `["Inception","top:month"]` | 62.221 | funciona el `top`, no el texto |
| `["Inception"]`, `["inception"]`, `["group:ETHEL"]`, `["nfo:true"]`, `?q=Inception` | ~13.694.500 (toda la BD) | **filtros de texto/grupo ignorados** |

Conclusión: los filtros `top:*` funcionan de forma anónima; el texto libre parece exigir API key (o el POST con autenticación). El addon por tanto:

1. usa **POST** con la especificación oficial y `x-api-key` si se configura;
2. cae a **GET** si el POST falla;
3. **siempre filtra por título en cliente** (`matchesTitle`), de modo que un listado sin filtrar se descarta en lugar de contaminar los streams;
4. usa `top:24h` (que sí funciona) para el **catálogo de releases recientes**.

Límite documentado: usuarios normales, 5 páginas de resultados → el addon sólo pide `page=0`.

---

## 6. xREL v2

- Wiki: <https://www.xrel.to/wiki/1681/API.html?lang=en_US> · OpenAPI: <https://github.com/xREL-to/OpenAPI/blob/master/xRELOpenAPI.yaml>
- Bases: `https://api.xrel.to/v2` y espejo `https://api-alternate.xrel.top/v2` (recomendado por la propia wiki para IPs bloqueadas; configurable en la UI).
- `GET /search/releases.json?q=&scene=1&p2p=1&limit=25` — **sin autenticación**.
- Límites: 900 llamadas/hora por IP y **2 búsquedas cada 5 s** → `RateLimiter` de 2.600 ms.
- **Discrepancia detectada:** el OpenAPI declara `result`, pero la respuesta real devuelve `results` **y** `p2p_results`. El addon lee ambas.

Respuesta real (`q=Inception&scene=1&p2p=1&limit=3`), recortada:

```json
{"total":6,
 "results":[{"dirname":"Inception.2010.German.DL.COMPLETE.PAL.DVD9-HYPNOKROETE",
   "size":{"number":6322,"unit":"MB"},"group_name":"HYPNOKROETE",
   "ext_info":{"type":"movie","uris":["imdb:tt1375666"]},"flags":{"english":true}}],
 "p2p_results":[{"dirname":"Inception.2010.Remux.1080p.BluRay.AVC.DTS-HD.MA.5.1-LEGi0N",
   "size_mb":29210,"category":{"meta_cat":"movie","sub_cat":"HD-1080p"},
   "group":{"name":"LEGi0N"},"ext_info":{"uris":["imdb:tt1375666"]}}]}
```

`ext_info.uris` contiene `imdb:tt…`: el addon **descarta releases cuyo IMDb conocido no coincide** con el solicitado (muy útil con títulos ambiguos: en `q=The Matrix` aparecía `The.Animatrix` con `tt0328832`). También se aprovechan `tv_season`/`tv_episode` y `size` + `unit` (o `size_mb`). Para el catálogo se usa `GET /p2p/releases.json?per_page=…`, que no consume el límite de búsqueda.

---

## 7. Stremio y Cinemeta

- SDK: <https://github.com/Stremio/stremio-addon-sdk> — [manifest](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md), [stream](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md), [defineStreamHandler](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests/defineStreamHandler.md), [advanced/Cinemeta](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/advanced.md).

Puntos que condicionan la implementación:

- El manifiesto declara `resources: [{name:'stream', types:['movie','series'], idPrefixes:['tt','release:']}]`; `behaviorHints.configurable = true` (abre `/configure`) y **`behaviorHints.p2p = true`** (Stremio avisa de que se expone la IP).
- El id de vídeo de una serie Cinemeta es `tt0903747:1:2` (meta:season:episode).
- Un *Stream Object* con torrent necesita `infoHash` (si no se da `fileIdx`, Stremio elige el fichero más grande) y opcionalmente `sources` con trackers en formato **`tracker:udp://…`**.
- `title` está en desuso frente a `description`; se envían ambos por compatibilidad. `behaviorHints.bingeGroup` agrupa episodios.
- Metadatos: `GET https://v3-cinemeta.strem.io/meta/{movie|series}/{imdbId}.json` → `meta.name`, `meta.year`/`releaseInfo`. Se cachean 24 h.
- Los ids propios `release:<base64url>` permiten que el catálogo tenga fichas (`meta`) y streams por nombre exacto sin depender de Cinemeta.

---

## 8. Límites y cómo se respetan

| Proveedor | Límite publicado | Medida en el addon |
| --- | --- | --- |
| Knaben | "no saturar el servidor" | caché + 400 ms entre llamadas + `s` acotado |
| torrents-csv | caché pública de 1 año | caché + 350 ms + máximo 3 páginas |
| Torlock | no repetir la misma URL en < 60 s | **TTL mínimo 60 s** + 1.200 ms + dedupe de peticiones en vuelo |
| PreDB.net | 30 / 60 s | 2.100 ms entre llamadas + caché |
| predataba.se | 5 páginas para anónimos | siempre `page=0` + caché + 1.200 ms |
| xREL | 900/h y 2 búsquedas / 5 s | 2.600 ms + caché de 24 h para Cinemeta |

Además: todas las peticiones salientes pasan por `src/lib/http.js` con **timeout**, **límite de 4 MB de respuesta**, `User-Agent` identificable, y la caché sirve el último valor válido si un proveedor falla (*stale-if-error*). Los seis proveedores devuelven `[]` ante cualquier error: un fallo nunca rompe la respuesta de Stremio.

---

## 9. Arquitectura resultante

```
Stremio ── /stream/movie/tt…json ──> server.js
                                      │
                        Cinemeta ── título + año
                                      │
        ┌─────────────────────────────┼─────────────────────────────┐
        │ índices CON infohash        │  bases de releases (sin hash)│
        │ Knaben / torrents-csv /     │  PreDB.net / predataba.se /  │
        │ Torlock                     │  xREL                        │
        └──────────────┬──────────────┴──────────────┬──────────────┘
                       │                             │ nombres exactos
                       │◄────── expansión ───────────┘
                       ▼
        merge por infohash → filtros → ranking → Stream Objects
```

Las tres bases de releases también alimentan un **catálogo opcional** (búsqueda + releases recientes) con ids `release:`.
