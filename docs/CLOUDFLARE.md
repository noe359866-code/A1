# Omni Index · Cloudflare Workers

Guía de publicación del addon en **Cloudflare Workers** (alternativa a
[Beamup](BEAMUP.md)). El mismo código sirve los dos despliegues: no hay una
copia del addon ni resultados simulados, y las rutas, el manifiesto, el ID
`org.omniindex.addon` y la configuración por URL son idénticos.

> Estado: el Worker está implementado y verificado con `wrangler deploy
> --dry-run` y con `wrangler dev` (workerd real) contra las seis APIs simuladas.
> La publicación en tu cuenta requiere tus credenciales de Cloudflare; desde
> este entorno no se ha ejecutado `wrangler deploy`.

---

## 1. Por qué funciona sin `nodejs_compat`

El `node:http` de `src/server.js` no puede ejecutarse en un Worker (no hay
sockets en escucha). Por eso se extrajo toda la lógica de rutas a
**`src/router.js`**, que sólo usa APIs web estándar y devuelve objetos
`Response`:

```
                       ┌─ src/server.js      (node:http → Request/Response)  → local / Docker / Beamup
src/router.js  ◄───────┤
  + proveedores        └─ src/worker.js     (export default { fetch })      → Cloudflare Workers
  + src/lib/*
```

Cambios realizados para que el núcleo sea portátil:

| Antes (Node) | Ahora (web estándar) |
| --- | --- |
| `node:http`, `node:fs`, `node:path` en el servidor | `Request` / `Response` en `router.js`; `fs` sólo en `assets-node.js` |
| `Buffer.from(..., 'base64url')` | `TextEncoder` + `btoa`/`atob` (`src/lib/encoding.js`) |
| `Buffer.concat(...).toString('utf8')` al leer respuestas | `ReadableStream` + `TextDecoder` |
| `Buffer.from(bytes).toString('hex')` | `bytesToHex()` |
| `process.env.OMNI_*` en cada módulo | objeto `env` de la petición (`ctx.env`, `options.env`), con `process` como valor por defecto sólo en Node |
| estáticos leídos del disco | binding `ASSETS` (Workers Assets) en el Worker, disco en Node |

`wrangler.toml` **no** activa `nodejs_compat`: el bundle resultante son ~65 KiB
sin dependencias de ejecución. `tests/worker.test.js` recorre el grafo de
imports y falla si alguien vuelve a introducir `node:*`, `Buffer`,
`require()` o variables de proceso en el núcleo.

---

## 2. Requisitos

- Cuenta de Cloudflare (el plan gratuito sirve para empezar).
- **Node.js ≥ 22** para el CLI `wrangler@4` (el addon en Node sigue funcionando
  con Node ≥ 18; es el CLI el que exige 22).
- No hace falta `npm install` para desarrollar el Worker: el repo no tiene
  dependencias de ejecución y los scripts usan `npx wrangler@4`.

```bash
node -v                       # v22.x recomendado
npx --yes wrangler@4 login    # abre el navegador y guarda el token local
```

---

## 3. Inicio rápido

```bash
git clone <este-repo> A1 && cd A1

npm run worker:dev            # = npx wrangler@4 dev
# abre http://localhost:8787/configure

npm run worker:deploy         # = npx wrangler@4 deploy
```

Al desplegar, Wrangler imprime la URL del Worker, por ejemplo:

```
https://omni-index.<tu-subdominio>.workers.dev
```

Instala el addon en Stremio con:

```
https://omni-index.<tu-subdominio>.workers.dev/manifest.json
```

o abre `/configure` en esa URL para generar tu enlace
`stremio://…/c/<config>/manifest.json` con tus opciones. **La configuración
sigue viajando en la URL**: cada usuario tiene su propia instalación y no hay
que tocar el Worker ni sus variables para cambiarla.

Para ver los logs en producción: `npm run worker:tail` (o *Workers & Pages →
Tu Worker → Logs*, gracias a `[observability] enabled = true`).

