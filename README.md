# Omni Index · addon de Stremio

Addon de Stremio que agrega **seis APIs públicas** de índices de torrents y de releases scene/P2P, y devuelve *streams* con `infoHash` listos para reproducir:

| Con infohash | Sólo metadatos (nombres de release) |
| --- | --- |
| [Knaben API v2](https://knaben.org/api/v2/) | [PreDB.net](https://predb.net/api-documentation/) |
| [torrents-csv](https://torrents-csv.com/) | [predataba.se](https://api.predataba.se/) |
| [Torlock Torznab](https://www.torlock.com/apis.html) | [xREL API](https://www.xrel.to/wiki/1681/API.html) |

> La investigación completa —parámetros, respuestas reales, límites y varias trampas descubiertas— está en **[docs/API-RESEARCH.md](docs/API-RESEARCH.md)**. Guías de publicación: **[docs/CLOUDFLARE.md](docs/CLOUDFLARE.md)** (Cloudflare Workers) y **[docs/BEAMUP.md](docs/BEAMUP.md)** (Stremio Beamup).

**Dos runtimes, un mismo addon.** `src/router.js` contiene todas las rutas con
APIs web estándar (`Request`/`Response`/`fetch`); `src/server.js` lo adapta a
`node:http` (local, Docker y Beamup) y `src/worker.js` lo expone como handler
`fetch` de **Cloudflare Workers**. No hay dependencias de ejecución ni
`nodejs_compat`: el bundle del Worker son ~65 KiB.

---

## Identidad

<img src="src/public/logo.png" alt="Logo de Omni Index: una lupa con seis puntos" width="112" align="right">

| | |
| --- | --- |
| **Nombre** | **Omni Index** |
| **Eslogan** | *Seis índices, una búsqueda* |
| **ID de Stremio** | `org.omniindex.addon` |
| **Versión** | `1.0.0` |
| **Proyecto en Beamup** | `omniindex` |
| **Worker en Cloudflare** | `omni-index` (`wrangler.toml`) |
| **Logo** | lupa de trazo único violeta `#7c5cff` con seis puntos turquesa `#00c2a8`, símbolo sin texto y fondo transparente |

Los assets de marca los sirve el propio addon: `/logo.png` (256 px, transparente),
`/favicon.ico`, `/favicon.png` y `/background.png` (1280×720). Stremio exige URLs
**absolutas** en `logo` y `background`, así que el manifiesto las construye con el
origen de la petición (en Beamup respetando `X-Forwarded-Proto` /
`X-Forwarded-Host`; en Cloudflare directamente con `request.url`) o con
`OMNI_PUBLIC_URL` si el proxy no reenvía el Host real. En el Worker los estáticos
vienen del binding `ASSETS` de Workers Assets. La UI usa el mismo nombre,
eslogan y paleta.

> **Cambio de ID** (antes `org.arena.omniindex`): Stremio identifica
> los addons por ID, así que una instalación anterior debe desinstalarse y volver
> a instalarse con la URL nueva. El proyecto de Beamup pasó a llamarse `omniindex`.

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

Requiere **Node.js ≥ 18** (probado con Node 22). **No tiene dependencias de ejecución**: `npm start` funciona directamente tras clonar. La única dependencia es de desarrollo (`stremio-addon-linter`) para verificar el contrato de Beamup; instálala con `npm ci` si vas a ejecutar los tests.

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
docker build -f docker/Dockerfile -t omni-index . \
  && docker run -p 7000:7000 omni-index
```

> El `Dockerfile` vive en `docker/` (no en la raíz) para que Beamup elija el buildpack Herokuish de Node y no el *builder* de Dockerfile. Ver [docs/BEAMUP.md](docs/BEAMUP.md).

### Cloudflare Workers

El addon también corre como Worker con el **mismo manifiesto, rutas y configuración por URL**, sin `nodejs_compat` ni dependencias. `wrangler.toml` declara el entry point `src/worker.js` y publica los estáticos de marca con el binding `ASSETS`:

```bash
npx wrangler@4 login
npm run worker:dev             # http://localhost:8787/configure (workerd local)
npm run worker:check           # valida el bundle sin publicar
npm run worker:deploy          # https://omni-index.<subdominio>.workers.dev
```

Instala en Stremio con `https://omni-index.<subdominio>.workers.dev/manifest.json`. Las variables `OMNI_*` se declaran en `[vars]`, como `wrangler secret` o en `.dev.vars`; el workflow manual `deploy-cloudflare.yml` usa `CLOUDFLARE_API_TOKEN` y `CLOUDFLARE_ACCOUNT_ID`. Guía completa (dominios propios, límites, caché en el *isolate*, solución de problemas): **[docs/CLOUDFLARE.md](docs/CLOUDFLARE.md)**. El CLI `wrangler@4` requiere **Node ≥ 22**; el servidor Node/Beamup sigue funcionando con Node ≥ 18. El despliegue real requiere tu cuenta de Cloudflare; desde este entorno no se ha ejecutado `wrangler deploy`.

### Beamup

El repositorio ya está preparado para publicarse en Beamup (Dokku/Herokuish): `package.json` + `npm start`, `Procfile` con `web: node src/index.js`, respeto de `PORT` y `0.0.0.0`, `.slugignore`, manifiesto que pasa el linter oficial y un workflow manual de despliegue. Resumen:

```bash
npm install -g beamup-cli       # comando `beamup`
beamup config a.baby-beamup.club TU_USUARIO_GITHUB
beamup init omniindex
beamup                          # despliega y muestra la URL del addon
```

Guía completa, secretos, GitHub Actions y resolución de problemas: **[docs/BEAMUP.md](docs/BEAMUP.md)**. El despliegue real requiere tu host y tu clave SSH; desde este entorno no se ha ejecutado.

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
| `PORT` / `OMNI_PORT`, `HOST` | Escucha (por defecto `7000`, `0.0.0.0`). Beamup/Dokku inyecta `PORT`; el addon lo respeta. |
| `OMNI_PUBLIC_URL` | URL pública del addon. Fija las URLs absolutas de `logo` y `background` cuando el proxy (Beamup) no reenvía el Host real. Vacío = deducirlo de la petición. |
| `OMNI_CONFIG` | JSON con valores por defecto del manifiesto raíz. |
| `OMNI_PREDATABA_KEY`, `OMNI_CACHE_TTL`, `OMNI_MAX_STREAMS`, `OMNI_ENABLE_CATALOG` | Atajos de configuración. |
| `OMNI_HTTP_TIMEOUT`, `OMNI_USER_AGENT`, `OMNI_QUIET` | Transporte y logs. |
| `OMNI_<PROVEEDOR>_BASE` | Sobrescribe la URL base (`OMNI_KNABEN_BASE`, `OMNI_TORRENTS_CSV_BASE`, `OMNI_TORLOCK_BASE`, `OMNI_PREDB_NET_BASE`, `OMNI_PREDATABA_SE_BASE`, `OMNI_XREL_BASE`, `OMNI_CINEMETA_BASE`, `OMNI_ANIME_KITSU_BASE`). Útil para proxies y tests. |

En **Cloudflare Workers** los mismos nombres `OMNI_*` se definen en `[vars]`
de `wrangler.toml`, como secretos (`wrangler secret put OMNI_PREDATABA_KEY`) o
en `.dev.vars` para desarrollo; llegan al handler en el objeto `env`.
`PORT` y `HOST` no existen allí. Ver [docs/CLOUDFLARE.md](docs/CLOUDFLARE.md).

---

## Endpoints HTTP

| Ruta | Respuesta |
| --- | --- |
| `/` , `/configure` | Página de configuración e instalación. |
| `/logo.png`, `/background.png`, `/favicon.ico`, `/favicon.png` | Assets de marca (PNG/ICO, caché 24 h). |
| `/manifest.json` | Manifiesto con la configuración por defecto. |
| `/c/<config>/manifest.json` | Manifiesto con la configuración incrustada. |
| `/stream/{movie\|series}/{id}.json` | `{ "streams": [ … ] }`. `id` puede ser `tt…`, `tt…:temporada:episodio` o `release:<base64url>`. |
| `/catalog/{type}/omni-scene[/search=…&skip=…].json` | `{ "metas": [ … ] }` (si `enableCatalog`). |
| `/meta/{type}/release:<…>.json` | Ficha generada a partir del id `release:`. |
| `/health`, `/api/status` | Estado, proveedores, caché y últimos errores. |
| `/c/<config>/…` | Todas las rutas anteriores aceptan el prefijo de configuración. |

Todas las respuestas incluyen CORS (`Access-Control-Allow-Origin: *`) y `Cache-Control` adecuado. Un proveedor caído **nunca** devuelve un 500 a Stremio: se registra en `/api/status` y se responde con una lista vacía. `/health` y `/api/status` incluyen el campo `runtime` (`node` o `cloudflare`) para saber qué despliegue estás consultando.

---

## Detalles de implementación que vale la pena conocer

- `torrents-csv` **ignora `size`** (su home lo documenta mal) y usa `to_tsquery`: los espacios son `AND`. Sanitizamos la consulta y paginamos con `after`.
- `predb.net` busca **desde el inicio** del nombre: `The.Matrix` funciona, `The Matrix` no. Convertimos el título a puntos.
- `xREL` devuelve `results` + `p2p_results` (su OpenAPI dice `result`) y suele incluir `imdb:tt…`, que usamos para descartar releases de otras producciones.
- `predataba.se` vive en `predataba.se/api/…`, no en `api.predataba.se` (que es Swagger). Sin API key, los filtros de texto se ignoran; `top:24h` sí funciona y alimenta el catálogo. Su campo `hash` **no** es un infohash.
- `Torlock` exige no repetir la misma URL en menos de un minuto: caché con TTL mínimo de 60 s.
- Infohashes: se aceptan hex de 40, base32 de 32 y magnets (`urn:btih:`), y los trackers del magnet se convierten a `sources: ["tracker:udp://…"]`.
- Caché con TTL, dedupe de peticiones concurrentes, *stale-if-error* y rate limiting por proveedor. En Workers vive en el *isolate* (se pierde al reciclarse, igual que al reiniciar el proceso Node).
- **Anime**: el manifiesto anuncia el tipo `anime` y los prefijos `kitsu`, `mal`, `anilist`, `anidb` y `anime`. Los metadatos se resuelven con `anime-kitsu.strem.fun` (título, alias y tabla de episodios), de modo que un episodio absoluto como `kitsu:12:1071` se traduce a `SxxExx` antes de buscar y, si el título romaji no está indexado, se reintenta con los alias ingleses.
- **Núcleo portable**: `router.js` sólo usa `Request`/`Response`/`fetch`, `TextEncoder`/`TextDecoder` y `atob`/`btoa`; no hay `Buffer` ni imports `node:*` en el grafo del Worker (`tests/worker.test.js` lo vigila). El entorno se inyecta como objeto `env`; el único acceso a variables de proceso es un *fallback* para Node. Los estáticos llegan por `fs` en Node y por el binding `ASSETS` en Cloudflare.

---

## Tests

```bash
npm ci               # instala stremio-addon-linter (sólo desarrollo)
npm test             # node:test (78 pruebas)
npm run lint         # comprobación de sintaxis
npm run worker:check # bundle de Cloudflare Workers sin publicar (Node >= 22)
```

La suite levanta un servidor HTTP local que simula las seis APIs y Cinemeta, y cubre: parseo Torznab/Newznab, normalización de hashes y magnets, dedupe y ranking (incluidos episodios de series), los seis proveedores con sus parámetros reales, el manifiesto configurable, catálogo/meta, resiliencia ante fallos y CORS.

- `tests/beamup.test.js` reproduce el **linter que Beamup ejecuta antes de aceptar un despliegue**: valida `/manifest.json` con `stremio-addon-linter`, comprueba el contrato Herokuish (`Procfile`, `package.json`, ausencia de `Dockerfile` en la raíz) y arranca el proceso real con un `PORT` arbitrario.
- `tests/worker.test.js` llama directamente al handler `fetch(request, env)` del Worker con un doble del binding `ASSETS` y las APIs apuntando al mock: comprueba que **todo llega por `env`** (sin variables de proceso), el origen del manifiesto, la inyección del bootstrap, los estáticos, los streams reales contra el mock y el contrato «sin módulos de Node» del grafo de imports. El bundle también se ha verificado con `wrangler dev` (workerd real) y `wrangler deploy --dry-run`.

---

## Solución de problemas: `streams: []`

Si Stremio no muestra ninguna fuente, comprueba en este orden:

1. **La ficha es de anime.** Los catálogos Anime Kitsu / AniList / MAL entregan
   ids `kitsu:…`, `mal:…`, `anilist:…`. Stremio **sólo** llama a `/stream` si el
   manifiesto anuncia ese tipo y ese prefijo; desde la v1.1.0 se anuncian, pero
   una instalación antigua conserva el manifiesto anterior: reinstala el addon.
2. **El id no es de IMDb.** Prueba la URL a mano:
   `https://<host>/stream/movie/tt1375666.json`. Si ahí sí hay streams, el
   problema es el id que envía Stremio (anime), no el addon.
3. **Título poco indexado.** Los seis índices son de scene/P2P: un documental o
   una película regional puede no existir. `streams: []` es entonces correcto;
   nunca se inventan resultados.
4. **IP bloqueada.** Mira `https://<host>/api/status` (`failures`) y
   `npm run worker:tail`. Torlock y xREL bloquean rangos de datacenter; prueba
   `OMNI_XREL_BASE=https://api-alternate.xrel.top`.
5. **Configuración restrictiva.** `minSeeders` alto, proveedores apagados o
   `maxStreams` pequeño desde `/configure` vacían la lista. Compara con
   `https://<host>/stream/movie/tt1375666.json` (config por defecto).
6. **Instalación duplicada.** El id es `org.omniindex.addon`: si cambiaste de
   host (Beamup <-> Workers), desinstala la versión anterior.

---

## Estructura

```
wrangler.toml         configuración de Cloudflare Workers (assets + vars)
.dev.vars.example     plantilla de variables locales para `wrangler dev`
Procfile              proceso `web` para Dokku/Herokuish (Beamup)
.slugignore           exclusiones del slug de Beamup
package-lock.json     bloqueo de dependencias (sólo desarrollo)
docker/Dockerfile     imagen local, fuera de la raíz por Beamup
src/
  worker.js           entry point de Cloudflare Workers (`fetch`)
  assets-worker.js    estáticos desde el binding ASSETS
  assets-node.js      estáticos desde disco (Node)
  router.js           rutas, CORS y configuración por URL (APIs web)
  server.js           adaptador node:http sobre router.js
  index.js            arranque del servidor (lee PORT)
  manifest.js         manifiesto (varía con la configuración)
  config.js           valores por defecto, validación y codificación
  context.js          caché y rate limiters compartidos
  metadata.js         resolución de títulos vía Cinemeta
  stream.js           orquestación, expansión y ranking
  catalog.js          catálogo y fichas release:
  endpoints.js        URLs base (sobrescribibles por entorno/bindings)
  providers/          knaben, torrents-csv, torlock, predb-net,
                      predataba-se, xrel (+ índice)
  lib/                http, cache, limiter, xml, query, torrent,
                      encoding, runtime-env, assets
  public/index.html   UI de configuración e instalación
  public/logo.png     logo 256 px transparente (+ favicon.ico/png, background.png)
tests/                mock server + pruebas (Beamup, Workers, proveedores…)
docs/API-RESEARCH.md  informe de investigación
docs/BEAMUP.md        guía de despliegue en Beamup
docs/CLOUDFLARE.md    guía de despliegue en Cloudflare Workers
.github/workflows/     CI + despliegues manuales (Beamup y Cloudflare)
```

---

## Respeto a los servicios y aviso legal

El addon **no aloja, no indexa por su cuenta y no redistribuye contenido**: sólo consulta metadatos públicos y devuelve identificadores de torrents a Stremio. Aplica caché, espaciado de peticiones y límites de tamaño/timeout para no saturar servicios ajenos, y respeta las condiciones de uso de cada uno (especialmente Torlock y PreDB.net). Si vas a desplegarlo públicamente, revisa las condiciones actuales de cada API y considera usar tus propias claves.

Los resultados pueden incluir material protegido por derechos de autor. **Utilízalo únicamente con contenido que tengas derecho a reproducir** y de acuerdo con la legislación de tu país. El manifiesto declara `behaviorHints.p2p = true` para que Stremio informe de que el uso de BitTorrent puede revelar tu IP; tampoco se activan los flags de contenido adulto de Knaben.

Código bajo licencia [MIT](LICENSE).
