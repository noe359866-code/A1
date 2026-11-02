# Omni Index · addon de Stremio

Addon de Stremio que agrega **seis APIs públicas** de índices de torrents y de releases scene/P2P, y devuelve *streams* con `infoHash` listos para reproducir:

| Con infohash | Sólo metadatos (nombres de release) |
| --- | --- |
| [Knaben API v2](https://knaben.org/api/v2/) | [PreDB.net](https://predb.net/api-documentation/) |
| [torrents-csv](https://torrents-csv.com/) | [predataba.se](https://api.predataba.se/) |
| [Torlock Torznab](https://www.torlock.com/apis.html) | [xREL API](https://www.xrel.to/wiki/1681/API.html) |

> La investigación completa —parámetros, respuestas reales, límites y varias trampas descubiertas— está en **[docs/API-RESEARCH.md](docs/API-RESEARCH.md)**.

---

## Idea central

Tres de las APIs devuelven torrents (`infohash`), y las otras tres devuelven **nombres de release** pero ningún torrent. En lugar de ignorarlas, el addon las usa como *inteligencia de búsqueda*:

1. Resuelve el `tt…` a título/año con Cinemeta.
2. Consulta en paralelo Knaben, torrents-csv y Torlock.
3. Consulta en paralelo PreDB.net, predataba.se y xREL para obtener nombres exactos (`Inception.2010.2160p.UHD.BluRay.H265-GROUP`).
4. Usa esos nombres como consultas adicionales en los índices con infohash (**expansión**, configurable).
5. Unifica por `infoHash` (sumando proveedores y trackers), filtra resultados irrelevantes/CAM/no-vídeo, puntúa por calidad + seeders + coincidencia de episodio y devuelve los mejores *streams*.

También puede publicar un **catálogo opcional** de releases (búsqueda global de Stremio + fila de recientes) con fichas `release:` que, al abrirse, buscan torrents por nombre exacto.

---

## Instalación rápida

Requiere **Node.js ≥ 18** (probado con Node 22). No tiene dependencias npm.

```bash
git clone <este-repo> A1 && cd A1
npm start                      # http://localhost:7000
```

Abre **<http://localhost:7000/configure>**, ajusta las opciones y pulsa *Generar enlace de instalación*. La página ofrece:

- el enlace `stremio://…/c/<config>/manifest.json` para instalar directamente,
- la URL `https://…/c/<config>/manifest.json` para pegar en *Addons → Add addon*,
- un botón **Probar con tt1375666** que ejecuta una búsqueda real y muestra los streams.

La configuración viaja codificada en base64url dentro de la propia URL; cada usuario puede tener su propia instalación sin tocar el servidor. El manifiesto raíz (`/manifest.json`) usa los valores por defecto.

### Docker

```bash
docker compose up -d --build     # puerto 7000
# o
docker build -t omni-index . && docker run -p 7000:7000 omni-index
```

> **Sólo datos reales.** El addon consulta siempre las seis APIs; no genera resultados de prueba ni respuestas inventadas. Si un proveedor falla o no hay red, no aporta resultados y el error queda registrado en `/api/status`. (Los tests usan un servidor HTTP local como *test double*, ajeno al addon en ejecución.)

---

## Configuración

Opciones de la página `/configure` (también vía `OMNI_CONFIG='{"maxStreams":20,…}'`):

| Opción | Defecto | Descripción |
| --- | --- | --- |
| `providers` | los 6 activos | Activar/desactivar cada fuente individualmente. |
| `expansion` | `true` | Buscar nombres de release exactos en los índices con infohash. |
| `maxExpansionQueries` | `2` | Nº de nombres extra (0–6). Cada uno dispara una consulta por proveedor de expansión. |
| `expansionProviders` | Knaben, torrents-csv | Proveedores usados en la expansión. |
| `maxStreams` | `40` | Límite de streams devueltos. |
| `minSeeders` | `1` | Descarta torrents con menos seeders (sólo si se conoce el dato). |
| `perProviderLimit` | `60` | Resultados pedidos a cada proveedor. |
| `sortBy` | `balanced` | `balanced` / `seeders` / `quality`. |
| `excludeCam` | `true` | Filtra CAM/TS/Telecine. |
| `cacheTtl` | `900` | Segundos de caché (Torlock fuerza ≥ 60 s por su *fair use*). |
| `httpTimeout` | `9000` | Timeout por petición saliente (ms). |
| `enableCatalog` | `false` | Publica el catálogo de releases (añade `catalog` + `meta` al manifiesto). |
| `predatabaKey` | vacío | `x-api-key` opcional de predataba.se. Sin ella los filtros de texto se ignoran; el addon filtra en cliente. |
| `predatabaType` | `scene` | `scene` / `p2p` / `both`. |
| `xrelAlternate` | `false` | Usa `api-alternate.xrel.top` si tu IP está bloqueada. |

Variables de entorno del servidor:

| Variable | Uso |
| --- | --- |
| `PORT` / `OMNI_PORT`, `HOST` | Escucha (por defecto `7000`, `0.0.0.0`). |
| `OMNI_CONFIG` | JSON con valores por defecto del manifiesto raíz. |
| `OMNI_PREDATABA_KEY`, `OMNI_CACHE_TTL`, `OMNI_MAX_STREAMS`, `OMNI_ENABLE_CATALOG` | Atajos de configuración. |
| `OMNI_HTTP_TIMEOUT`, `OMNI_USER_AGENT`, `OMNI_QUIET` | Transporte y logs. |
| `OMNI_<PROVEEDOR>_BASE` | Sobrescribe la URL base (`OMNI_KNABEN_BASE`, `OMNI_TORRENTS_CSV_BASE`, `OMNI_TORLOCK_BASE`, `OMNI_PREDB_NET_BASE`, `OMNI_PREDATABA_SE_BASE`, `OMNI_XREL_BASE`, `OMNI_CINEMETA_BASE`). Útil para proxies y tests. |

---

## Endpoints HTTP

| Ruta | Respuesta |
| --- | --- |
| `/` , `/configure` | Página de configuración e instalación. |
| `/manifest.json` | Manifiesto con la configuración por defecto. |
| `/c/<config>/manifest.json` | Manifiesto con la configuración incrustada. |
| `/stream/{movie\|series}/{id}.json` | `{ "streams": [ … ] }`. `id` puede ser `tt…`, `tt…:temporada:episodio` o `release:<base64url>`. |
| `/catalog/{type}/omni-scene[/search=…&skip=…].json` | `{ "metas": [ … ] }` (si `enableCatalog`). |
| `/meta/{type}/release:<…>.json` | Ficha generada a partir del id `release:`. |
| `/health`, `/api/status` | Estado, proveedores, caché y últimos errores. |
| `/c/<config>/…` | Todas las rutas anteriores aceptan el prefijo de configuración. |

Todas las respuestas incluyen CORS (`Access-Control-Allow-Origin: *`) y `Cache-Control` adecuado. Un proveedor caído **nunca** devuelve un 500 a Stremio: se registra en `/api/status` y se responde con una lista vacía.

---

## Detalles de implementación que vale la pena conocer

- `torrents-csv` **ignora `size`** (su home lo documenta mal) y usa `to_tsquery`: los espacios son `AND`. Sanitizamos la consulta y paginamos con `after`.
- `predb.net` busca **desde el inicio** del nombre: `The.Matrix` funciona, `The Matrix` no. Convertimos el título a puntos.
- `xREL` devuelve `results` + `p2p_results` (su OpenAPI dice `result`) y suele incluir `imdb:tt…`, que usamos para descartar releases de otras producciones.
- `predataba.se` vive en `predataba.se/api/…`, no en `api.predataba.se` (que es Swagger). Sin API key, los filtros de texto se ignoran; `top:24h` sí funciona y alimenta el catálogo. Su campo `hash` **no** es un infohash.
- `Torlock` exige no repetir la misma URL en menos de un minuto: caché con TTL mínimo de 60 s.
- Infohashes: se aceptan hex de 40, base32 de 32 y magnets (`urn:btih:`), y los trackers del magnet se convierten a `sources: ["tracker:udp://…"]`.
- Caché con TTL, dedupe de peticiones concurrentes, *stale-if-error* y rate limiting por proveedor.

---

## Tests

```bash
npm test        # 41 pruebas, node:test, sin dependencias
npm run lint    # comprobación de sintaxis
```

La suite levanta un servidor HTTP local que simula las seis APIs y Cinemeta, y cubre: parseo Torznab/Newznab, normalización de hashes y magnets, dedupe y ranking (incluidos episodios de series), los seis proveedores con sus parámetros reales, el manifiesto configurable, catálogo/meta, resiliencia ante fallos y CORS.

---

## Estructura

```
src/
  index.js            arranque del servidor
  server.js           rutas, CORS, configuración por URL, /configure
  manifest.js         manifiesto (varía con la configuración)
  config.js           valores por defecto, validación y codificación
  context.js          caché y rate limiters compartidos
  metadata.js         resolución de títulos vía Cinemeta
  stream.js           orquestación, expansión y ranking
  catalog.js          catálogo y fichas release:
  endpoints.js        URLs base (sobrescribibles por entorno)
  providers/          knaben, torrents-csv, torlock, predb-net,
                      predataba-se, xrel (+ índice)
  lib/                http, cache, limiter, xml, query, torrent
  public/index.html   UI de configuración e instalación
tests/                mock server + 5 ficheros de pruebas
docs/API-RESEARCH.md  informe de investigación
```

---

## Respeto a los servicios y aviso legal

El addon **no aloja, no indexa por su cuenta y no redistribuye contenido**: sólo consulta metadatos públicos y devuelve identificadores de torrents a Stremio. Aplica caché, espaciado de peticiones y límites de tamaño/timeout para no saturar servicios ajenos, y respeta las condiciones de uso de cada uno (especialmente Torlock y PreDB.net). Si vas a desplegarlo públicamente, revisa las condiciones actuales de cada API y considera usar tus propias claves.

Los resultados pueden incluir material protegido por derechos de autor. **Utilízalo únicamente con contenido que tengas derecho a reproducir** y de acuerdo con la legislación de tu país. El manifiesto declara `behaviorHints.p2p = true` para que Stremio informe de que el uso de BitTorrent puede revelar tu IP; tampoco se activan los flags de contenido adulto de Knaben.

Código bajo licencia [MIT](LICENSE).