---

## 4. Qué publica exactamente `wrangler.toml`

| Clave | Valor | Para qué |
| --- | --- | --- |
| `name` | `omni-index` | Nombre del Worker y del subdominio `*.workers.dev`. Cámbialo si ya existe. |
| `main` | `src/worker.js` | Entry point con `export default { fetch(request, env, ctx) }`. |
| `compatibility_date` | `2025-06-01` | Fija el comportamiento de workerd; puedes actualizarla al día del despliegue. |
| `compatibility_flags` | `[]` | Sin `nodejs_compat`. |
| `[assets] directory` | `src/public` | Publica `index.html`, `logo.png`, `favicon.*` y `background.png` en la red de Cloudflare. |
| `[assets] binding` | `ASSETS` | Permite servir esos ficheros desde el Worker con `env.ASSETS.fetch()`. |
| `[assets] run_worker_first` | `true` | **Todas** las peticiones pasan por `src/worker.js`: así se añaden CORS, se inyecta `window.OMNI_BOOTSTRAP` en `/configure` y funcionan rutas como `/c/<config>/logo.png`. |
| `[observability]` | `enabled = true` | Logs en el dashboard. |

> Si omites `binding = "ASSETS"` (o usas una versión de Wrangler sin Workers
> Assets), `/configure` y las imágenes responden **503 con un mensaje claro**
> en lugar de fallar en silencio. El resto de rutas sigue funcionando.

### Logo y fondo

Stremio exige URLs **absolutas** en `manifest.logo` y `manifest.background`.
En Cloudflare `request.url` ya es la URL pública HTTPS, así que el manifiesto
las construye desde ahí sin depender de proxies. A diferencia del adaptador
Node/Beamup, el Worker **ignora `X-Forwarded-Host` / `X-Forwarded-Proto`** que
pudiera enviar un cliente: no son fiables en el edge. Si usas un dominio
propio detrás de un proxy que reescribe el Host, fija:

```toml
[vars]
OMNI_PUBLIC_URL = "https://addon.tudominio.com"
```

Con un *Custom Domain* o una *Route* de Cloudflare sobre el Worker no hace
falta: el origen de la petición ya es el dominio correcto.

---

## 5. Variables y secretos

Las **mismas** variables `OMNI_*` de `.env.example` funcionan aquí, pero se
declaran de otra forma:

| Tipo | Dónde | Ejemplo |
| --- | --- | --- |
| Variable pública | `[vars]` en `wrangler.toml` o `wrangler deploy --var CLAVE:valor` | `OMNI_CONFIG = "{\"maxStreams\":30}"` |
| Secreto | `npx wrangler@4 secret put NOMBRE` | `OMNI_PREDATABA_KEY` |
| Variable local de desarrollo | `.dev.vars` (no versionado; plantilla en `.dev.vars.example`) | `OMNI_QUIET=1` |
| Configuración por usuario | URL `/c/<base64url>/…` | la genera `/configure` |

Variables disponibles (idénticas a las del README):

- `OMNI_PUBLIC_URL`, `OMNI_CONFIG`, `OMNI_PREDATABA_KEY`, `OMNI_CACHE_TTL`,
  `OMNI_MAX_STREAMS`, `OMNI_ENABLE_CATALOG`.
- `OMNI_HTTP_TIMEOUT`, `OMNI_USER_AGENT`, `OMNI_QUIET`.
- `OMNI_<PROVEEDOR>_BASE`: `OMNI_KNABEN_BASE`, `OMNI_TORRENTS_CSV_BASE`,
  `OMNI_TORLOCK_BASE`, `OMNI_PREDB_NET_BASE`, `OMNI_PREDATABA_SE_BASE`,
  `OMNI_XREL_BASE`, `OMNI_CINEMETA_BASE`, `OMNI_ANIME_KITSU_BASE`.

`PORT` y `HOST` **no existen** en Workers: Cloudflare gestiona el puerto.

Ejemplos útiles:

