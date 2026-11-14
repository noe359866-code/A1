# Addons Stremio y plugins Nuvio — integración implementada

**Actualizado:** 2026-11-14. Este documento describe el código de esta rama, no una propuesta pendiente.

## Dos instalaciones distintas

| Entrada | Qué se guarda | Qué hace Omni TV |
|---|---|---|
| Addon Stremio | URL `…/manifest.json`, incluido un prefijo de configuración | Comprueba `id`, `name`, `resources`, `types`; usa `catalog`, `meta` y `stream` según capacidades |
| Proyecto Omni Streams | URL base | `GET /stream/{type}/{id}.json`; alternativa sin `.json` para proyectos simples |
| Repositorio Nuvio | URL de un índice con `scrapers[]` | Lista los scrapers y delega su ejecución JS al puente configurado |

Un índice Nuvio **no** se acepta como addon Stremio. El campo de addons normaliza `stremio://` a `https://` y conserva rutas como `/configuracion/manifest.json`. HTTP sin cifrar solo se admite en la LAN; no se aceptan credenciales en la URL.

## Stremio

- Recursos como strings u objetos con `types`/`idPrefixes`.
- Restricciones globales y por recurso; un addon solo-películas no recibe peticiones de series.
- Catálogos con `extra`, `extraSupported`, `extraRequired` y opciones por defecto.
- Extras codificados en el path: `/catalog/movie/top/genre=Action&skip=20.json`.
- Búsqueda solo en catálogos que declaran `search`.
- Paginación `skip` con botón **Cargar más títulos** operable con el mando en Películas.
- Fichas desde el addon de origen o desde otro que declare `meta` para ese tipo/id.
- Streams desde addons instalados en el campo Stremio y desde proyectos base, sin duplicar la misma base.
- Respuestas `{streams:[…]}`, `{results:[…]}`, `{videos:[…]}` o array raíz.
- MP4/progresivo, HLS y DASH; cabeceras `behaviorHints.proxyHeaders.request` o `headers` por servidor.
- Se descartan torrents/infoHash, `ytId`, `notWebReady` y URLs que incumplen la política de red.

Las redirecciones JSON se revalidan con `NetPolicy`; los cuerpos tienen límite de 4 MiB. Los manifests/repositorios se cachean cinco minutos y se pueden forzar desde Ajustes.

## Nuvio y el puente JS

El televisor persiste `nuvioPlugins` y `nuvioBridge`, muestra repositorios/scrapers y el estado real del puente. **No incluye un motor JS embebido.** Sin puente, los repositorios quedan guardados pero no resuelven streams.

Se incluye un puente de referencia en `tools/nuvio-bridge/bridge.mjs` (Node 20+):

```bash
# Solo repositorios de confianza. El worker NO es un sandbox de seguridad.
node tools/nuvio-bridge/bridge.mjs \
  --repo https://raw.githubusercontent.com/D3adlyRocket/All-in-One-Nuvio/refs/heads/main/manifest.json \
  --host 0.0.0.0 --port 8788
```

En el panel QR del televisor:

1. **Plugins Nuvio → URLs de manifest**: la misma URL del repo.
2. **Puente JS**: `http://IP-DEL-EQUIPO:8788` (misma LAN).
3. Guardar y abrir Ajustes → **Comprobar manifests y puente**.

El puente solo ejecuta repositorios autorizados con `--repo` al arrancar; no acepta que un cliente le mande ejecutar una URL arbitraria. Descarga el archivo `filename` declarado, lo ejecuta en un worker desechable con límite de tiempo/memoria y llama a:

```js
getStreams(tmdbId, "movie" | "tv", seasonOrNull, episodeOrNull)
```

Los plugins deben estar compilados para Nuvio (CommonJS, `module.exports.getStreams`). Si usan dependencias npm no incluidas en el bundle, instálalas en el directorio del puente o indica `--modules /ruta/con/node_modules`. Para títulos IMDb, el puente necesita `TMDB_API_KEY` para consultar TMDB Find; con `tmdb:…` no hace falta. No se inventan ids ni resultados.

Contrato TV → puente:

```http
POST /scrape/{scraperId}
Content-Type: application/json

{
  "type": "tv",
  "id": "tmdb:123:2:3",
  "repoUrl": "https://…/manifest.json",
  "filename": "providers/anime.js",
  "scraper": "anime",
  "extra": {"tmdbId":"123", "imdbId":null, "season":2, "episode":3}
}
```

Respuesta: `{ "streams": [{ "name", "title", "url", "quality", "headers" }] }`. `GET /health` debe devolver `{ "ok": true }`. El puente de streams Python anterior (`tools/omni-stream-bridge`) sigue siendo un protocolo distinto y su `resolve()` de ejemplo no ejecuta plugins Nuvio.

## Seguridad y límites honestos

- Ejecuta el puente en un equipo/contenedor aislado y solo con repositorios de confianza. Un worker de Node no impide acceso al sistema como lo haría un sandbox real.
- El panel del QR sigue exigiendo código; guardar una URL no significa que el servidor haya respondido. Ajustes muestra el error real.
- No hay soporte P2P/torrent ni ejecución JS offline en el TV.
- La disponibilidad de cada scraper externo no está garantizada. El repositorio All-in-One consultado el 2026-11-14 declaraba 66 scrapers, no un número fijo prometido por la app.
- Las cabeceras de reproducción ya llegan a Media3 por fuente. Los subtítulos externos devueltos por scrapers aún no se incorporan como pistas laterales de Media3.
- Falta prueba en un televisor físico: compilación/lint/tests no sustituyen validar foco D-pad, codecs y reproducción HLS/DASH reales.

## Pruebas automatizadas

- JVM: parsers/capacidades, URLs configuradas, panel QR, política de red, HTTP real en loopback, redirecciones, límite de cuerpo, paginación y normalización de streams.
- Node: `node --test tools/nuvio-bridge/bridge.test.mjs` levanta un repo fixture y un puente reales, ejecuta `getStreams` en un worker y comprueba health, episodio, streams y denegación de repos no autorizados.
- CI ejecuta ambas suites, contrato TV, `assembleDebug` y `lintDebug`.