```bash
# Espejo de xREL si la IP de Cloudflare está bloqueada
npx wrangler@4 deploy --var OMNI_XREL_BASE:https://api-alternate.xrel.top

# Clave opcional de predataba.se (secreto cifrado, no aparece en wrangler.toml)
npx wrangler@4 secret put OMNI_PREDATABA_KEY

# Listar secretos / eliminar uno
npx wrangler@4 secret list
npx wrangler@4 secret delete OMNI_PREDATABA_KEY
```

> Los `vars` son visibles para cualquiera con acceso al repo. Las claves van
> siempre como `secret`, nunca en `[vars]` ni en `.dev.vars` versionado.

---

## 6. Caché, límites y comportamiento real de las APIs

- **Caché**: sigue siendo la caché en memoria con TTL, dedupe de peticiones
  concurrentes y *stale-if-error* de `src/lib/cache.js`. En Workers vive en el
  *isolate*: se comparte entre peticiones del mismo proceso, pero se pierde al
  reciclarse (como al reiniciar el contenedor de Beamup). Se recalienta sola;
  `/api/status` muestra `entries`, `hits`, `misses` y `stale`.
- **Subpeticiones**: una búsqueda con expansión puede lanzar ~20-30 `fetch` a
  las seis APIs + Cinemeta, dentro del límite de subpeticiones del plan
  gratuito. Si vas a publicar el addon para muchos usuarios, reduce
  `maxExpansionQueries` / `perProviderLimit` desde `/configure` o considera un
  plan de pago.
- **Tiempo**: cada petición saliente tiene su propio `AbortSignal.timeout`
  (`httpTimeout`, 9 s por defecto). Las seis consultas van en paralelo, de modo
  que el tiempo de respuesta lo marca el proveedor más lento, no la suma.
- **IPs de datacenter**: algunas fuentes (Torlock, xREL) bloquean rangos de
  Cloudflare. No se rellena con datos falsos: el proveedor falla, se registra
  en `/api/status` y Stremio recibe una lista vacía. Prueba
  `OMNI_XREL_BASE=https://api-alternate.xrel.top` y revisa los logs con
  `npm run worker:tail`.
- **Rate limiting**: los limitadores por proveedor siguen activos dentro de
  cada isolate (2100 ms para PreDB.net, 2600 ms para xREL, etc.).

---

## 7. Despliegue con GitHub Actions

`.github/workflows/deploy-cloudflare.yml` es **manual** (`workflow_dispatch`),
como el de Beamup: un despliegue por decisión explícita, nunca en cada push.

1. En Cloudflare: *My Profile → API Tokens → Create Token*, usa la plantilla
   **Edit Cloudflare Workers** (permisos `Account:Workers Scripts:Edit`,
   `Account:Account Settings:Read`, `Zone:Workers Routes:Edit` si usas rutas).
2. En GitHub: *Settings → Secrets and variables → Actions → New repository
   secret*:
   - `CLOUDFLARE_API_TOKEN`: el token anterior.
   - `CLOUDFLARE_ACCOUNT_ID`: tu *Account ID* (dashboard de Cloudflare,
     barra lateral derecha). Alternativamente añade `account_id = "..."` a
     `wrangler.toml`.
3. *Actions → Desplegar en Cloudflare Workers → Run workflow*.

El job ejecuta `npm ci`, `npm run lint`, `npm test`, `npm run worker:check`
(bundle sin publicar) y finalmente `npx wrangler@4 deploy`, usando
`CLOUDFLARE_API_TOKEN` / `CLOUDFLARE_ACCOUNT_ID` como variables de entorno.
La CI general (`ci.yml`) también comprueba el bundle en Node 22.

> El workflow debe existir en la rama por defecto para que GitHub permita
> despacharlo con `workflow_dispatch` (igual que el de Beamup).

### Dominio propio

Desde *Workers & Pages → omni-index → Settings → Domains & Routes* añade un
*Custom Domain* (Cloudflare gestiona el certificado) o una *Route* como
`addon.tudominio.com/*`. También puedes declarar rutas en `wrangler.toml`:

```toml
routes = [
  { pattern = "addon.tudominio.com", custom_domain = true }
]
```

---

## 8. Verificación y solución de problemas

```bash
npm test                    # 66 tests: incluye tests/worker.test.js
npm run worker:check        # wrangler deploy --dry-run (bundle, sin publicar)
npm run worker:dev          # workerd local en http://localhost:8787
curl -s localhost:8787/health
curl -s localhost:8787/manifest.json | jq .logo
```

| Síntoma | Causa / solución |
| --- | --- |
| `503` con «Assets no disponibles» en `/configure` o `/logo.png` | Falta `[assets]` / `binding = "ASSETS"` o el Wrangler es anterior a Workers Assets. Usa `wrangler@4`. |
| El logo apunta a `localhost:8787` en producción | Estás viendo el manifiesto de `wrangler dev`. En producción `request.url` es la URL pública; si hay un proxy que reescribe Host, define `OMNI_PUBLIC_URL`. |
| `streams: []` en una ficha de **anime** (`kitsu:…`, `mal:…`) | El manifiesto debe anunciar `types: [..., "anime"]` e `idPrefixes` con `kitsu`, `mal`, `anilist`, `anidb`. Desde la v1.1.0 está incluido: reinstala el addon para que Stremio descargue el manifiesto nuevo. |
| `streams: []` tras desplegar | Revisa `/api/status` y `npm run worker:tail`: probablemente las APIs bloquean la IP de Cloudflare. Prueba el espejo de xREL. Nunca se devuelven resultados inventados. |
| `Error: No such module "node:..."` al desplegar | Alguien añadió un módulo de Node al grafo del Worker. `npm test` lo detecta; mueve ese código a `src/server.js` / `src/assets-node.js`. |
| `wrangler` pide Node 22 | El CLI v4 exige Node ≥ 22. Actualiza Node o usa `npx wrangler@3` (no recomendado). El addon Node sigue soportando 18+. |
| `compatibility_date` warning | Ajusta la fecha de `wrangler.toml` a una no futura (p. ej. el día del despliegue). |
| Error de autenticación en Actions | Revisa `CLOUDFLARE_API_TOKEN` y `CLOUDFLARE_ACCOUNT_ID`; el token debe tener permiso *Workers Scripts:Edit*. |
| Caché vacía tras un despliegue | Es en memoria por diseño; se recalienta con el primer uso. |
| Addon duplicado en Stremio | El ID es el mismo (`org.omniindex.addon`): si vienes de Beamup, desinstala la instalación anterior y añade la nueva URL. |

---

## 9. Convivencia con Beamup / Docker

No hay que elegir: el repositorio mantiene `Procfile`, `npm start`,
`docker/Dockerfile`, `.slugignore` y el workflow de Beamup sin cambios
funcionales. `src/server.js` es ahora un adaptador delgado sobre `router.js`, y
`src/index.js` sigue siendo el proceso `web` que respeta `PORT`. Los tests de
`beamup.test.js` siguen validando el linter oficial y el arranque con `PORT`.

`.slugignore` y `.dockerignore` excluyen los ficheros exclusivos del Worker
(`wrangler.toml`, `src/worker.js`, `src/assets-worker.js`, `.dev.vars*`) para
que el slug de Beamup y la imagen Docker no crezcan innecesariamente.

---

## 10. Referencias

- `https://developers.cloudflare.com/workers/` (fetch handlers, límites)
- `https://developers.cloudflare.com/workers/static-assets/` (binding `ASSETS`, `run_worker_first`)
- `https://developers.cloudflare.com/workers/wrangler/configuration/` (`wrangler.toml`)
- `https://developers.cloudflare.com/workers/wrangler/commands/` (`dev`, `deploy`, `secret`, `tail`)
- `https://github.com/cloudflare/workers-sdk` (Wrangler / workerd)
